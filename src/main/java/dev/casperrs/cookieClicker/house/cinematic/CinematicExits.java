package dev.casperrs.cookieClicker.house.cinematic;

import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.UUID;

/** The player events that end a running cinematic; each goes through {@link HouseCinematics#abort}. Main thread. */
final class CinematicExits implements Listener {

    private final HouseCinematics cinematics;

    CinematicExits(HouseCinematics cinematics) {
        this.cinematics = cinematics;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        cinematics.clearKeepOut(event.getPlayer()); // transient modifiers never survive a quit, but be sure
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        cinematics.quit(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        if (cinematics.abort(id, true)) {
            cinematics.resumeAmbienceLater(id);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        cinematics.abort(event.getPlayer().getUniqueId(), true); // the client dropped the old world's blocks
    }

    /** Any real teleport ends the cinematic (tiny server position corrections don't). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        if (!cinematics.isRunning(id) || cinematics.isSelfTeleport(id)) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getWorld() != null && from.getWorld().equals(to.getWorld()) && from.distanceSquared(to) < 1.0) {
            return;
        }
        cinematics.abort(id, true, false); // they go where this teleport takes them, in their own game mode
        cinematics.resumeAmbienceLater(id);
    }
}
