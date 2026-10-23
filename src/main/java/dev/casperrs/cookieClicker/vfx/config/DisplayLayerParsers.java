package dev.casperrs.cookieClicker.vfx.config;

import dev.casperrs.cookieClicker.vfx.shape.DisplayTrack;
import dev.casperrs.cookieClicker.vfx.shape.DropTrack;
import dev.casperrs.cookieClicker.vfx.shape.Motions;
import dev.casperrs.cookieClicker.vfx.shape.PopSequence;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.Color;
import org.bukkit.entity.Display;
import org.jspecify.annotations.Nullable;

/** Parses the display layers: {@code floating-text}, {@code item-crumbs}, {@code scale-pop} and {@code block-drop}. */
final class DisplayLayerParsers {

    private DisplayLayerParsers() {
    }

    static @Nullable LayerSpec floatingText(String label, Fields f) {
        String text = f.requiredString("text");
        int startTick = f.integer("start-tick", 1, 1, 5);
        int popIn = f.integer("pop-in-ticks", 3, 0, 10);
        int fade = f.integer("fade-ticks", 6, 0, 40);
        int min = Motions.minTextDuration(popIn, fade, startTick);
        int duration = f.integer("duration-ticks", 20, 1, 200);
        if (duration < min) {
            f.problem("'duration-ticks' " + duration + " is too short for pop-in + fade; using " + min);
            duration = min;
        }
        double scale = f.number("scale", 1, 0.05, 10);
        DisplayTrack track = Motions.floatingText(
                f.number("rise", 0.8, -4, 4), duration, scale,
                f.number("pop-in-scale", 0.5, 0.05, 2),
                f.number("overshoot", 1.15, 0.5, 2),
                popIn, fade, startTick);
        double[] o = f.vec3("origin", EffectsConfigParser.ZERO);
        Display.Billboard billboard = EffectsConfigParser.enumValue(f, "billboard", Display.Billboard.class, Display.Billboard.CENTER);
        Color background = f.color("background", Color.fromARGB(0, 0, 0, 0));
        LayerSpec spec = new LayerSpec.FloatingText(label, f.integer("delay-ticks", 0, 0, 200), o[0], o[1], o[2],
                text, track, f.number("spread", 0.2, 0, 2), f.bool("shadow", true), f.bool("see-through", false),
                background == null ? Color.fromARGB(0, 0, 0, 0) : background, billboard,
                (float) f.number("view-range", 0.5, 0.05, 4));
        return f.fatal() ? null : spec;
    }


    static @Nullable LayerSpec itemCrumbs(String label, Fields f) {
        String item = ParticleLayerParser.material(f, "item", "cookie");
        String model = ParticleLayerParser.itemModel(f);
        int count = f.integer("count", 4, 1, 16);
        double distance = f.number("distance", 0.7, 0, 4);
        double spread = f.number("spread", 60, 0, 180);
        double lift = f.number("lift", 0.2, 0, 2);
        double drop = f.number("drop", 0.5, 0, 4);
        int start = f.integer("start-tick", 1, 1, 5);
        int duration = f.integer("duration-ticks", 14, start + 3, 200);
        double scale = f.number("scale", 0.2, 0.02, 2);
        double spin = f.number("spin", 150, 0, 200);
        List<DisplayTrack> tracks = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            tracks.add(Motions.crumb(i, count, distance, spread, lift, drop, duration, scale, spin, start));
        }
        double[] o = f.vec3("origin", EffectsConfigParser.ZERO);
        int delay = f.integer("delay-ticks", 0, 0, 200);
        float range = (float) f.number("view-range", 0.5, 0.05, 4);
        return item == null ? null
                : new LayerSpec.ItemCrumbs(label, delay, o[0], o[1], o[2], item, model, List.copyOf(tracks), range);
    }


    static @Nullable LayerSpec scalePop(String label, Fields f) {
        Object raw = f.raw("steps");
        if (raw == null) {
            return new LayerSpec.ScalePop(label, PopSequence.standard());
        }
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            f.fail("'steps' must be a list like [{ scale: [1.25, 0.75, 1.25], ticks: 2 }, ...]");
            return null;
        }
        List<PopSequence.Step> steps = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            if (!(list.get(i) instanceof Map<?, ?> m)) {
                f.fail("step " + i + " must be a map like { scale: [1.25, 0.75, 1.25], ticks: 2 }");
                return null;
            }
            Fields s = f.child(m, "step " + i);
            double[] scale = s.raw("scale") instanceof Number n
                    ? new double[] {n.doubleValue(), n.doubleValue(), n.doubleValue()}
                    : s.vec3("scale", new double[] {1, 1, 1});
            int ticks = s.integer("ticks", 2, 1, 20);
            boolean ok = true;
            for (double v : scale) {
                ok &= v > 0 && v <= 4;
            }
            if (!ok) {
                f.fail("step " + i + " scale must be between 0 and 4");
                return null;
            }
            s.reportUnknown();
            steps.add(new PopSequence.Step(scale[0], scale[1], scale[2], ticks));
        }
        return new LayerSpec.ScalePop(label, new PopSequence(steps));
    }


    static @Nullable LayerSpec blockDrop(String label, Fields f) {
        DropTrack.Params d = DropTrack.Params.DEFAULTS;
        double[] squash = f.raw("squash") instanceof Number n
                ? new double[] {n.doubleValue(), n.doubleValue(), n.doubleValue()}
                : f.vec3("squash", new double[] {d.squashX(), d.squashY(), d.squashZ()});
        for (int i = 0; i < 3; i++) {
            if (!(squash[i] >= 0.25 && squash[i] <= 2)) {
                f.problem("'squash' values must be between 0.25 and 2; using the default");
                squash = new double[] {d.squashX(), d.squashY(), d.squashZ()};
                break;
            }
        }
        DropTrack.Params p = new DropTrack.Params(
                f.integer("start-tick", d.startTick(), 1, 10),
                f.number("drop-height", d.dropHeight(), 0, 16),
                f.integer("fall-ticks", d.fallTicks(), 1, 40),
                f.number("bounce-height", d.bounceHeight(), 0, 2),
                f.integer("bounce-ticks", d.bounceTicks(), 1, 20),
                squash[0], squash[1], squash[2],
                f.integer("settle-ticks", d.settleTicks(), 1, 20));
        String impact = f.string("impact-effect");
        if (impact != null) {
            impact = Fields.lower(impact);
            if (impact.isEmpty() || impact.equals("none")) {
                impact = null;
            } else if (!EffectsConfigParser.ID.matcher(impact).matches()) {
                f.problem("'impact-effect' '" + impact + "' is not an effect ID; ignored");
                impact = null;
            }
        }
        int[] light = brightness(f);
        return new LayerSpec.BlockDrop(label, DropTrack.of(p), impact,
                f.integer("impact-particles", 4, 0, 32), light[0], light[1],
                (float) f.number("view-range", 1.0, 0.05, 4));
    }

    /** {@code brightness: auto} (default) or {@code [block, sky]} with 0..15 each. */
    private static int[] brightness(Fields f) {
        Object raw = f.raw("brightness");
        int[] auto = {-1, -1};
        if (raw == null || (raw instanceof String s && Fields.lower(s).equals("auto"))) {
            return auto;
        }
        if (raw instanceof List<?> list && list.size() == 2 && list.stream().allMatch(e -> e instanceof Integer)) {
            int block = (Integer) list.get(0);
            int sky = (Integer) list.get(1);
            if (block >= 0 && block <= 15 && sky >= 0 && sky <= 15) {
                return new int[] {block, sky};
            }
        }
        f.problem("'brightness' must be auto or [block, sky] with whole numbers 0..15, got '" + raw
                + "'; using auto");
        return auto;
    }
}
