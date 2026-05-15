package com.bloon.td;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.MotionEvent;

import java.util.ArrayList;
import java.util.List;

public class Game {
    // ---------- state ----------
    public static final int S_MENU = 0;
    public static final int S_PLAYING = 1;
    int state = S_MENU;

    // ---------- screen ----------
    int screenW = 1080, screenH = 1920;
    static final int COLS = 9;
    int rows = MapDef.MAP_ROWS;
    float tile;
    float topPad;     // HUD height
    float bottomBar;  // bottom UI height
    float gameAreaH;  // height of play area

    // ---------- map ----------
    int selectedWorld = 0;
    MapDef currentMap;
    final List<PointF> path = new ArrayList<>();
    final float[] segLen = new float[64];
    float totalPathLen;
    boolean[][] blocked;
    Bitmap bgCache;

    // ---------- game state ----------
    int cash, lives, wave, score;
    float speedMult = 1f;
    boolean paused = false;
    boolean waveActive = false;
    boolean gameOver = false;
    boolean victory = false;

    // ---------- spawning ----------
    final List<int[]> waveQueue = new ArrayList<>();
    int waveQueueIdx = 0;
    float waveQueueT = 0f;
    int waveQueueRemaining = 0;
    int currentBloonType = 0;
    float spawnInterval = 0.55f;

    // ---------- world ----------
    final List<Bloon> bloons = new ArrayList<>();
    final List<Tower> towers = new ArrayList<>();
    final List<Projectile> projectiles = new ArrayList<>();
    final List<Floater> floaters = new ArrayList<>();
    // staging buffers to avoid concurrent modification
    final List<Bloon> pendingBloons = new ArrayList<>();
    final List<Projectile> pendingProjectiles = new ArrayList<>();

    // ---------- ui ----------
    Tower selectedTower = null;
    int placingTowerType = -1;
    String popupMessage = null;
    float popupTimer = 0f;
    float lastTouchX, lastTouchY;
    final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Paint textP = new Paint(Paint.ANTI_ALIAS_FLAG);

    // ---------- towers ----------
    public static final int T_DART = 0, T_TACK = 1, T_BOMB = 2, T_SNIPER = 3,
            T_NINJA = 4, T_ICE = 5, T_SUPER = 6, T_WIZARD = 7;

    // ---------- bloons ----------
    public static final int B_RED = 0, B_BLUE = 1, B_GREEN = 2, B_YELLOW = 3,
            B_PINK = 4, B_BLACK = 5, B_LEAD = 6, B_MOAB = 7;
    public static final int[] BLOON_COLOR = {
            0xFFE53935, 0xFF1E88E5, 0xFF43A047, 0xFFFDD835,
            0xFFE91E63, 0xFF212121, 0xFF455A64, 0xFF8E24AA
    };
    public static final float[] BLOON_SPEED = {3.0f, 3.8f, 4.7f, 6.0f, 7.2f, 3.4f, 2.8f, 1.6f};
    public static final int[] BLOON_REWARD = {1, 2, 3, 4, 5, 11, 8, 90};
    public static final int[] BLOON_CHILD = {-1, B_RED, B_BLUE, B_GREEN, B_YELLOW, B_PINK, B_BLACK, -1};
    public static final int[] BLOON_CHILD_N = {0, 1, 1, 1, 1, 2, 2, 0};
    public static final int[] BLOON_HP = {1, 1, 1, 1, 1, 1, 1, 220};
    public static final float[] BLOON_RADIUS = {20, 22, 24, 26, 28, 30, 30, 70};

    public Game() {}

    void resize(int w, int h) {
        screenW = w; screenH = h;
        tile = (float) w / COLS;
        topPad = tile * 1.15f;
        bottomBar = tile * 2.0f;
        gameAreaH = h - topPad - bottomBar;
        rows = MapDef.MAP_ROWS;
        textP.setColor(Color.WHITE);
        textP.setTextSize(tile * 0.42f);
        if (state == S_PLAYING && currentMap != null) reloadMapGeometry();
    }

    // ---------------- MAP LOADING ----------------
    void loadMap(MapDef m) {
        currentMap = m;
        reloadMapGeometry();
        cash = 750;
        lives = 120;
        wave = 0;
        score = 0;
        speedMult = 1f;
        paused = false;
        waveActive = false;
        gameOver = false; victory = false;
        bloons.clear(); towers.clear(); projectiles.clear(); floaters.clear();
        pendingBloons.clear(); pendingProjectiles.clear();
        selectedTower = null; placingTowerType = -1;
        state = S_PLAYING;
        flash(m.name);
    }

    void reloadMapGeometry() {
        // Build pixel-space path from waypoints (col, row in MAP_ROWS grid)
        path.clear();
        float wH = gameAreaH;
        for (float[] wp : currentMap.waypoints) {
            float px = (wp[0] + 0.5f) * tile;
            float py = topPad + (wp[1] + 0.5f) * (wH / MapDef.MAP_ROWS);
            path.add(new PointF(px, py));
        }
        totalPathLen = 0;
        for (int i = 0; i < path.size() - 1; i++) {
            float d = dist(path.get(i), path.get(i + 1));
            segLen[i] = d;
            totalPathLen += d;
        }
        blocked = new boolean[COLS][MapDef.MAP_ROWS];
        markPathBlocked();
        bakeBackground();
    }

    void markPathBlocked() {
        // Block only the tile that contains each step of the path centerline.
        for (int i = 0; i < path.size() - 1; i++) {
            PointF a = path.get(i), b = path.get(i + 1);
            float d = segLen[i];
            int steps = (int) (d / (tile * 0.18f)) + 1;
            for (int s = 0; s <= steps; s++) {
                float t = (float) s / steps;
                float x = a.x + (b.x - a.x) * t;
                float y = a.y + (b.y - a.y) * t;
                int cx = (int) (x / tile);
                int cy = (int) ((y - topPad) / (gameAreaH / MapDef.MAP_ROWS));
                if (inGrid(cx, cy)) blocked[cx][cy] = true;
            }
        }
    }

    boolean inGrid(int cx, int cy) {
        return cx >= 0 && cx < COLS && cy >= 0 && cy < MapDef.MAP_ROWS;
    }

    float tileH() { return gameAreaH / MapDef.MAP_ROWS; }

    float gridCenterX(int c) { return (c + 0.5f) * tile; }
    float gridCenterY(int r) { return topPad + (r + 0.5f) * tileH(); }

    boolean canPlace(int cx, int cy) {
        if (!inGrid(cx, cy)) return false;
        if (blocked[cx][cy]) return false;
        for (Tower t : towers) {
            if (t.gridX == cx && t.gridY == cy) return false;
        }
        return true;
    }

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

    // ---------------- BACKGROUND BAKING ----------------
    void bakeBackground() {
        if (screenW <= 0 || screenH <= 0) return;
        if (bgCache != null && (bgCache.getWidth() != screenW || bgCache.getHeight() != screenH)) {
            bgCache.recycle(); bgCache = null;
        }
        if (bgCache == null) {
            bgCache = Bitmap.createBitmap(screenW, screenH, Bitmap.Config.ARGB_8888);
        }
        Canvas c = new Canvas(bgCache);
        c.drawColor(0xFF000000);

        int world = currentMap == null ? 0 : currentMap.world;
        drawBiomeBackground(c, world);
        drawPath(c, world);
        drawDecorations(c, world);
    }

    void drawBiomeBackground(Canvas c, int world) {
        int top, mid, bot;
        switch (world) {
            case MapDef.W_DESERT:
                top = 0xFFFFD180; mid = 0xFFFFCA28; bot = 0xFFFF8F00; break;
            case MapDef.W_SNOW:
                top = 0xFFE3F2FD; mid = 0xFFB3E5FC; bot = 0xFF81D4FA; break;
            case MapDef.W_LAVA:
                top = 0xFF3E2723; mid = 0xFF5D4037; bot = 0xFF263238; break;
            case MapDef.W_FOREST:
            default:
                top = 0xFF66BB6A; mid = 0xFF43A047; bot = 0xFF2E7D32; break;
        }
        Paint pp = new Paint(Paint.ANTI_ALIAS_FLAG);
        LinearGradient lg = new LinearGradient(0, topPad, 0, topPad + gameAreaH, top, bot, Shader.TileMode.CLAMP);
        pp.setShader(lg);
        c.drawRect(0, topPad, screenW, topPad + gameAreaH, pp);
        pp.setShader(null);

        // Subtle pattern overlay
        java.util.Random rng = new java.util.Random(world * 1337L + 17);
        for (int i = 0; i < 220; i++) {
            float xx = rng.nextFloat() * screenW;
            float yy = topPad + rng.nextFloat() * gameAreaH;
            float r = 3 + rng.nextFloat() * 7;
            int col;
            switch (world) {
                case MapDef.W_DESERT: col = (rng.nextBoolean()) ? 0x33FFE082 : 0x44E65100; break;
                case MapDef.W_SNOW:   col = (rng.nextBoolean()) ? 0x55FFFFFF : 0x4481D4FA; break;
                case MapDef.W_LAVA:   col = (rng.nextBoolean()) ? 0x66FF6F00 : 0x55000000; break;
                default:              col = (rng.nextBoolean()) ? 0x552E7D32 : 0x4481C784; break;
            }
            pp.setColor(col);
            c.drawCircle(xx, yy, r, pp);
        }

        // Dune lines for desert / cracks for lava
        if (world == MapDef.W_DESERT) {
            pp.setColor(0x44E65100);
            pp.setStyle(Paint.Style.STROKE);
            pp.setStrokeWidth(3);
            for (int i = 0; i < 8; i++) {
                Path pa = new Path();
                float y0 = topPad + (i + 1) * gameAreaH / 9f;
                pa.moveTo(0, y0);
                for (int k = 1; k <= 8; k++) {
                    float xx = k * screenW / 8f;
                    pa.quadTo(xx - screenW / 16f, y0 - 25 + rng.nextFloat() * 20, xx, y0);
                }
                c.drawPath(pa, pp);
            }
            pp.setStyle(Paint.Style.FILL);
        } else if (world == MapDef.W_LAVA) {
            // glowing cracks
            pp.setColor(0xCCFF6F00);
            pp.setStyle(Paint.Style.STROKE);
            pp.setStrokeWidth(4);
            for (int i = 0; i < 5; i++) {
                Path pa = new Path();
                float x0 = rng.nextFloat() * screenW;
                float y0 = topPad + rng.nextFloat() * gameAreaH;
                pa.moveTo(x0, y0);
                for (int k = 0; k < 6; k++) {
                    x0 += (rng.nextFloat() - 0.5f) * 200;
                    y0 += (rng.nextFloat() - 0.5f) * 200;
                    pa.lineTo(x0, y0);
                }
                c.drawPath(pa, pp);
            }
            pp.setColor(0x88FFEB3B);
            pp.setStrokeWidth(2);
            for (int i = 0; i < 5; i++) {
                Path pa = new Path();
                float x0 = rng.nextFloat() * screenW;
                float y0 = topPad + rng.nextFloat() * gameAreaH;
                pa.moveTo(x0, y0);
                for (int k = 0; k < 4; k++) {
                    x0 += (rng.nextFloat() - 0.5f) * 150;
                    y0 += (rng.nextFloat() - 0.5f) * 150;
                    pa.lineTo(x0, y0);
                }
                c.drawPath(pa, pp);
            }
            pp.setStyle(Paint.Style.FILL);
        }
    }

    void drawPath(Canvas c, int world) {
        if (path.isEmpty()) return;
        Paint pp = new Paint(Paint.ANTI_ALIAS_FLAG);
        Path p = new Path();
        p.moveTo(path.get(0).x, path.get(0).y);
        for (int i = 1; i < path.size(); i++) p.lineTo(path.get(i).x, path.get(i).y);

        int outerCol, innerCol, edgeCol;
        switch (world) {
            case MapDef.W_DESERT: outerCol = 0xFF6D4C41; innerCol = 0xFFA1887F; edgeCol = 0xFF4E342E; break;
            case MapDef.W_SNOW:   outerCol = 0xFF607D8B; innerCol = 0xFFB0BEC5; edgeCol = 0xFF37474F; break;
            case MapDef.W_LAVA:   outerCol = 0xFF263238; innerCol = 0xFF455A64; edgeCol = 0xFF000000; break;
            default:              outerCol = 0xFF5D4037; innerCol = 0xFF8D6E63; edgeCol = 0xFF3E2723; break;
        }
        // outer
        pp.setStyle(Paint.Style.STROKE);
        pp.setStrokeCap(Paint.Cap.ROUND);
        pp.setStrokeJoin(Paint.Join.ROUND);
        pp.setColor(edgeCol);
        pp.setStrokeWidth(tile * 1.02f);
        c.drawPath(p, pp);
        pp.setColor(outerCol);
        pp.setStrokeWidth(tile * 0.92f);
        c.drawPath(p, pp);
        pp.setColor(innerCol);
        pp.setStrokeWidth(tile * 0.72f);
        c.drawPath(p, pp);
        // dashed center
        Paint dashP = new Paint(Paint.ANTI_ALIAS_FLAG);
        dashP.setStyle(Paint.Style.STROKE);
        dashP.setColor(0x66FFFFFF);
        dashP.setStrokeWidth(tile * 0.06f);
        dashP.setPathEffect(new android.graphics.DashPathEffect(new float[]{tile * 0.35f, tile * 0.35f}, 0));
        c.drawPath(p, dashP);

        // little stones along the edge
        java.util.Random rng = new java.util.Random(7);
        pp.setStyle(Paint.Style.FILL);
        pp.setColor(world == MapDef.W_SNOW ? 0xFFFFFFFF : 0xFF6D4C41);
        for (int i = 0; i < path.size() - 1; i++) {
            PointF a = path.get(i), b = path.get(i + 1);
            float d = segLen[i];
            int n = (int) (d / (tile * 0.4f));
            for (int k = 0; k <= n; k++) {
                float t = (float) k / Math.max(1, n);
                float xx = a.x + (b.x - a.x) * t;
                float yy = a.y + (b.y - a.y) * t;
                // perpendicular offset
                float dx = b.x - a.x, dy = b.y - a.y;
                float l = (float) Math.sqrt(dx * dx + dy * dy);
                if (l < 0.01) continue;
                float nx = -dy / l, ny = dx / l;
                float side = (k % 2 == 0) ? 1f : -1f;
                float ox = xx + nx * tile * 0.42f * side;
                float oy = yy + ny * tile * 0.42f * side;
                c.drawCircle(ox, oy, tile * (0.04f + rng.nextFloat() * 0.04f), pp);
            }
        }
    }

    void drawDecorations(Canvas c, int world) {
        Paint pp = new Paint(Paint.ANTI_ALIAS_FLAG);
        java.util.Random rng = new java.util.Random(currentMap == null ? 0 : currentMap.name.hashCode());
        for (int gx = 0; gx < COLS; gx++) {
            for (int gy = 0; gy < MapDef.MAP_ROWS; gy++) {
                if (blocked[gx][gy]) continue;
                if (rng.nextFloat() > 0.35f) continue;
                float cx = gridCenterX(gx) + (rng.nextFloat() - 0.5f) * tile * 0.5f;
                float cy = gridCenterY(gy) + (rng.nextFloat() - 0.5f) * tile * 0.5f;
                drawDecor(c, pp, cx, cy, world, rng);
            }
        }
    }

    void drawDecor(Canvas c, Paint pp, float cx, float cy, int world, java.util.Random rng) {
        switch (world) {
            case MapDef.W_FOREST: {
                if (rng.nextFloat() < 0.4f) {
                    // tree
                    pp.setColor(0x44000000);
                    c.drawCircle(cx + 4, cy + 6, tile * 0.25f, pp);
                    pp.setColor(0xFF1B5E20);
                    c.drawCircle(cx, cy, tile * 0.27f, pp);
                    pp.setColor(0xFF2E7D32);
                    c.drawCircle(cx - tile * 0.07f, cy - tile * 0.08f, tile * 0.2f, pp);
                    pp.setColor(0xFF4CAF50);
                    c.drawCircle(cx - tile * 0.15f, cy - tile * 0.16f, tile * 0.1f, pp);
                    pp.setColor(0xFF5D4037);
                    c.drawRect(cx - tile * 0.04f, cy + tile * 0.18f, cx + tile * 0.04f, cy + tile * 0.3f, pp);
                } else if (rng.nextFloat() < 0.5f) {
                    // bush
                    pp.setColor(0xFF2E7D32);
                    c.drawCircle(cx, cy, tile * 0.18f, pp);
                    pp.setColor(0xFF66BB6A);
                    c.drawCircle(cx - tile * 0.05f, cy - tile * 0.06f, tile * 0.1f, pp);
                } else {
                    // flowers
                    int[] cols = {0xFFFFEB3B, 0xFFE53935, 0xFFFFFFFF, 0xFFE91E63};
                    pp.setColor(cols[rng.nextInt(cols.length)]);
                    c.drawCircle(cx, cy, tile * 0.06f, pp);
                    pp.setColor(0xFFFFEB3B);
                    c.drawCircle(cx, cy, tile * 0.03f, pp);
                }
                break;
            }
            case MapDef.W_DESERT: {
                if (rng.nextFloat() < 0.3f) {
                    // cactus
                    pp.setColor(0xFF2E7D32);
                    c.drawRoundRect(cx - tile * 0.07f, cy - tile * 0.28f, cx + tile * 0.07f, cy + tile * 0.25f, 10, 10, pp);
                    if (rng.nextBoolean()) {
                        c.drawRoundRect(cx + tile * 0.06f, cy - tile * 0.1f, cx + tile * 0.22f, cy, 8, 8, pp);
                        c.drawRoundRect(cx + tile * 0.16f, cy - tile * 0.2f, cx + tile * 0.22f, cy, 8, 8, pp);
                    }
                    pp.setColor(0xFFE8F5E9);
                    for (int i = 0; i < 4; i++)
                        c.drawCircle(cx, cy - tile * 0.2f + i * tile * 0.13f, 2, pp);
                } else if (rng.nextFloat() < 0.5f) {
                    pp.setColor(0xFFBCAAA4);
                    c.drawCircle(cx, cy, tile * 0.1f, pp);
                    pp.setColor(0xFF6D4C41);
                    c.drawCircle(cx - tile * 0.03f, cy - tile * 0.03f, tile * 0.06f, pp);
                }
                break;
            }
            case MapDef.W_SNOW: {
                if (rng.nextFloat() < 0.4f) {
                    // pine tree
                    pp.setColor(0x44000000);
                    c.drawCircle(cx + 4, cy + 5, tile * 0.2f, pp);
                    Path tri = new Path();
                    pp.setColor(0xFF1B5E20);
                    for (int k = 0; k < 3; k++) {
                        tri.reset();
                        float w = tile * (0.22f - k * 0.04f);
                        float yTop = cy - tile * 0.3f + k * tile * 0.12f;
                        float yBot = yTop + tile * 0.18f;
                        tri.moveTo(cx - w, yBot);
                        tri.lineTo(cx + w, yBot);
                        tri.lineTo(cx, yTop);
                        tri.close();
                        c.drawPath(tri, pp);
                    }
                    pp.setColor(0xFF5D4037);
                    c.drawRect(cx - tile * 0.04f, cy + tile * 0.1f, cx + tile * 0.04f, cy + tile * 0.22f, pp);
                    // snow cap
                    pp.setColor(0xFFFFFFFF);
                    c.drawCircle(cx, cy - tile * 0.27f, tile * 0.06f, pp);
                } else {
                    pp.setColor(0xFFFFFFFF);
                    c.drawCircle(cx, cy, tile * 0.1f, pp);
                    pp.setColor(0xFFE0F2F1);
                    c.drawCircle(cx + tile * 0.05f, cy + tile * 0.05f, tile * 0.06f, pp);
                }
                break;
            }
            case MapDef.W_LAVA: {
                if (rng.nextFloat() < 0.35f) {
                    // jagged rock
                    pp.setColor(0xFF424242);
                    Path pa = new Path();
                    pa.moveTo(cx - tile * 0.2f, cy + tile * 0.1f);
                    pa.lineTo(cx - tile * 0.05f, cy - tile * 0.15f);
                    pa.lineTo(cx + tile * 0.1f, cy - tile * 0.05f);
                    pa.lineTo(cx + tile * 0.2f, cy + tile * 0.12f);
                    pa.close();
                    c.drawPath(pa, pp);
                    pp.setColor(0xFFFF6F00);
                    c.drawCircle(cx, cy + tile * 0.08f, tile * 0.03f, pp);
                } else if (rng.nextFloat() < 0.4f) {
                    pp.setColor(0xCCFF6F00);
                    c.drawCircle(cx, cy, tile * 0.08f, pp);
                    pp.setColor(0xFFFFEB3B);
                    c.drawCircle(cx, cy, tile * 0.04f, pp);
                }
                break;
            }
        }
    }

    // ---------------- WAVES ----------------
    int[][] waveDef(int w) {
        int[][] base;
        switch (w) {
            case 1:  base = new int[][]{{B_RED, 14}}; break;
            case 2:  base = new int[][]{{B_RED, 22}}; break;
            case 3:  base = new int[][]{{B_RED, 12}, {B_BLUE, 8}}; break;
            case 4:  base = new int[][]{{B_BLUE, 16}}; break;
            case 5:  base = new int[][]{{B_RED, 30}}; break;
            case 6:  base = new int[][]{{B_BLUE, 14}, {B_GREEN, 8}}; break;
            case 7:  base = new int[][]{{B_GREEN, 16}}; break;
            case 8:  base = new int[][]{{B_BLUE, 20}, {B_GREEN, 12}}; break;
            case 9:  base = new int[][]{{B_GREEN, 22}, {B_YELLOW, 6}}; break;
            case 10: base = new int[][]{{B_YELLOW, 18}}; break;
            case 11: base = new int[][]{{B_GREEN, 22}, {B_YELLOW, 12}}; break;
            case 12: base = new int[][]{{B_YELLOW, 26}, {B_PINK, 8}}; break;
            case 13: base = new int[][]{{B_PINK, 22}}; break;
            case 14: base = new int[][]{{B_BLACK, 12}, {B_YELLOW, 16}}; break;
            case 15: base = new int[][]{{B_PINK, 26}, {B_BLACK, 10}}; break;
            case 16: base = new int[][]{{B_LEAD, 8}, {B_PINK, 20}}; break;
            case 17: base = new int[][]{{B_BLACK, 18}, {B_PINK, 22}}; break;
            case 18: base = new int[][]{{B_LEAD, 14}, {B_BLACK, 14}}; break;
            case 19: base = new int[][]{{B_PINK, 50}, {B_BLACK, 24}}; break;
            case 20: base = new int[][]{{B_MOAB, 1}, {B_PINK, 22}}; break;
            default: base = new int[][]{{B_MOAB, 1 + (w - 20) / 2}, {B_PINK, 30}};
        }
        return base;
    }

    void startWave() {
        if (waveActive || gameOver) return;
        wave++;
        waveQueue.clear();
        for (int[] r : waveDef(wave)) waveQueue.add(new int[]{r[0], r[1]});
        waveQueueIdx = 0;
        waveQueueT = 0f;
        waveQueueRemaining = waveQueue.get(0)[1];
        currentBloonType = waveQueue.get(0)[0];
        spawnInterval = wave > 15 ? 0.32f : 0.5f;
        waveActive = true;
        cash += 80 + wave * 8;
        flash("Wave " + wave);
    }

    void flash(String s) { popupMessage = s; popupTimer = 1.6f; }

    // ---------------- UPDATE ----------------
    public void update(float dt) {
        if (state == S_MENU) return;
        if (paused || gameOver) return;
        dt *= speedMult;

        if (waveActive) {
            waveQueueT -= dt;
            if (waveQueueRemaining > 0 && waveQueueT <= 0f) {
                pendingBloons.add(new Bloon(currentBloonType));
                waveQueueRemaining--;
                waveQueueT = spawnInterval;
                if (waveQueueRemaining == 0 && waveQueueIdx + 1 < waveQueue.size()) {
                    waveQueueIdx++;
                    int[] g = waveQueue.get(waveQueueIdx);
                    currentBloonType = g[0];
                    waveQueueRemaining = g[1];
                    waveQueueT = 1.2f;
                }
            }
            if (waveQueueRemaining == 0 && bloons.isEmpty() && pendingBloons.isEmpty()) {
                waveActive = false;
                int bonus = 100 + wave * 15;
                cash += bonus;
                if (wave >= 20) { victory = true; gameOver = true; flash("VICTORY!"); }
                else flash("Complete +$" + bonus);
            }
        }

        // bloons (indexed loop; pending added at end)
        for (int i = 0; i < bloons.size(); i++) {
            Bloon b = bloons.get(i);
            b.update(dt, this);
        }

        // towers
        for (int i = 0; i < towers.size(); i++) towers.get(i).update(dt, this);

        // projectiles
        for (int i = 0; i < projectiles.size(); i++) projectiles.get(i).update(dt, this);

        // merge pending lists (initialize positions so we never draw at (0,0))
        if (!pendingBloons.isEmpty()) {
            for (int i = 0; i < pendingBloons.size(); i++) {
                Bloon nb = pendingBloons.get(i);
                if (nb.dAlong < 0) nb.dAlong = 0;
                posAlongPath(nb.dAlong, nb.pos);
            }
            bloons.addAll(pendingBloons);
            pendingBloons.clear();
        }
        if (!pendingProjectiles.isEmpty()) { projectiles.addAll(pendingProjectiles); pendingProjectiles.clear(); }

        // remove escaped/dead bloons & dead projectiles
        for (int i = bloons.size() - 1; i >= 0; i--) {
            Bloon b = bloons.get(i);
            if (b.escaped) {
                lives -= b.damage;
                bloons.remove(i);
                if (lives <= 0) { lives = 0; gameOver = true; flash("GAME OVER"); }
            } else if (b.dead) {
                bloons.remove(i);
            }
        }
        for (int i = projectiles.size() - 1; i >= 0; i--) {
            if (projectiles.get(i).dead) projectiles.remove(i);
        }

        // floaters
        for (int i = floaters.size() - 1; i >= 0; i--) {
            Floater f = floaters.get(i);
            f.t += dt; f.y -= 32 * dt;
            if (f.t > 1f) floaters.remove(i);
        }

        if (popupTimer > 0) popupTimer -= dt;
    }

    // ---------------- DRAW ----------------
    public void draw(Canvas c) {
        if (c == null) return;
        if (tile <= 0 || screenW <= 0) { c.drawColor(0xFF000000); return; }
        if (state == S_MENU) { drawMenu(c); return; }
        drawGame(c);
    }

    void drawGame(Canvas c) {
        if (bgCache != null) c.drawBitmap(bgCache, 0, 0, null);
        else c.drawColor(0xFF2E7D32);

        // HUD
        drawHUD(c);

        // placement preview
        if (placingTowerType >= 0 && lastTouchY > topPad && lastTouchY < topPad + gameAreaH) {
            int cx = (int) (lastTouchX / tile);
            int cy = (int) ((lastTouchY - topPad) / tileH());
            if (inGrid(cx, cy)) {
                boolean ok = canPlace(cx, cy);
                paint.setShader(null);
                paint.setColor(ok ? 0x6644FF44 : 0x66FF4444);
                c.drawRect(cx * tile, topPad + cy * tileH(), (cx + 1) * tile, topPad + (cy + 1) * tileH(), paint);
                paint.setColor(0x44FFFFFF);
                float pr = new Tower(placingTowerType, 0, 0, 0, 0).baseRange();
                if (pr > screenW) pr = screenW;
                c.drawCircle(gridCenterX(cx), gridCenterY(cy), pr, paint);
            }
        }

        // towers
        for (int i = 0; i < towers.size(); i++) towers.get(i).draw(c, paint, this);

        // bloons (back-to-front by dAlong asc so leaders appear in front)
        for (int i = 0; i < bloons.size(); i++) bloons.get(i).draw(c, paint);

        // projectiles
        for (int i = 0; i < projectiles.size(); i++) projectiles.get(i).draw(c, paint);

        // selected tower range
        if (selectedTower != null) {
            paint.setShader(null);
            paint.setColor(0x22FFFFFF);
            c.drawCircle(selectedTower.x, selectedTower.y, selectedTower.range(), paint);
            paint.setColor(0xAAFFFFFF);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3);
            c.drawCircle(selectedTower.x, selectedTower.y, selectedTower.range(), paint);
            paint.setStyle(Paint.Style.FILL);
        }

        // floaters
        for (int i = 0; i < floaters.size(); i++) {
            Floater f = floaters.get(i);
            int alpha = (int) (255 * (1 - f.t));
            if (alpha < 0) alpha = 0;
            textP.setColor((alpha << 24) | (f.color & 0xFFFFFF));
            textP.setTextSize(tile * 0.5f);
            c.drawText(f.text, f.x, f.y, textP);
        }
        textP.setColor(Color.WHITE);
        textP.setTextSize(tile * 0.42f);

        // bottom UI
        if (selectedTower != null) drawUpgradePanel(c);
        else drawTowerBar(c);

        if (popupTimer > 0 && popupMessage != null) {
            textP.setTextSize(tile * 0.9f);
            float w = textP.measureText(popupMessage);
            paint.setColor(0xCC000000);
            c.drawRoundRect(new RectF(screenW / 2f - w / 2 - 30, topPad + gameAreaH / 2 - tile, screenW / 2f + w / 2 + 30, topPad + gameAreaH / 2 + tile * 0.3f), 20, 20, paint);
            textP.setColor(0xFFFFEB3B);
            c.drawText(popupMessage, screenW / 2f - w / 2, topPad + gameAreaH / 2, textP);
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
            c.drawText(msg, screenW / 2f - w / 2, screenH / 2f - tile, textP);
            textP.setTextSize(tile * 0.55f);
            textP.setColor(Color.WHITE);
            String s2 = "Score: " + score + "  Wave: " + wave;
            float w2 = textP.measureText(s2);
            c.drawText(s2, screenW / 2f - w2 / 2, screenH / 2f, textP);
            // restart/menu buttons
            float by = screenH / 2f + tile * 0.8f;
            drawBtnLabel(c, screenW / 2f - tile * 2.5f, by, tile * 2.2f, tile * 0.9f, "Restart", 0xFF388E3C);
            drawBtnLabel(c, screenW / 2f + tile * 0.3f, by, tile * 2.2f, tile * 0.9f, "Menu", 0xFF1976D2);
            textP.setTextSize(tile * 0.42f);
        }
    }

    void drawHUD(Canvas c) {
        paint.setShader(null);
        LinearGradient lg = new LinearGradient(0, 0, 0, topPad, 0xFF1B5E20, 0xFF2E7D32, Shader.TileMode.CLAMP);
        paint.setShader(lg);
        c.drawRect(0, 0, screenW, topPad, paint);
        paint.setShader(null);
        paint.setColor(0x88000000);
        c.drawRect(0, topPad - 4, screenW, topPad, paint);

        textP.setTextSize(tile * 0.5f);
        textP.setColor(0xFFFFEB3B);
        c.drawText("$ " + cash, 24, topPad * 0.55f, textP);
        textP.setColor(0xFFFF5252);
        c.drawText("♥ " + lives, screenW * 0.33f, topPad * 0.55f, textP);
        textP.setColor(0xFFFFFFFF);
        c.drawText("Wave " + wave + "/20", screenW * 0.57f, topPad * 0.55f, textP);
        textP.setColor(0xFF80DEEA);
        textP.setTextSize(tile * 0.38f);
        c.drawText(currentMap != null ? currentMap.name : "", 24, topPad * 0.92f, textP);
        c.drawText("Score " + score, screenW * 0.33f, topPad * 0.92f, textP);

        float bx = screenW - tile * 1.05f;
        drawBtnLabel(c, bx, 10, tile * 0.95f, tile * 0.5f,
                speedMult == 1f ? ">" : (speedMult == 2f ? ">>" : ">>>"), 0xFF1976D2);
        bx -= tile * 1.05f;
        drawBtnLabel(c, bx, 10, tile * 0.95f, tile * 0.5f, paused ? "▶" : "❚❚", 0xFF455A64);
        bx -= tile * 1.05f;
        drawBtnLabel(c, bx, 10, tile * 0.95f, tile * 0.5f, waveActive ? "..." : "GO", waveActive ? 0xFF757575 : 0xFFE53935);
        bx -= tile * 1.05f;
        drawBtnLabel(c, bx, 10, tile * 0.95f, tile * 0.5f, "≡", 0xFF607D8B);
    }

    void drawBtnLabel(Canvas c, float x, float y, float w, float h, String label, int color) {
        paint.setShader(null);
        paint.setColor(0x55000000);
        c.drawRoundRect(new RectF(x + 2, y + 3, x + w + 2, y + h + 3), 12, 12, paint);
        paint.setColor(color);
        c.drawRoundRect(new RectF(x, y, x + w, y + h), 12, 12, paint);
        paint.setColor(0x33FFFFFF);
        c.drawRoundRect(new RectF(x + 2, y + 2, x + w - 2, y + h * 0.5f), 10, 10, paint);
        textP.setColor(Color.WHITE);
        textP.setTextSize(h * 0.55f);
        float tw = textP.measureText(label);
        c.drawText(label, x + w / 2 - tw / 2, y + h * 0.7f, textP);
        textP.setTextSize(tile * 0.42f);
    }

    void drawTowerBar(Canvas c) {
        float by = topPad + gameAreaH;
        paint.setShader(null);
        LinearGradient lg = new LinearGradient(0, by, 0, screenH, 0xFF37474F, 0xFF263238, Shader.TileMode.CLAMP);
        paint.setShader(lg);
        c.drawRect(0, by, screenW, screenH, paint);
        paint.setShader(null);

        int cols = 4;
        int rows = 2;
        float pad = 6;
        float cw = (screenW - pad * (cols + 1)) / cols;
        float ch = (bottomBar - pad * (rows + 1)) / rows;
        int idx = 0;
        for (int rr = 0; rr < rows; rr++) {
            for (int cc = 0; cc < cols; cc++) {
                float x = pad + cc * (cw + pad);
                float y = by + pad + rr * (ch + pad);
                drawTowerCard(c, x, y, cw, ch, idx);
                idx++;
            }
        }
    }

    void drawTowerCard(Canvas c, float x, float y, float w, float h, int type) {
        boolean sel = placingTowerType == type;
        boolean affordable = cash >= Tower.BASE_COST[type];
        paint.setShader(null);
        paint.setColor(0x44000000);
        c.drawRoundRect(new RectF(x + 2, y + 3, x + w + 2, y + h + 3), 14, 14, paint);
        paint.setColor(sel ? 0xFF1976D2 : (affordable ? 0xFF455A64 : 0xFF263238));
        c.drawRoundRect(new RectF(x, y, x + w, y + h), 14, 14, paint);
        paint.setColor(0x22FFFFFF);
        c.drawRoundRect(new RectF(x + 2, y + 2, x + w - 2, y + h * 0.45f), 12, 12, paint);
        Tower.drawIcon(c, paint, type, x + w / 2, y + h * 0.45f, h * 0.32f);
        textP.setColor(affordable ? Color.WHITE : 0xFF9E9E9E);
        textP.setTextSize(h * 0.18f);
        String n = Tower.NAME[type];
        float tw = textP.measureText(n);
        c.drawText(n, x + w / 2 - tw / 2, y + h * 0.83f, textP);
        textP.setColor(affordable ? 0xFFFFEB3B : 0xFFE57373);
        textP.setTextSize(h * 0.16f);
        String cost = "$" + Tower.BASE_COST[type];
        float tw2 = textP.measureText(cost);
        c.drawText(cost, x + w / 2 - tw2 / 2, y + h * 0.98f, textP);
        textP.setColor(Color.WHITE);
        textP.setTextSize(tile * 0.42f);
    }

    void drawUpgradePanel(Canvas c) {
        float by = topPad + gameAreaH;
        paint.setShader(null);
        LinearGradient lg = new LinearGradient(0, by, 0, screenH, 0xFF263238, 0xFF1A237E, Shader.TileMode.CLAMP);
        paint.setShader(lg);
        c.drawRect(0, by, screenW, screenH, paint);
        paint.setShader(null);

        textP.setColor(0xFFFFEB3B);
        textP.setTextSize(tile * 0.5f);
        c.drawText(Tower.NAME[selectedTower.type] + " (tier "
                + selectedTower.tiers[0] + "/" + selectedTower.tiers[1] + ")",
                16, by + tile * 0.5f, textP);
        textP.setColor(0xFFB0BEC5);
        textP.setTextSize(tile * 0.3f);
        c.drawText("DMG " + selectedTower.damage() + "  Range " + (int) selectedTower.range()
                        + "  Rate " + String.format("%.1f", selectedTower.rate()),
                16, by + tile * 0.85f, textP);

        // two upgrade paths
        float panelTop = by + tile * 1.0f;
        float panelH = bottomBar - tile * 1.2f;
        float w = (screenW - 24 - 24 - 12) / 2f;
        drawUpgradePath(c, 16, panelTop, w, panelH, 0, 0xFFFFEB3B);
        drawUpgradePath(c, 16 + w + 12, panelTop, w, panelH, 1, 0xFF40C4FF);

        // sell button (top-right of panel)
        float bw = tile * 1.2f;
        int sellAmt = selectedTower.totalSpent * 7 / 10;
        drawBtnLabel(c, screenW - bw - 10, by + 10, bw, tile * 0.55f, "Sell $" + sellAmt, 0xFFD84315);
    }

    void drawUpgradePath(Canvas c, float x, float y, float w, float h, int path, int accent) {
        paint.setShader(null);
        paint.setColor(0xFF37474F);
        c.drawRoundRect(new RectF(x, y, x + w, y + h), 14, 14, paint);
        paint.setColor(accent);
        c.drawRoundRect(new RectF(x, y, x + w, y + tile * 0.3f), 14, 14, paint);
        textP.setColor(0xFF000000);
        textP.setTextSize(tile * 0.28f);
        String t = path == 0 ? "Path A" : "Path B";
        c.drawText(t, x + 10, y + tile * 0.23f, textP);

        UpgradeDef next = selectedTower.nextUpgrade(path);
        textP.setColor(Color.WHITE);
        textP.setTextSize(tile * 0.3f);
        if (next == null) {
            String done = "MAX TIER";
            float ww = textP.measureText(done);
            c.drawText(done, x + w / 2 - ww / 2, y + h / 2, textP);
        } else {
            // show name, cost, big buy button
            textP.setTextSize(tile * 0.34f);
            c.drawText(next.name, x + 12, y + tile * 0.7f, textP);
            textP.setTextSize(tile * 0.26f);
            textP.setColor(0xFFB0BEC5);
            String desc = upgradeDesc(next);
            c.drawText(desc, x + 12, y + tile * 1.0f, textP);

            float btY = y + h - tile * 0.65f;
            boolean afford = cash >= next.cost;
            paint.setColor(afford ? 0xFF388E3C : 0xFF616161);
            c.drawRoundRect(new RectF(x + 8, btY, x + w - 8, btY + tile * 0.55f), 12, 12, paint);
            textP.setColor(Color.WHITE);
            textP.setTextSize(tile * 0.3f);
            String label = "Buy $" + next.cost;
            float lw = textP.measureText(label);
            c.drawText(label, x + w / 2 - lw / 2, btY + tile * 0.4f, textP);
        }

        // show tier dots
        for (int i = 0; i < 3; i++) {
            float px = x + w - tile * 0.7f + i * tile * 0.22f;
            float py = y + tile * 0.18f;
            paint.setColor(selectedTower.tiers[path] > i ? accent : 0x55000000);
            c.drawCircle(px, py, tile * 0.07f, paint);
        }
        textP.setColor(Color.WHITE);
        textP.setTextSize(tile * 0.42f);
    }

    String upgradeDesc(UpgradeDef u) {
        StringBuilder sb = new StringBuilder();
        if (u.dmgAdd > 0) sb.append("+").append(u.dmgAdd).append("dmg ");
        if (u.pierceAdd > 0) sb.append("+").append(u.pierceAdd).append("pierce ");
        if (u.rateMult > 1.01f) sb.append("+").append((int) ((u.rateMult - 1f) * 100)).append("% rate ");
        if (u.rangeMult > 1.01f) sb.append("+").append((int) ((u.rangeMult - 1f) * 100)).append("% range ");
        if (u.rateMult < 0.99f) sb.append("slower ");
        if (u.special == UpgradeDef.SP_LEAD_POP) sb.append("pops Lead ");
        if (u.special == UpgradeDef.SP_TRIPLE_SHOT) sb.append("triple shot ");
        if (u.special == UpgradeDef.SP_LASER) sb.append("laser ");
        if (u.special == UpgradeDef.SP_PLASMA) sb.append("plasma ");
        if (u.special == UpgradeDef.SP_FREEZE) sb.append("stronger freeze ");
        if (u.special == UpgradeDef.SP_NINJA_CAMO) sb.append("anti-camo ");
        if (u.special == UpgradeDef.SP_SLOW_DARTS) sb.append("slows ");
        if (u.special == UpgradeDef.SP_BIGGER_BOMBS) sb.append("huge AoE ");
        if (u.special == UpgradeDef.SP_HOMING) sb.append("homing ");
        if (sb.length() == 0) sb.append("upgrade");
        return sb.toString().trim();
    }

    // ---------------- MENU ----------------
    void drawMenu(Canvas c) {
        // background gradient
        LinearGradient lg = new LinearGradient(0, 0, 0, screenH, 0xFF1A237E, 0xFF000000, Shader.TileMode.CLAMP);
        paint.setShader(lg);
        c.drawRect(0, 0, screenW, screenH, paint);
        paint.setShader(null);

        // floating bloons decorations
        java.util.Random rng = new java.util.Random(42);
        for (int i = 0; i < 18; i++) {
            float bx = rng.nextFloat() * screenW;
            float by = rng.nextFloat() * screenH;
            float br = 18 + rng.nextFloat() * 30;
            int col = BLOON_COLOR[rng.nextInt(BLOON_COLOR.length)];
            paint.setColor(0x33FFFFFF);
            c.drawCircle(bx + 3, by + 4, br, paint);
            RadialGradient rg = new RadialGradient(bx - br * 0.3f, by - br * 0.4f, br * 1.3f,
                    Bloon.lighten(col, 0.4f), Bloon.darken(col, 0.5f), Shader.TileMode.CLAMP);
            paint.setShader(rg);
            c.drawCircle(bx, by, br, paint);
            paint.setShader(null);
            paint.setColor(0x88FFFFFF);
            c.drawCircle(bx - br * 0.35f, by - br * 0.4f, br * 0.25f, paint);
        }

        // Title
        textP.setColor(0xFFFFEB3B);
        textP.setTextSize(tile * 1.2f);
        String title = "BLOON BATTLE";
        float tw = textP.measureText(title);
        textP.setColor(0xFF000000);
        c.drawText(title, screenW / 2f - tw / 2 + 4, tile * 1.4f + 4, textP);
        textP.setColor(0xFFFFEB3B);
        c.drawText(title, screenW / 2f - tw / 2, tile * 1.4f, textP);
        textP.setTextSize(tile * 0.42f);
        textP.setColor(0xFFB3E5FC);
        String sub = "Select a world and map";
        tw = textP.measureText(sub);
        c.drawText(sub, screenW / 2f - tw / 2, tile * 1.95f, textP);

        // world tabs
        float tabsY = tile * 2.3f;
        float tabH = tile * 0.85f;
        float tabW = (screenW - 40) / 4f;
        for (int i = 0; i < 4; i++) {
            float x = 20 + i * tabW;
            boolean sel = selectedWorld == i;
            paint.setColor(sel ? worldAccent(i) : 0xFF37474F);
            c.drawRoundRect(new RectF(x + 4, tabsY, x + tabW - 4, tabsY + tabH), 16, 16, paint);
            paint.setColor(0x33FFFFFF);
            c.drawRoundRect(new RectF(x + 6, tabsY + 4, x + tabW - 6, tabsY + tabH * 0.5f), 12, 12, paint);
            textP.setColor(sel ? 0xFFFFFFFF : 0xFFB0BEC5);
            textP.setTextSize(tile * 0.4f);
            String n = MapDef.WORLD_NAME[i];
            float nw = textP.measureText(n);
            c.drawText(n, x + tabW / 2 - nw / 2, tabsY + tabH * 0.65f, textP);
        }

        // map cards
        float cardsTop = tabsY + tabH + tile * 0.4f;
        float pad = 20;
        float cardW = (screenW - pad * 3) / 2f;
        float cardH = tile * 3.2f;
        int idx = 0;
        for (int m = 0; m < MapDef.ALL.length; m++) {
            MapDef map = MapDef.ALL[m];
            if (map.world != selectedWorld) continue;
            int row = idx / 2;
            int col = idx % 2;
            float x = pad + col * (cardW + pad);
            float y = cardsTop + row * (cardH + pad);
            drawMapCard(c, x, y, cardW, cardH, map);
            idx++;
        }
        if (idx == 0) {
            textP.setColor(0xFFB0BEC5);
            textP.setTextSize(tile * 0.4f);
            String s = "No maps in this world yet";
            float ww = textP.measureText(s);
            c.drawText(s, screenW / 2f - ww / 2, cardsTop + tile * 2, textP);
        }

        // footer
        textP.setColor(0xFF607D8B);
        textP.setTextSize(tile * 0.28f);
        String f = "v1.1  •  tap a map to play";
        float fw = textP.measureText(f);
        c.drawText(f, screenW / 2f - fw / 2, screenH - 20, textP);
        textP.setTextSize(tile * 0.42f);
    }

    void drawMapCard(Canvas c, float x, float y, float w, float h, MapDef m) {
        paint.setShader(null);
        paint.setColor(0x66000000);
        c.drawRoundRect(new RectF(x + 4, y + 5, x + w + 4, y + h + 5), 18, 18, paint);
        int top = worldAccent(m.world);
        int bot = darken(top, 0.5f);
        LinearGradient lg = new LinearGradient(0, y, 0, y + h, top, bot, Shader.TileMode.CLAMP);
        paint.setShader(lg);
        c.drawRoundRect(new RectF(x, y, x + w, y + h), 18, 18, paint);
        paint.setShader(null);

        // preview path
        Path p = new Path();
        float px0 = 0, py0 = 0;
        boolean first = true;
        for (float[] wp : m.waypoints) {
            float pxw = x + 16 + (wp[0] / COLS) * (w - 32);
            float pyw = y + tile * 0.7f + (wp[1] / MapDef.MAP_ROWS) * (h - tile * 1.4f);
            if (first) { p.moveTo(pxw, pyw); first = false; }
            else p.lineTo(pxw, pyw);
            px0 = pxw; py0 = pyw;
        }
        paint.setColor(0xFF3E2723);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(tile * 0.18f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        c.drawPath(p, paint);
        paint.setColor(0xFFBCAAA4);
        paint.setStrokeWidth(tile * 0.10f);
        c.drawPath(p, paint);
        paint.setStyle(Paint.Style.FILL);

        // name + difficulty
        textP.setColor(Color.WHITE);
        textP.setTextSize(tile * 0.42f);
        c.drawText(m.name, x + 14, y + tile * 0.5f, textP);
        // stars
        for (int i = 0; i < 5; i++) {
            paint.setColor(i < m.difficulty ? 0xFFFFEB3B : 0x44FFFFFF);
            c.drawCircle(x + w - 18 - (4 - i) * 22, y + tile * 0.35f, 9, paint);
        }
        textP.setColor(0xFFB3E5FC);
        textP.setTextSize(tile * 0.3f);
        c.drawText("PLAY ▶", x + w - tile * 1.3f, y + h - tile * 0.2f, textP);
        textP.setTextSize(tile * 0.42f);
    }

    int worldAccent(int w) {
        switch (w) {
            case MapDef.W_DESERT: return 0xFFFFA000;
            case MapDef.W_SNOW: return 0xFF26C6DA;
            case MapDef.W_LAVA: return 0xFFEF5350;
            default: return 0xFF66BB6A;
        }
    }
    static int darken(int c, float t) {
        int a = (c >> 24) & 0xFF;
        int r = (int) (((c >> 16) & 0xFF) * t);
        int g = (int) (((c >> 8) & 0xFF) * t);
        int b = (int) ((c & 0xFF) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    // ---------------- INPUT ----------------
    public void onTouch(MotionEvent e) {
        lastTouchX = e.getX();
        lastTouchY = e.getY();
        if (e.getAction() != MotionEvent.ACTION_DOWN) return;

        if (state == S_MENU) { onTouchMenu(); return; }
        onTouchGame();
    }

    void onTouchMenu() {
        // world tabs
        float tabsY = tile * 2.3f;
        float tabH = tile * 0.85f;
        float tabW = (screenW - 40) / 4f;
        if (lastTouchY > tabsY && lastTouchY < tabsY + tabH) {
            for (int i = 0; i < 4; i++) {
                float x = 20 + i * tabW;
                if (lastTouchX > x && lastTouchX < x + tabW) {
                    selectedWorld = i;
                    return;
                }
            }
        }
        // map cards
        float cardsTop = tabsY + tabH + tile * 0.4f;
        float pad = 20;
        float cardW = (screenW - pad * 3) / 2f;
        float cardH = tile * 3.2f;
        int idx = 0;
        for (int m = 0; m < MapDef.ALL.length; m++) {
            MapDef map = MapDef.ALL[m];
            if (map.world != selectedWorld) continue;
            int row = idx / 2;
            int col = idx % 2;
            float x = pad + col * (cardW + pad);
            float y = cardsTop + row * (cardH + pad);
            if (lastTouchX > x && lastTouchX < x + cardW
                    && lastTouchY > y && lastTouchY < y + cardH) {
                loadMap(map);
                return;
            }
            idx++;
        }
    }

    void onTouchGame() {
        if (gameOver) {
            float by = screenH / 2f + tile * 0.8f;
            float h = tile * 0.9f;
            if (lastTouchY > by && lastTouchY < by + h) {
                if (lastTouchX > screenW / 2f - tile * 2.5f && lastTouchX < screenW / 2f - tile * 0.3f) {
                    loadMap(currentMap);
                    return;
                } else if (lastTouchX > screenW / 2f + tile * 0.3f && lastTouchX < screenW / 2f + tile * 2.5f) {
                    state = S_MENU;
                    return;
                }
            }
            return;
        }

        // HUD top row
        if (lastTouchY < topPad) {
            float btnY = 10, btnH = tile * 0.5f;
            if (lastTouchY >= btnY && lastTouchY <= btnY + btnH) {
                float bw = tile * 0.95f;
                float right = screenW - bw - 10;
                if (lastTouchX >= right) {
                    speedMult = speedMult == 1f ? 2f : (speedMult == 2f ? 3f : 1f);
                    return;
                }
                right -= bw + (tile * 1.05f - bw);
                if (lastTouchX >= right && lastTouchX < right + bw) { paused = !paused; return; }
                right -= tile * 1.05f;
                if (lastTouchX >= right && lastTouchX < right + bw) {
                    if (!waveActive) startWave();
                    return;
                }
                right -= tile * 1.05f;
                if (lastTouchX >= right && lastTouchX < right + bw) {
                    state = S_MENU;
                    selectedTower = null;
                    placingTowerType = -1;
                    return;
                }
            }
            return;
        }

        // Bottom UI
        float by = topPad + gameAreaH;
        if (lastTouchY > by) {
            if (selectedTower != null) {
                // sell button
                float bw = tile * 1.2f;
                if (lastTouchX > screenW - bw - 10 && lastTouchY > by + 10 && lastTouchY < by + 10 + tile * 0.55f) {
                    sellSelected();
                    return;
                }
                // upgrade paths
                float panelTop = by + tile * 1.0f;
                float panelH = bottomBar - tile * 1.2f;
                float w = (screenW - 24 - 24 - 12) / 2f;
                for (int p2 = 0; p2 < 2; p2++) {
                    float x = 16 + p2 * (w + 12);
                    float btY = panelTop + panelH - tile * 0.65f;
                    if (lastTouchX > x + 8 && lastTouchX < x + w - 8
                            && lastTouchY > btY && lastTouchY < btY + tile * 0.55f) {
                        UpgradeDef u = selectedTower.nextUpgrade(p2);
                        if (u != null && cash >= u.cost) {
                            cash -= u.cost;
                            selectedTower.totalSpent += u.cost;
                            selectedTower.tiers[p2]++;
                        }
                        return;
                    }
                }
                // tap outside to deselect
                if (lastTouchY > panelTop + panelH) selectedTower = null;
                return;
            }

            // tower bar tap
            int colsN = 4, rowsN = 2;
            float pad = 6;
            float cw = (screenW - pad * (colsN + 1)) / colsN;
            float ch = (bottomBar - pad * (rowsN + 1)) / rowsN;
            for (int rr = 0; rr < rowsN; rr++) {
                for (int cc = 0; cc < colsN; cc++) {
                    int type = rr * colsN + cc;
                    if (type >= Tower.TYPE_COUNT) continue;
                    float x = pad + cc * (cw + pad);
                    float y = by + pad + rr * (ch + pad);
                    if (lastTouchX > x && lastTouchX < x + cw
                            && lastTouchY > y && lastTouchY < y + ch) {
                        if (cash >= Tower.BASE_COST[type]) {
                            placingTowerType = (placingTowerType == type) ? -1 : type;
                            selectedTower = null;
                        } else {
                            flash("Need $" + Tower.BASE_COST[type]);
                        }
                        return;
                    }
                }
            }
            return;
        }

        // Play area
        if (placingTowerType >= 0) {
            int cx = (int) (lastTouchX / tile);
            int cy = (int) ((lastTouchY - topPad) / tileH());
            if (inGrid(cx, cy) && canPlace(cx, cy)) {
                Tower t = new Tower(placingTowerType, gridCenterX(cx), gridCenterY(cy), cx, cy);
                t.totalSpent = Tower.BASE_COST[placingTowerType];
                towers.add(t);
                blocked[cx][cy] = true;
                cash -= Tower.BASE_COST[placingTowerType];
                placingTowerType = -1;
            } else {
                placingTowerType = -1;
            }
            return;
        }

        // select tower
        Tower best = null;
        float bestD = tile * 0.6f;
        for (int i = 0; i < towers.size(); i++) {
            Tower t = towers.get(i);
            float d = dist(t.x, t.y, lastTouchX, lastTouchY);
            if (d < bestD) { bestD = d; best = t; }
        }
        selectedTower = best;
    }

    void sellSelected() {
        if (selectedTower == null) return;
        cash += selectedTower.totalSpent * 7 / 10;
        int cx = selectedTower.gridX, cy = selectedTower.gridY;
        towers.remove(selectedTower);
        blocked = new boolean[COLS][MapDef.MAP_ROWS];
        markPathBlocked();
        for (Tower t : towers) blocked[t.gridX][t.gridY] = true;
        selectedTower = null;
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
