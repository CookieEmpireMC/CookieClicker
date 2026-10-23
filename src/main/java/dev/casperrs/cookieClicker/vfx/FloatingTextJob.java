package dev.casperrs.cookieClicker.vfx;

import dev.casperrs.cookieClicker.vfx.config.LayerSpec;
import dev.casperrs.cookieClicker.vfx.shape.DisplayTrack;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import net.kyori.adventure.text.Component;
import org.jspecify.annotations.Nullable;

/**
 * A floating {@link TextDisplay} ("+15"): spawns small, pops in, rises with ease-out via client-side translation
 * interpolation, then shrinks (interpolated) and steps its text opacity down (opacity isn't interpolated).
 */
final class FloatingTextJob extends DisplayJob {

    private final LayerSpec.FloatingText layer;
    private final Component text;
    private final double jitterX;
    private final double jitterZ;
    private @Nullable TextDisplay display;

    FloatingTextJob(Spawner spawner, DisplayRegistry registry, LayerSpec.FloatingText layer, Component text,
                    Location loc, @Nullable Player viewer, double jitterX, double jitterZ) {
        super(spawner, registry, loc, viewer, layer.delayTicks());
        this.layer = layer;
        this.text = text;
        this.jitterX = jitterX;
        this.jitterZ = jitterZ;
    }

    @Override
    protected int entityCount() {
        return 1;
    }

    @Override
    protected void spawnAll(Location at, @Nullable Player viewer) {
        Location pos = at.clone().add(layer.ox() + jitterX, layer.oy(), layer.oz() + jitterZ);
        DisplayTrack track = layer.track();
        display = spawner().spawn(pos, TextDisplay.class, viewer, d -> {
            d.text(text);
            d.setBillboard(layer.billboard());
            d.setShadowed(layer.shadow());
            d.setSeeThrough(layer.seeThrough());
            d.setDefaultBackground(false);
            d.setBackgroundColor(layer.background());
            d.setViewRange(layer.viewRange());
            d.setTransformation(Transformations.of(track.spawn()));
        });
        add(display, track);
    }

    @Override
    protected void extra(int t) {
        if (display == null) {
            return;
        }
        for (DisplayTrack.OpacityStep step : layer.track().opacity()) {
            if (step.tick() == t) {
                display.setTextOpacity((byte) step.opacity());
            }
        }
    }
}
