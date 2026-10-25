package dev.casperrs.cookieClicker.farm;

import dev.casperrs.cookieClicker.plot.Plot;
import dev.casperrs.cookieClicker.plot.PlotService;
import dev.casperrs.cookieClicker.vfx.EffectHandle;
import dev.casperrs.cookieClicker.vfx.Effects;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * Chimney smoke at the countryside places' {@code smoke} points, through the factory's smoke loop
 * ({@code house_chimney_smoke}, per viewer like the factory). A player gets a place's smoke while within
 * {@value #RANGE} blocks of it and loses it when they leave; {@code house.chimney.smoke: false} turns it off. One
 * shared task every {@value #CHECK_TICKS} ticks, running only while some farm has a smoke point. Main thread.
 */
public final class FarmAmbience {

    public static final String SMOKE = "house_chimney_smoke";
    /** The factory's ambience radius, so both smoke the same way. */
    private static final double RANGE = 48;
    private static final long CHECK_TICKS = 20;

    private final Plugin plugin;
    private final Effects effects;
    private final FarmRegistry farms;
    private final BooleanSupplier smokeOn;
    private final Map<UUID, Map<Plot.Point, EffectHandle>> loops = new HashMap<>();
    private List<Source> sources = List.of();
    private @Nullable BukkitTask task;

    /** One smoke point with its world. */
    private record Source(World world, Plot.Point at) {
    }

    public FarmAmbience(Plugin plugin, Effects effects, FarmRegistry farms, BooleanSupplier smokeOn) {
        this.plugin = plugin;
        this.effects = effects;
        this.farms = farms;
        this.smokeOn = smokeOn;
    }

    /** Re-reads the smoke points (farm change, reload) and starts or stops the task. */
    public void restart() {
        stopAll();
        List<Source> found = new ArrayList<>();
        for (Farm farm : farms.all()) {
            World world = PlotService.worldByKey(farm.worldKey()).orElse(null);
            if (world != null) {
                farm.places().values().forEach(p -> p.smoke().forEach(s -> found.add(new Source(world, s))));
            }
        }
        sources = List.copyOf(found);
        if (!sources.isEmpty() && smokeOn.getAsBoolean()) {
            task = Bukkit.getScheduler().runTaskTimer(plugin, this::check, CHECK_TICKS, CHECK_TICKS);
        }
    }

    private void check() {
        double r2 = RANGE * RANGE;
        for (Player p : Bukkit.getOnlinePlayers()) {
            Location loc = p.getLocation();
            Map<Plot.Point, EffectHandle> own = loops.get(p.getUniqueId());
            for (Source s : sources) {
                boolean near = s.world().equals(loc.getWorld()) && distanceSquared(loc, s.at()) <= r2;
                EffectHandle h = own == null ? null : own.get(s.at());
                if (near && (h == null || !h.isActive())) {
                    EffectHandle started = effects.loop(SMOKE, new Location(s.world(), s.at().x(), s.at().y(),
                            s.at().z()), p);
                    if (started.isActive()) {
                        own = own == null ? new HashMap<>() : own;
                        own.put(s.at(), started);
                        loops.put(p.getUniqueId(), own);
                    }
                } else if (!near && h != null) {
                    h.cancel();
                    own.remove(s.at());
                }
            }
        }
        Iterator<Map.Entry<UUID, Map<Plot.Point, EffectHandle>>> it = loops.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Map<Plot.Point, EffectHandle>> e = it.next();
            if (Bukkit.getPlayer(e.getKey()) == null || e.getValue().isEmpty()) {
                e.getValue().values().forEach(EffectHandle::cancel);
                it.remove();
            }
        }
    }

    private static double distanceSquared(Location loc, Plot.Point p) {
        double dx = loc.getX() - p.x();
        double dy = loc.getY() - p.y();
        double dz = loc.getZ() - p.z();
        return dx * dx + dy * dy + dz * dz;
    }

    /** Reload and disable: cancels the task and every loop. */
    public void stopAll() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        loops.values().forEach(m -> m.values().forEach(EffectHandle::cancel));
        loops.clear();
    }
}
