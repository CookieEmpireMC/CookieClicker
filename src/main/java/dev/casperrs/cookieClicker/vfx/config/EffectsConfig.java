package dev.casperrs.cookieClicker.vfx.config;

import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * The parsed {@code effects.yml}: limits, presets by ID, and every problem found while loading (already turned
 * into a skipped layer or preset, never an exception).
 */
public record EffectsConfig(Limits limits, Map<String, Preset> presets, List<String> problems) {

    /** Caps from the {@code limits} section. Display caps count display entities, not effects. */
    public record Limits(int maxParticlesPerEffect, int maxDisplayEntitiesPerPlayer, int maxDisplayEntitiesGlobal) {
        public static final Limits DEFAULTS = new Limits(200, 8, 150);
    }

    /** One effect ID and its layers, in config order. */
    public record Preset(String id, List<LayerSpec> layers) {
        public int particleCost() {
            return layers.stream().mapToInt(LayerSpec::particleCost).sum();
        }

        public int entityCost() {
            return layers.stream().mapToInt(LayerSpec::entityCost).sum();
        }
    }

    public @Nullable Preset preset(String id) {
        return presets.get(id);
    }
}
