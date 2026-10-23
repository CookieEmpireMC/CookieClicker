package dev.casperrs.cookieClicker.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * A MiniMessage template with {@code Placeholder.unparsed} values (or any parse function of plain-text values),
 * parsed once with marker texts in place of the values; {@link #render} swaps the markers for the values instead of
 * parsing again. The fast path is only used when,
 * for probe values, the swap gives exactly the same component as a real parse; otherwise (tags that reshape text
 * such as gradients, a placeholder inside a click event) and for empty values, {@link #render} parses. So its output
 * always equals {@link #parse}. Immutable.
 */
public final class MarkerTemplate {

    /** Unicode noncharacters: never in a message or a formatted number. */
    private static final char OPEN = '﷐';
    private static final char CLOSE = '﷑';
    private static final String[] PROBE_VALUES = {"0", "1.5 million", "12,345.6", "1e45", "999"};
    private static final int PROBE_ROUNDS = 3;

    private final List<String> names;
    private final Function<String[], Component> parser;
    private final String[] markers;
    private final @Nullable Component parsed;

    private MarkerTemplate(List<String> names, Function<String[], Component> parser, boolean usable) {
        this.names = List.copyOf(names);
        this.parser = parser;
        this.markers = new String[names.size()];
        for (int i = 0; i < markers.length; i++) {
            markers[i] = OPEN + names.get(i) + CLOSE;
        }
        this.parsed = usable ? verified() : null;
    }

    /** @param names the placeholder names, in the order {@link #render} takes their values */
    public static MarkerTemplate of(MiniMessage mm, String template, String... names) {
        List<String> list = List.of(names);
        return new MarkerTemplate(list, values -> {
            TagResolver[] resolvers = new TagResolver[values.length];
            for (int i = 0; i < values.length; i++) {
                resolvers[i] = Placeholder.unparsed(list.get(i), values[i]);
            }
            return mm.deserialize(template, resolvers);
        }, template.indexOf(OPEN) < 0);
    }

    /**
     * A template over any parse function whose values only ever end up as plain text (unparsed placeholders, also
     * inside component placeholders it builds). The probe check still decides whether the swap is used.
     *
     * @param names one name per value, in the order {@link #render} takes them
     */
    public static MarkerTemplate of(List<String> names, Function<String[], Component> parser) {
        return new MarkerTemplate(names, parser, true);
    }

    /** The real parse. */
    public Component parse(String... values) {
        checkCount(values);
        return parser.apply(values);
    }

    public Component render(String... values) {
        checkCount(values);
        if (parsed == null) {
            return parse(values);
        }
        for (String v : values) {
            if (v.isEmpty() || v.indexOf(OPEN) >= 0) { // an empty text compacts differently from the marker
                return parse(values);
            }
        }
        return swap(parsed, values);
    }

    /** True when {@link #render} swaps markers instead of parsing. */
    public boolean isFast() {
        return parsed != null;
    }

    private @Nullable Component verified() {
        Component withMarkers = parse(markers);
        for (int round = 0; round < PROBE_ROUNDS; round++) {
            String[] probe = new String[markers.length];
            for (int i = 0; i < probe.length; i++) {
                probe[i] = PROBE_VALUES[(round + i) % PROBE_VALUES.length];
            }
            if (!swap(withMarkers, probe).equals(parse(probe))) {
                return null;
            }
        }
        return withMarkers;
    }

    private void checkCount(String[] values) {
        if (values.length != names.size()) {
            throw new IllegalArgumentException("expected " + names.size() + " values, got " + values.length);
        }
    }

    private Component swap(Component c, String[] values) {
        Component out = c;
        if (c instanceof TextComponent text) {
            String content = text.content();
            if (content.indexOf(OPEN) >= 0) {
                for (int i = 0; i < markers.length; i++) {
                    content = content.replace(markers[i], values[i]);
                }
                out = text.content(content);
            }
        }
        HoverEvent<?> hover = c.hoverEvent();
        if (hover != null && hover.action() == HoverEvent.Action.SHOW_TEXT) {
            Component shown = (Component) hover.value();
            Component swapped = swap(shown, values);
            if (swapped != shown) {
                out = out.hoverEvent(HoverEvent.showText(swapped));
            }
        }
        List<Component> children = c.children();
        List<Component> swappedChildren = null;
        for (int i = 0; i < children.size(); i++) {
            Component child = children.get(i);
            Component swapped = swap(child, values);
            if (swapped != child && swappedChildren == null) {
                swappedChildren = new ArrayList<>(children);
            }
            if (swappedChildren != null) {
                swappedChildren.set(i, swapped);
            }
        }
        return swappedChildren == null ? out : out.children(swappedChildren);
    }
}
