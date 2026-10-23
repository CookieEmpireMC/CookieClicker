package dev.casperrs.cookieClicker.plot;

import java.util.List;

/**
 * A snapshot of a region's shape in block coordinates (inclusive min/max). Pure, so outline and bounding-box maths
 * are testable without WorldGuard. points are the ordered {@code [x, z]} outline vertices (a cuboid's 4 corners);
 * contains tests polygons against the real region, not the box.
 */
public record RegionShape(String id, boolean polygon, int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                          List<int[]> points, BlockTest contains) {

    @FunctionalInterface
    public interface BlockTest {
        boolean contains(int x, int y, int z);
    }

    /** Cuboid from two corners (any order). */
    public static RegionShape cuboid(String id, int x1, int y1, int z1, int x2, int y2, int z2) {
        int minX = Math.min(x1, x2);
        int minY = Math.min(y1, y2);
        int minZ = Math.min(z1, z2);
        int maxX = Math.max(x1, x2);
        int maxY = Math.max(y1, y2);
        int maxZ = Math.max(z1, z2);
        List<int[]> corners = List.of(new int[] {minX, minZ}, new int[] {maxX, minZ}, new int[] {maxX, maxZ},
                new int[] {minX, maxZ});
        return new RegionShape(id, false, minX, minY, minZ, maxX, maxY, maxZ, corners,
                (x, y, z) -> x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ);
    }

    /** Blocks in the bounding box (may overflow int for absurd regions, hence long). */
    public long boxVolume() {
        return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
    }

    /**
     * Every block of the region: the bounding box, keeping only blocks the region {@link #contains} (so polygons are
     * exact). Packed with {@link #pack}. Call once and cache (never per tick).
     */
    public long[] blocks() {
        long volume = boxVolume();
        if (volume > Integer.MAX_VALUE - 8) {
            throw new IllegalStateException("region " + id + " is too large to iterate (" + volume + " blocks)");
        }
        long[] out = new long[(int) volume];
        int n = 0;
        for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    if (contains.contains(x, y, z)) {
                        out[n++] = pack(x, y, z);
                    }
                }
            }
        }
        return n == out.length ? out : java.util.Arrays.copyOf(out, n);
    }

    // Same bit layout as BlockPosition / Paper's block keys: x 26 bits, z 26 bits, y 12 bits (signed).

    public static long pack(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | ((long) y & 0xFFFL);
    }

    public static int unpackX(long packed) {
        return (int) (packed >> 38);
    }

    public static int unpackY(long packed) {
        return (int) (packed << 52 >> 52);
    }

    public static int unpackZ(long packed) {
        return (int) (packed << 26 >> 38);
    }
}
