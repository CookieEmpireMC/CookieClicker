package dev.casperrs.cookieClicker.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** the ▲ / ▼ scroll of the buildings panel (clamped, dropped when the default moves, per player). */
class PanelPagesTest {

    /** Seven buildings in three rows: the default window starts at row 1, the last window at row 4. */
    private static final int DEFAULT_TOP = 1;
    private static final int MAX_TOP = 4;

    private final PanelPages pages = new PanelPages();
    private final UUID anna = UUID.randomUUID();
    private final UUID ben = UUID.randomUUID();

    @Test
    void aScrollMovesOneRowAndStopsAtBothEndsWhereItChangesNothing() {
        assertEquals(DEFAULT_TOP, pages.top(anna, PanelKind.BUILDINGS, DEFAULT_TOP, MAX_TOP));
        assertTrue(pages.scroll(anna, PanelKind.BUILDINGS, 1, DEFAULT_TOP, MAX_TOP));
        assertEquals(2, pages.top(anna, PanelKind.BUILDINGS, DEFAULT_TOP, MAX_TOP));
        assertTrue(pages.scroll(anna, PanelKind.BUILDINGS, 9, DEFAULT_TOP, MAX_TOP), "a big step clamps to the end");
        assertEquals(MAX_TOP, pages.top(anna, PanelKind.BUILDINGS, DEFAULT_TOP, MAX_TOP));
        assertFalse(pages.scroll(anna, PanelKind.BUILDINGS, 1, DEFAULT_TOP, MAX_TOP), "already at the bottom");
        assertTrue(pages.scroll(anna, PanelKind.BUILDINGS, -9, DEFAULT_TOP, MAX_TOP));
        assertEquals(0, pages.top(anna, PanelKind.BUILDINGS, DEFAULT_TOP, MAX_TOP), "clamps to the first row");
        assertFalse(pages.scroll(anna, PanelKind.BUILDINGS, -1, DEFAULT_TOP, MAX_TOP), "already at the top");
    }

    @Test
    void theScrollIsPerPlayerAndForgottenOnQuit() {
        pages.scroll(anna, PanelKind.BUILDINGS, 2, DEFAULT_TOP, MAX_TOP);
        assertEquals(DEFAULT_TOP, pages.top(ben, PanelKind.BUILDINGS, DEFAULT_TOP, MAX_TOP));
        pages.forget(anna);
        assertEquals(DEFAULT_TOP, pages.top(anna, PanelKind.BUILDINGS, DEFAULT_TOP, MAX_TOP), "back to the default");
    }

}
