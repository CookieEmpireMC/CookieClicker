package dev.casperrs.cookieClicker.sound;

import dev.casperrs.cookieClicker.log.WarnOnce;
import net.kyori.adventure.key.InvalidKeyException;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.random.RandomGenerator;

/**
 * The <b>only</b> place in the plugin that plays sounds. Plays events by name from {@code sounds.yml}; players without
 * our resource pack hear the vanilla fallback. Unknown events are logged once and skipped. Sounds go to one player only
 * (per-player bakery). Main thread only.
 */
public final class SoundService {

    /** An event with its keys parsed once at load; a null key is {@code @block} or invalid (warned when played). */
    private record Compiled(SoundDef def, @Nullable Key key, @Nullable Key fallback, Sound.Source source) {
    }

    private final RandomGenerator random;
    private final Predicate<Player> hasPack;
    private final WarnOnce warnings;
    private Map<String, Compiled> sounds = Map.of();
    /** Place sound key per block material ({@code @block} events); a block's sound group doesn't change. */
    private final Map<Material, Key> blockSounds = new EnumMap<>(Material.class);
    /** Survives {@link #load} (reload): a reload must not restart a loop that is still sounding. */
    private final LoopPacer loops = new LoopPacer();

    /** @param hasPack whether the player has our resource pack loaded (picks key vs fallback) */
    public SoundService(Logger log, RandomGenerator random, Predicate<Player> hasPack) {
        this.random = random;
        this.hasPack = hasPack;
        this.warnings = new WarnOnce(log);
    }

    /** Swap in a freshly parsed {@code sounds.yml} (enable / reload). */
    public void load(Map<String, SoundDef> sounds) {
        Map<String, Compiled> compiled = new HashMap<>();
        sounds.forEach((event, def) -> compiled.put(event,
                new Compiled(def, parse(def.key()), parse(def.fallback()), source(def.source()))));
        this.sounds = Map.copyOf(compiled);
        warnings.clear();
        blockSounds.clear();
    }

    public boolean has(String event) {
        return sounds.containsKey(event);
    }

    /** Plays {@code event} to {@code player}, at the player. */
    public void play(Player player, String event) {
        Sound sound = resolve(player, event);
        if (sound != null) {
            player.playSound(sound, Sound.Emitter.self());
        }
    }

    /** Plays {@code event} to {@code player} only, positioned at {@code at} (e.g. the station's cookie). */
    public void play(Player player, String event, Location at) {
        Sound sound = resolve(player, event);
        if (sound != null) {
            player.playSound(sound, at.getX(), at.getY(), at.getZ());
        }
    }

    /**
     * Plays the looping ambient {@code event} to {@code player} at {@code at}, unless its previous play for them is
     * still sounding: {@code period} is the file's length in ticks at its fixed pitch, {@code now} the server tick.
     * Callers replay it on a timer with that period ({@link LoopPacer}). Returns whether it played.
     */
    public boolean playLoop(Player player, String event, Location at, int period, long now) {
        if (period <= 0 || !loops.tryPlay(player.getUniqueId(), event, period, now)) {
            return false;
        }
        play(player, event, at);
        return true;
    }

    /** Ticks until {@link #playLoop} would play {@code event} for {@code player} again: a new timer's first delay. */
    public long loopDelay(UUID player, String event, int period, long now) {
        return loops.delay(player, event, period, now);
    }

    /** The player quit: their client stopped every sound, so their loops start fresh next time. */
    public void forgetLoops(UUID player) {
        loops.forget(player);
    }

    /**
     * Plays an {@code @block} event (e.g. {@code house_block_place}) to {@code player} only, at {@code at}: the
     * vanilla place sound of {@code block}'s sound group, with the event's volume, pitch range and source. Works with
     * or without our resource pack; the event's fallback is used only when the block's sound can't be resolved.
     * A normal event played through here ignores {@code block}.
     */
    public void playBlock(Player player, String event, Location at, BlockData block) {
        Sound sound = resolve(player, event, block);
        if (sound != null) {
            player.playSound(sound, at.getX(), at.getY(), at.getZ());
        }
    }

    private @Nullable Sound resolve(Player player, String event) {
        return resolve(player, event, null);
    }

    private @Nullable Sound resolve(Player player, String event, @Nullable BlockData block) {
        Compiled c = sounds.get(event);
        if (c == null) {
            warnings.warn(event, "Unknown sound event '" + event + "' (not in sounds.yml); skipped");
            return null;
        }
        SoundDef def = c.def();
        Key key;
        if (def.blockSound()) {
            key = block == null ? null : blockSound(block);
            if (key == null) {
                if (block == null) {
                    warnings.warn(event, "Sound event '" + event + "' is an @block sound but was played without a block");
                }
                key = checked(event, c.fallback(), def.fallback());
            }
        } else {
            key = hasPack.test(player) ? checked(event, c.key(), def.key()) : checked(event, c.fallback(), def.fallback());
        }
        if (key == null) {
            return null;
        }
        float pitch = def.pitchMax() > def.pitchMin()
                ? def.pitchMin() + random.nextFloat() * (def.pitchMax() - def.pitchMin())
                : def.pitchMin();
        return Sound.sound(key, c.source(), def.volume(), pitch);
    }

    private @Nullable Key checked(String event, @Nullable Key key, String keyString) {
        if (key == null && !SoundDef.BLOCK_KEY.equals(keyString)) { // @block without a usable fallback: silent
            warnings.warn(event, "sounds.yml: '" + event + "' has an invalid key '" + keyString + "'; skipped");
        }
        return key;
    }

    private static @Nullable Key parse(String keyString) {
        if (SoundDef.BLOCK_KEY.equals(keyString)) {
            return null;
        }
        try {
            return Key.key(keyString);
        } catch (InvalidKeyException e) {
            return null;
        }
    }

    private @Nullable Key blockSound(BlockData block) {
        Material material = block.getMaterial();
        Key cached = blockSounds.get(material);
        if (cached == null) {
            // SOUND_EVENT is the same registry as the @Obsolete SOUNDS alias; sounds can exist without a key
            NamespacedKey key = Registry.SOUND_EVENT.getKey(block.getSoundGroup().getPlaceSound());
            if (key == null) {
                warnings.warn("@block:" + material, "No place sound for " + material + "; using the fallback");
                return null;
            }
            cached = key;
            blockSounds.put(material, cached);
        }
        return cached;
    }

    static Sound.Source source(String name) {
        String normalized = switch (name) {
            case "records" -> "record";
            case "blocks" -> "block";
            case "players" -> "player";
            default -> name;
        };
        try {
            return Sound.Source.valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Sound.Source.MASTER;
        }
    }
}
