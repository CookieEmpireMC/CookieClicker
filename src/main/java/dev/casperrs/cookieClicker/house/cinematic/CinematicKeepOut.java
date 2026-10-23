package dev.casperrs.cookieClicker.house.cinematic;

import dev.casperrs.cookieClicker.config.HouseSettings;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Holds a player still (or slows them) while their house is invisible but solid (research: fake-blocks Q6): transient
 * {@code MOVEMENT_SPEED} / {@code JUMP_STRENGTH} modifiers under one fixed key. Transient modifiers are never saved
 * with the player, and {@link #clear} runs on every exit path and on join. No walk speed, no game mode changes.
 * Main thread only.
 */
public final class CinematicKeepOut {

    private final NamespacedKey key;

    public CinematicKeepOut(Plugin plugin) {
        this.key = new NamespacedKey(plugin, "house_cinematic");
    }

    public void apply(Player player, HouseSettings.KeepOut mode, double slowAmount) {
        clear(player);
        switch (mode) {
            case HOLD -> {
                add(player, Attribute.MOVEMENT_SPEED, -1.0);
                add(player, Attribute.JUMP_STRENGTH, -1.0);
            }
            case SLOW -> add(player, Attribute.MOVEMENT_SPEED, slowAmount);
            case NONE -> { }
        }
    }

    /** Removes our modifiers (safe when there are none). */
    public void clear(Player player) {
        remove(player, Attribute.MOVEMENT_SPEED);
        remove(player, Attribute.JUMP_STRENGTH);
    }

    private void add(Player player, Attribute attribute, double amount) {
        AttributeInstance a = player.getAttribute(attribute);
        if (a != null && amount != 0) {
            a.addTransientModifier(new AttributeModifier(key, amount, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
        }
    }

    private void remove(Player player, Attribute attribute) {
        AttributeInstance a = player.getAttribute(attribute);
        if (a != null && a.getModifier(key) != null) {
            a.removeModifier(key);
        }
    }
}
