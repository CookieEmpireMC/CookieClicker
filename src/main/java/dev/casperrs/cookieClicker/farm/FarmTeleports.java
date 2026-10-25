package dev.casperrs.cookieClicker.farm;

import dev.casperrs.cookieClicker.plot.Plot;
import dev.casperrs.cookieClicker.plot.PlotPresenceListener;
import dev.casperrs.cookieClicker.plot.PlotRegistry;
import dev.casperrs.cookieClicker.plot.PlotService;
import dev.casperrs.cookieClicker.text.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.IntSupplier;

/**
 * {@code /bakery farm} (to the countryside's arrival viewpoint) and {@code /bakery home} (back to the player's bakery
 * viewpoint), with one shared cooldown per player. A player's plot is the last plot they entered this session, else
 * the first plot with a viewpoint; with several farms the one whose id equals that plot id goes first. Main thread.
 */
public final class FarmTeleports implements Listener, PlotPresenceListener {

    private static final long MILLIS_PER_SECOND = 1000;

    private final FarmRegistry farms;
    private final PlotRegistry plots;
    private final Messages messages;
    private final IntSupplier cooldownSeconds;
    private final Map<UUID, Long> lastTeleport = new HashMap<>();
    private final Map<UUID, String> lastPlot = new HashMap<>();

    public FarmTeleports(FarmRegistry farms, PlotRegistry plots, Messages messages, IntSupplier cooldownSeconds) {
        this.farms = farms;
        this.plots = plots;
        this.messages = messages;
        this.cooldownSeconds = cooldownSeconds;
    }

    public void toFarm(Player player) {
        Optional<Farm> farm = farms.forPlot(plotOf(player));
        Optional<Plot.Viewpoint> at = farm.flatMap(Farm::arrival);
        if (farm.isEmpty() || at.isEmpty()) {
            messages.send(player, "farm.none");
            return;
        }
        teleport(player, farm.get().worldKey(), at.get(), "farm.teleported");
    }

    public void home(Player player) {
        Plot plot = Optional.ofNullable(plotOf(player)).flatMap(plots::get).orElse(null);
        if (plot == null || plot.viewpoint() == null) {
            messages.send(player, "farm.no-home");
            return;
        }
        teleport(player, plot.worldKey(), plot.viewpoint(), "farm.home");
    }

    private void teleport(Player player, String worldKey, Plot.Viewpoint v, String donePath) {
        long now = System.currentTimeMillis();
        long wait = cooldownSeconds.getAsInt() * MILLIS_PER_SECOND - (now - lastTeleport.getOrDefault(
                player.getUniqueId(), Long.MIN_VALUE / 2));
        if (wait > 0) {
            messages.send(player, "farm.cooldown",
                    Placeholder.unparsed("seconds", Long.toString((wait + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND)));
            return;
        }
        World world = PlotService.worldByKey(worldKey).orElse(null);
        if (world == null) {
            messages.send(player, "farm.world-missing");
            return;
        }
        lastTeleport.put(player.getUniqueId(), now);
        UUID id = player.getUniqueId();
        player.teleportAsync(new Location(world, v.x(), v.y(), v.z(), v.yaw(), v.pitch()),
                PlayerTeleportEvent.TeleportCause.COMMAND).thenAccept(ok -> {
                    Player p = Bukkit.getPlayer(id); // completes on the main thread
                    if (p != null && Boolean.TRUE.equals(ok)) {
                        messages.send(p, donePath);
                    }
                });
    }

    /** The last plot entered this session, else the first plot with a viewpoint. */
    private @Nullable String plotOf(Player player) {
        String last = lastPlot.get(player.getUniqueId());
        if (last != null && plots.has(last)) {
            return last;
        }
        return plots.all().stream().filter(p -> p.viewpoint() != null).map(Plot::id).findFirst().orElse(null);
    }

    @Override
    public void onEnterPlot(Player player, String plotId) {
        lastPlot.put(player.getUniqueId(), plotId);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        lastTeleport.remove(event.getPlayer().getUniqueId());
        lastPlot.remove(event.getPlayer().getUniqueId());
    }
}
