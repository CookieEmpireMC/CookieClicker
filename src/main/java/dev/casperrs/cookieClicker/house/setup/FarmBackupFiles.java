package dev.casperrs.cookieClicker.house.setup;

import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToIntFunction;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Farm backups on disk: {@code farm-backups/<farm>/<millis>/backup.yml} plus one {@code <slab>.nbt} per slab. The
 * manifest is written last, so a folder without it is an unfinished write and is ignored. Blocking IO: IO thread only.
 */
final class FarmBackupFiles {

    static final String MANIFEST = "backup.yml";

    /** One backup as read from disk; {@code slabs[i]} is the structure of slab i. */
    record Read(Path dir, String manifest, byte[][] slabs) {
    }

    private FarmBackupFiles() {
    }

    static boolean any(Path farmDir) throws IOException {
        return newest(farmDir) != null;
    }

    static @Nullable Path newest(Path farmDir) throws IOException {
        if (!Files.isDirectory(farmDir)) {
            return null;
        }
        try (Stream<Path> dirs = Files.list(farmDir)) {
            return dirs.filter(d -> d.getFileName().toString().matches("\\d{1,19}")
                            && Files.isRegularFile(d.resolve(MANIFEST)))
                    .max(Comparator.comparingLong(d -> Long.parseLong(d.getFileName().toString())))
                    .orElse(null);
        }
    }

    /** The newest backup, or null; {@code slabCount} comes from its manifest through {@code countSlabs}. */
    static @Nullable Read readNewest(Path farmDir, ToIntFunction<String> countSlabs)
            throws IOException {
        Path dir = newest(farmDir);
        if (dir == null) {
            return null;
        }
        String manifest = Files.readString(dir.resolve(MANIFEST), StandardCharsets.UTF_8);
        int n = countSlabs.applyAsInt(manifest);
        byte[][] slabs = new byte[n][];
        for (int i = 0; i < n; i++) {
            slabs[i] = Files.readAllBytes(dir.resolve(i + ".nbt"));
        }
        return new Read(dir, manifest, slabs);
    }

    static void write(Path dir, String manifest, List<byte[]> slabs) throws IOException {
        Files.createDirectories(dir);
        for (int i = 0; i < slabs.size(); i++) {
            Files.write(dir.resolve(i + ".nbt"), slabs.get(i));
        }
        Files.writeString(dir.resolve(MANIFEST), manifest, StandardCharsets.UTF_8);
    }

    static void deleteQuietly(Logger log, Path dir) {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> files = Files.list(dir)) {
            Files.deleteIfExists(dir.resolve(MANIFEST)); // first: a half-deleted folder never looks like a backup
            for (Path f : files.toList()) {
                Files.deleteIfExists(f);
            }
            Files.deleteIfExists(dir);
        } catch (IOException e) {
            log.log(Level.WARNING, "Could not delete the farm backup " + dir, e);
        }
    }
}
