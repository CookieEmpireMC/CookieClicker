package dev.casperrs.cookieClicker.house.setup;

import dev.casperrs.cookieClicker.farm.Farm;
import dev.casperrs.cookieClicker.farm.FarmDesigns;
import dev.casperrs.cookieClicker.farm.FarmPlan;
import dev.casperrs.cookieClicker.farm.FarmRegistry;
import dev.casperrs.cookieClicker.farm.PasteCursor;
import dev.casperrs.cookieClicker.house.design.DesignFiles;
import dev.casperrs.cookieClicker.house.setup.WorldOperation.Op;
import dev.casperrs.cookieClicker.plot.Plot;
import dev.casperrs.cookieClicker.plot.PlotRegions;
import dev.casperrs.cookieClicker.plot.PlotService;
import dev.casperrs.cookieClicker.plot.RegionShape;
import dev.casperrs.cookieClicker.text.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.structure.Structure;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.logging.Level;

/**
 * {@code /bakery farm setup <id>} and {@code /bakery farm undo <id>}: pastes {@code houses/countryside.yml} and its
 * place files into the region flagged {@code bakery-farm <id>}, and restores it. The backup (structure snapshots of
 * chunk-column slabs) is on disk before the first block changes; snapshot, paste and restore are each spread over
 * ticks ({@code farm.paste.blocks-per-tick}), with progress in chat and the chunks held by plugin tickets meanwhile.
 * Same block rules as the house paste (no physics, persistent leaves). One operation at a time, shared gate with
 * nothing else. Files on IO, world on main.
 */
public final class FarmSetup {

    public static final String BACKUP_DIR = "farm-backups";

    private final JavaPlugin plugin;
    private final PlotRegions regions;
    private final FarmRegistry farms;
    private final Messages messages;
    private final Executor io;
    private final Executor main;
    private final IntSupplier blocksPerTick;
    private final WorldOperation ops;
    private final ChunkHold chunks;
    private @Nullable Ticked running;

    /** The farm region found by its flag. */
    private record FarmRegion(World world, RegionShape shape) {
    }

    /** A checked plan with the block data per part ({@code palettes[part][char]}). */
    private record Paste(String id, FarmRegion region, FarmPlan plan, BlockData[][] palettes, long cells) {
    }

    public FarmSetup(JavaPlugin plugin, PlotRegions regions, FarmRegistry farms, Messages messages, Executor io,
                     Executor main, IntSupplier blocksPerTick) {
        this.plugin = plugin;
        this.regions = regions;
        this.farms = farms;
        this.messages = messages;
        this.io = io;
        this.main = main;
        this.blocksPerTick = blocksPerTick;
        this.chunks = new ChunkHold(plugin);
        this.ops = new WorldOperation(plugin.getLogger(), messages, io, main, "farm.busy", "farm.failed",
                "Farm command");
    }

    public void setup(CommandSender sender, String id) {
        if (!Plot.validId(id)) {
            messages.send(sender, "farm.bad-id", Placeholder.unparsed("id", id));
            return;
        }
        if (farms.get(id).isPresent()) {
            messages.send(sender, "farm.already-set-up", Placeholder.unparsed("id", id));
            return;
        }
        FarmRegion region = findRegion(sender, id);
        if (region == null) {
            return;
        }
        Op op = ops.begin(sender);
        if (op != null) {
            chunks.release(); // left by an operation that ended on the IO thread
            ops.step(op, () -> ops.submit(op, () -> readDesigns(op, id, region)));
        }
    }

    /** The one region flagged {@code bakery-farm <id>} in any loaded world, or null after telling the sender. */
    private @Nullable FarmRegion findRegion(CommandSender sender, String id) {
        List<FarmRegion> found = new ArrayList<>();
        for (World w : Bukkit.getWorlds()) {
            for (PlotRegions.RegionInfo r : regions.regions(w)) {
                if (id.equals(r.farmFlag())) {
                    regions.shape(w, r.id()).ifPresent(s -> found.add(new FarmRegion(w, s)));
                }
            }
        }
        if (found.size() != 1) {
            messages.send(sender, found.isEmpty() ? "farm.no-region" : "farm.region-ambiguous",
                    Placeholder.unparsed("id", id));
            return null;
        }
        return found.getFirst();
    }

    /** IO thread. */
    private void readDesigns(Op op, String id, FarmRegion region) {
        try {
            if (FarmBackupFiles.any(farmDir(id))) {
                ops.finish(op, "farm.already-set-up", Placeholder.unparsed("id", id));
                return;
            }
        } catch (IOException e) {
            ops.finish(op, "farm.failed", WorldOperation.problem(e.getMessage()));
            return;
        }
        FarmDesigns.Result result = FarmDesigns.load(this::designYaml);
        main.execute(() -> ops.step(op, () -> {
            if (result.layout() == null) {
                ops.finishNow(op, result.missing() ? "farm.design-not-found" : "farm.design-invalid",
                        Placeholder.unparsed("name", result.file()),
                        Placeholder.unparsed("problem", String.join("; ", result.problems())));
                return;
            }
            Paste paste = check(op, id, region, result.layout());
            if (paste != null) {
                snapshot(op, paste);
            }
        }));
    }

    /** IO thread: one design file as plain maps, null when missing. */
    private @Nullable Map<String, Object> designYaml(String name) {
        try {
            String text = DesignFiles.read(plugin, name + ".yml");
            return text == null ? null : DesignFiles.parseYaml(text);
        } catch (IOException e) {
            throw new IllegalArgumentException("could not read houses/" + name + ".yml: " + e.getMessage(), e);
        } catch (InvalidConfigurationException e) {
            throw new IllegalArgumentException("not valid YAML: " + e.getMessage(), e);
        }
    }

    /** Main thread: the plan against the region and the world; null = refused (already replied). */
    private @Nullable Paste check(Op op, String id, FarmRegion region, FarmDesigns.Layout layout) {
        RegionShape r = region.shape();
        int[] min = {r.minX(), r.minY(), r.minZ()};
        FarmPlan.Result planned = FarmPlan.plan(layout, min);
        FarmPlan plan = planned.plan();
        if (plan == null) {
            ops.finishNow(op, "farm.design-invalid", Placeholder.unparsed("name", FarmDesigns.COUNTRYSIDE),
                    Placeholder.unparsed("problem", String.join("; ", planned.problems())));
            return null;
        }
        int[] need = plan.neededSize(min, new int[] {r.maxX(), r.maxY(), r.maxZ()});
        if (need != null) {
            ops.finishNow(op, "farm.too-small", Placeholder.unparsed("id", id),
                    Placeholder.unparsed("size", FarmPlan.sizeText(need)),
                    Placeholder.unparsed("region", FarmPlan.sizeText(new int[] {r.maxX() - r.minX() + 1,
                            r.maxY() - r.minY() + 1, r.maxZ() - r.minZ() + 1})));
            return null;
        }
        BlockData[][] palettes = new BlockData[plan.parts().size()][];
        for (int i = 0; i < palettes.length; i++) {
            FarmPlan.Part part = plan.parts().get(i);
            try {
                palettes[i] = palette(part.design().palette());
            } catch (IllegalArgumentException e) {
                ops.finishNow(op, "farm.design-invalid", Placeholder.unparsed("name", part.name()),
                        Placeholder.unparsed("problem", "a palette entry is not valid block data: " + e.getMessage()));
                return null;
            }
        }
        if (!HousePlacer.loaded(region.world(), plan.box().corner(), plan.box().size())) {
            ops.finishNow(op, "farm.not-loaded", Placeholder.unparsed("id", id));
            return null;
        }
        chunks.hold(region.world(), plan.box()); // until the operation ends: the paste comes after the IO write
        return new Paste(id, region, plan, palettes, plan.cellCount());
    }

    private static BlockData[] palette(Map<Character, String> palette) {
        int size = 1;
        for (char c : palette.keySet()) {
            size = Math.max(size, c + 1);
        }
        BlockData[] out = new BlockData[size];
        palette.forEach((c, data) -> out[c] = DesignBlocks.create(data));
        return out;
    }

    /**
     * Main thread: captures the slabs (each at most {@code blocks-per-tick} blocks), about blocks-per-tick blocks per
     * tick; the IO thread compresses each one as it comes, and the backup goes to disk after the last.
     */
    private void snapshot(Op op, Paste paste) {
        World world = paste.region().world();
        int perTick = blocksPerTick.getAsInt();
        List<Farm.Box> slabs = FarmBackup.slabs(paste.plan().box(), perTick);
        byte[][] saved = new byte[slabs.size()][]; // filled and read on the IO thread only, in submit order
        IOException[] failure = new IOException[1];
        int[] next = {0};
        messages.send(op.sender(), "farm.setup-started", Placeholder.unparsed("id", paste.id()),
                Placeholder.unparsed("count", Long.toString(paste.cells())));
        start(op, "setup " + paste.id(), () -> {
            long spent = 0;
            while (next[0] < slabs.size() && (spent == 0 || spent + FarmBackup.volume(slabs.get(next[0])) <= perTick)) {
                int i = next[0]++;
                Farm.Box s = slabs.get(i);
                Structure captured = BoxStructures.capture(world, s.corner(), s.size());
                spent += FarmBackup.volume(s);
                io.execute(() -> {
                    try {
                        saved[i] = BoxStructures.serialize(captured);
                    } catch (IOException e) {
                        failure[0] = e;
                    }
                });
            }
            return next[0] == slabs.size();
        }, () -> writeBackup(op, paste, new FarmBackup(paste.id(), world.getKey().asString(),
                System.currentTimeMillis(), paste.plan().box(), slabs), saved, failure));
    }

    /** The undo must be on disk before the first block changes; runs after every slab's compression on IO. */
    private void writeBackup(Op op, Paste paste, FarmBackup backup, byte[][] slabs, IOException[] failure) {
        String manifest = BackupFiles.toYaml(backup.toMap());
        Path dir = farmDir(paste.id()).resolve(Long.toString(backup.createdAt()));
        ops.submit(op, () -> {
            try {
                if (failure[0] != null) {
                    throw failure[0];
                }
                FarmBackupFiles.write(dir, manifest, Arrays.asList(slabs));
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not write the backup of farm " + paste.id()
                        + "; nothing was placed", e);
                FarmBackupFiles.deleteQuietly(plugin.getLogger(), dir);
                failFromIo(op, "farm.failed", WorldOperation.problem("could not save the undo backup ("
                        + e.getMessage() + ")"));
                return;
            }
            main.execute(() -> {
                if (ops.isCurrent(op)) {
                    ops.step(op, () -> paste(op, paste));
                } else {
                    deleteLater(dir);
                }
            });
        });
    }

    /** Main thread, after the backup is on disk. */
    private void paste(Op op, Paste paste) {
        World world = paste.region().world();
        PasteCursor cursor = new PasteCursor(paste.plan());
        BlockData[][] palettes = paste.palettes();
        PasteProgress progress = new PasteProgress(messages, op.sender(), "farm.setup-progress", paste.cells());
        PasteCursor.Sink sink = (part, x, y, z, c) -> HousePlacer.setBlock(world, x, y, z, palettes[part][c]);
        start(op, "setup " + paste.id(), () -> {
            progress.add(cursor.next(blocksPerTick.getAsInt(), sink));
            return cursor.done();
        }, () -> finishPaste(op, paste));
    }

    private void finishPaste(Op op, Paste paste) {
        World world = paste.region().world();
        try {
            for (FarmPlan.Part part : paste.plan().parts()) {
                HousePlacer.writeSigns(plugin, world, part.design(), part.anchor());
            }
        } finally {
            chunks.release();
        }
        RegionShape r = paste.region().shape();
        farms.put(new Farm(paste.id(), world.getKey().asString(), r.id(), new Plot.Cell(r.minX(), r.minY(), r.minZ()),
                paste.plan().box(), paste.plan().places()));
        ops.finishNow(op, "farm.setup-done", Placeholder.unparsed("id", paste.id()),
                Placeholder.unparsed("count", Long.toString(paste.cells())),
                Placeholder.unparsed("amount", Integer.toString(paste.plan().places().size())));
    }

    public void undo(CommandSender sender, String id) {
        if (!Plot.validId(id)) {
            messages.send(sender, "farm.bad-id", Placeholder.unparsed("id", id));
            return;
        }
        Op op = ops.begin(sender);
        if (op == null) {
            return;
        }
        chunks.release();
        ops.step(op, () -> ops.submit(op, () -> {
            FarmBackupFiles.Read read;
            try {
                read = FarmBackupFiles.readNewest(farmDir(id), FarmSetup::slabCount);
            } catch (IOException | IllegalArgumentException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not read the backup of farm " + id, e);
                ops.finish(op, "farm.undo-failed", WorldOperation.problem(e.getMessage()));
                return;
            }
            if (read == null) {
                ops.finish(op, "farm.nothing-to-undo", Placeholder.unparsed("id", id));
                return;
            }
            Structure[] structures = new Structure[read.slabs().length];
            try {
                for (int i = 0; i < structures.length; i++) {
                    structures[i] = BoxStructures.deserialize(read.slabs()[i]);
                }
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not read the backup of farm " + id, e);
                ops.finish(op, "farm.undo-failed", WorldOperation.problem(e.getMessage()));
                return;
            }
            main.execute(() -> ops.step(op, () -> restore(op, id, read, structures)));
        }));
    }

    /** Main thread: places the slabs back, about {@code blocks-per-tick} blocks per tick. */
    private void restore(Op op, String id, FarmBackupFiles.Read read, Structure[] structures) {
        FarmBackup backup;
        try {
            backup = FarmBackup.fromMap(DesignFiles.parseYaml(read.manifest()));
        } catch (InvalidConfigurationException | IllegalArgumentException e) {
            ops.finishNow(op, "farm.undo-failed", WorldOperation.problem(e.getMessage()));
            return;
        }
        World world = PlotService.worldByKey(backup.worldKey()).orElse(null);
        if (world == null || !HousePlacer.loaded(world, backup.box().corner(), backup.box().size())) {
            ops.finishNow(op, "farm.not-loaded", Placeholder.unparsed("id", id));
            return;
        }
        List<Farm.Box> slabs = backup.slabs();
        int[] next = {0};
        PasteProgress progress = new PasteProgress(messages, op.sender(), "farm.undo-progress", slabs.size());
        if (structures.length != slabs.size()) {
            ops.finishNow(op, "farm.undo-failed", WorldOperation.problem("backup.yml lists " + slabs.size()
                    + " slabs but " + structures.length + " were read"));
            return;
        }
        chunks.hold(world, backup.box());
        int perTick = blocksPerTick.getAsInt();
        start(op, "undo " + id, () -> {
            long spent = 0;
            while (next[0] < slabs.size() && (spent == 0 || spent + FarmBackup.volume(slabs.get(next[0])) <= perTick)) {
                Farm.Box s = slabs.get(next[0]);
                BoxStructures.place(world, s.corner(), structures[next[0]]);
                spent += FarmBackup.volume(s);
                next[0]++;
                progress.add(1);
            }
            return next[0] == slabs.size();
        }, () -> {
            chunks.release();
            farms.remove(id);
            ops.submit(op, () -> {
                FarmBackupFiles.deleteQuietly(plugin.getLogger(), read.dir());
                ops.finish(op, "farm.undone", Placeholder.unparsed("id", id));
            });
        });
    }

    /** From the IO thread: lets the chunks go (if still this operation's) and ends the operation with a reply. */
    private void failFromIo(Op op, String path, TagResolver... resolvers) {
        main.execute(() -> {
            if (ops.isCurrent(op)) {
                chunks.release();
            }
            ops.finishNow(op, path, resolvers);
        });
    }

    private void deleteLater(Path dir) {
        try {
            io.execute(() -> FarmBackupFiles.deleteQuietly(plugin.getLogger(), dir));
        } catch (RuntimeException e) {
            plugin.getLogger().fine("Left a cancelled farm backup behind: " + dir);
        }
    }

    private static int slabCount(String manifest) {
        try {
            return FarmBackup.fromMap(DesignFiles.parseYaml(manifest)).slabs().size();
        } catch (InvalidConfigurationException e) {
            throw new IllegalArgumentException("backup.yml is not valid YAML: " + e.getMessage(), e);
        }
    }

    private Path farmDir(String id) {
        return plugin.getDataFolder().toPath().resolve(BACKUP_DIR).resolve(id);
    }

    /**
     * Runs {@code tick} once per tick inside the operation until it returns true, then {@code done} (in the same
     * step). A stale operation stops the task and lets the chunks go.
     */
    private void start(Op op, String label, BooleanSupplier tick, Runnable done) {
        stopRunning(false);
        Ticked t = new Ticked(label);
        running = t;
        t.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!ops.isCurrent(op)) {
                stop(t);
                chunks.release();
                return;
            }
            ops.step(op, () -> {
                if (tick.getAsBoolean()) {
                    stop(t);
                    done.run();
                } else {
                    ops.hold();
                }
            });
        }, 1, 1);
        ops.hold();
    }

    /** One ticked phase. */
    private static final class Ticked {
        final String label;
        @Nullable BukkitTask task;

        Ticked(String label) {
            this.label = label;
        }
    }

    private void stop(Ticked t) {
        if (t.task != null) {
            t.task.cancel();
        }
        if (running == t) {
            running = null;
        }
    }

    private void stopRunning(boolean interrupted) {
        Ticked t = running;
        if (t == null) {
            return;
        }
        if (interrupted) {
            plugin.getLogger().warning("Farm " + t.label + " was interrupted (reload or shutdown); the area may be"
                    + " half done. /bakery farm undo restores it from the backup.");
        }
        stop(t);
    }

    /** Reload / disable: stops the running phase; a setup interrupted mid-paste is undone with its backup. */
    public void reset() {
        ops.reset();
        stopRunning(true);
        chunks.release();
    }
}
