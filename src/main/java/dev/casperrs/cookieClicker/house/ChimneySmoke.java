package dev.casperrs.cookieClicker.house;

import dev.casperrs.cookieClicker.config.HouseSettings;
import dev.casperrs.cookieClicker.plot.Plot;
import dev.casperrs.cookieClicker.sound.SoundService;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The living house for every player in a plot who isn't watching the cinematic: a {@code house_chimney_smoke} loop
 * at every smoke point (with {@code house_chimney_crackle} every {@code house.chimney.crackle-every} plays of the
 * first one), a {@code factory_steam} loop at every steam point (vent starts staggered by {@code house.steam.every}
 * ticks) and the {@code factory_ambient} hum every {@code house.ambient.every} ticks while the player is within
 * {@code house.ambient.radius} of the first steam point (or the house centre). One set per player. The effects
 * engine ends loops by itself on quit, world change, reload and shutdown; {@link HouseCinematics} starts and stops
 * them. Main thread only.
 */
public final class ChimneySmoke {

    public static final String SMOKE = "house_chimney_smoke";
    public static final String CRACKLE = "house_chimney_crackle";
    public static final String STEAM = "factory_steam";
    public static final String AMBIENT = "factory_ambient";

    private final Plugin plugin;
    private final Effects effects;
    private final SoundService sounds;
    private final Supplier<HouseSettings> settings;
    private final Map<UUID, Session> sessions = new HashMap<>();

    /** One player's loops and the tasks that belong to them. */
    private static final class Session {
        final List<EffectHandle> loops = new ArrayList<>();
        final List<BukkitTask> tasks = new ArrayList<>();

        boolean active() {
            return loops.stream().anyMatch(EffectHandle::isActive) || tasks.stream().anyMatch(t -> !t.isCancelled());
        }

        void cancel() {
            loops.forEach(EffectHandle::cancel);
            tasks.forEach(BukkitTask::cancel);
            loops.clear();
            tasks.clear();
        }
    }

    public ChimneySmoke(Plugin plugin, Effects effects, SoundService sounds, Supplier<HouseSettings> settings) {
        this.plugin = plugin;
        this.effects = effects;
        this.sounds = sounds;
        this.settings = settings;
    }

    /**
     * Starts the player's smoke, steam and hum for {@code plot} (no-op when they already run).
     *
     * @param houseCentre where the hum plays when the plot has no steam point, or null (then the first smoke point)
     */
    public void start(Player player, Plot plot, World world, @Nullable Location houseCentre) {
        if (!player.getWorld().equals(world)) {
            return;
        }
        UUID id = player.getUniqueId();
        Session running = sessions.get(id);
        if (running != null && running.active()) {
            return;
        }
        stop(id);
        HouseSettings s = settings.get();
        Session session = new Session();

        if (s.chimney().smoke()) {
            int every = s.chimney().crackleEvery();
            boolean first = true;
            for (Plot.Point c : plot.smoke()) {
                Location at = at(world, c);
                Runnable crackle = null;
                if (first && every > 0) {
                    int[] plays = {0};
                    crackle = () -> {
                        if (plays[0]++ % every == 0) {
                            sounds.play(player, CRACKLE, at);
                        }
                    };
                }
                first = false;
                addLoop(session, effects.loop(SMOKE, at, player, crackle));
            }
        }

        if (s.steam().enabled()) {
            List<Plot.Point> steam = plot.steam();
            for (int i = 0; i < steam.size(); i++) {
                Location at = at(world, steam.get(i));
                long delay = (long) i * s.steam().every();
                if (delay <= 0) {
                    addLoop(session, effects.loop(STEAM, at, player));
                } else {
                    session.tasks.add(Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (sessions.get(id) == session && player.isOnline() && player.getWorld().equals(world)) {
                            addLoop(session, effects.loop(STEAM, at, player));
                        }
                    }, delay));
                }
            }
        }

        HouseSettings.Ambient ambient = s.ambient();
        Location anchor = !plot.steam().isEmpty() ? at(world, plot.steam().getFirst())
                : houseCentre != null ? houseCentre.clone()
                : plot.chimney() != null ? at(world, plot.chimney()) : null;
        if (ambient.enabled() && anchor != null) {
            double r2 = ambient.radius() * ambient.radius();
            session.tasks.add(Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (player.isOnline() && player.getWorld().equals(world)
                        && player.getLocation().distanceSquared(anchor) <= r2) {
                    sounds.play(player, AMBIENT, anchor);
                }
            }, 0L, ambient.every()));
        }

        if (!session.loops.isEmpty() || !session.tasks.isEmpty()) {
            sessions.put(id, session);
        }
    }

    private static void addLoop(Session session, EffectHandle handle) {
        if (handle.isActive()) {
            session.loops.add(handle);
        }
    }

    private static Location at(World world, Plot.Point p) {
        return new Location(world, p.x(), p.y(), p.z());
    }

    public void stop(UUID player) {
        Session s = sessions.remove(player);
        if (s != null) {
            s.cancel();
        }
    }

    public void stopAll() {
        sessions.values().forEach(Session::cancel);
        sessions.clear();
    }
}
