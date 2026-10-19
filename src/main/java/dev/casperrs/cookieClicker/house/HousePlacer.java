package dev.casperrs.cookieClicker.house;

import dev.casperrs.cookieClicker.config.YamlFiles;
import dev.casperrs.cookieClicker.plot.Plot;
import dev.casperrs.cookieClicker.plot.PlotFile;
import dev.casperrs.cookieClicker.plot.PlotRegistry;
import dev.casperrs.cookieClicker.plot.RegionShape;
import dev.casperrs.cookieClicker.text.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import net.kyori.adventure.text.Component;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
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
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.Executor;
import java.util.function.IntSupplier;
import java.util.logging.Level;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * {@code /bakery house place <design>} and {@code /bakery house undo}: the <b>only</b> code that changes the world.
 * Place backs up every block it will replace (a structure snapshot of the box with tile contents, plus the old block
 * data of each changed block) before pasting the design at the house region's minimum corner; undo restores the
 * newest backup of the plot exactly. Files are read and written on the IO thread; world access on the main thread.
 * One house operation at a time.
 */
public final class HousePlacer {

    public static final String BACKUP_DIR = "house-backups";
    public static final String DESIGN_DIR = "houses";
    private static final Pattern NAME = Pattern.compile("[a-z0-9_-]{1,64}");

    private final JavaPlugin plugin;
    private final PlotRegistry plots;
    private final PlotFile plotFile;
    private final Messages messages;
    private final Executor io;
    private final Executor main;
    private final IntSupplier maxBlocks;
    private boolean busy;

    /**
     * @param main      runs a task on the server thread (dropped once the plugin is disabled)
     * @param maxBlocks {@code house.max-blocks}: the largest design box {@code place} accepts
     */
    public HousePlacer(JavaPlugin plugin, PlotRegistry plots, PlotFile plotFile, Messages messages, Executor io,
                       Executor main, IntSupplier maxBlocks) {
        this.plugin = plugin;
        this.plots = plots;
        this.plotFile = plotFile;
        this.messages = messages;
        this.io = io;
        this.main = main;
        this.maxBlocks = maxBlocks;
    }

    public static boolean validDesignName(String name) {
        return NAME.matcher(name).matches();
    }

    // ---- place ----------------------------------------------------------------------------------------------------

    public void place(CommandSender sender, Plot plot, String designName) {
        if (!validDesignName(designName)) {
            messages.send(sender, "house.bad-design-name", Placeholder.unparsed("name", designName));
            return;
        }
        if (plots.resolved(plot.id()).houseRegion() == null) {
            messages.send(sender, "house.no-house-region", Placeholder.unparsed("id", plot.id()));
            return;
        }
        if (!begin(sender)) {
            return;
        }
        Path dataFolder = plugin.getDataFolder().toPath();
        submit(sender, () -> {
            String text;
            try {
                text = readDesign(dataFolder, designName);
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Could not read house design " + designName, e);
                text = null;
            }
            if (text == null) {
                finish(sender, "house.design-not-found", Placeholder.unparsed("name", designName));
                return;
            }
            HouseDesignParser.Result parsed;
            try {
                YamlConfiguration yaml = new YamlConfiguration();
                yaml.loadFromString(text);
                parsed = HouseDesignParser.parse(designName, YamlFiles.toMap(yaml));
            } catch (InvalidConfigurationException e) {
                parsed = new HouseDesignParser.Result(null, List.of("not valid YAML: " + e.getMessage()));
            }
            HouseDesignParser.Result result = parsed;
            main.execute(() -> {
                if (result.design() == null) {
                    busy = false;
                    messages.send(sender, "house.design-invalid", Placeholder.unparsed("name", designName),
                            Placeholder.unparsed("problem", String.join("; ", result.problems())));
                    return;
                }
                paste(sender, plot.id(), result.design());
            });
        });
    }

    /** Data folder {@code houses/<name>.yml} first (admins can add designs), then the jar's copy. Null = none. */
    private @Nullable String readDesign(Path dataFolder, String name) throws IOException {
        Path own = dataFolder.resolve(DESIGN_DIR).resolve(name + ".yml");
        if (Files.isRegularFile(own)) {
            return Files.readString(own, StandardCharsets.UTF_8);
        }
        try (InputStream in = plugin.getResource(DESIGN_DIR + "/" + name + ".yml")) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Main thread: validate against the world, back up, paste, then write the backup files. */
    private void paste(CommandSender sender, String plotId, HouseDesign design) {
        Plot plot = plots.get(plotId).orElse(null);
        Optional<RegionShape> house = plots.houseShape(plotId);
        World world = plot == null ? null : plots.world(plot).orElse(null);
        if (plot == null || house.isEmpty() || world == null) {
            finishNow(sender, "house.no-house-region", Placeholder.unparsed("id", plotId));
            return;
        }
        long volume = (long) design.sizeX() * design.sizeY() * design.sizeZ();
        if (volume > maxBlocks.getAsInt()) {
            finishNow(sender, "house.too-big", Placeholder.unparsed("name", design.name()),
                    Placeholder.unparsed("count", Long.toString(volume)),
                    Placeholder.unparsed("amount", Integer.toString(maxBlocks.getAsInt())));
            return;
        }
        Map<Character, BlockData> palette = new HashMap<>();
        for (Map.Entry<Character, String> e : design.palette().entrySet()) {
            try {
                palette.put(e.getKey(), Bukkit.createBlockData(e.getValue()));
            } catch (IllegalArgumentException ex) {
                finishNow(sender, "house.design-invalid", Placeholder.unparsed("name", design.name()),
                        Placeholder.unparsed("problem", "palette '" + e.getKey() + "': '" + e.getValue()
                                + "' is not valid block data"));
                return;
            }
        }
        RegionShape shape = house.get();
        int[] regionMin = {shape.minX(), shape.minY(), shape.minZ()};
        int[] corner = design.toWorld(regionMin, 0, 0, 0);
        int[] size = {design.sizeX(), design.sizeY(), design.sizeZ()};
        if (corner[1] < world.getMinHeight() || corner[1] + size[1] > world.getMaxHeight()) {
            finishNow(sender, "house.not-loaded", Placeholder.unparsed("id", plotId));
            return;
        }
        for (int cx = corner[0] >> 4; cx <= (corner[0] + size[0] - 1) >> 4; cx++) {
            for (int cz = corner[2] >> 4; cz <= (corner[2] + size[2] - 1) >> 4; cz++) {
                if (!world.isChunkLoaded(cx, cz)) {
                    finishNow(sender, "house.not-loaded", Placeholder.unparsed("id", plotId));
                    return;
                }
            }
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
            finishNow(sender, "house.place-failed", Placeholder.unparsed("problem", String.valueOf(e.getMessage())));
            return;
        }

        HouseBackup.BlockAccess access = worldAccess(world, byString);
        HouseBackup backup = HouseBackup.capture(plotId, design.name(), plot.worldKey(), System.currentTimeMillis(),
                corner, size, plot.chimney(), changes, access);
        for (HouseBackup.Change c : changes) {
            if (!access.get(c.x(), c.y(), c.z()).equals(c.newData())) { // identical blocks stay untouched
                access.set(c.x(), c.y(), c.z(), c.newData());
            }
        }
        for (HouseDesign.SignText sign : design.signs()) {
            int[] w = design.toWorld(regionMin, sign.at()[0], sign.at()[1], sign.at()[2]);
            if (world.getBlockAt(w[0], w[1], w[2]).getState(false) instanceof Sign state) {
                SignSide front = state.getSide(Side.FRONT);
                for (int i = 0; i < sign.lines().size(); i++) {
                    front.line(i, Component.text(sign.lines().get(i))); // design text, plain (never parsed)
                }
                state.update(true, false);
            } else {
                plugin.getLogger().warning("House design " + design.name() + ": signs entry at "
                        + java.util.Arrays.toString(sign.at()) + " is not a sign block; text skipped");
            }
        }

        int[] chimney = design.points().get("chimney");
        if (chimney != null) {
            int[] w = design.toWorld(regionMin, chimney[0], chimney[1], chimney[2]);
            Plot updated = plot.withChimney(new Plot.Point(w[0] + 0.5, w[1], w[2] + 0.5));
            plots.put(updated, problem -> plugin.getLogger().warning(problem));
            plotFile.saveAsync(plots.all());
        }

        int changed = backup.cells().size();
        String yaml = toYaml(backup.toMap());
        Path dir = plugin.getDataFolder().toPath().resolve(BACKUP_DIR).resolve(plotId);
        submit(sender, () -> {
            try {
                Files.createDirectories(dir);
                Files.write(dir.resolve(backup.createdAt() + ".nbt"), structureBytes);
                Files.writeString(dir.resolve(backup.createdAt() + ".yml"), yaml, StandardCharsets.UTF_8);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not write the house backup of plot " + plotId
                        + "; /bakery house undo will not be able to restore this paste", e);
            }
            finish(sender, "house.placed", Placeholder.unparsed("name", design.name()),
                    Placeholder.unparsed("id", plotId), Placeholder.unparsed("count", Integer.toString(changed)));
        });
    }

    private static HouseBackup.BlockAccess worldAccess(World world, Map<String, BlockData> known) {
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

    // ---- undo -----------------------------------------------------------------------------------------------------

    public void undo(CommandSender sender, Plot plot) {
        if (!begin(sender)) {
            return;
        }
        String plotId = plot.id();
        Path dir = plugin.getDataFolder().toPath().resolve(BACKUP_DIR).resolve(plotId);
        submit(sender, () -> {
            Path meta;
            String text;
            byte[] nbt;
            try {
                meta = newest(dir);
                if (meta == null) {
                    finish(sender, "house.nothing-to-undo", Placeholder.unparsed("id", plotId));
                    return;
                }
                text = Files.readString(meta, StandardCharsets.UTF_8);
                Path nbtFile = sibling(meta, ".nbt");
                nbt = Files.isRegularFile(nbtFile) ? Files.readAllBytes(nbtFile) : null;
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not read the house backups of plot " + plotId, e);
                finish(sender, "house.undo-failed", Placeholder.unparsed("problem", String.valueOf(e.getMessage())));
                return;
            }
            main.execute(() -> restore(sender, plotId, meta, text, nbt));
        });
    }

    private void restore(CommandSender sender, String plotId, Path meta, String text, byte @Nullable [] nbt) {
        HouseBackup backup;
        try {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.loadFromString(text);
            backup = HouseBackup.fromMap(YamlFiles.toMap(yaml));
        } catch (InvalidConfigurationException | IllegalArgumentException e) {
            finishNow(sender, "house.undo-failed", Placeholder.unparsed("problem",
                    meta.getFileName() + ": " + e.getMessage()));
            return;
        }
        World world = dev.casperrs.cookieClicker.plot.PlotService.worldByKey(backup.worldKey()).orElse(null);
        if (world == null) {
            finishNow(sender, "house.not-loaded", Placeholder.unparsed("id", plotId));
            return;
        }
        int[] c = backup.corner();
        int[] s = backup.size();
        for (int cx = c[0] >> 4; cx <= (c[0] + s[0] - 1) >> 4; cx++) {
            for (int cz = c[2] >> 4; cz <= (c[2] + s[2] - 1) >> 4; cz++) {
                if (!world.isChunkLoaded(cx, cz)) {
                    finishNow(sender, "house.not-loaded", Placeholder.unparsed("id", plotId));
                    return;
                }
            }
        }
        boolean exact = false;
        if (nbt != null) {
            try {
                Structure structure = Bukkit.getStructureManager().loadStructure(new ByteArrayInputStream(nbt));
                structure.place(new Location(world, c[0], c[1], c[2]), false, StructureRotation.NONE, Mirror.NONE,
                        0, 1f, new Random());
                exact = true;
            } catch (IOException | RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "Could not place the structure backup " + meta.getFileName()
                        + "; restoring block data only (tile contents are lost)", e);
            }
        }
        if (!exact) {
            try {
                backup.restoreCells(worldAccess(world, new HashMap<>()));
            } catch (IllegalArgumentException e) {
                finishNow(sender, "house.undo-failed", Placeholder.unparsed("problem", String.valueOf(e.getMessage())));
                return;
            }
        }
        plots.get(plotId).ifPresent(plot -> {
            plots.put(plot.withChimney(backup.previousChimney()), problem -> plugin.getLogger().warning(problem));
            plotFile.saveAsync(plots.all());
        });
        int count = backup.cells().size();
        submit(sender, () -> {
            try {
                Files.deleteIfExists(sibling(meta, ".nbt"));
                Files.deleteIfExists(meta);
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Could not delete the used backup " + meta, e);
            }
            finish(sender, "house.undone", Placeholder.unparsed("id", plotId),
                    Placeholder.unparsed("count", Integer.toString(count)));
        });
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

    // ---- busy flag + thread hops ------------------------------------------------------------------------------------

    private boolean begin(CommandSender sender) {
        if (busy) {
            messages.send(sender, "house.busy");
            return false;
        }
        busy = true;
        return true;
    }

    /** Runs on {@code io}; a rejected task (shutting down) clears the busy flag. */
    private void submit(CommandSender sender, Runnable task) {
        try {
            io.execute(task);
        } catch (RuntimeException e) {
            busy = false;
            messages.send(sender, "house.place-failed", Placeholder.unparsed("problem", "server is shutting down"));
        }
    }

    /** From the IO thread: hop to main, clear busy, reply. */
    private void finish(CommandSender sender, String path,
                        net.kyori.adventure.text.minimessage.tag.resolver.TagResolver... resolvers) {
        main.execute(() -> finishNow(sender, path, resolvers));
    }

    private void finishNow(CommandSender sender, String path,
                           net.kyori.adventure.text.minimessage.tag.resolver.TagResolver... resolvers) {
        busy = false;
        messages.send(sender, path, resolvers);
    }

    /** Disable / reload: a running operation's late callbacks are dropped by {@code main}; allow new ones. */
    public void reset() {
        busy = false;
    }
}
