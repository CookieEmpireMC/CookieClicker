package dev.casperrs.cookieClicker.plot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlotFileTest {

    private static final Plot A = new Plot("main", "minecraft:overworld", "pr", "hr", new Plot.Viewpoint(1, 2, 3, 90f, 5f),
            new Plot.Pedestal(4, 5, 6, 180f), new Plot.Point(7, 8, 9));
    private static final Plot B = Plot.empty("two", "minecraft:the_nether");

    private static Logger quiet() {
        Logger l = Logger.getAnonymousLogger();
        l.setLevel(Level.OFF);
        return l;
    }

    /** Runs queued tasks only when told to (a stand-in for a busy IO thread). */
    private static final class ManualExecutor implements Executor {
        final Queue<Runnable> queue = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            queue.add(command);
        }

        void runAll() {
            while (!queue.isEmpty()) {
                queue.poll().run();
            }
        }
    }

    @Test
    void savesAndLoadsBack(@TempDir Path dir) throws Exception {
        PlotFile file = new PlotFile(dir, quiet(), Runnable::run);
        assertFalse(file.exists());
        file.saveAsync(List.of(A, B));
        assertTrue(file.exists());
        List<String> problems = new ArrayList<>();
        assertEquals(List.of(A, B), file.load(problems::add));
        assertTrue(problems.isEmpty());
        assertTrue(Files.notExists(dir.resolve("plots.yml.tmp")), "temp file must be moved away");
        assertTrue(Files.readString(dir.resolve("plots.yml")).contains("/rg flag"), "header explains the flags");
    }

    @Test
    void loadDuringAPendingWriteReturnsTheNewestPlotsNotTheOldFile(@TempDir Path dir) throws Exception {
        ManualExecutor io = new ManualExecutor();
        PlotFile file = new PlotFile(dir, quiet(), io);
        file.saveAsync(List.of(A));
        assertTrue(file.exists(), "a queued write counts as existing, so migration can't run twice");
        assertTrue(Files.notExists(dir.resolve("plots.yml")));
        assertEquals(List.of(A), file.load(p -> { }));
        file.saveAsync(List.of(A, B));
        assertEquals(List.of(A, B), file.load(p -> { }));
        io.runAll();
        assertEquals(List.of(A, B), file.load(p -> { }));
    }

    @Test
    void afterWriteRunsOnlyAfterTheFileExists(@TempDir Path dir) {
        AtomicInteger ran = new AtomicInteger();
        Path target = dir.resolve("plots.yml");
        boolean[] existedWhenRun = new boolean[1];
        PlotFile file = new PlotFile(dir, quiet(), Runnable::run);
        file.saveAsync(List.of(A), () -> {
            ran.incrementAndGet();
            existedWhenRun[0] = Files.isRegularFile(target);
        });
        assertEquals(1, ran.get());
        assertTrue(existedWhenRun[0]);
    }

    @Test
    void afterWriteIsSkippedWhenTheWriteFails(@TempDir Path dir) throws Exception {
        // the data folder "is" a file, so createDirectories/writeString fails
        Path notADir = dir.resolve("blocker");
        Files.writeString(notADir, "x");
        PlotFile file = new PlotFile(notADir, quiet(), Runnable::run);
        AtomicInteger ran = new AtomicInteger();
        file.saveAsync(List.of(A), ran::incrementAndGet);
        assertEquals(0, ran.get(), "stations.yml must not be renamed when plots.yml was not written");
    }

    @Test
    void queueingAfterShutdownDoesNotLeaveLoadStuckOnMemory(@TempDir Path dir) throws Exception {
        ExecutorService stopped = Executors.newSingleThreadExecutor();
        stopped.shutdown();
        PlotFile file = new PlotFile(dir, quiet(), stopped);
        file.saveAsync(List.of(A));
        assertFalse(file.exists(), "pending counter restored after a rejected write");
        assertTrue(file.load(p -> { }).isEmpty());
    }

    @Test
    void badYamlThrowsSoTheCallerKeepsTheCurrentPlots(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("plots.yml"), "plots: [unclosed\n  - : :");
        PlotFile file = new PlotFile(dir, quiet(), Runnable::run);
        org.junit.jupiter.api.Assertions.assertThrows(dev.casperrs.cookieClicker.config.YamlFiles.BadYamlException.class,
                () -> file.load(p -> { }));
    }
}
