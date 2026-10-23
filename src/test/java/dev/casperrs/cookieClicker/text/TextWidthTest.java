package dev.casperrs.cookieClicker.text;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.casperrs.cookieClicker.config.StationVisualSettings.Offset;
import dev.casperrs.cookieClicker.view.StationFrame;
import java.util.List;
import net.kyori.adventure.text.format.TextDecoration;
import org.junit.jupiter.api.Test;

class TextWidthTest {

    private static List<TextWidth.Run> plain(String s) {
        return List.of(new TextWidth.Run(s, false));
    }

    @Test
    void asciiAdvancesMatchTheDefaultFont() {
        assertEquals(4, TextWidth.advance(' ', false));
        assertEquals(2, TextWidth.advance('i', false));
        assertEquals(3, TextWidth.advance('l', false));
        assertEquals(4, TextWidth.advance('t', false));
        assertEquals(5, TextWidth.advance('f', false));
        assertEquals(7, TextWidth.advance('@', false));
        assertEquals(6, TextWidth.advance('A', false));
        assertEquals(6, TextWidth.advance('7', false));
    }

    @Test
    void wrapsAtTheLastSpaceAndDropsIt() {
        // "aaa bbb" at 20 px: 18, space makes 22 > 20 -> break at the space
        assertEquals(List.of(18, 18), TextWidth.lineWidths(plain("aaa bbb"), 20));
        assertEquals(18, TextWidth.width(plain("aaa bbb"), 20));
    }

    @Test
    void longWordBreaksInsideTheWord() {
        assertEquals(List.of(18, 6), TextWidth.lineWidths(plain("aaaa"), 20));
    }

    @Test
    void newlinesStartANewLine() {
        assertEquals(List.of(6, 12), TextWidth.lineWidths(plain("a\nab"), 120));
    }

    private static net.kyori.adventure.text.format.Style Style(boolean bold) {
        return net.kyori.adventure.text.format.Style.style(TextDecoration.BOLD.withState(bold));
    }

    @Test
    void shiftedBlockHasItsLeftEdgeOnThePosition() {
        // block centred on the position spans [-w/2, +w/2] * scale / 40; after the shift the left edge is at 0
        int w = 77;
        float scale = 0.5f;
        float left = TextWidth.leftEdgeShift(w, scale) - w / 2f * scale / TextWidth.PIXELS_PER_BLOCK;
        assertEquals(0f, left, 1e-6);
    }

    @Test
    void localPlusXOfAFacingViewersEntityIsTheFramesRight() {
        // Display translation is in the entity's frame; local +X at yaw t is (cos t, sin t) in (x, z)
        // (yaw 0 = +Z, 90 = -X). The shift must point along the viewers' right = the frame's right.
        for (float yaw : new float[] {0, 45, 90, 135, 180, -90, -135, 30}) {
            StationFrame f = new StationFrame(0, 0, 0, yaw);
            double t = Math.toRadians(f.facingViewers(0));
            double[] right = f.point(new Offset(1, 0, 0));
            assertEquals(right[0], Math.cos(t), 1e-6, "x at yaw " + yaw);
            assertEquals(right[2], Math.sin(t), 1e-6, "z at yaw " + yaw);
        }
    }

}
