package dev.casperrs.cookieClicker.hud;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A HUD template parsed once: {@code <cookies>} and {@code <cps>} are parsed as marker texts, and rendering swaps
 * the markers for the formatted numbers instead of running MiniMessage again. {@link #compile} only returns a
 * template when, for probe values, the swap gives exactly the same component as a real parse (so tags that
 * reshape text, such as gradients, or placeholders inside click events fall back to parsing every time).
 * Immutable.
 */
final class HudTemplate {

    /** Unicode noncharacters: never in a message or a formatted number. */
    private static final String COOKIES = "﷐cookies﷑";
    private static final String CPS = "﷐cps﷑";
    private static final String[][] PROBES = {{"0", "0"}, {"1.5 million", "12,345.6"}, {"999", "1e45"}};

    private final Component parsed;

    private HudTemplate(Component parsed) {
        this.parsed = parsed;
    }

    /** @return the template, or null when the marker swap isn't provably equal to parsing (parse every time) */
    static @Nullable HudTemplate compile(MiniMessage mm, String template) {
        if (template.contains("﷐")) {
            return null;
        }
        HudTemplate t = new HudTemplate(parse(mm, template, COOKIES, CPS));
        for (String[] probe : PROBES) {
            if (!t.render(probe[0], probe[1]).equals(parse(mm, template, probe[0], probe[1]))) {
                return null;
            }
        }
        return t;
    }

    /** What the HUD always did: MiniMessage with both numbers as unparsed placeholders. */
    static Component parse(MiniMessage mm, String template, String cookies, String cps) {
        return mm.deserialize(template, Placeholder.unparsed("cookies", cookies), Placeholder.unparsed("cps", cps));
    }

    /** Values must not be empty (an empty text compacts differently from the marker); callers parse instead. */
    Component render(String cookies, String cps) {
        return swap(parsed, cookies, cps);
    }

    private static Component swap(Component c, String cookies, String cps) {
        Component out = c;
        if (c instanceof TextComponent text) {
            String content = text.content();
            if (content.indexOf('﷐') >= 0) {
                out = text.content(content.replace(COOKIES, cookies).replace(CPS, cps));
            }
        }
        HoverEvent<?> hover = c.hoverEvent();
        if (hover != null && hover.action() == HoverEvent.Action.SHOW_TEXT) {
            Component shown = (Component) hover.value();
            Component swapped = swap(shown, cookies, cps);
            if (swapped != shown) {
                out = out.hoverEvent(HoverEvent.showText(swapped));
            }
        }
        List<Component> children = c.children();
        List<Component> swappedChildren = null;
        for (int i = 0; i < children.size(); i++) {
            Component child = children.get(i);
            Component swapped = swap(child, cookies, cps);
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
