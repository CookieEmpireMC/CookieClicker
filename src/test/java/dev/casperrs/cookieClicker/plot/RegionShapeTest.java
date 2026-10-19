package dev.casperrs.cookieClicker.plot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RegionShapeTest {

    /** Right triangle in x/z: (0,0), (4,0), (0,4); contains x + z <= 4 (a stand-in for WorldGuard's polygon test). */
    private static RegionShape triangle() {
        return new RegionShape("tri", true, 0, 5, 0, 4, 7, 4, List.of(new int[] {0, 0}, new int[] {4, 0}, new int[] {0, 4}),
                (x, y, z) -> x >= 0 && z >= 0 && x + z <= 4 && y >= 5 && y <= 7);
    }

    private static Set<String> asSet(long[] packed) {
        Set<String> out = new HashSet<>();
        for (long p : packed) {
            out.add(RegionShape.unpackX(p) + "," + RegionShape.unpackY(p) + "," + RegionShape.unpackZ(p));
        }
        return out;
    }

    @Test
    void cuboidBlocksAreTheWholeBoxRegardlessOfCornerOrder() {
        RegionShape r = RegionShape.cuboid("c", 3, 12, 5, 1, 10, 4);
        assertEquals(1, r.minX());
        assertEquals(3, r.maxX());
        assertEquals(10, r.minY());
        assertEquals(12, r.maxY());
        assertEquals(4, r.minZ());
        assertEquals(5, r.maxZ());
        assertEquals(3 * 3 * 2, r.boxVolume());
        assertEquals(18, r.blocks().length);
        assertFalse(r.polygon());
        assertEquals(4, r.points().size());
    }

    @Test
    void polygonKeepsOnlyBlocksTheRegionContains() {
        RegionShape r = triangle();
        assertEquals(5L * 5 * 3, r.boxVolume());
        long[] blocks = r.blocks();
        // the triangle has 15 columns (x + z <= 4), 3 layers
        assertEquals(15 * 3, blocks.length);
        Set<String> set = asSet(blocks);
        assertTrue(set.contains("0,5,0"));
        assertTrue(set.contains("4,7,0"));
        assertTrue(set.contains("0,6,4"));
        assertFalse(set.contains("4,5,4"), "box corner outside the triangle");
        assertFalse(set.contains("3,5,3"));
        for (String s : set) {
            String[] p = s.split(",");
            assertTrue(Integer.parseInt(p[0]) + Integer.parseInt(p[2]) <= 4, s);
        }
    }

    @Test
    void blocksAreBottomLayerFirst() {
        long[] blocks = triangle().blocks();
        int lastY = Integer.MIN_VALUE;
        for (long b : blocks) {
            int y = RegionShape.unpackY(b);
            assertTrue(y >= lastY);
            lastY = y;
        }
    }

    @Test
    void regionContainingNothingGivesAnEmptyArray() {
        RegionShape none = new RegionShape("n", true, 0, 0, 0, 3, 3, 3, List.of(), (x, y, z) -> false);
        assertEquals(0, none.blocks().length);
    }

    @Test
    void absurdlyLargeRegionsAreRefusedInsteadOfAllocating() {
        RegionShape huge = RegionShape.cuboid("big", -30_000_000, 0, -30_000_000, 30_000_000, 255, 30_000_000);
        assertTrue(huge.boxVolume() > Integer.MAX_VALUE);
        IllegalStateException e = assertThrows(IllegalStateException.class, huge::blocks);
        assertTrue(e.getMessage().contains("big"));
    }

    @Test
    void packUnpackRoundTripsNegativeAndExtremeCoordinates() {
        int[][] samples = {{0, 0, 0}, {-1, -1, -1}, {29_999_999, 319, 29_999_999}, {-30_000_000, -64, -30_000_000},
                {123, -64, -456}, {-123, 2047, 456}, {-33_554_432, -2048, 33_554_431}};
        for (int[] s : samples) {
            long p = RegionShape.pack(s[0], s[1], s[2]);
            assertEquals(s[0], RegionShape.unpackX(p), "x of " + java.util.Arrays.toString(s));
            assertEquals(s[1], RegionShape.unpackY(p), "y of " + java.util.Arrays.toString(s));
            assertEquals(s[2], RegionShape.unpackZ(p), "z of " + java.util.Arrays.toString(s));
        }
    }

    @Test
    void packIsInjectiveOverASmallNeighbourhood() {
        Set<Long> seen = new HashSet<>();
        List<Long> all = new ArrayList<>();
        for (int x = -3; x <= 3; x++) {
            for (int y = -3; y <= 3; y++) {
                for (int z = -3; z <= 3; z++) {
                    all.add(RegionShape.pack(x, y, z));
                    seen.add(RegionShape.pack(x, y, z));
                }
            }
        }
        assertEquals(all.size(), seen.size());
    }
}
