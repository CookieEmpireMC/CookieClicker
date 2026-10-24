package dev.casperrs.cookieClicker.house.design;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The plot columns (plot cells from the plot's min corner, the frame of {@code layout:}) where decor may never go.
 * Pure. A column is out, at every height, when it lies on:
 * <ul>
 *   <li>the fence ring (the layout's outer ring, gate included);</li>
 *   <li>the factory: the whole design box plus {@link #MARGIN} (door, hatch, porch);</li>
 *   <li>the front zone: the rectangle around the gate, the pedestal, the shop and the click-upgrade spots, widened
 *       by {@link #FRONT_MARGIN_X} / {@link #FRONT_MARGIN_Z} (the walk, the click rows, hitboxes, panels, shelf);</li>
 *   <li>the walk from the gate to the house door ({@code points.door}): the bounding box of the gate and the door;</li>
 *   <li>the belt cells plus {@link #MARGIN};</li>
 *   <li>every building slot ({@code houses/building-slots.yml}), when given: each cell its footprint square covers
 *       ({@code floor(c+0.5-h) .. ceil(c+0.5+h)-1}, h = half the side, design/buildings-world.md).</li>
 * </ul>
 * For the factory layout this gives exactly the table in design/decor.md (factory x 5..25 z 9..24, front zone
 * x 12..23 z 1..8 inside the ring).
 *
 * <p><b>Ground decor</b> (y below 0: it replaces the plot's ground, so it blocks nothing above it) has its own, smaller
 * keep-out: the fence ring, the house design box itself (no margin; that ground belongs to the house design) and the
 * belt cells (no margin). Paths may run through the front zone, along the walk and under building slots.
 */
public final class DecorKeepOut {

    /** Free columns kept around the factory's design box and the belt. */
    public static final int MARGIN = 1;
    /**
     * The front zone (gate, pedestal, shop and click spots) is widened this far west/east: the click rows, the
     * panels' hitboxes and the upgrade shelf growing beside the click panel.
     */
    public static final int FRONT_MARGIN_X = 3;
    /** ... and this far north/south (the free rows in front of the pedestal and behind the panels). */
    public static final int FRONT_MARGIN_Z = 2;

    /** An inclusive column rectangle with the reason it is out. */
    private record Area(String reason, int minX, int minZ, int maxX, int maxZ) {
        static Area between(String reason, int ax, int az, int bx, int bz, int margin) {
            return new Area(reason, Math.min(ax, bx) - margin, Math.min(az, bz) - margin,
                    Math.max(ax, bx) + margin, Math.max(az, bz) + margin);
        }

        boolean contains(int x, int z) {
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
        }
    }

    private final int sizeX;
    private final int sizeZ;
    private final List<Area> areas;
    private final List<Area> groundAreas;

    private DecorKeepOut(int sizeX, int sizeZ, List<Area> areas, List<Area> groundAreas) {
        this.sizeX = sizeX;
        this.sizeZ = sizeZ;
        this.areas = List.copyOf(areas);
        this.groundAreas = List.copyOf(groundAreas);
    }

    /** The keep-out of {@code design}'s plot layout; null when the design has no layout. */
    public static @Nullable DecorKeepOut of(HouseDesign design) {
        return of(design, BuildingSlots.empty());
    }

    /**
     * The keep-out of {@code design}'s plot layout plus every building slot cell (decor never
     * stands where a bought building appears); null when the design has no layout.
     */
    public static @Nullable DecorKeepOut of(HouseDesign design, BuildingSlots slots) {
        return of(design, slots, Map.of());
    }

    /**
     * As {@link #of(HouseDesign, BuildingSlots)}, with every slot's footprint square kept out instead of its cell.
     * Footprint sides in blocks; a missing id keeps only the slot cell.
     */
    public static @Nullable DecorKeepOut of(HouseDesign design, BuildingSlots slots, Map<String, Double> footprints) {
        DecorKeepOut base = layoutOnly(design);
        if (base == null || slots.slots().isEmpty()) {
            return base;
        }
        List<Area> areas = new ArrayList<>(base.areas);
        slots.slots().forEach((id, list) -> {
            double h = footprints.getOrDefault(id, 0.0) / 2;
            for (BuildingSlots.Slot s : list) {
                areas.add(new Area("a building slot", low(s.x(), h), low(s.z(), h), high(s.x(), h), high(s.z(), h)));
            }
        });
        return new DecorKeepOut(base.sizeX, base.sizeZ, areas, base.groundAreas);
    }

    private static @Nullable DecorKeepOut layoutOnly(HouseDesign design) {
        HouseLayout layout = design.layout();
        if (layout == null) {
            return null;
        }
        List<Area> areas = new ArrayList<>();
        List<Area> ground = new ArrayList<>();
        int[] corner = design.toWorld(layout.houseAt(), 0, 0, 0);
        areas.add(Area.between("the factory", corner[0], corner[2], corner[0] + design.sizeX() - 1,
                corner[2] + design.sizeZ() - 1, MARGIN));
        ground.add(Area.between("the house design box", corner[0], corner[2], corner[0] + design.sizeX() - 1,
                corner[2] + design.sizeZ() - 1, 0));
        // front zone: the gate, the pedestal and both panel spots, widened (walk, click rows, hitboxes, shelf)
        int ex = layout.entrance()[0];
        int ez = layout.entrance()[1];
        int minX = ex;
        int maxX = ex;
        int minZ = ez;
        int maxZ = ez;
        List<HouseLayout.Spot> spots = new ArrayList<>(List.of(layout.pedestal(), layout.shop()));
        if (layout.clicks() != null) {
            spots.add(layout.clicks());
        }
        for (HouseLayout.Spot s : spots) {
            int[] c = cell(s.at());
            minX = Math.min(minX, c[0]);
            maxX = Math.max(maxX, c[0]);
            minZ = Math.min(minZ, c[2]);
            maxZ = Math.max(maxZ, c[2]);
        }
        areas.add(new Area("the walk, pedestal and panels", minX - FRONT_MARGIN_X, minZ - FRONT_MARGIN_Z,
                maxX + FRONT_MARGIN_X, maxZ + FRONT_MARGIN_Z));
        int[] door = design.points().get("door");
        if (door != null) {
            int[] d = design.toWorld(layout.houseAt(), door[0], door[1], door[2]);
            areas.add(Area.between("the walk to the door", ex, ez, d[0], d[2], 0));
        }
        if (layout.beltFrom() != null && layout.beltTo() != null) {
            int[] f = layout.beltFrom();
            int[] t = layout.beltTo();
            areas.add(Area.between("the belt", f[0], f[2], t[0], t[2], MARGIN));
            ground.add(Area.between("the belt", f[0], f[2], t[0], t[2], 0));
        }
        return new DecorKeepOut(layout.sizeX(), layout.sizeZ(), areas, ground);
    }

    /** Why decor may not go at plot cell {@code (x, z)}, or null when it may. */
    public @Nullable String reason(int x, int z) {
        return reason(x, z, areas);
    }

    /** Why ground decor (y below 0) may not go at plot cell {@code (x, z)}, or null when it may. */
    public @Nullable String groundReason(int x, int z) {
        return reason(x, z, groundAreas);
    }

    private @Nullable String reason(int x, int z, List<Area> areas) {
        if (x < 0 || z < 0 || x >= sizeX || z >= sizeZ) {
            return "outside the plot";
        }
        if (x == 0 || z == 0 || x == sizeX - 1 || z == sizeZ - 1) {
            return "the fence ring";
        }
        for (Area a : areas) {
            if (a.contains(x, z)) {
                return a.reason();
            }
        }
        return null;
    }

    /** Splits {@code decor} into the blocks that may be placed; every rejected one adds a problem. */
    public List<Decor.Block> allowed(Decor decor, List<String> problems) {
        List<Decor.Block> out = new ArrayList<>(decor.blocks().size());
        for (Decor.Block b : decor.blocks()) {
            String why = b.y() < 0 ? groundReason(b.x(), b.z()) : reason(b.x(), b.z());
            if (why == null) {
                out.add(b);
            } else {
                problems.add(b.x() + " " + b.y() + " " + b.z() + " (" + b.blockData() + ") lies in a keep-out cell (" + why + "); skipped");
            }
        }
        return out;
    }

    /** First cell a footprint of half side {@code h} centred on cell {@code c}'s middle covers. */
    private static int low(int c, double h) {
        return h <= 0 ? c : (int) Math.floor(c + 0.5 - h);
    }

    /** Last cell it covers. */
    private static int high(int c, double h) {
        return h <= 0 ? c : (int) Math.ceil(c + 0.5 + h) - 1;
    }

    private static int[] cell(double[] at) {
        return new int[] {(int) Math.floor(at[0]), (int) Math.floor(at[1]), (int) Math.floor(at[2])};
    }
}
