package dev.casperrs.cookieClicker;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Source-scan checks of the project rules (docs/architecture.md). */
class ArchitectureRulesTest {

    private static final Path MAIN = Path.of("src/main/java");

    private static List<Path> sources() throws IOException {
        try (Stream<Path> s = Files.walk(MAIN)) {
            return s.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }

    private static List<String> hits(String needle, String allowedPathPart) throws IOException {
        List<String> out = new ArrayList<>();
        for (Path p : sources()) {
            if (allowedPathPart != null && p.toString().contains(allowedPathPart)) {
                continue;
            }
            List<String> lines = Files.readAllLines(p);
            for (int i = 0; i < lines.size(); i++) {
                String t = lines.get(i).trim();
                if (t.startsWith("*") || t.startsWith("//") || t.startsWith("/*")) {
                    continue;
                }
                if (lines.get(i).contains(needle)) {
                    out.add(p.getFileName() + ":" + (i + 1));
                }
            }
        }
        return out;
    }

    @Test
    void playSoundOnlyInTheSoundService() throws IOException {
        assertTrue(hits("playSound(", "/sound/").isEmpty(), hits("playSound(", "/sound/").toString());
    }

    @Test
    void spawnParticleOnlyInVfx() throws IOException {
        assertTrue(hits("spawnParticle(", "/vfx/").isEmpty());
    }

    @Test
    void noLegacyTextNoNmsNoReflectionIntoServer() throws IOException {
        for (String bad : new String[] {"ChatColor", "§", "net.minecraft.", "org.bukkit.craftbukkit", "CraftPlayer"}) {
            assertTrue(hits(bad, null).isEmpty(), bad + " at " + hits(bad, null));
        }
        assertTrue(hits("Class.forName(", "DriverConnectionFactory").isEmpty(), "reflection only to load the JDBC driver");
    }

    @Test
    void miniMessageIsNeverShadedOrBuiltByConcatenationOfPlayerData() throws IOException {
        // Placeholder.parsed is only allowed for trusted config values (the prefix)
        List<String> parsed = hits("Placeholder.parsed(", null);
        assertTrue(parsed.size() <= 1, "Placeholder.parsed must only be used for the trusted prefix: " + parsed);
    }

    @Test
    void noBlockingFileOrSqlCallsInTheSchedulerDrivenClasses() throws IOException {
        for (String cls : new String[] {"BakeryTicker", "Hud", "SoundService", "StationRegistry"}) {
            for (Path p : sources()) {
                if (p.getFileName().toString().equals(cls + ".java")) {
                    String text = Files.readString(p);
                    assertTrue(!text.contains("java.sql") && !text.contains("Files.") && !text.contains("Thread.sleep"), cls + " does blocking work");
                }
            }
        }
    }
}
