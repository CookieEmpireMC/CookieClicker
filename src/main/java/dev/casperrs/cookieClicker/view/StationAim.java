package dev.casperrs.cookieClicker.view;

import dev.casperrs.cookieClicker.config.StationVisualSettings;
import dev.casperrs.cookieClicker.config.StationVisualSettings.Offset;
import dev.casperrs.cookieClicker.view.panel.PanelKind;
import dev.casperrs.cookieClicker.view.panel.PanelLayout;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.jspecify.annotations.Nullable;

/**
 * The first thing an eye ray hits at a view: a line of panel {@code panel} ({@code hit}), or the cookie
 * ({@code cookieAt} = where the ray enters its box). Every click on a view's boxes is resolved this way, so a box
 * that overlaps another never steals the click.
 */
record StationAim(@Nullable PanelKind panel, PanelLayout.@Nullable Hit hit, @Nullable Vector cookieAt,
                  double distance) {

    static @Nullable StationAim find(StationView view, StationVisualSettings s, Vector eye, Vector dir,
                                     double maxDistance) {
        StationAim best = null;
        RayTraceResult cookie = view.cookieHitbox.isValid()
                ? view.cookieHitbox.getBoundingBox().rayTrace(eye, dir, maxDistance) : null;
        if (cookie != null) {
            best = new StationAim(null, null, cookie.getHitPosition(), cookie.getHitPosition().distance(eye));
        }
        for (StationView.Panel panel : view.panels()) {
            Offset e = panel.frame.local(eye.getX(), eye.getY(), eye.getZ(), panel.anchor);
            Offset d = panel.frame.direction(dir.getX(), dir.getY(), dir.getZ());
            PanelLayout.Hit hit = PanelLayout.of(s, panel.rows.size())
                    .pick(e, d, maxDistance, panel.rowBoxes(), panel.toggleBox);
            if (hit != null && (best == null || hit.distance() < best.distance())) {
                best = new StationAim(panel.kind, hit, null, hit.distance());
            }
        }
        return best;
    }
}
