package com.bloon.td;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.RectF;
import android.view.MotionEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class Game {
    // ---------- screen / grid ----------
    int screenW = 1080, screenH = 1920;
    static final int COLS = 9;
    int rows = 16;
    float tile;
    float topPad, hudH, btnH;

    // ---------- game state ----------
    int cash = 650;
    int lives = 100;
    int wave = 0;
    int score = 0;
    float speedMult = 1f;
    boolean paused = false;
    boolean waveActive = false;
    int waveSpawned = 0;
    float waveSpawnTimer = 0f;
    List<int[]> waveQueue = new ArrayList<>(); // each [type, count, interval*100, hp]
    int waveQueueIdx = 0;
    float waveQueueT = 0f;
    int waveQueueRemaining = 0;
    int currentBloonType = 0;
    float spawnInterval = 0.6f;
    boolean gameOver = false;
    boolean victory = false;
    String popupMessage = null;
    float popupTimer = 0f;

    // ---------- world ----------
    final List<PointF> path = new ArrayList<>();
    final float[] segLen = new float[32];
    float totalPathLen;
    boolean[][] blocked;
    final List<Bloon> bloons = new ArrayList<>();
    final List<Tower> towers = new ArrayList<>();
    final List<Projectile> projectiles = new ArrayList<>();
    final List<Floater> floaters = new ArrayList<>();

    // ---------- ui ----------
    Tower selectedTower = null;
    int placingTowerType = -1;
    int menuTowerType = -1; // shown in tower menu at bottom
    long lastTapTime = 0;
    final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Paint textP = new Paint(Paint.ANTI_ALIAS_FLAG);

    // ---------- tower defs ----------
    static final int T_DART = 0, T_TACK = 1, T_BOMB = 2, T_SNIPER = 3;
    static final String[] TOWER_NAME = {"Dart", "Tack", "Bomb", "Sniper"};
    static final int[] TOWER_COST = {200, 360, 500, 600};
    static final int[] UPGRADE_COST = {180, 320, 450, 500};

    // ---------- bloon defs ----------
    // type -> color, base speed (tiles/sec), reward, childType (-1 if none)
    static final int B_RED = 0, B_BLUE = 1, B_GREEN = 2, B_YELLOW = 3, B_PINK = 4, B_BLACK = 5, B_LEAD = 6, B_MOAB = 7;
    static final int[] BLOON_COLOR = {
            0xFFE53935, 0xFF1E88E5, 0xFF43A047, 0xFFFDD835,
            0xFFE91E63, 0xFF212121, 0xFF607D8B, 0xFF8E24AA
    };
    static final float[] BLOON_SPEED = {2.8f, 3.6f, 4.6f, 5.8f, 7.0f, 3.2f, 2.6f, 1.8f};
    static final int[] BLOON_REWARD = {1, 2, 3, 4, 5, 11, 8, 80};
    static final int[] BLOON_CHILD = {-1, B_RED, B_BLUE, B_GREEN, B_YELLOW, B_PINK, B_BLACK, -1};
    static final int[] BLOON_CHILD_N = {0, 1, 1, 1, 1, 2, 2, 0};
    static final int[] BLOON_HP = {1, 1, 1, 1, 1, 1, 1, 200};
    static final float[] BLOON_RADIUS = {18, 20, 22, 24, 26, 28, 28, 70};

    public Game() { buildWaves(); }

    void resize(int w, int h) {
        screenW = w; screenH = h;
        tile = (float) w / COLS;
        hudH = tile * 1.2f;
        btnH = tile * 1.7f;
        topPad = hudH;
        rows = (int) ((h - hudH - btnH) / tile);
        buildPath();
        blocked = new boolean[COLS][rows];
        markPathBlocked();
        textP.setColor(Color.WHITE);
        textP.setTextSize(tile * 0.42f);
    }

    void buildPath() {
        path.clear();
        // Snake path through grid. Coords in screen px.
        int[][] pts = {
                {1, -1}, {1, 3}, {7, 3}, {7, 7},
                {2, 7}, {2, 11}, {6, 11}, {6, rows - 2},
                {1, rows - 2}, {1, rows + 1}
        };
        for (int[] p : pts) {
            path.add(new PointF(tileCenterX(p[0]), tileCenterY(p[1])));
        }
        totalPathLen = 0;
        for (int i = 0; i < path.size() - 1; i++) {
            PointF a = path.get(i), b = path.get(i + 1);
            float d = dist(a, b);
            segLen[i] = d;
            totalPathLen += d;
        }
    }

    void markPathBlocked() {
        for (int i = 0; i < path.size() - 1; i++) {
            PointF a = path.get(i), b = path.get(i + 1);
            float d = dist(a, b);
            int steps = (int) (d / (tile * 0.25f)) + 1;
            for (int s = 0; s <= steps; s++) {
                float t = (float) s / steps;
                float x = a.x + (b.x - a.x) * t;
                float y = a.y + (b.y - a.y) * t;
                int cx = (int) (x / tile);
                int cy = (int) ((y - topPad) / tile);
                for (int dx = -1; dx <= 1; dx++)
                    for (int dy = -1; dy <= 1; dy++) {
                        int nx = cx + dx, ny = cy + dy;
                        if (nx >= 0 && nx < COLS && ny >= 0 && ny < rows) blocked[nx][ny] = true;
                    }
            }
        }
    }

    float tileCenterX(int c) { return (c + 0.5f) * tile; }
    float tileCenterY(int r) { return topPad + (r + 0.5f) * tile; }

    static float dist(PointF a, PointF b) {
        float dx = a.x - b.x, dy = a.y - b.y;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    static float dist(float ax, float ay, float bx, float by) {
        float dx = ax - bx, dy = ay - by;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    PointF posAlongPath(float dAlong, PointF out) {
        float remaining = dAlong;
        for (int i = 0; i < path.size() - 1; i++) {
            if (remaining <= segLen[i]) {
                PointF a = path.get(i), b = path.get(i + 1);
                float t = remaining / segLen[i];
                out.x = a.x + (b.x - a.x) * t;
                out.y = a.y + (b.y - a.y) * t;
                return out;
            }
            remaining -= segLen[i];
        }
        PointF last = path.get(path.size() - 1);
        out.x = last.x; out.y = last.y;
        return out;
    }

    // ----------------- WAVES -----------------
    void buildWaves() {
        // empty; built when wave starts
    }

    int[][] waveDef(int w) {
        // {bloonType, count}
        switch (w) {
            case 1:  return new int[][]{{B_RED, 12}};
            case 2:  return new int[][]{{B_RED, 20}};
            case 3:  return new int[][]{{B_RED, 10}, {B_BLUE, 6}};
            case 4:  return new int[][]{{B_BLUE, 14}};
            case 5:  return new int[][]{{B_RED, 30}};
            case 6:  return new int[][]{{B_BLUE, 12}, {B_GREEN, 6}};
            case 7:  return new int[][]{{B_GREEN, 14}};
            case 8:  return new int[][]{{B_BLUE, 18}, {B_GREEN, 10}};
            case 9:  return new int[][]{{B_GREEN, 20}, {B_YELLOW, 4}};
            case 10: return new int[][]{{B_YELLOW, 16}};
            case 11: return new int[][]{{B_GREEN, 20}, {B_YELLOW, 10}};
            case 12: return new int[][]{{B_YELLOW, 24}, {B_PINK, 6}};
            case 13: return new int[][]{{B_PINK, 18}};
            case 14: return new int[][]{{B_BLACK, 10}, {B_YELLOW, 14}};
            case 15: return new int[][]{{B_PINK, 24}, {B_BLACK, 8}};
            case 16: return new int[][]{{B_LEAD, 6}, {B_PINK, 18}};
            case 17: return new int[][]{{B_BLACK, 16}, {B_PINK, 20}};
            case 18: return new int[][]{{B_LEAD, 12}, {B_BLACK, 12}};
            case 19: return new int[][]{{B_PINK, 40}, {B_BLACK, 20}};
            case 20: return new int[][]{{B_MOAB, 1}, {B_PINK, 20}};
            default: return new int[][]{{B_MOAB, 1}, {B_PINK, 30}};
        }
    }

    void startWave() {
        if (waveActive || gameOver) return;
        wave++;
        if (wave > 20) wave = 20; // sandbox after, but win at 20
        waveQueue.clear();
        int[][] def = waveDef(wave);
        for (int[] row : def) waveQueue.add(new int[]{row[0], row[1]});
        waveQueueIdx = 0;
        waveQueueT = 0f;
        waveQueueRemaining = waveQueue.get(0)[1];
        currentBloonType = waveQueue.get(0)[0];
        spawnInterval = wave > 15 ? 0.35f : 0.55f;
        waveActive = true;
        cash += 80 + wave * 8;
        flash("Wave " + wave);
    }

    void flash(String s) { popupMessage = s; popupTimer = 1.6f; }

    // ----------------- UPDATE -----------------
    public void update(float dt) {
        if (paused || gameOver) return;
        dt *= speedMult;

        // spawning
        if (waveActive) {
            waveQueueT -= dt;
            if (waveQueueRemaining > 0 && waveQueueT <= 0f) {
                spawnBloon(currentBloonType);
                waveQueueRemaining--;
                waveQueueT = spawnInterval;
                if (waveQueueRemaining == 0 && waveQueueIdx + 1 < waveQueue.size()) {
                    waveQueueIdx++;
                    int[] g = waveQueue.get(waveQueueIdx);
                    currentBloonType = g[0];
                    waveQueueRemaining = g[1];
                    waveQueueT = 1.2f; // small group gap
                }
            }
            if (waveQueueRemaining == 0 && bloons.isEmpty()) {
                waveActive = false;
                cash += 100 + wave * 15;
                if (wave >= 20) { victory = true; gameOver = true; flash("VICTORY!"); }
                else flash("Wave Complete +$" + (100 + wave * 15));
            }
        }

        // bloons
        Iterator<Bloon> it = bloons.iterator();
        while (it.hasNext()) {
            Bloon b = it.next();
            b.update(dt, this);
            if (b.escaped) {
                lives -= b.damage;
                it.remove();
                if (lives <= 0) { lives = 0; gameOver = true; flash("GAME OVER"); }
            } else if (b.dead) {
                it.remove();
            }
        }

        // towers
        for (Tower t : towers) t.update(dt, this);

        // projectiles
        Iterator<Projectile> pi = projectiles.iterator();
        while (pi.hasNext()) {
            Projectile p = pi.next();
            p.update(dt, this);
            if (p.dead) pi.remove();
        }

        // floaters (damage numbers, $)
        Iterator<Floater> fi = floaters.iterator();
        while (fi.hasNext()) {
            Floater f = fi.next();
            f.t += dt; f.y -= 30 * dt;
            if (f.t > 1.0f) fi.remove();
        }

        if (popupTimer > 0) popupTimer -= dt;
    }

    void spawnBloon(int type) {
        Bloon b = new Bloon(type);
        bloons.add(b);
    }

    // ----------------- DRAW -----------------
    public void draw(Canvas c) {
        if (c == null) return;
        // grass
        c.drawColor(0xFF3FA34D);
        // hud bg
        paint.setColor(0xFF1B5E20);
        c.drawRect(0, 0, screenW, topPad, paint);
        // path
        paint.setColor(0xFF8D6E63);
        Path p = new Path();
        if (!path.isEmpty()) {
            p.moveTo(path.get(0).x, path.get(0).y);
            for (int i = 1; i < path.size(); i++) p.lineTo(path.get(i).x, path.get(i).y);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(tile * 0.9f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            c.drawPath(p, paint);
            paint.setColor(0xFFA1887F);
            paint.setStrokeWidth(tile * 0.7f);
            c.drawPath(p, paint);
        }
        paint.setStyle(Paint.Style.FILL);

        // ghost / placement highlight
        if (placingTowerType >= 0 && lastTouchY > topPad && lastTouchY < topPad + rows * tile) {
            int cx = (int) (lastTouchX / tile);
            int cy = (int) ((lastTouchY - topPad) / tile);
            if (inGrid(cx, cy)) {
                boolean ok = canPlace(cx, cy);
                paint.setColor(ok ? 0x6644FF44 : 0x66FF4444);
                c.drawRect(cx * tile, topPad + cy * tile, (cx + 1) * tile, topPad + (cy + 1) * tile, paint);
                // range preview
                paint.setColor(0x40FFFFFF);
                c.drawCircle(tileCenterX(cx), tileCenterY(cy), Tower.range(placingTowerType, 1), paint);
            }
        }

        // towers
        for (Tower t : towers) t.draw(c, paint, this);
        // bloons
        for (Bloon b : bloons) b.draw(c, paint);
        // projectiles
        for (Projectile pr : projectiles) pr.draw(c, paint);

        // selected tower range
        if (selectedTower != null) {
            paint.setColor(0x33FFFFFF);
            c.drawCircle(selectedTower.x, selectedTower.y, selectedTower.range(), paint);
            paint.setColor(0xAAFFFFFF);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3);
            c.drawCircle(selectedTower.x, selectedTower.y, selectedTower.range(), paint);
            paint.setStyle(Paint.Style.FILL);
        }

        // floaters
        for (Floater f : floaters) {
            int alpha = (int) (255 * (1 - f.t));
            textP.setColor((alpha << 24) | (f.color & 0xFFFFFF));
            c.drawText(f.text, f.x, f.y, textP);
        }
        textP.setColor(Color.WHITE);

        drawHUD(c);
        drawBottomBar(c);

        if (popupTimer > 0 && popupMessage != null) {
            textP.setTextSize(tile * 0.9f);
            float w = textP.measureText(popupMessage);
            paint.setColor(0xCC000000);
            c.drawRoundRect(new RectF(screenW / 2f - w / 2 - 30, screenH / 2f - tile, screenW / 2f + w / 2 + 30, screenH / 2f + tile * 0.3f), 20, 20, paint);
            textP.setColor(0xFFFFEB3B);
            c.drawText(popupMessage, screenW / 2f - w / 2, screenH / 2f, textP);
            textP.setColor(Color.WHITE);
            textP.setTextSize(tile * 0.42f);
        }

        if (gameOver) {
            paint.setColor(0xCC000000);
            c.drawRect(0, 0, screenW, screenH, paint);
            textP.setTextSize(tile * 1.4f);
            String msg = victory ? "VICTORY!" : "GAME OVER";
            float w = textP.measureText(msg);
            textP.setColor(victory ? 0xFF4CAF50 : 0xFFE53935);
            c.drawText(msg, screenW / 2f - w / 2, screenH / 2f, textP);
            textP.setTextSize(tile * 0.55f);
            textP.setColor(Color.WHITE);
            String s2 = "Score: " + score + "  Wave: " + wave;
            float w2 = textP.measureText(s2);
            c.drawText(s2, screenW / 2f - w2 / 2, screenH / 2f + tile * 1.2f, textP);
            String s3 = "Tap to restart";
            float w3 = textP.measureText(s3);
            c.drawText(s3, screenW / 2f - w3 / 2, screenH / 2f + tile * 2.2f, textP);
            textP.setTextSize(tile * 0.42f);
        }
    }

    void drawHUD(Canvas c) {
        textP.setTextSize(tile * 0.5f);
        textP.setColor(0xFFFFEB3B);
        c.drawText("$" + cash, 20, topPad * 0.6f, textP);
        textP.setColor(0xFFFF5252);
        c.drawText("♥ " + lives, screenW * 0.35f, topPad * 0.6f, textP);
        textP.setColor(0xFFFFFFFF);
        c.drawText("Wave " + wave + "/20", screenW * 0.58f, topPad * 0.6f, textP);
        textP.setColor(0xFF80DEEA);
        c.drawText("Score " + score, 20, topPad * 0.95f, textP);
        // speed btn
        drawBtn(c, screenW - tile * 1.2f, 8, tile, tile * 0.5f, speedMult == 1f ? ">" : (speedMult == 2f ? ">>" : ">>>"), 0xFF1976D2);
        drawBtn(c, screenW - tile * 2.4f, 8, tile, tile * 0.5f, paused ? "▶" : "❚❚", 0xFF455A64);
        drawBtn(c, screenW - tile * 3.6f, 8, tile, tile * 0.5f, "GO", waveActive ? 0xFF616161 : 0xFFE53935);
        textP.setTextSize(tile * 0.42f);
    }

    void drawBottomBar(Canvas c) {
        float by = topPad + rows * tile;
        paint.setColor(0xFF263238);
        c.drawRect(0, by, screenW, screenH, paint);
        float bw = screenW / 4f;
        for (int i = 0; i < 4; i++) {
            float bx = i * bw;
            boolean sel = menuTowerType == i;
            boolean affordable = cash >= TOWER_COST[i];
            paint.setColor(sel ? 0xFF1976D2 : (affordable ? 0xFF37474F : 0xFF263238));
            c.drawRoundRect(new RectF(bx + 8, by + 8, bx + bw - 8, screenH - 8), 14, 14, paint);
            // tower icon
            Tower.drawIcon(c, paint, i, bx + bw / 2, by + btnH * 0.35f, tile * 0.5f);
            textP.setColor(affordable ? Color.WHITE : 0xFF9E9E9E);
            textP.setTextSize(tile * 0.35f);
            String n = TOWER_NAME[i];
            float w = textP.measureText(n);
            c.drawText(n, bx + bw / 2 - w / 2, by + btnH * 0.75f, textP);
            String cost = "$" + TOWER_COST[i];
            textP.setColor(affordable ? 0xFFFFEB3B : 0xFFE57373);
            float w2 = textP.measureText(cost);
            c.drawText(cost, bx + bw / 2 - w2 / 2, by + btnH * 0.96f, textP);
        }
        textP.setColor(Color.WHITE);
        textP.setTextSize(tile * 0.42f);

        // upgrade/sell panel if selected tower
        if (selectedTower != null) {
            paint.setColor(0xEE263238);
            float panelY = by - tile * 1.6f;
            c.drawRoundRect(new RectF(20, panelY, screenW - 20, by - 10), 20, 20, paint);
            textP.setTextSize(tile * 0.42f);
            textP.setColor(Color.WHITE);
            c.drawText(TOWER_NAME[selectedTower.type] + " Lv " + selectedTower.level, 40, panelY + tile * 0.5f, textP);
            int upCost = (UPGRADE_COST[selectedTower.type] * selectedTower.level);
            int sellAmt = selectedTower.totalSpent * 7 / 10;
            // upgrade btn
            boolean canUp = selectedTower.level < 4 && cash >= upCost;
            paint.setColor(canUp ? 0xFF388E3C : 0xFF616161);
            RectF rUp = new RectF(screenW * 0.35f, panelY + 15, screenW * 0.66f, by - 25);
            c.drawRoundRect(rUp, 14, 14, paint);
            String upTxt = selectedTower.level < 4 ? ("Upgrade $" + upCost) : "MAX";
            float w = textP.measureText(upTxt);
            c.drawText(upTxt, rUp.centerX() - w / 2, rUp.centerY() + tile * 0.15f, textP);
            // sell btn
            paint.setColor(0xFFD84315);
            RectF rSell = new RectF(screenW * 0.69f, panelY + 15, screenW - 40, by - 25);
            c.drawRoundRect(rSell, 14, 14, paint);
            String sTxt = "Sell $" + sellAmt;
            float ws = textP.measureText(sTxt);
            c.drawText(sTxt, rSell.centerX() - ws / 2, rSell.centerY() + tile * 0.15f, textP);
        }
    }

    void drawBtn(Canvas c, float x, float y, float w, float h, String label, int color) {
        paint.setColor(color);
        c.drawRoundRect(new RectF(x, y, x + w, y + h), 12, 12, paint);
        textP.setColor(Color.WHITE);
        textP.setTextSize(h * 0.55f);
        float tw = textP.measureText(label);
        c.drawText(label, x + w / 2 - tw / 2, y + h * 0.7f, textP);
    }

    // ----------------- INPUT -----------------
    float lastTouchX, lastTouchY;
    public void onTouch(MotionEvent e) {
        lastTouchX = e.getX();
        lastTouchY = e.getY();
        if (e.getAction() != MotionEvent.ACTION_DOWN && e.getAction() != MotionEvent.ACTION_UP) return;
        if (e.getAction() != MotionEvent.ACTION_DOWN) return;

        if (gameOver) {
            restart();
            return;
        }

        float by = topPad + rows * tile;
        // HUD buttons
        if (lastTouchY < topPad) {
            if (lastTouchX > screenW - tile * 1.2f && lastTouchY < tile * 0.6f + 8) {
                speedMult = speedMult == 1f ? 2f : (speedMult == 2f ? 3f : 1f);
                return;
            }
            if (lastTouchX > screenW - tile * 2.4f && lastTouchX < screenW - tile * 1.2f && lastTouchY < tile * 0.6f + 8) {
                paused = !paused;
                return;
            }
            if (lastTouchX > screenW - tile * 3.6f && lastTouchX < screenW - tile * 2.4f && lastTouchY < tile * 0.6f + 8) {
                if (!waveActive) startWave();
                return;
            }
            return;
        }

        // Bottom: tower bar
        if (lastTouchY > by) {
            float bw = screenW / 4f;

            // tower upgrade/sell panel
            if (selectedTower != null) {
                float panelY = by - tile * 1.6f;
                if (lastTouchY > panelY && lastTouchY < by - 10) {
                    if (lastTouchX > screenW * 0.35f && lastTouchX < screenW * 0.66f) {
                        upgradeSelected();
                        return;
                    } else if (lastTouchX > screenW * 0.69f && lastTouchX < screenW - 40) {
                        sellSelected();
                        return;
                    }
                }
            }

            int idx = (int) (lastTouchX / bw);
            if (idx < 0 || idx > 3) return;
            if (cash >= TOWER_COST[idx]) {
                placingTowerType = idx;
                menuTowerType = idx;
                selectedTower = null;
            }
            return;
        }

        // Game area
        if (placingTowerType >= 0) {
            int cx = (int) (lastTouchX / tile);
            int cy = (int) ((lastTouchY - topPad) / tile);
            if (inGrid(cx, cy) && canPlace(cx, cy)) {
                Tower t = new Tower(placingTowerType, tileCenterX(cx), tileCenterY(cy), cx, cy);
                t.totalSpent = TOWER_COST[placingTowerType];
                towers.add(t);
                blocked[cx][cy] = true;
                cash -= TOWER_COST[placingTowerType];
                placingTowerType = -1;
                menuTowerType = -1;
            } else {
                // cancel
                placingTowerType = -1;
                menuTowerType = -1;
            }
            return;
        }

        // Select existing tower
        Tower hit = null;
        float bestD = tile * 0.7f;
        for (Tower t : towers) {
            float d = dist(t.x, t.y, lastTouchX, lastTouchY);
            if (d < bestD) { bestD = d; hit = t; }
        }
        selectedTower = hit;
    }

    void upgradeSelected() {
        if (selectedTower == null || selectedTower.level >= 4) return;
        int upCost = UPGRADE_COST[selectedTower.type] * selectedTower.level;
        if (cash < upCost) return;
        cash -= upCost;
        selectedTower.totalSpent += upCost;
        selectedTower.level++;
    }

    void sellSelected() {
        if (selectedTower == null) return;
        cash += selectedTower.totalSpent * 7 / 10;
        int cx = selectedTower.gridX, cy = selectedTower.gridY;
        towers.remove(selectedTower);
        // recompute blocked: simplest is to rebuild
        blocked = new boolean[COLS][rows];
        markPathBlocked();
        for (Tower t : towers) blocked[t.gridX][t.gridY] = true;
        selectedTower = null;
    }

    void restart() {
        bloons.clear(); towers.clear(); projectiles.clear(); floaters.clear();
        cash = 650; lives = 100; wave = 0; score = 0;
        speedMult = 1f; paused = false; gameOver = false; victory = false;
        waveActive = false; selectedTower = null; placingTowerType = -1; menuTowerType = -1;
        if (blocked != null) {
            blocked = new boolean[COLS][rows];
            markPathBlocked();
        }
        flash("Restart");
    }

    boolean inGrid(int cx, int cy) {
        return cx >= 0 && cx < COLS && cy >= 0 && cy < rows;
    }

    boolean canPlace(int cx, int cy) {
        if (!inGrid(cx, cy)) return false;
        return !blocked[cx][cy];
    }

    void addFloater(String txt, float x, float y, int color) {
        Floater f = new Floater();
        f.text = txt; f.x = x; f.y = y; f.color = color;
        floaters.add(f);
    }

    static class Floater {
        String text;
        float x, y;
        float t;
        int color;
    }
}
