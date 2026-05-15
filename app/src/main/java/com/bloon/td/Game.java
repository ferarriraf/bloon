package com.bloon.td;

import android.graphics.Bitmap;
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
    // ---------- screen states ----------
    public static final int S_MAIN = 0;
    public static final int S_MAPS = 1;
    public static final int S_TOWERS = 2;
    public static final int S_PLAYING = 3;
    public static final int S_HOWTO = 4;
    public static final int S_SETTINGS = 5;
    public static final int S_RECORDS = 6;
    int state = S_MAIN;

    // settings (decorative toggles)
    boolean sfxOn = true;
    boolean musicOn = true;
    boolean autoStart = false;
    // per-map best wave (in-session)
    java.util.HashMap<String, Integer> bestWave = new java.util.HashMap<>();

    // ---------- screen ----------
    int screenW = 1920, screenH = 1080;
    static final int COLS = MapDef.COLS;
    static final int ROWS = MapDef.ROWS;
    float tile;
    float hudH;
    float playW, playH;
    float panelX, panelW; // right panel
    float menuAnim = 0f;

    // ---------- map ----------
    int selectedWorld = 0;
    MapDef currentMap;
    final List<PointF> path = new ArrayList<>();
    final float[] segLen = new float[64];
    float totalPathLen;
    boolean[][] blocked;
    Bitmap bgCache;

    // ---------- pre-rendered art ----------
    final Bitmap[] towerBmp = new Bitmap[Tower.TYPE_COUNT];
    final Bitmap[] towerGunBmp = new Bitmap[Tower.TYPE_COUNT];
    final Bitmap[] bloonBmp = new Bitmap[8];
    boolean assetsBuilt = false;
    int assetsTile = -1;

    // ---------- game state ----------
    int cash, lives, wave, score;
    float speedMult = 1f;
    boolean paused = false;
    boolean waveActive = false;
    boolean gameOver = false;
    boolean victory = false;
    int totalCoins = 0; // shown in menu
    // economy / send-bloons (BTD2 eco style)
    int ecoRate = 0;
    float ecoTimer = 0f;
    int rightTab = 0; // 0=towers, 1=send

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
    final Path scratchPath = new Path();

    float totalTime = 0f;

    // tower types
    public static final int T_DART = 0, T_TACK = 1, T_BOMB = 2, T_SNIPER = 3,
            T_NINJA = 4, T_ICE = 5, T_SUPER = 6, T_WIZARD = 7,
            T_GLUE = 8, T_BOOM = 9, T_MORTAR = 10, T_FARM = 11;

    // bloon types
    public static final int B_RED = 0, B_BLUE = 1, B_GREEN = 2, B_YELLOW = 3,
            B_PINK = 4, B_BLACK = 5, B_LEAD = 6, B_MOAB = 7;
    public static final int[] BLOON_COLOR = {
            0xFFE53935, 0xFF1E88E5, 0xFF43A047, 0xFFFDD835,
            0xFFE91E63, 0xFF212121, 0xFF607D8B, 0xFF8E24AA
    };
    public static final float[] BLOON_SPEED = {3.2f, 4.0f, 4.9f, 6.2f, 7.4f, 3.6f, 2.9f, 1.7f};
    public static final int[] BLOON_REWARD = {1, 2, 3, 4, 5, 11, 8, 90};
    public static final int[] BLOON_CHILD = {-1, B_RED, B_BLUE, B_GREEN, B_YELLOW, B_PINK, B_BLACK, -1};
    public static final int[] BLOON_CHILD_N = {0, 1, 1, 1, 1, 2, 2, 0};
    public static final int[] BLOON_HP = {1, 1, 1, 1, 1, 1, 1, 220};
    public static final float[] BLOON_RADIUS = {0.32f, 0.36f, 0.40f, 0.44f, 0.48f, 0.50f, 0.50f, 1.10f};

    public Game() {}

    void resize(int w, int h) {
        if (w <= 0 || h <= 0) return;
        screenW = w; screenH = h;
        hudH = Math.max(70, h * 0.10f);
        // Fit so play area is COLS x ROWS tiles, with at least 2.3 tile wide right panel
        float tA = (h - hudH) / ROWS;
        float tB = w / (COLS + 2.3f);
        tile = Math.min(tA, tB);
        playW = tile * COLS;
        playH = tile * ROWS;
        panelX = playW;
        panelW = w - playW;
        textP.setColor(Color.WHITE);
        textP.setTextSize(tile * 0.38f);
        if (currentMap != null) reloadMapGeometry();
        buildAssets();
    }

    void buildAssets() {
        int t = (int) Math.max(40, tile);
        if (assetsBuilt && assetsTile == t) return;
        assetsTile = t;
        // bloons
        for (int i = 0; i < bloonBmp.length; i++) {
            float r = Math.max(18, BLOON_RADIUS[i] * tile);
            int side = (int) (r * 2.6f);
            Bitmap b = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            drawBloonBitmap(c, side / 2f, side / 2f, r, i);
            if (bloonBmp[i] != null) bloonBmp[i].recycle();
            bloonBmp[i] = b;
        }
        // tower base + gun
        float r = tile * 0.45f;
        int side = (int) (r * 2.8f);
        for (int ty = 0; ty < Tower.TYPE_COUNT; ty++) {
            Bitmap b = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            drawTowerBase(c, side / 2f, side / 2f, r, ty);
            if (towerBmp[ty] != null) towerBmp[ty].recycle();
            towerBmp[ty] = b;

            Bitmap g = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888);
            Canvas gc = new Canvas(g);
            drawTowerGun(gc, side / 2f, side / 2f, r, ty);
            if (towerGunBmp[ty] != null) towerGunBmp[ty].recycle();
            towerGunBmp[ty] = g;
        }
        assetsBuilt = true;
    }

    // ---------------- MAP LOADING ----------------
    void loadMap(MapDef m) {
        currentMap = m;
        cash = 750;
        lives = 120;
        wave = 0;
        score = 0;
        ecoRate = 0;
        ecoTimer = 0f;
        rightTab = 0;
        speedMult = 1f;
        paused = false;
        waveActive = false;
        gameOver = false; victory = false;
        bloons.clear(); towers.clear(); projectiles.clear(); floaters.clear();
        pendingBloons.clear(); pendingProjectiles.clear();
        selectedTower = null; placingTowerType = -1;
        if (tile > 0) reloadMapGeometry();
        state = S_PLAYING;
        flash(m.name);
    }

    void reloadMapGeometry() {
        path.clear();
        for (float[] wp : currentMap.waypoints) {
            float px = (wp[0] + 0.5f) * tile;
            float py = hudH + (wp[1] + 0.5f) * tile;
            path.add(new PointF(px, py));
        }
        totalPathLen = 0;
        int maxSeg = Math.min(segLen.length, Math.max(0, path.size() - 1));
        for (int i = 0; i < maxSeg; i++) {
            float d = dist(path.get(i), path.get(i + 1));
            segLen[i] = d;
            totalPathLen += d;
        }
        blocked = new boolean[COLS][ROWS];
        markPathBlocked();
        bakeBackground();
    }

    void markPathBlocked() {
        for (int i = 0; i < path.size() - 1; i++) {
            PointF a = path.get(i), b = path.get(i + 1);
            float d = segLen[i];
            int steps = (int) (d / (tile * 0.18f)) + 1;
            for (int s = 0; s <= steps; s++) {
                float t = (float) s / steps;
                float x = a.x + (b.x - a.x) * t;
                float y = a.y + (b.y - a.y) * t;
                int cx = (int) (x / tile);
                int cy = (int) ((y - hudH) / tile);
                if (inGrid(cx, cy)) blocked[cx][cy] = true;
            }
        }
    }

    boolean inGrid(int cx, int cy) { return cx >= 0 && cx < COLS && cy >= 0 && cy < ROWS; }

    float gridCenterX(int c) { return (c + 0.5f) * tile; }
    float gridCenterY(int r) { return hudH + (r + 0.5f) * tile; }

    boolean canPlace(int cx, int cy) {
        if (!inGrid(cx, cy)) return false;
        if (blocked[cx][cy]) return false;
        for (int i = 0; i < towers.size(); i++) {
            Tower t = towers.get(i);
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
        if (path.size() < 2) { out.x = 0; out.y = 0; return out; }
        float remaining = dAlong;
        for (int i = 0; i < path.size() - 1; i++) {
            float L = segLen[i];
            if (remaining <= L) {
                PointF a = path.get(i), b = path.get(i + 1);
                float t = L > 0 ? remaining / L : 0;
                out.x = a.x + (b.x - a.x) * t;
                out.y = a.y + (b.y - a.y) * t;
                return out;
            }
            remaining -= L;
        }
        PointF last = path.get(path.size() - 1);
        out.x = last.x; out.y = last.y;
        return out;
    }

    // ---------------- ASSET PAINTING ----------------
    void drawBloonBitmap(Canvas c, float cx, float cy, float r, int type) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        // shadow
        p.setColor(0x55000000);
        c.drawCircle(cx + 3, cy + 5, r, p);

        int col = BLOON_COLOR[type];
        if (type == B_MOAB) {
            RadialGradient rg = new RadialGradient(cx - r * 0.35f, cy - r * 0.4f, r * 1.6f,
                    0xFFE1BEE7, 0xFF311B92, Shader.TileMode.CLAMP);
            p.setShader(rg);
            c.drawCircle(cx, cy, r, p);
            p.setShader(null);
            p.setColor(0xFF4A148C);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(3, r * 0.05f));
            c.drawCircle(cx, cy, r * 0.72f, p);
            c.drawLine(cx - r * 0.72f, cy, cx + r * 0.72f, cy, p);
            p.setStyle(Paint.Style.FILL);
            // engine
            p.setColor(0xFFFF7043);
            c.drawCircle(cx - r * 0.85f, cy, r * 0.18f, p);
            p.setColor(0xFFFFEB3B);
            c.drawCircle(cx - r * 0.88f, cy, r * 0.1f, p);
            // text
            p.setColor(0xFFFFEB3B);
            p.setTextSize(r * 0.42f);
            p.setFakeBoldText(true);
            float tw = p.measureText("MOAB");
            c.drawText("MOAB", cx - tw / 2, cy + r * 0.15f, p);
            p.setFakeBoldText(false);
        } else {
            int light = Bloon.lighten(col, 0.5f);
            int dark = Bloon.darken(col, 0.55f);
            RadialGradient rg = new RadialGradient(cx - r * 0.35f, cy - r * 0.45f, r * 1.4f,
                    light, dark, Shader.TileMode.CLAMP);
            p.setShader(rg);
            c.drawCircle(cx, cy, r, p);
            p.setShader(null);
            p.setColor(dark);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(2, r * 0.05f));
            c.drawCircle(cx, cy, r, p);
            p.setStyle(Paint.Style.FILL);
            // highlight
            p.setColor(0xCCFFFFFF);
            c.drawCircle(cx - r * 0.35f, cy - r * 0.4f, r * 0.28f, p);
            p.setColor(0x88FFFFFF);
            c.drawCircle(cx - r * 0.15f, cy - r * 0.15f, r * 0.15f, p);
            // knot
            p.setColor(dark);
            Path knot = new Path();
            knot.moveTo(cx - r * 0.2f, cy + r * 0.95f);
            knot.lineTo(cx + r * 0.2f, cy + r * 0.95f);
            knot.lineTo(cx, cy + r * 1.2f);
            knot.close();
            c.drawPath(knot, p);
            if (type == B_LEAD) {
                p.setColor(0xFF263238);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(r * 0.18f);
                c.drawCircle(cx, cy, r * 0.6f, p);
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xFFB0BEC5);
                for (int k = 0; k < 6; k++) {
                    double a = k * Math.PI / 3;
                    c.drawCircle(cx + (float) Math.cos(a) * r * 0.5f,
                            cy + (float) Math.sin(a) * r * 0.5f, r * 0.07f, p);
                }
            } else if (type == B_BLACK) {
                p.setColor(0xFF000000);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(r * 0.12f);
                c.drawCircle(cx, cy, r * 0.95f, p);
                p.setStyle(Paint.Style.FILL);
            }
        }
    }

    void drawTowerBase(Canvas c, float cx, float cy, float r, int type) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        // shadow
        p.setColor(0x66000000);
        c.drawOval(new RectF(cx - r * 1.0f, cy + r * 0.78f, cx + r * 1.0f, cy + r * 1.02f), p);
        // wood platform
        p.setColor(0xFF3E2723);
        c.drawCircle(cx, cy + r * 0.55f, r * 0.95f, p);
        p.setColor(0xFF8D6E63);
        c.drawCircle(cx, cy + r * 0.55f, r * 0.85f, p);
        p.setColor(0xFFA1887F);
        c.drawOval(new RectF(cx - r * 0.7f, cy + r * 0.35f, cx + r * 0.7f, cy + r * 0.65f), p);
        p.setColor(0xFF6D4C41);
        for (int k = -2; k <= 2; k++) {
            c.drawLine(cx + k * r * 0.3f, cy + r * 0.4f, cx + k * r * 0.3f, cy + r * 0.7f, p);
        }

        // Tack and Ice are pure devices (no character)
        if (type == T_TACK || type == T_ICE) {
            int c1 = Tower.BODY_COLOR[type];
            int c2 = Tower.BODY_COLOR2[type];
            RadialGradient bg = new RadialGradient(cx - r * 0.3f, cy - r * 0.3f, r * 1.4f,
                    c1, c2, Shader.TileMode.CLAMP);
            p.setShader(bg);
            c.drawCircle(cx, cy, r * 0.7f, p);
            p.setShader(null);
            p.setColor(Tower.darken(c2, 0.7f));
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(2, r * 0.07f));
            c.drawCircle(cx, cy, r * 0.7f, p);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0x77FFFFFF);
            c.drawCircle(cx - r * 0.25f, cy - r * 0.3f, r * 0.2f, p);
            return;
        }

        int c1 = Tower.BODY_COLOR[type];
        int c2 = Tower.BODY_COLOR2[type];

        // ----- SEAL BODY -----
        // back fluke peeks behind
        p.setColor(Tower.darken(c2, 0.7f));
        Path fluke = new Path();
        fluke.moveTo(cx - r * 0.55f, cy + r * 0.45f);
        fluke.quadTo(cx - r * 0.95f, cy + r * 0.55f, cx - r * 0.7f, cy + r * 0.7f);
        fluke.lineTo(cx - r * 0.4f, cy + r * 0.55f);
        fluke.close();
        c.drawPath(fluke, p);
        Path fluke2 = new Path();
        fluke2.moveTo(cx + r * 0.55f, cy + r * 0.45f);
        fluke2.quadTo(cx + r * 0.95f, cy + r * 0.55f, cx + r * 0.7f, cy + r * 0.7f);
        fluke2.lineTo(cx + r * 0.4f, cy + r * 0.55f);
        fluke2.close();
        c.drawPath(fluke2, p);

        // body oval with gradient
        RadialGradient bodyGrad = new RadialGradient(cx - r * 0.2f, cy - r * 0.1f, r * 1.4f,
                c1, c2, Shader.TileMode.CLAMP);
        p.setShader(bodyGrad);
        c.drawOval(new RectF(cx - r * 0.55f, cy - r * 0.15f, cx + r * 0.55f, cy + r * 0.6f), p);
        p.setShader(null);
        p.setColor(Tower.darken(c2, 0.55f));
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(2, r * 0.05f));
        c.drawOval(new RectF(cx - r * 0.55f, cy - r * 0.15f, cx + r * 0.55f, cy + r * 0.6f), p);
        p.setStyle(Paint.Style.FILL);
        // belly
        p.setColor(0xFFECEFF1);
        c.drawOval(new RectF(cx - r * 0.32f, cy + r * 0.05f, cx + r * 0.32f, cy + r * 0.55f), p);
        p.setColor(0xFFCFD8DC);
        c.drawOval(new RectF(cx - r * 0.25f, cy + r * 0.3f, cx + r * 0.25f, cy + r * 0.52f), p);

        // ----- HEAD -----
        p.setShader(bodyGrad);
        c.drawCircle(cx, cy - r * 0.35f, r * 0.45f, p);
        p.setShader(null);
        p.setColor(Tower.darken(c2, 0.55f));
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(2, r * 0.05f));
        c.drawCircle(cx, cy - r * 0.35f, r * 0.45f, p);
        p.setStyle(Paint.Style.FILL);
        // face oval (light)
        p.setColor(0xFFECEFF1);
        c.drawOval(new RectF(cx - r * 0.3f, cy - r * 0.45f, cx + r * 0.3f, cy - r * 0.1f), p);
        // muzzle puffs (rounded)
        p.setColor(0xFFFFFFFF);
        c.drawCircle(cx - r * 0.09f, cy - r * 0.18f, r * 0.09f, p);
        c.drawCircle(cx + r * 0.09f, cy - r * 0.18f, r * 0.09f, p);
        // eyes
        p.setColor(0xFF263238);
        c.drawCircle(cx - r * 0.13f, cy - r * 0.36f, r * 0.075f, p);
        c.drawCircle(cx + r * 0.13f, cy - r * 0.36f, r * 0.075f, p);
        // eye shine
        p.setColor(0xFFFFFFFF);
        c.drawCircle(cx - r * 0.11f, cy - r * 0.39f, r * 0.025f, p);
        c.drawCircle(cx + r * 0.15f, cy - r * 0.39f, r * 0.025f, p);
        // nose
        p.setColor(0xFF263238);
        c.drawCircle(cx, cy - r * 0.22f, r * 0.05f, p);
        p.setColor(0xFFFFFFFF);
        c.drawCircle(cx - r * 0.012f, cy - r * 0.235f, r * 0.018f, p);
        // whisker dots
        p.setColor(0xFF455A64);
        for (int i = 0; i < 3; i++) {
            float dx = r * (0.15f + i * 0.05f);
            c.drawCircle(cx - dx, cy - r * 0.16f, r * 0.013f, p);
            c.drawCircle(cx + dx, cy - r * 0.16f, r * 0.013f, p);
        }
        // mouth
        p.setColor(0xFF263238);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1.5f, r * 0.022f));
        Path mouth = new Path();
        mouth.moveTo(cx - r * 0.05f, cy - r * 0.09f);
        mouth.quadTo(cx, cy - r * 0.04f, cx + r * 0.05f, cy - r * 0.09f);
        c.drawPath(mouth, p);
        p.setStyle(Paint.Style.FILL);

        // role-specific accessory (hat / mask / band)
        drawTowerAccessory(c, p, cx, cy, r, type);
    }

    void drawTowerAccessory(Canvas c, Paint p, float cx, float cy, float r, int type) {
        switch (type) {
            case T_SNIPER: {
                // dark goggles
                p.setColor(0xFF263238);
                c.drawRect(cx - r * 0.32f, cy - r * 0.42f, cx + r * 0.32f, cy - r * 0.3f, p);
                p.setColor(0xFF1A237E);
                c.drawCircle(cx - r * 0.13f, cy - r * 0.36f, r * 0.08f, p);
                c.drawCircle(cx + r * 0.13f, cy - r * 0.36f, r * 0.08f, p);
                p.setColor(0xFFFFEB3B);
                c.drawCircle(cx - r * 0.11f, cy - r * 0.39f, r * 0.02f, p);
                c.drawCircle(cx + r * 0.15f, cy - r * 0.39f, r * 0.02f, p);
                break;
            }
            case T_NINJA: {
                // red headband
                p.setColor(0xFFE53935);
                c.drawRect(cx - r * 0.4f, cy - r * 0.52f, cx + r * 0.4f, cy - r * 0.42f, p);
                p.setColor(0xFFFFFFFF);
                Path k = new Path();
                k.moveTo(cx + r * 0.0f, cy - r * 0.51f);
                k.lineTo(cx + r * 0.05f, cy - r * 0.42f);
                k.lineTo(cx - r * 0.05f, cy - r * 0.42f);
                k.close();
                c.drawPath(k, p);
                // bandana tails
                p.setColor(0xFFE53935);
                c.drawRect(cx - r * 0.45f, cy - r * 0.5f, cx - r * 0.4f, cy - r * 0.35f, p);
                c.drawRect(cx + r * 0.4f, cy - r * 0.5f, cx + r * 0.45f, cy - r * 0.35f, p);
                break;
            }
            case T_SUPER: {
                // red cape behind body
                p.setColor(0xFFE53935);
                Path cape = new Path();
                cape.moveTo(cx - r * 0.4f, cy + r * 0.0f);
                cape.lineTo(cx - r * 0.7f, cy + r * 0.55f);
                cape.lineTo(cx + r * 0.7f, cy + r * 0.55f);
                cape.lineTo(cx + r * 0.4f, cy + r * 0.0f);
                cape.close();
                c.drawPath(cape, p);
                // yellow mask
                p.setColor(0xFFFFEB3B);
                c.drawRect(cx - r * 0.32f, cy - r * 0.43f, cx + r * 0.32f, cy - r * 0.3f, p);
                p.setColor(0xFFE53935);
                c.drawRect(cx - r * 0.32f, cy - r * 0.43f, cx + r * 0.32f, cy - r * 0.38f, p);
                p.setColor(0xFFFFFFFF);
                c.drawRect(cx - r * 0.18f, cy - r * 0.38f, cx + r * 0.18f, cy - r * 0.32f, p);
                p.setColor(0xFF1A237E);
                c.drawCircle(cx - r * 0.13f, cy - r * 0.36f, r * 0.05f, p);
                c.drawCircle(cx + r * 0.13f, cy - r * 0.36f, r * 0.05f, p);
                break;
            }
            case T_WIZARD: {
                // pointy hat
                p.setColor(0xFF311B92);
                Path hat = new Path();
                hat.moveTo(cx - r * 0.45f, cy - r * 0.45f);
                hat.lineTo(cx + r * 0.45f, cy - r * 0.45f);
                hat.lineTo(cx + r * 0.05f, cy - r * 1.05f);
                hat.close();
                c.drawPath(hat, p);
                p.setColor(0xFF673AB7);
                c.drawRect(cx - r * 0.48f, cy - r * 0.5f, cx + r * 0.48f, cy - r * 0.4f, p);
                // stars on hat
                p.setColor(0xFFFFEB3B);
                c.drawCircle(cx - r * 0.1f, cy - r * 0.7f, r * 0.05f, p);
                c.drawCircle(cx + r * 0.05f, cy - r * 0.5f, r * 0.03f, p);
                // hat tip
                p.setColor(0xFFFFEB3B);
                c.drawCircle(cx + r * 0.05f, cy - r * 1.05f, r * 0.07f, p);
                break;
            }
            case T_BOMB: {
                // small helmet
                p.setColor(0xFF263238);
                c.drawArc(new RectF(cx - r * 0.45f, cy - r * 0.65f, cx + r * 0.45f, cy - r * 0.25f), 180, 180, false, p);
                p.setColor(0xFFB71C1C);
                c.drawRect(cx - r * 0.45f, cy - r * 0.48f, cx + r * 0.45f, cy - r * 0.42f, p);
                // emblem
                p.setColor(0xFFFFEB3B);
                c.drawCircle(cx, cy - r * 0.55f, r * 0.07f, p);
                break;
            }
            case T_DART: {
                // green bandana
                p.setColor(0xFF1B5E20);
                c.drawRect(cx - r * 0.4f, cy - r * 0.5f, cx + r * 0.4f, cy - r * 0.4f, p);
                p.setColor(0xFF66BB6A);
                Path leaf = new Path();
                leaf.moveTo(cx + r * 0.3f, cy - r * 0.5f);
                leaf.lineTo(cx + r * 0.42f, cy - r * 0.62f);
                leaf.lineTo(cx + r * 0.4f, cy - r * 0.4f);
                leaf.close();
                c.drawPath(leaf, p);
                break;
            }
            case T_GLUE: {
                // yellow chef hat
                p.setColor(0xFFFFEB3B);
                Path puff = new Path();
                puff.addCircle(cx - r * 0.18f, cy - r * 0.65f, r * 0.18f, Path.Direction.CW);
                puff.addCircle(cx + r * 0.18f, cy - r * 0.65f, r * 0.18f, Path.Direction.CW);
                puff.addCircle(cx, cy - r * 0.78f, r * 0.2f, Path.Direction.CW);
                c.drawPath(puff, p);
                p.setColor(0xFFFBC02D);
                c.drawRect(cx - r * 0.32f, cy - r * 0.5f, cx + r * 0.32f, cy - r * 0.4f, p);
                break;
            }
            case T_BOOM: {
                // tribal headband + feather
                p.setColor(0xFF4E342E);
                c.drawRect(cx - r * 0.4f, cy - r * 0.5f, cx + r * 0.4f, cy - r * 0.4f, p);
                // feather
                p.setColor(0xFFE53935);
                Path feather = new Path();
                feather.moveTo(cx + r * 0.0f, cy - r * 0.5f);
                feather.lineTo(cx + r * 0.18f, cy - r * 0.9f);
                feather.lineTo(cx - r * 0.05f, cy - r * 0.5f);
                feather.close();
                c.drawPath(feather, p);
                p.setColor(0xFFFFEB3B);
                feather.reset();
                feather.moveTo(cx + r * 0.0f, cy - r * 0.5f);
                feather.lineTo(cx + r * 0.08f, cy - r * 0.85f);
                feather.lineTo(cx - r * 0.02f, cy - r * 0.55f);
                feather.close();
                c.drawPath(feather, p);
                break;
            }
            case T_MORTAR: {
                // army cap
                p.setColor(0xFF2E7D32);
                c.drawArc(new RectF(cx - r * 0.5f, cy - r * 0.65f, cx + r * 0.5f, cy - r * 0.25f), 180, 180, false, p);
                p.setColor(0xFF1B5E20);
                c.drawRect(cx - r * 0.5f, cy - r * 0.42f, cx + r * 0.5f, cy - r * 0.32f, p);
                // visor
                p.setColor(0xFF1B5E20);
                c.drawRect(cx - r * 0.4f, cy - r * 0.42f, cx + r * 0.4f, cy - r * 0.36f, p);
                // star
                p.setColor(0xFFFFEB3B);
                c.drawCircle(cx, cy - r * 0.55f, r * 0.06f, p);
                break;
            }
            case T_FARM: {
                // straw hat
                p.setColor(0xFFFFC107);
                c.drawOval(new RectF(cx - r * 0.6f, cy - r * 0.55f, cx + r * 0.6f, cy - r * 0.38f), p);
                p.setColor(0xFFFFA726);
                c.drawOval(new RectF(cx - r * 0.3f, cy - r * 0.75f, cx + r * 0.3f, cy - r * 0.45f), p);
                p.setColor(0xFFE53935);
                c.drawRect(cx - r * 0.32f, cy - r * 0.55f, cx + r * 0.32f, cy - r * 0.48f, p);
                break;
            }
        }
    }

    /** Draws a cute seal face centered at (cx, cy) sized by r (face radius). */
    void drawSealFace(Canvas c, Paint p, float cx, float cy, float r) {
        // face oval (head + muzzle)
        p.setShader(null);
        p.setColor(0xFFECEFF1);
        c.drawOval(new RectF(cx - r * 0.55f, cy - r * 0.45f, cx + r * 0.55f, cy + r * 0.4f), p);
        // shading under chin
        p.setColor(0xFFCFD8DC);
        c.drawOval(new RectF(cx - r * 0.35f, cy + r * 0.05f, cx + r * 0.35f, cy + r * 0.38f), p);
        // muzzle puffs (white)
        p.setColor(0xFFFFFFFF);
        c.drawCircle(cx - r * 0.12f, cy + r * 0.14f, r * 0.12f, p);
        c.drawCircle(cx + r * 0.12f, cy + r * 0.14f, r * 0.12f, p);
        // eyes
        p.setColor(0xFF263238);
        c.drawCircle(cx - r * 0.2f, cy - r * 0.15f, r * 0.1f, p);
        c.drawCircle(cx + r * 0.2f, cy - r * 0.15f, r * 0.1f, p);
        p.setColor(0xFFFFFFFF);
        c.drawCircle(cx - r * 0.17f, cy - r * 0.18f, r * 0.04f, p);
        c.drawCircle(cx + r * 0.23f, cy - r * 0.18f, r * 0.04f, p);
        // nose (small black)
        p.setColor(0xFF263238);
        c.drawCircle(cx, cy + r * 0.06f, r * 0.06f, p);
        p.setColor(0xFFFFFFFF);
        c.drawCircle(cx - r * 0.02f, cy + r * 0.04f, r * 0.02f, p);
        // whisker dots
        p.setColor(0xFF455A64);
        for (int i = 0; i < 3; i++) {
            float dx = r * (0.18f + i * 0.07f);
            float dy = r * (0.16f + i * 0.02f);
            c.drawCircle(cx - dx, cy + dy, r * 0.018f, p);
            c.drawCircle(cx + dx, cy + dy, r * 0.018f, p);
        }
        // mouth (tiny smile under nose)
        p.setColor(0xFF263238);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1.5f, r * 0.025f));
        Path mouth = new Path();
        mouth.moveTo(cx - r * 0.07f, cy + r * 0.2f);
        mouth.quadTo(cx, cy + r * 0.27f, cx + r * 0.07f, cy + r * 0.2f);
        c.drawPath(mouth, p);
        p.setStyle(Paint.Style.FILL);
        // little flippers on each cheek
        p.setColor(Tower.darken(0xFFCFD8DC, 0.85f));
        c.drawOval(new RectF(cx - r * 0.68f, cy + r * 0.0f, cx - r * 0.42f, cy + r * 0.28f), p);
        c.drawOval(new RectF(cx + r * 0.42f, cy + r * 0.0f, cx + r * 0.68f, cy + r * 0.28f), p);
    }

    void drawTowerGun(Canvas c, float cx, float cy, float r, int type) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        // Device-only towers
        if (type == T_TACK) {
            tackGun(c, p, cx, cy, r);
            return;
        }
        if (type == T_ICE) {
            iceGun(c, p, cx, cy, r);
            return;
        }
        if (type == T_FARM) {
            // farm has no rotating part — empty gun bitmap
            return;
        }

        // Generic SEAL flippers holding a weapon — drawn pointing right (+x direction)
        int bodyC = Tower.darken(Tower.BODY_COLOR2[type], 0.85f);
        int bodyL = Tower.darken(Tower.BODY_COLOR[type], 0.9f);
        // upper flipper
        p.setColor(bodyC);
        Path upper = new Path();
        upper.moveTo(cx + r * 0.05f, cy - r * 0.2f);
        upper.quadTo(cx + r * 0.4f, cy - r * 0.3f, cx + r * 0.75f, cy - r * 0.1f);
        upper.lineTo(cx + r * 0.75f, cy + r * 0.05f);
        upper.quadTo(cx + r * 0.35f, cy - r * 0.15f, cx + r * 0.05f, cy - r * 0.05f);
        upper.close();
        c.drawPath(upper, p);
        // lower flipper
        Path lower = new Path();
        lower.moveTo(cx + r * 0.05f, cy + r * 0.2f);
        lower.quadTo(cx + r * 0.4f, cy + r * 0.3f, cx + r * 0.75f, cy + r * 0.1f);
        lower.lineTo(cx + r * 0.75f, cy - r * 0.05f);
        lower.quadTo(cx + r * 0.35f, cy + r * 0.15f, cx + r * 0.05f, cy + r * 0.05f);
        lower.close();
        c.drawPath(lower, p);
        // flipper highlights
        p.setColor(bodyL);
        c.drawOval(new RectF(cx + r * 0.5f, cy - r * 0.18f, cx + r * 0.8f, cy - r * 0.0f), p);
        c.drawOval(new RectF(cx + r * 0.5f, cy + r * 0.0f, cx + r * 0.8f, cy + r * 0.18f), p);

        // weapon drawn in the grip at ~(cx + r*0.8, cy)
        float gx = cx + r * 0.75f;
        switch (type) {
            case T_DART: {
                // wood stock
                p.setColor(0xFF5D4037);
                c.drawRect(gx - r * 0.18f, cy - r * 0.1f, gx + r * 0.3f, cy + r * 0.1f, p);
                // metal barrel
                p.setColor(0xFF424242);
                c.drawRect(gx - r * 0.3f, cy - r * 0.06f, gx + r * 0.55f, cy + r * 0.06f, p);
                // dart tip
                Path tri = new Path();
                tri.moveTo(gx + r * 0.55f, cy - r * 0.2f);
                tri.lineTo(gx + r * 0.85f, cy);
                tri.lineTo(gx + r * 0.55f, cy + r * 0.2f);
                tri.close();
                p.setColor(0xFFFAFAFA);
                c.drawPath(tri, p);
                p.setColor(0xFF263238);
                c.drawLine(gx + r * 0.55f, cy - r * 0.2f, gx + r * 0.85f, cy, p);
                c.drawLine(gx + r * 0.85f, cy, gx + r * 0.55f, cy + r * 0.2f, p);
                break;
            }
            case T_BOMB: {
                // cannon
                p.setColor(0xFF263238);
                RectF barrel = new RectF(gx - r * 0.15f, cy - r * 0.22f, gx + r * 0.55f, cy + r * 0.22f);
                c.drawRoundRect(barrel, r * 0.1f, r * 0.1f, p);
                p.setColor(0xFF455A64);
                c.drawCircle(gx + r * 0.55f, cy, r * 0.26f, p);
                p.setColor(0xFF263238);
                c.drawCircle(gx + r * 0.55f, cy, r * 0.18f, p);
                p.setColor(0xFFFFCDD2);
                c.drawCircle(gx - r * 0.12f, cy - r * 0.18f, r * 0.07f, p);
                break;
            }
            case T_SNIPER: {
                // rifle
                p.setColor(0xFF263238);
                c.drawRect(gx - r * 0.3f, cy - r * 0.1f, gx + r * 0.7f, cy + r * 0.1f, p);
                p.setColor(0xFF424242);
                c.drawRect(gx + r * 0.55f, cy - r * 0.18f, gx + r * 0.7f, cy + r * 0.18f, p);
                p.setColor(0xFF1A237E);
                // scope
                c.drawRect(gx - r * 0.1f, cy - r * 0.2f, gx + r * 0.2f, cy - r * 0.05f, p);
                p.setColor(0xFFFFEB3B);
                c.drawCircle(gx + r * 0.05f, cy - r * 0.13f, r * 0.05f, p);
                p.setColor(0xFFB71C1C);
                c.drawCircle(gx + r * 0.05f, cy - r * 0.13f, r * 0.03f, p);
                break;
            }
            case T_NINJA: {
                // shuriken thrown in front
                p.setColor(0xFFB0BEC5);
                for (int i = 0; i < 4; i++) {
                    c.save();
                    c.rotate(i * 90, gx + r * 0.3f, cy);
                    c.drawRect(gx + r * 0.15f, cy - r * 0.05f, gx + r * 0.5f, cy + r * 0.05f, p);
                    c.restore();
                }
                p.setColor(0xFF263238);
                c.drawCircle(gx + r * 0.3f, cy, r * 0.08f, p);
                p.setColor(0xFFE0E0E0);
                c.drawCircle(gx + r * 0.3f, cy, r * 0.04f, p);
                break;
            }
            case T_SUPER: {
                // glowing fist / energy ball
                p.setColor(0x66FFEB3B);
                c.drawCircle(gx + r * 0.3f, cy, r * 0.32f, p);
                p.setColor(0xFFFFEB3B);
                c.drawCircle(gx + r * 0.3f, cy, r * 0.22f, p);
                p.setColor(0xFFFFFFFF);
                c.drawCircle(gx + r * 0.22f, cy - r * 0.05f, r * 0.1f, p);
                p.setColor(0xFFFF6F00);
                c.drawCircle(gx + r * 0.45f, cy, r * 0.06f, p);
                break;
            }
            case T_WIZARD: {
                // magic staff
                p.setColor(0xFF6D4C41);
                c.drawRect(gx - r * 0.15f, cy - r * 0.05f, gx + r * 0.55f, cy + r * 0.05f, p);
                p.setColor(0xFF4E342E);
                c.drawRect(gx - r * 0.15f, cy - r * 0.07f, gx + r * 0.0f, cy - r * 0.05f, p);
                // orb
                p.setColor(0x88E040FB);
                c.drawCircle(gx + r * 0.6f, cy, r * 0.22f, p);
                p.setColor(0xFFE040FB);
                c.drawCircle(gx + r * 0.6f, cy, r * 0.15f, p);
                p.setColor(0xFFFFFFFF);
                c.drawCircle(gx + r * 0.58f, cy - r * 0.05f, r * 0.07f, p);
                break;
            }
            case T_GLUE: {
                // glue gun shape
                p.setColor(0xFFFBC02D);
                RectF g1 = new RectF(gx - r * 0.18f, cy - r * 0.16f, gx + r * 0.45f, cy + r * 0.16f);
                c.drawRoundRect(g1, r * 0.06f, r * 0.06f, p);
                p.setColor(0xFFFFEB3B);
                c.drawRoundRect(new RectF(gx - r * 0.14f, cy - r * 0.12f, gx + r * 0.41f, cy + r * 0.12f), r * 0.05f, r * 0.05f, p);
                // nozzle
                p.setColor(0xFFFFA000);
                c.drawCircle(gx + r * 0.5f, cy, r * 0.1f, p);
                // glue drip
                p.setColor(0xFFFFEB3B);
                c.drawCircle(gx + r * 0.65f, cy + r * 0.06f, r * 0.07f, p);
                c.drawCircle(gx + r * 0.72f, cy + r * 0.12f, r * 0.04f, p);
                break;
            }
            case T_BOOM: {
                // boomerang held
                p.setColor(0xFF5D4037);
                Path bm = new Path();
                bm.moveTo(gx + r * 0.1f, cy - r * 0.3f);
                bm.quadTo(gx + r * 0.55f, cy, gx + r * 0.1f, cy + r * 0.3f);
                bm.quadTo(gx + r * 0.3f, cy, gx + r * 0.1f, cy - r * 0.3f);
                bm.close();
                c.drawPath(bm, p);
                p.setColor(0xFF8D6E63);
                Path inner = new Path();
                inner.moveTo(gx + r * 0.15f, cy - r * 0.22f);
                inner.quadTo(gx + r * 0.45f, cy, gx + r * 0.15f, cy + r * 0.22f);
                inner.quadTo(gx + r * 0.28f, cy, gx + r * 0.15f, cy - r * 0.22f);
                inner.close();
                c.drawPath(inner, p);
                // grip wrap
                p.setColor(0xFFE53935);
                c.drawRect(gx + r * 0.14f, cy - r * 0.08f, gx + r * 0.18f, cy + r * 0.08f, p);
                break;
            }
            case T_MORTAR: {
                // mortar tube tilted up (drawn vertically; rotation will tilt)
                p.setColor(0xFF263238);
                Path t1 = new Path();
                t1.moveTo(gx - r * 0.1f, cy - r * 0.05f);
                t1.lineTo(gx + r * 0.05f, cy - r * 0.5f);
                t1.lineTo(gx + r * 0.25f, cy - r * 0.5f);
                t1.lineTo(gx + r * 0.15f, cy - r * 0.05f);
                t1.close();
                c.drawPath(t1, p);
                p.setColor(0xFF424242);
                c.drawCircle(gx + r * 0.15f, cy - r * 0.5f, r * 0.13f, p);
                // tripod legs
                p.setColor(0xFF263238);
                c.drawRect(gx - r * 0.2f, cy - r * 0.05f, gx + r * 0.35f, cy + r * 0.05f, p);
                break;
            }
        }
    }

    void tackGun(Canvas c, Paint p, float cx, float cy, float r) {
        p.setColor(0xFF424242);
        for (int i = 0; i < 10; i++) {
            c.save(); c.rotate(i * 36f, cx, cy);
            Path nail = new Path();
            nail.moveTo(cx + r * 0.25f, cy - r * 0.06f);
            nail.lineTo(cx + r * 1.05f, cy);
            nail.lineTo(cx + r * 0.25f, cy + r * 0.06f);
            nail.close();
            c.drawPath(nail, p);
            c.restore();
        }
        p.setColor(0xFFE0E0E0);
        c.drawCircle(cx, cy, r * 0.28f, p);
        p.setColor(0xFF424242);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(2, r * 0.04f));
        c.drawCircle(cx, cy, r * 0.28f, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFFFFFFF);
        c.drawCircle(cx - r * 0.07f, cy - r * 0.07f, r * 0.08f, p);
    }

    void iceGun(Canvas c, Paint p, float cx, float cy, float r) {
        // big snowflake
        p.setColor(0xFFB3E5FC);
        c.drawCircle(cx, cy, r * 0.6f, p);
        p.setColor(0xFFFFFFFF);
        c.drawCircle(cx, cy, r * 0.45f, p);
        p.setColor(0xFF0277BD);
        for (int i = 0; i < 6; i++) {
            c.save(); c.rotate(i * 60, cx, cy);
            c.drawRect(cx, cy - r * 0.05f, cx + r * 0.65f, cy + r * 0.05f, p);
            // small spikes
            c.drawRect(cx + r * 0.45f, cy - r * 0.15f, cx + r * 0.55f, cy + r * 0.15f, p);
            c.drawRect(cx + r * 0.25f, cy - r * 0.12f, cx + r * 0.32f, cy + r * 0.12f, p);
            c.restore();
        }
        p.setColor(0xFFFFFFFF);
        c.drawCircle(cx, cy, r * 0.15f, p);
        p.setColor(0xFFE1F5FE);
        c.drawCircle(cx - r * 0.05f, cy - r * 0.05f, r * 0.08f, p);
    }

    // ---------------- BG BAKING ----------------
    void bakeBackground() {
        if (screenW <= 0 || screenH <= 0) return;
        if (bgCache != null && (bgCache.getWidth() != screenW || bgCache.getHeight() != screenH)) {
            bgCache.recycle(); bgCache = null;
        }
        if (bgCache == null) {
            try {
                bgCache = Bitmap.createBitmap(screenW, screenH, Bitmap.Config.ARGB_8888);
            } catch (OutOfMemoryError e) { return; }
        }
        Canvas c = new Canvas(bgCache);
        c.drawColor(0xFF000000);

        int world = currentMap == null ? 0 : currentMap.world;
        drawBiomeBackground(c, world);
        drawPathPretty(c, world);
        drawDecorations(c, world);
    }

    void drawBiomeBackground(Canvas c, int world) {
        int top, bot;
        switch (world) {
            case MapDef.W_DESERT: top = 0xFFFFD180; bot = 0xFFE65100; break;
            case MapDef.W_SNOW:   top = 0xFFE3F2FD; bot = 0xFF4FC3F7; break;
            case MapDef.W_LAVA:   top = 0xFF3E2723; bot = 0xFF1A0E08; break;
            case MapDef.W_FOREST:
            default:              top = 0xFF7CB342; bot = 0xFF1B5E20; break;
        }
        Paint pp = new Paint(Paint.ANTI_ALIAS_FLAG);
        LinearGradient lg = new LinearGradient(0, hudH, 0, hudH + playH, top, bot, Shader.TileMode.CLAMP);
        pp.setShader(lg);
        c.drawRect(0, hudH, playW, hudH + playH, pp);
        pp.setShader(null);

        // grid hint
        pp.setColor(0x14000000);
        for (int gx = 1; gx < COLS; gx++) c.drawLine(gx * tile, hudH, gx * tile, hudH + playH, pp);
        for (int gy = 1; gy < ROWS; gy++) c.drawLine(0, hudH + gy * tile, playW, hudH + gy * tile, pp);

        // pattern
        java.util.Random rng = new java.util.Random(world * 7919L + currentMap.name.hashCode());
        for (int i = 0; i < 300; i++) {
            float xx = rng.nextFloat() * playW;
            float yy = hudH + rng.nextFloat() * playH;
            float rr = 3 + rng.nextFloat() * 7;
            int col;
            switch (world) {
                case MapDef.W_DESERT: col = (rng.nextBoolean()) ? 0x55FFE082 : 0x44BF360C; break;
                case MapDef.W_SNOW:   col = (rng.nextBoolean()) ? 0x77FFFFFF : 0x4481D4FA; break;
                case MapDef.W_LAVA:   col = (rng.nextBoolean()) ? 0x88FF6F00 : 0x66000000; break;
                default:              col = (rng.nextBoolean()) ? 0x552E7D32 : 0x6681C784; break;
            }
            pp.setColor(col);
            c.drawCircle(xx, yy, rr, pp);
        }

        // biome accents
        if (world == MapDef.W_DESERT) {
            pp.setColor(0x55BF360C);
            pp.setStyle(Paint.Style.STROKE);
            pp.setStrokeWidth(3);
            for (int i = 0; i < 8; i++) {
                Path pa = new Path();
                float y0 = hudH + (i + 1) * playH / 9f;
                pa.moveTo(0, y0);
                for (int k = 1; k <= 14; k++) {
                    float xx = k * playW / 14f;
                    pa.quadTo(xx - playW / 28f, y0 - 25 + rng.nextFloat() * 20, xx, y0);
                }
                c.drawPath(pa, pp);
            }
            pp.setStyle(Paint.Style.FILL);
        } else if (world == MapDef.W_LAVA) {
            pp.setColor(0xDDFF6F00);
            pp.setStyle(Paint.Style.STROKE);
            pp.setStrokeWidth(4);
            for (int i = 0; i < 6; i++) {
                Path pa = new Path();
                float x0 = rng.nextFloat() * playW;
                float y0 = hudH + rng.nextFloat() * playH;
                pa.moveTo(x0, y0);
                for (int k = 0; k < 6; k++) {
                    x0 += (rng.nextFloat() - 0.5f) * 200;
                    y0 += (rng.nextFloat() - 0.5f) * 200;
                    pa.lineTo(x0, y0);
                }
                c.drawPath(pa, pp);
            }
            pp.setStyle(Paint.Style.FILL);
        } else if (world == MapDef.W_SNOW) {
            // soft snow patches
            pp.setColor(0x55FFFFFF);
            for (int i = 0; i < 20; i++) {
                float xx = rng.nextFloat() * playW;
                float yy = hudH + rng.nextFloat() * playH;
                c.drawCircle(xx, yy, 30 + rng.nextFloat() * 50, pp);
            }
        }
    }

    void drawPathPretty(Canvas c, int world) {
        if (path.size() < 2) return;
        Paint pp = new Paint(Paint.ANTI_ALIAS_FLAG);
        Path p = new Path();
        p.moveTo(path.get(0).x, path.get(0).y);
        for (int i = 1; i < path.size(); i++) p.lineTo(path.get(i).x, path.get(i).y);

        int outerCol, innerCol, edgeCol;
        switch (world) {
            case MapDef.W_DESERT: outerCol = 0xFF6D4C41; innerCol = 0xFFA1887F; edgeCol = 0xFF3E2723; break;
            case MapDef.W_SNOW:   outerCol = 0xFF607D8B; innerCol = 0xFFB0BEC5; edgeCol = 0xFF263238; break;
            case MapDef.W_LAVA:   outerCol = 0xFF212121; innerCol = 0xFF424242; edgeCol = 0xFF000000; break;
            default:              outerCol = 0xFF5D4037; innerCol = 0xFF8D6E63; edgeCol = 0xFF3E2723; break;
        }
        pp.setStyle(Paint.Style.STROKE);
        pp.setStrokeCap(Paint.Cap.ROUND);
        pp.setStrokeJoin(Paint.Join.ROUND);

        // thick shadow
        pp.setColor(0x66000000);
        pp.setStrokeWidth(tile * 1.05f);
        c.save();
        c.translate(4, 6);
        c.drawPath(p, pp);
        c.restore();

        pp.setColor(edgeCol);
        pp.setStrokeWidth(tile * 0.98f);
        c.drawPath(p, pp);
        pp.setColor(outerCol);
        pp.setStrokeWidth(tile * 0.88f);
        c.drawPath(p, pp);
        pp.setColor(innerCol);
        pp.setStrokeWidth(tile * 0.66f);
        c.drawPath(p, pp);
        // top highlight stripe
        pp.setColor(0x44FFFFFF);
        pp.setStrokeWidth(tile * 0.18f);
        pp.setStyle(Paint.Style.STROKE);
        c.save();
        c.translate(0, -tile * 0.16f);
        c.drawPath(p, pp);
        c.restore();
        // dashed center
        Paint dashP = new Paint(Paint.ANTI_ALIAS_FLAG);
        dashP.setStyle(Paint.Style.STROKE);
        dashP.setColor(0x99FFFFFF);
        dashP.setStrokeWidth(tile * 0.05f);
        dashP.setPathEffect(new android.graphics.DashPathEffect(new float[]{tile * 0.3f, tile * 0.3f}, 0));
        c.drawPath(p, dashP);

        // entry/exit markers (arrow & flag)
        if (path.size() >= 2) {
            PointF p0 = path.get(0), p1 = path.get(1);
            float ax = p0.x, ay = p0.y;
            float dx = p1.x - p0.x, dy = p1.y - p0.y;
            float l = (float) Math.sqrt(dx * dx + dy * dy);
            if (l > 0.001f) {
                dx /= l; dy /= l;
                pp.setStyle(Paint.Style.FILL);
                pp.setColor(0xFFFFEB3B);
                Path tri = new Path();
                tri.moveTo(ax + dx * tile * 0.4f, ay + dy * tile * 0.4f);
                tri.lineTo(ax + dx * tile * 0.05f - dy * tile * 0.25f, ay + dy * tile * 0.05f + dx * tile * 0.25f);
                tri.lineTo(ax + dx * tile * 0.05f + dy * tile * 0.25f, ay + dy * tile * 0.05f - dx * tile * 0.25f);
                tri.close();
                c.drawPath(tri, pp);
            }
            // exit flag
            PointF pn = path.get(path.size() - 1);
            pp.setColor(0xFFE53935);
            pp.setStyle(Paint.Style.FILL);
            c.drawRect(pn.x - tile * 0.25f, pn.y - tile * 0.4f, pn.x + tile * 0.25f, pn.y - tile * 0.05f, pp);
            pp.setColor(0xFFFFFFFF);
            c.drawRect(pn.x - tile * 0.2f, pn.y - tile * 0.35f, pn.x - tile * 0.05f, pn.y - tile * 0.1f, pp);
        }

        // stones along the path edges
        java.util.Random rng = new java.util.Random(world * 13 + 11);
        pp.setStyle(Paint.Style.FILL);
        pp.setColor(world == MapDef.W_SNOW ? 0xFFFFFFFF : (world == MapDef.W_LAVA ? 0xFF424242 : 0xFF6D4C41));
        for (int i = 0; i < path.size() - 1; i++) {
            PointF a = path.get(i), b = path.get(i + 1);
            float d = segLen[i];
            int n = (int) (d / (tile * 0.45f));
            for (int k = 0; k <= n; k++) {
                float t = (float) k / Math.max(1, n);
                float xx = a.x + (b.x - a.x) * t;
                float yy = a.y + (b.y - a.y) * t;
                float dxx = b.x - a.x, dyy = b.y - a.y;
                float ll = (float) Math.sqrt(dxx * dxx + dyy * dyy);
                if (ll < 0.01) continue;
                float nx = -dyy / ll, ny = dxx / ll;
                float side = (k % 2 == 0) ? 1f : -1f;
                float ox = xx + nx * tile * 0.45f * side;
                float oy = yy + ny * tile * 0.45f * side;
                c.drawCircle(ox, oy, tile * (0.05f + rng.nextFloat() * 0.04f), pp);
            }
        }
    }

    void drawDecorations(Canvas c, int world) {
        Paint pp = new Paint(Paint.ANTI_ALIAS_FLAG);
        java.util.Random rng = new java.util.Random(currentMap == null ? 0 : currentMap.name.hashCode() + 7);
        for (int gx = 0; gx < COLS; gx++) {
            for (int gy = 0; gy < ROWS; gy++) {
                if (blocked[gx][gy]) continue;
                if (rng.nextFloat() > 0.55f) continue;
                float cx = gridCenterX(gx) + (rng.nextFloat() - 0.5f) * tile * 0.4f;
                float cy = gridCenterY(gy) + (rng.nextFloat() - 0.5f) * tile * 0.4f;
                drawDecor(c, pp, cx, cy, world, rng);
            }
        }
    }

    void drawDecor(Canvas c, Paint pp, float cx, float cy, int world, java.util.Random rng) {
        switch (world) {
            case MapDef.W_FOREST: {
                if (rng.nextFloat() < 0.5f) {
                    // tree
                    pp.setColor(0x44000000);
                    c.drawCircle(cx + 4, cy + 6, tile * 0.32f, pp);
                    pp.setColor(0xFF1B5E20);
                    c.drawCircle(cx, cy, tile * 0.32f, pp);
                    pp.setColor(0xFF2E7D32);
                    c.drawCircle(cx - tile * 0.07f, cy - tile * 0.09f, tile * 0.24f, pp);
                    pp.setColor(0xFF66BB6A);
                    c.drawCircle(cx - tile * 0.15f, cy - tile * 0.18f, tile * 0.14f, pp);
                    pp.setColor(0xFF5D4037);
                    c.drawRect(cx - tile * 0.05f, cy + tile * 0.2f, cx + tile * 0.05f, cy + tile * 0.34f, pp);
                } else if (rng.nextFloat() < 0.6f) {
                    pp.setColor(0xFF2E7D32);
                    c.drawCircle(cx, cy, tile * 0.18f, pp);
                    pp.setColor(0xFF66BB6A);
                    c.drawCircle(cx - tile * 0.05f, cy - tile * 0.06f, tile * 0.1f, pp);
                } else {
                    int[] cols = {0xFFFFEB3B, 0xFFE53935, 0xFFFFFFFF, 0xFFE91E63, 0xFFAB47BC};
                    pp.setColor(cols[rng.nextInt(cols.length)]);
                    c.drawCircle(cx, cy, tile * 0.07f, pp);
                    pp.setColor(0xFFFFEB3B);
                    c.drawCircle(cx, cy, tile * 0.035f, pp);
                }
                break;
            }
            case MapDef.W_DESERT: {
                if (rng.nextFloat() < 0.4f) {
                    pp.setColor(0x55000000);
                    c.drawRoundRect(cx - tile * 0.08f, cy + tile * 0.2f, cx + tile * 0.08f, cy + tile * 0.32f, 4, 4, pp);
                    pp.setColor(0xFF2E7D32);
                    c.drawRoundRect(cx - tile * 0.09f, cy - tile * 0.3f, cx + tile * 0.09f, cy + tile * 0.27f, 12, 12, pp);
                    if (rng.nextBoolean()) {
                        c.drawRoundRect(cx + tile * 0.08f, cy - tile * 0.12f, cx + tile * 0.24f, cy + tile * 0.02f, 8, 8, pp);
                        c.drawRoundRect(cx + tile * 0.17f, cy - tile * 0.22f, cx + tile * 0.24f, cy + tile * 0.02f, 8, 8, pp);
                    }
                    pp.setColor(0xFF1B5E20);
                    for (int i = 0; i < 4; i++)
                        c.drawCircle(cx, cy - tile * 0.22f + i * tile * 0.13f, 3, pp);
                    // flower
                    if (rng.nextFloat() < 0.4f) {
                        pp.setColor(0xFFE91E63);
                        c.drawCircle(cx, cy - tile * 0.35f, tile * 0.04f, pp);
                    }
                } else {
                    pp.setColor(0xFFBCAAA4);
                    c.drawCircle(cx, cy, tile * 0.1f, pp);
                    pp.setColor(0xFF6D4C41);
                    c.drawCircle(cx - tile * 0.03f, cy - tile * 0.03f, tile * 0.07f, pp);
                    pp.setColor(0xFF8D6E63);
                    c.drawCircle(cx + tile * 0.04f, cy + tile * 0.02f, tile * 0.04f, pp);
                }
                break;
            }
            case MapDef.W_SNOW: {
                if (rng.nextFloat() < 0.5f) {
                    pp.setColor(0x44000000);
                    c.drawCircle(cx + 3, cy + 5, tile * 0.25f, pp);
                    pp.setColor(0xFF1B5E20);
                    Path tri = new Path();
                    for (int k = 0; k < 3; k++) {
                        tri.reset();
                        float w = tile * (0.26f - k * 0.05f);
                        float yTop = cy - tile * 0.35f + k * tile * 0.14f;
                        float yBot = yTop + tile * 0.22f;
                        tri.moveTo(cx - w, yBot);
                        tri.lineTo(cx + w, yBot);
                        tri.lineTo(cx, yTop);
                        tri.close();
                        c.drawPath(tri, pp);
                    }
                    pp.setColor(0xFF5D4037);
                    c.drawRect(cx - tile * 0.04f, cy + tile * 0.12f, cx + tile * 0.04f, cy + tile * 0.24f, pp);
                    pp.setColor(0xFFFFFFFF);
                    c.drawCircle(cx, cy - tile * 0.33f, tile * 0.06f, pp);
                    // snow tufts on branches
                    for (int k = 0; k < 3; k++) {
                        float w = tile * (0.26f - k * 0.05f);
                        float yBot = cy - tile * 0.13f + k * tile * 0.14f;
                        c.drawCircle(cx - w + 3, yBot, tile * 0.045f, pp);
                        c.drawCircle(cx + w - 3, yBot, tile * 0.045f, pp);
                    }
                } else {
                    pp.setColor(0xFFFFFFFF);
                    c.drawCircle(cx, cy, tile * 0.12f, pp);
                    pp.setColor(0xFFE0F2F1);
                    c.drawCircle(cx + tile * 0.06f, cy + tile * 0.06f, tile * 0.06f, pp);
                    if (rng.nextFloat() < 0.3f) {
                        pp.setColor(0xFF263238);
                        c.drawCircle(cx + tile * 0.06f, cy - tile * 0.04f, tile * 0.02f, pp);
                        c.drawCircle(cx - tile * 0.04f, cy - tile * 0.04f, tile * 0.02f, pp);
                    }
                }
                break;
            }
            case MapDef.W_LAVA: {
                if (rng.nextFloat() < 0.45f) {
                    pp.setColor(0xFF424242);
                    Path pa = new Path();
                    pa.moveTo(cx - tile * 0.22f, cy + tile * 0.12f);
                    pa.lineTo(cx - tile * 0.05f, cy - tile * 0.18f);
                    pa.lineTo(cx + tile * 0.1f, cy - tile * 0.05f);
                    pa.lineTo(cx + tile * 0.22f, cy + tile * 0.14f);
                    pa.close();
                    c.drawPath(pa, pp);
                    pp.setColor(0xFFFF6F00);
                    c.drawCircle(cx, cy + tile * 0.08f, tile * 0.03f, pp);
                } else {
                    pp.setColor(0xCCFF6F00);
                    c.drawCircle(cx, cy, tile * 0.1f, pp);
                    pp.setColor(0xFFFFEB3B);
                    c.drawCircle(cx, cy, tile * 0.05f, pp);
                    pp.setColor(0x88FFFFFF);
                    c.drawCircle(cx - tile * 0.03f, cy - tile * 0.03f, tile * 0.02f, pp);
                }
                break;
            }
        }
    }

    // ---------------- WAVES ----------------
    int[][] waveDef(int w) {
        switch (w) {
            case 1:  return new int[][]{{B_RED, 16}};
            case 2:  return new int[][]{{B_RED, 24}};
            case 3:  return new int[][]{{B_RED, 14}, {B_BLUE, 8}};
            case 4:  return new int[][]{{B_BLUE, 18}};
            case 5:  return new int[][]{{B_RED, 32}};
            case 6:  return new int[][]{{B_BLUE, 16}, {B_GREEN, 8}};
            case 7:  return new int[][]{{B_GREEN, 18}};
            case 8:  return new int[][]{{B_BLUE, 22}, {B_GREEN, 12}};
            case 9:  return new int[][]{{B_GREEN, 24}, {B_YELLOW, 6}};
            case 10: return new int[][]{{B_YELLOW, 20}};
            case 11: return new int[][]{{B_GREEN, 24}, {B_YELLOW, 12}};
            case 12: return new int[][]{{B_YELLOW, 28}, {B_PINK, 8}};
            case 13: return new int[][]{{B_PINK, 22}};
            case 14: return new int[][]{{B_BLACK, 12}, {B_YELLOW, 16}};
            case 15: return new int[][]{{B_PINK, 28}, {B_BLACK, 10}};
            case 16: return new int[][]{{B_LEAD, 8}, {B_PINK, 22}};
            case 17: return new int[][]{{B_BLACK, 18}, {B_PINK, 22}};
            case 18: return new int[][]{{B_LEAD, 14}, {B_BLACK, 14}};
            case 19: return new int[][]{{B_PINK, 50}, {B_BLACK, 24}};
            case 20: return new int[][]{{B_MOAB, 1}, {B_PINK, 22}};
            default: return new int[][]{{B_MOAB, 1 + (w - 20) / 2}, {B_PINK, 30}};
        }
    }

    void startWave() {
        if (waveActive || gameOver) return;
        if (path.size() < 2) return;
        wave++;
        if (currentMap != null) {
            Integer best = bestWave.get(currentMap.name);
            if (best == null || wave > best) bestWave.put(currentMap.name, wave);
        }
        waveQueue.clear();
        int[][] def = waveDef(wave);
        if (def == null || def.length == 0) return;
        for (int[] r : def) waveQueue.add(new int[]{r[0], r[1]});
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
        try {
            doUpdate(dt);
        } catch (Throwable t) {
            popupMessage = "ERR: " + t.getClass().getSimpleName() + " " + t.getMessage();
            popupTimer = 4f;
        }
    }
    void doUpdate(float dt) {
        totalTime += dt;
        menuAnim += dt;
        if (state != S_PLAYING) return;
        if (paused || gameOver) return;
        if (tile <= 0) return;
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
                totalCoins += 20 + wave * 4;
                flash("Wave +$" + bonus);
            }
        }
        // eco tick: every 1s, +ecoRate cash
        ecoTimer += dt;
        if (ecoTimer >= 1.0f) {
            ecoTimer -= 1.0f;
            if (ecoRate > 0) {
                cash += ecoRate;
                addFloater("+" + ecoRate + " eco", panelX + 20, hudH + 40, 0xFF4CAF50);
            }
        }

        for (int i = 0; i < bloons.size(); i++) bloons.get(i).update(dt, this);
        for (int i = 0; i < towers.size(); i++) towers.get(i).update(dt, this);
        for (int i = 0; i < projectiles.size(); i++) projectiles.get(i).update(dt, this);

        if (!pendingBloons.isEmpty()) {
            for (int i = 0; i < pendingBloons.size(); i++) {
                Bloon nb = pendingBloons.get(i);
                if (nb.dAlong < 0) nb.dAlong = 0;
                posAlongPath(nb.dAlong, nb.pos);
            }
            bloons.addAll(pendingBloons);
            pendingBloons.clear();
        }
        if (!pendingProjectiles.isEmpty()) {
            projectiles.addAll(pendingProjectiles);
            pendingProjectiles.clear();
        }

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
        try {
            doDraw(c);
        } catch (Throwable t) {
            c.drawColor(0xFF000000);
            textP.setColor(0xFFFF5252);
            textP.setTextSize(28);
            c.drawText("Render error: " + t.getClass().getSimpleName(), 20, 60, textP);
            c.drawText(String.valueOf(t.getMessage()), 20, 100, textP);
        }
    }
    void doDraw(Canvas c) {
        if (tile <= 0 || screenW <= 0) { c.drawColor(0xFF1A237E); return; }
        switch (state) {
            case S_MAIN:    drawMainMenu(c);  break;
            case S_MAPS:    drawMapsMenu(c);  break;
            case S_TOWERS:  drawTowersMenu(c); break;
            case S_HOWTO:   drawHowToMenu(c); break;
            case S_SETTINGS:drawSettingsMenu(c); break;
            case S_RECORDS: drawRecordsMenu(c); break;
            case S_PLAYING: drawGame(c);      break;
        }
    }

    void drawGame(Canvas c) {
        if (bgCache != null) c.drawBitmap(bgCache, 0, 0, null);
        else c.drawColor(0xFF2E7D32);

        // placement preview
        if (placingTowerType >= 0 && lastTouchX < playW && lastTouchY > hudH && lastTouchY < hudH + playH) {
            int cx = (int) (lastTouchX / tile);
            int cy = (int) ((lastTouchY - hudH) / tile);
            if (inGrid(cx, cy)) {
                boolean ok = canPlace(cx, cy);
                paint.setShader(null);
                paint.setColor(ok ? 0x6644FF44 : 0x66FF4444);
                c.drawRect(cx * tile, hudH + cy * tile, (cx + 1) * tile, hudH + (cy + 1) * tile, paint);
                paint.setColor(0x44FFFFFF);
                float pr = Math.min(playW, new Tower(placingTowerType, 0, 0, 0, 0).baseRange());
                c.drawCircle(gridCenterX(cx), gridCenterY(cy), pr, paint);
            }
        }

        for (int i = 0; i < towers.size(); i++) towers.get(i).draw(c, paint, this);
        for (int i = 0; i < bloons.size(); i++) bloons.get(i).draw(c, paint, this);
        for (int i = 0; i < projectiles.size(); i++) projectiles.get(i).draw(c, paint);

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

        for (int i = 0; i < floaters.size(); i++) {
            Floater f = floaters.get(i);
            int alpha = (int) (255 * (1 - f.t));
            if (alpha < 0) alpha = 0;
            textP.setColor((alpha << 24) | (f.color & 0xFFFFFF));
            textP.setTextSize(tile * 0.5f);
            c.drawText(f.text, f.x, f.y, textP);
        }
        textP.setColor(Color.WHITE);
        textP.setTextSize(tile * 0.38f);

        drawHUD(c);

        drawTowerBar(c); // right panel always visible
        if (selectedTower != null) drawUpgradePanel(c); // bottom overlay

        if (popupTimer > 0 && popupMessage != null) {
            textP.setTextSize(tile * 0.7f);
            float w = textP.measureText(popupMessage);
            paint.setShader(null);
            paint.setColor(0xCC000000);
            c.drawRoundRect(new RectF(playW / 2f - w / 2 - 30, hudH + playH / 2 - tile, playW / 2f + w / 2 + 30, hudH + playH / 2 + tile * 0.3f), 20, 20, paint);
            textP.setColor(0xFFFFEB3B);
            c.drawText(popupMessage, playW / 2f - w / 2, hudH + playH / 2, textP);
            textP.setColor(Color.WHITE);
            textP.setTextSize(tile * 0.38f);
        }

        if (gameOver) {
            paint.setColor(0xCC000000);
            c.drawRect(0, 0, screenW, screenH, paint);
            textP.setTextSize(tile * 1.2f);
            String msg = victory ? "VICTORY!" : "DEFEAT";
            float w = textP.measureText(msg);
            textP.setColor(victory ? 0xFF4CAF50 : 0xFFE53935);
            c.drawText(msg, screenW / 2f - w / 2, screenH / 2f - tile, textP);
            textP.setTextSize(tile * 0.5f);
            textP.setColor(Color.WHITE);
            String s2 = "Score " + score + " · Wave " + wave;
            float w2 = textP.measureText(s2);
            c.drawText(s2, screenW / 2f - w2 / 2, screenH / 2f, textP);
            float by = screenH / 2f + tile * 0.8f;
            drawPillButton(c, screenW / 2f - tile * 2.7f, by, tile * 2.4f, tile * 0.9f, "RESTART", 0xFF388E3C);
            drawPillButton(c, screenW / 2f + tile * 0.3f, by, tile * 2.4f, tile * 0.9f, "MENU", 0xFF1976D2);
            textP.setTextSize(tile * 0.38f);
        }
    }

    void drawHUD(Canvas c) {
        paint.setShader(null);
        LinearGradient lg = new LinearGradient(0, 0, 0, hudH, 0xFF0D47A1, 0xFF1565C0, Shader.TileMode.CLAMP);
        paint.setShader(lg);
        c.drawRect(0, 0, screenW, hudH, paint);
        paint.setShader(null);
        paint.setColor(0xFF0D47A1);
        c.drawRect(0, hudH - 4, screenW, hudH, paint);

        // currency bubbles
        drawCoinBubble(c, 20, hudH * 0.5f, "$" + cash, 0xFFFFEB3B);
        drawHeartBubble(c, 20 + tile * 2.6f, hudH * 0.5f, lives);
        textP.setColor(Color.WHITE);
        textP.setTextSize(hudH * 0.42f);
        textP.setFakeBoldText(true);
        c.drawText("Wave " + Math.max(1, wave), 20 + tile * 5.0f, hudH * 0.62f, textP);
        textP.setFakeBoldText(false);
        textP.setTextSize(hudH * 0.3f);
        textP.setColor(0xFFB3E5FC);
        c.drawText("Score " + score + "  ·  " + (currentMap != null ? currentMap.name : ""),
                20 + tile * 5.0f, hudH * 0.92f, textP);
        // Eco display (between wave info and buttons)
        if (ecoRate > 0) {
            float ecoX = panelX - tile * 1.8f;
            paint.setShader(null);
            paint.setColor(0xFF1B5E20);
            c.drawRoundRect(new RectF(ecoX, hudH * 0.2f, ecoX + tile * 1.6f, hudH * 0.8f), 14, 14, paint);
            paint.setColor(0xFF388E3C);
            c.drawRoundRect(new RectF(ecoX + 3, hudH * 0.2f + 3, ecoX + tile * 1.6f - 3, hudH * 0.8f - 3), 12, 12, paint);
            textP.setColor(0xFFFFFFFF);
            textP.setFakeBoldText(true);
            textP.setTextSize(hudH * 0.32f);
            c.drawText("+$" + ecoRate + "/s", ecoX + 14, hudH * 0.62f, textP);
            textP.setFakeBoldText(false);
        }

        float bw = tile * 1.15f;
        float bh = hudH * 0.6f;
        float by = hudH * 0.2f;
        float bx = screenW - bw - 10;
        drawPillButton(c, bx, by, bw, bh,
                speedMult == 1f ? ">" : (speedMult == 2f ? ">>" : ">>>"), 0xFF1976D2);
        bx -= bw + 10;
        drawPillButton(c, bx, by, bw, bh, paused ? "▶" : "❚❚", 0xFF455A64);
        bx -= bw + 10;
        drawPillButton(c, bx, by, bw, bh, waveActive ? "..." : "GO!", waveActive ? 0xFF616161 : 0xFFE53935);
        bx -= bw + 10;
        drawPillButton(c, bx, by, bw, bh, "MENU", 0xFF607D8B);
        textP.setTextSize(tile * 0.38f);
    }

    void drawCoinBubble(Canvas c, float x, float y, String label, int color) {
        Paint p = paint;
        p.setShader(null);
        p.setColor(0x55000000);
        c.drawRoundRect(new RectF(x + 3, y - hudH * 0.32f + 4, x + tile * 2.3f + 3, y + hudH * 0.32f + 4), 18, 18, p);
        p.setColor(0xFF263238);
        c.drawRoundRect(new RectF(x, y - hudH * 0.32f, x + tile * 2.3f, y + hudH * 0.32f), 18, 18, p);
        p.setColor(0xFF37474F);
        c.drawRoundRect(new RectF(x + 3, y - hudH * 0.32f + 3, x + tile * 2.3f - 3, y + hudH * 0.32f - 3), 16, 16, p);
        // coin
        p.setColor(color);
        c.drawCircle(x + hudH * 0.32f, y, hudH * 0.28f, p);
        p.setColor(Tower.darken(color, 0.7f));
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(3);
        c.drawCircle(x + hudH * 0.32f, y, hudH * 0.28f, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xCCFFFFFF);
        c.drawCircle(x + hudH * 0.32f - hudH * 0.1f, y - hudH * 0.12f, hudH * 0.08f, p);
        textP.setColor(Color.WHITE);
        textP.setTextSize(hudH * 0.4f);
        textP.setFakeBoldText(true);
        c.drawText(label, x + hudH * 0.7f, y + hudH * 0.15f, textP);
        textP.setFakeBoldText(false);
    }

    void drawHeartBubble(Canvas c, float x, float y, int hp) {
        Paint p = paint;
        p.setShader(null);
        p.setColor(0xFF263238);
        c.drawRoundRect(new RectF(x, y - hudH * 0.32f, x + tile * 2.3f, y + hudH * 0.32f), 18, 18, p);
        p.setColor(0xFF37474F);
        c.drawRoundRect(new RectF(x + 3, y - hudH * 0.32f + 3, x + tile * 2.3f - 3, y + hudH * 0.32f - 3), 16, 16, p);
        // heart
        p.setColor(0xFFE53935);
        float hx = x + hudH * 0.32f, hy = y;
        float hr = hudH * 0.2f;
        c.drawCircle(hx - hr * 0.5f, hy - hr * 0.3f, hr * 0.55f, p);
        c.drawCircle(hx + hr * 0.5f, hy - hr * 0.3f, hr * 0.55f, p);
        Path heart = new Path();
        heart.moveTo(hx - hr, hy - hr * 0.1f);
        heart.lineTo(hx + hr, hy - hr * 0.1f);
        heart.lineTo(hx, hy + hr * 0.9f);
        heart.close();
        c.drawPath(heart, p);
        p.setColor(0xCCFFFFFF);
        c.drawCircle(hx - hr * 0.5f, hy - hr * 0.4f, hr * 0.2f, p);
        textP.setColor(Color.WHITE);
        textP.setTextSize(hudH * 0.4f);
        textP.setFakeBoldText(true);
        c.drawText(String.valueOf(hp), x + hudH * 0.7f, y + hudH * 0.15f, textP);
        textP.setFakeBoldText(false);
    }

    void drawPillButton(Canvas c, float x, float y, float w, float h, String label, int color) {
        paint.setShader(null);
        paint.setColor(0x66000000);
        c.drawRoundRect(new RectF(x + 3, y + 5, x + w + 3, y + h + 5), h * 0.5f, h * 0.5f, paint);
        paint.setColor(Tower.darken(color, 0.65f));
        c.drawRoundRect(new RectF(x, y, x + w, y + h), h * 0.5f, h * 0.5f, paint);
        paint.setColor(color);
        c.drawRoundRect(new RectF(x + 3, y + 3, x + w - 3, y + h - 3), h * 0.5f, h * 0.5f, paint);
        paint.setColor(0x66FFFFFF);
        c.drawRoundRect(new RectF(x + 6, y + 6, x + w - 6, y + h * 0.4f), h * 0.5f, h * 0.5f, paint);
        textP.setColor(Color.WHITE);
        textP.setTextSize(h * 0.5f);
        textP.setFakeBoldText(true);
        float tw = textP.measureText(label);
        c.drawText(label, x + w / 2 - tw / 2, y + h * 0.68f, textP);
        textP.setFakeBoldText(false);
        textP.setTextSize(tile * 0.38f);
    }

    void drawTowerBar(Canvas c) {
        // panel
        paint.setShader(null);
        LinearGradient lg = new LinearGradient(panelX, 0, screenW, 0, 0xFF37474F, 0xFF263238, Shader.TileMode.CLAMP);
        paint.setShader(lg);
        c.drawRect(panelX, hudH, screenW, screenH, paint);
        paint.setShader(null);
        paint.setColor(0xFF1A237E);
        c.drawRect(panelX, hudH, panelX + 4, screenH, paint);

        // Tabs at top: TOWERS / SEND
        float tabH = hudH * 0.6f;
        float tabY = hudH + 8;
        float tabW = (panelW - 24) / 2f;
        drawTab(c, panelX + 8, tabY, tabW, tabH, "TOWERS", rightTab == 0, 0xFFFFEB3B);
        drawTab(c, panelX + 16 + tabW, tabY, tabW, tabH, "SEND", rightTab == 1, 0xFF81C784);

        if (rightTab == 0) drawTowersTab(c, tabY + tabH + 8);
        else drawSendTab(c, tabY + tabH + 8);
        textP.setTextSize(tile * 0.38f);
    }

    void drawTab(Canvas c, float x, float y, float w, float h, String label, boolean active, int accent) {
        paint.setShader(null);
        paint.setColor(0x66000000);
        c.drawRoundRect(new RectF(x + 2, y + 4, x + w + 2, y + h + 4), 14, 14, paint);
        paint.setColor(active ? accent : Tower.darken(accent, 0.4f));
        c.drawRoundRect(new RectF(x, y, x + w, y + h), 14, 14, paint);
        if (active) {
            paint.setColor(0x66FFFFFF);
            c.drawRoundRect(new RectF(x + 4, y + 4, x + w - 4, y + h * 0.4f), 12, 12, paint);
        }
        textP.setColor(active ? 0xFF1A237E : 0xFFCFD8DC);
        textP.setTextSize(h * 0.45f);
        textP.setFakeBoldText(true);
        float tw = textP.measureText(label);
        c.drawText(label, x + w / 2 - tw / 2, y + h * 0.65f, textP);
        textP.setFakeBoldText(false);
    }

    void drawTowersTab(Canvas c, float startY) {
        float pad = 8;
        int cols = 2;
        float cw = (panelW - pad * 3) / cols;
        float availH = screenH - startY - 10;
        int rows = 6; // 12 towers / 2 cols
        float ch = (availH - pad * (rows + 1)) / rows;
        for (int rr = 0; rr < rows; rr++) {
            for (int cc = 0; cc < cols; cc++) {
                int type = rr * cols + cc;
                if (type >= Tower.TYPE_COUNT) continue;
                float x = panelX + pad + cc * (cw + pad);
                float y = startY + pad + rr * (ch + pad);
                drawTowerCard(c, x, y, cw, ch, type);
            }
        }
    }

    public static final int[] SEND_BLOON = {B_RED, B_BLUE, B_GREEN, B_YELLOW, B_PINK};
    public static final int[] SEND_COST = {25, 60, 130, 260, 500};
    public static final int[] SEND_ECO = {2, 4, 8, 16, 30};
    public static final int[] SEND_COUNT = {8, 6, 5, 4, 3};

    void drawSendTab(Canvas c, float startY) {
        textP.setColor(0xFFE0F2F1);
        textP.setTextSize(panelW * 0.045f);
        c.drawText("Spend cash to send bloons.", panelX + 12, startY + 18, textP);
        c.drawText("Each pack boosts your ECO income.", panelX + 12, startY + 38, textP);
        textP.setColor(0xFF81C784);
        textP.setTextSize(panelW * 0.07f);
        textP.setFakeBoldText(true);
        c.drawText("ECO: +$" + ecoRate + "/s", panelX + 12, startY + 80, textP);
        textP.setFakeBoldText(false);

        float yy = startY + 100;
        float pad = 8;
        float availH = screenH - yy - 12;
        int rows = SEND_BLOON.length;
        float rh = (availH - pad * (rows + 1)) / rows;
        for (int i = 0; i < rows; i++) {
            float y = yy + pad + i * (rh + pad);
            drawSendCard(c, panelX + pad, y, panelW - pad * 2, rh, i);
        }
    }

    void drawSendCard(Canvas c, float x, float y, float w, float h, int idx) {
        int bType = SEND_BLOON[idx];
        int cost = SEND_COST[idx];
        int eco = SEND_ECO[idx];
        int count = SEND_COUNT[idx];
        boolean afford = cash >= cost;
        paint.setShader(null);
        paint.setColor(0x66000000);
        c.drawRoundRect(new RectF(x + 3, y + 4, x + w + 3, y + h + 4), 14, 14, paint);
        paint.setColor(afford ? Tower.darken(BLOON_COLOR[bType], 0.65f) : 0xFF263238);
        c.drawRoundRect(new RectF(x, y, x + w, y + h), 14, 14, paint);
        paint.setColor(afford ? BLOON_COLOR[bType] : 0xFF455A64);
        c.drawRoundRect(new RectF(x + 3, y + 3, x + w - 3, y + h - 3), 12, 12, paint);
        paint.setColor(0x44FFFFFF);
        c.drawRoundRect(new RectF(x + 6, y + 4, x + w - 6, y + h * 0.45f), 10, 10, paint);
        // bloon icon left
        float iconSize = h * 0.7f;
        if (bloonBmp[bType] != null) {
            RectF dst = new RectF(x + 8, y + (h - iconSize) / 2, x + 8 + iconSize, y + (h + iconSize) / 2 + iconSize * 0.1f);
            c.drawBitmap(bloonBmp[bType], null, dst, paint);
        }
        // count "x N"
        textP.setColor(Color.WHITE);
        textP.setTextSize(h * 0.32f);
        textP.setFakeBoldText(true);
        c.drawText("x" + count, x + 12 + iconSize + 4, y + h * 0.5f, textP);
        textP.setTextSize(h * 0.2f);
        textP.setFakeBoldText(false);
        textP.setColor(0xFFFFEB3B);
        c.drawText("$" + cost, x + 12 + iconSize + 4, y + h * 0.78f, textP);
        textP.setColor(0xFF80CBC4);
        c.drawText("+$" + eco + " eco", x + 12 + iconSize + 60, y + h * 0.78f, textP);
        // send badge right
        float bw = h * 1.2f;
        float bh = h * 0.6f;
        drawPillButton(c, x + w - bw - 8, y + (h - bh) / 2, bw, bh,
                afford ? "SEND" : "✕", afford ? 0xFF388E3C : 0xFF616161);
        textP.setColor(Color.WHITE);
        textP.setTextSize(tile * 0.38f);
    }

    void sendBloons(int idx) {
        if (idx < 0 || idx >= SEND_BLOON.length) return;
        if (cash < SEND_COST[idx]) { flash("Need $" + SEND_COST[idx]); return; }
        if (path.size() < 2) return;
        cash -= SEND_COST[idx];
        ecoRate += SEND_ECO[idx];
        for (int i = 0; i < SEND_COUNT[idx]; i++) {
            Bloon b = new Bloon(SEND_BLOON[idx]);
            b.dAlong = 0;
            b.spawnDelay = i * 0.25f;
            pendingBloons.add(b);
        }
        flash("+$" + SEND_ECO[idx] + " eco");
    }

    void drawTowerCard(Canvas c, float x, float y, float w, float h, int type) {
        boolean sel = placingTowerType == type;
        boolean affordable = cash >= Tower.BASE_COST[type];
        paint.setShader(null);
        paint.setColor(0x55000000);
        c.drawRoundRect(new RectF(x + 3, y + 4, x + w + 3, y + h + 4), 16, 16, paint);
        int bgCol = sel ? 0xFF1976D2 : (affordable ? 0xFF455A64 : 0xFF2C3940);
        paint.setColor(Tower.darken(bgCol, 0.7f));
        c.drawRoundRect(new RectF(x, y, x + w, y + h), 16, 16, paint);
        paint.setColor(bgCol);
        c.drawRoundRect(new RectF(x + 3, y + 3, x + w - 3, y + h - 3), 14, 14, paint);
        paint.setColor(0x33FFFFFF);
        c.drawRoundRect(new RectF(x + 6, y + 6, x + w - 6, y + h * 0.45f), 12, 12, paint);

        // tower icon (use pre-rendered bitmap centered)
        Bitmap bmp = towerBmp[type];
        if (bmp != null) {
            float iconSize = Math.min(w * 0.55f, h * 0.6f);
            float bx = x + w / 2 - iconSize / 2;
            float by = y + h * 0.1f;
            RectF dst = new RectF(bx, by, bx + iconSize, by + iconSize);
            c.drawBitmap(bmp, null, dst, paint);
            // gun overlay (tiny)
            Bitmap gun = towerGunBmp[type];
            if (gun != null) c.drawBitmap(gun, null, dst, paint);
        }

        textP.setColor(affordable ? Color.WHITE : 0xFF9E9E9E);
        textP.setTextSize(h * 0.16f);
        textP.setFakeBoldText(true);
        String n = Tower.NAME[type];
        float tw = textP.measureText(n);
        c.drawText(n, x + w / 2 - tw / 2, y + h * 0.82f, textP);
        textP.setColor(affordable ? 0xFFFFEB3B : 0xFFE57373);
        textP.setTextSize(h * 0.14f);
        String cost = "$" + Tower.BASE_COST[type];
        float tw2 = textP.measureText(cost);
        c.drawText(cost, x + w / 2 - tw2 / 2, y + h * 0.96f, textP);
        textP.setFakeBoldText(false);
        textP.setColor(Color.WHITE);
        textP.setTextSize(tile * 0.38f);
    }

    // bottom upgrade panel geometry
    float upX, upY, upW, upH;

    void drawUpgradePanel(Canvas c) {
        // Bottom horizontal panel over the play area
        upH = Math.min(playH * 0.5f, screenH * 0.36f);
        upY = screenH - upH;
        upX = 0;
        upW = panelX;
        paint.setShader(null);
        // shadow above panel
        paint.setColor(0x88000000);
        c.drawRect(upX, upY - 8, upX + upW, upY, paint);
        // panel body
        LinearGradient lg = new LinearGradient(0, upY, 0, screenH, 0xFF263238, 0xFF1A237E, Shader.TileMode.CLAMP);
        paint.setShader(lg);
        c.drawRect(upX, upY, upX + upW, screenH, paint);
        paint.setShader(null);
        // top accent line
        paint.setColor(0xFFFFEB3B);
        c.drawRect(upX, upY, upX + upW, upY + 4, paint);

        // ---- Left: Tower header section ----
        float infoW = upW * 0.22f;
        float pad = 12;
        // big tower preview
        float iconSize = Math.min(upH * 0.55f, infoW * 0.6f);
        float ix = upX + pad;
        float iy = upY + pad;
        Bitmap bmp = towerBmp[selectedTower.type];
        if (bmp != null) {
            RectF dst = new RectF(ix, iy, ix + iconSize, iy + iconSize);
            // bg badge
            paint.setColor(0xFF455A64);
            c.drawRoundRect(new RectF(ix - 6, iy - 6, ix + iconSize + 6, iy + iconSize + 6), 14, 14, paint);
            paint.setColor(0xFF607D8B);
            c.drawRoundRect(new RectF(ix - 2, iy - 2, ix + iconSize + 2, iy + iconSize + 2), 12, 12, paint);
            c.drawBitmap(bmp, null, dst, paint);
            Bitmap gun = towerGunBmp[selectedTower.type];
            if (gun != null) c.drawBitmap(gun, null, dst, paint);
        }
        textP.setColor(0xFFFFEB3B);
        textP.setTextSize(upH * 0.13f);
        textP.setFakeBoldText(true);
        c.drawText(Tower.NAME[selectedTower.type], ix + iconSize + 8, iy + iconSize * 0.35f, textP);
        textP.setFakeBoldText(false);
        textP.setTextSize(upH * 0.08f);
        textP.setColor(0xFFB0BEC5);
        c.drawText("DMG " + selectedTower.damage(), ix + iconSize + 8, iy + iconSize * 0.6f, textP);
        c.drawText("R " + (int) selectedTower.range(), ix + iconSize + 8, iy + iconSize * 0.78f, textP);
        c.drawText(String.format("%.1f/s", selectedTower.rate()), ix + iconSize + 8, iy + iconSize * 0.96f, textP);

        // sell/close at bottom of info section
        int sellAmt = selectedTower.totalSpent * 7 / 10;
        float bh = upH * 0.16f;
        float by = screenH - bh - pad;
        drawPillButton(c, upX + pad, by, infoW * 0.55f, bh, "Sell $" + sellAmt, 0xFFD84315);
        drawPillButton(c, upX + pad + infoW * 0.58f, by, infoW * 0.4f, bh, "Close", 0xFF607D8B);

        // ---- Middle & right: two upgrade paths side by side ----
        float pathX = upX + infoW + pad;
        float pathTotalW = upW - infoW - pad * 2;
        float pathW = (pathTotalW - pad) / 2f;
        float pathY = upY + pad;
        float pathH = upH - pad * 2;
        drawUpgradePathBig(c, pathX, pathY, pathW, pathH, 0, 0xFFFFEB3B);
        drawUpgradePathBig(c, pathX + pathW + pad, pathY, pathW, pathH, 1, 0xFF40C4FF);

        textP.setTextSize(tile * 0.38f);
    }

    void drawUpgradePathBig(Canvas c, float x, float y, float w, float h, int path, int accent) {
        paint.setShader(null);
        paint.setColor(Tower.darken(0xFF37474F, 0.7f));
        c.drawRoundRect(new RectF(x + 3, y + 4, x + w + 3, y + h + 4), 16, 16, paint);
        paint.setColor(0xFF37474F);
        c.drawRoundRect(new RectF(x, y, x + w, y + h), 16, 16, paint);
        // header strip
        paint.setColor(accent);
        c.drawRoundRect(new RectF(x, y, x + w, y + h * 0.22f), 16, 16, paint);
        paint.setColor(Tower.darken(accent, 0.7f));
        c.drawRect(x, y + h * 0.18f, x + w, y + h * 0.22f, paint);

        // header text
        textP.setColor(0xFF263238);
        textP.setTextSize(h * 0.16f);
        textP.setFakeBoldText(true);
        c.drawText("Path " + (path == 0 ? "A" : "B"), x + 12, y + h * 0.16f, textP);

        // tier preview thumbnails (3 mini towers + tier badges)
        Bitmap baseBmp = towerBmp[selectedTower.type];
        Bitmap gunBmp = towerGunBmp[selectedTower.type];
        for (int t = 0; t < 3; t++) {
            float tx = x + w * 0.38f + t * w * 0.21f;
            float ty = y + h * 0.55f;
            float sz = h * 0.42f;
            boolean owned = selectedTower.tiers[path] > t;
            boolean next = selectedTower.tiers[path] == t;
            UpgradeDef u = UpgradeDef.TREE[selectedTower.type][path][t];
            // backdrop
            paint.setShader(null);
            paint.setColor(owned ? Tower.darken(accent, 0.6f) : (next ? 0xFF1A237E : 0xFF263238));
            c.drawCircle(tx, ty, sz / 2 + 8, paint);
            paint.setColor(owned ? accent : (next ? 0xFF42A5F5 : 0x44FFFFFF));
            c.drawCircle(tx, ty, sz / 2 + 4, paint);
            // mini tower
            if (baseBmp != null) {
                RectF dst = new RectF(tx - sz / 2, ty - sz / 2, tx + sz / 2, ty + sz / 2);
                c.drawBitmap(baseBmp, null, dst, paint);
                if (gunBmp != null) {
                    c.save();
                    c.rotate(-30 + t * 30, tx, ty);
                    c.drawBitmap(gunBmp, null, dst, paint);
                    c.restore();
                }
            }
            // tier corner badges (accent-colored chevrons)
            paint.setColor(accent);
            for (int k = 0; k <= t; k++) {
                Path chev = new Path();
                float baseX = tx + sz / 2 + 2;
                float baseY = ty - sz / 2 - 2 + k * sz * 0.3f;
                chev.moveTo(baseX - sz * 0.2f, baseY);
                chev.lineTo(baseX, baseY + sz * 0.1f);
                chev.lineTo(baseX - sz * 0.2f, baseY + sz * 0.2f);
                chev.close();
                c.drawPath(chev, paint);
            }
            // tier number label below
            textP.setColor(owned ? 0xFFFFEB3B : (next ? 0xFFFFFFFF : 0xFF9E9E9E));
            textP.setTextSize(h * 0.13f);
            textP.setFakeBoldText(true);
            String lab = "T" + (t + 1);
            float lw = textP.measureText(lab);
            c.drawText(lab, tx - lw / 2, ty + sz / 2 + h * 0.18f, textP);
            // status overlay
            if (owned) {
                paint.setColor(0xFF4CAF50);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(4);
                c.drawCircle(tx, ty, sz / 2 + 10, paint);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0xFF4CAF50);
                c.drawCircle(tx + sz * 0.45f, ty - sz * 0.45f, sz * 0.18f, paint);
                paint.setColor(0xFFFFFFFF);
                textP.setColor(0xFFFFFFFF);
                textP.setTextSize(sz * 0.3f);
                c.drawText("✓", tx + sz * 0.45f - sz * 0.1f, ty - sz * 0.35f, textP);
            } else if (next) {
                // pulsing yellow ring
                float puls = 1f + (float) Math.sin(totalTime * 4) * 0.05f;
                paint.setColor(0xFFFFEB3B);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(4);
                c.drawCircle(tx, ty, (sz / 2 + 10) * puls, paint);
                paint.setStyle(Paint.Style.FILL);
            }
            textP.setFakeBoldText(false);
        }

        UpgradeDef next = selectedTower.nextUpgrade(path);
        textP.setFakeBoldText(false);
        if (next == null) {
            textP.setColor(0xFF4CAF50);
            textP.setTextSize(h * 0.16f);
            String done = "✓ MAXED";
            c.drawText(done, x + 12, y + h * 0.55f, textP);
        } else {
            textP.setColor(Color.WHITE);
            textP.setTextSize(h * 0.16f);
            textP.setFakeBoldText(true);
            c.drawText(next.name, x + 12, y + h * 0.45f, textP);
            textP.setFakeBoldText(false);
            textP.setTextSize(h * 0.11f);
            textP.setColor(0xFFB0BEC5);
            c.drawText(upgradeDesc(next), x + 12, y + h * 0.62f, textP);

            // buy button
            float btw = w * 0.36f;
            float bth = h * 0.28f;
            float btx = x + w - btw - 8;
            float bty = y + h - bth - 8;
            boolean afford = cash >= next.cost;
            drawPillButton(c, btx, bty, btw, bth, "Buy $" + next.cost, afford ? 0xFF388E3C : 0xFF616161);
        }
        textP.setColor(Color.WHITE);
        textP.setTextSize(tile * 0.38f);
    }

    String upgradeDesc(UpgradeDef u) {
        StringBuilder sb = new StringBuilder();
        if (u.dmgAdd > 0) sb.append("+").append(u.dmgAdd).append("dmg ");
        if (u.pierceAdd > 0) sb.append("+").append(u.pierceAdd).append("pierce ");
        if (u.rateMult > 1.01f) sb.append("+").append((int) ((u.rateMult - 1f) * 100)).append("% rate ");
        if (u.rangeMult > 1.01f) sb.append("+").append((int) ((u.rangeMult - 1f) * 100)).append("% range ");
        if (u.special == UpgradeDef.SP_LEAD_POP) sb.append("Lead-pop ");
        if (u.special == UpgradeDef.SP_TRIPLE_SHOT) sb.append("Triple shot ");
        if (u.special == UpgradeDef.SP_LASER) sb.append("Laser ");
        if (u.special == UpgradeDef.SP_PLASMA) sb.append("Plasma ");
        if (u.special == UpgradeDef.SP_FREEZE) sb.append("Stronger freeze ");
        if (u.special == UpgradeDef.SP_NINJA_CAMO) sb.append("Anti-Lead ");
        if (u.special == UpgradeDef.SP_SLOW_DARTS) sb.append("Slows ");
        if (u.special == UpgradeDef.SP_BIGGER_BOMBS) sb.append("Huge AoE ");
        if (u.special == UpgradeDef.SP_HOMING) sb.append("Homing ");
        if (sb.length() == 0) sb.append("upgrade");
        return sb.toString().trim();
    }

    // ===================== MAIN MENU =====================
    void drawMainMenu(Canvas c) {
        // Sky gradient background — shifts subtly over time
        float skyShift = (float) Math.sin(menuAnim * 0.2f) * 0.05f;
        int skyTop = Bloon.lighten(0xFF40C4FF, Math.max(0, skyShift));
        paint.setShader(new LinearGradient(0, 0, 0, screenH, skyTop, 0xFF1565C0, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, screenW, screenH, paint);
        paint.setShader(null);
        // Sand & water
        paint.setColor(0xFFFFE082);
        Path beach = new Path();
        beach.moveTo(0, screenH * 0.65f);
        beach.cubicTo(screenW * 0.3f, screenH * 0.55f, screenW * 0.7f, screenH * 0.75f, screenW, screenH * 0.62f);
        beach.lineTo(screenW, screenH);
        beach.lineTo(0, screenH);
        beach.close();
        c.drawPath(beach, paint);
        paint.setColor(0xFFFFCA28);
        c.drawPath(beach, paint);
        // sun with rotating rays
        float sunR = screenH * 0.06f;
        float sunX = screenW * 0.82f, sunY = screenH * 0.18f;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(8);
        paint.setColor(0xAAFFEB3B);
        for (int i = 0; i < 12; i++) {
            float a = (float) (menuAnim * 0.4f + i * Math.PI / 6);
            float r1 = sunR * 1.2f;
            float r2 = sunR * 1.8f;
            c.drawLine(
                    sunX + (float) Math.cos(a) * r1, sunY + (float) Math.sin(a) * r1,
                    sunX + (float) Math.cos(a) * r2, sunY + (float) Math.sin(a) * r2, paint);
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x55FFFFFF);
        c.drawCircle(sunX, sunY, sunR * 1.4f, paint);
        paint.setColor(0xFFFFEB3B);
        c.drawCircle(sunX, sunY, sunR, paint);
        paint.setColor(0xCCFFFFFF);
        c.drawCircle(sunX - sunR * 0.3f, sunY - sunR * 0.35f, sunR * 0.35f, paint);
        // clouds drift
        drawCloud(c, wrapX(screenW * 0.15f + menuAnim * 8f), screenH * 0.13f, screenH * 0.055f);
        drawCloud(c, wrapX(screenW * 0.55f + menuAnim * 14f), screenH * 0.08f, screenH * 0.065f);
        drawCloud(c, wrapX(screenW * 0.9f + menuAnim * 10f), screenH * 0.3f, screenH * 0.045f);
        // floating bloons (drift + bob)
        java.util.Random rng = new java.util.Random(7);
        for (int i = 0; i < 16; i++) {
            int t = rng.nextInt(8);
            float bx0 = rng.nextFloat() * screenW;
            float by0 = rng.nextFloat() * screenH * 0.7f;
            float br = screenH * (0.04f + rng.nextFloat() * 0.03f);
            float bx = bx0 + (float) Math.sin(menuAnim * 0.5f + i * 0.7f) * br * 1.5f;
            float by = by0 + (float) Math.sin(menuAnim * 0.8f + i) * br * 0.5f;
            // skip drawing decorations behind buttons
            if (bx > screenW * 0.32f && bx < screenW * 0.68f && by > screenH * 0.4f && by < screenH * 0.95f) continue;
            if (bloonBmp[t] != null) {
                RectF dst = new RectF(bx - br, by - br, bx + br, by + br * 1.2f);
                c.drawBitmap(bloonBmp[t], null, dst, paint);
            }
        }
        // big logo (animated bob + slight rotation)
        float logoBob = (float) Math.sin(menuAnim * 1.4f) * screenH * 0.012f;
        float logoTilt = (float) Math.sin(menuAnim * 0.9f) * 1.5f;
        c.save();
        c.rotate(logoTilt, screenW * 0.5f, screenH * 0.18f + logoBob);
        drawLogo(c, screenW * 0.5f, screenH * 0.18f + logoBob);
        c.restore();

        // central button stack (animated pulse)
        float btnW = Math.min(screenW * 0.42f, 700);
        float btnH = screenH * 0.09f;
        float gap = btnH * 0.16f;
        float startY = screenH * 0.34f;
        float cx = screenW * 0.5f;
        String[] labels = {"QUICK PLAY", "WORLDS", "TOWERS", "RECORDS", "SETTINGS", "HOW TO PLAY"};
        int[] colors = {0xFF66BB6A, 0xFFFFA000, 0xFF42A5F5, 0xFFFFEB3B, 0xFFB0BEC5, 0xFFAB47BC};
        int[] icons = {0, 1, 2, 4, 5, 3};
        for (int i = 0; i < 6; i++) {
            float scale = 1f + (float) Math.sin(menuAnim * 2.5f + i * 0.7f) * 0.013f;
            float y = startY + i * (btnH + gap);
            float dh = btnH * (scale - 1f) / 2f;
            drawMenuButton(c, cx - btnW * scale / 2, y - dh, btnW * scale, btnH * scale, labels[i], colors[i], icons[i]);
        }

        // side ornaments — seal characters with balloons
        drawSideCharacter(c, screenW * 0.13f, screenH * 0.6f, screenH * 0.18f, 0);
        drawSideCharacter(c, screenW * 0.87f, screenH * 0.6f, screenH * 0.18f, 1);

        // top-right currency
        drawCoinBubble(c, screenW - tile * 2.6f - 20, screenH * 0.06f, String.valueOf(totalCoins), 0xFFFFEB3B);
        // top-left lives badge
        drawHeartBubble(c, 20, screenH * 0.06f, lives > 0 ? lives : 120);
        // footer
        textP.setColor(0x88FFFFFF);
        textP.setTextSize(tile * 0.28f);
        String tag = "v1.2  ·  tap a menu option";
        float tw = textP.measureText(tag);
        c.drawText(tag, screenW / 2f - tw / 2, screenH - 20, textP);
        textP.setTextSize(tile * 0.38f);
    }

    float wrapX(float x) {
        float W = screenW + 300;
        x = x % W;
        if (x < 0) x += W;
        return x - 150;
    }

    void drawCloud(Canvas c, float x, float y, float r) {
        paint.setShader(null);
        paint.setColor(0xCCFFFFFF);
        c.drawCircle(x - r, y, r, paint);
        c.drawCircle(x + r, y, r, paint);
        c.drawCircle(x, y - r * 0.5f, r * 1.1f, paint);
        c.drawCircle(x, y + r * 0.3f, r * 0.9f, paint);
        c.drawCircle(x + r * 1.7f, y + r * 0.1f, r * 0.8f, paint);
    }

    void drawLogo(Canvas c, float cx, float cy) {
        float w = Math.min(screenW * 0.6f, 800);
        float h = screenH * 0.22f;
        paint.setShader(null);
        // background panel
        paint.setColor(0x44000000);
        c.drawRoundRect(new RectF(cx - w / 2 + 8, cy - h / 2 + 10, cx + w / 2 + 8, cy + h / 2 + 10), 36, 36, paint);
        paint.setColor(0xFFFF6F00);
        c.drawRoundRect(new RectF(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2), 36, 36, paint);
        paint.setColor(0xFFFFA726);
        c.drawRoundRect(new RectF(cx - w / 2 + 6, cy - h / 2 + 6, cx + w / 2 - 6, cy + h / 2 - 6), 30, 30, paint);
        paint.setColor(0x44FFFFFF);
        c.drawRoundRect(new RectF(cx - w / 2 + 12, cy - h / 2 + 12, cx + w / 2 - 12, cy - h * 0.05f), 24, 24, paint);
        textP.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        textP.setTextSize(h * 0.46f);
        textP.setColor(0xFF3E2723);
        String line1 = "BLOON";
        String line2 = "BATTLE";
        float w1 = textP.measureText(line1);
        float w2 = textP.measureText(line2);
        c.drawText(line1, cx - w1 / 2 + 3, cy - h * 0.02f + 3, textP);
        c.drawText(line2, cx - w2 / 2 + 3, cy + h * 0.35f + 3, textP);
        textP.setColor(0xFFFFEB3B);
        c.drawText(line1, cx - w1 / 2, cy - h * 0.02f, textP);
        textP.setColor(0xFFE53935);
        c.drawText(line2, cx - w2 / 2, cy + h * 0.35f, textP);
        textP.setTypeface(android.graphics.Typeface.DEFAULT);
        textP.setTextSize(tile * 0.38f);
    }

    void drawMenuButton(Canvas c, float x, float y, float w, float h, String label, int color, int iconType) {
        paint.setShader(null);
        // press-shadow
        paint.setColor(0x66000000);
        c.drawRoundRect(new RectF(x + 4, y + 8, x + w + 4, y + h + 8), h * 0.5f, h * 0.5f, paint);
        // outline
        paint.setColor(Tower.darken(color, 0.55f));
        c.drawRoundRect(new RectF(x, y, x + w, y + h), h * 0.5f, h * 0.5f, paint);
        paint.setColor(color);
        c.drawRoundRect(new RectF(x + 5, y + 5, x + w - 5, y + h - 5), h * 0.5f, h * 0.5f, paint);
        // glossy highlight
        paint.setColor(0x55FFFFFF);
        c.drawRoundRect(new RectF(x + 10, y + 8, x + w - 10, y + h * 0.42f), h * 0.5f, h * 0.5f, paint);

        // icon on left (round badge with mini tower)
        float icCx = x + h * 0.55f;
        float icR = h * 0.35f;
        paint.setColor(0xFFFFFFFF);
        c.drawCircle(icCx, y + h / 2, icR + 6, paint);
        paint.setColor(Tower.darken(color, 0.4f));
        c.drawCircle(icCx, y + h / 2, icR, paint);
        // mini icon
        Bitmap bmp = null;
        switch (iconType) {
            case 0: bmp = towerBmp[T_DART]; break;       // quick play
            case 1: bmp = towerBmp[T_NINJA]; break;      // worlds
            case 2: bmp = towerBmp[T_WIZARD]; break;     // towers
            case 3: bmp = towerBmp[T_SUPER]; break;      // how to play
            case 4: bmp = towerBmp[T_FARM]; break;       // records (banana farm = $)
            case 5: bmp = towerBmp[T_BOMB]; break;       // settings (cogwheel-ish)
        }
        if (bmp != null) {
            RectF dst = new RectF(icCx - icR, y + h / 2 - icR, icCx + icR, y + h / 2 + icR);
            c.drawBitmap(bmp, null, dst, paint);
        }
        // label
        textP.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        textP.setColor(0xFF3E2723);
        textP.setTextSize(h * 0.42f);
        float lw = textP.measureText(label);
        c.drawText(label, x + h + 30 + ((w - h - 30) / 2) - lw / 2 + 2, y + h * 0.65f + 2, textP);
        textP.setColor(0xFFFFFFFF);
        c.drawText(label, x + h + 30 + ((w - h - 30) / 2) - lw / 2, y + h * 0.65f, textP);
        textP.setTypeface(android.graphics.Typeface.DEFAULT);
        textP.setTextSize(tile * 0.38f);
    }

    void drawSideCharacter(Canvas c, float cx, float cy, float r, int side) {
        // Big SEAL character with subtle idle animation
        float sway = (float) Math.sin(menuAnim * 1.4f + side) * r * 0.04f;
        float bob = (float) Math.sin(menuAnim * 2.0f + side * 1.7f) * r * 0.06f;
        cy += bob;
        paint.setShader(null);
        // shadow
        paint.setColor(0x55000000);
        c.drawOval(new RectF(cx - r * 1.0f, cy + r * 1.05f, cx + r * 1.0f, cy + r * 1.25f), paint);
        // body
        paint.setColor(Tower.darken(0xFF90A4AE, 0.85f));
        c.drawOval(new RectF(cx - r * 0.95f, cy - r * 0.1f, cx + r * 0.95f, cy + r * 1.1f), paint);
        paint.setColor(0xFF90A4AE);
        c.drawOval(new RectF(cx - r * 0.85f, cy - r * 0.05f, cx + r * 0.85f, cy + r * 1.0f), paint);
        // belly
        paint.setColor(0xFFECEFF1);
        c.drawOval(new RectF(cx - r * 0.55f, cy + r * 0.15f, cx + r * 0.55f, cy + r * 0.95f), paint);
        // tail (back fluke)
        paint.setColor(Tower.darken(0xFF90A4AE, 0.8f));
        Path tail = new Path();
        tail.moveTo(cx, cy + r * 0.95f);
        tail.lineTo(cx - r * 0.55f, cy + r * 1.2f);
        tail.lineTo(cx, cy + r * 1.05f);
        tail.lineTo(cx + r * 0.55f, cy + r * 1.2f);
        tail.close();
        c.drawPath(tail, paint);
        // head
        float hx = cx + sway, hy = cy - r * 0.35f;
        paint.setColor(Tower.darken(0xFF90A4AE, 0.85f));
        c.drawCircle(hx, hy, r * 0.7f, paint);
        paint.setColor(0xFF90A4AE);
        c.drawCircle(hx, hy, r * 0.62f, paint);
        // face oval
        paint.setColor(0xFFECEFF1);
        c.drawOval(new RectF(hx - r * 0.45f, hy - r * 0.2f, hx + r * 0.45f, hy + r * 0.45f), paint);
        // muzzle puffs
        paint.setColor(0xFFFFFFFF);
        c.drawCircle(hx - r * 0.13f, hy + r * 0.18f, r * 0.13f, paint);
        c.drawCircle(hx + r * 0.13f, hy + r * 0.18f, r * 0.13f, paint);
        // eyes (blink)
        boolean blink = ((int) (menuAnim * 0.7f + side) % 6) == 0
                && Math.sin(menuAnim * 8 + side) > 0.95;
        paint.setColor(0xFF263238);
        if (blink) {
            paint.setStrokeWidth(3);
            paint.setStyle(Paint.Style.STROKE);
            c.drawLine(hx - r * 0.28f, hy - r * 0.05f, hx - r * 0.12f, hy - r * 0.05f, paint);
            c.drawLine(hx + r * 0.12f, hy - r * 0.05f, hx + r * 0.28f, hy - r * 0.05f, paint);
            paint.setStyle(Paint.Style.FILL);
        } else {
            c.drawCircle(hx - r * 0.2f, hy - r * 0.05f, r * 0.1f, paint);
            c.drawCircle(hx + r * 0.2f, hy - r * 0.05f, r * 0.1f, paint);
            paint.setColor(0xFFFFFFFF);
            c.drawCircle(hx - r * 0.17f, hy - r * 0.08f, r * 0.04f, paint);
            c.drawCircle(hx + r * 0.23f, hy - r * 0.08f, r * 0.04f, paint);
        }
        // nose
        paint.setColor(0xFF263238);
        c.drawCircle(hx, hy + r * 0.1f, r * 0.07f, paint);
        // whisker dots
        paint.setColor(0xFF455A64);
        for (int i = 0; i < 3; i++) {
            float dx = r * (0.2f + i * 0.06f);
            float dy = r * (0.18f + i * 0.015f);
            c.drawCircle(hx - dx, hy + dy, r * 0.018f, paint);
            c.drawCircle(hx + dx, hy + dy, r * 0.018f, paint);
        }
        // mouth
        paint.setColor(0xFF263238);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3);
        Path mouth = new Path();
        mouth.moveTo(hx - r * 0.08f, hy + r * 0.25f);
        mouth.quadTo(hx, hy + r * 0.33f, hx + r * 0.08f, hy + r * 0.25f);
        c.drawPath(mouth, paint);
        paint.setStyle(Paint.Style.FILL);
        // flippers
        paint.setColor(Tower.darken(0xFF90A4AE, 0.8f));
        c.drawOval(new RectF(cx - r * 1.05f, cy + r * 0.15f, cx - r * 0.75f, cy + r * 0.55f), paint);
        c.drawOval(new RectF(cx + r * 0.75f, cy + r * 0.15f, cx + r * 1.05f, cy + r * 0.55f), paint);
        // balloon
        float bx = cx + r * 1.05f, by = cy - r * 0.7f + bob * 0.5f;
        float br = r * 0.45f;
        int bType = side == 0 ? B_BLUE : B_PINK;
        if (bloonBmp[bType] != null) {
            RectF dst = new RectF(bx - br, by - br, bx + br, by + br * 1.2f);
            c.drawBitmap(bloonBmp[bType], null, dst, paint);
        }
        paint.setColor(0xCCFFFFFF);
        paint.setStrokeWidth(2);
        paint.setStyle(Paint.Style.STROKE);
        c.drawLine(bx, by + br, cx + r * 0.85f, cy + r * 0.25f, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    // ===================== MAPS MENU =====================
    void drawMapsMenu(Canvas c) {
        paint.setShader(new LinearGradient(0, 0, 0, screenH, 0xFF1A237E, 0xFF000000, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, screenW, screenH, paint);
        paint.setShader(null);

        // header bar
        paint.setShader(new LinearGradient(0, 0, 0, screenH * 0.12f, 0xFF0D47A1, 0xFF1565C0, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, screenW, screenH * 0.12f, paint);
        paint.setShader(null);

        textP.setColor(0xFFFFEB3B);
        textP.setTextSize(screenH * 0.06f);
        textP.setFakeBoldText(true);
        c.drawText("CHOOSE MAP", 24, screenH * 0.08f, textP);
        textP.setFakeBoldText(false);
        textP.setTextSize(tile * 0.38f);

        drawPillButton(c, screenW - tile * 2 - 20, screenH * 0.02f, tile * 2, screenH * 0.075f, "BACK", 0xFF607D8B);

        // world tabs
        float tabsY = screenH * 0.15f;
        float tabH = screenH * 0.085f;
        float tabW = (screenW - 60) / 4f;
        for (int i = 0; i < 4; i++) {
            float x = 30 + i * tabW;
            boolean sel = selectedWorld == i;
            int col = worldAccent(i);
            paint.setColor(sel ? col : Tower.darken(col, 0.45f));
            c.drawRoundRect(new RectF(x + 6, tabsY + 4, x + tabW - 6, tabsY + tabH + 4), 18, 18, paint);
            paint.setColor(sel ? Tower.darken(col, 0.7f) : Tower.darken(col, 0.35f));
            c.drawRoundRect(new RectF(x + 6, tabsY, x + tabW - 6, tabsY + tabH), 18, 18, paint);
            paint.setColor(sel ? col : Tower.darken(col, 0.6f));
            c.drawRoundRect(new RectF(x + 10, tabsY + 3, x + tabW - 10, tabsY + tabH - 3), 14, 14, paint);
            paint.setColor(0x44FFFFFF);
            c.drawRoundRect(new RectF(x + 14, tabsY + 6, x + tabW - 14, tabsY + tabH * 0.5f), 12, 12, paint);
            textP.setColor(0xFFFFFFFF);
            textP.setTextSize(tabH * 0.42f);
            textP.setFakeBoldText(true);
            String n = MapDef.WORLD_NAME[i];
            float nw = textP.measureText(n);
            c.drawText(n, x + tabW / 2 - nw / 2, tabsY + tabH * 0.65f, textP);
            textP.setFakeBoldText(false);
        }
        textP.setTextSize(tile * 0.38f);

        // grid of map cards
        float cardsTop = tabsY + tabH + 20;
        float pad = 22;
        float cardW = (screenW - pad * 3) / 2f;
        float cardH = (screenH - cardsTop - pad - screenH * 0.05f) / 2;
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
            c.drawText(s, screenW / 2f - ww / 2, cardsTop + screenH * 0.2f, textP);
        }
    }

    void drawMapCard(Canvas c, float x, float y, float w, float h, MapDef m) {
        int top = worldAccent(m.world);
        int bot = Tower.darken(top, 0.45f);
        paint.setShader(null);
        paint.setColor(0x99000000);
        c.drawRoundRect(new RectF(x + 6, y + 8, x + w + 6, y + h + 8), 22, 22, paint);
        paint.setColor(Tower.darken(top, 0.3f));
        c.drawRoundRect(new RectF(x, y, x + w, y + h), 22, 22, paint);
        paint.setShader(new LinearGradient(x, y, x, y + h, top, bot, Shader.TileMode.CLAMP));
        c.drawRoundRect(new RectF(x + 6, y + 6, x + w - 6, y + h - 6), 18, 18, paint);
        paint.setShader(null);
        paint.setColor(0x33FFFFFF);
        c.drawRoundRect(new RectF(x + 10, y + 10, x + w - 10, y + h * 0.45f), 16, 16, paint);

        // preview area
        float prevX = x + 16;
        float prevY = y + h * 0.20f;
        float prevW = w - 32;
        float prevH = h * 0.55f;
        paint.setColor(0xFF1B5E20);
        if (m.world == MapDef.W_DESERT) paint.setColor(0xFFFF8F00);
        else if (m.world == MapDef.W_SNOW) paint.setColor(0xFFB3E5FC);
        else if (m.world == MapDef.W_LAVA) paint.setColor(0xFF263238);
        c.drawRoundRect(new RectF(prevX, prevY, prevX + prevW, prevY + prevH), 14, 14, paint);

        // preview path with proper styling
        Path p = new Path();
        boolean first = true;
        for (float[] wp : m.waypoints) {
            float pxw = prevX + ((wp[0] + 0.5f) / MapDef.COLS) * prevW;
            float pyw = prevY + ((wp[1] + 0.5f) / MapDef.ROWS) * prevH;
            if (first) { p.moveTo(pxw, pyw); first = false; }
            else p.lineTo(pxw, pyw);
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(0xFF3E2723);
        paint.setStrokeWidth(prevH * 0.12f);
        c.drawPath(p, paint);
        paint.setColor(0xFFBCAAA4);
        paint.setStrokeWidth(prevH * 0.085f);
        c.drawPath(p, paint);
        paint.setStyle(Paint.Style.FILL);

        // name
        textP.setColor(0xFFFFFFFF);
        textP.setTextSize(h * 0.13f);
        textP.setFakeBoldText(true);
        c.drawText(m.name, x + 18, y + h * 0.15f, textP);
        textP.setFakeBoldText(false);
        // stars
        for (int i = 0; i < 5; i++) {
            paint.setColor(i < m.difficulty ? 0xFFFFEB3B : 0x55000000);
            float sx = x + w - 24 - (4 - i) * (h * 0.07f);
            float sy = y + h * 0.1f;
            drawStar(c, sx, sy, h * 0.04f, paint.getColor());
        }
        // play badge
        float badgeW = w * 0.32f;
        float badgeH = h * 0.18f;
        drawPillButton(c, x + w - badgeW - 14, y + h - badgeH - 14, badgeW, badgeH, "PLAY ▶", 0xFFE53935);

        textP.setTextSize(tile * 0.38f);
    }

    void drawStar(Canvas c, float cx, float cy, float r, int col) {
        paint.setColor(col);
        Path star = new Path();
        for (int i = 0; i < 10; i++) {
            double a = -Math.PI / 2 + i * Math.PI / 5;
            double rr = (i % 2 == 0) ? r : r * 0.45;
            float px = cx + (float) (Math.cos(a) * rr);
            float py = cy + (float) (Math.sin(a) * rr);
            if (i == 0) star.moveTo(px, py); else star.lineTo(px, py);
        }
        star.close();
        c.drawPath(star, paint);
    }

    int worldAccent(int w) {
        switch (w) {
            case MapDef.W_DESERT: return 0xFFFFA000;
            case MapDef.W_SNOW: return 0xFF26C6DA;
            case MapDef.W_LAVA: return 0xFFEF5350;
            default: return 0xFF66BB6A;
        }
    }

    // ===================== TOWERS MENU =====================
    void drawTowersMenu(Canvas c) {
        paint.setShader(new LinearGradient(0, 0, 0, screenH, 0xFF1A237E, 0xFF000000, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, screenW, screenH, paint);
        paint.setShader(null);

        paint.setShader(new LinearGradient(0, 0, 0, screenH * 0.12f, 0xFF0D47A1, 0xFF1565C0, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, screenW, screenH * 0.12f, paint);
        paint.setShader(null);

        textP.setColor(0xFFFFEB3B);
        textP.setTextSize(screenH * 0.06f);
        textP.setFakeBoldText(true);
        c.drawText("TOWERS & UPGRADES", 24, screenH * 0.08f, textP);
        textP.setFakeBoldText(false);
        textP.setTextSize(tile * 0.38f);

        drawPillButton(c, screenW - tile * 2 - 20, screenH * 0.02f, tile * 2, screenH * 0.075f, "BACK", 0xFF607D8B);

        // 8 cards (4 cols × 2 rows)
        float top = screenH * 0.14f;
        float pad = 14;
        int cols = 4, rows = 3;
        float cw = (screenW - pad * (cols + 1)) / cols;
        float ch = (screenH - top - pad * (rows + 1)) / rows;
        for (int i = 0; i < Tower.TYPE_COUNT; i++) {
            int r = i / cols;
            int co = i % cols;
            float x = pad + co * (cw + pad);
            float y = top + pad + r * (ch + pad);
            drawTowerRoster(c, x, y, cw, ch, i);
        }
    }

    void drawTowerRoster(Canvas c, float x, float y, float w, float h, int type) {
        paint.setShader(null);
        paint.setColor(0xFF263238);
        c.drawRoundRect(new RectF(x + 4, y + 6, x + w + 4, y + h + 6), 18, 18, paint);
        paint.setColor(0xFF37474F);
        c.drawRoundRect(new RectF(x, y, x + w, y + h), 18, 18, paint);
        paint.setColor(0xFF455A64);
        c.drawRoundRect(new RectF(x + 4, y + 4, x + w - 4, y + h - 4), 14, 14, paint);
        // tower icon
        float iconY = y + 12;
        float iconSize = Math.min(w * 0.45f, h * 0.32f);
        if (towerBmp[type] != null) {
            RectF dst = new RectF(x + w / 2 - iconSize / 2, iconY, x + w / 2 + iconSize / 2, iconY + iconSize);
            c.drawBitmap(towerBmp[type], null, dst, paint);
            if (towerGunBmp[type] != null) c.drawBitmap(towerGunBmp[type], null, dst, paint);
        }
        textP.setColor(Color.WHITE);
        textP.setTextSize(h * 0.10f);
        textP.setFakeBoldText(true);
        String n = Tower.NAME[type];
        float tw = textP.measureText(n);
        c.drawText(n, x + w / 2 - tw / 2, iconY + iconSize + h * 0.1f, textP);
        textP.setFakeBoldText(false);
        textP.setTextSize(h * 0.075f);
        textP.setColor(0xFFFFEB3B);
        String cost = "$" + Tower.BASE_COST[type];
        float cw2 = textP.measureText(cost);
        c.drawText(cost, x + w / 2 - cw2 / 2, iconY + iconSize + h * 0.18f, textP);

        // 2 paths summary, each 3 tier name
        textP.setTextSize(h * 0.06f);
        textP.setColor(0xFFFFEB3B);
        c.drawText("Path A:", x + 8, y + h * 0.6f, textP);
        textP.setColor(0xFF40C4FF);
        c.drawText("Path B:", x + 8, y + h * 0.78f, textP);
        textP.setColor(0xFFE0E0E0);
        for (int p = 0; p < 2; p++) {
            for (int t = 0; t < 3; t++) {
                UpgradeDef u = UpgradeDef.TREE[type][p][t];
                if (u == null) continue;
                String s = "• " + u.name;
                float sy = y + h * (p == 0 ? 0.65f : 0.83f) + t * h * 0.045f;
                if (t > 0) sy = y + h * (p == 0 ? 0.6f : 0.78f) + (t + 1) * h * 0.06f;
                c.drawText(s, x + 12 + (t * (w / 3 - 8)) % w, y + h * (p == 0 ? 0.65f : 0.83f) + (t * 0) * h * 0.05f, textP);
            }
        }
        // simpler: just three small badges per path
        // Path A row
        float pyA = y + h * 0.68f;
        for (int t = 0; t < 3; t++) {
            float bx = x + 10 + t * (w / 3.3f);
            paint.setColor(0xFFFFEB3B);
            c.drawRoundRect(new RectF(bx, pyA, bx + w / 3.6f, pyA + h * 0.07f), 6, 6, paint);
            textP.setColor(0xFF263238);
            textP.setTextSize(h * 0.05f);
            UpgradeDef u = UpgradeDef.TREE[type][0][t];
            String label = u != null ? u.name : "-";
            float lw = textP.measureText(label);
            float pw = w / 3.6f;
            if (lw > pw - 6) {
                label = label.substring(0, Math.min(label.length(), Math.max(3, (int)((pw - 6) / (h * 0.03f)))));
            }
            c.drawText(label, bx + 4, pyA + h * 0.055f, textP);
        }
        float pyB = y + h * 0.86f;
        for (int t = 0; t < 3; t++) {
            float bx = x + 10 + t * (w / 3.3f);
            paint.setColor(0xFF40C4FF);
            c.drawRoundRect(new RectF(bx, pyB, bx + w / 3.6f, pyB + h * 0.07f), 6, 6, paint);
            textP.setColor(0xFF263238);
            textP.setTextSize(h * 0.05f);
            UpgradeDef u = UpgradeDef.TREE[type][1][t];
            String label = u != null ? u.name : "-";
            float pw = w / 3.6f;
            float lw = textP.measureText(label);
            if (lw > pw - 6) {
                label = label.substring(0, Math.min(label.length(), Math.max(3, (int)((pw - 6) / (h * 0.03f)))));
            }
            c.drawText(label, bx + 4, pyB + h * 0.055f, textP);
        }
        textP.setColor(Color.WHITE);
        textP.setTextSize(tile * 0.38f);
    }

    // ===================== HOW TO MENU =====================
    void drawHowToMenu(Canvas c) {
        paint.setShader(new LinearGradient(0, 0, 0, screenH, 0xFF1A237E, 0xFF000000, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, screenW, screenH, paint);
        paint.setShader(null);

        paint.setShader(new LinearGradient(0, 0, 0, screenH * 0.12f, 0xFF0D47A1, 0xFF1565C0, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, screenW, screenH * 0.12f, paint);
        paint.setShader(null);

        textP.setColor(0xFFFFEB3B);
        textP.setTextSize(screenH * 0.06f);
        textP.setFakeBoldText(true);
        c.drawText("HOW TO PLAY", 24, screenH * 0.08f, textP);
        textP.setFakeBoldText(false);
        drawPillButton(c, screenW - tile * 2 - 20, screenH * 0.02f, tile * 2, screenH * 0.075f, "BACK", 0xFF607D8B);

        textP.setColor(0xFFE0F2F1);
        textP.setTextSize(screenH * 0.04f);
        String[] lines = {
                "1. Pick a world and a map from the menu.",
                "2. Tap a tower in the right panel to buy it.",
                "3. Tap an empty grass tile to place the tower.",
                "4. Tap GO! at the top to spawn the wave.",
                "5. Tap a tower to open the bottom upgrade panel.",
                "6. Each tower has TWO upgrade paths, 3 tiers each.",
                "7. Use the SEND tab to send bloons for extra ECO income.",
                "8. Waves are infinite — survive as long as you can!",
                "",
                "Tips:",
                "• Banana Farm gives passive income every few seconds.",
                "• Glue Gunner slows; Boomerang and Mortar hit groups.",
                "• Ninja can pop Lead, Bomb explosions can't pop Black.",
                "• Super Monkey and Wizard are expensive but devastating.",
        };
        float yy = screenH * 0.18f;
        for (String s : lines) {
            c.drawText(s, 40, yy, textP);
            yy += screenH * 0.055f;
        }
        textP.setTextSize(tile * 0.38f);
    }

    // ===================== SETTINGS MENU =====================
    void drawSettingsMenu(Canvas c) {
        paint.setShader(new LinearGradient(0, 0, 0, screenH, 0xFF263238, 0xFF000000, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, screenW, screenH, paint);
        paint.setShader(null);
        paint.setShader(new LinearGradient(0, 0, 0, screenH * 0.12f, 0xFF0D47A1, 0xFF1565C0, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, screenW, screenH * 0.12f, paint);
        paint.setShader(null);
        textP.setColor(0xFFFFEB3B);
        textP.setTextSize(screenH * 0.06f);
        textP.setFakeBoldText(true);
        c.drawText("SETTINGS", 24, screenH * 0.08f, textP);
        textP.setFakeBoldText(false);
        drawPillButton(c, screenW - tile * 2 - 20, screenH * 0.02f, tile * 2, screenH * 0.075f, "BACK", 0xFF607D8B);

        float cx = screenW * 0.5f;
        float w = Math.min(screenW * 0.6f, 900);
        float h = screenH * 0.1f;
        String[] names = {"Sound Effects", "Music", "Auto-start next wave"};
        boolean[] vals = {sfxOn, musicOn, autoStart};
        for (int i = 0; i < 3; i++) {
            float y = screenH * 0.22f + i * (h + 24);
            drawToggleRow(c, cx - w / 2, y, w, h, names[i], vals[i]);
        }
        // credits
        textP.setColor(0xFF80DEEA);
        textP.setTextSize(screenH * 0.03f);
        c.drawText("Bloon Battle  ·  Built with custom Android build chain",
                cx - screenW * 0.2f, screenH * 0.92f, textP);
        textP.setTextSize(tile * 0.38f);
    }

    void drawToggleRow(Canvas c, float x, float y, float w, float h, String label, boolean on) {
        paint.setColor(0x66000000);
        c.drawRoundRect(new RectF(x + 4, y + 6, x + w + 4, y + h + 6), 18, 18, paint);
        paint.setColor(0xFF37474F);
        c.drawRoundRect(new RectF(x, y, x + w, y + h), 18, 18, paint);
        paint.setColor(0xFF455A64);
        c.drawRoundRect(new RectF(x + 4, y + 4, x + w - 4, y + h - 4), 14, 14, paint);
        textP.setColor(Color.WHITE);
        textP.setTextSize(h * 0.42f);
        textP.setFakeBoldText(true);
        c.drawText(label, x + 24, y + h * 0.62f, textP);
        textP.setFakeBoldText(false);
        // toggle pill
        float pW = h * 1.6f;
        float pH = h * 0.55f;
        float px = x + w - pW - 18;
        float py = y + (h - pH) / 2;
        paint.setColor(on ? 0xFF388E3C : 0xFF616161);
        c.drawRoundRect(new RectF(px, py, px + pW, py + pH), pH * 0.5f, pH * 0.5f, paint);
        // knob
        paint.setColor(0xFFFFFFFF);
        float knobR = pH * 0.45f;
        float kx = on ? px + pW - knobR - 4 : px + knobR + 4;
        c.drawCircle(kx, py + pH / 2, knobR, paint);
        // on/off label inside
        textP.setColor(0xFFFFFFFF);
        textP.setTextSize(pH * 0.6f);
        textP.setFakeBoldText(true);
        c.drawText(on ? "ON" : "OFF", on ? px + 14 : px + pW * 0.5f, py + pH * 0.7f, textP);
        textP.setFakeBoldText(false);
    }

    // ===================== RECORDS MENU =====================
    void drawRecordsMenu(Canvas c) {
        paint.setShader(new LinearGradient(0, 0, 0, screenH, 0xFF1A237E, 0xFF000000, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, screenW, screenH, paint);
        paint.setShader(null);
        paint.setShader(new LinearGradient(0, 0, 0, screenH * 0.12f, 0xFF0D47A1, 0xFF1565C0, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, screenW, screenH * 0.12f, paint);
        paint.setShader(null);
        textP.setColor(0xFFFFEB3B);
        textP.setTextSize(screenH * 0.06f);
        textP.setFakeBoldText(true);
        c.drawText("RECORDS", 24, screenH * 0.08f, textP);
        textP.setFakeBoldText(false);
        drawPillButton(c, screenW - tile * 2 - 20, screenH * 0.02f, tile * 2, screenH * 0.075f, "BACK", 0xFF607D8B);

        // total coins display
        drawCoinBubble(c, screenW * 0.5f - tile * 1.5f, screenH * 0.16f,
                "Total $" + totalCoins, 0xFFFFEB3B);

        // map records
        float top = screenH * 0.24f;
        float pad = 16;
        int cols = 4;
        int rows = 2;
        float cw = (screenW - pad * (cols + 1)) / cols;
        float ch = (screenH - top - pad * (rows + 1)) / rows;
        for (int i = 0; i < MapDef.ALL.length; i++) {
            int r = i / cols;
            int co = i % cols;
            float x = pad + co * (cw + pad);
            float y = top + pad + r * (ch + pad);
            drawRecordCard(c, x, y, cw, ch, MapDef.ALL[i]);
        }
    }

    void drawRecordCard(Canvas c, float x, float y, float w, float h, MapDef m) {
        int top = worldAccent(m.world);
        int bot = Tower.darken(top, 0.4f);
        paint.setShader(null);
        paint.setColor(0x99000000);
        c.drawRoundRect(new RectF(x + 4, y + 6, x + w + 4, y + h + 6), 18, 18, paint);
        paint.setShader(new LinearGradient(x, y, x, y + h, top, bot, Shader.TileMode.CLAMP));
        c.drawRoundRect(new RectF(x, y, x + w, y + h), 18, 18, paint);
        paint.setShader(null);
        paint.setColor(0x33FFFFFF);
        c.drawRoundRect(new RectF(x + 6, y + 6, x + w - 6, y + h * 0.4f), 14, 14, paint);
        textP.setColor(Color.WHITE);
        textP.setTextSize(h * 0.14f);
        textP.setFakeBoldText(true);
        c.drawText(m.name, x + 14, y + h * 0.25f, textP);
        textP.setFakeBoldText(false);
        textP.setTextSize(h * 0.1f);
        textP.setColor(0xFFB3E5FC);
        c.drawText(MapDef.WORLD_NAME[m.world], x + 14, y + h * 0.4f, textP);
        // best wave
        Integer best = bestWave.get(m.name);
        int bw = best == null ? 0 : best;
        textP.setTextSize(h * 0.28f);
        textP.setColor(0xFFFFEB3B);
        textP.setFakeBoldText(true);
        c.drawText("Wave " + bw, x + 14, y + h * 0.78f, textP);
        textP.setFakeBoldText(false);
        textP.setTextSize(h * 0.1f);
        textP.setColor(0xFFE0F2F1);
        c.drawText(bw == 0 ? "(never played)" : "best run", x + 14, y + h * 0.95f, textP);
    }

    // ===================== INPUT =====================
    public void onTouch(MotionEvent e) {
        lastTouchX = e.getX();
        lastTouchY = e.getY();
        if (e.getAction() != MotionEvent.ACTION_DOWN) return;
        try {
            switch (state) {
                case S_MAIN:    onTouchMain();    break;
                case S_MAPS:    onTouchMaps();    break;
                case S_TOWERS:  onTouchTowers();  break;
                case S_HOWTO:   onTouchHowTo();   break;
                case S_SETTINGS:onTouchSettings(); break;
                case S_RECORDS: onTouchRecords(); break;
                case S_PLAYING: onTouchGame();    break;
            }
        } catch (Throwable t) {
            popupMessage = "INPUT ERR: " + t.getClass().getSimpleName();
            popupTimer = 3f;
        }
    }

    void onTouchMain() {
        float btnW = Math.min(screenW * 0.42f, 700);
        float btnH = screenH * 0.09f;
        float gap = btnH * 0.16f;
        float startY = screenH * 0.34f;
        float cx = screenW * 0.5f;
        for (int i = 0; i < 6; i++) {
            float y = startY + i * (btnH + gap);
            if (lastTouchX > cx - btnW / 2 && lastTouchX < cx + btnW / 2
                    && lastTouchY > y && lastTouchY < y + btnH) {
                if (i == 0) loadMap(MapDef.ALL[0]);
                else if (i == 1) state = S_MAPS;
                else if (i == 2) state = S_TOWERS;
                else if (i == 3) state = S_RECORDS;
                else if (i == 4) state = S_SETTINGS;
                else if (i == 5) state = S_HOWTO;
                return;
            }
        }
    }

    void onTouchMaps() {
        // back button
        if (lastTouchX > screenW - tile * 2 - 20 && lastTouchY < screenH * 0.12f) {
            state = S_MAIN; return;
        }
        float tabsY = screenH * 0.15f;
        float tabH = screenH * 0.085f;
        float tabW = (screenW - 60) / 4f;
        if (lastTouchY > tabsY && lastTouchY < tabsY + tabH) {
            for (int i = 0; i < 4; i++) {
                float x = 30 + i * tabW;
                if (lastTouchX > x && lastTouchX < x + tabW) {
                    selectedWorld = i;
                    return;
                }
            }
        }
        float cardsTop = tabsY + tabH + 20;
        float pad = 22;
        float cardW = (screenW - pad * 3) / 2f;
        float cardH = (screenH - cardsTop - pad - screenH * 0.05f) / 2;
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

    void onTouchTowers() {
        if (lastTouchX > screenW - tile * 2 - 20 && lastTouchY < screenH * 0.12f) {
            state = S_MAIN; return;
        }
    }

    void onTouchHowTo() {
        if (lastTouchX > screenW - tile * 2 - 20 && lastTouchY < screenH * 0.12f) {
            state = S_MAIN; return;
        }
    }

    void onTouchSettings() {
        if (lastTouchX > screenW - tile * 2 - 20 && lastTouchY < screenH * 0.12f) {
            state = S_MAIN; return;
        }
        float cx = screenW * 0.5f;
        float w = Math.min(screenW * 0.6f, 900);
        float h = screenH * 0.1f;
        for (int i = 0; i < 3; i++) {
            float y = screenH * 0.22f + i * (h + 24);
            if (lastTouchX > cx - w / 2 && lastTouchX < cx + w / 2
                    && lastTouchY > y && lastTouchY < y + h) {
                if (i == 0) sfxOn = !sfxOn;
                else if (i == 1) musicOn = !musicOn;
                else if (i == 2) autoStart = !autoStart;
                return;
            }
        }
    }

    void onTouchRecords() {
        if (lastTouchX > screenW - tile * 2 - 20 && lastTouchY < screenH * 0.12f) {
            state = S_MAIN; return;
        }
    }

    void onTouchGame() {
        if (gameOver) {
            float by = screenH / 2f + tile * 0.8f;
            float h = tile * 0.9f;
            if (lastTouchY > by && lastTouchY < by + h) {
                if (lastTouchX > screenW / 2f - tile * 2.7f && lastTouchX < screenW / 2f - tile * 0.3f) {
                    loadMap(currentMap);
                    return;
                } else if (lastTouchX > screenW / 2f + tile * 0.3f && lastTouchX < screenW / 2f + tile * 2.7f) {
                    state = S_MAIN;
                    return;
                }
            }
            return;
        }

        // HUD top buttons
        if (lastTouchY < hudH) {
            float bw = tile * 1.15f;
            float bh = hudH * 0.6f;
            float by = hudH * 0.2f;
            if (lastTouchY >= by && lastTouchY <= by + bh) {
                float bx = screenW - bw - 10;
                if (lastTouchX > bx) {
                    speedMult = speedMult == 1f ? 2f : (speedMult == 2f ? 3f : 1f);
                    return;
                }
                bx -= bw + 10;
                if (lastTouchX > bx && lastTouchX < bx + bw) { paused = !paused; return; }
                bx -= bw + 10;
                if (lastTouchX > bx && lastTouchX < bx + bw) { if (!waveActive) startWave(); return; }
                bx -= bw + 10;
                if (lastTouchX > bx && lastTouchX < bx + bw) {
                    state = S_MAIN;
                    selectedTower = null;
                    placingTowerType = -1;
                    return;
                }
            }
            return;
        }

        // Right panel — always Towers/Send tabs
        if (lastTouchX > panelX) {
            float tabH = hudH * 0.6f;
            float tabY = hudH + 8;
            float tabW = (panelW - 24) / 2f;
            if (lastTouchY > tabY && lastTouchY < tabY + tabH) {
                if (lastTouchX > panelX + 8 && lastTouchX < panelX + 8 + tabW) {
                    rightTab = 0;
                    return;
                }
                if (lastTouchX > panelX + 16 + tabW && lastTouchX < panelX + 16 + tabW * 2) {
                    rightTab = 1;
                    return;
                }
            }
            float bodyY = tabY + tabH + 8;
            if (rightTab == 0) {
                float pad = 8;
                int cols = 2;
                float cw = (panelW - pad * 3) / cols;
                float availH = screenH - bodyY - 10;
                int rows = 6;
                float ch = (availH - pad * (rows + 1)) / rows;
                for (int rr = 0; rr < rows; rr++) {
                    for (int cc = 0; cc < cols; cc++) {
                        int type = rr * cols + cc;
                        if (type >= Tower.TYPE_COUNT) continue;
                        float x = panelX + pad + cc * (cw + pad);
                        float y = bodyY + pad + rr * (ch + pad);
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
            } else {
                float yy = bodyY + 100;
                float pad = 8;
                float availH = screenH - yy - 12;
                int rows = SEND_BLOON.length;
                float rh = (availH - pad * (rows + 1)) / rows;
                for (int i = 0; i < rows; i++) {
                    float y = yy + pad + i * (rh + pad);
                    if (lastTouchY > y && lastTouchY < y + rh
                            && lastTouchX > panelX + pad && lastTouchX < panelX + panelW - pad) {
                        sendBloons(i);
                        return;
                    }
                }
            }
            return;
        }

        // Bottom upgrade panel (when a tower is selected)
        if (selectedTower != null && lastTouchY > upY) {
            float pad = 12;
            float infoW = upW * 0.22f;
            float bh = upH * 0.16f;
            float by = screenH - bh - pad;
            // sell
            if (lastTouchY > by && lastTouchY < by + bh) {
                if (lastTouchX > upX + pad && lastTouchX < upX + pad + infoW * 0.55f) {
                    sellSelected();
                    return;
                }
                if (lastTouchX > upX + pad + infoW * 0.58f && lastTouchX < upX + pad + infoW * 0.98f) {
                    selectedTower = null;
                    return;
                }
            }
            // buy buttons in path panels
            float pathX = upX + infoW + pad;
            float pathTotalW = upW - infoW - pad * 2;
            float pathW = (pathTotalW - pad) / 2f;
            float pathY = upY + pad;
            float pathH = upH - pad * 2;
            for (int p2 = 0; p2 < 2; p2++) {
                float px = pathX + p2 * (pathW + pad);
                UpgradeDef u = selectedTower.nextUpgrade(p2);
                if (u == null) continue;
                float btw = pathW * 0.36f;
                float bth = pathH * 0.28f;
                float btx = px + pathW - btw - 8;
                float bty = pathY + pathH - bth - 8;
                if (lastTouchX > btx && lastTouchX < btx + btw
                        && lastTouchY > bty && lastTouchY < bty + bth) {
                    if (cash >= u.cost) {
                        cash -= u.cost;
                        selectedTower.totalSpent += u.cost;
                        selectedTower.tiers[p2]++;
                    } else {
                        flash("Need $" + u.cost);
                    }
                    return;
                }
            }
            return;
        }

        // Play area
        if (placingTowerType >= 0) {
            int cx = (int) (lastTouchX / tile);
            int cy = (int) ((lastTouchY - hudH) / tile);
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
        towers.remove(selectedTower);
        blocked = new boolean[COLS][ROWS];
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
