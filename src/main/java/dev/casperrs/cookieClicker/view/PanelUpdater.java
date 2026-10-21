package dev.casperrs.cookieClicker.view;

import dev.casperrs.cookieClicker.config.StationVisualSettings;
import dev.casperrs.cookieClicker.economy.CookiePouch;
import dev.casperrs.cookieClicker.economy.Economy;
import dev.casperrs.cookieClicker.text.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Color;
import org.bukkit.entity.Display;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Fills the two panels of a view: which item each row slot shows, the values for the player's bulk mode, and the
 * price roll. A changed cost or +CPS/+CPC starts a {@link PriceTween} from what the row shows now; the animation
 * tick re-sends the row text at most every {@code station.panel.tween.step-ticks} until the roll has landed on the
 * exact value. The 1 Hz refresh only retargets a roll when the value really changed, so it never cuts one short.
 * A row text is only sent when its formatted content changed. Main thread only.
 */
final class PanelUpdater {

    /** The icon item of a building or upgrade (pack model or vanilla fallback). */
    interface Icons {
        ItemStack icon(PanelKind kind, String id, boolean withPack);
    }

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private final Supplier<StationVisualSettings> settings;
    private final Economy economy;
    private final Messages messages;
    private final BulkModes modes;
    private final Icons icons;

    PanelUpdater(Supplier<StationVisualSettings> settings, Economy economy, Messages messages, BulkModes modes,
                 Icons icons) {
        this.settings = settings;
        this.economy = economy;
        this.messages = messages;
        this.modes = modes;
        this.icons = icons;
    }

    /**
     * Recomputes every row of both panels.
     *
     * @param pulse a player action (purchase, toggle) changed the values: rows that roll also pulse
     */
    void update(StationView view, CookiePouch pouch, long now, boolean pulse) {
        for (StationView.Panel panel : view.panels()) {
            update(view, panel, pouch, now, pulse);
        }
    }

    private void update(StationView view, StationView.Panel panel, CookiePouch pouch, long now, boolean pulse) {
        BulkMode mode = modes.get(view.owner, panel.kind);
        if (mode != panel.shownMode && panel.toggle.isValid()) {
            panel.toggle.text(toggleText(mode));
            panel.shownMode = mode;
        }
        List<String> ids = panel.kind == PanelKind.BUILDINGS
                ? PanelRows.buildings(economy.config(), panel.rows.size())
                : PanelRows.clicks(economy.config(), pouch.upgradeLevelsView(), panel.rows.size());
        StationVisualSettings.Tween tween = settings.get().tween();
        int ticks = tween.ticks();
        int step = tween.stepTicks();
        for (StationView.Row row : panel.rows) {
            if (!row.text.isValid()) {
                continue;
            }
            String id = row.index < ids.size() ? ids.get(row.index) : null;
            PanelRowState state = id == null ? null : state(panel.kind, pouch, id, mode);
            if (state == null) {
                id = null;
            }
            if (!Objects.equals(id, row.id)) {
                // another item moved into this slot (a bought upgrade left): show it right away, with a pulse
                boolean hadItem = row.id != null;
                row.id = id;
                row.state = state;
                row.icon.setItemStack(id == null ? ItemStack.empty() : icons.icon(panel.kind, id, view.withPack));
                row.cost = PriceTween.fixed(state == null ? 0 : state.cost());
                row.gain = PriceTween.fixed(state == null ? 0 : state.gain());
                if (hadItem && id != null) {
                    row.pulseStart = now;
                }
                row.animating = true;
                render(panel, row, now);
                continue;
            }
            if (state == null || state.equals(row.state)) {
                continue; // unchanged: a running roll keeps going
            }
            PanelRowState old = row.state;
            row.state = state;
            if (state.rolls() && old != null && old.rolls()) {
                PriceTween cost = row.cost.retarget(state.cost(), now, ticks);
                PriceTween gain = row.gain.retarget(state.gain(), now, ticks);
                if (pulse && (cost != row.cost || gain != row.gain)) {
                    row.pulseStart = now;
                }
                row.cost = cost;
                row.gain = gain;
            } else {
                row.cost = PriceTween.fixed(state.cost());
                row.gain = PriceTween.fixed(state.gain());
            }
            // rate-limited like every roll step: inside the current step window animate() renders it when the
            // window ends (or at once when settled), so value churn (Max) never sends text faster than step-ticks
            row.animating = true;
            if (stepDue(row, now, step)) {
                render(panel, row, now);
            }
        }
    }

    /** A new text step may be sent: the row never rendered, or the last text is at least one step old. */
    private static boolean stepDue(StationView.Row row, long now, int step) {
        return row.lastTextTick == Long.MIN_VALUE || now - row.lastTextTick >= step;
    }

    private @Nullable PanelRowState state(PanelKind kind, CookiePouch pouch, String id, BulkMode mode) {
        if (kind == PanelKind.BUILDINGS) {
            return economy.config().buildings().containsKey(id) ? RowState.of(economy, pouch, id, mode) : null;
        }
        return UpgradeRowState.of(economy, pouch, id, mode);
    }

    /** Animation tick: rolling rows get their next text step, finished rolls land on the exact value. */
    void animate(StationView view, long now) {
        int step = settings.get().tween().stepTicks();
        for (StationView.Panel panel : view.panels()) {
            for (StationView.Row row : panel.rows) {
                if (!row.animating) {
                    continue;
                }
                if (!row.text.isValid()) {
                    row.animating = false;
                    continue;
                }
                boolean settled = row.cost.done(now) && row.gain.done(now) && now >= row.pulseStart + step;
                if (settled || stepDue(row, now, step)) {
                    render(panel, row, now);
                    if (settled) {
                        row.animating = false;
                    }
                }
            }
        }
    }

    // ---- rendering ------------------------------------------------------------------------------------------

    private void render(StationView.Panel panel, StationView.Row row, long now) {
        StationVisualSettings.Panel p = settings.get().panel();
        row.lastTextTick = now;
        PanelRowState state = row.state;
        String id = row.id;
        if (id == null || state == null) {
            if (!"".equals(row.renderedKey)) {
                row.text.text(Component.empty());
                row.renderedKey = "";
                row.renderedWidth = 0;
            }
            background(row, 0, p.iconBrightUnaffordable());
            return;
        }
        String cost = messages.number(row.cost.valueAt(now));
        String gain = messages.number(row.gain.valueAt(now));
        BulkMode mode = panel.shownMode == null ? BulkMode.ONE : panel.shownMode;
        Component text = null;
        if (state instanceof RowState b) {
            String path = b.style().messagePath();
            String key = path + '|' + id + '|' + b.owned() + '|' + b.count() + '|' + mode + '|' + cost + '|' + gain;
            if (!key.equals(row.renderedKey)) {
                row.renderedKey = key;
                text = messages.component(path,
                        Placeholder.unparsed("name", PLAIN.serialize(messages.buildingName(id))),
                        messages.number("owned", b.owned()),
                        Placeholder.unparsed("cost", cost),
                        Placeholder.unparsed("cps", gain),
                        messages.number("needed", b.needed()),
                        Placeholder.component("amount", amount(mode, b.count())));
            }
        } else if (state instanceof UpgradeRowState u) {
            String path = clickPath(u);
            String key = path + '|' + id + '|' + u.level() + '|' + u.count() + '|' + mode + '|' + cost + '|' + gain
                    + '|' + u.neededBuilding();
            if (!key.equals(row.renderedKey)) {
                row.renderedKey = key;
                text = messages.component(path,
                        Placeholder.unparsed("name", PLAIN.serialize(messages.upgradeName(id))),
                        Placeholder.unparsed("level", Integer.toString(u.level())),
                        Placeholder.unparsed("cost", cost),
                        Placeholder.unparsed("cpc", gain),
                        Placeholder.component("amount", u.repeatable() ? amount(mode, u.count()) : Component.empty()),
                        Placeholder.component("effect", messages.upgradeEffect(id, u.shownValue(), u.level())),
                        Placeholder.component("requirement", requirement(u)));
            }
        }
        if (text != null) {
            row.text.text(text);
            row.renderedWidth = TextWidth.width(text, p.lineWidth());
        }
        transform(row, now);
        background(row, p.color(state.colorKey()), state.bright() ? p.iconBrightAffordable() : p.iconBrightUnaffordable());
    }

    /**
     * Left-aligns the row text (a text display is drawn centred on its position: shift it right by half its
     * rendered width) at its size, which is briefly {@code pulse-scale} larger after a player action. Interpolated
     * over one text step, so the pulse and width changes glide.
     */
    private void transform(StationView.Row row, long now) {
        StationVisualSettings s = settings.get();
        StationVisualSettings.Panel p = s.panel();
        int step = s.tween().stepTicks();
        float base = p.textScale();
        float scale = now < row.pulseStart + step ? base * s.tween().pulseScale() : base;
        float shift = p.textLeftEdge() ? TextWidth.leftEdgeShift(row.renderedWidth, scale) : 0;
        if (shift == row.sentShift && scale == row.sentScale) {
            return;
        }
        boolean first = Float.isNaN(row.sentScale);
        row.sentShift = shift;
        row.sentScale = scale;
        row.text.setInterpolationDelay(0);
        row.text.setInterpolationDuration(first ? 0 : step);
        row.text.setTransformation(new Transformation(new Vector3f(shift, 0, 0), new Quaternionf(),
                new Vector3f(scale, scale, scale), new Quaternionf()));
    }

    private static void background(StationView.Row row, int argb, int light) {
        if (row.sentColor != argb) {
            row.sentColor = argb;
            row.text.setBackgroundColor(Color.fromARGB(argb));
        }
        if (row.sentLight != light) {
            row.sentLight = light;
            row.icon.setBrightness(new Display.Brightness(light, light));
        }
    }

    /** {@code panel.bulk.amount} ("×37 ") for every mode but ×1. */
    private Component amount(BulkMode mode, int count) {
        return mode == BulkMode.ONE ? Component.empty()
                : messages.component("panel.bulk.amount", Placeholder.unparsed("count", Integer.toString(count)));
    }

    private Component requirement(UpgradeRowState u) {
        if (u.neededBuilding() != null) {
            return messages.component("panel.clicks.need-building", messages.number("owned", u.neededOwned()),
                    Placeholder.unparsed("building", PLAIN.serialize(messages.buildingName(u.neededBuilding()))));
        }
        return messages.component("panel.clicks.need-baked", messages.number("needed", u.neededBaked()));
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
                Placeholder.component("label", name));
    }
}
