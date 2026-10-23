package dev.casperrs.cookieClicker.view;

import dev.casperrs.cookieClicker.config.StationVisualSettings;
import dev.casperrs.cookieClicker.config.StationVisualSettings.Offset;
import dev.casperrs.cookieClicker.station.Station;
import dev.casperrs.cookieClicker.view.panel.PanelKind;
import org.bukkit.World;
import org.jspecify.annotations.Nullable;

/**
 * Where the parts of a station stand. With a plot layout ({@code station.shop()} set) the cookie stands on the station
 * point and the panels on the shop and clicks points, so only the "up" part of the configured offsets is used;
 * without one, the full offsets apply. Pure apart from the chunk checks.
 */
final class StationAnchors {

    private StationAnchors() {
    }

    static Offset cookieOffset(Station station, StationVisualSettings s) {
        return station.shop() == null ? s.cookie().offset() : upOnly(s.cookie().offset());
    }

    static Offset pedestalOffset(Station station, StationVisualSettings s) {
        return station.shop() == null ? s.pedestal().offset() : upOnly(s.pedestal().offset());
    }

    static Offset panelOffset(Station station, PanelKind kind, StationVisualSettings s) {
        Offset o = (kind == PanelKind.BUILDINGS ? s.buildings() : s.clicks()).offset();
        return anchor(station, kind) == null ? o : upOnly(o);
    }

    static StationFrame panelFrame(Station station, PanelKind kind) {
        Station.Anchor a = anchor(station, kind);
        return a == null ? StationFrame.of(station) : new StationFrame(a.x(), a.y(), a.z(), a.yaw());
    }

    /** Buildings on the shop spot, click upgrades on the clicks spot (mirrored for older plots); null without a layout. */
    private static Station.@Nullable Anchor anchor(Station station, PanelKind kind) {
        if (kind == PanelKind.BUILDINGS) {
            return station.shop();
        }
        return station.clicks() != null ? station.clicks() : mirroredClicks(station);
    }

    /**
     * The clicks spot of a plot set up before it existed: the shop spot mirrored across the pedestal
     * (2·station − shop on x and z, same height and yaw). Null when the station has no shop spot.
     */
    static Station.@Nullable Anchor mirroredClicks(Station station) {
        Station.Anchor shop = station.shop();
        return shop == null ? null
                : new Station.Anchor(2 * station.x() - shop.x(), shop.y(), 2 * station.z() - shop.z(), shop.yaw());
    }

    static boolean chunksLoaded(World world, Station station, StationVisualSettings s) {
        StationFrame frame = StationFrame.of(station);
        return chunkLoaded(world, frame, Offset.ZERO) && chunkLoaded(world, frame, cookieOffset(station, s))
                && chunkLoaded(world, panelFrame(station, PanelKind.BUILDINGS),
                        panelOffset(station, PanelKind.BUILDINGS, s))
                && chunkLoaded(world, panelFrame(station, PanelKind.CLICKS), panelOffset(station, PanelKind.CLICKS, s));
    }

    private static Offset upOnly(Offset o) {
        return new Offset(0, o.up(), 0);
    }

    private static boolean chunkLoaded(World world, StationFrame frame, Offset o) {
        double[] p = frame.point(o);
        return world.isChunkLoaded((int) Math.floor(p[0]) >> 4, (int) Math.floor(p[2]) >> 4);
    }
}
