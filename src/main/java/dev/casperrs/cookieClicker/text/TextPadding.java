package dev.casperrs.cookieClicker.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.util.List;

/**
 * Inner padding of the panel text backgrounds: a text display's background hugs its text, so the padding is made
 * of spaces ({@link TextWidth#SPACE_PX} each). Rows get spaces in front of every line and after the last line (up
 * to the shared row width); single-line texts (header, toggle) get spaces on both sides. Pure.
 */
public final class TextPadding {

    private TextPadding() {
    }

    /**
     * A MiniMessage template with {@code spaces} spaces in front of every line ({@code <newline>} / {@code <br>}).
     * Spaces inside the template's tags are ordinary text, so placeholders stay {@code Placeholder.unparsed}.
     */
    public static Component lines(String template, int spaces, TagResolver... resolvers) {
        String pad = " ".repeat(spaces);
        String padded = pad + template.replace("<newline>", "<newline>" + pad).replace("<br>", "<br>" + pad);
        return Messages.miniMessage().deserialize(padded, resolvers);
    }

    /**
     * {@code text} with spaces after its last line, so its background is at least {@code spaces} spaces wider
     * than its widest line and at least {@code targetPx} wide, without ever exceeding {@code lineWidth} (the client
     * would wrap the padding onto a new line).
     */
    public static Component right(Component text, int spaces, int targetPx, int lineWidth) {
        List<Integer> widths = TextWidth.lineWidths(TextWidth.runs(text), lineWidth);
        int last = widths.getLast();
        int widest = widths.stream().mapToInt(Integer::intValue).max().orElse(0);
        int want = Math.max(widest + spaces * TextWidth.SPACE_PX, targetPx);
        int n = (int) Math.ceil((want - last) / (double) TextWidth.SPACE_PX);
        n = Math.min(n, (lineWidth - last) / TextWidth.SPACE_PX);
        return n <= 0 ? text : Component.text().append(text).append(Component.text(" ".repeat(n))).build();
    }

    /**
     * A single line with at least {@code spaces} spaces on each side, padded to the space count closest to
     * {@code targetPx} (so its background matches the column within half a space on each side) and centred in it;
     * an odd count puts the extra space on the right.
     */
    public static Component centred(Component text, int spaces, int targetPx) {
        int width = TextWidth.width(text, TextWidth.SINGLE_LINE_WIDTH);
        int total = (int) Math.round((targetPx - width) / (double) TextWidth.SPACE_PX);
        int left = Math.max(spaces, total / 2);
        int right = Math.max(spaces, total - total / 2);
        if (left + right <= 0) {
            return text;
        }
        return Component.text().append(Component.text(" ".repeat(left))).append(text)
                .append(Component.text(" ".repeat(right))).build();
    }
}
