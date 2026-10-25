package dev.casperrs.cookieClicker.farm;

import dev.casperrs.cookieClicker.house.design.HouseDesign;
import dev.casperrs.cookieClicker.house.design.HouseDesignParser;
import dev.casperrs.cookieClicker.plot.Plot;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Where the countryside and each place land for a farm region: the countryside's origin cell on the region's minimum
 * corner, each place's cell [0, 0, 0] on the countryside cell {@code places.<p>.at}. Pure.
 */
public record FarmPlan(List<Part> parts, Farm.Box box, Map<String, Farm.FarmPlace> places) {

    /** One design to paste: {@code anchor} is what {@link HouseDesign#toWorld} takes as the region minimum. */
    public record Part(String name, HouseDesign design, int[] anchor) {
        public int[] corner() {
            return design.corner(anchor);
        }
    }

    /** The plan, or null with the problems (a place sticking out of the countryside). */
    public record Result(@Nullable FarmPlan plan, List<String> problems) {
    }

    public FarmPlan {
        parts = List.copyOf(parts);
        places = Collections.unmodifiableMap(new LinkedHashMap<>(places));
    }

    public static Result plan(FarmDesigns.Layout layout, int[] regionMin) {
        HouseDesign land = layout.countryside();
        List<Part> parts = new ArrayList<>();
        parts.add(new Part(FarmDesigns.COUNTRYSIDE, land, regionMin.clone()));
        Farm.Box landBox = boxOf(parts.getFirst());
        int lowest = regionMin[1] - HouseDesignParser.MAX_FARM_GROUND_DEPTH;
        List<String> problems = new ArrayList<>();
        Map<String, Farm.FarmPlace> places = new LinkedHashMap<>();
        Farm.Box box = landBox;
        for (FarmDesigns.Place place : layout.places().values()) {
            PlacePoints.Cell at = layout.placeAt().get(place.name());
            int[] target = land.toWorld(regionMin, at.x(), at.y(), at.z());
            int[] origin = place.design().origin();
            Part part = new Part(place.name(), place.design(),
                    new int[] {target[0] + origin[0], target[1] + origin[1], target[2] + origin[2]});
            Farm.Box b = boxOf(part);
            if (b.minX() < landBox.minX() || b.minZ() < landBox.minZ() || b.maxX() > landBox.maxX()
                    || b.maxZ() > landBox.maxZ() || b.maxY() > landBox.maxY()) {
                problems.add("place " + place.name() + " (" + sizeText(b.size()) + " at places." + place.name()
                        + ".at) sticks out of the countryside (" + sizeText(landBox.size()) + ")");
            } else if (b.minY() < lowest) {
                problems.add("place " + place.name() + " reaches more than "
                        + HouseDesignParser.MAX_FARM_GROUND_DEPTH + " layers below the countryside's base");
            }
            parts.add(part);
            box = union(box, b);
            places.put(place.name(), toWorld(part, place.points()));
        }
        return problems.isEmpty() ? new Result(new FarmPlan(parts, box, places), List.of())
                : new Result(null, List.copyOf(problems));
    }

    /**
     * The size a region with minimum corner {@code regionMin} and maximum {@code regionMax} must have for this plan
     * ({@code [x, y, z]}, y counted from the region's min y up), or null when it fits.
     */
    public int @Nullable [] neededSize(int[] regionMin, int[] regionMax) {
        int[] need = {box.maxX() - regionMin[0] + 1, box.maxY() - regionMin[1] + 1, box.maxZ() - regionMin[2] + 1};
        boolean fits = box.minX() >= regionMin[0] && box.minZ() >= regionMin[2]
                && need[0] <= regionMax[0] - regionMin[0] + 1 && need[1] <= regionMax[1] - regionMin[1] + 1
                && need[2] <= regionMax[2] - regionMin[2] + 1;
        return fits ? null : need;
    }

    /** Non-keep cells over all parts (what the paste sets, places overriding the countryside). */
    public long cellCount() {
        long n = 0;
        for (Part p : parts) {
            char[][][] grid = p.design().grid();
            for (char[][] layer : grid) {
                for (char[] row : layer) {
                    for (char c : row) {
                        if (!HouseDesignParser.isKeep(c)) {
                            n++;
                        }
                    }
                }
            }
        }
        return n;
    }

    public static String sizeText(int[] size) {
        return size[0] + " x " + size[1] + " x " + size[2];
    }

    private static Farm.Box boxOf(Part p) {
        int[] c = p.corner();
        int[] s = p.design().size();
        return new Farm.Box(c[0], c[1], c[2], c[0] + s[0] - 1, c[1] + s[1] - 1, c[2] + s[2] - 1);
    }

    private static Farm.Box union(Farm.Box a, Farm.Box b) {
        return new Farm.Box(Math.min(a.minX(), b.minX()), Math.min(a.minY(), b.minY()), Math.min(a.minZ(), b.minZ()),
                Math.max(a.maxX(), b.maxX()), Math.max(a.maxY(), b.maxY()), Math.max(a.maxZ(), b.maxZ()));
    }

    private static Farm.FarmPlace toWorld(Part part, PlacePoints pts) {
        int[] v = world(part, pts.viewpoint().at());
        Plot.Viewpoint viewpoint = new Plot.Viewpoint(v[0] + 0.5, v[1], v[2] + 0.5, pts.viewpoint().yaw(),
                pts.viewpoint().pitch());
        int[] from = part.design().toWorld(part.anchor(), pts.yard().fromX(), 0, pts.yard().fromZ());
        int[] to = part.design().toWorld(part.anchor(), pts.yard().toX(), 0, pts.yard().toZ());
        Farm.Yard yard = new Farm.Yard(from[0], from[2], to[0], to[2], v[1]);
        Farm.Wheel wheel = null;
        if (pts.wheel() != null) {
            int[] w = world(part, pts.wheel().at());
            wheel = new Farm.Wheel(new Plot.Point(w[0] + 0.5, w[1] + 0.5, w[2] + 0.5), pts.wheel().axis());
        }
        List<Plot.Point> smoke = pts.smoke() == null ? List.of() : List.of(point(part, pts.smoke()));
        return new Farm.FarmPlace(viewpoint, yard, point(part, pts.hologram()), pedestal(part, pts.panel()),
                point(part, pts.ripe()), smoke, wheel,
                pts.recipeBook() == null ? null : point(part, pts.recipeBook()),
                pts.noticeBoard() == null ? null : pedestal(part, pts.noticeBoard()));
    }

    private static int[] world(Part part, PlacePoints.Cell c) {
        return part.design().toWorld(part.anchor(), c.x(), c.y(), c.z());
    }

    private static Plot.Point point(Part part, PlacePoints.Cell c) {
        int[] w = world(part, c);
        return new Plot.Point(w[0] + 0.5, w[1], w[2] + 0.5);
    }

    private static Plot.Pedestal pedestal(Part part, PlacePoints.Spot s) {
        int[] w = world(part, s.at());
        return new Plot.Pedestal(w[0] + 0.5, w[1], w[2] + 0.5, s.yaw());
    }
}
