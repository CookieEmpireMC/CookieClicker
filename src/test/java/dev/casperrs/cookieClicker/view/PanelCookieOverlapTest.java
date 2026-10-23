package dev.casperrs.cookieClicker.view;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.casperrs.cookieClicker.config.StationVisualSettings;
import dev.casperrs.cookieClicker.config.StationVisualSettings.Offset;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

/**
 * the shipped plot layout (factory.yml: pedestal 18,6, shop 20,6, clicks 16,6, viewpoint
 * 15,2) with the default cookie box. Does the floating cookie's box sit in front of the panels' clickable text, seen
 * from the viewpoint? Ground y = 0, frame yaw 0 (the viewer looks along +z), cookie and panels on the z = 0 plane.
 */
class PanelCookieOverlapTest {
    private static final StationVisualSettings S = StationVisualSettings.from(new YamlConfiguration(), p -> { });
    private static final PanelLayout.RowBox TWO = new PanelLayout.RowBox(true, 2, 116);
    private static final PanelLayout.ToggleBox TOGGLE = new PanelLayout.ToggleBox(80, Map.of(
            BulkMode.ONE, new int[] {0, 20}, BulkMode.FIVE, new int[] {20, 40},
            BulkMode.TEN, new int[] {40, 60}, BulkMode.MAX, new int[] {60, 80}));

    /** {clickable samples, samples where the cookie box is hit first}. */
    static int[] count(double panelWorldX, double anchorUp, double cookieCentre, double eyeX, double eyeZ) {
        StationFrame frame = new StationFrame(panelWorldX, 0, 0, 0);
        Offset anchor = new Offset(0, anchorUp, 0);
        double w = S.cookie().scale() + S.cookie().hitboxMargin();
        double h = S.cookie().scale() + 2 * S.cookie().bobAmplitude() + S.cookie().hitboxMargin();
        BoundingBox box = new BoundingBox(-w / 2, cookieCentre - h / 2, -w / 2, w / 2, cookieCentre + h / 2, w / 2);
        Vector eye = new Vector(eyeX, 1.62, eyeZ);
        PanelLayout layout = PanelLayout.of(S, 2);
        int total = 0;
        int stolen = 0;
        // sample the whole click box (icons hang left of the text, so it is off-centre on the anchor)
        double left = panelWorldX + layout.boxBottom().right() - layout.boxWidth() / 2;
        for (double wx = left; wx <= left + layout.boxWidth(); wx += 0.02) {
            for (double wy = anchorUp + layout.boxBottom().up(); wy <= anchorUp + layout.headerHeight(); wy += 0.02) {
                Vector dir = new Vector(wx, wy, 0).subtract(eye).normalize();
                Offset e = frame.local(eye.getX(), eye.getY(), eye.getZ(), anchor);
                Offset d = frame.direction(dir.getX(), dir.getY(), dir.getZ());
                PanelLayout.Hit hit = layout.pick(e, d, 6, List.of(TWO, TWO), TOGGLE);
                if (hit == null) {
                    continue;
                }
                total++;
                var c = box.rayTrace(eye, dir, 6);
                if (c != null && c.getHitPosition().distance(eye) < hit.distance()) {
                    stolen++;
                }
            }
        }
        return new int[] {total, stolen};
    }

    @Test
    void theCookieBoxNeverShadowsAPanelSeenFromInFrontOfItOrFromTheViewpoint() {
        // basket looks from wicker_0 (cookie centre ~1.2) to golden_4 (~2.09, the highest allowed is 2.2)
        for (double cookieCentre : new double[] {1.2, 1.8, 2.088, 2.2}) {
            for (double eyeX : new double[] {-2, 2}) {
                for (double[] panel : new double[][] {{2, 2.55}, {-2, 3.2}}) {
                    assertEquals(0, count(panel[0], panel[1], cookieCentre, panel[0] + (eyeX > 0 ? 0.5 : -0.5), -3)[1],
                            "panel at x=" + panel[0] + ", cookie " + cookieCentre);
                }
            }
            // the viewpoint (15,2): the clicks panel (16,6) is 4.1 blocks away, in reach and not shadowed
            assertEquals(0, count(-2, 3.2, cookieCentre, -3, -4)[1], "clicks panel from the viewpoint, cookie " + cookieCentre);
        }
    }
}
