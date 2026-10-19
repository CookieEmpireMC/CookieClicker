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

    @Test
    void worldGuardIsOnlyTouchedInTheWorldguardPackage() throws IOException {
        for (String bad : new String[] {"com.sk89q."}) {
            List<String> found = new ArrayList<>();
            for (Path p : sources()) {
                if (p.toString().contains("/worldguard/")) {
                    continue;
                }
                if (Files.readString(p).contains(bad)) {
                    found.add(p.getFileName().toString());
                }
            }
            assertTrue(found.isEmpty(), bad + " outside worldguard/: " + found);
        }
    }

    @Test
    void onlyTheHousePackageChangesBlocksInTheWorld() throws IOException {
        // starter-house: "the world is only ever modified by /bakery house place and /bakery house undo"
        for (String bad : new String[] {".setBlockData(", ".setType(", ".breakNaturally(", "setBiome(", "getStructureManager("}) {
            assertTrue(hits(bad, "/house/").isEmpty(), bad + " outside house/: " + hits(bad, "/house/"));
        }
    }

    @Test
    void fakeBlocksAreOnlySentFromFakeBlocks() throws IOException {
        // the cinematic's one place that talks fake blocks to a player; the world itself is never changed by it
        for (String needle : new String[] {"sendBlockChange(", "sendBlockChanges(", "sendMultiBlockChange(",
                "sendBlockUpdate("}) {
            List<String> found = new ArrayList<>(hits(needle, null));
            found.removeAll(hits(needle, null).stream().filter(h -> h.startsWith("FakeBlocks.java:")).toList());
            assertTrue(found.isEmpty(), needle + " outside house/FakeBlocks.java: " + found);
        }
        assertTrue(!hits("sendMultiBlockChange(", null).isEmpty(), "FakeBlocks should send the batches");
    }

    @Test
    void theWorldIsNeverModifiedByTheCinematicClasses() throws IOException {
        for (String cls : new String[] {"HouseCinematics", "FakeBlocks", "KeepOut", "ChimneySmoke", "RevealPlan",
                "RevealOrder", "HouseBox"}) {
            String text = Files.readString(MAIN.resolve("dev/casperrs/cookieClicker/house/" + cls + ".java"));
            for (String bad : new String[] {".setType(", ".setBlockData(", ".breakNaturally(", ".applyPhysics(",
                    "getState().update(", ".setBlock("}) {
                assertTrue(!text.contains(bad), cls + " modifies the world via " + bad);
            }
        }
    }

    @Test
    void noRegionQueryOnEveryMoveEventInTheTracker() throws IOException {
        String tracker = Files.readString(MAIN.resolve("dev/casperrs/cookieClicker/plot/PlotTracker.java"));
        int onMove = tracker.indexOf("public void onMove(");
        assertTrue(onMove > 0);
        int guard = tracker.indexOf("if (event.hasChangedBlock())", onMove);
        int update = tracker.indexOf("update(", onMove);
        assertTrue(guard > 0 && guard < update, "region checks must be guarded by hasChangedBlock()");
    }

    @Test
    void noRegionObjectsAreCachedOnlyIds() throws IOException {
        for (Path p : sources()) {
            if (p.toString().contains("/worldguard/")) {
                continue;
            }
            String text = Files.readString(p);
            assertTrue(!text.contains("ProtectedRegion") && !text.contains("RegionManager"), p.getFileName() + " touches WG region types");
        }
    }

    @Test
    void ioExecutorWorkIsKeptOffBukkitInThePlotAndHouseFiles() throws IOException {
        // file/DB classes must not call the Bukkit world from the io lambda; spot check: HouseSeen and PlotFile have no Bukkit imports
        for (String name : new String[] {"house/HouseSeen.java", "plot/PlotFile.java", "plot/PlotCodec.java", "plot/RegionShape.java",
                "plot/Outline.java", "house/HouseBackup.java", "house/HouseDesignParser.java", "plot/PlotResolver.java"}) {
            String text = Files.readString(MAIN.resolve("dev/casperrs/cookieClicker/" + name));
            assertTrue(!text.contains("import org.bukkit.entity") && !text.contains("import org.bukkit.Bukkit;")
                    && !text.contains("import org.bukkit.World;") && !text.contains("import org.bukkit.Location;"), name + " must stay pure");
        }
    }
}
