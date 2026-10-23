package dev.casperrs.cookieClicker.house.setup;

import org.bukkit.configuration.file.YamlConfiguration;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * A backup folder holds pairs {@code <millis>.yml} (block data, plot points) + {@code <millis>.nbt} (structure of the
 * house box); undo uses the newest pair. Blocking IO: call on the IO thread.
 */
final class BackupFiles {

    private BackupFiles() {
    }

    /** One backup as read from disk; {@code nbt} is null when the structure file is missing. */
    record Read(Path yml, String text, byte @Nullable [] nbt) {
    }

    static @Nullable Path newest(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return null;
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(p -> p.getFileName().toString().matches("\\d+\\.yml"))
                    .max((a, b) -> Long.compare(stamp(a), stamp(b)))
                    .orElse(null);
        }
    }

    /** The newest backup of {@code dir}, or null when there is none. */
    static @Nullable Read readNewest(Path dir) throws IOException {
        Path yml = newest(dir);
        if (yml == null) {
            return null;
        }
        Path nbt = sibling(yml, ".nbt");
        return new Read(yml, Files.readString(yml, StandardCharsets.UTF_8),
                Files.isRegularFile(nbt) ? Files.readAllBytes(nbt) : null);
    }

    static Path sibling(Path yml, String ext) {
        String n = yml.getFileName().toString();
        return yml.resolveSibling(n.substring(0, n.length() - ".yml".length()) + ext);
    }

    static void write(Path yml, String text, byte[] nbt) throws IOException {
        Files.createDirectories(yml.getParent());
        Files.write(sibling(yml, ".nbt"), nbt);
        Files.writeString(yml, text, StandardCharsets.UTF_8);
    }

    static void delete(Path yml) throws IOException {
        Files.deleteIfExists(sibling(yml, ".nbt"));
        Files.deleteIfExists(yml);
    }

    static void deleteQuietly(Logger log, Path yml) {
        try {
            delete(yml);
        } catch (IOException e) {
            log.log(Level.WARNING, "Could not delete the backup " + yml, e);
        }
    }

    static String toYaml(Map<String, Object> map) {
        YamlConfiguration yaml = new YamlConfiguration();
        map.forEach(yaml::set);
        return yaml.saveToString();
    }

    private static long stamp(Path p) {
        String n = p.getFileName().toString();
        return Long.parseLong(n.substring(0, n.length() - ".yml".length()));
    }
}
