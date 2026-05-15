package com.bloon.td;

public class MapDef {
    public static final int W_FOREST = 0;
    public static final int W_DESERT = 1;
    public static final int W_SNOW = 2;
    public static final int W_LAVA = 3;
    public static final String[] WORLD_NAME = {"Forest", "Desert", "Snow", "Volcano"};

    /** Maps use a fixed logical grid of COLS x MAP_ROWS. */
    public static final int MAP_ROWS = 14;

    public final String name;
    public final int world;
    public final int difficulty; // 1..5
    public final float[][] waypoints; // {col, row}

    public MapDef(String name, int world, int difficulty, float[][] wps) {
        this.name = name;
        this.world = world;
        this.difficulty = difficulty;
        this.waypoints = wps;
    }

    public static final MapDef[] ALL = new MapDef[]{
            new MapDef("Meadow", W_FOREST, 1, new float[][]{
                    {1, -1}, {1, 3}, {7, 3}, {7, 6}, {2, 6}, {2, 9}, {6, 9}, {6, 12}, {1, 12}, {1, 14}
            }),
            new MapDef("Riverside", W_FOREST, 2, new float[][]{
                    {-1, 2}, {3, 2}, {3, 5}, {6, 5}, {6, 2}, {8, 2}, {8, 8}, {1, 8}, {1, 11}, {8, 11}, {8, 14}
            }),
            new MapDef("Dunes", W_DESERT, 2, new float[][]{
                    {4, -1}, {4, 3}, {1, 3}, {1, 7}, {7, 7}, {7, 10}, {3, 10}, {3, 14}
            }),
            new MapDef("Oasis", W_DESERT, 3, new float[][]{
                    {-1, 4}, {2, 4}, {2, 1}, {6, 1}, {6, 5}, {1, 5}, {1, 9}, {7, 9}, {7, 12}, {2, 12}, {2, 14}
            }),
            new MapDef("Frostpeak", W_SNOW, 3, new float[][]{
                    {1, -1}, {1, 3}, {4, 3}, {4, 1}, {7, 1}, {7, 7}, {2, 7}, {2, 10}, {7, 10}, {7, 14}
            }),
            new MapDef("Glacier", W_SNOW, 4, new float[][]{
                    {-1, 2}, {3, 2}, {3, 1}, {7, 1}, {7, 5}, {2, 5}, {2, 8}, {6, 8}, {6, 11}, {1, 11}, {1, 13}, {8, 13}, {8, 14}
            }),
            new MapDef("Crater", W_LAVA, 4, new float[][]{
                    {4, -1}, {4, 2}, {1, 2}, {1, 5}, {7, 5}, {7, 8}, {3, 8}, {3, 11}, {7, 11}, {7, 13}, {1, 13}, {1, 14}
            }),
            new MapDef("Magmaflow", W_LAVA, 5, new float[][]{
                    {-1, 1}, {7, 1}, {7, 3}, {1, 3}, {1, 5}, {7, 5}, {7, 7}, {1, 7}, {1, 9}, {7, 9}, {7, 11}, {1, 11}, {1, 14}
            }),
    };
}
