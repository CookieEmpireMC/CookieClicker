package dev.casperrs.cookieClicker.station;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StationFileTest {

    /**
     * Minimal plugin. {@code JavaPlugin} can't be constructed without a server, so the instance is allocated
     * without running any constructor and only the one field {@code StationFile} reads is set (test-only).
     */
    static final class TestPlugin extends JavaPlugin {
        static TestPlugin create(File dataFolder) throws Exception {
            var unsafeField = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
            TestPlugin plugin = (TestPlugin) unsafe.allocateInstance(TestPlugin.class);
            var field = JavaPlugin.class.getDeclaredField("dataFolder");
            field.setAccessible(true);
            field.set(plugin, dataFolder);
            return plugin;
        }

        @Override
        public Logger getLogger() {
            Logger l = Logger.getAnonymousLogger();
            l.setLevel(Level.OFF);
            return l;
        }

        @Override
        public java.io.InputStream getResource(String filename) {
            return null; // no jar defaults
        }
    }

    private static final Station A = new Station("station-1", "minecraft:overworld", 1.5, 64, 2.5, 90f);
    private static final Station B = new Station("station-2", "minecraft:the_nether", -3.5, 70, 0.5, 180f);

    @Test
    void loadDuringPendingWriteReturnsTheNewestStationsNotTheOldFile(@TempDir Path dir) throws Exception {
        StationFile file = new StationFile(TestPlugin.create(dir.toFile()), blockedExecutor(dir));
        CountDownLatch release = BLOCK;
        file.saveAsync(List.of(A));
        // the write is queued behind the blocker: stations.yml does not exist yet
        assertTrue(Files.notExists(dir.resolve(StationFile.NAME)));
        assertEquals(List.of(A), file.load());
        file.saveAsync(List.of(A, B));
        assertEquals(List.of(A, B), file.load());
        release.countDown();
        drain();
        assertEquals(List.of(A, B), file.load(), "after the writes finish, load reads the file");
        assertTrue(Files.notExists(dir.resolve(StationFile.NAME + ".tmp")));
    }

    @Test
    void queueingAfterShutdownDoesNotLeaveLoadStuckOnMemory(@TempDir Path dir) throws Exception {
        ExecutorService stopped = Executors.newSingleThreadExecutor();
        stopped.shutdown();
        StationFile file = new StationFile(TestPlugin.create(dir.toFile()), stopped);
        file.saveAsync(List.of(A)); // rejected: logged, counter restored
        assertEquals(List.of(), file.load(), "no file was written and nothing is pending");
    }

    @Test
    void roundTripsThroughTheFile(@TempDir Path dir) throws Exception {
        ExecutorService io = Executors.newSingleThreadExecutor();
        StationFile file = new StationFile(TestPlugin.create(dir.toFile()), io);
        file.saveAsync(List.of(A, B));
        io.shutdown();
        assertTrue(io.awaitTermination(5, TimeUnit.SECONDS));
        assertEquals(List.of(A, B), file.load());
    }

    // ---- helpers: a single IO thread whose first task blocks until released ----

    private static CountDownLatch BLOCK;
    private static ExecutorService io;

    private static ExecutorService blockedExecutor(Path dir) {
        BLOCK = new CountDownLatch(1);
        io = Executors.newSingleThreadExecutor();
        io.execute(() -> {
            try {
                BLOCK.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });
        return io;
    }

    private static void drain() throws Exception {
        io.shutdown();
        assertTrue(io.awaitTermination(5, TimeUnit.SECONDS));
    }
}
