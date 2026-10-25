package dev.casperrs.cookieClicker.config;

import org.bukkit.configuration.ConfigurationSection;

import java.util.function.Consumer;

/**
 * {@code config.yml farm.*}: the countryside teleports and the {@code /bakery farm setup} paste speed. Cooldown in
 * seconds (0 = none); blocks per tick 100..100000.
 */
public record FarmSettings(int teleportCooldownSeconds, int pasteBlocksPerTick) {

    private static final int DEFAULT_COOLDOWN_SECONDS = 5;
    private static final int DEFAULT_BLOCKS_PER_TICK = 5000;
    private static final int MIN_BLOCKS_PER_TICK = 100;
    private static final int MAX_BLOCKS_PER_TICK = 100_000;

    public static FarmSettings from(ConfigurationSection c, Consumer<String> problems) {
        int cooldown = c.getInt("farm.teleport-cooldown-seconds", DEFAULT_COOLDOWN_SECONDS);
        if (cooldown < 0) {
            problems.accept("config.yml: farm.teleport-cooldown-seconds must be >= 0; using "
                    + DEFAULT_COOLDOWN_SECONDS);
            cooldown = DEFAULT_COOLDOWN_SECONDS;
        }
        int perTick = c.getInt("farm.paste.blocks-per-tick", DEFAULT_BLOCKS_PER_TICK);
        if (perTick < MIN_BLOCKS_PER_TICK || perTick > MAX_BLOCKS_PER_TICK) {
            problems.accept("config.yml: farm.paste.blocks-per-tick must be " + MIN_BLOCKS_PER_TICK + ".."
                    + MAX_BLOCKS_PER_TICK + "; using " + DEFAULT_BLOCKS_PER_TICK);
            perTick = DEFAULT_BLOCKS_PER_TICK;
        }
        return new FarmSettings(cooldown, perTick);
    }
}
