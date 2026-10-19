package dev.casperrs.cookieClicker.house;

import dev.casperrs.cookieClicker.config.HouseSettings;
import dev.casperrs.cookieClicker.plot.Plot;
import dev.casperrs.cookieClicker.sound.SoundService;
import dev.casperrs.cookieClicker.vfx.EffectHandle;
import dev.casperrs.cookieClicker.vfx.Effects;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Chimney smoke (brief Decision 3): every player in a plot who isn't watching the cinematic gets their own
 * {@code house_chimney_smoke} loop at the plot's chimney point, with {@code house_chimney_crackle} replayed every
 * {@code house.chimney.crackle-every} plays. At most one loop per player. The effects engine ends loops by itself on
 * quit, world change, reload and shutdown; {@link HouseCinematics} starts and stops them. Main thread only.
 */
public final class ChimneySmoke {

    public static final String SMOKE = "house_chimney_smoke";
    public static final String CRACKLE = "house_chimney_crackle";

    private final Effects effects;
    private final SoundService sounds;
    private final Supplier<HouseSettings.Chimney> settings;
    private final Map<UUID, EffectHandle> loops = new HashMap<>();

    public ChimneySmoke(Effects effects, SoundService sounds, Supplier<HouseSettings.Chimney> settings) {
        this.effects = effects;
        this.sounds = sounds;
        this.settings = settings;
    }

    /** Starts the player's smoke at {@code plot}'s chimney (no-op when it already runs or there is no chimney). */
    public void start(Player player, Plot plot, World world) {
        HouseSettings.Chimney s = settings.get();
        Plot.Point c = plot.chimney();
        if (!s.smoke() || c == null || !player.getWorld().equals(world)) {
            return;
        }
        EffectHandle running = loops.get(player.getUniqueId());
        if (running != null && running.isActive()) {
            return;
        }
        Location at = new Location(world, c.x(), c.y(), c.z());
        int every = s.crackleEvery();
        int[] plays = {0};
        EffectHandle handle = effects.loop(SMOKE, at, player, () -> {
            if (every > 0 && plays[0]++ % every == 0) {
                sounds.play(player, CRACKLE, at);
            }
        });
        if (handle.isActive()) {
            loops.put(player.getUniqueId(), handle);
        } else {
            loops.remove(player.getUniqueId());
        }
    }

    public void stop(UUID player) {
        EffectHandle h = loops.remove(player);
        if (h != null) {
            h.cancel();
        }
    }

    public void stopAll() {
        loops.values().forEach(EffectHandle::cancel);
        loops.clear();
    }
}
