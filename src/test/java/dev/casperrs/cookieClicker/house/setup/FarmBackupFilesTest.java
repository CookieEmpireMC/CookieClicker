package dev.casperrs.cookieClicker.house.setup;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Farm backups on disk: undo reads the newest complete backup, never a half-written one. */
class FarmBackupFilesTest {

    @TempDir
    Path dir;

    @Test
    void theNewestCompleteBackupIsReadBackWithItsSlabs() throws IOException {
        Path farm = dir.resolve("farm1");
        FarmBackupFiles.write(farm.resolve("1000"), "old", List.of(new byte[] {1}));
        FarmBackupFiles.write(farm.resolve("2000"), "new", List.of(new byte[] {2}, new byte[] {3}));
        FarmBackupFiles.Read read = FarmBackupFiles.readNewest(farm, m -> m.equals("new") ? 2 : 1);
        assertNotNull(read);
        assertEquals(farm.resolve("2000"), read.dir());
        assertEquals("new", read.manifest());
        assertArrayEquals(new byte[] {3}, read.slabs()[1]);
    }

    @Test
    void aFolderWithoutItsManifestIsAnUnfinishedWriteAndIsIgnored() throws IOException {
        Path farm = dir.resolve("farm1");
        Files.createDirectories(farm.resolve("3000"));
        Files.write(farm.resolve("3000").resolve("0.nbt"), new byte[] {9});
        assertFalse(FarmBackupFiles.any(farm));
        assertNull(FarmBackupFiles.readNewest(farm, m -> 1));
    }
}
