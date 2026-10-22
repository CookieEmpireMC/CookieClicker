package dev.casperrs.cookieClicker.config;

import org.bukkit.configuration.ConfigurationSection;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The bought buildings on the factory plot ({@code config.yml: plot.buildings.*}, brief building-roster). Visual
 * values only: the stage thresholds pick the model look, they are not economy values.
 *
 * @param enabled             show bought buildings at all
 * @param stage2              owned count from which the stage-2 look shows ({@code custom_model_data} "2")
 * @param stage3              owned count from which the stage-3 look shows ("3")
 * @param viewRange           display view range multiplier (1 = 64 blocks)
 * @param effectStaggerTicks  ticks between the effects of several instances that change at once (×10 buy, a stage
 *                            change on every instance)
 * @param yawOffset           added to every slot's yaw (degrees), in case a model faces another way
 * @param fallbackScale       size of the vanilla fallback block (blocks)
 * @param fallbackBlocks      building id → vanilla block shown without our pack
 * @param fallbackBlock       the block for buildings not in {@code fallbackBlocks}
 */
public record BuildingVisualSettings(boolean enabled, int stage2, int stage3, float viewRange, int effectStaggerTicks,
                                     float yawOffset, float fallbackScale, Map<String, String> fallbackBlocks,
                                     String fallbackBlock) {

    public BuildingVisualSettings {
        fallbackBlocks = Map.copyOf(fallbackBlocks);
    }

    /** Model stage 1/2/3 for {@code owned} buildings of one type. */
    public int stage(int owned) {
        return owned >= stage3 ? 3 : owned >= stage2 ? 2 : 1;
    }

    public String fallbackFor(String buildingId) {
        return fallbackBlocks.getOrDefault(buildingId, fallbackBlock);
    }

    public static BuildingVisualSettings defaults() {
        return new BuildingVisualSettings(true, 25, 100, 1f, 2, 0f, 0.8f, Map.of(), "minecraft:barrel");
    }

    /** @param c the {@code plot.buildings} section, or null (all defaults) */
    public static BuildingVisualSettings from(@Nullable ConfigurationSection c, Consumer<String> problems) {
        if (c == null) {
            return defaults();
        }
        String p = "";
        int stage2 = c.getInt(p + "stage-2", 25);
        int stage3 = c.getInt(p + "stage-3", 100);
        if (stage2 < 1 || stage3 <= stage2) {
            problems.accept("config.yml: plot.buildings.stage-2/stage-3 must be 1 <= stage-2 < stage-3; using 25 / 100");
            stage2 = 25;
            stage3 = 100;
        }
        Map<String, String> blocks = new LinkedHashMap<>();
        ConfigurationSection fb = c.getConfigurationSection(p + "fallback-blocks");
        if (fb != null) {
            for (String id : fb.getKeys(false)) {
                String block = fb.getString(id);
                if (block != null && !block.isBlank()) {
                    blocks.put(id, block.trim());
                }
            }
        }
        return new BuildingVisualSettings(
                c.getBoolean(p + "enabled", true),
                stage2,
                stage3,
                (float) Math.clamp(c.getDouble(p + "view-range", 1.0), 0.1, 10.0),
                Math.clamp(c.getInt(p + "effect-stagger-ticks", 2), 0, 20),
                (float) c.getDouble(p + "yaw-offset", 0.0),
                (float) Math.clamp(c.getDouble(p + "fallback-scale", 0.8), 0.1, 3.0),
                blocks,
                c.getString(p + "fallback-block", "minecraft:barrel"));
    }
}
