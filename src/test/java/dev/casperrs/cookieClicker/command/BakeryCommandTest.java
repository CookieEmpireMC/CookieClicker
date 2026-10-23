package dev.casperrs.cookieClicker.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BakeryCommandTest {

    @Test
    void snapYawRoundsToStep() {
        assertEquals(90f, BakeryCommand.snapYaw(80f, 90f));
        assertEquals(0f, BakeryCommand.snapYaw(44f, 90f));
        assertEquals(90f, BakeryCommand.snapYaw(45f, 90f));
        assertEquals(-90f, BakeryCommand.snapYaw(270f, 90f));
        assertEquals(-180f, BakeryCommand.snapYaw(180f, 90f));
        assertEquals(-90f, BakeryCommand.snapYaw(-80f, 90f));
        assertEquals(0f, BakeryCommand.snapYaw(360f, 90f));
    }

    @Test
    void permissionName() {
        assertEquals("cookieempire.admin", BakeryCommand.PERMISSION);
    }
}
