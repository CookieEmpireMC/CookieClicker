package dev.casperrs.cookieClicker.config;

import org.bukkit.configuration.ConfigurationSection;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.function.Consumer;

/**
 * The bought buildings on the factory plot. The stage thresholds (owned counts) pick the model look; they are not
 * economy values. Footprint sides in blocks; past maxEffectsPerPurchase a purchase plays no more effects (sound stays).
 */
public record BuildingVisualSettings(boolean enabled, int stage2, int stage3, float viewRange, int effectStaggerTicks,
                                     float yawOffset, float fallbackScale, Map<String, String> fallbackBlocks,
                                     String fallbackBlock, int maxEffectsPerPurchase, Map<String, Double> footprints) {

    public BuildingVisualSettings {
        fallbackBlocks = Map.copyOf(fallbackBlocks);
        footprints = Map.copyOf(footprints);
    }

    /** Model stage 1/2/3 for {@code owned} buildings of one type. */
    public int stage(int owned) {
        return owned >= stage3 ? 3 : owned >= stage2 ? 2 : 1;
    }

    public String fallbackFor(String buildingId) {
        return fallbackBlocks.getOrDefault(buildingId, fallbackBlock);
    }

    public static BuildingVisualSettings defaults() {
        return withEnabled(true);
    }

    /** A null {@code plot.buildings} section gives all defaults. */
    public static BuildingVisualSettings from(@Nullable ConfigurationSection c, Consumer<String> problems) {
        return withEnabled(c == null || c.getBoolean("enabled", true));
    }

    private static BuildingVisualSettings withEnabled(boolean enabled) {
        return new BuildingVisualSettings(enabled, 25, 100, 1f, 2, 0f, 0.8f, VisualFallbacks.BUILDING_BLOCKS,
                VisualFallbacks.DEFAULT_BLOCK, 12, VisualFallbacks.FOOTPRINTS);
    }
}
