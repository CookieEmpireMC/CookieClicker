package dev.casperrs.cookieClicker.house.setup;

import dev.casperrs.cookieClicker.house.design.DesignFiles;
import dev.casperrs.cookieClicker.house.design.HouseDesign;
import dev.casperrs.cookieClicker.house.design.HouseDesignParser;
import dev.casperrs.cookieClicker.house.setup.WorldOperation.Op;
import dev.casperrs.cookieClicker.plot.Plot;
import dev.casperrs.cookieClicker.plot.PlotFile;
import dev.casperrs.cookieClicker.plot.PlotRegistry;
import dev.casperrs.cookieClicker.plot.PlotService;
import dev.casperrs.cookieClicker.plot.RegionShape;
import dev.casperrs.cookieClicker.text.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.IntSupplier;
import java.util.logging.Level;
import java.util.regex.Pattern;

/**
 * {@code /bakery house place <design>} and {@code /bakery house undo}, one of the two world writers. Place backs up
 * every block it will replace (structure snapshot + old block data) and pastes only once the backup is on disk; undo
 * restores the plot's newest backup exactly. Files on IO, world on main, one operation at a time.
 */
public final class HousePlacer {

    public static final String BACKUP_DIR = "house-backups";
    private static final Pattern NAME = Pattern.compile("[a-z0-9_-]{1,64}");

    private final JavaPlugin plugin;
    private final PlotRegistry plots;
    private final PlotFile plotFile;
    private final Messages messages;
    private final IntSupplier maxBlocks;
    private final Executor main;
    private final WorldOperation ops;

    /** A checked paste: everything the world step needs once its backup is on disk. */
    private record Paste(String plotId, HouseDesign design, int[] regionMin, List<HouseBackup.Change> changes,
                         Map<String, BlockData> byString, HouseBackup backup, byte[] structure) {
    }

    public HousePlacer(JavaPlugin plugin, PlotRegistry plots, PlotFile plotFile, Messages messages, Executor io,
                       Executor main, IntSupplier maxBlocks) {
        this.plugin = plugin;
        this.plots = plots;
        this.plotFile = plotFile;
        this.messages = messages;
        this.main = main;
        this.maxBlocks = maxBlocks;
        this.ops = new WorldOperation(plugin.getLogger(), messages, io, main, "house.busy", "house.place-failed",
                "House command");
    }

    public static boolean validDesignName(String name) {
        return NAME.matcher(name).matches();
    }

    public void place(CommandSender sender, Plot plot, String designName) {
        if (!validDesignName(designName)) {
            messages.send(sender, "house.bad-design-name", Placeholder.unparsed("name", designName));
            return;
        }
        if (plots.resolved(plot.id()).houseRegion() == null) {
            messages.send(sender, "house.no-house-region", Placeholder.unparsed("id", plot.id()));
            return;
        }
        Op op = ops.begin(sender);
        if (op != null) {
            ops.step(op, () -> ops.submit(op, () -> readDesign(op, plot.id(), designName)));
        }
    }

    /** IO thread. */
    private void readDesign(Op op, String plotId, String designName) {
        String text;
        try {
            text = DesignFiles.read(plugin, designName + ".yml");
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not read house design " + designName, e);
            text = null;
        }
        if (text == null) {
            ops.finish(op, "house.design-not-found", Placeholder.unparsed("name", designName));
            return;
        }
        HouseDesignParser.Result result = DesignFiles.parse(text, map -> HouseDesignParser.parse(designName, map),
                problem -> new HouseDesignParser.Result(null, List.of(problem)));
        main.execute(() -> ops.step(op, () -> {
            if (result.design() == null) {
                ops.finishNow(op, "house.design-invalid", Placeholder.unparsed("name", designName),
                        Placeholder.unparsed("problem", String.join("; ", result.problems())));
                return;
            }
            Paste paste = planPaste(op, plotId, result.design());
            if (paste != null) {
                writeBackup(op, paste);
            }
        }));
    }

    /** Main thread: checks the design against the world and snapshots the box; null = refused (already replied). */
    private @Nullable Paste planPaste(Op op, String plotId, HouseDesign design) {
        Plot plot = plots.get(plotId).orElse(null);
        Optional<RegionShape> house = plots.houseShape(plotId);
        World world = plot == null ? null : plots.world(plot).orElse(null);
        if (plot == null || house.isEmpty() || world == null) {
            ops.finishNow(op, "house.no-house-region", Placeholder.unparsed("id", plotId));
            return null;
        }
        long volume = (long) design.sizeX() * design.sizeY() * design.sizeZ();
        if (volume > maxBlocks.getAsInt()) {
            ops.finishNow(op, "house.too-big", Placeholder.unparsed("name", design.name()),
                    Placeholder.unparsed("count", Long.toString(volume)),
                    Placeholder.unparsed("amount", Integer.toString(maxBlocks.getAsInt())));
            return null;
        }
        Map<Character, BlockData> palette = new HashMap<>();
        for (Map.Entry<Character, String> e : design.palette().entrySet()) {
            try {
                palette.put(e.getKey(), Bukkit.createBlockData(e.getValue()));
            } catch (IllegalArgumentException ex) {
                ops.finishNow(op, "house.design-invalid", Placeholder.unparsed("name", design.name()),
                        Placeholder.unparsed("problem", "palette '" + e.getKey() + "': '" + e.getValue()
                                + "' is not valid block data"));
                return null;
            }
        }
        RegionShape shape = house.get();
        int[] regionMin = {shape.minX(), shape.minY(), shape.minZ()};
        int[] corner = design.toWorld(regionMin, 0, 0, 0);
        int[] size = {design.sizeX(), design.sizeY(), design.sizeZ()};
        if (!loaded(world, corner, size)) {
            ops.finishNow(op, "house.not-loaded", Placeholder.unparsed("id", plotId));
            return null;
        }
        List<HouseBackup.Change> changes = new ArrayList<>();
        Map<String, BlockData> byString = new HashMap<>();
        for (HouseDesign.Cell c : design.cells()) {
            BlockData data = palette.get(design.grid()[c.y()][c.z()][c.x()]);
            String s = data.getAsString();
            byString.putIfAbsent(s, data);
            int[] w = design.toWorld(regionMin, c.x(), c.y(), c.z());
            changes.add(new HouseBackup.Change(w[0], w[1], w[2], s));
        }
        byte[] structure;
        try {
            structure = BoxStructures.snapshot(world, corner, size);
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not back up the house area of plot " + plotId, e);
            ops.finishNow(op, "house.place-failed", WorldOperation.problem(e.getMessage()));
            return null;
        }
        HouseBackup backup = HouseBackup.capture(plotId, design.name(), plot.worldKey(), System.currentTimeMillis(),
                corner, size, plot.smoke(), plot.steam(), changes, worldAccess(world, byString));
        return new Paste(plotId, design, regionMin, changes, byString, backup, structure);
    }

    /** The undo must be on disk before the first block changes: write first, paste only after it succeeded. */
    private void writeBackup(Op op, Paste paste) {
        String yaml = BackupFiles.toYaml(paste.backup().toMap());
        Path yml = plugin.getDataFolder().toPath().resolve(BACKUP_DIR).resolve(paste.plotId())
                .resolve(paste.backup().createdAt() + ".yml");
        ops.submit(op, () -> {
            try {
                BackupFiles.write(yml, yaml, paste.structure());
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not write the house backup of plot " + paste.plotId()
                        + "; nothing was placed", e);
                BackupFiles.deleteQuietly(plugin.getLogger(), yml);
                ops.finish(op, "house.place-failed", Placeholder.unparsed("problem",
                        "could not save the undo backup (" + e.getMessage() + ")"));
                return;
            }
            main.execute(() -> ops.stepOrDiscard(op, yml, () -> applyPaste(op, paste, yml)));
        });
    }

    /** Main thread, after the backup is on disk: paste, sign text, and the design's smoke/steam points. */
    private void applyPaste(Op op, Paste paste, Path yml) {
        HouseDesign design = paste.design();
        Plot plot = plots.get(paste.plotId()).orElse(null);
        World world = plot == null ? null : plots.world(plot).orElse(null);
        if (plot == null || world == null || !loaded(world, design.toWorld(paste.regionMin(), 0, 0, 0),
                new int[] {design.sizeX(), design.sizeY(), design.sizeZ()})) {
            ops.submit(op, () -> {
                BackupFiles.deleteQuietly(plugin.getLogger(), yml);
                ops.finish(op, "house.not-loaded", Placeholder.unparsed("id", paste.plotId()));
            });
            return;
        }
        HouseBackup.BlockAccess access = worldAccess(world, paste.byString());
        for (HouseBackup.Change c : paste.changes()) {
            if (!access.get(c.x(), c.y(), c.z()).equals(c.newData())) {
                access.set(c.x(), c.y(), c.z(), c.newData());
            }
        }
        writeSigns(plugin, world, design, paste.regionMin());
        // a design without smoke keeps the plot's own smoke points
        List<Plot.Point> smoke = cellCentres(design, paste.regionMin(), design.smoke());
        Plot updated = (smoke.isEmpty() ? plot : plot.withSmoke(smoke))
                .withSteam(cellCentres(design, paste.regionMin(), design.steam()));
        if (!updated.equals(plot)) {
            plots.put(updated, problem -> plugin.getLogger().warning(problem));
            plotFile.saveAsync(plots.all());
        }
        ops.finishNow(op, "house.placed", Placeholder.unparsed("name", design.name()),
                Placeholder.unparsed("id", paste.plotId()),
                Placeholder.unparsed("count", Integer.toString(paste.backup().cells().size())));
    }

    public void undo(CommandSender sender, Plot plot) {
        Op op = ops.begin(sender);
        if (op == null) {
            return;
        }
        String plotId = plot.id();
        Path dir = plugin.getDataFolder().toPath().resolve(BACKUP_DIR).resolve(plotId);
        ops.step(op, () -> ops.submit(op, () -> {
            BackupFiles.Read read;
            try {
                read = BackupFiles.readNewest(dir);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not read the house backups of plot " + plotId, e);
                ops.finish(op, "house.undo-failed", WorldOperation.problem(e.getMessage()));
                return;
            }
            if (read == null) {
                ops.finish(op, "house.nothing-to-undo", Placeholder.unparsed("id", plotId));
                return;
            }
            main.execute(() -> ops.step(op, () -> restore(op, plotId, read)));
        }));
    }

    private void restore(Op op, String plotId, BackupFiles.Read read) {
        HouseBackup backup;
        try {
            backup = HouseBackup.fromMap(DesignFiles.parseYaml(read.text()));
        } catch (InvalidConfigurationException | IllegalArgumentException e) {
            ops.finishNow(op, "house.undo-failed", Placeholder.unparsed("problem",
                    read.yml().getFileName() + ": " + e.getMessage()));
            return;
        }
        World world = PlotService.worldByKey(backup.worldKey()).orElse(null);
        if (world == null || !loaded(world, backup.corner(), backup.size())) {
            ops.finishNow(op, "house.not-loaded", Placeholder.unparsed("id", plotId));
            return;
        }
        if (!placeStructure(world, backup, read)) {
            try {
                backup.restoreCells(worldAccess(world, new HashMap<>()));
            } catch (IllegalArgumentException e) {
                ops.finishNow(op, "house.undo-failed", WorldOperation.problem(e.getMessage()));
                return;
            }
        }
        plots.get(plotId).ifPresent(plot -> {
            plots.put(plot.withSmoke(backup.previousSmoke()).withSteam(backup.previousSteam()),
                    problem -> plugin.getLogger().warning(problem));
            plotFile.saveAsync(plots.all());
        });
        int count = backup.cells().size();
        ops.submit(op, () -> {
            BackupFiles.deleteQuietly(plugin.getLogger(), read.yml());
            ops.finish(op, "house.undone", Placeholder.unparsed("id", plotId),
                    Placeholder.unparsed("count", Integer.toString(count)));
        });
    }

    /** The exact restore (tile contents included); false when there is no structure or it can't be placed. */
    private boolean placeStructure(World world, HouseBackup backup, BackupFiles.Read read) {
        if (read.nbt() == null) {
            return false;
        }
        try {
            BoxStructures.place(world, backup.corner(), read.nbt());
            return true;
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Could not place the structure backup " + read.yml().getFileName()
                    + "; restoring block data only (tile contents are lost)", e);
            return false;
        }
    }

    /** Reload / disable: cancels the running operation; a paste whose backup was still being written never happens. */
    public void reset() {
        ops.reset();
    }

    /** Sets the design's sign text (plain, never parsed) on the pasted sign blocks. Main thread. */
    static void writeSigns(JavaPlugin plugin, World world, HouseDesign design, int[] regionMin) {
        for (HouseDesign.SignText sign : design.signs()) {
            int[] w = design.toWorld(regionMin, sign.at()[0], sign.at()[1], sign.at()[2]);
            if (world.getBlockAt(w[0], w[1], w[2]).getState(false) instanceof Sign state) {
                SignSide front = state.getSide(Side.FRONT);
                for (int i = 0; i < sign.lines().size(); i++) {
                    front.line(i, Component.text(sign.lines().get(i)));
                }
                state.update(true, false);
            } else {
                plugin.getLogger().warning("House design " + design.name() + ": signs entry at "
                        + Arrays.toString(sign.at()) + " is not a sign block; text skipped");
            }
        }
    }

    /** Bottom centre of each design cell, in world coordinates. */
    static List<Plot.Point> cellCentres(HouseDesign design, int[] regionMin, List<int[]> cells) {
        List<Plot.Point> out = new ArrayList<>(cells.size());
        for (int[] c : cells) {
            int[] w = design.toWorld(regionMin, c[0], c[1], c[2]);
            out.add(new Plot.Point(w[0] + 0.5, w[1], w[2] + 0.5));
        }
        return out;
    }

    /** Whether the box fits the world's height and all its chunks are loaded. */
    static boolean loaded(World world, int[] corner, int[] size) {
        if (corner[1] < world.getMinHeight() || corner[1] + size[1] > world.getMaxHeight()) {
            return false;
        }
        for (int cx = corner[0] >> 4; cx <= (corner[0] + size[0] - 1) >> 4; cx++) {
            for (int cz = corner[2] >> 4; cz <= (corner[2] + size[2] - 1) >> 4; cz++) {
                if (!world.isChunkLoaded(cx, cz)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * The real world behind {@link HouseBackup.BlockAccess}; the only block writes of both world writers go through
     * here ({@code known} caches parsed block data). No physics, so a paste never drops or updates neighbours.
     */
    static HouseBackup.BlockAccess worldAccess(World world, Map<String, BlockData> known) {
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
}
