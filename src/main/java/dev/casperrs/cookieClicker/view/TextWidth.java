package dev.casperrs.cookieClicker.view;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Estimates how wide a text display renders a component, in font pixels at scale 1 (1 px = 1/40 block), the way
 * the vanilla client does it: default-font glyph advances (+1 per bold glyph), word wrap at the display's line
 * width, and the background hugging the widest wrapped line. A text display draws its block centred on its
 * position (the alignment only aligns lines inside the block), so shifting it right by half that width puts the
 * left edge of its background on the position. Pure; no server needed.
 *
 * <p>Glyphs outside the default font's ASCII sheet use a safe over-estimate, so the estimate may be a little too
 * wide (text a little further right), never so narrow that the text spreads left over the row icon.
 */
public final class TextWidth {

    /** Font pixels per block at display scale 1. */
    public static final float PIXELS_PER_BLOCK = 40f;
    /** Height of one rendered line (font line height 9 + 1 px spacing), in pixels at scale 1. */
    public static final int LINE_HEIGHT_PX = 10;
    /** Advance of a plain space, the unit text padding is made of. */
    public static final int SPACE_PX = 4;

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    /** A run of text with one bold state. */
    public record Run(String text, boolean bold) {
    }

    private TextWidth() {
    }

    /**
     * Advance of one glyph in the default (ASCII) font, in pixels, including the 1 px gap; bold adds 1.
     * Unknown glyphs: 6 for Latin/Greek/Cyrillic, 9 (unifont, half size) for everything else.
     */
    public static int advance(int codePoint, boolean bold) {
        return base(codePoint) + (bold ? 1 : 0);
    }

    private static int base(int c) {
        return switch (c) {
            case ' ' -> 4;
            case '!', '\'', ',', '.', ':', ';', 'i', '|', '·' -> 2;
            case '`', 'l' -> 3;
            case '"', 'I', 't', '[', ']' -> 4;
            case '(', ')', '*', '<', '>', '{', '}', 'f', 'k' -> 5;
            case '@', '~' -> 7;
            default -> c < 0x0590 ? 6 : 9;
        };
    }

    /** Flattens a component into bold/non-bold runs (newlines stay as {@code '\n'}). */
    public static List<Run> runs(Component component) {
        List<Run> out = new ArrayList<>();
        flatten(component, false, out);
        return out;
    }

    private static void flatten(Component c, boolean parentBold, List<Run> out) {
        TextDecoration.State state = c.style().decoration(TextDecoration.BOLD);
        boolean bold = state == TextDecoration.State.NOT_SET ? parentBold : state == TextDecoration.State.TRUE;
        String own = c instanceof TextComponent t ? t.content() : PLAIN.serialize(c.children(List.of()));
        if (!own.isEmpty()) {
            out.add(new Run(own, bold));
        }
        for (Component child : c.children()) {
            flatten(child, bold, out);
        }
    }

    /** Widths (px) of the lines the client wraps {@code runs} into at {@code lineWidth} pixels. */
    public static List<Integer> lineWidths(List<Run> runs, int lineWidth) {
        // flatten to parallel arrays of code points and advances
        List<int[]> glyphs = new ArrayList<>(); // [codePoint, advance]
        for (Run r : runs) {
            r.text().codePoints().forEach(cp -> glyphs.add(new int[]{cp, advance(cp, r.bold())}));
        }
        List<Integer> widths = new ArrayList<>();
        int n = glyphs.size();
        int start = 0;
        while (true) {
            int width = 0;
            int lastSpace = -1;
            int widthAtSpace = 0;
            int next = -1; // where the next line starts; -1 = the text ended on this line
            for (int i = start; i < n; i++) {
                int cp = glyphs.get(i)[0];
                if (cp == '\n') {
                    next = i + 1;
                    break;
                }
                if (cp == ' ') {
                    lastSpace = i;
                    widthAtSpace = width;
                }
                width += glyphs.get(i)[1];
                if (width > lineWidth) {
                    if (lastSpace >= 0) { // break at the last space; the space itself is dropped
                        next = lastSpace + 1;
                        width = widthAtSpace;
                    } else if (i > start) { // one long word: break inside it
                        next = i;
                        width -= glyphs.get(i)[1];
                    } else { // a single glyph wider than the line still takes the line
                        next = i + 1;
                    }
                    break;
                }
            }
            widths.add(width);
            if (next < 0) {
                break;
            }
            start = next;
        }
        return widths;
    }

    /** Width (px) of the rendered block: its widest wrapped line. */
    public static int width(List<Run> runs, int lineWidth) {
        int max = 0;
        for (int w : lineWidths(runs, lineWidth)) {
            max = Math.max(max, w);
        }
        return max;
    }

    /** Width (px) of {@code component} as a text display with this line width renders it. */
    public static int width(Component component, int lineWidth) {
        return width(runs(component), lineWidth);
    }

    /**
     * Right shift (blocks, in the display's own frame) that puts the left edge of the text background on the
     * display's position: half the rendered width, times the display scale.
     */
    public static float leftEdgeShift(int widthPx, float scale) {
        return widthPx / 2f * scale / PIXELS_PER_BLOCK;
    }

    /** Block size of {@code px} font pixels at display scale {@code scale}. */
    public static double blocks(double px, float scale) {
        return px * scale / PIXELS_PER_BLOCK;
    }

    /**
     * Where the parts of a one-line component that carry an insertion starting with {@code prefix} are drawn:
     * insertion -> {start, end} in pixels from the left of the line. Used to find the toggle's labels.
     */
    public static Map<String, int[]> spans(Component component, String prefix) {
        Map<String, int[]> out = new HashMap<>();
        walk(component, false, null, prefix, new int[1], out);
        return out;
    }

    private static void walk(Component c, boolean parentBold, @Nullable String parentInsertion, String prefix,
                             int[] x, Map<String, int[]> out) {
        TextDecoration.State state = c.style().decoration(TextDecoration.BOLD);
        boolean bold = state == TextDecoration.State.NOT_SET ? parentBold : state == TextDecoration.State.TRUE;
        String insertion = c.insertion() != null ? c.insertion() : parentInsertion;
        int start = x[0];
        String own = c instanceof TextComponent t ? t.content() : PLAIN.serialize(c.children(List.of()));
        own.codePoints().forEach(cp -> x[0] += cp == '\n' ? 0 : advance(cp, bold));
        for (Component child : c.children()) {
            walk(child, bold, insertion, prefix, x, out);
        }
        if (insertion != null && insertion.startsWith(prefix) && !insertion.equals(parentInsertion)) {
            // a label split over several components (MiniMessage may compact styles onto the leaves) is one span
            int end = x[0];
            out.merge(insertion, new int[] {start, end}, (a, b) -> new int[] {Math.min(a[0], b[0]), Math.max(a[1], b[1])});
        }
    }
}
