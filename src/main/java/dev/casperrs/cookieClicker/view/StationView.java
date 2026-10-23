package dev.casperrs.cookieClicker.view;

import dev.casperrs.cookieClicker.view.panel.BulkMode;
import dev.casperrs.cookieClicker.view.panel.PriceTween;
import dev.casperrs.cookieClicker.view.panel.PanelRowState;
import dev.casperrs.cookieClicker.view.panel.PanelKind;
import dev.casperrs.cookieClicker.view.panel.PanelLayout;
import dev.casperrs.cookieClicker.config.StationVisualSettings.Offset;
import dev.casperrs.cookieClicker.station.Station;
import dev.casperrs.cookieClicker.text.TextWidth;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One owner's visuals at one station: pedestal, cookie + hitbox, the buildings and click upgrades panels (header,
 * ×N toggle, rows) and the upgrade shelf. Every entity is per-player (hidden by default, shown to {@link #owner}
 * only), non-persistent and tagged. Main thread only.
 */
final class StationView {

    /**
     * One panel row slot: icon and text (clicks come through the panel's box and a ray check). Which item it shows
     * ({@link #id}) can change (click upgrades move up when one is bought); an empty slot shows nothing and ignores
     * clicks.
     */
    static final class Row {
        final int index;
        final ItemDisplay icon;
        final TextDisplay text;
        /** Centre of the row in world space (where the purchase effect plays). */
        final double[] centre;
        /** Icon centre in world space (where a bought upgrade's icon flies out from). */
        final double[] iconAt;
        @Nullable String id;
        @Nullable PanelRowState state;
        PriceTween cost = PriceTween.fixed(0);
        PriceTween gain = PriceTween.fixed(0);
        /** A roll or pulse still needs text updates from the animation tick. */
        boolean animating;
        long lastTextTick = Long.MIN_VALUE;
        long pulseStart = Long.MIN_VALUE;
        /** What is rendered (template + formatted values); unchanged = nothing sent. */
        @Nullable String renderedKey;
        /** Estimated pixel width of the rendered text (for the left-edge shift). */
        int renderedWidth;
        /** Wrapped line count of the rendered text (for the click bounds). */
        int renderedLines = TextWidth.ROW_LINES;
        float sentShift = Float.NaN;
        float sentScale = Float.NaN;
        int sentColor;
        int sentLight = -1;

        Row(int index, ItemDisplay icon, TextDisplay text, double[] centre, double[] iconAt) {
            this.index = index;
            this.icon = icon;
            this.text = text;
            this.centre = centre;
            this.iconAt = iconAt;
        }

        PanelLayout.RowBox box() {
            return new PanelLayout.RowBox(id != null, renderedLines, renderedWidth);
        }
    }

    /**
     * One hologram panel: header, ×1/×5/×10/Max toggle, row slots and one click box over all of them (it only
     * triggers the ray check that picks the line).
     */
    static final class Panel {
        final PanelKind kind;
        final StationFrame frame;
        /** Header anchor in {@link #frame}. */
        final Offset anchor;
        final float iconYaw;
        final TextDisplay header;
        final TextDisplay toggle;
        final Interaction hitbox;
        final List<Row> rows;
        @Nullable BulkMode shownMode;
        /** What the toggle line shows (mode + ▲ / ▼ state); null before its first text. */
        @Nullable String shownToggle;
        /** What the toggle renders (width and label spans), for the click bounds; null before its first text. */
        PanelLayout.@Nullable ToggleBox toggleBox;
        /** The player scrolled since the last update: rows whose item changed by the scroll don't pulse. */
        boolean scrolled;

        Panel(PanelKind kind, StationFrame frame, Offset anchor, float iconYaw, TextDisplay header, TextDisplay toggle,
              Interaction hitbox, List<Row> rows) {
            this.kind = kind;
            this.frame = frame;
            this.anchor = anchor;
            this.iconYaw = iconYaw;
            this.header = header;
            this.toggle = toggle;
            this.hitbox = hitbox;
            this.rows = List.copyOf(rows);
        }

        List<PanelLayout.RowBox> rowBoxes() {
            List<PanelLayout.RowBox> out = new ArrayList<>(rows.size());
            for (Row r : rows) {
                out.add(r.box());
            }
            return out;
        }

        void addEntities(List<Entity> out) {
            out.add(header);
            out.add(toggle);
            out.add(hitbox);
            for (Row r : rows) {
                out.add(r.icon);
                out.add(r.text);
            }
        }
    }

    /**
     * One bought upgrade's icon on the shelf. While it flies in from its row it has no hitbox yet;
     * {@code flightStart}/{@code landTick} are {@link Long#MIN_VALUE} once passed.
     */
    static final class ShelfIcon {
        final String upgradeId;
        final ItemDisplay icon;
        @Nullable Interaction hitbox;
        int slot;
        long flightStart = Long.MIN_VALUE;
        long landTick = Long.MIN_VALUE;

        ShelfIcon(String upgradeId, ItemDisplay icon, int slot) {
            this.upgradeId = upgradeId;
            this.icon = icon;
            this.slot = slot;
        }

        boolean flying() {
            return landTick != Long.MIN_VALUE;
        }
    }

    final UUID owner;
    final Station station;
    final ItemDisplay cookie;
    final Interaction cookieHitbox;
    /** Cookie centre in world space (left clicks have no hit position). */
    final double[] cookieCentre;
    /** The pedestal: the cookie basket (item display, with our pack) or the plain block (block display), or none. */
    final @Nullable Display pedestal;
    /** The basket (same entity as {@link #pedestal}), or null when the plain block or nothing is shown. */
    final @Nullable ItemDisplay basket;
    /** Bottom centre of the pedestal in world space (where the basket tier-up effect plays). */
    double[] pedestalBottom = new double[3];
    /** Height of the pedestal bottom above the station point (the "up" of its offset). */
    double pedestalUp;
    @Nullable BasketLook basketLook;
    /** A tier-up waiting for its effect's peak: this look is shown at tick {@link #basketSwitchAt}. */
    @Nullable BasketLook pendingLook;
    long basketSwitchAt = Long.MIN_VALUE;
    final Panel buildings;
    final Panel clicks;
    final Map<String, ShelfIcon> shelf = new LinkedHashMap<>();
    /** The fixed entities (cookie, pedestal, panels); the shelf comes on top. */
    private final List<Entity> fixed;
    boolean withPack;
    /** Server tick the cookie's current spin/bob target is for (sent by a segment or by a pop step). */
    long motionTarget = Long.MIN_VALUE;
    /** The current target came from a pop step: wait until the tick after it before the next segment. */
    boolean motionFromPop;

    StationView(UUID owner, Station station, ItemDisplay cookie, Interaction cookieHitbox, double[] cookieCentre,
                @Nullable Display pedestal, Panel buildings, Panel clicks, boolean withPack) {
        this.owner = owner;
        this.station = station;
        this.cookie = cookie;
        this.cookieHitbox = cookieHitbox;
        this.cookieCentre = cookieCentre;
        this.pedestal = pedestal;
        this.basket = pedestal instanceof ItemDisplay d ? d : null;
        this.buildings = buildings;
        this.clicks = clicks;
        this.withPack = withPack;
        List<Entity> all = new ArrayList<>();
        all.add(cookie);
        all.add(cookieHitbox);
        if (pedestal != null) {
            all.add(pedestal);
        }
        buildings.addEntities(all);
        clicks.addEntities(all);
        this.fixed = List.copyOf(all);
    }

    Panel panel(PanelKind kind) {
        return kind == PanelKind.BUILDINGS ? buildings : clicks;
    }

    List<Panel> panels() {
        return List.of(buildings, clicks);
    }

    /** Every entity of this view: cookie, hitbox, pedestal, both panels, then the shelf icons and their hitboxes. */
    List<Entity> entities() {
        if (shelf.isEmpty()) {
            return fixed;
        }
        List<Entity> all = new ArrayList<>(fixed.size() + 2 * shelf.size());
        all.addAll(fixed);
        for (ShelfIcon s : shelf.values()) {
            all.add(s.icon);
            if (s.hitbox != null) {
                all.add(s.hitbox);
            }
        }
        return all;
    }

    /** False once any entity is gone (chunk unload, another plugin, /kill): the whole view is then respawned. */
    boolean healthy() {
        for (Entity e : entities()) {
            if (!e.isValid()) {
                return false;
            }
        }
        return true;
    }

    void remove() {
        for (Entity e : entities()) {
            e.remove();
        }
        shelf.clear();
    }
}
