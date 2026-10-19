package dev.casperrs.cookieClicker.vfx;

import static org.junit.jupiter.api.Assertions.assertFalse;

import dev.casperrs.cookieClicker.vfx.config.EffectsConfig;
import dev.casperrs.cookieClicker.vfx.config.EffectsConfigParser;
import dev.casperrs.cookieClicker.vfx.config.LayerSpec;
import dev.casperrs.cookieClicker.vfx.shape.DisplayTrack;
import dev.casperrs.cookieClicker.vfx.shape.DropTrack;
import dev.casperrs.cookieClicker.vfx.shape.PopSequence;
import dev.casperrs.cookieClicker.vfx.shape.ShapePlan;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.bukkit.Particle;
import org.junit.jupiter.api.Test;

/**
 * Not a unit test: the name doesn't match Surefire's patterns, so only {@code mise run vfx-previews} runs it.
 * Parses the shipped {@code effects.yml} with the real loader and writes what every preset does over time to
 * {@code target/vfx-points/<effect>.csv} ({@code tick,x,y,z,layer}); {@code design/vfx-scripts/plot_effects.py}
 * plots them.
 *
 * <ul>
 *   <li>particles: the exact spawn points per tick (facing shapes as seen by a viewer on +z); launched particles
 *       also get an approximate flight path ({@code ~flight}, vanilla drag/gravity guessed, not exact)</li>
 *   <li>floating text / crumbs: the client-side interpolated position per tick (emulated)</li>
 *   <li>pop: the outline of a 1-block disc at the interpolated pop scale, per tick</li>
 *   <li>block-drop: the edges of the falling block at the interpolated transformation, per tick</li>
 *   <li>looping presets ({@code loop-every}): three plays, each shifted by the interval</li>
 * </ul>
 */
class EffectsPreviewExport {

    private static final Path OUT = Path.of("target", "vfx-points");
    private static final int FLIGHT_TICKS = 6;
    private static final int LOOP_CYCLES = 3;

    @Test
    void exportEveryPreset() throws Exception {
        EffectsConfig config = EffectsConfigParser.parse(EffectsConfigParserTest.shipped(), null);
        assertFalse(config.presets().isEmpty());
        Files.createDirectories(OUT);
        for (EffectsConfig.Preset preset : config.presets().values()) {
            try (Writer w = Files.newBufferedWriter(OUT.resolve(preset.id() + ".csv"), StandardCharsets.UTF_8)) {
                w.write("tick,x,y,z,layer\n");
                // looping presets: show LOOP_CYCLES plays, each shifted by loop-every
                int cycles = preset.loopEvery() > 0 ? LOOP_CYCLES : 1;
                for (int cycle = 0; cycle < cycles; cycle++) {
                    int shift = cycle * preset.loopEvery();
                    for (LayerSpec layer : preset.layers()) {
                        switch (layer) {
                            case LayerSpec.Particles p -> particles(w, p, shift);
                            case LayerSpec.FloatingText t -> track(w, t.track(), t, t.label() + " text");
                            case LayerSpec.ItemCrumbs c -> {
                                for (DisplayTrack track : c.tracks()) {
                                    track(w, track, c, c.label() + " crumbs");
                                }
                            }
                            case LayerSpec.ScalePop pop -> pop(w, pop);
                            case LayerSpec.BlockDrop drop -> drop(w, drop);
                        }
                    }
                }
            }
        }
    }

    private static void particles(Writer w, LayerSpec.Particles p, int shift) throws IOException {
        ShapePlan plan = p.plan();
        String name = p.label() + " " + p.particle().getKey().getKey();
        boolean gravity = p.particle() == Particle.ITEM;
        for (int t = 0; t < plan.ticks(); t++) {
            double[] pts = plan.at(t);
            int tick = shift + p.delayTicks() + t;
            for (int i = 0; i < pts.length; i += ShapePlan.STRIDE) {
                double x = p.ox() + pts[i];
                double y = p.oy() + pts[i + 1];
                double z = p.oz() + pts[i + 2];
                row(w, tick, x, y, z, name);
                if (p.directional()) {
                    double vx = pts[i + 3] * p.speed();
                    double vy = pts[i + 4] * p.speed();
                    double vz = pts[i + 5] * p.speed();
                    for (int f = 1; f <= FLIGHT_TICKS; f++) {
                        x += vx;
                        y += vy;
                        z += vz;
                        vx *= 0.91;
                        vy = vy * 0.91 - (gravity ? 0.04 : 0);
                        vz *= 0.91;
                        row(w, tick + f, x, y, z, name + " ~flight");
                    }
                }
            }
        }
    }

    private static void track(Writer w, DisplayTrack track, LayerSpec layer, String name) throws IOException {
        for (int t = 0; t <= track.removeTick(); t++) {
            double[] r = track.renderedAt(t);
            row(w, layer.delayTicks() + t, layer.ox() + r[0], layer.oy() + r[1], layer.oz() + r[2], name);
        }
    }

    private static void pop(Writer w, LayerSpec.ScalePop pop) throws IOException {
        PopSequence seq = pop.sequence();
        double[] from = {1, 1, 1};
        double[] cur = {1, 1, 1};
        for (int t = 0; t <= seq.totalTicks(); t++) {
            for (int s = 0; s < seq.steps().size(); s++) {
                PopSequence.Step step = seq.steps().get(s);
                int start = seq.startTick(s);
                if (t >= start && t <= start + step.ticks()) {
                    if (t == start) {
                        from = cur.clone();
                    }
                    double f = (double) (t - start) / step.ticks();
                    cur = new double[] {from[0] + (step.sx() - from[0]) * f, from[1] + (step.sy() - from[1]) * f,
                            from[2] + (step.sz() - from[2]) * f};
                    break;
                }
            }
            // outline of a 1-block-wide cookie standing up (x/y), centred 1 block up
            for (int i = 0; i < 24; i++) {
                double a = 2 * Math.PI * i / 24;
                row(w, t, 0.5 * Math.cos(a) * cur[0], 1 + 0.5 * Math.sin(a) * cur[1], 0, pop.label() + " scale");
            }
        }
    }

    /** The 12 edges of the falling block (unit cube from its corner), 3 samples each, per tick until it lands. */
    private static void drop(Writer w, LayerSpec.BlockDrop drop) throws IOException {
        DropTrack track = drop.track();
        int[][] corners = {{0, 0, 0}, {1, 0, 0}, {1, 0, 1}, {0, 0, 1}, {0, 1, 0}, {1, 1, 0}, {1, 1, 1}, {0, 1, 1}};
        int[][] edges = {{0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6}, {6, 7}, {7, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
        for (int t = 0; t <= track.landTick(); t++) {
            double[] r = track.renderedAt(t);
            for (int[] e : edges) {
                for (int k = 0; k < 3; k++) {
                    double f = k / 2.0;
                    double cx = corners[e[0]][0] + (corners[e[1]][0] - corners[e[0]][0]) * f;
                    double cy = corners[e[0]][1] + (corners[e[1]][1] - corners[e[0]][1]) * f;
                    double cz = corners[e[0]][2] + (corners[e[1]][2] - corners[e[0]][2]) * f;
                    row(w, t, r[0] + cx * r[3], r[1] + cy * r[4], r[2] + cz * r[5], drop.label() + " block");
                }
            }
        }
    }

    private static void row(Writer w, int tick, double x, double y, double z, String layer) throws IOException {
        w.write(String.format(Locale.ROOT, "%d,%.4f,%.4f,%.4f,%s%n", tick, x, y, z, layer));
    }
}
