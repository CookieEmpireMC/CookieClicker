package dev.casperrs.cookieClicker.vfx.config;

import dev.casperrs.cookieClicker.vfx.config.EffectsConfig.Limits;
import dev.casperrs.cookieClicker.vfx.shape.ShapeKind;
import dev.casperrs.cookieClicker.vfx.shape.ShapeParams;
import dev.casperrs.cookieClicker.vfx.shape.ShapeParams.Direction;
import dev.casperrs.cookieClicker.vfx.shape.ShapeParams.Plane;
import dev.casperrs.cookieClicker.vfx.shape.Shapes;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/** Parses {@code type: particle} layers, and applies the per-effect particle cap to a preset's layers. */
final class ParticleLayerParser {

    private ParticleLayerParser() {
    }

    static @Nullable LayerSpec particle(String label, Fields f) {
        String name = f.requiredString("particle");
        Particle particle = name.isEmpty() ? null : particleByKey(name);
        if (particle == null) {
            if (!name.isEmpty()) {
                f.fail("unknown particle '" + name + "' (use a 26.3 particle key like dust, item, end_rod)");
            }
            return null;
        }
        LayerSpec.ParticleData data = particleData(particle, f);
        if (data == null) {
            return null;
        }
        String shapeName = f.string("shape");
        ShapeKind kind = shapeName == null ? ShapeKind.POINT : ShapeKind.fromConfig(shapeName);
        if (kind == null) {
            f.fail("unknown shape '" + shapeName + "' (point, burst, sphere, ring, arc, line, helix, fountain, sparkles)");
            return null;
        }
        ShapeParams d = ShapeParams.defaults(kind);
        double radius = f.number("radius", d.radius(), 0, 8);
        double[] to = f.vec3("to", new double[] {d.toX(), d.toY(), d.toZ()});
        Plane plane = EffectsConfigParser.enumValue(f, "plane", Plane.class, d.plane());
        Direction direction = EffectsConfigParser.enumValue(f, "direction", Direction.class, d.direction());
        ShapeParams shape = new ShapeParams(kind,
                f.integer("points", d.points(), 1, 500),
                radius,
                f.number("radius-start", radius, 0, 8),
                f.number("height", d.height(), -8, 8),
                f.number("turns", d.turns(), 0, 16),
                f.number("angle", d.angleDeg(), 0, 360),
                f.number("start-angle", d.startAngleDeg(), -360, 360),
                f.number("spread", d.spreadDeg(), 0, 180),
                f.number("up-bias", d.upBias(), 0, 1),
                to[0], to[1], to[2], plane,
                f.integer("duration-ticks", d.durationTicks(), 1, 200),
                f.integer("stagger-ticks", d.staggerTicks(), 0, 100),
                f.integer("emit-every", d.emitEvery(), 1, 20),
                direction,
                f.integer("strands", d.strands(), 1, 8));
        double[] o = f.vec3("origin", EffectsConfigParser.ZERO);
        return new LayerSpec.Particles(label,
                f.integer("delay-ticks", 0, 0, 200), o[0], o[1], o[2],
                particle, data, shape, Shapes.build(shape),
                f.integer("count", 1, 1, 100),
                f.number("offset", 0, 0, 4),
                f.number("speed", 0, 0, 4),
                f.bool("force", false));
    }

    /** Resolves {@code dust} or {@code minecraft:dust} against the 26.3 {@link Particle} enum. */
    public static @Nullable Particle particleByKey(String name) {
        String key = Fields.lower(name);
        if (key.startsWith("minecraft:")) {
            key = key.substring("minecraft:".length());
        }
        for (Particle p : Particle.values()) {
            if (p.getKey().getKey().equals(key)) {
                return p;
            }
        }
        return null;
    }

    private static LayerSpec.@Nullable ParticleData particleData(Particle particle, Fields f) {
        Class<?> type = particle.getDataType();
        Color white = Color.WHITE;
        if (type == Void.class) {
            return new LayerSpec.ParticleData(type, null, null, 0, 0, null, null, null, 0);
        }
        if (type == Particle.DustOptions.class) {
            return new LayerSpec.ParticleData(type, f.color("color", white), null,
                    (float) f.number("size", 1, 0.01, 4), 0, null, null, null, 0);
        }
        if (type == Particle.DustTransition.class) {
            Color from = f.color("color", white);
            return new LayerSpec.ParticleData(type, from, f.color("to-color", from),
                    (float) f.number("size", 1, 0.01, 4), 0, null, null, null, 0);
        }
        if (type == Color.class) {
            return new LayerSpec.ParticleData(type, f.color("color", white), null, 0, 0, null, null, null, 0);
        }
        if (type == Particle.Spell.class) {
            return new LayerSpec.ParticleData(type, f.color("color", white), null, 0,
                    (float) f.number("power", 1, 0, 10), null, null, null, 0);
        }
        if (type == ItemStack.class) {
            String item = material(f, "item", "cookie");
            String model = itemModel(f);
            return item == null ? null : new LayerSpec.ParticleData(type, null, null, 0, 0, item, model, null, 0);
        }
        if (type == BlockData.class) {
            String block = material(f, "block", null);
            return block == null ? null : new LayerSpec.ParticleData(type, null, null, 0, 0, null, null, block, 0);
        }
        if (type == Float.class || type == Integer.class) {
            return new LayerSpec.ParticleData(type, null, null, 0, 0, null, null, null,
                    (float) f.number("value", type == Float.class ? 1 : 0, 0, 1000));
        }
        f.fail("particle '" + particle.getKey().getKey() + "' needs " + type.getSimpleName()
                + " data, which effects.yml doesn't support");
        return null;
    }

    /** A material name (e.g. {@code cookie}); {@code def == null} makes it required. */
    static @Nullable String material(Fields f, String key, @Nullable String def) {
        String name = f.string(key);
        if (name == null) {
            if (def == null) {
                f.fail("missing '" + key + "'");
            }
            return def;
        }
        if (Material.matchMaterial(name) == null) {
            f.fail("unknown " + key + " '" + name + "'");
            return null;
        }
        return name;
    }

    /** Optional {@code item-model: namespace:path} (syntax only; the pack decides if it exists). */
    static @Nullable String itemModel(Fields f) {
        String model = f.string("item-model");
        if (model != null && NamespacedKey.fromString(Fields.lower(model)) == null) {
            f.problem("'item-model' '" + model + "' is not a valid key like cookieclicker:cookie; ignored");
            return null;
        }
        return model == null ? null : Fields.lower(model);
    }

    /**
     * Keeps the preset under {@code max-particles-per-effect}: thins every particle layer by the same factor
     * (fewer points), then drops trailing particle layers if that isn't enough. Done once at load.
     */
    static List<LayerSpec> enforceCap(String id, List<LayerSpec> layers, Limits limits,
                                                     List<String> problems) {
        int cap = limits.maxParticlesPerEffect();
        int total = layers.stream().mapToInt(LayerSpec::particleCost).sum();
        if (total <= cap) {
            return layers;
        }
        problems.add(id + ": " + total + " particles per play is over max-particles-per-effect (" + cap
                + "); thinning the particle layers");
        List<LayerSpec> out = new ArrayList<>(layers);
        for (int round = 0; round < 8 && total > cap; round++) {
            double factor = (double) cap / total;
            for (int i = 0; i < out.size(); i++) {
                if (out.get(i) instanceof LayerSpec.Particles p) {
                    out.set(i, thin(p, factor));
                }
            }
            total = out.stream().mapToInt(LayerSpec::particleCost).sum();
        }
        for (int i = out.size() - 1; i >= 0 && total > cap; i--) {
            if (out.get(i) instanceof LayerSpec.Particles p) {
                problems.add(id + ": dropped " + p.label() + " to stay under the particle cap");
                out.remove(i);
                total -= p.particleCost();
            }
        }
        return out;
    }

    private static LayerSpec.Particles thin(LayerSpec.Particles p, double factor) {
        if (p.shape().kind() == ShapeKind.POINT || p.count() > 1) {
            int count = Math.max(1, (int) Math.floor(p.count() * factor));
            if (count < p.count()) {
                return new LayerSpec.Particles(p.label(), p.delayTicks(), p.ox(), p.oy(), p.oz(), p.particle(),
                        p.data(), p.shape(), p.plan(), count, p.offset(), p.speed(), p.force());
            }
        }
        ShapeParams s = p.shape().withPoints(Math.max(1, (int) Math.floor(p.shape().points() * factor)));
        return new LayerSpec.Particles(p.label(), p.delayTicks(), p.ox(), p.oy(), p.oz(), p.particle(), p.data(),
                s, Shapes.build(s), p.count(), p.offset(), p.speed(), p.force());
    }
}
