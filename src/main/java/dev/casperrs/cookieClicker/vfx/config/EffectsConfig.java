package dev.casperrs.cookieClicker.vfx.config;

import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * The parsed {@code effects.yml}: limits, presets by ID, and every problem found while loading (already turned
 * into a skipped layer or preset, never an exception).
 */
public record EffectsConfig(Limits limits, Map<String, Preset> presets, List<String> problems) {

    /**
     * Caps from the {@code limits} section. Display caps count display entities, not effects. Falling blocks
     * ({@code Effects.dropBlock}) and loops ({@code Effects.loop}) have their own caps and don't count toward the
     * display caps, so a house cinematic never evicts click numbers.
     */
    public record Limits(int maxParticlesPerEffect, int maxDisplayEntitiesPerPlayer, int maxDisplayEntitiesGlobal,
                         int maxFallingBlocksPerPlayer, int maxFallingBlocksGlobal, int maxLoopsPerPlayer) {
        public static final Limits DEFAULTS = new Limits(200, 8, 150, 24, 240, 8);

        /** The original three caps; the falling-block and loop caps take their defaults. */
        public Limits(int maxParticlesPerEffect, int maxDisplayEntitiesPerPlayer, int maxDisplayEntitiesGlobal) {
            this(maxParticlesPerEffect, maxDisplayEntitiesPerPlayer, maxDisplayEntitiesGlobal, 24, 240, 8);
        }
    }

    /**
     * One effect ID and its layers, in config order.
     * {@code loopEvery} (ticks, {@code loop-every}) is 0 when unset; {@code minIntervalTicks}
     * ({@code min-interval-ticks}) drops plays closer together for one viewer, 0 = no cap.
     */
    public record Preset(String id, List<LayerSpec> layers, int loopEvery, int minIntervalTicks) {

        public Preset(String id, List<LayerSpec> layers) {
            this(id, layers, 0, 0);
        }

        public Preset(String id, List<LayerSpec> layers, int loopEvery) {
            this(id, layers, loopEvery, 0);
        }

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
