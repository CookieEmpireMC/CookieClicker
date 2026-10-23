package dev.casperrs.cookieClicker.vfx.config;

import dev.casperrs.cookieClicker.vfx.shape.DisplayTrack;
import dev.casperrs.cookieClicker.vfx.shape.DropTrack;
import dev.casperrs.cookieClicker.vfx.shape.PopSequence;
import dev.casperrs.cookieClicker.vfx.shape.ShapeParams;
import dev.casperrs.cookieClicker.vfx.shape.ShapePlan;
import java.util.List;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.entity.Display;
import org.jspecify.annotations.Nullable;

/**
 * One parsed, validated layer of an effect preset. Everything that can be precomputed (shape points, display
 * keyframes) is computed here at load; the runtime only turns the layer into packets/entities.
 * The format is documented at the top of {@code effects.yml} and in {@code design/effects.md}.
 */
public sealed interface LayerSpec {

    /** For logs and previews: {@code "<index>:<type>"}, e.g. {@code 0:particle}. */
    String label();

    /** Ticks after {@code play} before this layer starts. */
    int delayTicks();

    /** Where the layer starts, relative to the play location, in blocks. */
    double ox();

    double oy();

    double oz();

    /** Particles spawned per play (0 for entity layers). */
    default int particleCost() {
        return 0;
    }

    /** Display entities spawned per play (0 for particle layers). */
    default int entityCost() {
        return 0;
    }

    /** {@code type: particle}. */
    record Particles(String label, int delayTicks, double ox, double oy, double oz,
                     Particle particle, ParticleData data, ShapeParams shape, ShapePlan plan,
                     int count, double offset, double speed, boolean force) implements LayerSpec {

        /** Without {@code force} (false: vanilla's normal 32-block particle range). */
        public Particles(String label, int delayTicks, double ox, double oy, double oz,
                         Particle particle, ParticleData data, ShapeParams shape, ShapePlan plan,
                         int count, double offset, double speed) {
            this(label, delayTicks, ox, oy, oz, particle, data, shape, plan, count, offset, speed, false);
        }

        /** True when particles are launched along a direction (vanilla count = 0) instead of placed. */
        public boolean directional() {
            return shape.direction() != ShapeParams.Direction.NONE;
        }

        @Override
        public int particleCost() {
            return plan.totalPoints() * (directional() ? 1 : count);
        }
    }

    /**
     * Raw particle data from config; turned into the Bukkit data object at runtime (ItemStack and BlockData need
     * a running server). Only the fields the particle's data type uses are set.
     */
    record ParticleData(Class<?> type, @Nullable Color color, @Nullable Color toColor, float size, float power,
                        @Nullable String item, @Nullable String itemModel, @Nullable String block, float value) {
    }

    /** {@code type: floating-text}: one TextDisplay with MiniMessage text that pops in, rises and fades. */
    record FloatingText(String label, int delayTicks, double ox, double oy, double oz,
                        String text, DisplayTrack track, double spread, boolean shadow, boolean seeThrough,
                        Color background, Display.Billboard billboard, float viewRange) implements LayerSpec {
        @Override
        public int entityCost() {
            return 1;
        }
    }

    /** {@code type: item-crumbs}: small ItemDisplays that fly out, tumble and shrink away. */
    record ItemCrumbs(String label, int delayTicks, double ox, double oy, double oz,
                      String item, @Nullable String itemModel, List<DisplayTrack> tracks,
                      float viewRange) implements LayerSpec {
        @Override
        public int entityCost() {
            return tracks.size();
        }
    }

    /** {@code type: scale-pop}: squash/stretch of the display passed to {@code Effects.pop}. Ignored by play. */
    record ScalePop(String label, PopSequence sequence) implements LayerSpec {
        @Override
        public int delayTicks() {
            return 0;
        }

        @Override
        public double ox() {
            return 0;
        }

        @Override
        public double oy() {
            return 0;
        }

        @Override
        public double oz() {
            return 0;
        }
    }

    /**
     * {@code type: block-drop}: how {@code Effects.dropBlock} animates a falling block. Ignored by play.
     * {@code impactEffect} is an effect ID played for the viewer on impact (or null), {@code impactParticles} counts
     * {@code block} particles on impact (0 = none), the lights are 0-15 or -1 for {@code brightness: auto}.
     */
    record BlockDrop(String label, DropTrack track, @Nullable String impactEffect, int impactParticles,
                     int blockLight, int skyLight, float viewRange) implements LayerSpec {

        /** True when the light is sampled above the target column at drop time. */
        public boolean autoBrightness() {
            return blockLight < 0 || skyLight < 0;
        }

        @Override
        public int delayTicks() {
            return 0;
        }

        @Override
        public double ox() {
            return 0;
        }

        @Override
        public double oy() {
            return 0;
        }

        @Override
        public double oz() {
            return 0;
        }
    }
}
