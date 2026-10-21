package dev.casperrs.cookieClicker.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.casperrs.cookieClicker.config.StationVisualSettings.Offset;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
    void boldAddsOnePixelPerGlyph() {
        assertEquals(7, TextWidth.advance('A', true));
        assertEquals(14, TextWidth.width(List.of(new TextWidth.Run("ab", true)), 1000));
        assertEquals(12, TextWidth.width(plain("ab"), 1000));
    }

    @Test
    void unknownGlyphsOverEstimate() {
        assertEquals(6, TextWidth.advance('é', false));
        assertEquals(9, TextWidth.advance('中', false));
    }

    @Test
    void widthSumsAdvancesAndEmptyIsZero() {
        assertEquals(24, TextWidth.width(plain("Hello"), 1000)); // 6+6+3+3+6
        assertEquals(0, TextWidth.width(plain(""), 120));
    }

    @Test
    void wrapsAtTheLastSpaceAndDropsIt() {
        // "aaa bbb" at 20 px: 18, space makes 22 > 20 -> break at the space
        assertEquals(List.of(18, 18), TextWidth.lineWidths(plain("aaa bbb"), 20));
        assertEquals(18, TextWidth.width(plain("aaa bbb"), 20));
    }

    @Test
    void textExactlyAtLineWidthDoesNotWrap() {
        assertEquals(List.of(18), TextWidth.lineWidths(plain("aaa"), 18));
    }

    @Test
    void longWordBreaksInsideTheWord() {
        assertEquals(List.of(18, 6), TextWidth.lineWidths(plain("aaaa"), 20));
    }

    @Test
    void singleGlyphWiderThanTheLineStillTakesTheLine() {
        assertEquals(9, TextWidth.width(plain("中中"), 5)); // trailing empty line (width 0) is harmless
    }

    @Test
    void newlinesStartANewLine() {
        assertEquals(List.of(6, 12), TextWidth.lineWidths(plain("a\nab"), 120));
    }

    @Test
    void wrappedBlockIsAsWideAsItsWidestLine() {
        assertEquals(24, TextWidth.width(plain("Hello wi"), 30)); // "Hello"=24, "wi"=8
    }

    @Test
    void boldStateInheritsAndOverridesAcrossChildren() {
        Component c = Component.text("a", Style(true))
                .append(Component.text("b"))
                .append(Component.text("c").decoration(TextDecoration.BOLD, false))
                .append(Component.text("d", NamedTextColor.RED));
        List<TextWidth.Run> runs = TextWidth.runs(c);
        assertEquals(List.of(new TextWidth.Run("a", true), new TextWidth.Run("b", true),
                new TextWidth.Run("c", false), new TextWidth.Run("d", true)).toString(), runs.toString());
    }

    private static net.kyori.adventure.text.format.Style Style(boolean bold) {
        return net.kyori.adventure.text.format.Style.style(TextDecoration.BOLD.withState(bold));
    }

    @Test
    void widthOfComponentUsesFlattenedText() {
        Component c = Component.text("He").append(Component.text("llo"));
        assertEquals(24, TextWidth.width(c, 120));
    }

    @Test
    void shiftIsHalfWidthTimesScaleOver40() {
        assertEquals(0f, TextWidth.leftEdgeShift(0, 0.5f));
        assertEquals(60f / 2 * 0.5f / 40f, TextWidth.leftEdgeShift(60, 0.5f), 1e-6);
        assertEquals(0.75f, TextWidth.leftEdgeShift(120, 0.5f), 1e-6); // a full line at scale 0.5
        assertEquals(1.5f, TextWidth.leftEdgeShift(120, 1f), 1e-6);
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

    @Test
    void defaultRowBoxesClearNeighboursAndCookieWhenAxisAligned() {
        var d = dev.casperrs.cookieClicker.config.StationVisualSettings.from(
                new org.bukkit.configuration.file.YamlConfiguration(), m -> { });
        var p = d.panel();
        var c = d.cookie();
        assertTrue(p.rowPitch() > p.hitboxHeight(), "rows overlap vertically");
        // no-layout geometry: row box centre vs cookie centre, sideways in the frame (same forward)
        double rowCentre = p.offset().right() + p.hitboxOffset().right();
        double cookieCentre = c.offset().right();
        double gap = Math.abs(rowCentre - cookieCentre) - p.hitboxWidth() / 2 - (c.scale() + c.hitboxMargin()) / 2;
        assertTrue(gap > 0, "row box overlaps the cookie box at an axis-aligned yaw, gap " + gap);
    }
}
