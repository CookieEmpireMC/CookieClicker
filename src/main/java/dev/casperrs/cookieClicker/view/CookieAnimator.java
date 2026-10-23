package dev.casperrs.cookieClicker.view;

import dev.casperrs.cookieClicker.config.BasketSettings;
import dev.casperrs.cookieClicker.config.StationVisualSettings;
import dev.casperrs.cookieClicker.economy.CookiePouch;
import dev.casperrs.cookieClicker.vfx.PopMotion;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * The cookie's spin/bob (one interpolated segment at a time, shared with the click pop) and the basket that grows
 * with the pouch. Main thread.
 */
final class CookieAnimator {

    private final Supplier<StationVisualSettings> settings;
    private CookieMotion motion;
    private BiConsumer<Player, Location> tierUp = (player, at) -> { };

    CookieAnimator(Supplier<StationVisualSettings> settings) {
        this.settings = settings;
        this.motion = CookieMotion.of(settings.get().cookie());
    }

    void setTierUp(BiConsumer<Player, Location> tierUp) {
        this.tierUp = tierUp;
    }

    /** Re-reads the motion settings (reload). */
    void reset() {
        motion = CookieMotion.of(settings.get().cookie());
    }

    CookieMotion motion() {
        return motion;
    }

    /** Animation tick for one view: the next spin segment when due, and a tier-up look switch at its peak. */
    void animate(StationView v, long now) {
        // a pop owns the cookie's interpolation until its last step's tick; the next segment starts the tick after
        // it, so it can never overwrite a pop step sent in the same tick
        if (v.motionFromPop ? now > v.motionTarget : now >= v.motionTarget) {
            segment(v, now);
        }
        if (v.pendingLook != null && now >= v.basketSwitchAt) {
            showLook(v, v.pendingLook);
        }
    }

    /** Sends a spin segment that is due this tick before a pop, so the pop's transformation is the last one and wins. */
    void beforePop(StationView view) {
        long now = Bukkit.getCurrentTick();
        if (!view.motionFromPop && now >= view.motionTarget) {
            segment(view, now);
        }
    }

    /** Each pop step carries the motion at its end tick and marks the cookie as owned by the pop until then. */
    PopMotion popMotion(StationView view) {
        return new CookiePopMotion(motion, Bukkit::getCurrentTick, target -> {
            view.motionTarget = target;
            view.motionFromPop = true;
        });
    }

    /** One interpolated segment toward the motion at {@code now + segment}; keeps the current scale (pop rule). */
    private void segment(StationView v, long now) {
        int seg = settings.get().cookie().segmentTicks();
        long target = now + seg;
        v.motionTarget = target;
        v.motionFromPop = false;
        if (!v.cookie.isValid()) {
            return;
        }
        Transformation current = v.cookie.getTransformation();
        v.cookie.setInterpolationDelay(0);
        v.cookie.setInterpolationDuration(seg);
        v.cookie.setTransformation(new Transformation(new Vector3f(0, (float) motion.bob(target), 0),
                new Quaternionf().rotationY((float) motion.angle(target)),
                new Vector3f(current.getScale()), new Quaternionf(current.getRightRotation())));
    }

    /**
     * The basket follows the pouch (only when its look changes). A higher tier first plays the tier-up feedback and
     * switches at the effect's peak ({@code tier-up-switch-ticks}); a lower tier or another fill switches at once.
     */
    void updateBasket(Player owner, StationView v, CookiePouch pouch, long now) {
        BasketLook current = v.basketLook;
        if (v.basket == null || current == null || v.pendingLook != null) {
            return;
        }
        BasketSettings b = settings.get().basket();
        BasketLook next = BasketLook.pick(pouch.pouch(), current, b);
        if (next.equals(current)) {
            return;
        }
        if (next.tier() > current.tier()) {
            double[] at = v.pedestalBottom;
            tierUp.accept(owner, new Location(owner.getWorld(), at[0], at[1], at[2]));
            if (b.tierUpSwitchTicks() > 0) {
                v.pendingLook = next;
                v.basketSwitchAt = now + b.tierUpSwitchTicks();
                return;
            }
        }
        showLook(v, next);
    }

    /** Shows {@code look} on the basket and glides the cookie (and moves its hitbox) to the new height. */
    private void showLook(StationView v, BasketLook look) {
        BasketLook old = v.basketLook;
        v.pendingLook = null;
        v.basketSwitchAt = Long.MIN_VALUE;
        v.basketLook = look;
        if (v.basket == null || old == null || !v.basket.isValid()) {
            return;
        }
        v.basket.setItemStack(StationSpawner.basketStack(look));
        StationVisualSettings s = settings.get();
        StationVisualSettings.Cookie c = s.cookie();
        double dy = look.cookieCentre(s.basket(), v.pedestalUp, c.scale(), c.bobAmplitude())
                - old.cookieCentre(s.basket(), v.pedestalUp, c.scale(), c.bobAmplitude());
        if (Math.abs(dy) < 1e-4) {
            return;
        }
        v.cookieCentre[1] += dy;
        moveUp(v.cookie, dy);
        moveUp(v.cookieHitbox, dy);
    }

    private static void moveUp(Entity e, double dy) {
        if (e.isValid()) {
            Location l = e.getLocation();
            l.setY(l.getY() + dy);
            e.teleport(l);
        }
    }
}
