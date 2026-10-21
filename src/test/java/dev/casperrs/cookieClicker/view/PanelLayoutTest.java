package dev.casperrs.cookieClicker.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.casperrs.cookieClicker.config.StationVisualSettings.Offset;
import dev.casperrs.cookieClicker.config.StationVisualSettings.Shelf;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** upgrade-holograms: the ×1/×5/×10/Max toggle cycle, the shelf grid and the per-player bulk memory. */
class PanelLayoutTest {

    @Test
    void theToggleCyclesOneFiveTenMaxAndBack() {
        assertEquals(BulkMode.FIVE, BulkMode.ONE.next());
        assertEquals(BulkMode.TEN, BulkMode.FIVE.next());
        assertEquals(BulkMode.MAX, BulkMode.TEN.next());
        assertEquals(BulkMode.ONE, BulkMode.MAX.next());
        assertEquals(1, BulkMode.ONE.count());
        assertEquals(10, BulkMode.TEN.count());
        assertTrue(BulkMode.MAX.max());
        assertFalse(BulkMode.TEN.max());
    }

    @Test
    void shelfIconsFillRowByRowFromTheTopLeft() {
        Shelf shelf = new Shelf(new Offset(-0.28, -2.4, 0), 6, 0.22, 0.18f, 24, 0.2f, 14, 3);
        assertEquals(new Offset(-0.28, -2.4, 0), shelf.slot(0));
        assertEquals(new Offset(-0.28 + 5 * 0.22, -2.4, 0), shelf.slot(5), "the sixth icon is still on row one");
        assertEquals(new Offset(-0.28, -2.4 - 0.22, 0), shelf.slot(6), "the seventh starts the second row");
        assertEquals(new Offset(-0.28 + 0.22, -2.4 - 0.22, 0), shelf.slot(7));
    }

    @Test
    void theChosenModeIsRememberedPerPlayerAndPerPanelOnly() {
        BulkModes modes = new BulkModes();
        UUID anna = UUID.randomUUID();
        UUID ben = UUID.randomUUID();
        assertEquals(BulkMode.ONE, modes.get(anna, PanelKind.BUILDINGS), "default ×1");
        assertEquals(BulkMode.FIVE, modes.cycle(anna, PanelKind.BUILDINGS));
        assertEquals(BulkMode.FIVE, modes.get(anna, PanelKind.BUILDINGS));
        assertEquals(BulkMode.ONE, modes.get(anna, PanelKind.CLICKS));
        assertEquals(BulkMode.ONE, modes.get(ben, PanelKind.BUILDINGS));
        modes.forget(anna);
        assertEquals(BulkMode.ONE, modes.get(anna, PanelKind.BUILDINGS), "quit forgets the choice");
    }
}
