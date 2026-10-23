package dev.casperrs.cookieClicker.house.design;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.casperrs.cookieClicker.config.YamlFiles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class BuildingSlotsTest {

    private static BuildingSlots.Result parse(String text) throws Exception {
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString(text);
        return BuildingSlots.parse(YamlFiles.toMap(y));
    }

    @Test
    void goodSlotsKeepFileOrderCellsAndYaw() throws Exception {
        BuildingSlots.Result r = parse("""
                slots:
                  villager_baker:
                    - {at: [5, 0, 2], yaw: 270}
                    - {at: [7, 0, 2], yaw: 270}
                  industrial_oven:
                    - {at: [27, 0, 12], yaw: 90}
                """);
        assertTrue(r.problems().isEmpty(), r.problems().toString());
        assertEquals(List.of(new BuildingSlots.Slot(5, 0, 2, 270f), new BuildingSlots.Slot(7, 0, 2, 270f)),
                r.slots().of("villager_baker"));
        assertEquals(1, r.slots().of("industrial_oven").size());
        assertEquals(3, r.slots().columns().size());
        assertTrue(r.slots().of("cocoa_plantation").isEmpty());
    }

    @Test
    void overTheCapKeepsTenPerTypeAndWarns() throws Exception {
        StringBuilder text = new StringBuilder("slots:\n  villager_baker:\n");
        for (int i = 0; i < 11; i++) {
            text.append("    - {at: [").append(i).append(", 0, 0], yaw: 0}\n");
        }
        BuildingSlots.Result r = parse(text.toString());
        assertEquals(BuildingSlots.MAX_PER_TYPE, r.slots().of("villager_baker").size());
        assertEquals(1, r.problems().size(), r.problems().toString());
    }

    @Test
    void shippedSlotsFileLoadsWithoutProblems() throws Exception {
        Path file = Path.of("src/main/resources/houses/building-slots.yml");
        assumeTrue(Files.isRegularFile(file), "houses/building-slots.yml is not on this branch yet");
        BuildingSlots.Result r = parse(Files.readString(file));
        assertTrue(r.problems().isEmpty(), r.problems().toString());
        assertEquals(8, r.slots().of("villager_baker").size());
    }
}
