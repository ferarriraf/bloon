package com.bloon.td;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

public class Tower {
    public static final int TYPE_COUNT = 8;

    int type;
    float x, y;
    int gridX, gridY;
    final int[] tiers = new int[2];
    float cooldown = 0f;
    float aimAngle = 0f;
    int totalSpent = 0;
    float fireAnim = 0f;

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
            for (int t = 0; t < tiers[p]; t++) {
                UpgradeDef u = UpgradeDef.TREE[type][p][t];
                if (u != null) mul *= u.rangeMult;
            }
        return r * mul;
    }
    public float rate() {
        float r = baseRate();
        float mul = 1f;
        for (int p = 0; p < 2; p++)
            for (int t = 0; t < tiers[p]; t++) {
                UpgradeDef u = UpgradeDef.TREE[type][p][t];
                if (u != null) mul *= u.rateMult;
            }
        return r * mul;
    }
    public int damage() {
        int d = baseDamage();
        for (int p = 0; p < 2; p++)
            for (int t = 0; t < tiers[p]; t++) {
                UpgradeDef u = UpgradeDef.TREE[type][p][t];
                if (u != null) d += u.dmgAdd;
            }
        return d;
    }
    public int pierce() {
        int v = basePierce();
        for (int p = 0; p < 2; p++)
            for (int t = 0; t < tiers[p]; t++) {
                UpgradeDef u = UpgradeDef.TREE[type][p][t];
                if (u != null) v += u.pierceAdd;
            }
        return v;
    }
    public float projSpeed() {
        float v = baseProjSpeed();
        for (int p = 0; p < 2; p++)
            for (int t = 0; t < tiers[p]; t++) {
                UpgradeDef u = UpgradeDef.TREE[type][p][t];
                if (u != null) v *= u.projSpeedMult;
            }
        return v;
    }

    public boolean has(int specialId) {
        for (int p = 0; p < 2; p++)
            for (int t = 0; t < tiers[p]; t++) {
                UpgradeDef u = UpgradeDef.TREE[type][p][t];
                if (u != null && u.special == specialId) return true;
            }
        return false;
    }

    public int tierSum() { return tiers[0] + tiers[1]; }

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
        cooldown = 1f / Math.max(0.01f, rate());
        fire(target, g);
        fireAnim = 1f;
    }

    Bloon findTarget(Game g) {
        Bloon best = null;
        float bestD = -1;
        float r = range();
        for (int i = 0; i < g.bloons.size(); i++) {
            Bloon b = g.bloons.get(i);
            if (b == null || b.dead || b.spawnDelay > 0) continue;
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
                    p.leadPop = leadPop; p.angle = a;
                    g.projectiles.add(p);
                }
                break;
            }
            case Game.T_TACK: {
                int n = 8 + tierSum() * 2;
                boolean fire = has(UpgradeDef.SP_LEAD_POP) && tiers[1] >= 2;
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
                    p.leadPop = leadPop; p.angle = a;
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
                int hits = (tiers[1] >= 2) ? 3 : 1;
                Bloon t = target;
                for (int i = 0; i < hits && t != null; i++) {
                    if (t.dead) break;
                    t.hit(dmg, Projectile.K_DART, leadPop, g);
                    Projectile p = new Projectile();
                    p.x = x; p.y = y;
                    p.kind = Projectile.K_BEAM;
                    p.beamX2 = t.pos.x; p.beamY2 = t.pos.y;
                    p.life = 0.15f; p.color = 0xFFFFEB3B;
                    g.projectiles.add(p);
                    Bloon nb = null;
                    float bestD = 9999;
                    for (int j = 0; j < g.bloons.size(); j++) {
                        Bloon b2 = g.bloons.get(j);
                        if (b2 == null || b2 == t || b2.dead) continue;
                        float d = Game.dist(b2.pos.x, b2.pos.y, t.pos.x, t.pos.y);
                        if (d < 260 && d < bestD) { bestD = d; nb = b2; }
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
                    p.slow = slow; p.angle = a;
                    g.projectiles.add(p);
                }
                break;
            }
            case Game.T_ICE: {
                float r = range();
                float freezeDur = 1.0f + tierSum() * 0.4f;
                int n = 0;
                for (int i = 0; i < g.bloons.size(); i++) {
                    Bloon b = g.bloons.get(i);
                    if (b == null || b.dead || b.spawnDelay > 0) continue;
                    if (Game.dist(b.pos.x, b.pos.y, x, y) <= r) {
                        b.freezeTimer = Math.max(b.freezeTimer, freezeDur);
                        if (dmg > 0) b.hit(dmg, Projectile.K_DART, leadPop, g);
                        n++;
                        if (n > pierce && pierce < 1000) break;
                    }
                }
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
                    p.leadPop = true; p.angle = aimAngle;
                    g.projectiles.add(p);
                } else if (has(UpgradeDef.SP_LASER)) {
                    Projectile p = new Projectile();
                    p.x = x; p.y = y;
                    p.vx = nx * ps * 1.4f; p.vy = ny * ps * 1.4f;
                    p.kind = Projectile.K_LASER;
                    p.damage = dmg; p.pierceLeft = pierce + 3;
                    p.life = 0.7f; p.size = 12;
                    p.color = 0xFFFF1744; p.trailColor = 0xFFFF8A80;
                    p.leadPop = true; p.angle = aimAngle;
                    g.projectiles.add(p);
                } else {
                    Projectile p = new Projectile();
                    p.x = x; p.y = y;
                    p.vx = nx * ps; p.vy = ny * ps;
                    p.kind = Projectile.K_DART;
                    p.damage = dmg; p.pierceLeft = pierce;
                    p.life = 1.2f; p.size = 9;
                    p.color = 0xFFFFEB3B; p.trailColor = 0xFFFFC107;
                    p.angle = aimAngle; p.homing = homing;
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
                p.leadPop = true; p.homing = homing;
                p.angle = aimAngle;
                g.projectiles.add(p);
                break;
            }
        }
    }

    public void draw(Canvas c, Paint p, Game g) {
        Bitmap base = g.towerBmp[type];
        Bitmap gun = g.towerGunBmp[type];
        float dstSize = g.tile * 1.1f;
        RectF dst = new RectF(x - dstSize / 2, y - dstSize / 2, x + dstSize / 2, y + dstSize / 2);
        if (base != null) c.drawBitmap(base, null, dst, p);
        if (gun != null) {
            c.save();
            c.rotate((float) Math.toDegrees(aimAngle), x, y);
            c.drawBitmap(gun, null, dst, p);
            c.restore();
        }
        // fire flash overlay
        if (fireAnim > 0) {
            p.setColor((((int) (180 * fireAnim)) << 24) | 0xFFEB3B);
            c.save();
            c.rotate((float) Math.toDegrees(aimAngle), x, y);
            c.drawCircle(x + g.tile * 0.5f, y, g.tile * 0.15f * fireAnim, p);
            c.restore();
        }
        // upgrade tier pips on top (path A) and bottom (path B)
        for (int pp = 0; pp < 2; pp++) {
            for (int t = 0; t < 3; t++) {
                boolean owned = tiers[pp] > t;
                p.setColor(owned ? (pp == 0 ? 0xFFFFEB3B : 0xFF40C4FF) : 0x55000000);
                float py = y + g.tile * (pp == 0 ? -0.55f : 0.48f);
                float px = x - g.tile * 0.22f + t * g.tile * 0.22f;
                c.drawCircle(px, py, g.tile * 0.07f, p);
                p.setColor(0xFF263238);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(2);
                c.drawCircle(px, py, g.tile * 0.07f, p);
                p.setStyle(Paint.Style.FILL);
            }
        }
    }

    public static int darken(int c, float t) {
        int a = (c >> 24) & 0xFF;
        int r = (int) (((c >> 16) & 0xFF) * t);
        int g = (int) (((c >> 8) & 0xFF) * t);
        int b = (int) ((c & 0xFF) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
