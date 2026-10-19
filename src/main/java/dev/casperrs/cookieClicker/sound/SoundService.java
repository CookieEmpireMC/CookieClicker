package dev.casperrs.cookieClicker.sound;

import net.kyori.adventure.key.InvalidKeyException;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.random.RandomGenerator;

/**
 * The <b>only</b> place in the plugin that plays sounds. Plays events by name from {@code sounds.yml};
 * players who haven't loaded our resource pack hear the vanilla fallback. Unknown events are logged
 * once and skipped. Sounds go to one player only (per-player bakery). Main thread only.
 */
public final class SoundService {

    private final Logger log;
    private final RandomGenerator random;
    private final Predicate<Player> hasPack;
    private final Set<String> warned = new HashSet<>();
    private Map<String, SoundDef> sounds = Map.of();
    /** Place sound key per block material ({@code @block} events); a block's sound group doesn't change. */
    private final Map<Material, Key> blockSounds = new EnumMap<>(Material.class);

    /** @param hasPack whether the player has our resource pack loaded (picks key vs fallback) */
    public SoundService(Logger log, RandomGenerator random, Predicate<Player> hasPack) {
        this.log = log;
        this.random = random;
        this.hasPack = hasPack;
    }

    /** Swap in a freshly parsed {@code sounds.yml} (enable / reload). */
    public void load(Map<String, SoundDef> sounds) {
        this.sounds = Map.copyOf(sounds);
        warned.clear();
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
        SoundDef def = sounds.get(event);
        if (def == null) {
            warnOnce(event, "Unknown sound event '" + event + "' (not in sounds.yml); skipped");
            return null;
        }
        Key key;
        if (def.blockSound()) {
            key = block == null ? null : blockSound(block);
            if (key == null) {
                if (block == null) {
                    warnOnce(event, "Sound event '" + event + "' is an @block sound but was played without a block");
                }
                key = parse(event, def.fallback());
            }
        } else {
            key = parse(event, hasPack.test(player) ? def.key() : def.fallback());
        }
        if (key == null) {
            return null;
        }
        float pitch = def.pitchMax() > def.pitchMin()
                ? def.pitchMin() + random.nextFloat() * (def.pitchMax() - def.pitchMin())
                : def.pitchMin();
        return Sound.sound(key, source(def.source()), def.volume(), pitch);
    }

    private @Nullable Key parse(String event, String keyString) {
        if (SoundDef.BLOCK_KEY.equals(keyString)) {
            return null; // @block without a usable fallback: silent
        }
        try {
            return Key.key(keyString);
        } catch (InvalidKeyException e) {
            warnOnce(event, "sounds.yml: '" + event + "' has an invalid key '" + keyString + "'; skipped");
            return null;
        }
    }

    private @Nullable Key blockSound(BlockData block) {
        Material material = block.getMaterial();
        Key cached = blockSounds.get(material);
        if (cached == null) {
            try {
                cached = block.getSoundGroup().getPlaceSound().key();
                blockSounds.put(material, cached);
            } catch (RuntimeException e) { // unregistered sound in a custom/modded group: use the fallback
                warnOnce("@block:" + material, "No place sound for " + material + "; using the fallback");
                return null;
            }
        }
        return cached;
    }

    private void warnOnce(String event, String message) {
        if (warned.add(event)) {
            log.warning(message);
        }
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
