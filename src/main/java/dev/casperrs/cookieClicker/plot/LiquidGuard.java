package dev.casperrs.cookieClicker.plot;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFromToEvent;

/**
 * Water and lava never flow inside the plots: designs (house ground, ponds in the decor) place liquids with physics
 * off, and this keeps them where they were put when a later block update would make them spread. Uses the cheap
 * {@link PlotRegistry#mayContainPlot} bounds, so a flow anywhere else costs one cached box check.
 */
public final class LiquidGuard implements Listener {

    private final PlotRegistry plots;

    public LiquidGuard(PlotRegistry plots) {
        this.plots = plots;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        Block from = event.getBlock();
        if (from.getType() == Material.DRAGON_EGG) {
            return; // the same event fires for a dragon egg teleporting; not a liquid
        }
        if (plots.mayContainPlot(from.getWorld(), from.getX(), from.getY(), from.getZ())) {
            event.setCancelled(true);
        }
    }
}
