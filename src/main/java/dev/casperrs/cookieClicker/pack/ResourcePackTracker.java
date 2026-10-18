package dev.casperrs.cookieClicker.pack;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Remembers which players have a resource pack loaded, so sounds and the HUD can fall back to vanilla
 * when they declined it. Pass 1 only listens; pass 2 sends our pack (and may filter by pack id).
 */
public final class ResourcePackTracker implements Listener {

    private final Set<UUID> loaded = new HashSet<>();

    public boolean hasPack(Player player) {
        return loaded.contains(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onStatus(PlayerResourcePackStatusEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        switch (event.getStatus()) {
            case SUCCESSFULLY_LOADED -> loaded.add(id);
            case ACCEPTED, DOWNLOADED -> {
                // still in progress
            }
            default -> loaded.remove(id);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        loaded.remove(event.getPlayer().getUniqueId());
    }
}
