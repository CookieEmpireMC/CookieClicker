package dev.casperrs.cookieClicker.vfx;

import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;

/**
 * Cleanup paths that don't wait for an effect to finish: chunk unload (remove our entities now), chunk load (remove
 * any tagged leftover that isn't ours anymore, e.g. after a crash) and viewer quit (remove their effects, falling
 * blocks and loops). Falling blocks in an unloading chunk need nothing here: their job sees the display is gone on
 * the next tick and lands it.
 */
public final class VfxListener implements Listener {

    private final DisplayRegistry registry;
    private final Spawner spawner;
    private final Predicate<UUID> tracked;
    private final Consumer<UUID> viewerQuit;

    /**
     * @param tracked    true for entity IDs of running effects (display effects and falling blocks)
     * @param viewerQuit stops everything shown to that viewer (display effects, falling blocks, loops)
     */
    VfxListener(DisplayRegistry registry, Spawner spawner, Predicate<UUID> tracked, Consumer<UUID> viewerQuit) {
        this.registry = registry;
        this.spawner = spawner;
        this.tracked = tracked;
        this.viewerQuit = viewerQuit;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesUnload(EntitiesUnloadEvent event) {
        if (registry.isEmpty()) {
            return; // no effect running: nothing in this chunk can be ours
        }
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
            if (spawner.isOurs(entity) && !tracked.test(entity.getUniqueId())) {
                entity.remove();
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        viewerQuit.accept(event.getPlayer().getUniqueId());
    }
}
