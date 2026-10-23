package dev.casperrs.cookieClicker.house.setup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

}
