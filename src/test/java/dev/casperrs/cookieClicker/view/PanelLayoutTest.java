package dev.casperrs.cookieClicker.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.casperrs.cookieClicker.config.StationVisualSettings;
import dev.casperrs.cookieClicker.config.StationVisualSettings.Offset;
import dev.casperrs.cookieClicker.config.StationVisualSettings.Shelf;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.configuration.file.YamlConfiguration;
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

    // ---- click picking: the rendered text under the crosshair (default station.panel numbers) ----------------
    // Header 0..0.15; gap 0.2; toggle text 0.1125 tall, bottom -0.3125; gap 0.25; row text 0.25 tall, bottom of
    // row i = -0.8125 - 0.42 i (row 1 = -1.2325). Text grows up from its position, so a row spans bottom..bottom+h.

    private static final StationVisualSettings DEFAULTS =
            StationVisualSettings.from(new YamlConfiguration(), problem -> { });
    private static final Offset ORIGIN = new Offset(0, 0, 0);
    private static final PanelLayout.RowBox TWO_LINES = new PanelLayout.RowBox(true, 2, 116);
    private static final PanelLayout.RowBox EMPTY = new PanelLayout.RowBox(false, 2, 116);
    /** Four 20 px labels in an 80 px toggle (scale 0.45: 1 block = 40 / 0.45 px). */
    private static final PanelLayout.ToggleBox TOGGLE = new PanelLayout.ToggleBox(80, Map.of(
            BulkMode.ONE, new int[] {0, 20}, BulkMode.FIVE, new int[] {20, 40},
            BulkMode.TEN, new int[] {40, 60}, BulkMode.MAX, new int[] {60, 80}));

    private static List<PanelLayout.RowBox> fourRows() {
        return List.of(TWO_LINES, TWO_LINES, TWO_LINES, TWO_LINES);
    }

    /** A ray from 3 blocks in front of the panel, straight at it, hitting the text plane at (right, up). */
    private static PanelLayout.Hit aim(double right, double up, List<PanelLayout.RowBox> rows,
                                       PanelLayout.ToggleBox toggle) {
        return PanelLayout.of(DEFAULTS, rows.size())
                .pick(new Offset(right, up, -3), new Offset(0, 0, 1), 6, rows, toggle);
    }

    private static void assertRow(int row, PanelLayout.Hit hit) {
        assertNotNull(hit, "row " + row);
        assertEquals(PanelLayout.Hit.Kind.ROW, hit.kind());
        assertEquals(row, hit.row());
    }

    private static void assertToggle(BulkMode mode, double right) {
        PanelLayout.Hit hit = aim(right, -0.26, List.of(), TOGGLE);
        assertNotNull(hit, "label " + mode);
        assertEquals(PanelLayout.Hit.Kind.TOGGLE, hit.kind());
        assertEquals(mode, hit.mode());
    }

    @Test
    void aRowIsPickedByItsRenderedTextAndItsIcon() {
        assertRow(0, aim(0.2, -0.6875, fourRows(), null));    // middle of row 0's text
        assertRow(0, aim(0.2, -0.8025, fourRows(), null));    // just above row 0's bottom: text grows up from it
        assertRow(0, aim(-0.75, -0.6875, fourRows(), null));  // the icon belongs to the row
        assertRow(1, aim(0.2, -1.0, fourRows(), null));
        assertRow(3, aim(0.2, -1.9, fourRows(), null));
    }

    @Test
    void theGapsBetweenRowsBesideTheIconAndBelowTheLastRowPickNothing() {
        assertNull(aim(0.2, -0.89, fourRows(), null));        // air between row 0 and row 1 (margin 0.03 each side)
        assertNull(aim(-0.95, -0.6875, fourRows(), null));    // beside the icon, outside the margin
        assertNull(aim(0.2, -2.3, fourRows(), null));         // below the last row
    }

    @Test
    void anEmptySlotAndRaysThatMissOrAreOutOfReachPickNothing() {
        List<PanelLayout.RowBox> rows = List.of(TWO_LINES, EMPTY, TWO_LINES);
        assertNull(aim(0.2, -1.1, rows, null), "row 1 has no item");
        assertRow(0, aim(0.2, -0.6875, rows, null));
        PanelLayout one = PanelLayout.of(DEFAULTS, 1);
        Offset dir = new Offset(0, 0, 1);
        assertNull(one.pick(new Offset(0.2, -0.6875, -7), dir, 6, List.of(TWO_LINES), null), "beyond the click distance");
        assertEquals(7, one.pick(new Offset(0.2, -0.6875, -7), dir, 8, List.of(TWO_LINES), null).distance(), 1e-9);
        assertNull(one.pick(new Offset(0.2, -0.6875, 3), dir, 6, List.of(TWO_LINES), null), "behind the eye");
        assertNull(one.pick(new Offset(0.2, -0.6875, -3), new Offset(1, 0, 0), 6, List.of(TWO_LINES), null),
                "parallel to the panel");
    }

    @Test
    void eachToggleLabelSelectsItsModeAndAGapBetweenLabelsCycles() {
        assertToggle(BulkMode.ONE, -0.3375);   // 10 px from the left edge of the toggle
        assertToggle(BulkMode.FIVE, -0.1125);  // 30 px
        assertToggle(BulkMode.TEN, 0.1125);    // 50 px
        assertToggle(BulkMode.MAX, 0.3375);    // 70 px
        PanelLayout.ToggleBox tight = new PanelLayout.ToggleBox(80,
                Map.of(BulkMode.ONE, new int[] {0, 18}, BulkMode.FIVE, new int[] {22, 40}));
        PanelLayout.Hit gap = aim(-0.225, -0.26, List.of(), tight); // 20 px: between the two labels
        assertNotNull(gap);
        assertEquals(PanelLayout.Hit.Kind.TOGGLE, gap.kind());
        assertNull(gap.mode(), "between labels: the toggle cycles");
    }

    @Test
    void theHeaderAndTheAirAboveTheToggleAreNotClickable() {
        assertNull(aim(0, 0.07, fourRows(), TOGGLE));                                  // header text
        assertNull(aim(0, -0.1, fourRows(), TOGGLE));                                  // air under the header
        assertEquals(PanelLayout.Hit.Kind.TOGGLE, aim(0, -0.25, fourRows(), TOGGLE).kind());
    }

    @Test
    void thePickIsTheSameOnAPanelAtAnyYaw() {
        for (float yaw : new float[] {0, 30, 90, -135, 200}) {
            StationFrame frame = new StationFrame(10.5, 64, -20.25, yaw);
            double[] eye = frame.point(new Offset(0.2, -0.6875, -3));
            double[] at = frame.point(new Offset(0.2, -0.6875, 0));
            double[] dir = {at[0] - eye[0], at[1] - eye[1], at[2] - eye[2]};
            double len = Math.sqrt(dir[0] * dir[0] + dir[1] * dir[1] + dir[2] * dir[2]);
            Offset local = frame.local(eye[0], eye[1], eye[2], ORIGIN);
            Offset d = frame.direction(dir[0] / len, dir[1] / len, dir[2] / len);
            PanelLayout.Hit hit = PanelLayout.of(DEFAULTS, 4).pick(local, d, 6, fourRows(), null);
            assertRow(0, hit);
            assertEquals(3, hit.distance(), 1e-6, "yaw " + yaw);
        }
    }

    @Test
    void theOneClickBoxOfAPanelCoversEveryLineAndStaysClearOfTheCookie() {
        StationVisualSettings.Cookie cookie = DEFAULTS.cookie();
        double cookieHalf = (cookie.scale() + cookie.hitboxMargin()) / 2;
        PanelLayout layout = PanelLayout.of(DEFAULTS, 4);
        for (Offset panel : List.of(DEFAULTS.buildings().offset(), DEFAULTS.clicks().offset())) {
            double gap = Math.abs(panel.right() - cookie.offset().right()) - layout.boxWidth() / 2 - cookieHalf;
            assertTrue(gap > 0, "the panel box touches the cookie box, gap " + gap);
        }
        assertTrue(layout.boxBottom().up() < layout.rowBottom(3), "box misses the last row");
        assertTrue(layout.boxBottom().up() + layout.boxHeight() > layout.headerHeight(), "box misses the header");
    }
}
