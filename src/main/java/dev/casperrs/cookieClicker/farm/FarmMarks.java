package dev.casperrs.cookieClicker.farm;

import dev.casperrs.cookieClicker.plot.Outline;
import dev.casperrs.cookieClicker.plot.Plot;
import dev.casperrs.cookieClicker.plot.RegionShape;

import java.util.ArrayList;
import java.util.List;

/** The particle points of {@code /bakery farm show}: each place's yard rectangle and a post at its viewpoint. Pure. */
public final class FarmMarks {

    /** Points per viewpoint post, half a block apart. */
    private static final int POST_POINTS = 5;
    private static final double POST_STEP = 0.5;

    private FarmMarks() {
    }

    public static List<double[]> marks(Farm farm, double step, int maxPointsPerYard) {
        List<double[]> out = new ArrayList<>();
        for (Farm.FarmPlace p : farm.places().values()) {
            Farm.Yard y = p.yard();
            out.addAll(Outline.points(RegionShape.cuboid("yard", y.minX(), y.y(), y.minZ(), y.maxX(), y.y(), y.maxZ()),
                    step, maxPointsPerYard));
            Plot.Viewpoint v = p.viewpoint();
            for (int i = 0; i < POST_POINTS; i++) {
                out.add(new double[] {v.x(), v.y() + i * POST_STEP, v.z()});
            }
        }
        return out;
    }
}
