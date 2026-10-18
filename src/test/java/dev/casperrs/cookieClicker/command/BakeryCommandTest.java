package dev.casperrs.cookieClicker.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void snapYawStaysInRangeForWildInputs() {
        for (float yaw = -1000; yaw <= 1000; yaw += 7.3f) {
            for (float step : new float[] {0f, 15f, 45f, 90f}) {
                float y = BakeryCommand.snapYaw(yaw, step);
                assertTrue(y >= -180f && y < 180f, yaw + "/" + step + " -> " + y);
            }
        }
    }

    @Test
    void zeroStepKeepsTheYawExactlyApartFromWrapping() {
        assertEquals(12.5f, BakeryCommand.snapYaw(12.5f, 0f));
        assertEquals(-170f, BakeryCommand.snapYaw(190f, 0f), 1e-4);
    }

    @Test
    void permissionName() {
        assertEquals("cookieempire.admin", BakeryCommand.PERMISSION);
    }
}
