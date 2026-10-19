package dev.casperrs.cookieClicker.house;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OperationGateTest {

    @Test
    void startsIdleAndHandsOutAValidToken() {
        OperationGate g = new OperationGate();
        assertFalse(g.busy());
        long t = g.begin();
        assertNotEquals(OperationGate.REFUSED, t);
        assertTrue(g.valid(t));
        assertTrue(g.busy());
    }

    @Test
    void refusesASecondOperationWhileBusyAndDoesNotDisturbTheFirst() {
        OperationGate g = new OperationGate();
        long first = g.begin();
        assertEquals(OperationGate.REFUSED, g.begin());
        assertEquals(OperationGate.REFUSED, g.begin());
        assertTrue(g.valid(first));
    }

    @Test
    void endingFreesTheGateAndInvalidatesTheToken() {
        OperationGate g = new OperationGate();
        long t = g.begin();
        g.end(t);
        assertFalse(g.busy());
        assertFalse(g.valid(t));
        assertNotEquals(OperationGate.REFUSED, g.begin());
    }

    @Test
    void resetInvalidatesTheOldTokenAndFreesTheGate() {
        OperationGate g = new OperationGate();
        long old = g.begin();
        g.reset();
        assertFalse(g.valid(old));
        assertFalse(g.busy());
        long fresh = g.begin();
        assertNotEquals(OperationGate.REFUSED, fresh);
        assertFalse(g.valid(old), "the old token stays stale even though the gate is busy again");
    }

    @Test
    void aStaleEndDoesNotFreeANewerOperation() {
        OperationGate g = new OperationGate();
        long old = g.begin();
        g.reset();
        long newer = g.begin();
        g.end(old); // the late callback of the cancelled operation
        assertTrue(g.busy());
        assertTrue(g.valid(newer));
        assertEquals(OperationGate.REFUSED, g.begin(), "still one at a time");
    }

    @Test
    void endingTwiceIsHarmless() {
        OperationGate g = new OperationGate();
        long a = g.begin();
        g.end(a);
        long b = g.begin();
        g.end(a); // duplicate end of the finished operation
        assertTrue(g.valid(b));
    }

    @Test
    void tokensAreUniqueAcrossResetsAndEnds() {
        OperationGate g = new OperationGate();
        Set<Long> seen = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            long t = g.begin();
            assertTrue(seen.add(t), "token reused: " + t);
            if (i % 3 == 0) {
                g.reset();
            } else if (i % 3 == 1) {
                g.end(t);
            } else {
                g.reset();
                g.reset();
            }
        }
    }

    @Test
    void refusedIsNeverValidNotEvenWhenIdle() {
        OperationGate g = new OperationGate();
        assertFalse(g.valid(OperationGate.REFUSED));
        g.begin();
        assertFalse(g.valid(OperationGate.REFUSED));
        g.end(OperationGate.REFUSED); // must not free the running operation
        assertTrue(g.busy());
        g.reset();
        assertFalse(g.valid(OperationGate.REFUSED));
    }

    @Test
    void resetOnAnIdleGateIsHarmless() {
        OperationGate g = new OperationGate();
        g.reset();
        g.reset();
        assertFalse(g.busy());
        assertNotEquals(OperationGate.REFUSED, g.begin());
    }
}
