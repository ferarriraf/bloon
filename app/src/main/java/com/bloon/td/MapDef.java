package com.bloon.td;

public class MapDef {
    public static final int W_FOREST = 0;
    public static final int W_DESERT = 1;
    public static final int W_SNOW = 2;
    public static final int W_LAVA = 3;
    public static final String[] WORLD_NAME = {"Forest", "Desert", "Snow", "Volcano"};

    /** Landscape grid */
    public static final int COLS = 14;
    public static final int ROWS = 8;

    public final String name;
    public final int world;
    public final int difficulty;
    public final float[][] waypoints; // {col, row}

    public MapDef(String name, int world, int difficulty, float[][] wps) {
        this.name = name;
        this.world = world;
        this.difficulty = difficulty;
        this.waypoints = wps;
    }

    public static final MapDef[] ALL = new MapDef[]{
            new MapDef("Meadow", W_FOREST, 1, new float[][]{
                    {-1, 4}, {4, 4}, {4, 2}, {9, 2}, {9, 5}, {14, 5}
            }),
            new MapDef("Riverside", W_FOREST, 2, new float[][]{
                    {-1, 2}, {4, 2}, {4, 5}, {8, 5}, {8, 2}, {12, 2}, {12, 6}, {14, 6}
            }),
            new MapDef("Dunes", W_DESERT, 2, new float[][]{
                    {-1, 1}, {5, 1}, {5, 5}, {9, 5}, {9, 2}, {14, 2}
            }),
            new MapDef("Oasis", W_DESERT, 3, new float[][]{
                    {-1, 5}, {3, 5}, {3, 1}, {7, 1}, {7, 4}, {11, 4}, {11, 7}, {14, 7}
            }),
            new MapDef("Frostpeak", W_SNOW, 3, new float[][]{
                    {-1, 3}, {4, 3}, {4, 1}, {7, 1}, {7, 6}, {11, 6}, {11, 3}, {14, 3}
            }),
            new MapDef("Glacier", W_SNOW, 4, new float[][]{
                    {-1, 1}, {3, 1}, {3, 4}, {6, 4}, {6, 1}, {10, 1}, {10, 5}, {13, 5}, {13, 2}, {14, 2}
            }),
            new MapDef("Crater", W_LAVA, 4, new float[][]{
                    {-1, 4}, {2, 4}, {2, 1}, {5, 1}, {5, 5}, {8, 5}, {8, 2}, {11, 2}, {11, 6}, {14, 6}
            }),
            new MapDef("Magmaflow", W_LAVA, 5, new float[][]{
                    {-1, 1}, {12, 1}, {12, 3}, {2, 3}, {2, 5}, {12, 5}, {12, 7}, {14, 7}
            }),
    };
}
