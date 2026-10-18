package dev.casperrs.cookieClicker.vfx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.casperrs.cookieClicker.vfx.config.EffectsConfig;
import dev.casperrs.cookieClicker.vfx.config.EffectsConfigParser;
import dev.casperrs.cookieClicker.vfx.config.LayerSpec;
import dev.casperrs.cookieClicker.vfx.shape.ShapeParams;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.bukkit.Particle;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class EffectsConfigParserTest {

    /** The effect IDs in the bakery-core contract, plus the pop preset behind Effects.pop. */
    private static final List<String> CONTRACT = List.of("cookie_click", "cookie_crit", "purchase", "pop");

    static YamlConfiguration shipped() throws Exception {
        try (InputStream in = EffectsConfigParserTest.class.getResourceAsStream("/effects.yml")) {
            assertNotNull(in, "effects.yml on the classpath");
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    private static YamlConfiguration yaml(String text) throws InvalidConfigurationException {
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString(text);
        return y;
    }

    private static EffectsConfig parse(String text) throws InvalidConfigurationException {
        return EffectsConfigParser.parse(yaml(text), null);
    }

    @Test
    void shippedEffectsLoadWithoutProblems() throws Exception {
        EffectsConfig config = EffectsConfigParser.parse(shipped(), null);
        assertEquals(List.of(), config.problems());
        for (String id : CONTRACT) {
            assertNotNull(config.preset(id), id + " exists");
        }
    }

    @Test
    void shippedEffectsStayInsideTheBudget() throws Exception {
        EffectsConfig config = EffectsConfigParser.parse(shipped(), null);
        for (EffectsConfig.Preset preset : config.presets().values()) {
            assertTrue(preset.particleCost() <= config.limits().maxParticlesPerEffect(), preset.id());
            assertTrue(preset.entityCost() <= config.limits().maxDisplayEntitiesPerPlayer(), preset.id());
        }
        // the click is spammable: style guide says at most 15 particles and one entity
        assertTrue(config.preset("cookie_click").particleCost() <= 15);
        assertEquals(1, config.preset("cookie_click").entityCost());
        int purchase = config.preset("purchase").particleCost();
        assertTrue(purchase >= 40 && purchase <= 120, "purchase in the 40-120 band, got " + purchase);
    }

    @Test
    void clickAndCritShowTheAmountPlaceholder() throws Exception {
        EffectsConfig config = EffectsConfigParser.parse(shipped(), null);
        for (String id : List.of("cookie_click", "cookie_crit")) {
            boolean hasText = config.preset(id).layers().stream()
                    .anyMatch(l -> l instanceof LayerSpec.FloatingText t && t.text().contains("<amount>"));
            assertTrue(hasText, id + " floats <amount>");
        }
    }

    @Test
    void clickCrumbsUseOurItemModel() throws Exception {
        LayerSpec first = EffectsConfigParser.parse(shipped(), null).preset("cookie_click").layers().getFirst();
        LayerSpec.Particles crumbs = assertInstanceOf(LayerSpec.Particles.class, first);
        assertEquals(Particle.ITEM, crumbs.particle());
        assertEquals("cookieclicker:cookie", crumbs.data().itemModel());
        assertEquals("cookie", crumbs.data().item());
    }

    @Test
    void popPresetIsTheStandardScaleOnlySequence() throws Exception {
        LayerSpec layer = EffectsConfigParser.parse(shipped(), null).preset("pop").layers().getFirst();
        LayerSpec.ScalePop pop = assertInstanceOf(LayerSpec.ScalePop.class, layer);
        assertEquals(7, pop.sequence().totalTicks());
        assertEquals(3, pop.sequence().steps().size());
    }

    @Test
    void particleKeysResolveWithOrWithoutNamespace() {
        assertEquals(Particle.DUST, EffectsConfigParser.particleByKey("dust"));
        assertEquals(Particle.END_ROD, EffectsConfigParser.particleByKey("minecraft:end_rod"));
        assertEquals(Particle.ITEM, EffectsConfigParser.particleByKey(" ITEM "));
        assertNull(EffectsConfigParser.particleByKey("redstone"), "1.20 name, renamed to dust");
    }

    @Test
    void unknownParticleSkipsOnlyThatLayer() throws Exception {
        EffectsConfig config = parse("""
                fx:
                  layers:
                    - { type: particle, particle: sparkle_magic }
                    - { type: particle, particle: end_rod }
                """);
        assertEquals(1, config.preset("fx").layers().size());
        assertTrue(config.problems().stream().anyMatch(p -> p.contains("unknown particle 'sparkle_magic'")));
    }

    @Test
    void unknownTypeShapeAndFieldsAreReported() throws Exception {
        EffectsConfig config = parse("""
                fx:
                  layers:
                    - { type: laser, particle: dust }
                    - { type: particle, particle: dust, shape: star }
                    - { type: particle, particle: dust, radious: 2 }
                """);
        assertEquals(1, config.preset("fx").layers().size(), "only the typo layer survives (typo ignored)");
        List<String> problems = config.problems();
        assertTrue(problems.stream().anyMatch(p -> p.contains("unknown type 'laser'")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("unknown shape 'star'")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("unknown field 'radious'")), problems.toString());
    }

    @Test
    void badValuesFallBackAndAreReported() throws Exception {
        EffectsConfig config = parse("""
                fx:
                  layers:
                    - { type: particle, particle: dust, color: "gold", size: 9, points: "lots", shape: ring }
                """);
        LayerSpec.Particles layer = assertInstanceOf(LayerSpec.Particles.class,
                config.preset("fx").layers().getFirst());
        assertEquals(4.0f, layer.data().size(), "clamped to the dust maximum");
        assertEquals(ShapeParams.defaults(layer.shape().kind()).points(), layer.shape().points());
        assertEquals(3, config.problems().size(), config.problems().toString());
    }

    @Test
    void particlesWithUnsupportedDataAreSkipped() throws Exception {
        EffectsConfig config = parse("""
                fx:
                  layers:
                    - { type: particle, particle: vibration }
                    - { type: particle, particle: dust }
                """);
        assertEquals(1, config.preset("fx").layers().size());
        assertTrue(config.problems().stream().anyMatch(p -> p.contains("Vibration")));
    }

    @Test
    void unknownItemSkipsTheLayer() throws Exception {
        EffectsConfig config = parse("""
                fx:
                  layers:
                    - { type: particle, particle: item, item: cookie_dough }
                    - { type: particle, particle: item, item-model: "not a key" }
                """);
        assertEquals(1, config.preset("fx").layers().size());
        LayerSpec.Particles p = assertInstanceOf(LayerSpec.Particles.class, config.preset("fx").layers().getFirst());
        assertNull(p.data().itemModel(), "invalid model key is dropped, plain item stays");
        assertEquals(2, config.problems().size(), config.problems().toString());
    }

    @Test
    void presetsWithoutValidLayersAreSkipped() throws Exception {
        EffectsConfig config = parse("""
                empty:
                  layers: []
                broken:
                  layers:
                    - { type: particle }
                nolayers:
                  colour: red
                Bad-Id:
                  layers:
                    - { type: particle, particle: dust }
                """);
        assertTrue(config.presets().isEmpty());
        assertEquals(5, config.problems().size(), config.problems().toString());
    }

    @Test
    void overBudgetPresetsAreThinnedAtLoad() throws Exception {
        EffectsConfig config = parse("""
                limits:
                  max-particles-per-effect: 50
                fx:
                  layers:
                    - { type: particle, particle: dust, shape: ring, points: 40, duration-ticks: 2 }
                    - { type: particle, particle: dust, shape: point, count: 40 }
                """);
        EffectsConfig.Preset fx = config.preset("fx");
        assertEquals(2, fx.layers().size());
        assertTrue(fx.particleCost() <= 50, "got " + fx.particleCost());
        assertTrue(fx.particleCost() >= 30, "thinned, not gutted: " + fx.particleCost());
        assertTrue(config.problems().stream().anyMatch(p -> p.contains("thinning")));
    }

    @Test
    void missingPresetsAndLimitsComeFromTheDefaults() throws Exception {
        YamlConfiguration user = yaml("""
                limits:
                  max-display-entities-global: 20
                cookie_click:
                  layers:
                    - { type: particle, particle: end_rod }
                """);
        EffectsConfig config = EffectsConfigParser.parse(user, shipped());
        assertEquals(List.of(), config.problems());
        assertEquals(20, config.limits().maxDisplayEntitiesGlobal());
        assertEquals(200, config.limits().maxParticlesPerEffect(), "from the shipped file");
        assertEquals(1, config.preset("cookie_click").layers().size(), "server file wins");
        assertNotNull(config.preset("purchase"), "missing preset from the shipped file");
    }

    @Test
    void floatingTextTooShortIsLengthenedAndTextIsRequired() throws Exception {
        EffectsConfig config = parse("""
                fx:
                  layers:
                    - { type: floating-text, text: "<gold>+<amount>", duration-ticks: 3 }
                    - { type: floating-text }
                """);
        LayerSpec.FloatingText text = assertInstanceOf(LayerSpec.FloatingText.class,
                config.preset("fx").layers().getFirst());
        assertEquals(11, text.track().removeTick());
        assertEquals(1, config.preset("fx").layers().size());
        assertFalse(config.problems().isEmpty());
    }

    @Test
    void scalePopStepsAreValidated() throws Exception {
        EffectsConfig config = parse("""
                a:
                  layers:
                    - { type: scale-pop, steps: [ { scale: 1.3, ticks: 2 } ] }
                b:
                  layers:
                    - { type: scale-pop, steps: [ { scale: [0, 1, 1], ticks: 2 } ] }
                """);
        LayerSpec.ScalePop a = assertInstanceOf(LayerSpec.ScalePop.class, config.preset("a").layers().getFirst());
        assertEquals(2, a.sequence().steps().size(), "rest step appended");
        assertNull(config.preset("b"));
    }
}
