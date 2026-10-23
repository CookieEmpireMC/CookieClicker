package dev.casperrs.cookieClicker.house;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class CameraReturnTest {

    @Test
    void roundTripsExactly() {
        CameraReturn r = new CameraReturn("CREATIVE", "minecraft:overworld", 15.5, 64.0, -2.25, 179.9f, -14f);
        assertEquals(r, CameraReturn.decode(r.encode()));
        CameraReturn odd = new CameraReturn("SURVIVAL", "minecraft:the_nether", -1e7, 1.0E-5, 123456789.123456789,
                -0.0f, 90f);
        assertEquals(odd, CameraReturn.decode(odd.encode()));
    }

    @Test
    void badInputIsNullNeverAnException() {
        String[] bad = {"", ";", "SURVIVAL", "SURVIVAL;minecraft:overworld;1;2;3;4", "SURVIVAL;minecraft:overworld;1;2;3;4;5;6",
                "SURVIVAL;minecraft:overworld;a;2;3;4;5", "SURVIVAL;minecraft:overworld;1;2;3;4;x",
                "SURVIVAL;minecraft:overworld;NaN;2;3;4;5", "SURVIVAL;minecraft:overworld;1;Infinity;3;4;5",
                "SURVIVAL;minecraft:overworld;1;2;3;NaN;5", "SURVIVAL;minecraft:overworld;1;2;3;4;-Infinity",
                ";minecraft:overworld;1;2;3;4;5", "SURVIVAL;;1;2;3;4;5", "  ;minecraft:overworld;1;2;3;4;5",
                "SURVIVAL;minecraft:overworld;;2;3;4;5"};
        for (String b : bad) {
            assertNull(CameraReturn.decode(b), "'" + b + "'");
        }
        assertNull(CameraReturn.decode(null));
    }

}
