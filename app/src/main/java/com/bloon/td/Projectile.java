package com.bloon.td;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

import java.util.HashSet;
import java.util.Set;

public class Projectile {
    public static final int K_DART = 0;
    public static final int K_TACK = 1;
    public static final int K_BOMB = 2;
    public static final int K_EXPLOSION = 3;
    public static final int K_BEAM = 4;
    public static final int K_SHURIKEN = 5;
    public static final int K_ICE_PULSE = 6;
    public static final int K_LASER = 7;
    public static final int K_PLASMA = 8;
    public static final int K_MAGIC = 9;
    public static final int K_FIRE = 10;
    public static final int K_GLUE = 11;
    public static final int K_BOOMERANG = 12;
    public static final int K_MORTAR = 13;

    float x, y;
    float vx, vy;
    float life;
    float lifeMax;
    int damage;
    int pierceLeft;
    int kind;
    int color = 0xFFFFFFFF;
    int trailColor = 0xFFBDBDBD;
    float size = 6;
    boolean dead = false;
    float angle = 0f;
    boolean leadPop = false;
    boolean slow = false;
    boolean homing = false;
    float glueDuration = 0f;
    // bomb
    float explosionRadius = 0;
    int explosionDamage = 0;
    int explosionPierce = 0;
    // beam
    float beamX2, beamY2;
    // boomerang
    float boomerangAngle = 0f;
    float boomerangT = 0f;
    float originX, originY;
    // mortar
    float targetX, targetY;
    // pierce memory
    final Set<Bloon> hitSet = new HashSet<>();

    public void update(float dt, Game g) {
        if (kind == K_BEAM || kind == K_EXPLOSION || kind == K_ICE_PULSE) {
            life -= dt;
            if (life <= 0) dead = true;
            return;
        }
        if (kind == K_MORTAR) {
            // life starts at lifeMax (e.g. 1.2s), decrements; impact at 0
            life -= dt;
            if (life <= 0) {
                // explode at target
                x = targetX; y = targetY;
                explode(g);
                dead = true;
                return;
            }
            // arc travel: visualization handled in draw
            return;
        }
        life -= dt;
        if (life <= 0) {
            if (kind == K_BOMB) explode(g);
            dead = true;
            return;
        }
        if (kind == K_BOOMERANG) {
            boomerangT += dt;
            // boomerang: half arc out then back. 0..life/2 out, life/2..life back.
            float total = 1.8f;
            // velocity rotates: forward arc
            float arcSpeed = (float) Math.sqrt(vx * vx + vy * vy);
            if (arcSpeed < 0.01f) { dead = true; return; }
            // simple curve: rotate the angle gradually toward origin in second half
            float pct = Math.max(0, Math.min(1, life / total));
            // adjust velocity: pull toward origin during return phase
            if (life < total * 0.5f) {
                // return phase
                float dxO = originX - x;
                float dyO = originY - y;
                float lO = (float) Math.sqrt(dxO * dxO + dyO * dyO);
                if (lO > 0.01f) {
                    vx = vx * 0.86f + (dxO / lO) * arcSpeed * 0.18f;
                    vy = vy * 0.86f + (dyO / lO) * arcSpeed * 0.18f;
                    // renormalize speed
                    float s = (float) Math.sqrt(vx * vx + vy * vy);
                    if (s > 0.01f) { vx = vx / s * arcSpeed; vy = vy / s * arcSpeed; }
                }
            }
            x += vx * dt;
            y += vy * dt;
            angle += dt * 12f;
            for (int i = 0; i < g.bloons.size(); i++) {
                Bloon b = g.bloons.get(i);
                if (b == null || b.dead || hitSet.contains(b) || b.spawnDelay > 0) continue;
                float dx = b.pos.x - x, dy = b.pos.y - y;
                float rr = b.radius + size;
                if (dx * dx + dy * dy <= rr * rr) {
                    b.hit(damage, K_SHURIKEN, leadPop, g);
                    hitSet.add(b);
                    pierceLeft--;
                    if (pierceLeft <= 0) { dead = true; return; }
                }
            }
            return;
        }

        if (homing) {
            Bloon nearest = null;
            float nd = 9999;
            for (int i = 0; i < g.bloons.size(); i++) {
                Bloon b = g.bloons.get(i);
                if (b == null || b.dead || hitSet.contains(b) || b.spawnDelay > 0) continue;
                float d = Game.dist(b.pos.x, b.pos.y, x, y);
                if (d < 250 && d < nd) { nd = d; nearest = b; }
            }
            if (nearest != null) {
                float dx = nearest.pos.x - x, dy = nearest.pos.y - y;
                float l = (float) Math.sqrt(dx * dx + dy * dy);
                if (l > 0.01f) {
                    float speed = (float) Math.sqrt(vx * vx + vy * vy);
                    float tx = dx / l * speed;
                    float ty = dy / l * speed;
                    vx = vx * 0.85f + tx * 0.15f;
                    vy = vy * 0.85f + ty * 0.15f;
                    float ns = (float) Math.sqrt(vx * vx + vy * vy);
                    if (ns > 0.01f) { vx = vx / ns * speed; vy = vy / ns * speed; }
                }
            }
        }

        x += vx * dt;
        y += vy * dt;
        angle = (float) Math.atan2(vy, vx);

        if (x < -50 || x > g.playW + 50 || y < g.hudH - 50 || y > g.hudH + g.playH + 50) {
            if (kind == K_BOMB) explode(g);
            dead = true;
            return;
        }
        for (int i = 0; i < g.bloons.size(); i++) {
            Bloon b = g.bloons.get(i);
            if (b == null || b.dead || hitSet.contains(b) || b.spawnDelay > 0) continue;
            float dx = b.pos.x - x, dy = b.pos.y - y;
            float rr = (b.radius + size);
            if (dx * dx + dy * dy <= rr * rr) {
                if (kind == K_BOMB) {
                    explode(g);
                    dead = true;
                    return;
                }
                if (kind == K_GLUE) {
                    b.freezeTimer = Math.max(b.freezeTimer, glueDuration);
                    b.glued = true;
                    b.hit(damage, kind, leadPop, g);
                } else {
                    b.hit(damage, kind, leadPop, g);
                    if (slow) b.freezeTimer = Math.max(b.freezeTimer, 0.6f);
                }
                hitSet.add(b);
                pierceLeft--;
                if (pierceLeft <= 0) { dead = true; return; }
            }
        }
    }

    void explode(Game g) {
        Projectile fx = new Projectile();
        fx.kind = K_EXPLOSION;
        fx.x = x; fx.y = y;
        fx.life = 0.35f;
        fx.explosionRadius = explosionRadius;
        g.pendingProjectiles.add(fx);

        int p = explosionPierce;
        for (int i = 0; i < g.bloons.size(); i++) {
            Bloon b = g.bloons.get(i);
            if (b == null || b.dead) continue;
            float dx = b.pos.x - x, dy = b.pos.y - y;
            float er = explosionRadius + b.radius;
            if (dx * dx + dy * dy <= er * er) {
                b.hit(explosionDamage, K_EXPLOSION, leadPop, g);
                if (--p <= 0) break;
            }
        }
    }

    public void draw(Canvas c, Paint p) {
        switch (kind) {
            case K_BEAM: {
                int alpha = (int) ((life / 0.15f) * 255);
                if (alpha < 0) alpha = 0; if (alpha > 255) alpha = 255;
                p.setColor((alpha << 24) | (color & 0xFFFFFF));
                p.setStrokeWidth(4);
                p.setStyle(Paint.Style.STROKE);
                c.drawLine(x, y, beamX2, beamY2, p);
                p.setStrokeWidth(8);
                p.setColor(((alpha / 2) << 24) | (color & 0xFFFFFF));
                c.drawLine(x, y, beamX2, beamY2, p);
                p.setStyle(Paint.Style.FILL);
                return;
            }
            case K_EXPLOSION: {
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
            case K_ICE_PULSE: {
                float t = 1f - (life / 0.45f);
                float r = explosionRadius * (0.2f + t * 1.0f);
                int alpha = (int) (180 * (1 - t));
                if (alpha < 0) alpha = 0;
                p.setColor((alpha << 24) | 0xB3E5FC);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(6);
                c.drawCircle(x, y, r, p);
                p.setStrokeWidth(3);
                p.setColor((alpha << 24) | 0x4FC3F7);
                c.drawCircle(x, y, r * 0.8f, p);
                p.setStyle(Paint.Style.FILL);
                return;
            }
            case K_DART: {
                p.setColor(trailColor);
                c.drawCircle(x - vx * 0.012f, y - vy * 0.012f, size * 0.55f, p);
                c.save();
                c.rotate((float) Math.toDegrees(angle), x, y);
                p.setColor(0xFF6D4C41);
                c.drawRect(x - size * 0.9f, y - size * 0.15f, x + size * 0.6f, y + size * 0.15f, p);
                p.setColor(0xFFEEEEEE);
                Path tri = new Path();
                tri.moveTo(x + size * 0.6f, y - size * 0.4f);
                tri.lineTo(x + size * 1.1f, y);
                tri.lineTo(x + size * 0.6f, y + size * 0.4f);
                tri.close();
                c.drawPath(tri, p);
                p.setColor(0xFFE53935);
                c.drawRect(x - size * 0.9f, y - size * 0.35f, x - size * 0.55f, y + size * 0.35f, p);
                c.restore();
                return;
            }
            case K_TACK: {
                p.setColor(trailColor);
                c.drawCircle(x, y, size * 0.85f, p);
                p.setColor(color);
                c.drawCircle(x, y, size * 0.55f, p);
                p.setColor(0xFF424242);
                c.save();
                c.rotate((float) Math.toDegrees(angle), x, y);
                c.drawRect(x - size * 0.9f, y - size * 0.12f, x + size * 0.9f, y + size * 0.12f, p);
                c.restore();
                return;
            }
            case K_FIRE: {
                p.setColor(0x88FF3D00);
                c.drawCircle(x, y, size * 1.4f, p);
                p.setColor(0xCCFFA000);
                c.drawCircle(x, y, size * 1.0f, p);
                p.setColor(0xFFFFEB3B);
                c.drawCircle(x, y, size * 0.6f, p);
                return;
            }
            case K_BOMB: {
                p.setColor(trailColor);
                c.drawCircle(x - vx * 0.015f, y - vy * 0.015f, size * 0.7f, p);
                p.setColor(0xFF212121);
                c.drawCircle(x, y, size, p);
                p.setColor(0xFF424242);
                c.drawCircle(x - size * 0.3f, y - size * 0.3f, size * 0.4f, p);
                p.setColor(0xFFFFEB3B);
                c.drawCircle(x, y - size * 0.95f, size * 0.35f, p);
                p.setColor(0xFFFF6F00);
                c.drawCircle(x, y - size * 1.05f, size * 0.18f, p);
                return;
            }
            case K_SHURIKEN: {
                c.save();
                c.rotate((float) Math.toDegrees(angle) + life * 600, x, y);
                p.setColor(color);
                Path star = new Path();
                for (int i = 0; i < 8; i++) {
                    double a = i * Math.PI / 4;
                    double r = (i % 2 == 0) ? size : size * 0.4;
                    float px = (float) (x + Math.cos(a) * r);
                    float py = (float) (y + Math.sin(a) * r);
                    if (i == 0) star.moveTo(px, py); else star.lineTo(px, py);
                }
                star.close();
                c.drawPath(star, p);
                p.setColor(0xFFCFD8DC);
                c.drawCircle(x, y, size * 0.3f, p);
                c.restore();
                return;
            }
            case K_LASER: {
                p.setColor(0x55FF1744);
                c.drawCircle(x, y, size * 1.5f, p);
                p.setColor(0xFFFFCDD2);
                c.drawCircle(x, y, size * 0.6f, p);
                p.setColor(0xFFFF1744);
                c.save();
                c.rotate((float) Math.toDegrees(angle), x, y);
                c.drawRect(x - size * 1.5f, y - size * 0.25f, x + size * 1.5f, y + size * 0.25f, p);
                c.restore();
                return;
            }
            case K_PLASMA: {
                p.setColor(0x66FFEB3B);
                c.drawCircle(x, y, size * 1.4f, p);
                p.setColor(0xFFFFEB3B);
                c.drawCircle(x, y, size * 0.9f, p);
                p.setColor(0xFFFFFFFF);
                c.drawCircle(x - size * 0.3f, y - size * 0.3f, size * 0.4f, p);
                return;
            }
            case K_MAGIC: {
                p.setColor(0x66E040FB);
                c.drawCircle(x, y, size * 1.5f, p);
                p.setColor(0xFFE040FB);
                c.drawCircle(x, y, size * 0.9f, p);
                p.setColor(0xFFFFFFFF);
                c.drawCircle(x, y, size * 0.4f, p);
                p.setColor(0xCCE1BEE7);
                for (int i = 0; i < 4; i++) {
                    double a = life * 8 + i * Math.PI / 2;
                    float px = (float) (x + Math.cos(a) * size * 1.6);
                    float py = (float) (y + Math.sin(a) * size * 1.6);
                    c.drawCircle(px, py, size * 0.2f, p);
                }
                return;
            }
            case K_GLUE: {
                p.setColor(0x88FFEB3B);
                c.drawCircle(x, y, size * 1.4f, p);
                p.setColor(0xCCFFF59D);
                c.drawCircle(x, y, size * 0.95f, p);
                p.setColor(0xFFFFFFFF);
                c.drawCircle(x - size * 0.3f, y - size * 0.3f, size * 0.3f, p);
                // sticky drip
                p.setColor(0x66FFF176);
                c.drawCircle(x, y + size * 1.0f, size * 0.4f, p);
                return;
            }
            case K_BOOMERANG: {
                c.save();
                c.rotate((float) Math.toDegrees(angle), x, y);
                p.setColor(0xFF5D4037);
                Path boom = new Path();
                boom.moveTo(x - size, y);
                boom.quadTo(x - size * 0.7f, y - size * 1.1f, x + size, y - size * 0.5f);
                boom.quadTo(x + size * 0.3f, y, x + size, y + size * 0.5f);
                boom.quadTo(x - size * 0.7f, y + size * 1.1f, x - size, y);
                boom.close();
                c.drawPath(boom, p);
                p.setColor(0xFF8D6E63);
                Path inner = new Path();
                inner.moveTo(x - size * 0.6f, y);
                inner.quadTo(x - size * 0.4f, y - size * 0.7f, x + size * 0.6f, y - size * 0.3f);
                inner.quadTo(x, y, x + size * 0.6f, y + size * 0.3f);
                inner.quadTo(x - size * 0.4f, y + size * 0.7f, x - size * 0.6f, y);
                inner.close();
                c.drawPath(inner, p);
                c.restore();
                return;
            }
            case K_MORTAR: {
                // arc path visualization: parametric from origin (originX,originY) to target
                float t = 1f - life / Math.max(0.01f, lifeMax);
                float startX = (originX != 0 || originY != 0) ? originX : x;
                float startY = (originX != 0 || originY != 0) ? originY : y;
                // we never set origin for K_MORTAR, so use targetX/Y backtrack via initial pos:
                // The actual position is interpolated linearly with an arc.
                // We compute current visible position:
                // approximation: linear interp + sine arc bump
                // We can't easily know start position - rely on origin (set when fired) - let's set in fire if needed.
                float curX, curY;
                if (originX == 0 && originY == 0) {
                    curX = x; curY = y;
                } else {
                    curX = startX + (targetX - startX) * t;
                    curY = startY + (targetY - startY) * t - (float) Math.sin(t * Math.PI) * 220;
                }
                p.setColor(0xFF263238);
                c.drawCircle(curX, curY, size, p);
                p.setColor(0xFF455A64);
                c.drawCircle(curX - size * 0.3f, curY - size * 0.3f, size * 0.4f, p);
                p.setColor(0x44000000);
                c.drawOval(targetX - size, targetY - size * 0.4f, targetX + size, targetY + size * 0.4f, p);
                p.setColor(0x88E53935);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(3);
                c.drawCircle(targetX, targetY, explosionRadius * 0.3f * (0.5f + t), p);
                p.setStyle(Paint.Style.FILL);
                return;
            }
        }
    }
}
