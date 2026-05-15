package com.bloon.td;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;

public class Tower {
    public static final int TYPE_COUNT = 8;

    int type;
    float x, y;
    int gridX, gridY;
    final int[] tiers = new int[2]; // tiers bought in path 0 and path 1 (0..3)
    float cooldown = 0f;
    float aimAngle = 0f;
    int totalSpent = 0;
    float fireAnim = 0f; // 0..1 muzzle flash anim

    public static final int[] BASE_COST = {150, 280, 400, 500, 380, 600, 2200, 480};
    public static final String[] NAME = {"Dart", "Tack", "Bomb", "Sniper", "Ninja", "Ice", "Super", "Wizard"};
    public static final int[] BODY_COLOR = {
            0xFF8BC34A, 0xFFFFA726, 0xFFE53935, 0xFF7E57C2,
            0xFF455A64, 0xFF4FC3F7, 0xFFFFEB3B, 0xFF7E57C2
    };
    public static final int[] BODY_COLOR2 = {
            0xFF558B2F, 0xFFE65100, 0xFFB71C1C, 0xFF4527A0,
            0xFF263238, 0xFF0277BD, 0xFFFFC400, 0xFF4527A0
    };

    public Tower(int type, float x, float y, int gx, int gy) {
        this.type = type;
        this.x = x; this.y = y;
        this.gridX = gx; this.gridY = gy;
    }

    public float baseRange() {
        switch (type) {
            case Game.T_DART:   return 230;
            case Game.T_TACK:   return 170;
            case Game.T_BOMB:   return 260;
            case Game.T_SNIPER: return 9999;
            case Game.T_NINJA:  return 220;
            case Game.T_ICE:    return 180;
            case Game.T_SUPER:  return 320;
            case Game.T_WIZARD: return 250;
            default: return 200;
        }
    }
    public float baseRate() {
        switch (type) {
            case Game.T_DART:   return 1.4f;
            case Game.T_TACK:   return 0.7f;
            case Game.T_BOMB:   return 0.55f;
            case Game.T_SNIPER: return 0.55f;
            case Game.T_NINJA:  return 1.9f;
            case Game.T_ICE:    return 0.6f;
            case Game.T_SUPER:  return 3.5f;
            case Game.T_WIZARD: return 1.2f;
            default: return 1f;
        }
    }
    public int baseDamage() {
        switch (type) {
            case Game.T_DART:   return 1;
            case Game.T_TACK:   return 1;
            case Game.T_BOMB:   return 2;
            case Game.T_SNIPER: return 4;
            case Game.T_NINJA:  return 1;
            case Game.T_ICE:    return 0;
            case Game.T_SUPER:  return 1;
            case Game.T_WIZARD: return 1;
            default: return 1;
        }
    }
    public int basePierce() {
        switch (type) {
            case Game.T_DART:   return 2;
            case Game.T_TACK:   return 1;
            case Game.T_BOMB:   return 6;
            case Game.T_SNIPER: return 1;
            case Game.T_NINJA:  return 2;
            case Game.T_ICE:    return 9999;
            case Game.T_SUPER:  return 2;
            case Game.T_WIZARD: return 3;
            default: return 1;
        }
    }
    public float baseProjSpeed() {
        switch (type) {
            case Game.T_DART:   return 900;
            case Game.T_TACK:   return 700;
            case Game.T_BOMB:   return 600;
            case Game.T_NINJA:  return 1100;
            case Game.T_SUPER:  return 1300;
            case Game.T_WIZARD: return 800;
            default: return 900;
        }
    }

    public float range() {
        float r = baseRange();
        float mul = 1f;
        for (int p = 0; p < 2; p++)
            for (int t = 0; t < tiers[p]; t++)
                mul *= UpgradeDef.TREE[type][p][t].rangeMult;
        return r * mul;
    }
    public float rate() {
        float r = baseRate();
        float mul = 1f;
        for (int p = 0; p < 2; p++)
            for (int t = 0; t < tiers[p]; t++)
                mul *= UpgradeDef.TREE[type][p][t].rateMult;
        return r * mul;
    }
    public int damage() {
        int d = baseDamage();
        for (int p = 0; p < 2; p++)
            for (int t = 0; t < tiers[p]; t++)
                d += UpgradeDef.TREE[type][p][t].dmgAdd;
        return d;
    }
    public int pierce() {
        int v = basePierce();
        for (int p = 0; p < 2; p++)
            for (int t = 0; t < tiers[p]; t++)
                v += UpgradeDef.TREE[type][p][t].pierceAdd;
        return v;
    }
    public float projSpeed() {
        float v = baseProjSpeed();
        for (int p = 0; p < 2; p++)
            for (int t = 0; t < tiers[p]; t++)
                v *= UpgradeDef.TREE[type][p][t].projSpeedMult;
        return v;
    }

    public boolean has(int specialId) {
        for (int p = 0; p < 2; p++)
            for (int t = 0; t < tiers[p]; t++)
                if (UpgradeDef.TREE[type][p][t].special == specialId) return true;
        return false;
    }

    public int tierSum() { return tiers[0] + tiers[1]; }

    public boolean canBuyUpgrade(int path, int cash) {
        if (path < 0 || path > 1) return false;
        if (tiers[path] >= 3) return false;
        UpgradeDef u = UpgradeDef.TREE[type][path][tiers[path]];
        return cash >= u.cost;
    }
    public UpgradeDef nextUpgrade(int path) {
        if (tiers[path] >= 3) return null;
        return UpgradeDef.TREE[type][path][tiers[path]];
    }

    public void update(float dt, Game g) {
        if (fireAnim > 0) fireAnim -= dt * 4f;
        cooldown -= dt;
        if (cooldown > 0) return;
        Bloon target = findTarget(g);
        if (target == null) return;
        cooldown = 1f / rate();
        fire(target, g);
        fireAnim = 1f;
    }

    Bloon findTarget(Game g) {
        Bloon best = null;
        float bestD = -1;
        float r = range();
        for (int i = 0; i < g.bloons.size(); i++) {
            Bloon b = g.bloons.get(i);
            if (b.dead || b.spawnDelay > 0) continue;
            float d = Game.dist(b.pos.x, b.pos.y, x, y);
            if (d <= r) {
                if (b.dAlong > bestD) { bestD = b.dAlong; best = b; }
            }
        }
        return best;
    }

    void fire(Bloon target, Game g) {
        float dx = target.pos.x - x;
        float dy = target.pos.y - y;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.01f) return;
        aimAngle = (float) Math.atan2(dy, dx);
        float nx = dx / len, ny = dy / len;
        float ps = projSpeed();
        int dmg = damage();
        int pierce = pierce();
        boolean leadPop = has(UpgradeDef.SP_LEAD_POP);
        boolean slow = has(UpgradeDef.SP_SLOW_DARTS);
        boolean homing = has(UpgradeDef.SP_HOMING);

        switch (type) {
            case Game.T_DART: {
                int shots = has(UpgradeDef.SP_TRIPLE_SHOT) ? 3 : 1;
                float spread = 0.18f;
                for (int i = 0; i < shots; i++) {
                    float a = aimAngle + (i - (shots - 1) / 2f) * spread;
                    Projectile p = new Projectile();
                    p.x = x; p.y = y;
                    p.vx = (float) Math.cos(a) * ps; p.vy = (float) Math.sin(a) * ps;
                    p.kind = Projectile.K_DART;
                    p.damage = dmg; p.pierceLeft = pierce;
                    p.life = 1.4f; p.size = 9;
                    p.color = 0xFFFFFFFF; p.trailColor = 0xFFBDBDBD;
                    p.leadPop = leadPop;
                    p.angle = a;
                    g.projectiles.add(p);
                }
                break;
            }
            case Game.T_TACK: {
                int n = 8 + tierSum() * 2;
                boolean fire = has(UpgradeDef.SP_LEAD_POP) && tiers[1] >= 2; // ring of fire
                for (int i = 0; i < n; i++) {
                    float a = (float) (Math.PI * 2 * i / n);
                    Projectile p = new Projectile();
                    p.x = x; p.y = y;
                    p.vx = (float) Math.cos(a) * ps; p.vy = (float) Math.sin(a) * ps;
                    p.kind = fire ? Projectile.K_FIRE : Projectile.K_TACK;
                    p.damage = dmg; p.pierceLeft = pierce;
                    p.life = range() / ps;
                    p.size = 7;
                    p.color = fire ? 0xFFFF7043 : 0xFFFAFAFA;
                    p.trailColor = fire ? 0xFFFFA000 : 0xFFBDBDBD;
                    p.leadPop = leadPop;
                    p.angle = a;
                    g.projectiles.add(p);
                }
                break;
            }
            case Game.T_BOMB: {
                Projectile p = new Projectile();
                p.x = x; p.y = y;
                p.vx = nx * ps; p.vy = ny * ps;
                p.kind = Projectile.K_BOMB;
                p.damage = dmg; p.pierceLeft = 9999;
                p.life = 2.0f; p.size = 13;
                p.color = 0xFF212121; p.trailColor = 0xFFFF6F00;
                p.explosionRadius = 80 + tierSum() * 14
                        + (has(UpgradeDef.SP_BIGGER_BOMBS) ? 35 : 0);
                p.explosionDamage = dmg + 1;
                p.explosionPierce = pierce;
                p.leadPop = true;
                p.angle = aimAngle;
                g.projectiles.add(p);
                break;
            }
            case Game.T_SNIPER: {
                int hits = (tiers[1] >= 2) ? 3 : 1; // bouncing bullet
                Bloon t = target;
                for (int i = 0; i < hits && t != null; i++) {
                    t.hit(dmg, Projectile.K_DART, leadPop, g);
                    // visual beam
                    Projectile p = new Projectile();
                    p.x = x; p.y = y;
                    p.kind = Projectile.K_BEAM;
                    p.beamX2 = t.pos.x; p.beamY2 = t.pos.y;
                    p.life = 0.15f; p.color = 0xFFFFEB3B;
                    g.projectiles.add(p);
                    // next bloon to hit
                    Bloon nb = null;
                    float bestD = 9999;
                    for (int j = 0; j < g.bloons.size(); j++) {
                        Bloon b2 = g.bloons.get(j);
                        if (b2 == t || b2.dead) continue;
                        float d = Game.dist(b2.pos.x, b2.pos.y, t.pos.x, t.pos.y);
                        if (d < 250 && d < bestD) { bestD = d; nb = b2; }
                    }
                    t = nb;
                }
                break;
            }
            case Game.T_NINJA: {
                int shots = (tiers[0] >= 1) ? (tiers[0] >= 2 ? 3 : 2) : 1;
                float spread = 0.12f;
                for (int i = 0; i < shots; i++) {
                    float a = aimAngle + (i - (shots - 1) / 2f) * spread;
                    Projectile p = new Projectile();
                    p.x = x; p.y = y;
                    p.vx = (float) Math.cos(a) * ps; p.vy = (float) Math.sin(a) * ps;
                    p.kind = Projectile.K_SHURIKEN;
                    p.damage = dmg + (has(UpgradeDef.SP_NINJA_CAMO) ? 1 : 0);
                    p.pierceLeft = pierce;
                    p.life = 1.4f; p.size = 11;
                    p.color = 0xFF455A64; p.trailColor = 0xFFB0BEC5;
                    p.leadPop = leadPop || has(UpgradeDef.SP_NINJA_CAMO);
                    p.slow = slow;
                    p.angle = a;
                    g.projectiles.add(p);
                }
                break;
            }
            case Game.T_ICE: {
                // pulse: freeze all bloons in range
                float r = range();
                float freezeDur = 1.0f + tierSum() * 0.4f;
                int n = 0;
                for (int i = 0; i < g.bloons.size(); i++) {
                    Bloon b = g.bloons.get(i);
                    if (b.dead || b.spawnDelay > 0) continue;
                    if (Game.dist(b.pos.x, b.pos.y, x, y) <= r) {
                        b.freezeTimer = Math.max(b.freezeTimer, freezeDur);
                        if (dmg > 0) b.hit(dmg, Projectile.K_DART, leadPop, g);
                        n++;
                        if (n > pierce && pierce < 1000) break;
                    }
                }
                // VFX
                Projectile fx = new Projectile();
                fx.kind = Projectile.K_ICE_PULSE;
                fx.x = x; fx.y = y;
                fx.explosionRadius = r;
                fx.life = 0.45f;
                g.projectiles.add(fx);
                break;
            }
            case Game.T_SUPER: {
                if (has(UpgradeDef.SP_PLASMA)) {
                    Projectile p = new Projectile();
                    p.x = x; p.y = y;
                    p.vx = nx * ps; p.vy = ny * ps;
                    p.kind = Projectile.K_PLASMA;
                    p.damage = dmg + 1; p.pierceLeft = pierce + 2;
                    p.life = 1.3f; p.size = 16;
                    p.color = 0xFFFFEB3B; p.trailColor = 0xFFFFC107;
                    p.leadPop = true;
                    p.angle = aimAngle;
                    g.projectiles.add(p);
                } else if (has(UpgradeDef.SP_LASER)) {
                    Projectile p = new Projectile();
                    p.x = x; p.y = y;
                    p.vx = nx * ps * 1.4f; p.vy = ny * ps * 1.4f;
                    p.kind = Projectile.K_LASER;
                    p.damage = dmg; p.pierceLeft = pierce + 3;
                    p.life = 0.7f; p.size = 12;
                    p.color = 0xFFFF1744; p.trailColor = 0xFFFF8A80;
                    p.leadPop = true;
                    p.angle = aimAngle;
                    g.projectiles.add(p);
                } else {
                    Projectile p = new Projectile();
                    p.x = x; p.y = y;
                    p.vx = nx * ps; p.vy = ny * ps;
                    p.kind = Projectile.K_DART;
                    p.damage = dmg; p.pierceLeft = pierce;
                    p.life = 1.2f; p.size = 9;
                    p.color = 0xFFFFEB3B; p.trailColor = 0xFFFFC107;
                    p.angle = aimAngle;
                    p.homing = homing;
                    g.projectiles.add(p);
                }
                break;
            }
            case Game.T_WIZARD: {
                Projectile p = new Projectile();
                p.x = x; p.y = y;
                p.vx = nx * ps; p.vy = ny * ps;
                p.kind = Projectile.K_MAGIC;
                p.damage = dmg; p.pierceLeft = pierce;
                p.life = 1.4f; p.size = 12;
                p.color = 0xFF9C27B0; p.trailColor = 0xFFE1BEE7;
                p.leadPop = true;
                p.homing = homing;
                p.angle = aimAngle;
                g.projectiles.add(p);
                break;
            }
        }
    }

    // --------------- DRAWING ---------------
    public void draw(Canvas c, Paint p, Game g) {
        float r = g.tile * 0.42f;
        // shadow
        p.setShader(null);
        p.setColor(0x55000000);
        c.drawCircle(x + r * 0.15f, y + r * 0.25f, r * 1.05f, p);

        // base ring
        p.setColor(0xFF5D4037);
        c.drawCircle(x, y, r * 1.02f, p);
        p.setColor(0xFF8D6E63);
        c.drawCircle(x, y, r * 0.9f, p);

        // body with gradient
        int c1 = BODY_COLOR[type];
        int c2 = BODY_COLOR2[type];
        RadialGradient bodyGrad = new RadialGradient(x - r * 0.25f, y - r * 0.25f, r * 1.0f,
                c1, c2, Shader.TileMode.CLAMP);
        p.setShader(bodyGrad);
        c.drawCircle(x, y, r * 0.7f, p);
        p.setShader(null);

        // highlight
        p.setColor(0x60FFFFFF);
        c.drawCircle(x - r * 0.25f, y - r * 0.3f, r * 0.25f, p);

        // top/gun
        drawTop(c, p, r);

        // upgrade tier pips: top row = path A, bottom = path B
        for (int pp = 0; pp < 2; pp++) {
            for (int t = 0; t < 3; t++) {
                boolean owned = tiers[pp] > t;
                p.setColor(owned ? (pp == 0 ? 0xFFFFEB3B : 0xFF40C4FF) : 0x55000000);
                float py = y + r * (pp == 0 ? -1.1f : 1.05f);
                float px = x - r * 0.5f + t * r * 0.5f;
                c.drawCircle(px, py, r * 0.13f, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(2);
                p.setColor(0xFF212121);
                c.drawCircle(px, py, r * 0.13f, p);
                p.setStyle(Paint.Style.FILL);
            }
        }
    }

    void drawTop(Canvas c, Paint p, float r) {
        c.save();
        c.rotate((float) Math.toDegrees(aimAngle), x, y);
        float flash = Math.max(0, fireAnim);
        switch (type) {
            case Game.T_DART: {
                p.setColor(0xFF263238);
                c.drawRect(x, y - r * 0.13f, x + r * 0.95f + tiers[0] * r * 0.08f, y + r * 0.13f, p);
                p.setColor(0xFFFAFAFA);
                c.drawCircle(x + r * 0.95f, y, r * 0.16f, p);
                if (flash > 0) {
                    p.setColor(0xCCFFE082);
                    c.drawCircle(x + r * 1.05f, y, r * 0.32f * flash, p);
                }
                break;
            }
            case Game.T_TACK: {
                p.setColor(0xFF424242);
                int spokes = 8 + tierSum() * 2;
                for (int i = 0; i < spokes; i++) {
                    c.save();
                    c.rotate(i * (360f / spokes), x, y);
                    c.drawRect(x + r * 0.3f, y - r * 0.07f, x + r * 0.95f, y + r * 0.07f, p);
                    c.restore();
                }
                p.setColor(0xFFE0E0E0);
                c.drawCircle(x, y, r * 0.2f, p);
                break;
            }
            case Game.T_BOMB: {
                p.setColor(0xFF263238);
                c.drawRoundRect(x - r * 0.05f, y - r * 0.22f, x + r * 0.95f, y + r * 0.22f, r * 0.1f, r * 0.1f, p);
                p.setColor(0xFF455A64);
                c.drawCircle(x + r * 0.95f, y, r * 0.26f, p);
                if (flash > 0) {
                    p.setColor(0xCCFFC107);
                    c.drawCircle(x + r * 1.1f, y, r * 0.45f * flash, p);
                    p.setColor(0xAAFFEBEE);
                    c.drawCircle(x + r * 1.15f, y, r * 0.3f * flash, p);
                }
                break;
            }
            case Game.T_SNIPER: {
                p.setColor(0xFF1A237E);
                c.drawRect(x - r * 0.2f, y - r * 0.11f, x + r * 1.15f, y + r * 0.11f, p);
                p.setColor(0xFF424242);
                c.drawRect(x + r * 0.95f, y - r * 0.18f, x + r * 1.15f, y + r * 0.18f, p);
                p.setColor(0xFFFFEB3B);
                c.drawCircle(x - r * 0.25f, y, r * 0.18f, p);
                break;
            }
            case Game.T_NINJA: {
                p.setColor(0xFF263238);
                c.drawRect(x - r * 0.05f, y - r * 0.18f, x + r * 0.5f, y + r * 0.18f, p);
                p.setColor(0xFFE53935);
                c.drawRect(x - r * 0.1f, y - r * 0.2f, x + r * 0.6f, y - r * 0.1f, p);
                // shuriken
                p.setColor(0xFFB0BEC5);
                for (int i = 0; i < 4; i++) {
                    c.save();
                    c.rotate(i * 90 + flash * 90, x + r * 0.55f, y);
                    c.drawRect(x + r * 0.4f, y - r * 0.05f, x + r * 0.7f, y + r * 0.05f, p);
                    c.restore();
                }
                break;
            }
            case Game.T_ICE: {
                p.setColor(0xFFB3E5FC);
                c.drawCircle(x, y, r * 0.45f, p);
                p.setColor(0xFF0277BD);
                for (int i = 0; i < 6; i++) {
                    c.save();
                    c.rotate(i * 60, x, y);
                    c.drawRect(x + r * 0.1f, y - r * 0.05f, x + r * 0.55f, y + r * 0.05f, p);
                    c.restore();
                }
                p.setColor(0xFFE1F5FE);
                c.drawCircle(x, y, r * 0.18f, p);
                break;
            }
            case Game.T_SUPER: {
                // cape
                p.setColor(0xFFE53935);
                c.drawCircle(x - r * 0.15f, y + r * 0.05f, r * 0.55f, p);
                // body
                p.setColor(0xFFFFEB3B);
                c.drawCircle(x, y, r * 0.42f, p);
                // arms
                p.setColor(0xFFFFEB3B);
                c.drawRect(x, y - r * 0.1f, x + r * 0.95f, y + r * 0.1f, p);
                if (flash > 0) {
                    p.setColor(0xCCFFFFFF);
                    c.drawCircle(x + r * 1.05f, y, r * 0.4f * flash, p);
                }
                // visor
                p.setColor(0xFF1A237E);
                c.drawRect(x - r * 0.1f, y - r * 0.05f, x + r * 0.2f, y + r * 0.05f, p);
                break;
            }
            case Game.T_WIZARD: {
                // robe
                p.setColor(0xFF6A1B9A);
                c.drawCircle(x, y, r * 0.45f, p);
                // hat
                android.graphics.Path tri = new android.graphics.Path();
                tri.moveTo(x - r * 0.35f, y - r * 0.2f);
                tri.lineTo(x + r * 0.35f, y - r * 0.2f);
                tri.lineTo(x, y - r * 0.9f);
                tri.close();
                p.setColor(0xFF311B92);
                c.drawPath(tri, p);
                // star on hat
                p.setColor(0xFFFFEB3B);
                c.drawCircle(x, y - r * 0.45f, r * 0.08f, p);
                // staff
                p.setColor(0xFF6D4C41);
                c.drawRect(x, y - r * 0.07f, x + r * 0.85f, y + r * 0.07f, p);
                p.setColor(0xFFE040FB);
                c.drawCircle(x + r * 0.95f, y, r * 0.18f, p);
                if (flash > 0) {
                    p.setColor(0xCCE1BEE7);
                    c.drawCircle(x + r * 0.95f, y, r * 0.35f * flash, p);
                }
                break;
            }
        }
        c.restore();
    }

    public static void drawIcon(Canvas c, Paint p, int type, float cx, float cy, float r) {
        p.setShader(null);
        p.setColor(0xFF6D4C41);
        c.drawCircle(cx, cy, r, p);
        p.setColor(0xFF8D6E63);
        c.drawCircle(cx, cy, r * 0.9f, p);
        RadialGradient g = new RadialGradient(cx - r * 0.25f, cy - r * 0.25f, r,
                BODY_COLOR[type], BODY_COLOR2[type], Shader.TileMode.CLAMP);
        p.setShader(g);
        c.drawCircle(cx, cy, r * 0.7f, p);
        p.setShader(null);
        p.setColor(0x55FFFFFF);
        c.drawCircle(cx - r * 0.25f, cy - r * 0.3f, r * 0.25f, p);

        // small detail per type
        p.setColor(Color.BLACK);
        switch (type) {
            case Game.T_DART:
                c.drawRect(cx, cy - r * 0.1f, cx + r * 0.9f, cy + r * 0.1f, p);
                break;
            case Game.T_TACK:
                for (int i = 0; i < 8; i++) {
                    c.save(); c.rotate(i * 45, cx, cy);
                    c.drawRect(cx + r * 0.25f, cy - r * 0.07f, cx + r * 0.85f, cy + r * 0.07f, p);
                    c.restore();
                }
                break;
            case Game.T_BOMB:
                p.setColor(0xFF263238);
                c.drawCircle(cx, cy, r * 0.32f, p);
                break;
            case Game.T_SNIPER:
                p.setColor(0xFF1A237E);
                c.drawRect(cx - r * 0.55f, cy - r * 0.1f, cx + r * 1.0f, cy + r * 0.1f, p);
                break;
            case Game.T_NINJA:
                p.setColor(0xFFE53935);
                c.drawRect(cx - r * 0.3f, cy - r * 0.15f, cx + r * 0.3f, cy - r * 0.05f, p);
                p.setColor(0xFFB0BEC5);
                c.drawCircle(cx + r * 0.4f, cy - r * 0.2f, r * 0.12f, p);
                break;
            case Game.T_ICE:
                p.setColor(0xFFB3E5FC);
                c.drawCircle(cx, cy, r * 0.4f, p);
                p.setColor(0xFF0277BD);
                for (int i = 0; i < 6; i++) {
                    c.save(); c.rotate(i * 60, cx, cy);
                    c.drawRect(cx + r * 0.05f, cy - r * 0.04f, cx + r * 0.5f, cy + r * 0.04f, p);
                    c.restore();
                }
                break;
            case Game.T_SUPER:
                p.setColor(0xFFE53935);
                c.drawCircle(cx - r * 0.15f, cy + r * 0.05f, r * 0.5f, p);
                p.setColor(0xFFFFEB3B);
                c.drawCircle(cx, cy, r * 0.4f, p);
                p.setColor(0xFF1A237E);
                c.drawRect(cx - r * 0.1f, cy - r * 0.05f, cx + r * 0.2f, cy + r * 0.05f, p);
                break;
            case Game.T_WIZARD:
                p.setColor(0xFF311B92);
                android.graphics.Path tri = new android.graphics.Path();
                tri.moveTo(cx - r * 0.35f, cy - r * 0.2f);
                tri.lineTo(cx + r * 0.35f, cy - r * 0.2f);
                tri.lineTo(cx, cy - r * 0.9f);
                tri.close();
                c.drawPath(tri, p);
                p.setColor(0xFFFFEB3B);
                c.drawCircle(cx, cy - r * 0.5f, r * 0.08f, p);
                break;
        }
    }
}
