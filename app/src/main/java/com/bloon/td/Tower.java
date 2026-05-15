package com.bloon.td;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

public class Tower {
    int type;
    float x, y;
    int gridX, gridY;
    int level = 1;
    float cooldown = 0f;
    float aimAngle = 0f;
    int totalSpent = 0;

    static final int[] LEVEL_RANGE_BONUS = {0, 0, 12, 24, 40};
    static final float[] LEVEL_RATE_MULT = {0f, 1f, 1.4f, 1.9f, 2.6f};
    static final int[] LEVEL_DMG_BONUS = {0, 0, 0, 1, 2};
    static final int[] LEVEL_PIERCE_BONUS = {0, 0, 1, 2, 3};

    public Tower(int type, float x, float y, int gx, int gy) {
        this.type = type;
        this.x = x; this.y = y;
        this.gridX = gx; this.gridY = gy;
    }

    public static float range(int type, int level) {
        float base;
        switch (type) {
            case Game.T_DART:   base = 260; break;
            case Game.T_TACK:   base = 180; break;
            case Game.T_BOMB:   base = 280; break;
            case Game.T_SNIPER: base = 9999; break;
            default: base = 200;
        }
        return base + LEVEL_RANGE_BONUS[level] * 10;
    }
    public float range() { return range(type, level); }

    float fireRate() {
        // shots per second baseline
        float base;
        switch (type) {
            case Game.T_DART:   base = 1.4f; break;
            case Game.T_TACK:   base = 0.7f; break;
            case Game.T_BOMB:   base = 0.55f; break;
            case Game.T_SNIPER: base = 0.55f; break;
            default: base = 1f;
        }
        return base * LEVEL_RATE_MULT[level];
    }
    int damage() {
        int base;
        switch (type) {
            case Game.T_DART:   base = 1; break;
            case Game.T_TACK:   base = 1; break;
            case Game.T_BOMB:   base = 2; break;
            case Game.T_SNIPER: base = 4; break;
            default: base = 1;
        }
        return base + LEVEL_DMG_BONUS[level];
    }
    int pierce() {
        int base;
        switch (type) {
            case Game.T_DART:   base = 2; break;
            case Game.T_TACK:   base = 1; break;
            case Game.T_BOMB:   base = 6; break;
            case Game.T_SNIPER: base = 1; break;
            default: base = 1;
        }
        return base + LEVEL_PIERCE_BONUS[level];
    }

    public void update(float dt, Game g) {
        cooldown -= dt;
        if (cooldown > 0) return;
        Bloon target = findTarget(g);
        if (target == null) return;
        cooldown = 1f / fireRate();
        fire(target, g);
    }

    Bloon findTarget(Game g) {
        Bloon best = null;
        float bestD = -1;
        float r = range();
        for (Bloon b : g.bloons) {
            if (b.dead || b.spawnDelay > 0) continue;
            float d = Game.dist(b.pos.x, b.pos.y, x, y);
            if (d <= r) {
                // prefer furthest along path
                if (b.dAlong > bestD) {
                    bestD = b.dAlong;
                    best = b;
                }
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
        switch (type) {
            case Game.T_DART: {
                Projectile p = new Projectile();
                p.x = x; p.y = y;
                p.vx = nx * 900; p.vy = ny * 900;
                p.kind = Projectile.K_SHARP;
                p.damage = damage();
                p.pierceLeft = pierce();
                p.life = 1.0f;
                p.size = 10;
                p.color = 0xFFFFFFFF;
                p.trailColor = 0xFFE0E0E0;
                g.projectiles.add(p);
                break;
            }
            case Game.T_TACK: {
                int n = 8 + (level - 1) * 2;
                for (int i = 0; i < n; i++) {
                    float a = (float) (Math.PI * 2 * i / n);
                    Projectile p = new Projectile();
                    p.x = x; p.y = y;
                    p.vx = (float) Math.cos(a) * 700;
                    p.vy = (float) Math.sin(a) * 700;
                    p.kind = Projectile.K_SHARP;
                    p.damage = damage();
                    p.pierceLeft = pierce();
                    p.life = range() / 700f;
                    p.size = 7;
                    p.color = 0xFFFAFAFA;
                    p.trailColor = 0xFFBDBDBD;
                    g.projectiles.add(p);
                }
                break;
            }
            case Game.T_BOMB: {
                Projectile p = new Projectile();
                p.x = x; p.y = y;
                p.vx = nx * 600; p.vy = ny * 600;
                p.kind = Projectile.K_BOMB;
                p.damage = damage();
                p.pierceLeft = 9999;
                p.life = 1.8f;
                p.size = 14;
                p.color = 0xFF424242;
                p.trailColor = 0xFFFF6F00;
                p.explosionRadius = 80 + level * 8;
                p.explosionDamage = damage() + 1;
                p.explosionPierce = pierce();
                g.projectiles.add(p);
                break;
            }
            case Game.T_SNIPER: {
                // instant hit
                target.hit(damage() + 2, Projectile.K_SHARP, g);
                Projectile p = new Projectile();
                p.x = x; p.y = y;
                p.vx = 0; p.vy = 0;
                p.kind = Projectile.K_BEAM;
                p.beamX2 = target.pos.x;
                p.beamY2 = target.pos.y;
                p.life = 0.15f;
                p.color = 0xFFFFEB3B;
                g.projectiles.add(p);
                break;
            }
        }
    }

    public void draw(Canvas c, Paint p, Game g) {
        float r = g.tile * 0.42f;
        // base
        p.setColor(0xFF6D4C41);
        c.drawCircle(x, y, r, p);
        p.setColor(0xFF8D6E63);
        c.drawCircle(x, y, r * 0.85f, p);
        // body color
        int col = bodyColor(type);
        p.setColor(col);
        c.drawCircle(x, y, r * 0.65f, p);
        // gun
        p.setStyle(Paint.Style.FILL);
        c.save();
        c.rotate((float) Math.toDegrees(aimAngle), x, y);
        if (type == Game.T_BOMB) {
            p.setColor(0xFF263238);
            c.drawRect(x, y - r * 0.18f, x + r * 0.95f, y + r * 0.18f, p);
            p.setColor(0xFF455A64);
            c.drawCircle(x + r * 0.95f, y, r * 0.22f, p);
        } else if (type == Game.T_TACK) {
            p.setColor(0xFF424242);
            for (int i = 0; i < 8; i++) {
                c.save();
                c.rotate(i * 45, x, y);
                c.drawRect(x + r * 0.3f, y - r * 0.08f, x + r * 0.9f, y + r * 0.08f, p);
                c.restore();
            }
        } else if (type == Game.T_SNIPER) {
            p.setColor(0xFF1A237E);
            c.drawRect(x, y - r * 0.12f, x + r * 1.1f, y + r * 0.12f, p);
        } else {
            p.setColor(0xFF424242);
            c.drawRect(x, y - r * 0.12f, x + r * 0.9f, y + r * 0.12f, p);
        }
        c.restore();
        // level pips
        p.setColor(0xFFFFEB3B);
        for (int i = 0; i < level; i++) {
            c.drawCircle(x - r * 0.6f + i * r * 0.3f, y + r * 0.85f, r * 0.1f, p);
        }
    }

    static int bodyColor(int t) {
        switch (t) {
            case Game.T_DART: return 0xFF9CCC65;
            case Game.T_TACK: return 0xFFFFA726;
            case Game.T_BOMB: return 0xFFE53935;
            case Game.T_SNIPER: return 0xFF7E57C2;
            default: return 0xFF9E9E9E;
        }
    }

    public static void drawIcon(Canvas c, Paint p, int type, float cx, float cy, float r) {
        p.setColor(0xFF8D6E63);
        c.drawCircle(cx, cy, r, p);
        p.setColor(bodyColor(type));
        c.drawCircle(cx, cy, r * 0.65f, p);
        p.setColor(Color.BLACK);
        if (type == Game.T_DART) {
            c.drawRect(cx, cy - r * 0.1f, cx + r * 0.9f, cy + r * 0.1f, p);
        } else if (type == Game.T_TACK) {
            for (int i = 0; i < 8; i++) {
                c.save(); c.rotate(i * 45, cx, cy);
                c.drawRect(cx + r * 0.25f, cy - r * 0.07f, cx + r * 0.9f, cy + r * 0.07f, p);
                c.restore();
            }
        } else if (type == Game.T_BOMB) {
            c.drawCircle(cx, cy, r * 0.32f, p);
        } else if (type == Game.T_SNIPER) {
            c.drawRect(cx - r * 0.5f, cy - r * 0.1f, cx + r * 1.0f, cy + r * 0.1f, p);
        }
    }
}
