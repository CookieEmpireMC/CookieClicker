package dev.casperrs.cookieClicker.view;

import dev.casperrs.cookieClicker.text.MarkerTemplate;
import dev.casperrs.cookieClicker.text.Messages;
import dev.casperrs.cookieClicker.text.TextPadding;
import dev.casperrs.cookieClicker.text.TextWidth;
import dev.casperrs.cookieClicker.view.panel.BulkMode;
import dev.casperrs.cookieClicker.view.panel.PanelLayout;
import dev.casperrs.cookieClicker.view.panel.UpgradeRowState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The panel texts from {@code messages.yml}: row lines (compiled once per template and shape with
 * {@link MarkerTemplate}, so a price roll step swaps numbers instead of parsing), the ×N toggle and ▲ / ▼ labels.
 * Caches are dropped when the messages reload. Main thread.
 */
final class PanelText {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    /** Insertion that marks a toggle label (the client ignores it on a text display), + the mode name. */
    static final String LABEL = "cookieclicker:bulk:";
    /** Insertion that marks a ▲ / ▼ page label, + the {@link PanelLayout.Hit.Kind} name. */
    static final String PAGE = "cookieclicker:page:";
    private static final List<String> BUILDING_VALUES = List.of("name", "owned", "cost", "cps", "needed", "count");
    private static final List<String> CLICK_VALUES = List.of("name", "level", "cost", "cpc", "count");
    /** Click row templates depend on the upgrade and level: bound the cache, it refills in a few renders. */
    private static final int MAX_TEMPLATES = 256;

    private final Messages messages;
    private final Map<String, MarkerTemplate> templates = new HashMap<>();
    private final Map<String, String> plainNames = new HashMap<>();
    private int revision = Integer.MIN_VALUE;

    PanelText(Messages messages) {
        this.messages = messages;
    }

    /** A buildings row before the right padding; values are formatted numbers. */
    Component buildingRow(String path, int spaces, String buildingId, String owned, String cost, String gain,
                          String needed, BulkMode mode, int count) {
        boolean one = mode == BulkMode.ONE;
        MarkerTemplate t = template(path + '|' + spaces + '|' + one, BUILDING_VALUES,
                v -> row(messages, path, spaces, v[0], v[1], v[2], v[3], v[4], amount(messages, one, v[5])));
        return t.render(plainName(true, buildingId), owned, cost, gain, needed, Integer.toString(count));
    }

    /** A click upgrades row before the right padding. */
    Component clickRow(String path, int spaces, String upgradeId, UpgradeRowState u, String cost, String gain,
                       BulkMode mode) {
        boolean amount = u.repeatable() && mode != BulkMode.ONE;
        String key = path + '|' + spaces + '|' + upgradeId + '|' + u.level() + '|' + amount + '|' + u.shownValue()
                + '|' + u.neededBuilding() + '|' + u.neededOwned() + '|' + u.neededBaked();
        MarkerTemplate t = template(key, CLICK_VALUES, v -> TextPadding.lines(messages.raw(path), spaces,
                Placeholder.unparsed("name", v[0]),
                Placeholder.unparsed("level", v[1]),
                Placeholder.unparsed("cost", v[2]),
                Placeholder.unparsed("cpc", v[3]),
                Placeholder.component("amount", amount(messages, !amount, v[4])),
                Placeholder.component("effect", shortEffect(upgradeId, u)),
                Placeholder.component("requirement", requirement(u))));
        return t.render(plainName(false, upgradeId), Integer.toString(u.level()), cost, gain,
                Integer.toString(u.count()));
    }

    private MarkerTemplate template(String key, List<String> names,
                                    Function<String[], Component> parser) {
        if (revision != messages.revision()) {
            revision = messages.revision();
            templates.clear();
            plainNames.clear();
        }
        if (templates.size() >= MAX_TEMPLATES && !templates.containsKey(key)) {
            templates.clear();
        }
        return templates.computeIfAbsent(key, k -> MarkerTemplate.of(names, parser));
    }

    /** Names are shown as plain text so the row colours apply. */
    private String plainName(boolean building, String id) {
        return plainNames.computeIfAbsent((building ? "b:" : "u:") + id, k -> PLAIN.serialize(
                building ? messages.buildingName(id) : messages.upgradeName(id)));
    }

    /**
     * A buildings row as the panel sends it (before the right padding): template {@code path} with {@code spaces}
     * padding spaces in front of every line. The real parse; a test renders the longest names and biggest numbers
     * with it to check rows stay {@link TextWidth#ROW_LINES} lines.
     */
    static Component buildingRow(Messages messages, String path, int spaces, String buildingId, int owned, String cost,
                                 String gain, double needed, Component amount) {
        return row(messages, path, spaces, PLAIN.serialize(messages.buildingName(buildingId)), messages.number(owned),
                cost, gain, messages.number(needed), amount);
    }

    private static Component row(Messages messages, String path, int spaces, String name, String owned, String cost,
                                 String gain, String needed, Component amount) {
        return TextPadding.lines(messages.raw(path), spaces,
                Placeholder.unparsed("name", name),
                Placeholder.unparsed("owned", owned),
                Placeholder.unparsed("cost", cost),
                Placeholder.unparsed("cps", gain),
                Placeholder.unparsed("needed", needed),
                Placeholder.component("amount", amount));
    }

    /** {@code panel.bulk.amount} ("×37 ") for every mode but ×1. */
    static Component amount(Messages messages, BulkMode mode, int count) {
        return amount(messages, mode == BulkMode.ONE, Integer.toString(count));
    }

    private static Component amount(Messages messages, boolean none, String count) {
        return none ? Component.empty()
                : messages.component("panel.bulk.amount", Placeholder.unparsed("count", count));
    }

    private Component requirement(UpgradeRowState u) {
        if (u.neededBuilding() != null) {
            return messages.component("panel.clicks.need-building", messages.number("owned", u.neededOwned()),
                    Placeholder.unparsed("building", PLAIN.serialize(messages.buildingName(u.neededBuilding()))));
        }
        return messages.component("panel.clicks.need-baked", messages.number("needed", u.neededBaked()));
    }

    /** {@code upgrades.<id>.short} when configured, else the long {@code effect} (which stays for the shelf tooltip). */
    private Component shortEffect(String id, UpgradeRowState u) {
        String path = "upgrades." + id + ".short";
        if (!messages.has(path)) {
            return messages.upgradeEffect(id, u.shownValue(), u.level());
        }
        return messages.component(path, messages.number("value", u.shownValue()),
                Placeholder.unparsed("level", Integer.toString(u.level())));
    }

    static String clickPath(UpgradeRowState u) {
        if (u.repeatable()) {
            return switch (u.style()) {
                case AFFORDABLE -> "panel.clicks.repeatable.affordable";
                case UNAFFORDABLE -> "panel.clicks.repeatable.unaffordable";
                case LOCKED -> "panel.clicks.repeatable.locked";
                case MAXED -> "panel.clicks.repeatable.maxed";
            };
        }
        return switch (u.style()) {
            case AFFORDABLE -> "panel.clicks.one-time.affordable";
            case UNAFFORDABLE, MAXED -> "panel.clicks.one-time.unaffordable";
            case LOCKED -> "panel.clicks.one-time.locked";
        };
    }

    /** The toggle row: every mode's label, the chosen one highlighted. */
    Component toggleText(BulkMode selected) {
        return messages.component("panel.bulk.toggle",
                Placeholder.component("one", label(BulkMode.ONE, selected)),
                Placeholder.component("five", label(BulkMode.FIVE, selected)),
                Placeholder.component("ten", label(BulkMode.TEN, selected)),
                Placeholder.component("max", label(BulkMode.MAX, selected)));
    }

    private Component label(BulkMode mode, BulkMode selected) {
        Component name = messages.component(switch (mode) {
            case ONE -> "panel.bulk.labels.one";
            case FIVE -> "panel.bulk.labels.five";
            case TEN -> "panel.bulk.labels.ten";
            case MAX -> "panel.bulk.labels.max";
        });
        return messages.component(mode == selected ? "panel.bulk.selected" : "panel.bulk.unselected",
                Placeholder.component("label", name)).insertion(LABEL + mode.name());
    }

    /**
     * The toggle line of a panel that pages: the ×N labels and ▲ / ▼, each enabled or disabled (a disabled one is
     * still tagged, so a click on it does nothing instead of cycling the mode).
     */
    Component pagedToggle(Component toggle, boolean up, boolean down) {
        return messages.component("panel.page.line", Placeholder.component("toggle", toggle),
                Placeholder.component("up", pageLabel(PanelLayout.Hit.Kind.PAGE_UP, up)),
                Placeholder.component("down", pageLabel(PanelLayout.Hit.Kind.PAGE_DOWN, down)));
    }

    private Component pageLabel(PanelLayout.Hit.Kind kind, boolean enabled) {
        Component name = messages.component(kind == PanelLayout.Hit.Kind.PAGE_UP ? "panel.page.up" : "panel.page.down");
        return messages.component(enabled ? "panel.page.enabled" : "panel.page.disabled",
                Placeholder.component("label", name)).insertion(PAGE + kind.name());
    }

    /** Width and label spans of a rendered toggle text (found by their {@link #LABEL} / {@link #PAGE} insertions). */
    static PanelLayout.ToggleBox toggleBox(Component toggle) {
        Map<BulkMode, int[]> labels = new EnumMap<>(BulkMode.class);
        TextWidth.spans(toggle, LABEL).forEach((insertion, span) ->
                labels.put(BulkMode.valueOf(insertion.substring(LABEL.length())), span));
        Map<PanelLayout.Hit.Kind, int[]> pages = new EnumMap<>(PanelLayout.Hit.Kind.class);
        TextWidth.spans(toggle, PAGE).forEach((insertion, span) ->
                pages.put(PanelLayout.Hit.Kind.valueOf(insertion.substring(PAGE.length())), span));
        return new PanelLayout.ToggleBox(TextWidth.width(toggle, TextWidth.SINGLE_LINE_WIDTH), labels, pages);
    }
}
