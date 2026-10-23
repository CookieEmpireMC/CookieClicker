package dev.casperrs.cookieClicker.house.design;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The fence maths on the real 31 x 30 factory plot ring and the real fence.yml. */
class FenceLayoutTest {

    private static FenceStyle style;

    @BeforeAll
    static void load() throws Exception {
        style = FenceStyle.parse(FenceStyleTest.fixture()).style();
        assertNotNull(style);
    }

    private static final int W = 31;
    private static final int D = 30;
    private static final int GROUND = 10;

    private static FenceLayout.Plan plan(FenceLayout.Ground ground, FenceLayout.Volume house) {
        return FenceLayout.plan(0, 0, W - 1, D - 1, (x, z) -> x >= 0 && x < W && z >= 0 && z < D, house, ground, style,
                15, 0);
    }

    private static FenceLayout.Plan flat() {
        return plan((x, z) -> GROUND, (x, y, z) -> false);
    }

    private static List<FenceLayout.Piece> at(FenceLayout.Plan p, int x, int z) {
        return p.pieces().stream().filter(q -> q.x() == x && q.z() == z).toList();
    }

    private static boolean onRing(FenceLayout.Piece p) {
        return p.x() == 0 || p.z() == 0 || p.x() == W - 1 || p.z() == D - 1;
    }

    @Test
    void everyPieceIsOnTheRingAndRingColumnsAreAllCovered() {
        FenceLayout.Plan p = flat();
        assertTrue(p.pieces().stream().allMatch(FenceLayoutTest::onRing));
        Set<Long> columns = p.pieces().stream().map(q -> FenceLayout.key(q.x(), q.z())).collect(Collectors.toSet());
        assertEquals(2 * (W + D) - 4, columns.size(), "every ring column has a block (no gap in the fence)");
        assertEquals(p.pieces().size(), new HashSet<>(p.pieces().stream()
                .map(q -> List.of(q.x(), q.y(), q.z())).toList()).size(), "no block is planned twice");
    }

    @Test
    void gateIsCentredOnTheEntranceWithPillarsOnBothSides() {
        FenceLayout.Plan p = flat();
        assertArrayEquals(new int[] {15, GROUND, 0}, p.entrance());
        for (int x = 14; x <= 16; x++) {
            List<FenceLayout.Piece> col = at(p, x, 0);
            assertEquals(5, col.size(), "arch height at x " + x);
            assertTrue(col.stream().allMatch(q -> q.role() == FenceLayout.Role.ARCH));
            assertEquals("minecraft:dark_oak_planks", col.stream().filter(q -> q.level() == 3).findFirst().orElseThrow()
                    .blockData(style));
        }
        // seen from outside (north, looking south) east is on the left
        assertTrue(at(p, 17, 0).stream().allMatch(q -> q.role() == FenceLayout.Role.GATE_LEFT));
        assertTrue(at(p, 13, 0).stream().allMatch(q -> q.role() == FenceLayout.Role.GATE_RIGHT));
        assertEquals(5, at(p, 17, 0).size());
        assertEquals(5, at(p, 13, 0).size());
    }

    @Test
    void nothingLandsInsideTheHouseBox() {
        FenceLayout.Plan p = plan((x, z) -> GROUND, (x, y, z) -> x <= 6 && y <= GROUND + 1);
        assertTrue(p.pieces().stream().noneMatch(q -> q.x() <= 6 && q.y() <= GROUND + 1));
        assertEquals(List.of(FenceLayout.Role.TOPPER), at(p, 0, 0).stream().map(FenceLayout.Piece::role).toList(),
                "only the part of the corner column above the box is built");
    }

    @Test
    void lShapedAreaIsFencedAroundItsOutline() {
        FenceLayout.Area l = (x, z) -> x >= 0 && x < 12 && z >= 0 && z < 12 && !(x >= 6 && z < 6);
        FenceLayout.Plan p = FenceLayout.plan(0, 0, 11, 11, l, (x, y, z) -> false, (x, z) -> 0, style, 0, 11);
        assertNotNull(p.entrance());
        assertTrue(p.pieces().stream().allMatch(q -> l.contains(q.x(), q.z())), "the fence is inside the area");
        assertTrue(at(p, 3, 3).isEmpty(), "interior stays free");
    }
}
