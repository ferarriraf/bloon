package com.bloon.td;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RadialGradient;
import android.graphics.Shader;

public class Bloon {
    int type;
    int hp;
    int damage;
    float dAlong;
    float radius;
    boolean escaped;
    boolean dead;
    final PointF pos = new PointF();
    boolean leadImmuneSharp = false;
    boolean blackImmuneExplosion = false;
    float dirX = 1, dirY = 0;
    float spawnDelay = 0f;
    float wobble = 0f;
    float freezeTimer = 0f; // when > 0, bloon frozen

    public Bloon(int type) {
        this.type = type;
        this.hp = Game.BLOON_HP[type];
        this.damage = 1;
        if (type == Game.B_BLACK) blackImmuneExplosion = true;
        if (type == Game.B_LEAD) { leadImmuneSharp = true; blackImmuneExplosion = false; }
        if (type == Game.B_MOAB) { damage = 100; this.hp = 200; }
        wobble = (float) (Math.random() * Math.PI * 2);
    }

    public void update(float dt, Game g) {
        if (spawnDelay > 0) { spawnDelay -= dt; return; }
        float speedMult = 1f;
        if (freezeTimer > 0) {
            freezeTimer -= dt;
            speedMult = 0.25f;
        }
        float sp = Game.BLOON_SPEED[type] * g.tile * speedMult;
        dAlong += sp * dt;
        if (dAlong >= g.totalPathLen) { escaped = true; return; }
        PointF prev = new PointF(pos.x, pos.y);
        g.posAlongPath(dAlong, pos);
        float dx = pos.x - prev.x, dy = pos.y - prev.y;
        float l = (float) Math.sqrt(dx * dx + dy * dy);
        if (l > 0.01f) { dirX = dx / l; dirY = dy / l; }
        wobble += dt * 6f;
        // radius
        float base = Game.BLOON_RADIUS[type];
        radius = base * (g.tile / 110f);
        if (radius < 13) radius = 13;
        if (type == Game.B_MOAB) radius = g.tile * 0.62f;
    }

    public void draw(Canvas c, Paint p) {
        float wob = (float) Math.sin(wobble) * radius * 0.04f;
        float drawR = radius + wob;
        // shadow
        p.setShader(null);
        p.setColor(0x44000000);
        c.drawCircle(pos.x + 3, pos.y + 5, drawR * 0.95f, p);

        int col = Game.BLOON_COLOR[type];
        int colDark = darken(col, 0.55f);
        if (type == Game.B_MOAB) {
            // metallic look
            RadialGradient rg = new RadialGradient(pos.x - drawR * 0.35f, pos.y - drawR * 0.4f, drawR * 1.4f,
                    0xFFE1BEE7, 0xFF4A148C, Shader.TileMode.CLAMP);
            p.setShader(rg);
            c.drawCircle(pos.x, pos.y, drawR, p);
            p.setShader(null);
            // panels
            p.setColor(0xFF4A148C);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(3);
            c.drawCircle(pos.x, pos.y, drawR * 0.7f, p);
            c.drawLine(pos.x - drawR * 0.7f, pos.y, pos.x + drawR * 0.7f, pos.y, p);
            p.setStyle(Paint.Style.FILL);
            // engines
            p.setColor(0xFFFF7043);
            c.drawCircle(pos.x - drawR * 0.85f, pos.y, drawR * 0.15f, p);
            c.drawCircle(pos.x - drawR * 0.85f, pos.y, drawR * 0.08f, p);
            p.setColor(0xFFFFEB3B);
            p.setTextSize(drawR * 0.4f);
            c.drawText("MOAB", pos.x - drawR * 0.45f, pos.y + drawR * 0.15f, p);
        } else {
            // body gradient
            RadialGradient rg = new RadialGradient(pos.x - drawR * 0.35f, pos.y - drawR * 0.45f, drawR * 1.3f,
                    lighten(col, 0.4f), colDark, Shader.TileMode.CLAMP);
            p.setShader(rg);
            c.drawCircle(pos.x, pos.y, drawR, p);
            p.setShader(null);
            // outline
            p.setColor(colDark);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2);
            c.drawCircle(pos.x, pos.y, drawR, p);
            p.setStyle(Paint.Style.FILL);
            // highlight
            p.setColor(0x88FFFFFF);
            c.drawCircle(pos.x - drawR * 0.35f, pos.y - drawR * 0.4f, drawR * 0.28f, p);
            p.setColor(0x55FFFFFF);
            c.drawCircle(pos.x - drawR * 0.15f, pos.y - drawR * 0.2f, drawR * 0.15f, p);
            // knot
            p.setColor(colDark);
            android.graphics.Path knot = new android.graphics.Path();
            knot.moveTo(pos.x - drawR * 0.2f, pos.y + drawR);
            knot.lineTo(pos.x + drawR * 0.2f, pos.y + drawR);
            knot.lineTo(pos.x, pos.y + drawR * 1.2f);
            knot.close();
            c.drawPath(knot, p);
        }

        // freeze overlay
        if (freezeTimer > 0) {
            p.setColor(0x60B3E5FC);
            c.drawCircle(pos.x, pos.y, drawR * 1.05f, p);
            p.setColor(0xCC81D4FA);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(3);
            for (int i = 0; i < 3; i++) {
                c.save();
                c.rotate(i * 60, pos.x, pos.y);
                c.drawLine(pos.x - drawR * 0.8f, pos.y, pos.x + drawR * 0.8f, pos.y, p);
                c.restore();
            }
            p.setStyle(Paint.Style.FILL);
        }
    }

    /** apply hit. returns true if popped. */
    public boolean hit(int dmg, int kind, boolean canLeadPop, Game g) {
        if (dead) return false;
        boolean sharp = (kind == Projectile.K_DART || kind == Projectile.K_TACK
                || kind == Projectile.K_SHURIKEN || kind == Projectile.K_LASER
                || kind == Projectile.K_PLASMA);
        if (sharp && leadImmuneSharp && !canLeadPop) return false;
        if (kind == Projectile.K_EXPLOSION && blackImmuneExplosion) return false;
        if (kind == Projectile.K_FIRE && type == Game.B_LEAD && !canLeadPop) return false;

        hp -= dmg;
        if (hp <= 0) {
            g.cash += Game.BLOON_REWARD[type];
            g.score += Game.BLOON_REWARD[type];
            int childType = Game.BLOON_CHILD[type];
            int n = Game.BLOON_CHILD_N[type];
            if (childType >= 0 && n > 0) {
                for (int i = 0; i < n; i++) {
                    Bloon b = new Bloon(childType);
                    b.dAlong = Math.max(0, dAlong - i * 6f);
                    g.pendingBloons.add(b);
                }
            }
            if (type == Game.B_MOAB) {
                for (int i = 0; i < 4; i++) {
                    Bloon b = new Bloon(Game.B_PINK);
                    b.dAlong = Math.max(0, dAlong - i * 12f);
                    g.pendingBloons.add(b);
                }
                g.addFloater("BOOM!", pos.x, pos.y, 0xFFFFEB3B);
            }
            dead = true;
            return true;
        }
        return false;
    }

    static int lighten(int c, float t) {
        int a = (c >> 24) & 0xFF;
        int r = (c >> 16) & 0xFF;
        int g = (c >> 8) & 0xFF;
        int b = c & 0xFF;
        r = (int) (r + (255 - r) * t);
        g = (int) (g + (255 - g) * t);
        b = (int) (b + (255 - b) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
    static int darken(int c, float t) {
        int a = (c >> 24) & 0xFF;
        int r = (int) (((c >> 16) & 0xFF) * t);
        int g = (int) (((c >> 8) & 0xFF) * t);
        int b = (int) ((c & 0xFF) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
