package dev.casperrs.cookieClicker.vfx;

import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;

/**
 * Cleanup paths that don't wait for an effect to finish: chunk unload (remove our entities now), chunk load (remove
 * any tagged leftover that isn't ours anymore, e.g. after a crash) and viewer quit (remove their effects).
 */
public final class VfxListener implements Listener {

    private final DisplayRegistry registry;
    private final Spawner spawner;

    VfxListener(DisplayRegistry registry, Spawner spawner) {
        this.registry = registry;
        this.spawner = spawner;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesUnload(EntitiesUnloadEvent event) {
        for (Entity entity : event.getEntities()) {
            DisplayJob job = registry.job(entity.getUniqueId());
            if (job != null) {
                job.cancel(); // removes every entity of that effect and releases it
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (spawner.isOurs(entity) && !registry.tracks(entity.getUniqueId())) {
                entity.remove();
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        registry.cancelViewer(event.getPlayer().getUniqueId());
    }
}
