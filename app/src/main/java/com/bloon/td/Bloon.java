package com.bloon.td;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;

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
    float freezeTimer = 0f;
    boolean glued = false;

    public Bloon(int type) {
        this.type = type;
        this.hp = Game.BLOON_HP[type];
        this.damage = 1;
        if (type == Game.B_BLACK) blackImmuneExplosion = true;
        if (type == Game.B_LEAD) { leadImmuneSharp = true; blackImmuneExplosion = false; }
        if (type == Game.B_MOAB) { damage = 100; this.hp = 220; }
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
        radius = Math.max(14, Game.BLOON_RADIUS[type] * g.tile);
        if (type == Game.B_MOAB) radius = g.tile * 1.1f;
    }

    public void draw(Canvas c, Paint p, Game g) {
        Bitmap bmp = g.bloonBmp[type];
        float wob = (float) Math.sin(wobble) * radius * 0.04f;
        float r = radius + wob;
        if (bmp != null) {
            float sz = r * 2.2f;
            RectF dst = new RectF(pos.x - sz / 2, pos.y - sz / 2, pos.x + sz / 2, pos.y + sz / 2 + r * 0.25f);
            // use dedicated bitmap paint (full opacity, filter on)
            c.drawBitmap(bmp, null, dst, g.bmpP);
        } else {
            // fallback
            p.setColor(Game.BLOON_COLOR[type]);
            c.drawCircle(pos.x, pos.y, r, p);
        }
        if (freezeTimer > 0) {
            p.setColor(0x60B3E5FC);
            c.drawCircle(pos.x, pos.y, r * 1.1f, p);
            p.setColor(0xCC81D4FA);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(3);
            for (int i = 0; i < 3; i++) {
                c.save();
                c.rotate(i * 60, pos.x, pos.y);
                c.drawLine(pos.x - r * 0.8f, pos.y, pos.x + r * 0.8f, pos.y, p);
                c.restore();
            }
            p.setStyle(Paint.Style.FILL);
        }
    }

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

    public static int lighten(int c, float t) {
        int a = (c >> 24) & 0xFF;
        int r = (c >> 16) & 0xFF;
        int g = (c >> 8) & 0xFF;
        int b = c & 0xFF;
        r = (int) (r + (255 - r) * t);
        g = (int) (g + (255 - g) * t);
        b = (int) (b + (255 - b) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
    public static int darken(int c, float t) {
        int a = (c >> 24) & 0xFF;
        int r = (int) (((c >> 16) & 0xFF) * t);
        int g = (int) (((c >> 8) & 0xFF) * t);
        int b = (int) ((c & 0xFF) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
