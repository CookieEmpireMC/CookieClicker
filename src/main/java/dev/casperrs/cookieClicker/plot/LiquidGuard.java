package dev.casperrs.cookieClicker.plot;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFromToEvent;

/**
 * Water and lava never flow inside the plots: designs (house ground, ponds in the decor) place liquids with physics
 * off, and this keeps them where they were put when a later block update would make them spread. Only flows whose
 * source lies where a paste writes ({@link PlotRegistry#inPasteArea}: a farm region and its ground, a plot's own plot
 * region, house region and ground, house box) are stopped; natural water between and around plots flows as usual.
 * A flow far from every plot costs one cached box check.
 */
public final class LiquidGuard implements Listener {

    private final PlotRegistry plots;

    public LiquidGuard(PlotRegistry plots) {
        this.plots = plots;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        Block from = event.getBlock();
        if (plots.inPasteArea(from.getWorld(), from.getX(), from.getY(), from.getZ())
                && from.getType() != Material.DRAGON_EGG) { // the same event fires for a dragon egg teleporting
            event.setCancelled(true);
        }
    }
}
