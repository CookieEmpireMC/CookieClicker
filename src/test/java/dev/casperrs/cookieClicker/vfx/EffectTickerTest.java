package dev.casperrs.cookieClicker.vfx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;
import java.util.logging.Logger;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;

/** The shared ticker without a server: the scheduler is a proxy that hands us the repeating Runnable. */
class EffectTickerTest {

    private Runnable repeating;
    private boolean taskCancelled;
    private int tasksStarted;

    private EffectTicker ticker() {
        BukkitTask task = (BukkitTask) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {BukkitTask.class}, (p, m, a) -> {
                    if (m.getName().equals("cancel")) {
                        taskCancelled = true;
                        repeating = null;
                    }
                    return null;
                });
        BukkitScheduler scheduler = (BukkitScheduler) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {BukkitScheduler.class}, (p, m, a) -> {
                    if (m.getName().equals("runTaskTimer")) {
                        repeating = (Runnable) a[1];
                        taskCancelled = false;
                        tasksStarted++;
                        return task;
                    }
                    return null;
                });
        Server server = (Server) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {Server.class}, (p, m, a) -> m.getName().equals("getScheduler") ? scheduler : null);
        Plugin plugin = (Plugin) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Plugin.class},
                (p, m, a) -> switch (m.getName()) {
                    case "getServer" -> server;
                    case "getLogger" -> Logger.getAnonymousLogger();
                    default -> null;
                });
        return new EffectTicker(plugin);
    }

    /** A job that stays alive while {@code alive.test(age)} and counts its steps. */
    private static final class Counting extends Job {
        final IntPredicate alive;
        final List<Integer> ages = new ArrayList<>();
        int cleanups;
        Runnable onTick = () -> { };

        Counting(IntPredicate alive) {
            this.alive = alive;
        }

        @Override
        protected boolean tick(int age) {
            ages.add(age);
            onTick.run();
            return alive.test(age);
        }

        @Override
        protected void cleanup() {
            cleanups++;
        }
    }

    @Test
    void jobStartedInsideACallbackIsSteppedOncePerTickNotTwice() {
        EffectTicker ticker = ticker();
        Counting child = new Counting(age -> age < 3);
        Counting parent = new Counting(age -> age < 1);
        parent.onTick = () -> {
            if (parent.ages.size() == 2) {
                ticker.start(child); // started from inside run()
            }
        };
        ticker.start(parent); // age 0
        Runnable run = repeating;
        assertNotNull(run);
        run.run(); // parent age 1 -> starts child (age 0 immediately), child must NOT be stepped again this tick
        assertEquals(List.of(0), child.ages);
        run.run();
        assertEquals(List.of(0, 1), child.ages);
        run.run();
        run.run();
        assertEquals(List.of(0, 1, 2, 3), child.ages); // ends at age 3
    }

    @Test
    void taskStopsWhenTheLastJobEndsAndRestartsOnTheNextOne() {
        EffectTicker ticker = ticker();
        ticker.start(new Counting(age -> age < 1));
        repeating.run();
        assertTrue(taskCancelled);
        assertNull(repeating);
        ticker.start(new Counting(age -> age < 1));
        assertNotNull(repeating);
        assertEquals(2, tasksStarted);
    }

    @Test
    void endingJobsMidListNeverSkipsTheOthers() {
        EffectTicker ticker = ticker();
        List<Counting> jobs = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            int dies = i % 2 == 0 ? 1 : 5; // 0,2,4 end on tick 1
            Counting c = new Counting(age -> age < dies);
            jobs.add(c);
            ticker.start(c);
        }
        for (int t = 0; t < 4; t++) {
            repeating.run();
        }
        for (int i = 1; i < 6; i += 2) {
            assertEquals(5, jobs.get(i).ages.size(), "long job " + i + " stepped every tick");
        }
        for (int i = 0; i < 6; i += 2) {
            assertEquals(2, jobs.get(i).ages.size(), "short job " + i + " stopped after ending");
        }
    }

    @Test
    void cancelAllFromInsideACallbackCleansEverythingAndStopsTheTask() {
        EffectTicker ticker = ticker();
        Counting a = new Counting(age -> true);
        Counting b = new Counting(age -> true);
        Counting killer = new Counting(age -> true);
        killer.onTick = () -> {
            if (killer.ages.size() == 2) {
                ticker.cancelAll();
            }
        };
        ticker.start(a);
        ticker.start(killer);
        ticker.start(b);
        repeating.run(); // killer's second step cancels all
        assertEquals(1, a.cleanups);
        assertEquals(1, b.cleanups);
        assertEquals(1, killer.cleanups);
        assertTrue(taskCancelled);
    }

    @Test
    void jobStartedInsideACallbackThenCancelAllIsCancelledToo() {
        EffectTicker ticker = ticker();
        Counting child = new Counting(age -> true);
        Counting parent = new Counting(age -> true);
        parent.onTick = () -> {
            if (parent.ages.size() == 2) {
                ticker.start(child);
            }
        };
        ticker.start(parent);
        repeating.run();
        ticker.cancelAll();
        assertEquals(1, child.cleanups);
        assertEquals(1, parent.cleanups);
    }
}
