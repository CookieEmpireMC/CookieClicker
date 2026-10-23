package dev.casperrs.cookieClicker.house;

import dev.casperrs.cookieClicker.plot.RegionShape;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HouseBoxTest {

    private final HouseBox box = new HouseBox(10, 64, -20, 18, 70, -14);

    @Test
    void chunkOverlapUsesFloorDivisionForNegativeCoordinates() {
        // x 10..18 -> chunks 0..1; z -20..-14 -> chunks -2..-1
        assertTrue(box.overlapsChunk(0, -2));
        assertTrue(box.overlapsChunk(1, -1));
        assertFalse(box.overlapsChunk(2, -1));
        assertFalse(box.overlapsChunk(-1, -1));
        assertFalse(box.overlapsChunk(0, 0));
        assertFalse(box.overlapsChunk(0, -3));
    }

    @Test
    void bodyOverlapsFindsTheBlockAFeetPositionTouches() {
        Set<Long> hidden = Set.of(RegionShape.pack(5, 64, 5));
        // standing in the middle of the block, at its base, and at its top edge (feet y = 65 is above it)
        assertTrue(HouseBox.bodyOverlaps(5.5, 64.0, 5.5, hidden::contains));
        assertTrue(HouseBox.bodyOverlaps(5.5, 64.9, 5.5, hidden::contains));
        assertFalse(HouseBox.bodyOverlaps(5.5, 65.0, 5.5, hidden::contains));
        // head reaches into a block above: feet 62.3 + 1.8 = 64.1
        assertTrue(HouseBox.bodyOverlaps(5.5, 62.3, 5.5, hidden::contains));
        assertFalse(HouseBox.bodyOverlaps(5.5, 62.1, 5.5, hidden::contains)); // 63.9 is under the block
    }

    @Test
    void bodyWidthCountsSoAPlayerPressedAgainstAWallTouchesIt() {
        Set<Long> hidden = Set.of(RegionShape.pack(5, 64, 5));
        assertTrue(HouseBox.bodyOverlaps(4.8, 64, 5.5, hidden::contains)); // 4.8 + 0.31 > 5
        assertFalse(HouseBox.bodyOverlaps(4.6, 64, 5.5, hidden::contains));
        assertTrue(HouseBox.bodyOverlaps(6.2, 64, 5.5, hidden::contains)); // 6.2 - 0.31 < 6
        assertFalse(HouseBox.bodyOverlaps(6.4, 64, 5.5, hidden::contains));
    }

}
