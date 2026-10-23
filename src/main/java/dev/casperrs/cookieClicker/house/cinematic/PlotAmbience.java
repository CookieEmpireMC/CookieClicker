package dev.casperrs.cookieClicker.house.cinematic;

import dev.casperrs.cookieClicker.config.HouseSettings;
import dev.casperrs.cookieClicker.plot.Plot;
import dev.casperrs.cookieClicker.sound.LoopPlayer;
import dev.casperrs.cookieClicker.sound.SoundService;
import dev.casperrs.cookieClicker.vfx.EffectHandle;
import dev.casperrs.cookieClicker.vfx.Effects;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * What a player in a plot sees and hears while idle: chimney smoke and crackle at the smoke points, staggered steam
 * at the steam points, and the factory hum near the first steam point (or the house centre). The two sounds are
 * fixed-period loops on the shared {@link LoopPlayer} that keep their beat across restarts
 * ({@link SoundService#playLoop}). One set per player;
 * {@link HouseCinematics} starts and stops it, and the effects engine ends its loops on quit, world change, reload
 * and shutdown. Main thread only.
 */
public final class PlotAmbience {

    public static final String SMOKE = "house_chimney_smoke";
    public static final String CRACKLE = "house_chimney_crackle";
    public static final String STEAM = "factory_steam";
    public static final String AMBIENT = "factory_ambient";

    private final Effects effects;
    private final LoopPlayer loopPlayer;
    private final Supplier<HouseSettings> settings;
    private final Map<UUID, Session> sessions = new HashMap<>();

    /** One player's effect loops and sound loops. */
    private static final class Session {
        final List<EffectHandle> loops = new ArrayList<>();
        final List<LoopPlayer.Loop> sounds = new ArrayList<>();

        boolean active() {
            return loops.stream().anyMatch(EffectHandle::isActive) || sounds.stream().anyMatch(LoopPlayer.Loop::isActive);
        }

        void cancel() {
            loops.forEach(EffectHandle::cancel);
            sounds.forEach(LoopPlayer.Loop::cancel);
            loops.clear();
            sounds.clear();
        }
    }

    public PlotAmbience(Plugin plugin, Effects effects, SoundService sounds, Supplier<HouseSettings> settings) {
        this.effects = effects;
        this.loopPlayer = new LoopPlayer(plugin, sounds);
        this.settings = settings;
    }

    /**
     * Starts the player's smoke, steam and hum for {@code plot} (no-op when they already run). Without a steam point
     * the hum plays at {@code houseCentre}, or at the first smoke point when that is null.
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
            for (Plot.Point c : plot.smoke()) {
                addLoop(session, effects.loop(SMOKE, at(world, c), player));
            }
            int crackle = s.chimney().crackleTicks();
            if (crackle > 0 && !plot.smoke().isEmpty()) {
                Location at = at(world, plot.smoke().getFirst());
                loopTimer(session, player, world, CRACKLE, at, crackle, Double.POSITIVE_INFINITY);
            }
        }

        if (s.steam().enabled()) {
            List<Plot.Point> steam = plot.steam();
            for (int i = 0; i < steam.size(); i++) {
                Location at = at(world, steam.get(i));
                int delay = (int) Math.min(Integer.MAX_VALUE, (long) i * s.steam().every());
                addLoop(session, effects.loop(STEAM, at, player, Math.max(0, delay), null));
            }
        }

        HouseSettings.Ambient ambient = s.ambient();
        Location anchor = !plot.steam().isEmpty() ? at(world, plot.steam().getFirst())
                : houseCentre != null ? houseCentre.clone()
                : plot.chimney() != null ? at(world, plot.chimney()) : null;
        if (ambient.enabled() && anchor != null) {
            loopTimer(session, player, world, AMBIENT, anchor, ambient.every(), ambient.radius());
        }

        if (!session.loops.isEmpty() || !session.sounds.isEmpty()) {
            sessions.put(id, session);
        }
    }

    /**
     * A fixed-period loop for one sound: every {@code period} ticks (the file length at its fixed pitch), starting
     * where the player's previous play of it ends, so a restart never overlaps or doubles it
     * ({@link SoundService#playLoop}); a beat is skipped while the player is away or out of range.
     */
    private void loopTimer(Session session, Player player, World world, String event, Location at, int period,
                           double radius) {
        double r2 = radius * radius;
        session.sounds.add(loopPlayer.start(player, event, at, period, () -> player.isOnline()
                && player.getWorld().equals(world) && player.getLocation().distanceSquared(at) <= r2));
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
        loopPlayer.stopAll();
    }
}
