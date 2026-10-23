package dev.casperrs.cookieClicker.view;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;

/** the spin/bob a pop step carries is the segment's value for the step's end tick. */
class CookiePopMotionTest {

    private static final CookieMotion MOTION = new CookieMotion(120, 0.05, 80, 10);

    @Test
    void eachAnswerIsTheMotionAtNowPlusTicksAheadAndReportsThatTick() {
        long[] clock = {1000};
        List<Long> reported = new ArrayList<>();
        CookiePopMotion pop = new CookiePopMotion(MOTION, () -> clock[0], reported::add);

        assertEquals((float) MOTION.bob(1002), pop.translation(2).y(), 1e-6f);
        assertEquals(new Quaternionf().rotationY((float) MOTION.angle(1003)), pop.leftRotation(3));
        assertEquals(List.of(1002L, 1003L), reported, "the callback gets the tick each answer is for");

        clock[0] = 1010; // the next pop step, ten ticks later: sampled from the clock at that call
        pop.translation(2);
        assertEquals(1012L, reported.getLast());
    }
}
