package dev.casperrs.cookieClicker.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.casperrs.cookieClicker.config.PluginSettings;
import dev.casperrs.cookieClicker.house.HousePlacer;
import dev.casperrs.cookieClicker.plot.Plot;
import dev.casperrs.cookieClicker.plot.PlotOutlines;
import dev.casperrs.cookieClicker.plot.PlotService;
import dev.casperrs.cookieClicker.plot.RegionShape;
import dev.casperrs.cookieClicker.text.Messages;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * {@code /bakery plot setviewpoint|setpedestal|setchimney|show}, {@code /bakery house place <design>|undo} and
 * {@code /bakery reload}, permission {@code cookieempire.admin}. Plot commands act on the plot whose
 * {@code bakery-plot} region the admin stands in. Built for Paper's Brigadier lifecycle registration
 * ({@link #node()}).
 */
public final class BakeryCommand {

    public static final String PERMISSION = "cookieempire.admin";

    /** Result of {@code /bakery reload}: problems found (empty = all good). */
    @FunctionalInterface
    public interface Reloader {
        List<String> reload();
    }

    private final PlotService plots;
    private final PlotOutlines outlines;
    private final HousePlacer houses;
    private final Supplier<PluginSettings> settings;
    private final Messages messages;
    private final Reloader reloader;

    public BakeryCommand(PlotService plots, PlotOutlines outlines, HousePlacer houses,
                         Supplier<PluginSettings> settings, Messages messages, Reloader reloader) {
        this.plots = plots;
        this.outlines = outlines;
        this.houses = houses;
        this.settings = settings;
        this.messages = messages;
        this.reloader = reloader;
    }

    public LiteralCommandNode<CommandSourceStack> node() {
        return Commands.literal("bakery")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(Commands.literal("plot")
                        .then(Commands.literal("setviewpoint").executes(this::setViewpoint))
                        .then(Commands.literal("setpedestal").executes(this::setPedestal))
                        .then(Commands.literal("setchimney").executes(this::setChimney))
                        .then(Commands.literal("show").executes(this::show)))
                .then(Commands.literal("house")
                        .then(Commands.literal("place")
                                .then(Commands.argument("design", StringArgumentType.word())
                                        .executes(this::place)))
                        .then(Commands.literal("undo").executes(this::undo)))
                .then(Commands.literal("reload").executes(this::reload))
                .executes(ctx -> {
                    messages.send(ctx.getSource().getSender(), "commands.usage");
                    return Command.SINGLE_SUCCESS;
                })
                .build();
    }

    private static Player player(CommandSourceStack src) {
        Entity executor = src.getExecutor();
        if (executor instanceof Player p) {
            return p;
        }
        return src.getSender() instanceof Player p ? p : null;
    }

    /** The admin and the plot they stand in, or null after telling them why not. */
    private record Here(Player player, Plot plot) {
    }

    private Here here(CommandContext<CommandSourceStack> ctx) {
        Player player = player(ctx.getSource());
        if (player == null) {
            messages.send(ctx.getSource().getSender(), "commands.players-only");
            return null;
        }
        Optional<String> id = plots.plotIdAt(player.getLocation());
        if (id.isEmpty()) {
            messages.send(player, "plot.not-in-plot");
            return null;
        }
        if (!Plot.validId(id.get())) {
            messages.send(player, "plot.bad-id", Placeholder.unparsed("id", id.get()));
            return null;
        }
        return new Here(player, plots.getOrCreate(id.get(), player.getWorld()));
    }

    private void save(Player player, Plot plot) {
        plots.update(plot, problem -> messages.send(player, "commands.reload.problem",
                Placeholder.unparsed("problem", problem)));
    }

    private int setViewpoint(CommandContext<CommandSourceStack> ctx) {
        Here h = here(ctx);
        if (h == null) {
            return 0;
        }
        Location l = h.player().getLocation();
        save(h.player(), h.plot().withViewpoint(new Plot.Viewpoint(l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch())));
        messages.send(h.player(), "plot.viewpoint-set", Placeholder.unparsed("id", h.plot().id()),
                Placeholder.unparsed("x", fmt(l.getX())), Placeholder.unparsed("y", fmt(l.getY())),
                Placeholder.unparsed("z", fmt(l.getZ())));
        return Command.SINGLE_SUCCESS;
    }

    private int setPedestal(CommandContext<CommandSourceStack> ctx) {
        Here h = here(ctx);
        if (h == null) {
            return 0;
        }
        PluginSettings.StationSettings cfg = settings.get().station();
        Location loc = h.player().getLocation();
        double x = cfg.snapToBlock() ? loc.getBlockX() + 0.5 : loc.getX();
        double y = cfg.snapToBlock() ? loc.getBlockY() : loc.getY();
        double z = cfg.snapToBlock() ? loc.getBlockZ() + 0.5 : loc.getZ();
        float yaw = snapYaw(loc.getYaw(), cfg.snapYawDegrees());
        save(h.player(), h.plot().withPedestal(new Plot.Pedestal(x, y, z, yaw)));
        messages.send(h.player(), "plot.pedestal-set", Placeholder.unparsed("id", h.plot().id()),
                Placeholder.unparsed("x", fmt(x)), Placeholder.unparsed("y", fmt(y)), Placeholder.unparsed("z", fmt(z)));
        return Command.SINGLE_SUCCESS;
    }

    /** The smoke starts at the bottom centre of the cell above the looked-at block (the chimney top). */
    private int setChimney(CommandContext<CommandSourceStack> ctx) {
        Here h = here(ctx);
        if (h == null) {
            return 0;
        }
        int reach = settings.get().plot().chimneyReach();
        Block target = h.player().getTargetBlockExact(reach);
        if (target == null) {
            messages.send(h.player(), "plot.chimney-no-target", Placeholder.unparsed("radius", Integer.toString(reach)));
            return 0;
        }
        double x = target.getX() + 0.5;
        double y = target.getY() + 1.0;
        double z = target.getZ() + 0.5;
        save(h.player(), h.plot().withChimney(new Plot.Point(x, y, z)));
        messages.send(h.player(), "plot.chimney-set", Placeholder.unparsed("id", h.plot().id()),
                Placeholder.unparsed("x", fmt(x)), Placeholder.unparsed("y", fmt(y)), Placeholder.unparsed("z", fmt(z)));
        return Command.SINGLE_SUCCESS;
    }

    private int show(CommandContext<CommandSourceStack> ctx) {
        Here h = here(ctx);
        if (h == null) {
            return 0;
        }
        String id = h.plot().id();
        if (!plots.registry().has(id)) {
            plots.update(h.plot(), problem -> { }); // a flagged region plots.yml doesn't know yet: add it
        }
        Optional<RegionShape> plot = plots.registry().plotShape(id);
        Optional<RegionShape> house = plots.registry().houseShape(id);
        if (plot.isEmpty()) {
            messages.send(h.player(), "plot.show-no-regions", Placeholder.unparsed("id", id));
            return 0;
        }
        if (house.isEmpty()) {
            messages.send(h.player(), "plot.show-no-house", Placeholder.unparsed("id", id));
        }
        outlines.show(h.player(), h.player().getWorld(), plot.get(), house.orElse(null));
        messages.send(h.player(), "plot.show-started", Placeholder.unparsed("id", id),
                Placeholder.unparsed("amount", Integer.toString(settings.get().plot().show().durationSeconds())));
        return Command.SINGLE_SUCCESS;
    }

    private int place(CommandContext<CommandSourceStack> ctx) {
        Here h = here(ctx);
        if (h == null) {
            return 0;
        }
        if (!plots.registry().has(h.plot().id())) {
            plots.update(h.plot(), problem -> { }); // the house needs a plots.yml entry for its chimney point
        }
        houses.place(h.player(), plots.registry().get(h.plot().id()).orElse(h.plot()),
                StringArgumentType.getString(ctx, "design").toLowerCase(Locale.ROOT));
        return Command.SINGLE_SUCCESS;
    }

    private int undo(CommandContext<CommandSourceStack> ctx) {
        Here h = here(ctx);
        if (h == null) {
            return 0;
        }
        houses.undo(h.player(), h.plot());
        return Command.SINGLE_SUCCESS;
    }

    private int reload(CommandContext<CommandSourceStack> ctx) {
        List<String> problems = reloader.reload();
        var sender = ctx.getSource().getSender();
        if (problems.isEmpty()) {
            messages.send(sender, "commands.reload.done");
        } else {
            messages.send(sender, "commands.reload.problems", Placeholder.unparsed("count", Integer.toString(problems.size())));
            for (String problem : problems) {
                messages.send(sender, "commands.reload.problem", Placeholder.unparsed("problem", problem));
            }
        }
        return Command.SINGLE_SUCCESS;
    }

    /** Rounds to the nearest {@code step} degrees (0 = unchanged) and normalises to [-180, 180). */
    static float snapYaw(float yaw, float step) {
        float y = step > 0 ? Math.round(yaw / step) * step : yaw;
        y = ((y % 360) + 360) % 360;
        return y >= 180 ? y - 360 : y;
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }
}
