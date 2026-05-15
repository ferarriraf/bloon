package com.bloon.td;

import android.graphics.Canvas;
import android.graphics.Paint;

import java.util.HashSet;
import java.util.Set;

public class Projectile {
    public static final int K_SHARP = 0;
    public static final int K_BOMB = 1;
    public static final int K_EXPLOSION = 2;
    public static final int K_BEAM = 3;

    float x, y;
    float vx, vy;
    float life;
    int damage;
    int pierceLeft;
    int kind;
    int color = 0xFFFFFFFF;
    int trailColor = 0xFFBDBDBD;
    float size = 6;
    boolean dead = false;
    // bomb
    float explosionRadius = 0;
    int explosionDamage = 0;
    int explosionPierce = 0;
    // beam
    float beamX2, beamY2;
    // pierce memory
    final Set<Bloon> hitSet = new HashSet<>();

    public void update(float dt, Game g) {
        life -= dt;
        if (life <= 0) {
            if (kind == K_BOMB) explode(g);
            dead = true;
            return;
        }
        if (kind == K_BEAM) return;

        x += vx * dt;
        y += vy * dt;
        if (x < -50 || x > g.screenW + 50 || y < g.topPad - 50 || y > g.topPad + g.rows * g.tile + 50) {
            if (kind == K_BOMB) explode(g);
            dead = true;
            return;
        }
        // collide
        for (Bloon b : g.bloons) {
            if (b.dead || hitSet.contains(b) || b.spawnDelay > 0) continue;
            float dx = b.pos.x - x, dy = b.pos.y - y;
            float rr = (b.radius + size);
            if (dx * dx + dy * dy <= rr * rr) {
                if (kind == K_BOMB) {
                    explode(g);
                    dead = true;
                    return;
                }
                b.hit(damage, kind, g);
                hitSet.add(b);
                pierceLeft--;
                if (pierceLeft <= 0) {
                    dead = true;
                    return;
                }
            }
        }
    }

    void explode(Game g) {
        // visual
        Projectile fx = new Projectile();
        fx.kind = K_EXPLOSION;
        fx.x = x; fx.y = y;
        fx.size = 0;
        fx.life = 0.35f;
        fx.explosionRadius = explosionRadius;
        g.projectiles.add(fx);

        // damage all bloons in radius
        int p = explosionPierce;
        for (Bloon b : g.bloons) {
            if (b.dead) continue;
            float dx = b.pos.x - x, dy = b.pos.y - y;
            float er = explosionRadius + b.radius;
            if (dx * dx + dy * dy <= er * er) {
                b.hit(explosionDamage, K_EXPLOSION, g);
                if (--p <= 0) break;
            }
        }
    }

    public void draw(Canvas c, Paint p) {
        if (kind == K_BEAM) {
            p.setColor((int) (((life / 0.15f) * 255)) << 24 | (color & 0xFFFFFF));
            p.setStrokeWidth(3);
            p.setStyle(Paint.Style.STROKE);
            c.drawLine(x, y, beamX2, beamY2, p);
            p.setStyle(Paint.Style.FILL);
            return;
        }
        if (kind == K_EXPLOSION) {
            float t = 1f - (life / 0.35f);
            float r = explosionRadius * (0.3f + t * 1.0f);
            int alpha = (int) (220 * (1 - t));
            if (alpha < 0) alpha = 0;
            p.setColor((alpha << 24) | 0xFF6F00);
            c.drawCircle(x, y, r, p);
            p.setColor((alpha << 24) | 0xFFD740);
            c.drawCircle(x, y, r * 0.65f, p);
            p.setColor((alpha << 24) | 0xFFFFFF);
            c.drawCircle(x, y, r * 0.3f, p);
            return;
        }
        p.setColor(trailColor);
        c.drawCircle(x - vx * 0.01f, y - vy * 0.01f, size * 0.7f, p);
        p.setColor(color);
        c.drawCircle(x, y, size, p);
        if (kind == K_BOMB) {
            p.setColor(0xFFFFEB3B);
            c.drawCircle(x, y - size * 0.6f, size * 0.3f, p);
        }
    }
}
