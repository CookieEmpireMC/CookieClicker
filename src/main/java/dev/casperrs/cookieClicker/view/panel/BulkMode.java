package dev.casperrs.cookieClicker.view.panel;

import java.util.Locale;

/**
 * The ×1 / ×5 / ×10 / Max toggle of a panel. Clicking the toggle row cycles through them in this order. Pure Java.
 */
public enum BulkMode {
    ONE(1), FIVE(5), TEN(10), MAX(0);

    private final int count;

    BulkMode(int count) {
        this.count = count;
    }

    /** How many a ×N buy takes; 0 for {@link #MAX} (the economy works out the count). */
    public int count() {
        return count;
    }

    public boolean max() {
        return this == MAX;
    }

    /** The next mode when the toggle is clicked: ×1 → ×5 → ×10 → Max → ×1. */
    public BulkMode next() {
        BulkMode[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    /** Key under {@code panel.bulk.labels}. */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
