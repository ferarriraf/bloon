package com.bloon.td;

public class UpgradeDef {
    // Special abilities granted by an upgrade
    public static final int SP_NONE = 0;
    public static final int SP_LEAD_POP = 1;       // can damage lead bloons with sharp/dart
    public static final int SP_TRIPLE_SHOT = 2;    // dart: shoots three darts
    public static final int SP_LASER = 3;          // super: laser beams instead of darts
    public static final int SP_PLASMA = 4;         // super: plasma orbs with AoE
    public static final int SP_FREEZE = 5;         // ice: slow bloons in radius
    public static final int SP_NINJA_CAMO = 6;     // ninja: extra dmg vs lead/black
    public static final int SP_SLOW_DARTS = 7;     // ninja: darts slow bloons hit
    public static final int SP_BIGGER_BOMBS = 8;   // bomb: huge AoE
    public static final int SP_HOMING = 9;         // wizard: homing magic darts

    public final String name;
    public final int cost;
    public final float rateMult;
    public final float rangeMult;
    public final int dmgAdd;
    public final int pierceAdd;
    public final int special;
    public final float projSpeedMult;

    public UpgradeDef(String name, int cost, float rateMult, float rangeMult,
                      int dmgAdd, int pierceAdd, int special, float projSpeedMult) {
        this.name = name;
        this.cost = cost;
        this.rateMult = rateMult;
        this.rangeMult = rangeMult;
        this.dmgAdd = dmgAdd;
        this.pierceAdd = pierceAdd;
        this.special = special;
        this.projSpeedMult = projSpeedMult;
    }

    /** [towerType][pathIndex(0|1)][tier(0..2)] -> upgrade */
    public static final UpgradeDef[][][] TREE = new UpgradeDef[Tower.TYPE_COUNT][2][3];

    static {
        // ------- Dart Monkey (T_DART) -------
        TREE[Game.T_DART][0][0] = new UpgradeDef("Long Range",   140, 1f,    1.30f, 0, 1, SP_NONE, 1f);
        TREE[Game.T_DART][0][1] = new UpgradeDef("Quick Shots",  260, 1.55f, 1f,    0, 0, SP_NONE, 1.15f);
        TREE[Game.T_DART][0][2] = new UpgradeDef("Triple Shot",  500, 1f,    1f,    0, 1, SP_TRIPLE_SHOT, 1f);

        TREE[Game.T_DART][1][0] = new UpgradeDef("Sharp Darts",  120, 1f,    1f,    1, 1, SP_NONE, 1f);
        TREE[Game.T_DART][1][1] = new UpgradeDef("Razor Sharp",  220, 1f,    1f,    1, 2, SP_LEAD_POP, 1f);
        TREE[Game.T_DART][1][2] = new UpgradeDef("Spike-O-Pult", 460, 0.7f,  1.05f, 4, 6, SP_LEAD_POP, 0.95f);

        // ------- Tack Shooter (T_TACK) -------
        TREE[Game.T_TACK][0][0] = new UpgradeDef("Faster Tacks", 180, 1.40f, 1f,    0, 0, SP_NONE, 1f);
        TREE[Game.T_TACK][0][1] = new UpgradeDef("Even Faster",  320, 1.55f, 1f,    0, 0, SP_NONE, 1.10f);
        TREE[Game.T_TACK][0][2] = new UpgradeDef("Hot Shots",    540, 1f,    1.10f, 2, 1, SP_LEAD_POP, 1f);

        TREE[Game.T_TACK][1][0] = new UpgradeDef("More Tacks",   160, 1f,    1f,    0, 1, SP_NONE, 1f);
        TREE[Game.T_TACK][1][1] = new UpgradeDef("Tack Spray",   300, 1f,    1.15f, 0, 1, SP_NONE, 1f);
        TREE[Game.T_TACK][1][2] = new UpgradeDef("Ring of Fire", 560, 1.10f, 1.20f, 1, 2, SP_LEAD_POP, 1f);

        // ------- Bomb Tower (T_BOMB) -------
        TREE[Game.T_BOMB][0][0] = new UpgradeDef("Bigger Bombs", 240, 1f,    1.10f, 1, 0, SP_NONE, 1f);
        TREE[Game.T_BOMB][0][1] = new UpgradeDef("Heavy Bombs",  450, 1f,    1f,    2, 0, SP_NONE, 1f);
        TREE[Game.T_BOMB][0][2] = new UpgradeDef("Carpet Bomb",  900, 1f,    1.10f, 3, 6, SP_BIGGER_BOMBS, 1f);

        TREE[Game.T_BOMB][1][0] = new UpgradeDef("Faster Reload",220, 1.40f, 1f,    0, 0, SP_NONE, 1.10f);
        TREE[Game.T_BOMB][1][1] = new UpgradeDef("Missile Launch",420,1.30f, 1.20f, 0, 0, SP_NONE, 1.25f);
        TREE[Game.T_BOMB][1][2] = new UpgradeDef("MOAB Mauler",  820, 1f,    1f,    4, 4, SP_NONE, 1f);

        // ------- Sniper Monkey (T_SNIPER) -------
        TREE[Game.T_SNIPER][0][0] = new UpgradeDef("Full Metal",  220, 1f,    1f,    2, 0, SP_NONE, 1f);
        TREE[Game.T_SNIPER][0][1] = new UpgradeDef("Large Caliber",420,1f,    1f,    3, 1, SP_LEAD_POP, 1f);
        TREE[Game.T_SNIPER][0][2] = new UpgradeDef("Cripple MOAB", 950,0.85f, 1f,    8, 2, SP_NONE, 1f);

        TREE[Game.T_SNIPER][1][0] = new UpgradeDef("Quick Shot",  240, 1.40f, 1f,    0, 0, SP_NONE, 1f);
        TREE[Game.T_SNIPER][1][1] = new UpgradeDef("Faster Firing",420,1.55f,1f,    0, 0, SP_NONE, 1f);
        TREE[Game.T_SNIPER][1][2] = new UpgradeDef("Bouncing Bullet",820,1f,1f,     2, 4, SP_LEAD_POP, 1f);

        // ------- Ninja Monkey (T_NINJA) -------
        TREE[Game.T_NINJA][0][0] = new UpgradeDef("Sharp Shurikens",240,1f,1f,      1, 1, SP_NONE, 1f);
        TREE[Game.T_NINJA][0][1] = new UpgradeDef("Double Shuriken",400,1f,1f,      0, 2, SP_NONE, 1.10f);
        TREE[Game.T_NINJA][0][2] = new UpgradeDef("Bloonjitsu",     780,1.20f,1f,   1, 3, SP_NINJA_CAMO, 1.10f);

        TREE[Game.T_NINJA][1][0] = new UpgradeDef("Distraction",    220,1f,1f,      0, 0, SP_SLOW_DARTS, 1f);
        TREE[Game.T_NINJA][1][1] = new UpgradeDef("Sticky Bombs",   380,1f,1.05f,   0, 1, SP_SLOW_DARTS, 1f);
        TREE[Game.T_NINJA][1][2] = new UpgradeDef("Caltrops",       720,1.15f,1.15f,1, 2, SP_SLOW_DARTS, 1f);

        // ------- Ice Tower (T_ICE) -------
        TREE[Game.T_ICE][0][0] = new UpgradeDef("Cold Snap",      200, 1.20f,1.10f, 0, 0, SP_FREEZE, 1f);
        TREE[Game.T_ICE][0][1] = new UpgradeDef("Permafrost",     380, 1.20f,1.10f, 0, 0, SP_FREEZE, 1f);
        TREE[Game.T_ICE][0][2] = new UpgradeDef("Arctic Wind",    720, 1.10f,1.30f, 0, 0, SP_FREEZE, 1f);

        TREE[Game.T_ICE][1][0] = new UpgradeDef("Deep Freeze",    220, 1f,   1.05f, 1, 0, SP_FREEZE, 1f);
        TREE[Game.T_ICE][1][1] = new UpgradeDef("Snowstorm",      420, 1f,   1.15f, 1, 1, SP_FREEZE, 1f);
        TREE[Game.T_ICE][1][2] = new UpgradeDef("Absolute Zero",  820, 1.10f,1.20f, 3, 2, SP_FREEZE, 1f);

        // ------- Super Monkey (T_SUPER) -------
        TREE[Game.T_SUPER][0][0] = new UpgradeDef("Laser Vision",  600, 1.10f,1.10f, 1, 1, SP_LASER, 1f);
        TREE[Game.T_SUPER][0][1] = new UpgradeDef("Plasma Vision", 950, 1.20f,1.15f, 2, 2, SP_PLASMA, 1f);
        TREE[Game.T_SUPER][0][2] = new UpgradeDef("Sun God",      1800, 1.30f,1.25f, 4, 4, SP_PLASMA, 1.20f);

        TREE[Game.T_SUPER][1][0] = new UpgradeDef("Epic Range",    500, 1f,   1.40f, 0, 1, SP_NONE, 1f);
        TREE[Game.T_SUPER][1][1] = new UpgradeDef("Robo Monkey",   900, 1.50f,1f,    1, 1, SP_NONE, 1.10f);
        TREE[Game.T_SUPER][1][2] = new UpgradeDef("Tech Terror",  1700, 1.60f,1.15f, 2, 3, SP_HOMING, 1.20f);

        // ------- Wizard (T_WIZARD) -------
        TREE[Game.T_WIZARD][0][0] = new UpgradeDef("Arcane Blast", 240, 1.20f,1.10f, 1, 1, SP_LEAD_POP, 1f);
        TREE[Game.T_WIZARD][0][1] = new UpgradeDef("Fireball",     420, 1f,   1f,    2, 3, SP_NONE, 1f);
        TREE[Game.T_WIZARD][0][2] = new UpgradeDef("Wall of Fire", 780, 1.20f,1.10f, 2, 3, SP_BIGGER_BOMBS, 1f);

        TREE[Game.T_WIZARD][1][0] = new UpgradeDef("Homing Bolts", 240, 1f,   1f,    0, 1, SP_HOMING, 1f);
        TREE[Game.T_WIZARD][1][1] = new UpgradeDef("Lightning",    480, 1.10f,1.10f, 1, 2, SP_HOMING, 1f);
        TREE[Game.T_WIZARD][1][2] = new UpgradeDef("Tempest Tornado",880,1.20f,1.20f,2, 4, SP_HOMING, 1f);

        // ------- Glue Gunner (T_GLUE) -------
        TREE[Game.T_GLUE][0][0] = new UpgradeDef("Stickier Glue",  200, 1.0f, 1.0f,  0, 0, SP_SLOW_DARTS, 1f);
        TREE[Game.T_GLUE][0][1] = new UpgradeDef("Glue Soak",      360, 1.10f,1.05f, 1, 0, SP_SLOW_DARTS, 1f);
        TREE[Game.T_GLUE][0][2] = new UpgradeDef("Corrosive Glue", 720, 1.10f,1.05f, 2, 1, SP_SLOW_DARTS, 1f);

        TREE[Game.T_GLUE][1][0] = new UpgradeDef("Larger Range",   180, 1.0f, 1.25f, 0, 1, SP_NONE, 1f);
        TREE[Game.T_GLUE][1][1] = new UpgradeDef("Glue Splatter",  340, 1.0f, 1.15f, 0, 3, SP_NONE, 1f);
        TREE[Game.T_GLUE][1][2] = new UpgradeDef("Glue Storm",     680, 1.20f,1.20f, 1, 4, SP_LEAD_POP, 1f);

        // ------- Boomerang (T_BOOM) -------
        TREE[Game.T_BOOM][0][0] = new UpgradeDef("Faster Throws",  220, 1.30f,1.0f,  0, 1, SP_NONE, 1f);
        TREE[Game.T_BOOM][0][1] = new UpgradeDef("Glaives",        420, 1.0f, 1.05f, 1, 2, SP_LEAD_POP, 1f);
        TREE[Game.T_BOOM][0][2] = new UpgradeDef("Glaive Lord",    820, 1.20f,1.10f, 2, 4, SP_LEAD_POP, 1.10f);

        TREE[Game.T_BOOM][1][0] = new UpgradeDef("Long Reach",     200, 1.0f, 1.30f, 0, 1, SP_NONE, 1f);
        TREE[Game.T_BOOM][1][1] = new UpgradeDef("Sonic Boomerang",400, 1.10f,1.10f, 1, 2, SP_NONE, 1.20f);
        TREE[Game.T_BOOM][1][2] = new UpgradeDef("Turbo Charge",   780, 1.25f,1.15f, 1, 3, SP_LEAD_POP, 1.20f);

        // ------- Mortar (T_MORTAR) -------
        TREE[Game.T_MORTAR][0][0] = new UpgradeDef("Bigger Shells", 320, 1.0f, 1.0f, 1, 0, SP_NONE, 1f);
        TREE[Game.T_MORTAR][0][1] = new UpgradeDef("Heavy Shells",  600, 1.0f, 1.0f, 2, 2, SP_NONE, 1f);
        TREE[Game.T_MORTAR][0][2] = new UpgradeDef("Pop & Awe",    1100, 1.0f, 1.05f,3, 5, SP_BIGGER_BOMBS, 1f);

        TREE[Game.T_MORTAR][1][0] = new UpgradeDef("Increased Rate",280, 1.40f,1.0f, 0, 0, SP_NONE, 1.10f);
        TREE[Game.T_MORTAR][1][1] = new UpgradeDef("Burny Stuff",   520, 1.30f,1.0f, 1, 1, SP_NONE, 1f);
        TREE[Game.T_MORTAR][1][2] = new UpgradeDef("The Big One",  1050, 1.10f,1.0f, 4, 3, SP_BIGGER_BOMBS, 1f);

        // ------- Banana Farm (T_FARM) -------
        TREE[Game.T_FARM][0][0] = new UpgradeDef("More Bananas",   300, 1.40f,1.0f,  0, 0, SP_NONE, 1f);
        TREE[Game.T_FARM][0][1] = new UpgradeDef("Greater Yield",  600, 1.30f,1.0f,  0, 0, SP_NONE, 1f);
        TREE[Game.T_FARM][0][2] = new UpgradeDef("Banana Plantation",1200,1.25f,1.0f,0, 0, SP_NONE, 1f);

        TREE[Game.T_FARM][1][0] = new UpgradeDef("Long Life Banana",250, 1.20f,1.0f, 0, 0, SP_NONE, 1f);
        TREE[Game.T_FARM][1][1] = new UpgradeDef("Valuable Bananas",480, 1.20f,1.0f, 0, 0, SP_NONE, 1f);
        TREE[Game.T_FARM][1][2] = new UpgradeDef("Banana Republic",1000, 1.30f,1.0f, 0, 0, SP_NONE, 1f);
    }
}
