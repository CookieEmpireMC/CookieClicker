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

    @Test
    void growingRingEasesOutFromStartToEndRadius() {
        ShapePlan plan = Shapes.build(shape(ShapeKind.RING, 10, 1.0, 0.2, 3));
        assertEquals(3, plan.ticks());
        assertEquals(30, plan.totalPoints());
        double r0 = Math.hypot(plan.at(0)[0], plan.at(0)[2]);
        double r1 = Math.hypot(plan.at(1)[0], plan.at(1)[2]);
        double r2 = Math.hypot(plan.at(2)[0], plan.at(2)[2]);
        assertEquals(0.2, r0, EPS);
        assertEquals(1.0, r2, EPS);
        assertTrue(r1 - r0 > r2 - r1, "ease-out: the first step is the biggest");
    }

    @Test
    void verticalRingStandsInTheXyPlane() {
        ShapeParams d = shape(ShapeKind.RING, 8, 0.5, 0.5, 1);
        ShapeParams vertical = new ShapeParams(d.kind(), d.points(), d.radius(), d.radiusStart(), d.height(),
                d.turns(), d.angleDeg(), d.startAngleDeg(), d.spreadDeg(), d.upBias(), d.toX(), d.toY(), d.toZ(),
                Plane.VERTICAL, 1, 0, 1, Direction.NONE, 1);
        double[] p = Shapes.build(vertical).at(0);
        for (int i = 0; i < p.length; i += ShapePlan.STRIDE) {
            assertEquals(0, p[i + 2], EPS, "z is 0");
            assertEquals(0.5, Math.hypot(p[i], p[i + 1]), EPS);
        }
    }

    @Test
    void burstDirectionsAreUnitVectorsAndPointsSitOnTheRadius() {
        ShapePlan plan = Shapes.build(shape(ShapeKind.BURST, 20, 0.3, 0.3, 1));
        assertEquals(20, plan.totalPoints());
        double[] p = plan.at(0);
        double sx = 0;
        double sy = 0;
        double sz = 0;
        for (int i = 0; i < p.length; i += ShapePlan.STRIDE) {
            assertEquals(1.0, Math.sqrt(p[i + 3] * p[i + 3] + p[i + 4] * p[i + 4] + p[i + 5] * p[i + 5]), EPS);
            assertEquals(0.3, Math.sqrt(p[i] * p[i] + p[i + 1] * p[i + 1] + p[i + 2] * p[i + 2]), EPS);
            sx += p[i + 3];
            sy += p[i + 4];
            sz += p[i + 5];
        }
        // a golden-spiral sphere is balanced: the mean direction is close to zero
        assertTrue(Math.abs(sx) / 20 < 0.1 && Math.abs(sy) / 20 < 0.1 && Math.abs(sz) / 20 < 0.1);
    }

    @Test
    void upBiasPushesBurstUpward() {
        ShapeParams d = shape(ShapeKind.BURST, 20, 0.3, 0.3, 1);
        ShapeParams biased = new ShapeParams(d.kind(), 20, 0.3, 0.3, 1, 1, 180, 0, 30, 0.5, 0, 1, 0,
                Plane.HORIZONTAL, 1, 0, 1, Direction.OUTWARD, 1);
        double[] p = Shapes.build(biased).at(0);
        for (int i = 0; i < p.length; i += ShapePlan.STRIDE) {
            assertTrue(p[i + 4] >= 0 - EPS, "with up-bias 0.5 nothing points down");
        }
    }

    @Test
    void progressiveShapesSpreadPointsOverTheDuration() {
        ShapePlan plan = Shapes.build(shape(ShapeKind.HELIX, 12, 0.5, 0.5, 4));
        assertEquals(4, plan.ticks());
        assertEquals(12, plan.totalPoints());
        for (int t = 0; t < 4; t++) {
            assertEquals(3, plan.pointsAt(t));
        }
    }

    @Test
    void sparklesStartOnTheOriginPlaneAndReachTheirHeight() {
        ShapeParams p = new ShapeParams(ShapeKind.SPARKLES, 5, 0.5, 0.5, 1.2, 1, 180, 0, 30, 0, 0, 1, 0,
                Plane.HORIZONTAL, 6, 0, 1, Direction.NONE, 1);
        ShapePlan plan = Shapes.build(p);
        assertEquals(6, plan.ticks());
        assertEquals(30, plan.totalPoints());
        for (int i = 0; i < plan.at(0).length; i += ShapePlan.STRIDE) {
            assertEquals(0, plan.at(0)[i + 1], EPS, "no gap at the bottom");
        }
        double[] last = plan.at(5);
        for (int i = 0; i < last.length; i += ShapePlan.STRIDE) {
            assertEquals(1.2, last[i + 1], EPS);
            assertTrue(Math.hypot(last[i], last[i + 2]) <= 0.5 + EPS, "inside the disc");
        }
    }

    @Test
    void sparkleStaggerAndEmitEveryChangeTimingAndCost() {
        ShapeParams p = new ShapeParams(ShapeKind.SPARKLES, 4, 0.5, 0.5, 1, 1, 180, 0, 30, 0, 0, 1, 0,
                Plane.HORIZONTAL, 8, 3, 2, Direction.NONE, 1);
        ShapePlan plan = Shapes.build(p);
        assertEquals(11, plan.ticks(), "stagger + life");
        assertEquals(16, plan.totalPoints(), "4 sparks x 4 emissions");
    }

    @Test
    void fountainStaysInsideItsCone() {
        ShapeParams d = ShapeParams.defaults(ShapeKind.FOUNTAIN);
        ShapePlan plan = Shapes.build(d);
        double minY = Math.cos(Math.toRadians(d.spreadDeg()));
        double[] p = plan.at(0);
        for (int i = 0; i < p.length; i += ShapePlan.STRIDE) {
            assertTrue(p[i + 4] >= minY - EPS);
        }
    }

    @Test
    void lineRunsFromOriginToTarget() {
        ShapeParams d = ShapeParams.defaults(ShapeKind.LINE);
        ShapeParams line = new ShapeParams(ShapeKind.LINE, 5, 0, 0, 1, 1, 180, 0, 30, 0, 2, 0, 0,
                Plane.HORIZONTAL, 1, 0, 1, d.direction(), 1);
        double[] p = Shapes.build(line).at(0);
        assertEquals(0, p[0], EPS);
        assertEquals(2, p[p.length - ShapePlan.STRIDE], EPS);
    }

    @Test
    void goldenDiscStaysInsideTheRadius() {
        for (int i = 0; i < 50; i++) {
            double[] xz = Shapes.goldenDisc(i, 50, 0.25);
            assertTrue(Math.hypot(xz[0], xz[1]) <= 0.25 + EPS);
        }
    }
}
