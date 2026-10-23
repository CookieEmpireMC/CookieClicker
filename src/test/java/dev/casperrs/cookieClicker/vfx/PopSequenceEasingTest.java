package dev.casperrs.cookieClicker.vfx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.casperrs.cookieClicker.vfx.shape.Easing;
import dev.casperrs.cookieClicker.vfx.shape.PopSequence;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Edge cases of the pure pop timeline and easing maths (reviewer-owned). */
class PopSequenceEasingTest {

    @Test
    void emptySequenceGetsOnlyTheRestStep() {
        PopSequence seq = new PopSequence(List.of());
        assertEquals(1, seq.steps().size());
        assertEquals(3, seq.totalTicks());
        assertEquals(0, seq.startTick(0));
    }

    @Test
    void lastStepNotAtRestIsFollowedByARestStep() {
        PopSequence seq = new PopSequence(List.of(new PopSequence.Step(1.5, 1.5, 1.5, 4)));
        assertEquals(2, seq.steps().size());
        PopSequence.Step last = seq.steps().getLast();
        assertEquals(1.0, last.sx());
        assertEquals(1.0, last.sy());
        assertEquals(1.0, last.sz());
        assertEquals(4, seq.startTick(1));
        assertEquals(7, seq.totalTicks());
    }

    @Test
    void easingClampsAndHitsEndpoints() {
        assertEquals(0.0, Easing.outQuad(-5));
        assertEquals(1.0, Easing.outQuad(7));
        assertEquals(0.0, Easing.outQuad(0));
        assertEquals(1.0, Easing.outQuad(1));
        assertEquals(0.75, Easing.outQuad(0.5), 1e-12);
        assertEquals(0.0, Easing.clamp01(Double.NEGATIVE_INFINITY));
        assertEquals(1.0, Easing.clamp01(Double.POSITIVE_INFINITY));
        assertEquals(5.0, Easing.lerp(0, 10, 0.5), 1e-12);
    }

}
