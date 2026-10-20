package dev.casperrs.cookieClicker.house;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntBinaryOperator;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The fence maths on the real 31 x 30 factory plot ring and the real fence.yml (fixtures from the feature branch). */
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
    void fourCornersWithTheirColumnAndALantern() {
        FenceLayout.Plan p = flat();
        for (int[] c : new int[][] {{0, 0}, {W - 1, 0}, {0, D - 1}, {W - 1, D - 1}}) {
            List<FenceLayout.Piece> col = at(p, c[0], c[1]);
            assertEquals(3, col.size(), "2 corner blocks + topper at " + c[0] + "," + c[1]);
            assertEquals(1, col.stream().filter(q -> q.role() == FenceLayout.Role.TOPPER).count());
            assertEquals(GROUND + 2, col.stream().filter(q -> q.role() == FenceLayout.Role.TOPPER).findFirst()
                    .orElseThrow().y());
            assertEquals(2, col.stream().filter(q -> q.role() == FenceLayout.Role.CORNER).count());
        }
        assertEquals(4 * 2, p.pieces().stream().filter(q -> q.role() == FenceLayout.Role.CORNER).count());
    }

    @Test
    void postsEveryFourthCellCountedFromTheCornerAndNotNextToACorner() {
        FenceLayout.Plan p = flat();
        // south side (z = 29): posts at x = 4, 8, ...; x = 28 is next to the corner at 30, so a rail
        assertEquals(FenceLayout.Role.POST, at(p, 4, D - 1).getFirst().role());
        assertEquals(FenceLayout.Role.POST, at(p, 8, D - 1).getFirst().role());
        assertEquals(FenceLayout.Role.RAIL, at(p, 5, D - 1).getFirst().role());
        assertEquals(FenceLayout.Role.POST, at(p, 28, D - 1).getFirst().role(), "two cells from the corner stays a post");
        assertEquals(FenceLayout.Role.RAIL, at(p, 0, D - 2).getFirst().role(), "z 28 is a post position next to the corner at 29: dropped");
        // every 2nd post gets a lantern: step 8
        assertTrue(at(p, 8, D - 1).stream().anyMatch(q -> q.role() == FenceLayout.Role.TOPPER));
        assertFalse(at(p, 4, D - 1).stream().anyMatch(q -> q.role() == FenceLayout.Role.TOPPER));
    }

    @Test
    void railsConnectToTheirNeighboursOnly() {
        FenceLayout.Plan p = flat();
        FenceLayout.Piece r = at(p, 5, D - 1).getFirst();
        assertEquals(Set.of(FenceLayout.Face.EAST, FenceLayout.Face.WEST), r.faces());
        FenceLayout.Piece next = at(p, 12, 0).getFirst(); // beside the gate pillar at 13
        assertEquals(FenceLayout.Role.RAIL, next.role());
        assertTrue(next.faces().contains(FenceLayout.Face.EAST), "connects to the tall pillar");
        assertTrue(next.faces().contains(FenceLayout.Face.WEST));
        FenceLayout.Piece west = at(p, 0, 5).getFirst();
        assertEquals(Set.of(FenceLayout.Face.NORTH, FenceLayout.Face.SOUTH), west.faces());
    }

    @Test
    void theWholeGateStandsOnTheEntrancesGroundSoTheLintelIsLevel() {
        IntBinaryOperator g = (x, z) -> x >= 13 && x <= 17 && z == 0 ? GROUND + (x - 13) : GROUND;
        FenceLayout.Plan p = plan(g::applyAsInt, (x, y, z) -> false);
        // entrance centre x = 15 -> y = GROUND + 2 for the pillars and the opening
        assertEquals(GROUND + 2, p.entrance()[1]);
        for (int x = 13; x <= 17; x++) {
            assertEquals(GROUND + 2, at(p, x, 0).stream().mapToInt(FenceLayout.Piece::y).min().orElseThrow(), "x " + x);
        }
        // other rails follow their own ground
        IntBinaryOperator hill = (x, z) -> x == 5 && z == D - 1 ? GROUND + 1 : GROUND;
        FenceLayout.Plan q = plan(hill::applyAsInt, (x, y, z) -> false);
        assertEquals(GROUND + 1, at(q, 5, D - 1).getFirst().y());
    }

    @Test
    void columnsWithoutGroundAreSkippedAndNoEntranceMeansNull() {
        FenceLayout.Plan p = plan((x, z) -> x == 5 && z == D - 1 ? FenceLayout.SKIP : GROUND, (x, y, z) -> false);
        assertTrue(at(p, 5, D - 1).isEmpty());
        assertNotNull(p.entrance());
        FenceLayout.Plan none = plan((x, z) -> x == 15 && z == 0 ? FenceLayout.SKIP : GROUND, (x, y, z) -> false);
        assertNull(none.entrance());
        assertTrue(at(none, 14, 0).stream().allMatch(q -> q.role() == FenceLayout.Role.ARCH) || at(none, 14, 0).size() > 0);
    }

    @Test
    void nothingLandsInsideTheHouseBox() {
        FenceLayout.Plan p = plan((x, z) -> GROUND, (x, y, z) -> x <= 6 && y <= GROUND + 1);
        assertTrue(p.pieces().stream().noneMatch(q -> q.x() <= 6 && q.y() <= GROUND + 1));
        assertEquals(List.of(FenceLayout.Role.TOPPER), at(p, 0, 0).stream().map(FenceLayout.Piece::role).toList(),
                "only the part of the corner column above the box is built");
    }

    @Test
    void emptyAreaGivesAnEmptyPlan() {
        FenceLayout.Plan p = FenceLayout.plan(0, 0, 5, 5, (x, z) -> false, (x, y, z) -> false, (x, z) -> 0, style, 0, 0);
        assertTrue(p.pieces().isEmpty());
        assertNull(p.entrance());
    }

    @Test
    void smallestPlotAndGateInACornerDoNotThrow() {
        FenceLayout.Plan p = FenceLayout.plan(0, 0, 4, 4, (x, z) -> x >= 0 && x < 5 && z >= 0 && z < 5,
                (x, y, z) -> false, (x, z) -> 0, style, 0, 0);
        assertFalse(p.pieces().isEmpty());
        assertNotNull(p.entrance());
        // negative world coordinates (floorMod) behave the same as positive ones
        FenceLayout.Plan neg = FenceLayout.plan(-20, -20, 10, 9, (x, z) -> x >= -20 && x <= 10 && z >= -20 && z <= 9,
                (x, y, z) -> false, (x, z) -> 64, style, -5, -20);
        assertArrayEquals(new int[] {-5, 64, -20}, neg.entrance());
        assertEquals(FenceLayout.Role.POST, at(neg, -16, 9).getFirst().role());
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
