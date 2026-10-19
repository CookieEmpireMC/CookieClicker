package dev.casperrs.cookieClicker.house;

import dev.casperrs.cookieClicker.plot.RegionShape;

import java.util.function.LongPredicate;

/**
 * The house region's bounding box in block coordinates (inclusive), with the geometry the cinematic needs: "is this
 * click near the house", "does this chunk hold part of it", "does a player's body overlap a hidden block". Pure.
 */
public record HouseBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

    /** Half the width of a player's hitbox, plus a hair so a player pressed against a wall counts as touching it. */
    public static final double PLAYER_HALF_WIDTH = 0.31;
    public static final double PLAYER_HEIGHT = 1.8;

    public static HouseBox of(RegionShape s) {
        return new HouseBox(s.minX(), s.minY(), s.minZ(), s.maxX(), s.maxY(), s.maxZ());
    }

    /** Whether block {@code x y z} lies within {@code radius} blocks of the box (0 = inside it). */
    public boolean near(int x, int y, int z, int radius) {
        return x >= minX - radius && x <= maxX + radius && y >= minY - radius && y <= maxY + radius
                && z >= minZ - radius && z <= maxZ + radius;
    }

    /** Whether chunk {@code cx cz} contains a column of the box. */
    public boolean overlapsChunk(int cx, int cz) {
        return cx >= minX >> 4 && cx <= maxX >> 4 && cz >= minZ >> 4 && cz <= maxZ >> 4;
    }

    /**
     * Whether a player standing with their feet at {@code x y z} would overlap a block for which {@code blocked} is
     * true (packed with {@link RegionShape#pack}). Checks only the up to 2 x 3 x 2 cells the hitbox touches.
     */
    public static boolean bodyOverlaps(double x, double y, double z, LongPredicate blocked) {
        int x0 = floor(x - PLAYER_HALF_WIDTH);
        int x1 = floor(x + PLAYER_HALF_WIDTH);
        int y0 = floor(y);
        int y1 = floor(y + PLAYER_HEIGHT);
        int z0 = floor(z - PLAYER_HALF_WIDTH);
        int z1 = floor(z + PLAYER_HALF_WIDTH);
        for (int bx = x0; bx <= x1; bx++) {
            for (int by = y0; by <= y1; by++) {
                for (int bz = z0; bz <= z1; bz++) {
                    if (blocked.test(RegionShape.pack(bx, by, bz))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** X of the middle of the box (block centres). */
    public double centreX() {
        return (minX + maxX + 1) / 2.0;
    }

    public double centreZ() {
        return (minZ + maxZ + 1) / 2.0;
    }

    private static int floor(double v) {
        return (int) Math.floor(v);
    }
}
