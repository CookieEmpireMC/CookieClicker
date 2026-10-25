package dev.casperrs.cookieClicker.farm;

import dev.casperrs.cookieClicker.plot.Plot;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A countryside placed by {@code /bakery farm setup <id>} ({@code plots.yml farms.<id>}), in world coordinates. Pure.
 * {@code origin} is the farm region's minimum corner (where the countryside's origin cell landed), {@code box} the
 * whole pasted box including ground layers; places are in paste order.
 */
public record Farm(String id, String worldKey, String regionId, Plot.Cell origin, Box box,
                   Map<String, FarmPlace> places) {

    /** The place {@code /bakery farm} teleports to. */
    public static final String MARKET = "market";

    public Farm {
        places = Collections.unmodifiableMap(new LinkedHashMap<>(places));
    }

    /** An inclusive block box. */
    public record Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public int[] corner() {
            return new int[] {minX, minY, minZ};
        }

        public int[] size() {
            return new int[] {maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1};
        }
    }

    /** An inclusive x/z rectangle at height {@code y} (the viewpoint's feet level). */
    public record Yard(int minX, int minZ, int maxX, int maxZ, int y) {
    }

    /** The waterwheel's axle: the centre of its cell. */
    public record Wheel(Plot.Point at, PlacePoints.Axis axis) {
    }

    /**
     * One place's points: cell points are the bottom centre of their cell, viewpoint/panel/notice board are feet
     * spots with a facing.
     */
    public record FarmPlace(Plot.Viewpoint viewpoint, Yard yard, Plot.Point hologram, Plot.Pedestal panel,
                            Plot.Point ripe, List<Plot.Point> smoke, @Nullable Wheel wheel,
                            Plot.@Nullable Point recipeBook, Plot.@Nullable Pedestal noticeBoard) {
        public FarmPlace {
            smoke = List.copyOf(smoke);
        }
    }

    /** The market's viewpoint, else the first place's; empty for a farm without places. */
    public Optional<Plot.Viewpoint> arrival() {
        FarmPlace market = places.get(MARKET);
        if (market != null) {
            return Optional.of(market.viewpoint());
        }
        return places.values().stream().findFirst().map(FarmPlace::viewpoint);
    }
}
