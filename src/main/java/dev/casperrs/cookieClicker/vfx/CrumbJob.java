package dev.casperrs.cookieClicker.vfx;

import dev.casperrs.cookieClicker.vfx.config.LayerSpec;
import dev.casperrs.cookieClicker.vfx.shape.DisplayTrack;
import org.bukkit.Location;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Small {@link ItemDisplay} "crumbs" that fly out in a cone, tumble and shrink away, all by client-side
 * interpolation (two keyframes each), then get removed.
 */
final class CrumbJob extends DisplayJob {

    private final LayerSpec.ItemCrumbs layer;
    private final ItemStack item;

    CrumbJob(Spawner spawner, DisplayRegistry registry, LayerSpec.ItemCrumbs layer, ItemStack item, Location loc,
             @Nullable Player viewer) {
        super(spawner, registry, loc, viewer, layer.delayTicks());
        this.layer = layer;
        this.item = item;
    }

    @Override
    protected int entityCount() {
        return layer.tracks().size();
    }

    @Override
    protected void spawnAll(Location at, @Nullable Player viewer) {
        Location pos = at.clone().add(layer.ox(), layer.oy(), layer.oz());
        for (DisplayTrack track : layer.tracks()) {
            ItemDisplay crumb = spawner().spawn(pos, ItemDisplay.class, viewer, d -> {
                d.setItemStack(item);
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                d.setViewRange(layer.viewRange());
                d.setTransformation(Transformations.of(track.spawn()));
            });
            add(crumb, track);
        }
    }
}
