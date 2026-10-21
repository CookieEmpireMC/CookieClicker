package dev.casperrs.cookieClicker.view;

import dev.casperrs.cookieClicker.vfx.PopMotion;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

/**
 * The giant cookie's spin and bob for a pop: every pop step asks where the cookie will be {@code ticksAhead} ticks
 * from now (sampled from {@code clock}, the server tick, at the call), so squash, spin and bob travel in one
 * interpolation. Each answer is reported to {@code onTarget} (the tick it is for), so the spin/bob task knows the
 * pop owns the display until that tick and sends its next segment only after the pop has ended. Pure Java.
 *
 * <p>Translation {@code (0, bob(now + a), 0)}, left rotation {@code rotationY(angle(now + a))}, the same values a
 * spin/bob segment would send for that tick.
 */
public final class CookiePopMotion implements PopMotion {

    private final CookieMotion motion;
    private final LongSupplier clock;
    private final LongConsumer onTarget;

    public CookiePopMotion(CookieMotion motion, LongSupplier clock, LongConsumer onTarget) {
        this.motion = motion;
        this.clock = clock;
        this.onTarget = onTarget;
    }

    @Override
    public Vector3f translation(int ticksAhead) {
        long t = target(ticksAhead);
        return new Vector3f(0, (float) motion.bob(t), 0);
    }

    @Override
    public Quaternionf leftRotation(int ticksAhead) {
        long t = target(ticksAhead);
        return new Quaternionf().rotationY((float) motion.angle(t));
    }

    private long target(int ticksAhead) {
        long t = clock.getAsLong() + ticksAhead;
        onTarget.accept(t);
        return t;
    }
}
