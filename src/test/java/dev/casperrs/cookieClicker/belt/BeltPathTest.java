package dev.casperrs.cookieClicker.belt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.casperrs.cookieClicker.plot.Plot;
import java.util.List;
import org.junit.jupiter.api.Test;

class BeltPathTest {

    /** The cookie's own transformation, applied to the model's corners: min y of the bottom, and the face normal. */

    @Test
    void cellsRunInOrderFromTheFirstToTheLastInclusive() {
        List<Plot.Cell> c = BeltPath.cells(new int[] {18, 0, 11}, new int[] {18, 0, 7});
        assertNotNull(c);
        assertEquals(5, c.size());
        assertEquals(new Plot.Cell(18, 0, 11), c.getFirst());
        assertEquals(new Plot.Cell(18, 0, 9), c.get(2));
        assertEquals(new Plot.Cell(18, 0, 7), c.getLast());
        List<Plot.Cell> east = BeltPath.cells(new int[] {2, 5, 4}, new int[] {5, 5, 4});
        assertEquals(List.of(new Plot.Cell(2, 5, 4), new Plot.Cell(3, 5, 4), new Plot.Cell(4, 5, 4),
                new Plot.Cell(5, 5, 4)), east);
    }

    @Test
    void notStraightOrNotLevelIsNull() {
        assertNull(BeltPath.cells(new int[] {0, 0, 0}, new int[] {3, 0, 3}));
        assertNull(BeltPath.cells(new int[] {0, 0, 0}, new int[] {0, 1, 4}));
    }

    @Test
    void ofTheFactoryBeltRunsNorthFromTheHatchToTheEdgeBeforeThePedestal() {
        Plot.Belt belt = new Plot.Belt(BeltPath.cells(new int[] {18, 64, 11}, new int[] {18, 64, 7}), 1.0);
        BeltPath p = BeltPath.of(belt, new Plot.Pedestal(18.5, 64, 6.5, 180f));
        assertNotNull(p);
        assertEquals(0, p.dirX());
        assertEquals(-1, p.dirZ());
        assertEquals(5, p.length());
        assertEquals(18.5, p.startX(), 1e-12);
        assertEquals(12.0, p.startZ(), 1e-12);
        assertEquals(65.0, p.y(), 1e-12);
        assertEquals(7.0, p.end()[2], 1e-12);
        assertEquals(11.5, p.cellCentre(0)[2], 1e-12);
        assertEquals(7.5, p.cellCentre(4)[2], 1e-12);
        assertEquals(9.5, p.middle()[2], 1e-12);
        assertEquals(Math.PI, Math.abs(p.travelAngle()), 1e-12);
        assertEquals(40, p.rideTicks(2.5));
    }

    @Test
    void brokenStoredBeltsGiveNoPath() {
        assertNull(BeltPath.of(new Plot.Belt(List.of(new Plot.Cell(0, 0, 0), new Plot.Cell(1, 0, 1)), 1), null));
        assertNull(BeltPath.of(new Plot.Belt(List.of(new Plot.Cell(0, 0, 0), new Plot.Cell(0, 0, 2)), 1), null));
        assertNull(BeltPath.of(new Plot.Belt(List.of(new Plot.Cell(0, 0, 0), new Plot.Cell(0, 1, 1)), 1), null));
    }

}
