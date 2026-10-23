package dev.casperrs.cookieClicker.view;

import dev.casperrs.cookieClicker.config.BasketSettings;
import org.jspecify.annotations.Nullable;

/**
 * One look of the cookie basket: tier (0 wicker, 1 bushel, 2 golden) and fill (0..4), picked from the cookies in
 * the pouch with the basket tier starts (each tier's range split into 5 fill steps on a log scale) and
 * a small hysteresis so a pouch hovering at a boundary never flickers. Pure maths.
 */
record BasketLook(int tier, int fill) {

    static final int LEVELS = BasketSettings.TIERS.size() * BasketSettings.FILLS;

    /** {@code custom_model_data} strings[0] of the {@code cookieclicker:cookie_basket} model, e.g. {@code golden_4}. */
    String id() {
        return BasketSettings.TIERS.get(tier) + "_" + fill;
    }

    int level() {
        return tier * BasketSettings.FILLS + fill;
    }

    static BasketLook ofLevel(int level) {
        int l = Math.clamp(level, 0, LEVELS - 1);
        return new BasketLook(l / BasketSettings.FILLS, l % BasketSettings.FILLS);
    }

    /** The level (tier × 5 + fill) for exactly {@code cookies}, without hysteresis. */
    static int level(double cookies, BasketSettings b) {
        var starts = b.tierStarts();
        if (!(cookies >= starts.getFirst())) {
            return 0; // also NaN
        }
        int tiers = BasketSettings.TIERS.size();
        int tier = 0;
        while (tier + 1 < tiers && cookies >= starts.get(tier + 1)) {
            tier++;
        }
        double lo = starts.get(tier);
        double hi = starts.get(tier + 1);
        double t = Math.log(cookies / lo) / Math.log(hi / lo);
        int fill = (int) Math.clamp(Math.floor(t * BasketSettings.FILLS), 0, BasketSettings.FILLS - 1);
        return tier * BasketSettings.FILLS + fill;
    }

    /**
     * The look to show for {@code cookies}: the exact one when nothing is shown yet, otherwise {@code current} until
     * the pouch is {@code hysteresis} past a boundary (then the look it reached, possibly several steps away).
     */
    static BasketLook pick(double cookies, @Nullable BasketLook current, BasketSettings b) {
        if (current == null) {
            return ofLevel(level(cookies, b));
        }
        double h = 1 + b.hysteresis();
        int up = level(cookies / h, b);
        int down = level(cookies * h, b);
        int now = current.level();
        if (now < up) {
            return ofLevel(up);
        }
        if (now > down) {
            return ofLevel(down);
        }
        return current;
    }

    /**
     * The cookie centre above the ground for this look: basket bottom + top + gap + half the cookie + its bob,
     * clamped to {@code cookieMaxHeight} so it stays clickable.
     */
    double cookieCentre(BasketSettings b, double basketBottom, double cookieScale, double bobAmplitude) {
        double up = basketBottom + b.height(tier, fill) + b.cookieGap() + cookieScale / 2 + bobAmplitude;
        return Math.min(up, b.cookieMaxHeight());
    }
}
