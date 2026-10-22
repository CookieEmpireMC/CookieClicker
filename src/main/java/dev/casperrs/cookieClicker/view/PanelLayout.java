package dev.casperrs.cookieClicker.view;

import dev.casperrs.cookieClicker.config.StationVisualSettings;
import dev.casperrs.cookieClicker.config.StationVisualSettings.Offset;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Where everything of one hologram panel sits, and which line an eye ray hits. Positions are in the panel's frame
 * ({@link StationFrame}: right, up, forward) relative to the panel anchor, the bottom centre of the header.
 *
 * <p>Header, ×N toggle and rows are centred on one column: a row is its icon plus its text, and that pair is
 * centred under the header. Going down: header, {@code gap-after-header}, toggle, {@code gap-after-toggle}, then the
 * rows with {@code row-gap} between them. Every row is laid out as two text lines; with {@code same-width} each
 * row's background is padded to {@link #rowWidthPx()} and the header and toggle to the column width.
 *
 * <p>Clicks use the same numbers: {@link #pick} intersects the ray with the text plane and picks the row or toggle
 * whose rendered bounds contain the hit (plus {@code click-margin}); between lines or off the text it picks
 * nothing. A text display draws its lines upward from its position ({@link TextWidth#LINE_HEIGHT_PX} per line) and
 * centred on it. The text yaw calibration offset is assumed 0 (the text plane faces the frame's forward). Pure.
 */
record PanelLayout(StationVisualSettings.Panel p, float toggleScale, int rowCount) {

    /** Lines every row is laid out for (the panel texts are written as two lines). */
    static final int ROW_LINES = 2;
    /** Line width of the header and toggle displays: single lines that are never wrapped. */
    static final int SINGLE_LINE_WIDTH = 1000;

    static PanelLayout of(StationVisualSettings s, int rowCount) {
        return new PanelLayout(s.panel(), s.bulk().scale(), rowCount);
    }

    /** What a ray hit on a panel: a row slot, a toggle label ({@code mode} set) or the toggle between labels. */
    record Hit(Kind kind, int row, @Nullable BulkMode mode, double distance) {
        enum Kind { ROW, TOGGLE }
    }

    /** What a row slot renders now: whether it shows an item, its line count and background width (px). */
    record RowBox(boolean present, int lines, int widthPx) {
    }

    /** What the toggle renders now: its background width (px) and each label's {start, end} px from its left. */
    record ToggleBox(int widthPx, Map<BulkMode, int[]> labels) {
    }

    double headerHeight() {
        return TextWidth.blocks(TextWidth.LINE_HEIGHT_PX, p.headerScale());
    }

    double toggleHeight() {
        return TextWidth.blocks(TextWidth.LINE_HEIGHT_PX, toggleScale);
    }

    /** Bottom of the toggle text (its display position's height). */
    double toggleBottom() {
        return -p.headerGap() - toggleHeight();
    }

    double rowHeight() {
        return TextWidth.blocks(ROW_LINES * TextWidth.LINE_HEIGHT_PX, p.textScale());
    }

    /** Bottom of row {@code i}'s text block. */
    double rowBottom(int i) {
        return toggleBottom() - p.toggleGap() - rowHeight() - i * p.rowPitch();
    }

    /** Background width every row is padded to: the line width less one space, so padding never wraps. */
    int rowWidthPx() {
        return p.lineWidth() - TextWidth.SPACE_PX;
    }

    private double textWidth() {
        return TextWidth.blocks(rowWidthPx(), p.textScale());
    }

    private double iconLeft() {
        return p.iconOffset().right() - p.iconScale() / 2.0;
    }

    /** Width of the column: from a row icon's left edge to the right edge of a full-width row text. */
    double columnWidth() {
        return p.textOffset().right() + textWidth() - iconLeft();
    }

    /** Moves the icon + text pair so that it is centred on the column. */
    private double shift() {
        return -(iconLeft() + p.textOffset().right() + textWidth()) / 2.0;
    }

    /** Left edge of the row texts. */
    double textLeft() {
        return p.textOffset().right() + shift();
    }

    Offset iconAt(int i) {
        return new Offset(p.iconOffset().right() + shift(), rowBottom(i) + p.iconOffset().up(),
                p.iconOffset().forward());
    }

    /** Position of row {@code i}'s text display: its left edge with text-left-edge, else the column's text centre. */
    Offset textAt(int i) {
        double right = p.textLeftEdge() ? textLeft() : textLeft() + textWidth() / 2.0;
        return new Offset(right, rowBottom(i) + p.textOffset().up(), p.textOffset().forward());
    }

    Offset toggleAt() {
        return new Offset(0, toggleBottom(), 0);
    }

    /** Pixel width (at its own scale) the header is padded to; 0 without same-width. */
    int headerTargetPx() {
        return p.sameWidth() ? (int) Math.round(columnWidth() * TextWidth.PIXELS_PER_BLOCK / p.headerScale()) : 0;
    }

    /** Pixel width (at its own scale) the toggle is padded to; 0 without same-width. */
    int toggleTargetPx() {
        return p.sameWidth() ? (int) Math.round(columnWidth() * TextWidth.PIXELS_PER_BLOCK / toggleScale) : 0;
    }

    /** Bottom centre of the panel's one click box (it only triggers the ray check). */
    Offset boxBottom() {
        double bottom = rowCount > 0 ? rowBottom(rowCount - 1) : toggleBottom();
        return new Offset(0, bottom - p.clickMargin(), 0);
    }

    double boxHeight() {
        return headerHeight() + p.clickMargin() - boxBottom().up();
    }

    double boxWidth() {
        return columnWidth() + 2 * p.clickMargin();
    }

    /**
     * The row or toggle a ray hits first, or null.
     *
     * @param eye  ray start, in the panel frame relative to the panel anchor
     * @param dir  unit ray direction in the panel frame
     * @param rows what every row slot shows now (index = slot)
     */
    @Nullable Hit pick(Offset eye, Offset dir, double maxDistance, List<RowBox> rows, @Nullable ToggleBox toggle) {
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
                double textL = p.textLeftEdge() ? textLeft() : textLeft() + textWidth() / 2.0 - width / 2.0;
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
                double bottom = toggleBottom();
                if (r >= -half - m && r <= half + m && u >= bottom - m && u <= bottom + toggleHeight() + m) {
                    double px = r * TextWidth.PIXELS_PER_BLOCK / toggleScale + toggle.widthPx() / 2.0;
                    best = new Hit(Hit.Kind.TOGGLE, -1, label(toggle, px), tt);
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

    /** Distance along the ray to the plane {@code forward = plane}, or -1 when parallel, behind or too far. */
    private static double along(Offset eye, Offset dir, double plane, double max) {
        if (Math.abs(dir.forward()) < 1e-9) {
            return -1;
        }
        double t = (plane - eye.forward()) / dir.forward();
        return t >= 0 && t <= max ? t : -1;
    }
}
