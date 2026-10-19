package dev.casperrs.cookieClicker.house;

import dev.casperrs.cookieClicker.plot.RegionShape;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RevealOrderTest {

    private static long[] box(int x0, int y0, int z0, int x1, int y1, int z1) {
        List<Long> out = new ArrayList<>();
        for (int y = y0; y <= y1; y++) {
            for (int z = z0; z <= z1; z++) {
                for (int x = x0; x <= x1; x++) {
                    out.add(RegionShape.pack(x, y, z));
                }
            }
        }
        return out.stream().mapToLong(Long::longValue).toArray();
    }

    private static long[] shuffled(long[] in, long seed) {
        List<Long> l = new ArrayList<>();
        for (long v : in) {
            l.add(v);
        }
        Collections.shuffle(l, new Random(seed));
        return l.stream().mapToLong(Long::longValue).toArray();
    }

    @Test
    void emptyInputGivesEmptyOrder() {
        RevealOrder o = RevealOrder.of(new long[0]);
        assertEquals(0, o.size());
        assertEquals(0, o.layers());
    }

    @Test
    void layersGoBottomToTopAndStartsAreConsistent() {
        RevealOrder o = RevealOrder.of(shuffled(box(-3, 60, 10, 4, 66, 17), 1));
        assertEquals(7, o.layers());
        assertEquals(8 * 8 * 7, o.size());
        int lastY = Integer.MIN_VALUE;
        for (int layer = 0; layer < o.layers(); layer++) {
            int start = o.layerStarts()[layer];
            int end = o.layerEnd(layer);
            assertEquals(64, end - start);
            int y = RegionShape.unpackY(o.positions()[start]);
            assertTrue(y > lastY, "layers ascend");
            lastY = y;
            for (int i = start; i < end; i++) {
                assertEquals(y, RegionShape.unpackY(o.positions()[i]), "one y per layer");
            }
        }
        assertEquals(0, o.layerStarts()[0]);
        assertEquals(o.size(), o.layerEnd(o.layers() - 1));
    }

    @Test
    void insideALayerTheDistanceFromTheCentreNeverShrinks() {
        RevealOrder o = RevealOrder.of(shuffled(box(0, 5, 0, 8, 5, 6), 7)); // 9 x 7, centre (4, 3)
        double last = -1;
        for (long p : o.positions()) {
            double dx = RegionShape.unpackX(p) - 4.0;
            double dz = RegionShape.unpackZ(p) - 3.0;
            double d = dx * dx + dz * dz;
            assertTrue(d >= last, "outward from the middle");
            last = d;
        }
        long first = o.positions()[0];
        assertEquals(4, RegionShape.unpackX(first));
        assertEquals(3, RegionShape.unpackZ(first));
    }

    @Test
    void evenSizedFootprintHasItsCentreBetweenBlocks() {
        // 4 x 4: the four middle blocks come first (all at the same distance)
        RevealOrder o = RevealOrder.of(box(0, 0, 0, 3, 0, 3));
        Set<Long> firstFour = new HashSet<>();
        for (int i = 0; i < 4; i++) {
            firstFour.add(o.positions()[i]);
        }
        assertEquals(Set.of(RegionShape.pack(1, 0, 1), RegionShape.pack(2, 0, 1), RegionShape.pack(1, 0, 2),
                RegionShape.pack(2, 0, 2)), firstFour);
    }

    @Test
    void theOrderIsADeterministicPermutationWhateverTheInputOrder() {
        long[] base = box(2, 0, 2, 9, 3, 8);
        RevealOrder a = RevealOrder.of(base);
        for (long seed = 0; seed < 5; seed++) {
            RevealOrder b = RevealOrder.of(shuffled(base, seed));
            assertArrayEquals(a.positions(), b.positions());
            assertArrayEquals(a.layerStarts(), b.layerStarts());
        }
        Set<Long> in = new HashSet<>();
        for (long v : base) {
            in.add(v);
        }
        Set<Long> out = new HashSet<>();
        for (long v : a.positions()) {
            out.add(v);
        }
        assertEquals(in, out);
        assertEquals(base.length, a.size());
    }

    @Test
    void sparseLayersAndNegativeCoordinatesWork() {
        // a polygon-ish house: ragged layers, one lonely block on top, negative coords, gap in y
        long[] blocks = {
                RegionShape.pack(-10, 4, -20), RegionShape.pack(-9, 4, -20), RegionShape.pack(-10, 4, -19),
                RegionShape.pack(-10, 9, -20), RegionShape.pack(-8, 6, -18)};
        RevealOrder o = RevealOrder.of(blocks);
        assertEquals(3, o.layers());
        assertEquals(RegionShape.pack(-8, 6, -18), o.positions()[3]);
        assertEquals(RegionShape.pack(-10, 9, -20), o.positions()[4]);
        assertEquals(3, o.layerEnd(0));
        assertEquals(4, o.layerEnd(1));
        assertEquals(5, o.layerEnd(2));
    }

    @Test
    void singleBlock() {
        RevealOrder o = RevealOrder.of(new long[] {RegionShape.pack(1, 2, 3)});
        assertEquals(1, o.layers());
        assertEquals(1, o.layerEnd(0));
    }

    @Test
    void anLShapedPolygonKeepsItsNotchOutOfTheOrderAndTheHiddenSet() {
        // bounding box 6 x 3 x 4 minus a 3 x 3 notch in the corner (an L-shaped "polygon" region)
        RegionShape l = new RegionShape("l", true, 0, 0, 0, 5, 2, 3, List.of(),
                (x, y, z) -> !(x >= 3 && z >= 2));
        long[] blocks = l.blocks();
        assertEquals(6 * 3 * 4 - 3 * 3 * 2, blocks.length);
        RevealOrder o = RevealOrder.of(blocks);
        assertEquals(blocks.length, o.size());
        for (long p : o.positions()) {
            assertTrue(l.contains().contains(RegionShape.unpackX(p), RegionShape.unpackY(p), RegionShape.unpackZ(p)));
        }
        RevealPlan plan = new RevealPlan(o, new RevealPlan.Pace(4, 8, 0, 100, 0));
        assertFalse(plan.isHidden(RegionShape.pack(4, 1, 3)), "a block in the notch is never made fake air");
        assertTrue(plan.isHidden(RegionShape.pack(1, 1, 3)));
    }
}
