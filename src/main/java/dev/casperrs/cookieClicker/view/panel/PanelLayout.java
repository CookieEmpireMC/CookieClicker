package dev.casperrs.cookieClicker.view.panel;

import dev.casperrs.cookieClicker.config.StationVisualSettings;
import dev.casperrs.cookieClicker.config.StationVisualSettings.Offset;
import dev.casperrs.cookieClicker.text.TextWidth;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Where everything of one hologram panel sits, and which line an eye ray hits. Positions are in the panel's frame
 * (the station frame: right, up, forward) relative to the panel anchor, the bottom centre of the header.
 *
 * <p>One text column per panel, centred on the anchor and as wide as a full row background ({@link #rowWidthPx()}
 * at the row text scale): header, ×N toggle and every row text share its left edge and width (with
 * {@code same-width}), header and toggle centred in it, row texts left-aligned, row icons hanging left of it.
 * Going down: header, {@code gap-after-header}, toggle, {@code gap-after-toggle}, then the rows with
 * {@code row-gap} between them. Every row is laid out as two text lines.
 *
 * <p>Clicks use the same numbers: {@link #pick} intersects the ray with the text plane and picks the row or toggle
 * whose rendered bounds contain the hit (plus {@code click-margin}); between lines or off the text it picks
 * nothing. A text display draws its lines upward from its position ({@link TextWidth#LINE_HEIGHT_PX} per line) and
 * centred on it. The text yaw calibration offset is assumed 0 (the text plane faces the frame's forward). Pure.
 */
public record PanelLayout(StationVisualSettings.Panel p, float toggleScale, int rowCount) {


    public static PanelLayout of(StationVisualSettings s, int rowCount) {
        return new PanelLayout(s.panel(), s.bulk().scale(), rowCount);
    }

    /**
     * What a ray hit on a panel: a row slot, a toggle label ({@code mode} set), the toggle between labels, or a
     * ▲ / ▼ page label on the toggle line.
     */
    public record Hit(Kind kind, int row, @Nullable BulkMode mode, double distance) {
        public enum Kind { ROW, TOGGLE, PAGE_UP, PAGE_DOWN }
    }

    /** What a row slot renders now: whether it shows an item, its line count and background width (px). */
    public record RowBox(boolean present, int lines, int widthPx) {
    }

    /**
     * What the toggle renders now: its background width (px) and each label's {start, end} px from its left, the
     * ▲ / ▼ page labels ({@link Hit.Kind#PAGE_UP} / {@link Hit.Kind#PAGE_DOWN}) included when shown.
     */
    public record ToggleBox(int widthPx, Map<BulkMode, int[]> labels, Map<Hit.Kind, int[]> pages) {
        /** Without page labels. */
        public ToggleBox(int widthPx, Map<BulkMode, int[]> labels) {
            this(widthPx, labels, Map.of());
        }
    }

    public double headerHeight() {
        return TextWidth.blocks(TextWidth.LINE_HEIGHT_PX, p.headerScale());
    }

    public double toggleHeight() {
        return TextWidth.blocks(TextWidth.LINE_HEIGHT_PX, toggleScale);
    }

    /** Bottom of the toggle text (its display position's height). */
    public double toggleBottom() {
        return -p.headerGap() - toggleHeight();
    }

    /**
     * Lowest point of the toggle's click band: the toggle line is easy to hit, so its band reaches half-way into
     * the gaps above and below it (never less than {@code click-margin}); the other half stays air.
     */
    public double toggleHitBottom() {
        return toggleBottom() - Math.max(p.clickMargin(), p.toggleGap() / 2.0);
    }

    /** Highest point of the toggle's click band (see {@link #toggleHitBottom()}). */
    public double toggleHitTop() {
        return toggleBottom() + toggleHeight() + Math.max(p.clickMargin(), p.headerGap() / 2.0);
    }

    public double rowHeight() {
        return TextWidth.blocks(TextWidth.ROW_LINES * TextWidth.LINE_HEIGHT_PX, p.textScale());
    }

    /** Bottom of row {@code i}'s text block. */
    public double rowBottom(int i) {
        return toggleBottom() - p.toggleGap() - rowHeight() - i * p.rowPitch();
    }

    /** Background width every row is padded to: the line width less one space, so padding never wraps. */
    public int rowWidthPx() {
        return p.lineWidth() - TextWidth.SPACE_PX;
    }

    /** Width of the text column: a full-width row background ({@link #rowWidthPx()} at the row text scale). */
    public double columnWidth() {
        return TextWidth.blocks(rowWidthPx(), p.textScale());
    }

    /** Left edge of the text column and of every row text: the column is centred on the panel anchor. */
    public double textLeft() {
        return -columnWidth() / 2.0;
    }

    /** Moves a row (icon + text) so that its text starts at {@link #textLeft()}; the icon hangs left of it. */
    private double shift() {
        return textLeft() - p.textOffset().right();
    }

    /** Left edge of the row icons, outside the text column. */
    private double iconLeft() {
        return p.iconOffset().right() + shift() - p.iconScale() / 2.0;
    }

    public Offset iconAt(int i) {
        return new Offset(p.iconOffset().right() + shift(), rowBottom(i) + p.iconOffset().up(),
                p.iconOffset().forward());
    }

    /** Position of row {@code i}'s text display: its left edge with text-left-edge, else the column centre. */
    public Offset textAt(int i) {
        double right = p.textLeftEdge() ? textLeft() : textLeft() + columnWidth() / 2.0;
        return new Offset(right, rowBottom(i) + p.textOffset().up(), p.textOffset().forward());
    }

    /** The toggle line, centred on the text column. */
    public Offset toggleAt() {
        return new Offset(0, toggleBottom(), 0);
    }

    /** Pixel width (at its own scale) the header is padded to: the text column; 0 without same-width. */
    public int headerTargetPx() {
        return p.sameWidth() ? (int) Math.round(columnWidth() * TextWidth.PIXELS_PER_BLOCK / p.headerScale()) : 0;
    }

    /** Pixel width (at its own scale) the toggle is padded to: the text column; 0 without same-width. */
    public int toggleTargetPx() {
        return p.sameWidth() ? (int) Math.round(columnWidth() * TextWidth.PIXELS_PER_BLOCK / toggleScale) : 0;
    }

    /** Left edge of everything clickable: the row icons hang left of the text column. */
    private double boxLeft() {
        return Math.min(iconLeft(), textLeft());
    }

    private double boxRight() {
        return textLeft() + columnWidth();
    }

    /** Bottom centre of the panel's one click box (it only triggers the ray check). */
    public Offset boxBottom() {
        double bottom = rowCount > 0 ? rowBottom(rowCount - 1) : toggleBottom();
        return new Offset((boxLeft() + boxRight()) / 2.0, bottom - p.clickMargin(), 0);
    }

    public double boxHeight() {
        return headerHeight() + p.clickMargin() - boxBottom().up();
    }

    public double boxWidth() {
        return boxRight() - boxLeft() + 2 * p.clickMargin();
    }

    /**
     * The row or toggle a ray hits first, or null. The ray ({@code eye}, unit {@code dir}) is in the panel frame
     * relative to the anchor; {@code rows} are indexed by slot.
     */
    public @Nullable Hit pick(Offset eye, Offset dir, double maxDistance, List<RowBox> rows, @Nullable ToggleBox toggle) {
        double m = p.clickMargin();
        Hit best = null;
        double rowPlane = p.textOffset().forward();
        double t = along(eye, dir, rowPlane, maxDistance);
        if (t >= 0) {
            double r = eye.right() + t * dir.right();
            double u = eye.up() + t * dir.up();
            for (int i = 0; i < Math.min(rows.size(), rowCount); i++) {
                RowBox row = rows.get(i);
                if (!row.present()) {
                    continue;
                }
                double bottom = rowBottom(i) + p.textOffset().up();
                double top = bottom + TextWidth.blocks(Math.max(1, row.lines()) * TextWidth.LINE_HEIGHT_PX,
                        p.textScale());
                Offset icon = iconAt(i);
                double width = TextWidth.blocks(row.widthPx(), p.textScale());
                double textL = p.textLeftEdge() ? textLeft() : textLeft() + columnWidth() / 2.0 - width / 2.0;
                double left = Math.min(icon.right() - p.iconScale() / 2.0, textL);
                double right = textL + width;
                double low = Math.min(bottom, icon.up() - p.iconScale() / 2.0);
                double high = Math.max(top, icon.up() + p.iconScale() / 2.0);
                if (r >= left - m && r <= right + m && u >= low - m && u <= high + m) {
                    best = new Hit(Hit.Kind.ROW, i, null, t);
                    break;
                }
            }
        }
        if (toggle != null) {
            double tt = along(eye, dir, 0, maxDistance);
            if (tt >= 0 && (best == null || tt < best.distance())) {
                double r = eye.right() + tt * dir.right();
                double u = eye.up() + tt * dir.up();
                double half = TextWidth.blocks(toggle.widthPx() / 2.0, toggleScale);
                if (r >= -half - m && r <= half + m && u >= toggleHitBottom() && u <= toggleHitTop()) {
                    double px = r * TextWidth.PIXELS_PER_BLOCK / toggleScale + toggle.widthPx() / 2.0;
                    Hit.Kind page = page(toggle, px);
                    best = page != null ? new Hit(page, -1, null, tt) : new Hit(Hit.Kind.TOGGLE, -1, label(toggle, px), tt);
                }
            }
        }
        return best;
    }

    /** The label under pixel {@code px} of the toggle (labels are tight: a gap between them cycles). */
    private static @Nullable BulkMode label(ToggleBox toggle, double px) {
        for (Map.Entry<BulkMode, int[]> e : toggle.labels().entrySet()) {
            if (px >= e.getValue()[0] && px <= e.getValue()[1]) {
                return e.getKey();
            }
        }
        return null;
    }

    /** The page label under pixel {@code px} of the toggle, or null. */
    private static Hit.@Nullable Kind page(ToggleBox toggle, double px) {
        for (Map.Entry<Hit.Kind, int[]> e : toggle.pages().entrySet()) {
            if (px >= e.getValue()[0] && px <= e.getValue()[1]) {
                return e.getKey();
            }
        }
        return null;
    }

    /** Distance along the ray to the plane {@code forward = plane}, or -1 when parallel, behind or too far. */
    private static double along(Offset eye, Offset dir, double plane, double max) {
        if (Math.abs(dir.forward()) < 1e-9) {
            return -1;
        }
        double t = (plane - eye.forward()) / dir.forward();
        return t >= 0 && t <= max ? t : -1;
    }
}
