package dev.casperrs.cookieClicker.config;

import org.bukkit.configuration.ConfigurationSection;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.function.Consumer;

/**
 * The bought buildings on the factory plot. The stage thresholds pick the model look; they are not economy values.
 *
 * @param stage2                owned count from which the stage-2 look shows ({@code custom_model_data} "2")
 * @param stage3                owned count from which the stage-3 look shows ("3")
 * @param effectStaggerTicks    ticks between the effects of several instances that change at once
 * @param fallbackBlocks        building id -&gt; vanilla block shown without our pack
 * @param maxEffectsPerPurchase most effects one purchase plays (a ×Max buy crossing a stage for many types); the
 *                              rest plays none (the one sound stays)
 * @param footprints            building id -&gt; side of its centred footprint square (blocks), for the decor keep-out
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

    /** @param c the {@code plot.buildings} section, or null (all defaults) */
    public static BuildingVisualSettings from(@Nullable ConfigurationSection c, Consumer<String> problems) {
        return withEnabled(c == null || c.getBoolean("enabled", true));
    }

    private static BuildingVisualSettings withEnabled(boolean enabled) {
        return new BuildingVisualSettings(enabled, 25, 100, 1f, 2, 0f, 0.8f, VisualFallbacks.BUILDING_BLOCKS,
                VisualFallbacks.DEFAULT_BLOCK, 12, VisualFallbacks.FOOTPRINTS);
    }
}
