package com.bloon.td;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;

public class Bloon {
    int type;
    int hp;
    int damage; // lives lost if escapes
    float dAlong;
    float speed; // px/sec
    float radius;
    boolean escaped;
    boolean dead;
    final PointF pos = new PointF();
    // for popping order on MOAB
    boolean leadImmuneSharp = false;
    boolean blackImmuneExplosion = false;
    float dirX = 1, dirY = 0;
    float spawnDelay = 0f;

    public Bloon(int type) {
        this.type = type;
        this.hp = Game.BLOON_HP[type];
        this.damage = 1;
        if (type == Game.B_BLACK) { blackImmuneExplosion = true; }
        if (type == Game.B_LEAD) { leadImmuneSharp = true; }
        if (type == Game.B_MOAB) { damage = 100; this.hp = 200; }
    }

    public void update(float dt, Game g) {
        if (spawnDelay > 0) { spawnDelay -= dt; return; }
        // speed in tiles per second -> px/s
        float sp = Game.BLOON_SPEED[type] * g.tile;
        dAlong += sp * dt;
        if (dAlong >= g.totalPathLen) {
            escaped = true;
            return;
        }
        // compute pos and direction
        PointF prev = new PointF(pos.x, pos.y);
        g.posAlongPath(dAlong, pos);
        if (pos.x != prev.x || pos.y != prev.y) {
            float dx = pos.x - prev.x, dy = pos.y - prev.y;
            float l = (float) Math.sqrt(dx * dx + dy * dy);
            if (l > 0.01f) { dirX = dx / l; dirY = dy / l; }
        }
        radius = Game.BLOON_RADIUS[type] * (g.tile / 90f);
        if (radius < 14) radius = 14;
        if (type == Game.B_MOAB) radius = g.tile * 0.7f;
    }

    public void draw(Canvas c, Paint p) {
        p.setColor(Game.BLOON_COLOR[type]);
        c.drawCircle(pos.x, pos.y, radius, p);
        // shine
        p.setColor(0x55FFFFFF);
        c.drawCircle(pos.x - radius * 0.3f, pos.y - radius * 0.35f, radius * 0.32f, p);
        // outline / MOAB details
        if (type == Game.B_MOAB) {
            p.setColor(Color.WHITE);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(3);
            c.drawCircle(pos.x, pos.y, radius, p);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFF3E2723);
            c.drawCircle(pos.x - radius * 0.4f, pos.y, radius * 0.15f, p);
            c.drawCircle(pos.x + radius * 0.4f, pos.y, radius * 0.15f, p);
            p.setColor(Color.WHITE);
            p.setTextSize(radius * 0.55f);
            c.drawText("MOAB", pos.x - radius * 0.55f, pos.y + radius * 0.2f, p);
        } else {
            p.setColor(0x66000000);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2);
            c.drawCircle(pos.x, pos.y, radius, p);
            p.setStyle(Paint.Style.FILL);
        }
    }

    /** apply hit. returns true if popped this hit. */
    public boolean hit(int dmg, int kind, Game g) {
        if (dead) return false;
        // immunity rules
        if (kind == Projectile.K_SHARP && leadImmuneSharp) return false;
        if (kind == Projectile.K_EXPLOSION && blackImmuneExplosion) return false;

        hp -= dmg;
        if (hp <= 0) {
            // pop -> child(ren)
            g.cash += Game.BLOON_REWARD[type];
            g.score += Game.BLOON_REWARD[type];
            int childType = Game.BLOON_CHILD[type];
            int n = Game.BLOON_CHILD_N[type];
            if (childType >= 0 && n > 0) {
                for (int i = 0; i < n; i++) {
                    Bloon b = new Bloon(childType);
                    b.dAlong = dAlong - i * 6f;
                    g.bloons.add(b);
                }
            }
            // MOAB explodes into 4 pinks
            if (type == Game.B_MOAB) {
                for (int i = 0; i < 4; i++) {
                    Bloon b = new Bloon(Game.B_PINK);
                    b.dAlong = dAlong - i * 12f;
                    g.bloons.add(b);
                }
                g.addFloater("MOAB!", pos.x, pos.y, 0xFFFFEB3B);
            }
            dead = true;
            return true;
        }
        return false;
    }
}
