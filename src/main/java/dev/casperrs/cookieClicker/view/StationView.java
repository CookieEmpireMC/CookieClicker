package dev.casperrs.cookieClicker.view;

import dev.casperrs.cookieClicker.station.Station;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One owner's visuals at one station: pedestal, cookie + hitbox, panel header and rows. Every entity is
 * per-player (hidden by default, shown to {@link #owner} only), non-persistent and tagged. Main thread only.
 */
final class StationView {

    /** One shop row: icon, text and its own hitbox. {@code last} is what is currently rendered. */
    static final class Row {
        final String buildingId;
        final ItemDisplay icon;
        final TextDisplay text;
        final Interaction hitbox;
        /** Centre of the row in world space (where the purchase effect plays). */
        final double[] centre;
        @Nullable RowState last;

        Row(String buildingId, ItemDisplay icon, TextDisplay text, Interaction hitbox, double[] centre) {
            this.buildingId = buildingId;
            this.icon = icon;
            this.text = text;
            this.hitbox = hitbox;
            this.centre = centre;
        }
    }

    final UUID owner;
    final Station station;
    final ItemDisplay cookie;
    final Interaction cookieHitbox;
    /** Cookie centre in world space (left clicks have no hit position). */
    final double[] cookieCentre;
    final @Nullable BlockDisplay pedestal;
    final TextDisplay header;
    final List<Row> rows;
    boolean withPack;
    /** Server tick of the last spin/bob segment sent for this cookie. */
    long lastSegmentTick = Long.MIN_VALUE;

    StationView(UUID owner, Station station, ItemDisplay cookie, Interaction cookieHitbox, double[] cookieCentre,
                @Nullable BlockDisplay pedestal, TextDisplay header, List<Row> rows, boolean withPack) {
        this.owner = owner;
        this.station = station;
        this.cookie = cookie;
        this.cookieHitbox = cookieHitbox;
        this.cookieCentre = cookieCentre;
        this.pedestal = pedestal;
        this.header = header;
        this.rows = List.copyOf(rows);
        this.withPack = withPack;
    }

    List<Entity> entities() {
        List<Entity> all = new ArrayList<>();
        all.add(cookie);
        all.add(cookieHitbox);
        if (pedestal != null) {
            all.add(pedestal);
        }
        all.add(header);
        for (Row r : rows) {
            all.add(r.icon);
            all.add(r.text);
            all.add(r.hitbox);
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
    }
}
