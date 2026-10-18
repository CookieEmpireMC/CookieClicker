package dev.casperrs.cookieClicker.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.casperrs.cookieClicker.config.PluginSettings;
import dev.casperrs.cookieClicker.station.Station;
import dev.casperrs.cookieClicker.station.StationFile;
import dev.casperrs.cookieClicker.station.StationRegistry;
import dev.casperrs.cookieClicker.text.Messages;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * {@code /bakery station set|remove} and {@code /bakery reload}, permission {@code cookieempire.admin}.
 * Built for Paper's Brigadier lifecycle registration ({@link #node()}).
 */
public final class BakeryCommand {

    public static final String PERMISSION = "cookieempire.admin";

    /** Result of {@code /bakery reload}: problems found (empty = all good). */
    @FunctionalInterface
    public interface Reloader {
        List<String> reload();
    }

    private final StationRegistry stations;
    private final StationFile stationFile;
    private final Supplier<PluginSettings> settings;
    private final Messages messages;
    private final Reloader reloader;

    public BakeryCommand(StationRegistry stations, StationFile stationFile, Supplier<PluginSettings> settings,
                         Messages messages, Reloader reloader) {
        this.stations = stations;
        this.stationFile = stationFile;
        this.settings = settings;
        this.messages = messages;
        this.reloader = reloader;
    }

    public LiteralCommandNode<CommandSourceStack> node() {
        return Commands.literal("bakery")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(Commands.literal("station")
                        .then(Commands.literal("set").executes(this::set))
                        .then(Commands.literal("remove").executes(this::remove)))
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

    private int set(CommandContext<CommandSourceStack> ctx) {
        Player player = player(ctx.getSource());
        if (player == null) {
            messages.send(ctx.getSource().getSender(), "commands.players-only");
            return 0;
        }
        PluginSettings.StationSettings cfg = settings.get().station();
        Location loc = player.getLocation();
        double x = cfg.snapToBlock() ? loc.getBlockX() + 0.5 : loc.getX();
        double y = cfg.snapToBlock() ? loc.getBlockY() : loc.getY();
        double z = cfg.snapToBlock() ? loc.getBlockZ() + 0.5 : loc.getZ();
        float yaw = snapYaw(loc.getYaw(), cfg.snapYawDegrees());
        String world = player.getWorld().getKey().asString();

        if (cfg.minDistance() > 0 && stations.nearest(world, x, y, z, cfg.minDistance()).isPresent()) {
            messages.send(player, "commands.station.too-close", Placeholder.unparsed("distance", fmt(cfg.minDistance())));
            return 0;
        }
        Station station = new Station(stations.nextId(), world, x, y, z, yaw);
        stations.add(station);
        stationFile.saveAsync(stations.all());
        messages.send(player, "commands.station.set",
                Placeholder.unparsed("id", station.id()),
                Placeholder.unparsed("world", world),
                Placeholder.unparsed("x", fmt(x)), Placeholder.unparsed("y", fmt(y)), Placeholder.unparsed("z", fmt(z)));
        return Command.SINGLE_SUCCESS;
    }

    private int remove(CommandContext<CommandSourceStack> ctx) {
        Player player = player(ctx.getSource());
        if (player == null) {
            messages.send(ctx.getSource().getSender(), "commands.players-only");
            return 0;
        }
        double radius = settings.get().station().removeRadius();
        Location loc = player.getLocation();
        String world = player.getWorld().getKey().asString();
        Station nearest = stations.nearest(world, loc.getX(), loc.getY(), loc.getZ(), radius).orElse(null);
        if (nearest == null) {
            messages.send(player, "commands.station.none-nearby", Placeholder.unparsed("radius", fmt(radius)));
            return 0;
        }
        stations.remove(nearest.id());
        stationFile.saveAsync(stations.all());
        messages.send(player, "commands.station.removed", Placeholder.unparsed("id", nearest.id()));
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
