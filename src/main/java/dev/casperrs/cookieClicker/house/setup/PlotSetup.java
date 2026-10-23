package dev.casperrs.cookieClicker.house.setup;

import dev.casperrs.cookieClicker.house.design.DesignFiles;
import dev.casperrs.cookieClicker.house.setup.WorldOperation.Op;
import dev.casperrs.cookieClicker.plot.Plot;
import dev.casperrs.cookieClicker.plot.PlotRegistry;
import dev.casperrs.cookieClicker.plot.PlotService;
import dev.casperrs.cookieClicker.text.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * {@code /bakery plot setup [design]} and {@code /bakery plot setup undo}, the second world writer: builds a whole
 * plot from the design's fixed layout, centred on the plot region ({@link SetupPlanner}). A setup first undoes the
 * plot's previous one. Every replaced block is backed up under {@code setup-backups/<plot>/} before the first block
 * changes, and undo restores the world exactly. Files on IO, world on main, one operation at a time.
 */
public final class PlotSetup {

    public static final String BACKUP_DIR = "setup-backups";
    public static final String DEFAULT_DESIGN = DesignFiles.DEFAULT_DESIGN;

    private final JavaPlugin plugin;
    private final PlotRegistry plots;
    private final Consumer<Plot> store;
    private final Messages messages;
    private final Executor main;
    private final SetupPlanner planner;
    private final WorldOperation ops;

    /** {@code store} saves a changed plot everywhere (registry, stations, plots.yml). */
    public PlotSetup(JavaPlugin plugin, PlotRegistry plots, Consumer<Plot> store, Messages messages, Executor io,
                     Executor main, IntSupplier maxBlocks, Supplier<Map<String, Double>> footprints) {
        this.plugin = plugin;
        this.plots = plots;
        this.store = store;
        this.messages = messages;
        this.main = main;
        this.planner = new SetupPlanner(plugin.getLogger(), plots, maxBlocks, footprints);
        this.ops = new WorldOperation(plugin.getLogger(), messages, io, main, "plot.setup.busy", "plot.setup.failed",
                "Plot setup");
    }

    public void setup(CommandSender sender, Plot plot, String designName) {
        if (!HousePlacer.validDesignName(designName)) {
            messages.send(sender, "house.bad-design-name", Placeholder.unparsed("name", designName));
            return;
        }
        if (plots.plotShape(plot.id()).isEmpty()) {
            messages.send(sender, "plot.setup.no-plot-region", Placeholder.unparsed("id", plot.id()));
            return;
        }
        Op op = ops.begin(sender);
        if (op == null) {
            return;
        }
        String plotId = plot.id();
        Path dir = backupDir(plotId);
        ops.submit(op, () -> {
            SetupFiles.Inputs in;
            try {
                in = SetupFiles.read(plugin, dir, designName);
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Could not read the setup files of plot " + plotId, e);
                ops.finish(op, "plot.setup.failed", WorldOperation.problem(e.getMessage()));
                return;
            }
            if (in.design() == null) {
                ops.finish(op, "house.design-not-found", Placeholder.unparsed("name", designName));
            } else if (in.fence() == null) {
                ops.finish(op, "plot.setup.no-fence-style");
            } else {
                main.execute(() -> ops.step(op, () -> build(op, plotId, designName, in)));
            }
        });
    }

    /** Main thread: check, undo the previous setup, plan; then write the backup on IO. */
    private void build(Op op, String plotId, String designName, SetupFiles.Inputs in) {
        SetupPlanner.Checked checked;
        try {
            checked = planner.check(plotId, designName, in);
        } catch (SetupRefusal r) {
            ops.finishNow(op, r.path(), r.resolvers());
            return;
        }
        Plot before = checked.plot();
        SetupBackup old = checked.old();
        if (old != null) {
            restore(checked.world(), old, in.old().nbt());
            store.accept(old.previousPlot());
            before = old.previousPlot();
        }
        SetupPlan plan;
        try {
            plan = planner.plan(checked, before, in);
        } catch (SetupRefusal r) {
            abandon(op, old != null, in, r);
            return;
        }
        writeBackup(op, checked.world(), plan, in);
    }

    private void writeBackup(Op op, World world, SetupPlan plan, SetupFiles.Inputs in) {
        String plotId = plan.updated().id();
        String text = BackupFiles.toYaml(plan.backup().toMap());
        Path yml = backupDir(plotId).resolve(plan.backup().createdAt() + ".yml");
        ops.submit(op, () -> {
            try {
                SetupFiles.deleteOld(in);
                BackupFiles.write(yml, text, plan.structure());
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not write the setup backup of plot " + plotId
                        + "; nothing was built", e);
                BackupFiles.deleteQuietly(plugin.getLogger(), yml);
                ops.finish(op, "plot.setup.failed", Placeholder.unparsed("problem",
                        "could not save the undo backup (" + e.getMessage() + ")"));
                return;
            }
            main.execute(() -> ops.stepOrDiscard(op, yml, () -> apply(op, world, plan, yml)));
        });
    }

    /** Main thread, once the backup is on disk: set the blocks, sign text and the plot's points. */
    private void apply(Op op, World world, SetupPlan plan, Path yml) {
        Plot updated = plan.updated();
        for (HouseBackup.Change c : plan.changes()) {
            if (!world.isChunkLoaded(c.x() >> 4, c.z() >> 4)) {
                ops.deleteLater(yml);
                ops.finishNow(op, "plot.setup.not-loaded", Placeholder.unparsed("id", updated.id()));
                return;
            }
        }
        HouseBackup.BlockAccess access = HousePlacer.worldAccess(world, new HashMap<>());
        for (HouseBackup.Change c : plan.changes()) {
            access.set(c.x(), c.y(), c.z(), c.newData());
        }
        HousePlacer.writeSigns(plugin, world, plan.design(), plan.houseAt());
        store.accept(updated);
        Plot.Pedestal p = updated.pedestal();
        Plot.Point e = updated.entrance();
        ops.finishNow(op, "plot.setup.done", Placeholder.unparsed("id", updated.id()),
                Placeholder.unparsed("name", plan.design().name()),
                Placeholder.unparsed("ground", Integer.toString(plan.groundY())),
                Placeholder.unparsed("count", Integer.toString(plan.changes().size())),
                Placeholder.unparsed("pedestal", p == null ? "-" : xyz(p.x(), p.y(), p.z())),
                Placeholder.unparsed("entrance", e == null ? "-" : xyz(e.x(), e.y(), e.z())),
                Placeholder.unparsed("smoke", Integer.toString(updated.smoke().size())),
                Placeholder.unparsed("steam", Integer.toString(updated.steam().size())));
    }

    /**
     * A check failed after the previous setup was already undone: the world and plots.yml now match the state
     * before it, so its backup files go; then reply.
     */
    private void abandon(Op op, boolean oldRestored, SetupFiles.Inputs in, SetupRefusal refusal) {
        if (!oldRestored) {
            ops.finishNow(op, refusal.path(), refusal.resolvers());
            return;
        }
        ops.submit(op, () -> {
            try {
                SetupFiles.deleteOld(in);
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Could not delete the used setup backup " + in.old().yml(), e);
            }
            ops.finish(op, refusal.path(), refusal.resolvers());
        });
    }

    public void undo(CommandSender sender, Plot plot) {
        Op op = ops.begin(sender);
        if (op == null) {
            return;
        }
        String plotId = plot.id();
        Path dir = backupDir(plotId);
        ops.submit(op, () -> {
            BackupFiles.Read read;
            try {
                read = BackupFiles.readNewest(dir);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not read the setup backups of plot " + plotId, e);
                ops.finish(op, "plot.setup.undo-failed", WorldOperation.problem(e.getMessage()));
                return;
            }
            if (read == null) {
                ops.finish(op, "plot.setup.nothing-to-undo", Placeholder.unparsed("id", plotId));
                return;
            }
            main.execute(() -> ops.step(op, () -> undoNow(op, plotId, read)));
        });
    }

    private void undoNow(Op op, String plotId, BackupFiles.Read read) {
        SetupBackup backup;
        try {
            backup = SetupBackup.fromMap(DesignFiles.parseYaml(read.text()));
        } catch (InvalidConfigurationException | IllegalArgumentException e) {
            ops.finishNow(op, "plot.setup.undo-failed", Placeholder.unparsed("problem",
                    read.yml().getFileName() + ": " + e.getMessage()));
            return;
        }
        World world = PlotService.worldByKey(backup.worldKey()).orElse(null);
        if (world == null || !SetupPlanner.loaded(world, backup)) {
            ops.finishNow(op, "plot.setup.not-loaded", Placeholder.unparsed("id", plotId));
            return;
        }
        restore(world, backup, read.nbt());
        store.accept(backup.previousPlot());
        int count = backup.cells().size();
        ops.submit(op, () -> {
            BackupFiles.deleteQuietly(plugin.getLogger(), read.yml());
            ops.finish(op, "plot.setup.undone", Placeholder.unparsed("id", plotId),
                    Placeholder.unparsed("count", Integer.toString(count)));
        });
    }

    /** Block data of every changed block, then the house box structure (brings back tile contents). Main thread. */
    private void restore(World world, SetupBackup backup, byte @Nullable [] nbt) {
        backup.restoreCells(HousePlacer.worldAccess(world, new HashMap<>()));
        if (nbt == null) {
            return;
        }
        try {
            BoxStructures.place(world, backup.houseCorner(), nbt);
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Could not place the house structure backup of plot "
                    + backup.plotId() + "; block data was restored, tile contents are lost", e);
        }
    }

    /** Reload / disable: cancels the running operation (its late callbacks change nothing). */
    public void reset() {
        ops.reset();
    }

    private Path backupDir(String plotId) {
        return plugin.getDataFolder().toPath().resolve(BACKUP_DIR).resolve(plotId);
    }

    private static String xyz(double x, double y, double z) {
        return String.format(Locale.ROOT, "%.1f %.1f %.1f", x, y, z);
    }
}
