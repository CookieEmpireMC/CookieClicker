package dev.casperrs.cookieClicker.view;

import dev.casperrs.cookieClicker.config.StationVisualSettings;
import dev.casperrs.cookieClicker.economy.CookiePouch;
import dev.casperrs.cookieClicker.economy.Economy;
import dev.casperrs.cookieClicker.text.Messages;
import dev.casperrs.cookieClicker.text.TextPadding;
import dev.casperrs.cookieClicker.text.TextWidth;
import dev.casperrs.cookieClicker.view.panel.BulkMode;
import dev.casperrs.cookieClicker.view.panel.BulkModes;
import dev.casperrs.cookieClicker.view.panel.PanelKind;
import dev.casperrs.cookieClicker.view.panel.PanelLayout;
import dev.casperrs.cookieClicker.view.panel.PanelPages;
import dev.casperrs.cookieClicker.view.panel.PanelRowState;
import dev.casperrs.cookieClicker.view.panel.PanelRows;
import dev.casperrs.cookieClicker.view.panel.PriceTween;
import dev.casperrs.cookieClicker.view.panel.RowState;
import dev.casperrs.cookieClicker.view.panel.UpgradeRowState;
import net.kyori.adventure.text.Component;
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
 * price roll. A changed cost or gain starts a {@link PriceTween} from what the row shows now; the animation tick
 * re-sends the row text at most every tween step until the roll lands on the exact value. An update only retargets a
 * roll when the value really changed, so it never cuts one short, and a row text is only sent when its formatted
 * content changed. Main thread only.
 */
final class PanelUpdater {

    /** The icon item of a building or upgrade (pack model or vanilla fallback). */
    interface Icons {
        ItemStack icon(PanelKind kind, String id, boolean withPack);
    }

    private final Supplier<StationVisualSettings> settings;
    private final Economy economy;
    private final Messages messages;
    private final BulkModes modes;
    private final Icons icons;
    private final PanelPages pages;
    private final PanelText text;

    PanelUpdater(Supplier<StationVisualSettings> settings, Economy economy, Messages messages, BulkModes modes,
                 Icons icons) {
        this(settings, economy, messages, modes, icons, new PanelPages());
    }

    PanelUpdater(Supplier<StationVisualSettings> settings, Economy economy, Messages messages, BulkModes modes,
                 Icons icons, PanelPages pages) {
        this.settings = settings;
        this.economy = economy;
        this.messages = messages;
        this.modes = modes;
        this.icons = icons;
        this.pages = pages;
        this.text = new PanelText(messages);
    }

    PanelText text() {
        return text;
    }

    private int defaultTop(CookiePouch pouch, int rows) {
        return PanelRows.defaultTop(economy.config(), id -> economy.buildingUnlocked(pouch, id), rows);
    }

    /** Scrolls the buildings window one row (-1 up, +1 down); false at that end, or when the panel doesn't page. */
    boolean scroll(StationView view, StationView.Panel panel, CookiePouch pouch, int delta) {
        if (panel.kind != PanelKind.BUILDINGS) {
            return false;
        }
        int rows = panel.rows.size();
        boolean moved = pages.scroll(view.owner, panel.kind, delta, defaultTop(pouch, rows),
                PanelRows.maxTop(economy.config(), rows));
        panel.scrolled |= moved;
        return moved;
    }

    /**
     * Recomputes every row of both panels; {@code pulse} (a purchase or toggle) makes rolling rows pulse too.
     */
    void update(StationView view, CookiePouch pouch, long now, boolean pulse) {
        for (StationView.Panel panel : view.panels()) {
            BulkMode mode = modes.get(view.owner, panel.kind);
            List<String> ids = panel.kind == PanelKind.BUILDINGS
                    ? updateBuildingsToggle(view, panel, pouch, mode)
                    : updateClicksToggle(panel, pouch, mode);
            updateRows(view, panel, pouch, ids, mode, now, pulse);
        }
    }

    /** The scrolled buildings window, and the toggle line with ▲ / ▼ when the panel pages. */
    private List<String> updateBuildingsToggle(StationView view, StationView.Panel panel, CookiePouch pouch,
                                               BulkMode mode) {
        int rows = panel.rows.size();
        int maxTop = PanelRows.maxTop(economy.config(), rows);
        int top = pages.top(view.owner, panel.kind, defaultTop(pouch, rows), maxTop);
        boolean up = maxTop > 0 && top > 0;
        boolean down = maxTop > 0 && top < maxTop;
        String pageKey = maxTop > 0 ? (up ? "^" : "-") + (down ? "v" : "-") : "";
        showToggle(panel, mode, pageKey, up, down);
        return PanelRows.buildings(economy.config(), top, rows);
    }

    private List<String> updateClicksToggle(StationView.Panel panel, CookiePouch pouch, BulkMode mode) {
        showToggle(panel, mode, "", false, false);
        return PanelRows.clicks(economy.config(), pouch.upgradeLevelsView(), panel.rows.size());
    }

    private void showToggle(StationView.Panel panel, BulkMode mode, String pageKey, boolean up, boolean down) {
        String toggleKey = mode.name() + '|' + pageKey;
        if (toggleKey.equals(panel.shownToggle) || !panel.toggle.isValid()) {
            return;
        }
        StationVisualSettings s = settings.get();
        Component line = pageKey.isEmpty() ? text.toggleText(mode) : text.pagedToggle(text.toggleText(mode), up, down);
        Component toggle = TextPadding.centred(line, s.panel().textPadding(),
                PanelLayout.of(s, panel.rows.size()).toggleTargetPx());
        panel.toggle.text(toggle);
        panel.toggleBox = PanelText.toggleBox(toggle);
        panel.shownMode = mode;
        panel.shownToggle = toggleKey;
    }

    private void updateRows(StationView view, StationView.Panel panel, CookiePouch pouch, List<String> ids,
                            BulkMode mode, long now, boolean pulse) {
        // items that slid into a row because the player scrolled are not news: only a purchase or unlock pulses
        boolean scrolled = panel.scrolled;
        panel.scrolled = false;
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
                showItem(view, panel, row, id, state, now, scrolled);
            } else if (state != null && !state.equals(row.state)) {
                retarget(panel, row, state, now, pulse);
            }
        }
    }

    /** Another item moved into this slot (a bought upgrade left, a building unlocked): show it at once. */
    private void showItem(StationView view, StationView.Panel panel, StationView.Row row, @Nullable String id,
                          @Nullable PanelRowState state, long now, boolean scrolled) {
        boolean hadItem = row.id != null;
        row.id = id;
        row.state = state;
        row.icon.setItemStack(id == null ? ItemStack.empty() : icons.icon(panel.kind, id, view.withPack));
        row.cost = PriceTween.fixed(state == null ? 0 : state.cost());
        row.gain = PriceTween.fixed(state == null ? 0 : state.gain());
        if (hadItem && id != null && !scrolled) {
            row.pulseStart = now;
        }
        row.animating = true;
        render(panel, row, now);
    }

    private void retarget(StationView.Panel panel, StationView.Row row, PanelRowState state, long now, boolean pulse) {
        StationVisualSettings.Tween tween = settings.get().tween();
        PanelRowState old = row.state;
        row.state = state;
        if (state.rolls() && old != null && old.rolls()) {
            PriceTween cost = row.cost.retarget(state.cost(), now, tween.ticks());
            PriceTween gain = row.gain.retarget(state.gain(), now, tween.ticks());
            if (pulse && (cost != row.cost || gain != row.gain)) {
                row.pulseStart = now;
            }
            row.cost = cost;
            row.gain = gain;
        } else {
            row.cost = PriceTween.fixed(state.cost());
            row.gain = PriceTween.fixed(state.gain());
        }
        // rate-limited like every roll step: inside the current step window animate() renders it when the window
        // ends (or at once when settled), so value churn (Max) never sends text faster than step-ticks
        row.animating = true;
        if (stepDue(row, now, tween.stepTicks())) {
            render(panel, row, now);
        }
    }

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
        Component line = switch (state) {
            case RowState b -> renderBuildingRow(row, id, b, cost, gain, mode, p);
            case UpgradeRowState u -> renderUpgradeRow(row, id, u, cost, gain, mode, p);
        };
        if (line != null) {
            send(panel, row, line, p);
        }
        transform(row, now);
        background(row, p.color(state.colorKey()), state.bright() ? p.iconBrightAffordable() : p.iconBrightUnaffordable());
    }

    /** The row text, or null when it shows exactly this already. */
    private @Nullable Component renderBuildingRow(StationView.Row row, String id, RowState b, String cost, String gain,
                                                  BulkMode mode, StationVisualSettings.Panel p) {
        String path = b.style().messagePath();
        String key = path + '|' + id + '|' + b.owned() + '|' + b.count() + '|' + mode + '|' + cost + '|' + gain;
        if (key.equals(row.renderedKey)) {
            return null;
        }
        row.renderedKey = key;
        return text.buildingRow(path, p.textPadding(), id, messages.number(b.owned()), cost, gain,
                messages.number(b.needed()), mode, b.count());
    }

    private @Nullable Component renderUpgradeRow(StationView.Row row, String id, UpgradeRowState u, String cost,
                                                 String gain, BulkMode mode, StationVisualSettings.Panel p) {
        String path = PanelText.clickPath(u);
        String key = path + '|' + id + '|' + u.level() + '|' + u.count() + '|' + mode + '|' + cost + '|' + gain
                + '|' + u.neededBuilding();
        if (key.equals(row.renderedKey)) {
            return null;
        }
        row.renderedKey = key;
        return text.clickRow(path, p.textPadding(), id, u, cost, gain, mode);
    }

    private void send(StationView.Panel panel, StationView.Row row, Component line, StationVisualSettings.Panel p) {
        int target = p.sameWidth() ? PanelLayout.of(settings.get(), panel.rows.size()).rowWidthPx() : 0;
        Component padded = TextPadding.right(line, p.textPadding(), target, p.lineWidth());
        row.text.text(padded);
        List<Integer> lines = TextWidth.lineWidths(TextWidth.runs(padded), p.lineWidth());
        int widest = 0;
        for (int w : lines) {
            widest = Math.max(widest, w);
        }
        row.renderedWidth = widest;
        row.renderedLines = lines.size();
    }

    /**
     * Left-aligns the row text (a text display is drawn centred on its position: shift it right by half its rendered
     * width) at its size, briefly {@code pulse-scale} larger after a player action. Interpolated over one text step,
     * so the pulse and width changes glide.
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
}
