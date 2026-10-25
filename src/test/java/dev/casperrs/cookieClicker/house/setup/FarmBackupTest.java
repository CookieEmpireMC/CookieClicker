package dev.casperrs.cookieClicker.house.setup;

import dev.casperrs.cookieClicker.farm.Farm;
import dev.casperrs.cookieClicker.house.design.DesignFiles;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The undo data of a farm setup: the slabs must cover the pasted box exactly, and the manifest must come back whole. */
class FarmBackupTest {

    private static final Farm.Box BOX = new Farm.Box(-20, 60, -5, 10, 80, 33);

    @Test
    void theSlabsCoverTheBoxExactlyOnceEvenAcrossNegativeCoordinates() {
        assertCoversExactlyOnce(FarmBackup.slabs(BOX));
    }

    @Test
    void slabsAreCutToTheBlockBudgetAndStillCoverTheBox() {
        List<Farm.Box> slabs = FarmBackup.slabs(BOX, 5000);
        for (Farm.Box s : slabs) {
            assertTrue(FarmBackup.volume(s) <= 5000, "slab over the budget: " + s);
        }
        assertCoversExactlyOnce(slabs);
    }

    private static void assertCoversExactlyOnce(List<Farm.Box> slabs) {
        Set<String> seen = new HashSet<>();
        for (Farm.Box s : slabs) {
            assertTrue(s.minX() >= BOX.minX() && s.maxX() <= BOX.maxX() && s.minZ() >= BOX.minZ()
                    && s.maxZ() <= BOX.maxZ() && s.minY() >= BOX.minY() && s.maxY() <= BOX.maxY(), "slab inside box");
            assertEquals(s.minX() >> 4, s.maxX() >> 4, "one chunk column in x");
            assertEquals(s.minZ() >> 4, s.maxZ() >> 4, "one chunk column in z");
            for (int x = s.minX(); x <= s.maxX(); x++) {
                for (int y = s.minY(); y <= s.maxY(); y++) {
                    for (int z = s.minZ(); z <= s.maxZ(); z++) {
                        assertTrue(seen.add(x + "," + y + "," + z), "block in two slabs: " + x + "," + y + "," + z);
                    }
                }
            }
        }
        assertEquals(FarmBackup.volume(BOX), seen.size());
    }

    @Test
    void theManifestComesBackWhole() throws Exception {
        FarmBackup backup = new FarmBackup("farm1", "minecraft:overworld", 1_700_000_000_000L, BOX,
                FarmBackup.slabs(BOX));
        String yaml = BackupFiles.toYaml(backup.toMap());
        assertEquals(backup, FarmBackup.fromMap(DesignFiles.parseYaml(yaml)));
    }

    @Test
    void aManifestWithoutSlabsIsRejected() {
        Map<String, Object> noSlabs = Map.of("created-at", 1L, "farm", "farm1", "world", "minecraft:overworld",
                "box", Map.of("min", List.of(0, 0, 0), "max", List.of(1, 1, 1)));
        assertThrows(IllegalArgumentException.class, () -> FarmBackup.fromMap(noSlabs));
    }
}
