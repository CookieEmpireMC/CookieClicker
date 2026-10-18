package dev.casperrs.cookieClicker.vfx.shape;

import java.util.Locale;
import org.jspecify.annotations.Nullable;

/** Every particle shape the engine can draw. The config name is the lower-case constant name. */
public enum ShapeKind {
    /** One point at the origin, {@code count} particles per tick with the vanilla random {@code offset}. */
    POINT,
    /** Points on a sphere (golden spiral), launched outward. Spread over {@code duration-ticks}. */
    BURST,
    /** A sphere shell, redrawn every tick while the radius grows from {@code radius-start} to {@code radius}. */
    SPHERE,
    /** A circle, redrawn every tick while the radius grows from {@code radius-start} to {@code radius}. */
    RING,
    /** Part of a circle ({@code angle} degrees from {@code start-angle}), drawn progressively. */
    ARC,
    /** A straight line from the origin to {@code to}, drawn progressively. */
    LINE,
    /** A rising spiral ({@code turns}, {@code height}, {@code strands}), drawn progressively. */
    HELIX,
    /** A cone of directions around straight up ({@code spread} half-angle), launched outward. */
    FOUNTAIN,
    /** Sparks on a disc that rise {@code height} with ease-out over their life, staggered. */
    SPARKLES;

    public String configName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static @Nullable ShapeKind fromConfig(String name) {
        for (ShapeKind kind : values()) {
            if (kind.configName().equals(name.trim().toLowerCase(Locale.ROOT))) {
                return kind;
            }
        }
        return null;
    }
}
