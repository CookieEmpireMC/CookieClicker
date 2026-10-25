package dev.casperrs.cookieClicker.house.setup;

import dev.casperrs.cookieClicker.house.design.BuildingSlots;
import dev.casperrs.cookieClicker.house.design.Decor;
import dev.casperrs.cookieClicker.house.design.DecorKeepOut;
import dev.casperrs.cookieClicker.house.design.DesignFiles;
import dev.casperrs.cookieClicker.house.design.FenceLayout;
import dev.casperrs.cookieClicker.house.design.FenceStyle;
import dev.casperrs.cookieClicker.house.design.HouseDesign;
import dev.casperrs.cookieClicker.house.design.HouseDesignParser;
import dev.casperrs.cookieClicker.house.design.HouseLayout;
import dev.casperrs.cookieClicker.plot.Plot;
import dev.casperrs.cookieClicker.plot.PlotRegistry;
import dev.casperrs.cookieClicker.plot.RegionShape;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.type.Wall;
import org.bukkit.configuration.InvalidConfigurationException;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Plans a {@code /bakery plot setup} on the main thread without changing the world: {@link #check} validates the
 * files and the region, {@link #plan} (after the previous setup was undone) measures the ground and lists every
 * block of the house, fence, belt frame and decor, plus the plot's new points.
 */
final class SetupPlanner {

    static final int MAX_GROUND_SPREAD = 2;
    /** The fence follows the ground per column, but never more than this far from the layout's ground. */
    private static final int FENCE_GROUND_SLACK = 3;

    private final Logger log;
    private final PlotRegistry plots;
    private final IntSupplier maxBlocks;
    private final Supplier<Map<String, Double>> footprints;

    /**
     * Everything {@link #check} verified; {@code ax}/{@code az} is the layout's corner, centred on the region; the
     * {@code old} setup is undone first.
     */
    record Checked(Plot plot, HouseDesign design, HouseLayout layout, FenceStyle fence, Map<String, BlockData> known,
                   @Nullable SetupBackup old, World world, RegionShape region, int ax, int az) {

        String plotId() {
            return plot.id();
        }
    }

    SetupPlanner(Logger log, PlotRegistry plots, IntSupplier maxBlocks, Supplier<Map<String, Double>> footprints) {
        this.log = log;
        this.plots = plots;
        this.maxBlocks = maxBlocks;
        this.footprints = footprints;
    }

    Checked check(String plotId, String designName, SetupFiles.Inputs in) throws SetupRefusal {
        HouseDesign design = parseDesign(designName, in);
        HouseLayout layout = design.layout();
        if (layout == null) {
            throw new SetupRefusal("plot.setup.no-layout", Placeholder.unparsed("name", designName));
        }
        FenceStyle fence = parseFence(in);
        checkSize(design, layout);
        Map<String, BlockData> known = blockData(design, fence);
        SetupBackup old = parseOld(in);
        Plot plot = plots.get(plotId).orElse(null);
        World world = plot == null ? null : plots.world(plot).orElse(null);
        RegionShape region = plots.plotShape(plotId).orElse(null);
        if (plot == null || world == null || region == null) {
            throw new SetupRefusal("plot.setup.no-plot-region", Placeholder.unparsed("id", plotId));
        }
        int width = region.maxX() - region.minX() + 1;
        int depth = region.maxZ() - region.minZ() + 1;
        if (width < layout.sizeX() || depth < layout.sizeZ()) {
            throw new SetupRefusal("plot.setup.too-small", Placeholder.unparsed("name", design.name()),
                    Placeholder.unparsed("need", layout.sizeX() + " x " + layout.sizeZ()),
                    Placeholder.unparsed("have", width + " x " + depth));
        }
        int ax = region.minX() + (width - layout.sizeX()) / 2;
        int az = region.minZ() + (depth - layout.sizeZ()) / 2;
        if (!HousePlacer.loaded(world, new int[] {ax, world.getMinHeight(), az},
                new int[] {layout.sizeX(), 1, layout.sizeZ()}) || (old != null && !loaded(world, old))) {
            throw new SetupRefusal("plot.setup.not-loaded", Placeholder.unparsed("id", plotId));
        }
        return new Checked(plot, design, layout, fence, known, old, world, region, ax, az);
    }

    private static HouseDesign parseDesign(String designName, SetupFiles.Inputs in) throws SetupRefusal {
        HouseDesignParser.Result parsed = DesignFiles.parse(in.design(), map -> HouseDesignParser.parse(designName, map),
                problem -> new HouseDesignParser.Result(null, List.of(problem)));
        if (parsed.design() == null) {
            throw new SetupRefusal("house.design-invalid", Placeholder.unparsed("name", designName),
                    Placeholder.unparsed("problem", String.join("; ", parsed.problems())));
        }
        return parsed.design();
    }

    private static FenceStyle parseFence(SetupFiles.Inputs in) throws SetupRefusal {
        FenceStyle.Result parsed = DesignFiles.parse(in.fence(), FenceStyle::parse,
                problem -> new FenceStyle.Result(null, List.of(problem)));
        if (parsed.style() == null) {
            throw new SetupRefusal("plot.setup.fence-style-invalid",
                    Placeholder.unparsed("problem", String.join("; ", parsed.problems())));
        }
        return parsed.style();
    }

    private void checkSize(HouseDesign design, HouseLayout layout) throws SetupRefusal {
        long volume = (long) design.sizeX() * design.sizeY() * design.sizeZ();
        long area = (long) layout.sizeX() * layout.sizeZ();
        int max = maxBlocks.getAsInt();
        if (volume > max || area > max) {
            throw new SetupRefusal("house.too-big", Placeholder.unparsed("name", design.name()),
                    Placeholder.unparsed("count", Long.toString(Math.max(volume, area))),
                    Placeholder.unparsed("amount", Integer.toString(max)));
        }
    }

    private static Map<String, BlockData> blockData(HouseDesign design, FenceStyle fence) throws SetupRefusal {
        Map<String, BlockData> known = new HashMap<>();
        List<String> all = new ArrayList<>(design.palette().values());
        all.addAll(fence.allBlockData());
        for (String s : all) {
            try {
                known.computeIfAbsent(s, DesignBlocks::create);
            } catch (IllegalArgumentException e) {
                throw new SetupRefusal("plot.setup.failed", WorldOperation.problem("'" + s
                        + "' (design or fence style) is not valid block data"));
            }
        }
        return known;
    }

    private static @Nullable SetupBackup parseOld(SetupFiles.Inputs in) throws SetupRefusal {
        if (in.old() == null) {
            return null;
        }
        try {
            return SetupBackup.fromMap(DesignFiles.parseYaml(in.old().text()));
        } catch (InvalidConfigurationException | IllegalArgumentException e) {
            throw new SetupRefusal("plot.setup.failed", WorldOperation.problem("the previous setup backup "
                    + in.old().yml().getFileName() + " can't be read (" + e.getMessage() + "); move it away first"));
        }
    }

    /** After the previous setup was undone (so the ground is measured on the world as it was before it). */
    SetupPlan plan(Checked c, Plot before, SetupFiles.Inputs in) throws SetupRefusal {
        World world = c.world();
        int groundY = groundY(c);
        int y0 = groundY + 1;
        HouseDesign design = c.design();
        HouseLayout layout = c.layout();
        int[] houseAt = {c.ax() + layout.houseAt()[0], y0 + layout.houseAt()[1], c.az() + layout.houseAt()[2]};
        int[] corner = design.corner(houseAt); // ground layers included: the backup covers them
        int[] size = design.size();
        if (corner[1] < world.getMinHeight() || corner[1] + size[1] > world.getMaxHeight()) {
            throw new SetupRefusal("plot.setup.not-loaded", Placeholder.unparsed("id", c.plotId()));
        }
        int[] base = design.toWorld(houseAt, 0, 0, 0); // the house box starts above the design's ground layers
        Plot.Box houseBox = new Plot.Box(base[0], base[1], base[2], base[0] + size[0] - 1,
                base[1] + design.houseHeight() - 1, base[2] + size[2] - 1, design.groundDepth());
        byte[] structure = snapshot(c, corner, size);
        ChangeList changes = new ChangeList(world);
        for (HouseDesign.Cell cell : design.cells()) {
            int[] w = design.toWorld(houseAt, cell.x(), cell.y(), cell.z());
            changes.set(w[0], w[1], w[2], c.known().get(cell.blockData()).getAsString());
        }
        FenceLayout.Plan fence = planFence(c, y0, houseBox, changes);
        Plot.Belt belt = BeltFramePlanner.plan(log, layout, c.ax(), y0, c.az(), in.belt(), c.known(), changes);
        planDecor(c, y0, in, changes);
        Plot updated = withPoints(c, before, y0, houseAt, houseBox, fence, belt);
        SetupBackup backup = new SetupBackup(c.plotId(), design.name(), before.worldKey(), System.currentTimeMillis(),
                corner, size, changes.cells(), before);
        return new SetupPlan(design, houseAt, groundY, changes.changes(), updated, backup, structure);
    }

    /** The most common ground height over the layout inside the region; refused when it isn't flat enough. */
    private int groundY(Checked c) throws SetupRefusal {
        World world = c.world();
        RegionShape region = c.region();
        int regionY = Math.clamp(region.minY(), world.getMinHeight(), world.getMaxHeight() - 1);
        Map<Integer, Integer> counts = new HashMap<>();
        int low = Integer.MAX_VALUE;
        int high = Integer.MIN_VALUE;
        for (int x = c.ax(); x < c.ax() + c.layout().sizeX(); x++) {
            for (int z = c.az(); z < c.az() + c.layout().sizeZ(); z++) {
                if (region.contains().contains(x, regionY, z)) {
                    int g = ground(world, x, z);
                    counts.merge(g, 1, Integer::sum);
                    low = Math.min(low, g);
                    high = Math.max(high, g);
                }
            }
        }
        if (counts.isEmpty()) {
            throw new SetupRefusal("plot.setup.no-plot-region", Placeholder.unparsed("id", c.plotId()));
        }
        if (high - low > MAX_GROUND_SPREAD) {
            throw new SetupRefusal("plot.setup.not-flat", Placeholder.unparsed("low", Integer.toString(low)),
                    Placeholder.unparsed("high", Integer.toString(high)),
                    Placeholder.unparsed("amount", Integer.toString(MAX_GROUND_SPREAD)));
        }
        return counts.entrySet().stream()
                .max(Map.Entry.<Integer, Integer>comparingByValue().thenComparing(Map.Entry.comparingByKey()))
                .orElseThrow().getKey();
    }

    private byte[] snapshot(Checked c, int[] corner, int[] size) throws SetupRefusal {
        try {
            return BoxStructures.snapshot(c.world(), corner, size);
        } catch (IOException | RuntimeException e) {
            log.log(Level.SEVERE, "Could not back up the house area of plot " + c.plotId(), e);
            throw new SetupRefusal("plot.setup.failed", WorldOperation.problem(e.getMessage()));
        }
    }

    /** The fence on the layout's outer ring, gate at the entrance, each post on its column's ground near y0. */
    private static FenceLayout.Plan planFence(Checked c, int y0, Plot.Box houseBox, ChangeList changes) {
        World world = c.world();
        int ax = c.ax();
        int az = c.az();
        int bx = ax + c.layout().sizeX() - 1;
        int bz = az + c.layout().sizeZ() - 1;
        FenceLayout.Plan fence = FenceLayout.plan(ax, az, bx, bz,
                (x, z) -> x >= ax && x <= bx && z >= az && z <= bz,
                houseBox::contains,
                (x, z) -> {
                    int g = ground(world, x, z) + 1;
                    return Math.abs(g - y0) > FENCE_GROUND_SLACK ? y0 : g;
                },
                c.fence(), ax + c.layout().entrance()[0], az + c.layout().entrance()[1]);
        for (FenceLayout.Piece p : fence.pieces()) {
            changes.setUnlessTile(p.x(), p.y(), p.z(),
                    withFaces(c.known().get(p.blockData(c.fence())), p).getAsString());
        }
        return fence;
    }

    /** {@code houses/decor.yml} outside the {@link DecorKeepOut} columns; every problem is a warning. */
    private void planDecor(Checked c, int y0, SetupFiles.Inputs in, ChangeList changes) {
        if (in.decor() == null) {
            return;
        }
        BuildingSlots slots = in.slots() == null ? BuildingSlots.empty()
                : DesignFiles.parse(in.slots(), map -> BuildingSlots.parse(map).slots(), problem -> {
                    log.warning(DesignFiles.DIR + "/" + DesignFiles.BUILDING_SLOTS
                            + ": not valid YAML; decor ignores the slots");
                    return BuildingSlots.empty();
                });
        DecorKeepOut keepOut = DecorKeepOut.of(c.design(), slots, footprints.get());
        if (keepOut == null) {
            return;
        }
        Decor.Result parsed = DesignFiles.parse(in.decor(), Decor::parse,
                problem -> new Decor.Result(Decor.empty(), List.of(problem)));
        List<String> problems = new ArrayList<>(parsed.problems());
        for (Decor.Block b : keepOut.allowed(parsed.decor(), problems)) {
            BlockData data;
            try {
                data = c.known().computeIfAbsent(b.blockData(), DesignBlocks::create);
            } catch (IllegalArgumentException e) {
                problems.add("'" + b.blockData() + "' is not valid block data; skipped");
                continue;
            }
            changes.setUnlessTile(c.ax() + b.x(), y0 + b.y(), c.az() + b.z(), data.getAsString());
        }
        for (String p : problems) {
            log.warning(DesignFiles.DIR + "/" + DesignFiles.DECOR + ": " + p);
        }
    }

    private static Plot withPoints(Checked c, Plot before, int y0, int[] houseAt, Plot.Box houseBox,
                                   FenceLayout.Plan fence, Plot.@Nullable Belt belt) {
        HouseLayout layout = c.layout();
        HouseDesign design = c.design();
        int ax = c.ax();
        int az = c.az();
        Plot.Pedestal pedestal = spot(layout.pedestal(), ax, y0, az);
        Plot.Pedestal shop = spot(layout.shop(), ax, y0, az);
        Plot.Pedestal clicks = layout.clicks() == null ? null : spot(layout.clicks(), ax, y0, az);
        Plot.Viewpoint viewpoint = view(layout.viewpoint(), ax, y0, az);
        int[] e = fence.entrance();
        Plot.Point entrance = e == null ? null : new Plot.Point(e[0] + 0.5, e[1], e[2] + 0.5);
        Plot.Viewpoint camera = layout.camera() == null ? null : view(layout.camera(), ax, y0, az);
        return before.withLayout(viewpoint, pedestal, HousePlacer.cellCentres(design, houseAt, design.smoke()),
                HousePlacer.cellCentres(design, houseAt, design.steam()), entrance, shop, houseBox, belt, camera,
                clicks);
    }

    private static Plot.Viewpoint view(HouseLayout.View v, int ax, int y0, int az) {
        return new Plot.Viewpoint(ax + v.at()[0] + 0.5, y0 + v.at()[1], az + v.at()[2] + 0.5, v.yaw(), v.pitch());
    }

    /**
     * A layout spot's yaw is the way it faces (toward the visitors); a station's yaw is the opposite (the facing of
     * someone looking at it, like the admin who placed a pedestal by hand).
     */
    private static Plot.Pedestal spot(HouseLayout.Spot s, int ax, int y0, int az) {
        float yaw = ((s.yaw() + 180f) % 360f + 360f) % 360f;
        return new Plot.Pedestal(ax + s.at()[0] + 0.5, y0 + s.at()[1], az + s.at()[2] + 0.5,
                yaw >= 180f ? yaw - 360f : yaw);
    }

    /** Highest solid block of a column, ignoring foliage (grass, flowers, leaves, snow layers). */
    static int ground(World world, int x, int z) {
        int y = world.getHighestBlockYAt(x, z, HeightMap.WORLD_SURFACE);
        while (y > world.getMinHeight()) {
            Material type = world.getBlockAt(x, y, z).getType();
            if (type.isSolid() && !Tag.LEAVES.isTagged(type) && type != Material.SNOW) {
                return y;
            }
            y--;
        }
        return y;
    }

    /** Whether the house box and every cell of {@code backup} are in loaded chunks. */
    static boolean loaded(World world, SetupBackup backup) {
        if (!HousePlacer.loaded(world, backup.houseCorner(), backup.houseSize())) {
            return false;
        }
        for (HouseBackup.Cell cell : backup.cells()) {
            if (!world.isChunkLoaded(cell.x() >> 4, cell.z() >> 4)) {
                return false;
            }
        }
        return true;
    }

    /** Sets the fence connections a fence/wall/pane block supports; other blocks are returned as they are. */
    private static BlockData withFaces(BlockData base, FenceLayout.Piece piece) {
        if (piece.faces().isEmpty() || !(base instanceof MultipleFacing || base instanceof Wall)) {
            return base;
        }
        BlockData data = base.clone();
        for (FenceLayout.Face f : piece.faces()) {
            BlockFace face = BlockFace.valueOf(f.name());
            if (data instanceof MultipleFacing mf && mf.getAllowedFaces().contains(face)) {
                mf.setFace(face, true);
            } else if (data instanceof Wall wall) {
                wall.setHeight(face, Wall.Height.LOW);
            }
        }
        return data;
    }
}
