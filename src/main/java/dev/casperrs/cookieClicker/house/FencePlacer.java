package dev.casperrs.cookieClicker.house;

import dev.casperrs.cookieClicker.config.YamlFiles;
import dev.casperrs.cookieClicker.plot.Plot;
import dev.casperrs.cookieClicker.plot.PlotFile;
import dev.casperrs.cookieClicker.plot.PlotRegistry;
import dev.casperrs.cookieClicker.plot.PlotService;
import dev.casperrs.cookieClicker.plot.RegionShape;
import dev.casperrs.cookieClicker.text.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.type.Wall;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.IntSupplier;
import java.util.logging.Level;
import java.util.stream.Stream;

/**
 * {@code /bakery plot fence place} and {@code /bakery plot fence undo}: the wooden plot fence along the inside edge of
 * the plot region, style from {@code houses/fence.yml}, with the gate at the border point nearest the admin. Place
 * first undoes the plot's previous fence, then backs up every block it replaces ({@code fence-backups/<plot>/}) and
 * writes that backup <b>before</b> changing a block; undo restores the newest backup. Blocks inside the house region
 * and blocks with tile entities (chests, signs) are never touched. Files on the IO thread, world on main; one fence
 * operation at a time ({@link OperationGate}, reset on reload/disable).
 */
public final class FencePlacer {

    public static final String BACKUP_DIR = "fence-backups";
    public static final String STYLE_FILE = "fence.yml";

    private final JavaPlugin plugin;
    private final PlotRegistry plots;
    private final PlotFile plotFile;
    private final Messages messages;
    private final Executor io;
    private final Executor main;
    private final IntSupplier maxArea;
    private final OperationGate gate = new OperationGate();

    /** @param maxArea {@code house.max-blocks}: the largest plot box (x * z) the fence accepts */
    public FencePlacer(JavaPlugin plugin, PlotRegistry plots, PlotFile plotFile, Messages messages, Executor io,
                       Executor main, IntSupplier maxArea) {
        this.plugin = plugin;
        this.plots = plots;
        this.plotFile = plotFile;
        this.messages = messages;
        this.io = io;
        this.main = main;
        this.maxArea = maxArea;
    }

    private record Op(CommandSender sender, long token) {
    }

    // ---- place ----------------------------------------------------------------------------------------------------

    public void place(Player admin, Plot plot) {
        String plotId = plot.id();
        Optional<RegionShape> shape = plots.plotShape(plotId);
        if (shape.isEmpty()) {
            messages.send(admin, "plot.fence.no-plot-region", Placeholder.unparsed("id", plotId));
            return;
        }
        long area = (long) (shape.get().maxX() - shape.get().minX() + 1) * (shape.get().maxZ() - shape.get().minZ() + 1);
        if (area > maxArea.getAsInt()) {
            messages.send(admin, "plot.fence.too-big", Placeholder.unparsed("count", Long.toString(area)),
                    Placeholder.unparsed("amount", Integer.toString(maxArea.getAsInt())));
            return;
        }
        Op op = begin(admin);
        if (op == null) {
            return;
        }
        int adminX = admin.getLocation().getBlockX();
        int adminZ = admin.getLocation().getBlockZ();
        Path dataFolder = plugin.getDataFolder().toPath();
        Path dir = dataFolder.resolve(BACKUP_DIR).resolve(plotId);
        submit(op, () -> {
            String style;
            Path oldFile;
            String oldText;
            try {
                style = readStyle(dataFolder);
                oldFile = newest(dir);
                oldText = oldFile == null ? null : Files.readString(oldFile, StandardCharsets.UTF_8);
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Could not read the fence style or backups of plot " + plotId, e);
                finish(op, "plot.fence.failed", Placeholder.unparsed("problem", String.valueOf(e.getMessage())));
                return;
            }
            if (style == null) {
                finish(op, "plot.fence.style-not-found");
                return;
            }
            main.execute(() -> step(op, () -> build(op, plotId, style, oldFile, oldText, adminX, adminZ)));
        });
    }

    /** Data folder {@code houses/fence.yml} first, then the jar's copy. Null = none. IO thread. */
    private @Nullable String readStyle(Path dataFolder) throws IOException {
        Path own = dataFolder.resolve(HousePlacer.DESIGN_DIR).resolve(STYLE_FILE);
        if (Files.isRegularFile(own)) {
            return Files.readString(own, StandardCharsets.UTF_8);
        }
        try (InputStream in = plugin.getResource(HousePlacer.DESIGN_DIR + "/" + STYLE_FILE)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Main thread: undo the old fence, plan the new one, capture the backup, then write it on IO. */
    private void build(Op op, String plotId, String styleText, @Nullable Path oldFile, @Nullable String oldText,
                       int adminX, int adminZ) {
        FenceStyle.Result parsed;
        try {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.loadFromString(styleText);
            parsed = FenceStyle.parse(YamlFiles.toMap(yaml));
        } catch (InvalidConfigurationException e) {
            parsed = new FenceStyle.Result(null, List.of("not valid YAML: " + e.getMessage()));
        }
        if (parsed.style() == null) {
            finishNow(op, "plot.fence.style-invalid", Placeholder.unparsed("problem", String.join("; ", parsed.problems())));
            return;
        }
        FenceStyle style = parsed.style();
        Map<String, BlockData> resolved = new HashMap<>();
        List<String> all = new ArrayList<>(List.of(style.post(), style.rail(), style.corner(), style.gateLeft(),
                style.gateRight()));
        all.addAll(style.arch());
        if (style.topper() != null) {
            all.add(style.topper());
        }
        for (String s : all) {
            try {
                resolved.putIfAbsent(s, Bukkit.createBlockData(s));
            } catch (IllegalArgumentException e) {
                finishNow(op, "plot.fence.style-invalid", Placeholder.unparsed("problem", "'" + s
                        + "' is not valid block data"));
                return;
            }
        }

        FenceBackup old = null;
        if (oldText != null) {
            try {
                old = parseBackup(oldText);
            } catch (InvalidConfigurationException | IllegalArgumentException e) {
                finishNow(op, "plot.fence.failed", Placeholder.unparsed("problem", "the previous fence backup "
                        + oldFile.getFileName() + " can't be read (" + e.getMessage() + "); move it away first"));
                return;
            }
        }

        Plot plot = plots.get(plotId).orElse(null);
        World world = plot == null ? null : plots.world(plot).orElse(null);
        RegionShape shape = plots.plotShape(plotId).orElse(null);
        if (plot == null || world == null || shape == null) {
            finishNow(op, "plot.fence.no-plot-region", Placeholder.unparsed("id", plotId));
            return;
        }
        if (!boxLoaded(world, shape.minX(), shape.minZ(), shape.maxX(), shape.maxZ())
                || (old != null && !cellsLoaded(world, old.cells()))) {
            finishNow(op, "plot.fence.not-loaded", Placeholder.unparsed("id", plotId));
            return;
        }

        HouseBackup.BlockAccess access = worldAccess(world);
        if (old != null) {
            old.restore(access); // placing again first undoes the previous fence (its file is deleted below)
        }
        Plot.Point previousEntrance = old != null ? old.previousEntrance() : plot.entrance();

        RegionShape house = plots.houseShape(plotId).orElse(null);
        int minY = Math.max(shape.minY(), world.getMinHeight());
        int maxY = Math.min(shape.maxY(), world.getMaxHeight() - 1);
        int y0 = shape.minY();
        FenceLayout.Plan plan = FenceLayout.plan(shape.minX(), shape.minZ(), shape.maxX(), shape.maxZ(),
                (x, z) -> shape.contains().contains(x, y0, z),
                house == null ? (x, y, z) -> false : house.contains()::contains,
                (x, z) -> {
                    int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1;
                    return y < minY || y > maxY ? FenceLayout.SKIP : y;
                },
                style, adminX, adminZ);

        List<HouseBackup.Cell> cells = new ArrayList<>();
        List<HouseBackup.Change> changes = new ArrayList<>();
        for (FenceLayout.Piece p : plan.pieces()) {
            if (p.y() < world.getMinHeight() || p.y() >= world.getMaxHeight()) {
                continue;
            }
            Block block = world.getBlockAt(p.x(), p.y(), p.z());
            if (block.getState(false) instanceof TileState) {
                continue; // never replace a chest, sign, ...: undo could not bring its contents back
            }
            String data = withFaces(resolved.get(p.blockData(style)), p).getAsString();
            String now = block.getBlockData().getAsString();
            if (!now.equals(data)) {
                cells.add(new HouseBackup.Cell(p.x(), p.y(), p.z(), now));
                changes.add(new HouseBackup.Change(p.x(), p.y(), p.z(), data));
            }
        }
        int[] e = plan.entrance();
        Plot.Point entrance = e == null ? null : new Plot.Point(e[0] + 0.5, e[1], e[2] + 0.5);
        FenceBackup backup = new FenceBackup(plotId, plot.worldKey(), System.currentTimeMillis(), previousEntrance,
                cells);
        String yaml = toYaml(backup.toMap());
        Path newFile = plugin.getDataFolder().toPath().resolve(BACKUP_DIR).resolve(plotId)
                .resolve(backup.createdAt() + ".yml");
        boolean hadOld = old != null;
        if (hadOld) { // the old fence is gone: forget its entrance now, even if the rest fails
            putPlot(plot.withEntrance(previousEntrance));
        }
        submit(op, () -> {
            try {
                if (oldFile != null) {
                    Files.deleteIfExists(oldFile);
                }
                if (changes.isEmpty()) {
                    finish(op, entrance == null ? "plot.fence.no-border" : "plot.fence.unchanged",
                            Placeholder.unparsed("id", plotId));
                    return;
                }
                Files.createDirectories(newFile.getParent());
                Files.writeString(newFile, yaml, StandardCharsets.UTF_8);
            } catch (IOException ex) {
                plugin.getLogger().log(Level.SEVERE, "Could not write the fence backup of plot " + plotId
                        + "; no fence was placed", ex);
                finish(op, "plot.fence.failed", Placeholder.unparsed("problem",
                        "could not save the undo backup (" + ex.getMessage() + ")"));
                return;
            }
            main.execute(() -> {
                if (!gate.valid(op.token())) { // cancelled by a reload: nothing placed, the backup is useless
                    deleteLater(newFile);
                    return;
                }
                step(op, () -> apply(op, plotId, world, changes, entrance, newFile));
            });
        });
    }

    /** Main thread, once the backup is on disk. */
    private void apply(Op op, String plotId, World world, List<HouseBackup.Change> changes,
                       Plot.@Nullable Point entrance, Path backupFile) {
        if (!cellsLoaded(world, changes.stream().map(c -> new HouseBackup.Cell(c.x(), c.y(), c.z(), "")).toList())) {
            deleteLater(backupFile);
            finishNow(op, "plot.fence.not-loaded", Placeholder.unparsed("id", plotId));
            return;
        }
        HouseBackup.BlockAccess access = worldAccess(world);
        for (HouseBackup.Change c : changes) {
            access.set(c.x(), c.y(), c.z(), c.newData());
        }
        plots.get(plotId).ifPresent(p -> putPlot(p.withEntrance(entrance)));
        finishNow(op, "plot.fence.placed", Placeholder.unparsed("id", plotId),
                Placeholder.unparsed("count", Integer.toString(changes.size())),
                Placeholder.unparsed("x", entrance == null ? "?" : fmt(entrance.x())),
                Placeholder.unparsed("y", entrance == null ? "?" : fmt(entrance.y())),
                Placeholder.unparsed("z", entrance == null ? "?" : fmt(entrance.z())));
    }

    /** Sets the fence connections a fence/wall/pane block supports; other blocks are returned as they are. */
    private static BlockData withFaces(BlockData base, FenceLayout.Piece piece) {
        if (piece.faces().isEmpty() && !(base instanceof MultipleFacing) && !(base instanceof Wall)) {
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
            Path file;
            String text;
            try {
                file = newest(dir);
                if (file == null) {
                    finish(op, "plot.fence.nothing-to-undo", Placeholder.unparsed("id", plotId));
                    return;
                }
                text = Files.readString(file, StandardCharsets.UTF_8);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not read the fence backups of plot " + plotId, e);
                finish(op, "plot.fence.undo-failed", Placeholder.unparsed("problem", String.valueOf(e.getMessage())));
                return;
            }
            main.execute(() -> step(op, () -> restore(op, plotId, file, text)));
        });
    }

    private void restore(Op op, String plotId, Path file, String text) {
        FenceBackup backup;
        try {
            backup = parseBackup(text);
        } catch (InvalidConfigurationException | IllegalArgumentException e) {
            finishNow(op, "plot.fence.undo-failed", Placeholder.unparsed("problem", file.getFileName() + ": "
                    + e.getMessage()));
            return;
        }
        World world = PlotService.worldByKey(backup.worldKey()).orElse(null);
        if (world == null || !cellsLoaded(world, backup.cells())) {
            finishNow(op, "plot.fence.not-loaded", Placeholder.unparsed("id", plotId));
            return;
        }
        backup.restore(worldAccess(world));
        plots.get(plotId).ifPresent(p -> putPlot(p.withEntrance(backup.previousEntrance())));
        int count = backup.cells().size();
        submit(op, () -> {
            try {
                Files.deleteIfExists(file);
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Could not delete the used fence backup " + file, e);
            }
            finish(op, "plot.fence.undone", Placeholder.unparsed("id", plotId),
                    Placeholder.unparsed("count", Integer.toString(count)));
        });
    }

    // ---- helpers --------------------------------------------------------------------------------------------------

    private void putPlot(Plot plot) {
        plots.put(plot, problem -> plugin.getLogger().warning(problem));
        plotFile.saveAsync(plots.all());
    }

    private static FenceBackup parseBackup(String text) throws InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(text);
        return FenceBackup.fromMap(YamlFiles.toMap(yaml));
    }

    private static boolean boxLoaded(World world, int minX, int minZ, int maxX, int maxZ) {
        for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
            for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
                if (!world.isChunkLoaded(cx, cz)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean cellsLoaded(World world, List<HouseBackup.Cell> cells) {
        for (HouseBackup.Cell c : cells) {
            if (!world.isChunkLoaded(c.x() >> 4, c.z() >> 4)) {
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

    /** IO thread. */
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

    private static String toYaml(Map<String, Object> map) {
        YamlConfiguration yaml = new YamlConfiguration();
        map.forEach(yaml::set);
        return yaml.saveToString();
    }

    private static String fmt(double v) {
        return String.format(java.util.Locale.ROOT, "%.1f", v);
    }

    // ---- one operation at a time ------------------------------------------------------------------------------------

    private @Nullable Op begin(CommandSender sender) {
        long token = gate.begin();
        if (token == OperationGate.REFUSED) {
            messages.send(sender, "plot.fence.busy");
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
            plugin.getLogger().log(Level.SEVERE, "Fence command failed", e);
            messages.send(op.sender(), "plot.fence.failed", Placeholder.unparsed("problem", String.valueOf(e.getMessage())));
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
                    plugin.getLogger().log(Level.SEVERE, "Fence command failed on the IO thread", e);
                    finish(op, "plot.fence.failed", Placeholder.unparsed("problem", String.valueOf(e.getMessage())));
                }
            });
            handedOff = true;
        } catch (RuntimeException e) {
            gate.end(op.token());
            messages.send(op.sender(), "plot.fence.failed", Placeholder.unparsed("problem", "server is shutting down"));
        }
    }

    private void deleteLater(Path file) {
        try {
            io.execute(() -> {
                try {
                    Files.deleteIfExists(file);
                } catch (IOException e) {
                    plugin.getLogger().log(Level.WARNING, "Could not delete " + file, e);
                }
            });
        } catch (RuntimeException e) {
            plugin.getLogger().fine("Left a cancelled fence backup behind: " + file);
        }
    }

    /** From the IO thread: hop to main, end the operation, reply. */
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
