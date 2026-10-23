package dev.casperrs.cookieClicker.vfx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.casperrs.cookieClicker.vfx.shape.ShapeKind;
import dev.casperrs.cookieClicker.vfx.shape.ShapeParams;
import dev.casperrs.cookieClicker.vfx.shape.ShapeParams.Direction;
import dev.casperrs.cookieClicker.vfx.shape.ShapeParams.Plane;
import dev.casperrs.cookieClicker.vfx.shape.ShapePlan;
import dev.casperrs.cookieClicker.vfx.shape.Shapes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ShapesTest {

    private static final double EPS = 1e-9;

    private static ShapeParams shape(ShapeKind kind, int points, double radius, double radiusStart, int duration) {
        ShapeParams d = ShapeParams.defaults(kind);
        return new ShapeParams(kind, points, radius, radiusStart, d.height(), d.turns(), d.angleDeg(),
                d.startAngleDeg(), d.spreadDeg(), d.upBias(), d.toX(), d.toY(), d.toZ(), d.plane(), duration,
                d.staggerTicks(), d.emitEvery(), d.direction(), d.strands());
    }

    @ParameterizedTest
    @EnumSource(ShapeKind.class)
    void everyShapeIsFiniteAndHasPoints(ShapeKind kind) {
        ShapePlan plan = Shapes.build(shape(kind, 12, 0.8, 0.2, 4));
        assertTrue(plan.totalPoints() > 0, kind + " has points");
        for (int t = 0; t < plan.ticks(); t++) {
            for (double v : plan.at(t)) {
                assertFalse(Double.isNaN(v) || Double.isInfinite(v), kind + " has a NaN/inf at tick " + t);
            }
        }
    }

    @Test
    void ringHasPointsPerTickOnItsRadiusAndIsCentred() {
        ShapePlan plan = Shapes.build(shape(ShapeKind.RING, 16, 1.0, 1.0, 1));
        assertEquals(1, plan.ticks());
        assertEquals(16, plan.totalPoints());
        double[] p = plan.at(0);
        double cx = 0;
        double cz = 0;
        for (int i = 0; i < p.length; i += ShapePlan.STRIDE) {
            assertEquals(1.0, Math.hypot(p[i], p[i + 2]), EPS);
            assertEquals(0, p[i + 1], EPS);
            cx += p[i];
            cz += p[i + 2];
        }
        assertEquals(0, cx, 1e-9, "symmetric around x");
        assertEquals(0, cz, 1e-9, "symmetric around z");
    }

}
