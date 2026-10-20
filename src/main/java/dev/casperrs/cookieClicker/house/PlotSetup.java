package dev.casperrs.cookieClicker.house;

import dev.casperrs.cookieClicker.config.YamlFiles;
import dev.casperrs.cookieClicker.plot.Plot;
import dev.casperrs.cookieClicker.plot.PlotRegistry;
import dev.casperrs.cookieClicker.plot.PlotService;
import dev.casperrs.cookieClicker.plot.RegionShape;
import dev.casperrs.cookieClicker.text.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.type.Wall;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.structure.Structure;
import org.bukkit.util.BlockVector;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.logging.Level;
import java.util.stream.Stream;

/**
 * {@code /bakery plot setup [design]} and {@code /bakery plot setup undo}: builds a whole plot from the design's fixed
 * {@link HouseLayout}, so the admin only draws the plot region. The layout square is centred on the region's bounding
 * box (x/z); the ground is found per column (highest solid, non-foliage block) and must be flat within
 * {@link #MAX_GROUND_SPREAD} blocks. Then: the house at {@code layout.house-at}, the fence on the layout's outer ring
 * with the gate at {@code layout.entrance}, and every point (pedestal, shop, viewpoint, smoke, steam, entrance, house
 * box) stored in plots.yml. A setup first undoes the plot's previous setup. Every replaced block is backed up
 * ({@code setup-backups/<plot>/}: structure of the house box + old block data of every changed block + the old
 * plots.yml entry) and the backup is on disk before the first block changes; undo restores the world exactly. Blocks
 * with tile entities are never replaced by the fence. Files on IO, world on main, one operation at a time.
 */
public final class PlotSetup {

    public static final String BACKUP_DIR = "setup-backups";
    public static final String DEFAULT_DESIGN = "factory";
    public static final String FENCE_STYLE = "fence.yml";
    public static final int MAX_GROUND_SPREAD = 2;
    /** The fence follows the ground per column, but never more than this far from the layout's ground. */
    private static final int FENCE_GROUND_SLACK = 3;

    private final JavaPlugin plugin;
    private final PlotRegistry plots;
    private final Consumer<Plot> store;
    private final Messages messages;
    private final Executor io;
    private final Executor main;
    private final IntSupplier maxBlocks;
    private final OperationGate gate = new OperationGate();

    /**
     * @param store     saves a changed plot everywhere (registry, stations, plots.yml): {@code PlotService#update}
     * @param maxBlocks {@code house.max-blocks}: the largest design box and layout area accepted
     */
    public PlotSetup(JavaPlugin plugin, PlotRegistry plots, Consumer<Plot> store, Messages messages, Executor io,
                     Executor main, IntSupplier maxBlocks) {
        this.plugin = plugin;
        this.plots = plots;
        this.store = store;
        this.messages = messages;
        this.io = io;
        this.main = main;
        this.maxBlocks = maxBlocks;
    }

    private record Op(CommandSender sender, long token) {
    }

    /** What the IO thread read for a setup. */
    private record Inputs(@Nullable String design, @Nullable String style, @Nullable Path oldYml,
                          @Nullable String oldText, byte @Nullable [] oldNbt) {
    }

    // ---- setup ----------------------------------------------------------------------------------------------------

    public void setup(CommandSender sender, Plot plot, String designName) {
        if (!HousePlacer.validDesignName(designName)) {
            messages.send(sender, "house.bad-design-name", Placeholder.unparsed("name", designName));
            return;
        }
        if (plots.plotShape(plot.id()).isEmpty()) {
            messages.send(sender, "plot.setup.no-plot-region", Placeholder.unparsed("id", plot.id()));
            return;
        }
        Op op = begin(sender);
        if (op == null) {
            return;
        }
        String plotId = plot.id();
        Path data = plugin.getDataFolder().toPath();
        Path dir = data.resolve(BACKUP_DIR).resolve(plotId);
        submit(op, () -> {
            Inputs in;
            try {
                Path oldYml = newest(dir);
                Path oldNbt = oldYml == null ? null : sibling(oldYml, ".nbt");
                in = new Inputs(read(data, designName + ".yml"), read(data, FENCE_STYLE), oldYml,
                        oldYml == null ? null : Files.readString(oldYml, StandardCharsets.UTF_8),
                        oldNbt != null && Files.isRegularFile(oldNbt) ? Files.readAllBytes(oldNbt) : null);
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Could not read the setup files of plot " + plotId, e);
                finish(op, "plot.setup.failed", Placeholder.unparsed("problem", String.valueOf(e.getMessage())));
                return;
            }
            if (in.design() == null) {
                finish(op, "house.design-not-found", Placeholder.unparsed("name", designName));
                return;
            }
            if (in.style() == null) {
                finish(op, "plot.setup.no-fence-style");
                return;
            }
            main.execute(() -> step(op, () -> build(op, plotId, designName, in)));
        });
    }

    /** Data folder {@code houses/<file>} first (admins can override), then the jar's copy. Null = none. IO thread. */
    private @Nullable String read(Path dataFolder, String file) throws IOException {
        Path own = dataFolder.resolve(HousePlacer.DESIGN_DIR).resolve(file);
        if (Files.isRegularFile(own)) {
            return Files.readString(own, StandardCharsets.UTF_8);
        }
        try (InputStream in = plugin.getResource(HousePlacer.DESIGN_DIR + "/" + file)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Main thread: validate, undo the previous setup, find the ground, plan, snapshot; then write the backup on IO. */
    private void build(Op op, String plotId, String designName, Inputs in) {
        HouseDesignParser.Result parsed;
        FenceStyle.Result styleParsed;
        try {
            parsed = HouseDesignParser.parse(designName, yaml(in.design()));
        } catch (InvalidConfigurationException e) {
            parsed = new HouseDesignParser.Result(null, List.of("not valid YAML: " + e.getMessage()));
        }
        if (parsed.design() == null) {
            finishNow(op, "house.design-invalid", Placeholder.unparsed("name", designName),
                    Placeholder.unparsed("problem", String.join("; ", parsed.problems())));
            return;
        }
        HouseDesign design = parsed.design();
        HouseLayout layout = design.layout();
        if (layout == null) {
            finishNow(op, "plot.setup.no-layout", Placeholder.unparsed("name", designName));
            return;
        }
        try {
            styleParsed = FenceStyle.parse(yaml(in.style()));
        } catch (InvalidConfigurationException e) {
            styleParsed = new FenceStyle.Result(null, List.of("not valid YAML: " + e.getMessage()));
        }
        if (styleParsed.style() == null) {
            finishNow(op, "plot.setup.fence-style-invalid",
                    Placeholder.unparsed("problem", String.join("; ", styleParsed.problems())));
            return;
        }
        FenceStyle style = styleParsed.style();
        long volume = (long) design.sizeX() * design.sizeY() * design.sizeZ();
        long area = (long) layout.sizeX() * layout.sizeZ();
        if (volume > maxBlocks.getAsInt() || area > maxBlocks.getAsInt()) {
            finishNow(op, "house.too-big", Placeholder.unparsed("name", design.name()),
                    Placeholder.unparsed("count", Long.toString(Math.max(volume, area))),
                    Placeholder.unparsed("amount", Integer.toString(maxBlocks.getAsInt())));
            return;
        }
        Map<String, BlockData> known = new HashMap<>();
        List<String> blockData = new ArrayList<>(design.palette().values());
        blockData.addAll(style.allBlockData());
        for (String s : blockData) {
            try {
                known.computeIfAbsent(s, Bukkit::createBlockData);
            } catch (IllegalArgumentException e) {
                finishNow(op, "plot.setup.failed", Placeholder.unparsed("problem", "'" + s
                        + "' (design or fence style) is not valid block data"));
                return;
            }
        }

        SetupBackup old = null;
        if (in.oldText() != null) {
            try {
                YamlConfiguration y = new YamlConfiguration();
                y.loadFromString(in.oldText());
                old = SetupBackup.fromMap(YamlFiles.toMap(y));
            } catch (InvalidConfigurationException | IllegalArgumentException e) {
                finishNow(op, "plot.setup.failed", Placeholder.unparsed("problem", "the previous setup backup "
                        + in.oldYml().getFileName() + " can't be read (" + e.getMessage() + "); move it away first"));
                return;
            }
        }

        Plot plot = plots.get(plotId).orElse(null);
        World world = plot == null ? null : plots.world(plot).orElse(null);
        RegionShape region = plots.plotShape(plotId).orElse(null);
        if (plot == null || world == null || region == null) {
            finishNow(op, "plot.setup.no-plot-region", Placeholder.unparsed("id", plotId));
            return;
        }
        int width = region.maxX() - region.minX() + 1;
        int depth = region.maxZ() - region.minZ() + 1;
        if (width < layout.sizeX() || depth < layout.sizeZ()) {
            finishNow(op, "plot.setup.too-small", Placeholder.unparsed("name", design.name()),
                    Placeholder.unparsed("need", layout.sizeX() + " x " + layout.sizeZ()),
                    Placeholder.unparsed("have", width + " x " + depth));
            return;
        }
        int ax = region.minX() + (width - layout.sizeX()) / 2;
        int az = region.minZ() + (depth - layout.sizeZ()) / 2;
        int bx = ax + layout.sizeX() - 1;
        int bz = az + layout.sizeZ() - 1;
        if (!HousePlacer.loaded(world, new int[] {ax, world.getMinHeight(), az},
                new int[] {layout.sizeX(), 1, layout.sizeZ()})
                || (old != null && !cellsLoaded(world, old))) {
            finishNow(op, "plot.setup.not-loaded", Placeholder.unparsed("id", plotId));
            return;
        }

        if (old != null) { // setup again: put the old plot back first (the ground is measured afterwards)
            restore(world, old, in.oldNbt());
            store.accept(old.previousPlot());
            plot = old.previousPlot();
        }
        Plot before = plot;

        // ground: the most common highest solid block over the layout footprint inside the region
        int regionY = Math.clamp(region.minY(), world.getMinHeight(), world.getMaxHeight() - 1);
        Map<Integer, Integer> counts = new HashMap<>();
        int low = Integer.MAX_VALUE;
        int high = Integer.MIN_VALUE;
        for (int x = ax; x <= bx; x++) {
            for (int z = az; z <= bz; z++) {
                if (!region.contains().contains(x, regionY, z)) {
                    continue;
                }
                int g = ground(world, x, z);
                counts.merge(g, 1, Integer::sum);
                low = Math.min(low, g);
                high = Math.max(high, g);
            }
        }
        if (counts.isEmpty()) {
            abandon(op, old, in, "plot.setup.no-plot-region", Placeholder.unparsed("id", plotId));
            return;
        }
        if (high - low > MAX_GROUND_SPREAD) {
            abandon(op, old, in, "plot.setup.not-flat", Placeholder.unparsed("low", Integer.toString(low)),
                    Placeholder.unparsed("high", Integer.toString(high)),
                    Placeholder.unparsed("amount", Integer.toString(MAX_GROUND_SPREAD)));
            return;
        }
        int groundY = counts.entrySet().stream()
                .max(Map.Entry.<Integer, Integer>comparingByValue().thenComparing(Map.Entry.comparingByKey()))
                .orElseThrow().getKey();
        int y0 = groundY + 1;

        // house
        int[] houseAt = {ax + layout.houseAt()[0], y0 + layout.houseAt()[1], az + layout.houseAt()[2]};
        int[] corner = design.toWorld(houseAt, 0, 0, 0);
        int[] size = {design.sizeX(), design.sizeY(), design.sizeZ()};
        if (corner[1] < world.getMinHeight() || corner[1] + size[1] > world.getMaxHeight()) {
            abandon(op, old, in, "plot.setup.not-loaded", Placeholder.unparsed("id", plotId));
            return;
        }
        Plot.Box houseBox = new Plot.Box(corner[0], corner[1], corner[2], corner[0] + size[0] - 1,
                corner[1] + size[1] - 1, corner[2] + size[2] - 1);
        byte[] structureBytes;
        try {
            Structure snapshot = Bukkit.getStructureManager().createStructure();
            snapshot.fill(new Location(world, corner[0], corner[1], corner[2]),
                    new BlockVector(size[0], size[1], size[2]), false);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Bukkit.getStructureManager().saveStructure(out, snapshot);
            structureBytes = out.toByteArray();
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not back up the house area of plot " + plotId, e);
            abandon(op, old, in, "plot.setup.failed", Placeholder.unparsed("problem", String.valueOf(e.getMessage())));
            return;
        }
        List<HouseBackup.Cell> cells = new ArrayList<>();
        List<HouseBackup.Change> changes = new ArrayList<>();
        for (HouseDesign.Cell c : design.cells()) {
            int[] w = design.toWorld(houseAt, c.x(), c.y(), c.z());
            change(world, w[0], w[1], w[2], known.get(c.blockData()).getAsString(), cells, changes);
        }

        // fence on the outer ring, gate at the entrance, per-column ground near y0
        FenceLayout.Plan fence = FenceLayout.plan(ax, az, bx, bz,
                (x, z) -> x >= ax && x <= bx && z >= az && z <= bz,
                houseBox::contains,
                (x, z) -> {
                    int g = ground(world, x, z) + 1;
                    return Math.abs(g - y0) > FENCE_GROUND_SLACK ? y0 : g;
                },
                style, ax + layout.entrance()[0], az + layout.entrance()[1]);
        for (FenceLayout.Piece p : fence.pieces()) {
            if (p.y() < world.getMinHeight() || p.y() >= world.getMaxHeight()
                    || world.getBlockAt(p.x(), p.y(), p.z()).getState(false) instanceof TileState) {
                continue; // never replace a chest, sign, ...
            }
            change(world, p.x(), p.y(), p.z(), withFaces(known.get(p.blockData(style)), p).getAsString(), cells,
                    changes);
        }

        // points
        Plot.Pedestal pedestal = spot(layout.pedestal(), ax, y0, az);
        Plot.Pedestal shop = spot(layout.shop(), ax, y0, az);
        double[] v = layout.viewpoint().at();
        Plot.Viewpoint viewpoint = new Plot.Viewpoint(ax + v[0] + 0.5, y0 + v[1], az + v[2] + 0.5,
                layout.viewpoint().yaw(), layout.viewpoint().pitch());
        int[] e = fence.entrance();
        Plot.Point entrance = e == null ? null : new Plot.Point(e[0] + 0.5, e[1], e[2] + 0.5);
        Plot updated = before.withLayout(viewpoint, pedestal, HousePlacer.cellCentres(design, houseAt, design.smoke()),
                HousePlacer.cellCentres(design, houseAt, design.steam()), entrance, shop, houseBox);

        SetupBackup backup = new SetupBackup(plotId, design.name(), before.worldKey(), System.currentTimeMillis(),
                corner, size, cells, before);
        String text = toYaml(backup.toMap());
        Path dir = plugin.getDataFolder().toPath().resolve(BACKUP_DIR).resolve(plotId);
        Path yml = dir.resolve(backup.createdAt() + ".yml");
        Path nbt = dir.resolve(backup.createdAt() + ".nbt");
        submit(op, () -> {
            try {
                deleteOld(in);
                Files.createDirectories(dir);
                Files.write(nbt, structureBytes);
                Files.writeString(yml, text, StandardCharsets.UTF_8);
            } catch (IOException ex) {
                plugin.getLogger().log(Level.SEVERE, "Could not write the setup backup of plot " + plotId
                        + "; nothing was built", ex);
                deleteQuietly(nbt, yml);
                finish(op, "plot.setup.failed", Placeholder.unparsed("problem",
                        "could not save the undo backup (" + ex.getMessage() + ")"));
                return;
            }
            main.execute(() -> {
                if (!gate.valid(op.token())) { // cancelled by a reload: nothing built, the backup is useless
                    deleteLater(nbt, yml);
                    return;
                }
                step(op, () -> apply(op, world, design, houseAt, changes, updated, groundY, yml, nbt));
            });
        });
    }

    /** Main thread, once the backup is on disk: set the blocks, sign text and the plot's points. */
    private void apply(Op op, World world, HouseDesign design, int[] houseAt, List<HouseBackup.Change> changes,
                       Plot updated, int groundY, Path yml, Path nbt) {
        for (HouseBackup.Change c : changes) {
            if (!world.isChunkLoaded(c.x() >> 4, c.z() >> 4)) {
                deleteLater(nbt, yml);
                finishNow(op, "plot.setup.not-loaded", Placeholder.unparsed("id", updated.id()));
                return;
            }
        }
        HouseBackup.BlockAccess access = worldAccess(world);
        for (HouseBackup.Change c : changes) {
            access.set(c.x(), c.y(), c.z(), c.newData());
        }
        HousePlacer.writeSigns(plugin, world, design, houseAt);
        store.accept(updated);
        Plot.Pedestal p = updated.pedestal();
        Plot.Point e = updated.entrance();
        finishNow(op, "plot.setup.done", Placeholder.unparsed("id", updated.id()),
                Placeholder.unparsed("name", design.name()),
                Placeholder.unparsed("ground", Integer.toString(groundY)),
                Placeholder.unparsed("count", Integer.toString(changes.size())),
                Placeholder.unparsed("pedestal", p == null ? "-" : xyz(p.x(), p.y(), p.z())),
                Placeholder.unparsed("entrance", e == null ? "-" : xyz(e.x(), e.y(), e.z())),
                Placeholder.unparsed("smoke", Integer.toString(updated.smoke().size())),
                Placeholder.unparsed("steam", Integer.toString(updated.steam().size())));
    }

    /**
     * After the old setup was already restored, a check failed: the world and plots.yml now match the state before
     * the old setup, so its backup files go; then reply.
     */
    private void abandon(Op op, @Nullable SetupBackup old, Inputs in, String path, TagResolver... resolvers) {
        if (old == null) {
            finishNow(op, path, resolvers);
            return;
        }
        submit(op, () -> {
            try {
                deleteOld(in);
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Could not delete the used setup backup " + in.oldYml(), e);
            }
            finish(op, path, resolvers);
        });
    }

    private static void deleteOld(Inputs in) throws IOException {
        if (in.oldYml() != null) {
            Files.deleteIfExists(sibling(in.oldYml(), ".nbt"));
            Files.deleteIfExists(in.oldYml());
        }
    }

    private static void change(World world, int x, int y, int z, String data, List<HouseBackup.Cell> cells,
                               List<HouseBackup.Change> changes) {
        String now = world.getBlockData(x, y, z).getAsString();
        if (!now.equals(data)) {
            cells.add(new HouseBackup.Cell(x, y, z, now));
            changes.add(new HouseBackup.Change(x, y, z, data));
        }
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
            Block b = world.getBlockAt(x, y, z);
            Material type = b.getType();
            if (type.isSolid() && !Tag.LEAVES.isTagged(type) && type != Material.SNOW) {
                return y;
            }
            y--;
        }
        return y;
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

    // ---- undo -----------------------------------------------------------------------------------------------------

    public void undo(CommandSender sender, Plot plot) {
        Op op = begin(sender);
        if (op == null) {
            return;
        }
        String plotId = plot.id();
        Path dir = plugin.getDataFolder().toPath().resolve(BACKUP_DIR).resolve(plotId);
        submit(op, () -> {
            Inputs in;
            try {
                Path yml = newest(dir);
                if (yml == null) {
                    finish(op, "plot.setup.nothing-to-undo", Placeholder.unparsed("id", plotId));
                    return;
                }
                Path nbt = sibling(yml, ".nbt");
                in = new Inputs(null, null, yml, Files.readString(yml, StandardCharsets.UTF_8),
                        Files.isRegularFile(nbt) ? Files.readAllBytes(nbt) : null);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not read the setup backups of plot " + plotId, e);
                finish(op, "plot.setup.undo-failed", Placeholder.unparsed("problem", String.valueOf(e.getMessage())));
                return;
            }
            main.execute(() -> step(op, () -> undoNow(op, plotId, in)));
        });
    }

    private void undoNow(Op op, String plotId, Inputs in) {
        SetupBackup backup;
        try {
            YamlConfiguration y = new YamlConfiguration();
            y.loadFromString(in.oldText());
            backup = SetupBackup.fromMap(YamlFiles.toMap(y));
        } catch (InvalidConfigurationException | IllegalArgumentException e) {
            finishNow(op, "plot.setup.undo-failed", Placeholder.unparsed("problem",
                    in.oldYml().getFileName() + ": " + e.getMessage()));
            return;
        }
        World world = PlotService.worldByKey(backup.worldKey()).orElse(null);
        if (world == null || !cellsLoaded(world, backup)) {
            finishNow(op, "plot.setup.not-loaded", Placeholder.unparsed("id", plotId));
            return;
        }
        restore(world, backup, in.oldNbt());
        store.accept(backup.previousPlot());
        int count = backup.cells().size();
        submit(op, () -> {
            try {
                deleteOld(in);
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Could not delete the used setup backup " + in.oldYml(), e);
            }
            finish(op, "plot.setup.undone", Placeholder.unparsed("id", plotId),
                    Placeholder.unparsed("count", Integer.toString(count)));
        });
    }

    /** Block data of every changed block, then the house box structure (brings back tile contents). Main thread. */
    private void restore(World world, SetupBackup backup, byte @Nullable [] nbt) {
        backup.restoreCells(worldAccess(world));
        if (nbt == null) {
            return;
        }
        try {
            Structure structure = Bukkit.getStructureManager().loadStructure(new ByteArrayInputStream(nbt));
            int[] c = backup.houseCorner();
            structure.place(new Location(world, c[0], c[1], c[2]), false, StructureRotation.NONE, Mirror.NONE, 0, 1f,
                    new Random());
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Could not place the house structure backup of plot "
                    + backup.plotId() + "; block data was restored, tile contents are lost", e);
        }
    }

    // ---- helpers --------------------------------------------------------------------------------------------------

    private static Map<String, Object> yaml(String text) throws InvalidConfigurationException {
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString(text);
        return YamlFiles.toMap(y);
    }

    private static boolean cellsLoaded(World world, SetupBackup b) {
        int[] c = b.houseCorner();
        int[] s = b.houseSize();
        if (!HousePlacer.loaded(world, c, s)) {
            return false;
        }
        for (HouseBackup.Cell cell : b.cells()) {
            if (!world.isChunkLoaded(cell.x() >> 4, cell.z() >> 4)) {
                return false;
            }
        }
        return true;
    }

    private static HouseBackup.BlockAccess worldAccess(World world) {
        Map<String, BlockData> known = new HashMap<>();
        return new HouseBackup.BlockAccess() {
            @Override
            public String get(int x, int y, int z) {
                return world.getBlockData(x, y, z).getAsString();
            }

            @Override
            public void set(int x, int y, int z, String blockData) {
                BlockData data = known.computeIfAbsent(blockData, Bukkit::createBlockData);
                world.getBlockAt(x, y, z).setBlockData(data, false);
            }
        };
    }

    private static @Nullable Path newest(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return null;
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(p -> p.getFileName().toString().matches("\\d+\\.yml"))
                    .max((a, b) -> Long.compare(stamp(a), stamp(b)))
                    .orElse(null);
        }
    }

    private static long stamp(Path p) {
        String n = p.getFileName().toString();
        return Long.parseLong(n.substring(0, n.length() - ".yml".length()));
    }

    private static Path sibling(Path yml, String ext) {
        String n = yml.getFileName().toString();
        return yml.resolveSibling(n.substring(0, n.length() - ".yml".length()) + ext);
    }

    private static String toYaml(Map<String, Object> map) {
        YamlConfiguration yaml = new YamlConfiguration();
        map.forEach(yaml::set);
        return yaml.saveToString();
    }

    private static String xyz(double x, double y, double z) {
        return String.format(Locale.ROOT, "%.1f %.1f %.1f", x, y, z);
    }

    /** IO thread. */
    private void deleteQuietly(Path... files) {
        for (Path f : files) {
            try {
                Files.deleteIfExists(f);
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Could not delete " + f, e);
            }
        }
    }

    private void deleteLater(Path... files) {
        try {
            io.execute(() -> deleteQuietly(files));
        } catch (RuntimeException e) {
            plugin.getLogger().fine("Left a cancelled setup backup behind");
        }
    }

    // ---- one operation at a time ------------------------------------------------------------------------------------

    private @Nullable Op begin(CommandSender sender) {
        long token = gate.begin();
        if (token == OperationGate.REFUSED) {
            messages.send(sender, "plot.setup.busy");
            return null;
        }
        return new Op(sender, token);
    }

    /** Set by {@link #submit} while a {@link #step} runs: the operation continues on the IO thread. */
    private boolean handedOff;

    /** One main-thread step; a stale token runs nothing. Ends the operation unless the step handed it to IO. */
    private void step(Op op, Runnable body) {
        if (!gate.valid(op.token())) {
            return;
        }
        handedOff = false;
        boolean completed = false;
        try {
            body.run();
            completed = true;
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Plot setup failed", e);
            messages.send(op.sender(), "plot.setup.failed", Placeholder.unparsed("problem", String.valueOf(e.getMessage())));
        } finally {
            if (!completed || !handedOff) {
                gate.end(op.token());
            }
            handedOff = false;
        }
    }

    /** Continues on IO; an exception there replies and ends the operation, a rejected task ends it right away. */
    private void submit(Op op, Runnable task) {
        try {
            io.execute(() -> {
                try {
                    task.run();
                } catch (RuntimeException e) {
                    plugin.getLogger().log(Level.SEVERE, "Plot setup failed on the IO thread", e);
                    finish(op, "plot.setup.failed", Placeholder.unparsed("problem", String.valueOf(e.getMessage())));
                }
            });
            handedOff = true;
        } catch (RuntimeException e) {
            gate.end(op.token());
            messages.send(op.sender(), "plot.setup.failed", Placeholder.unparsed("problem", "server is shutting down"));
        }
    }

    private void finish(Op op, String path, TagResolver... resolvers) {
        main.execute(() -> finishNow(op, path, resolvers));
    }

    private void finishNow(Op op, String path, TagResolver... resolvers) {
        if (!gate.valid(op.token())) {
            return;
        }
        gate.end(op.token());
        messages.send(op.sender(), path, resolvers);
    }

    /** Reload / disable: cancels the running operation (its late callbacks change nothing). */
    public void reset() {
        gate.reset();
    }
}
