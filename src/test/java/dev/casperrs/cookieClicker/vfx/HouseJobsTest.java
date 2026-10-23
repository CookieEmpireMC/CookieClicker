package dev.casperrs.cookieClicker.vfx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.casperrs.cookieClicker.vfx.shape.DisplayTrack;
import dev.casperrs.cookieClicker.vfx.shape.DropTrack;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.junit.jupiter.api.Test;

/**
 * Callback and cancel semantics of falling blocks and loops, and the per-viewer caps, without a server: the jobs
 * are stepped by hand (one {@code step()} = one tick) against a fake display.
 */
class HouseJobsTest {

    private static final DropTrack TRACK = DropTrack.of(DropTrack.Params.DEFAULTS);

    /** A fake display that records what happens to it into a shared event log. */
    private static final class FakeBody implements DropJob.Body {
        final List<String> log;
        boolean valid = true;

        FakeBody(List<String> log) {
            this.log = log;
        }

        @Override
        public boolean valid() {
            return valid;
        }

        @Override
        public void send(DropTrack.Keyframe k) {
            log.add("send@" + k.tick());
        }

        @Override
        public void remove() {
            if (valid) {
                valid = false;
                log.add("remove");
            }
        }
    }

    private final List<String> log = new ArrayList<>();
    private final FakeBody body = new FakeBody(log);
    private final AtomicBoolean viewerHere = new AtomicBoolean(true);
    private final AtomicInteger ends = new AtomicInteger();

    private DropJob drop(Runnable onImpact) {
        return new DropJob(TRACK, body, viewerHere::get, onImpact, () -> log.add("landed"),
                j -> ends.incrementAndGet());
    }

    private DropJob drop() {
        return drop(() -> log.add("impact"));
    }

    /** Steps until the job ends or {@code maxTicks} pass; returns the number of steps that returned true. */
    private static int run(Job job, int maxTicks) {
        int alive = 0;
        for (int t = 0; t < maxTicks; t++) {
            if (!job.step()) {
                break;
            }
            alive++;
        }
        return alive;
    }

    @Test
    void normalDropSendsThreeSegmentsThenImpactThenRemoveThenLanded() {
        DropJob job = drop();
        int alive = run(job, 100);
        assertEquals(TRACK.landTick(), alive, "alive for ticks 0..landTick-1, lands on landTick");
        assertEquals(List.of("send@2", "impact", "send@8", "send@10", "remove", "landed"), log);
        assertTrue(job.landed());
        assertEquals(1, ends.get());
        assertFalse(job.step(), "a landed drop stays ended");
        assertEquals(6, log.size(), "nothing runs twice");
    }

    @Test
    void cancelMidFallRemovesTheDisplayAndNeverCallsBack() {
        DropJob job = drop();
        run(job, 5);
        job.cancel();
        job.cancel();
        assertFalse(job.step());
        assertEquals(List.of("send@2", "remove"), log);
        assertFalse(job.impacted());
        assertFalse(job.landed());
        assertEquals(1, ends.get());
    }

    @Test
    void displayVanishingWhileTheViewerIsThereLandsNow() {
        DropJob job = drop();
        run(job, 4);
        body.valid = false; // chunk unloaded or removed by someone else
        assertFalse(job.step());
        assertEquals(List.of("send@2", "impact", "landed"), log, "the reveal never stalls");
        assertTrue(job.landed());
        assertEquals(1, ends.get());
    }

    @Test
    void viewerLeavingEndsTheDropWithoutCallbacks() {
        DropJob job = drop();
        run(job, 4);
        viewerHere.set(false);
        assertFalse(job.step());
        assertEquals(List.of("send@2", "remove"), log);
        assertEquals(1, ends.get());
    }

    @Test
    void displayIsReleasedBeforeOnLandedRuns() {
        List<Integer> slotsAtLanding = new ArrayList<>();
        ViewerJobs<DropJob> drops = new ViewerJobs<>();
        UUID viewer = UUID.randomUUID();
        DropJob[] self = new DropJob[1];
        self[0] = new DropJob(TRACK, body, () -> true, () -> { }, () -> slotsAtLanding.add(drops.count(viewer)),
                j -> drops.remove(viewer, j));
        drops.add(viewer, self[0]);
        run(self[0], 100);
        assertEquals(List.of(0), slotsAtLanding, "the cap slot is free when onLanded runs (it may drop the next)");
    }

    @Test
    void loopPlaysOnItsIntervalUntilCancelled() {
        AtomicInteger plays = new AtomicInteger();
        LoopJob loop = new LoopJob(10, 0, () -> true, plays::incrementAndGet, j -> ends.incrementAndGet());
        assertTrue(loop.isActive());
        run(loop, 25);
        assertEquals(3, plays.get(), "ticks 0, 10 and 20");
        loop.cancel();
        loop.cancel();
        assertFalse(loop.isActive());
        assertFalse(loop.step());
        assertEquals(3, plays.get());
        assertEquals(1, ends.get());
    }

    @Test
    void loopChecksTheViewerOnlyOnItsPlayTicks() {
        AtomicBoolean valid = new AtomicBoolean(true);
        AtomicInteger checks = new AtomicInteger();
        AtomicInteger plays = new AtomicInteger();
        LoopJob loop = new LoopJob(10, 0, () -> {
            checks.incrementAndGet();
            return valid.get();
        }, plays::incrementAndGet, j -> ends.incrementAndGet());
        run(loop, 25);
        assertEquals(3, checks.get(), "checked on ticks 0, 10 and 20 only");
        valid.set(false);
        assertEquals(5, run(loop, 10), "ticks 25..29 are quiet");
        assertFalse(loop.isActive(), "ends on the play tick 30, before playing");
        assertEquals(3, plays.get(), "no play for a viewer who left");
        assertEquals(4, checks.get());
        assertEquals(1, ends.get());
    }

    @Test
    void keyframesGoOutOnTheirTicksIncludingTwoOnOneTick() {
        int[] now = {0};
        List<Object> sent = new ArrayList<>();
        UUID id = UUID.randomUUID();
        Display display = (Display) Proxy.newProxyInstance(HouseJobsTest.class.getClassLoader(),
                new Class<?>[] {Display.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "isValid" -> true;
                    case "getUniqueId" -> id;
                    case "setTransformation" -> {
                        sent.add("@" + now[0] + ":" + (int) ((Transformation) args[0]).getTranslation().x);
                        yield null;
                    }
                    case "remove" -> {
                        sent.add("@" + now[0] + ":remove");
                        yield null;
                    }
                    default -> null;
                });
        DisplayTrack track = new DisplayTrack(key(0), List.of(key(0), key(2), key(2), key(5), key(9)), List.of(), 10);
        DisplayJob job = new DisplayJob(null, new DisplayRegistry(), null, null, 2) {
            @Override
            protected int entityCount() {
                return 1;
            }

            @Override
            protected void spawnAll(Location at, Player viewer) {
                add(display, track);
            }
        };
        for (; now[0] < 30 && job.step(); now[0]++) {
            // one step per tick
        }
        assertEquals(List.of("@2:0", "@4:2", "@4:2", "@7:5", "@11:9", "@12:remove"), sent,
                "each keyframe goes out on its own tick (after the delay), the duplicate tick too");
    }

    private static DisplayTrack.Keyframe key(int tick) {
        return new DisplayTrack.Keyframe(tick, tick, 0, 0, 1, 0, 0, 1, 0, 1);
    }

    @Test
    void loopStartDelayShiftsTheFirstPlay() {
        AtomicInteger plays = new AtomicInteger();
        LoopJob loop = new LoopJob(10, 4, () -> true, plays::incrementAndGet, j -> ends.incrementAndGet());
        run(loop, 4);
        assertEquals(0, plays.get(), "ticks 0..3 are still waiting");
        run(loop, 11);
        assertEquals(2, plays.get(), "ticks 4 and 14");
    }

    @Test
    void loopEndsWhenTheViewerIsGoneOrItCancelsItself() {
        AtomicBoolean valid = new AtomicBoolean(true);
        AtomicInteger plays = new AtomicInteger();
        LoopJob loop = new LoopJob(1, 0, valid::get, plays::incrementAndGet, j -> ends.incrementAndGet());
        run(loop, 3);
        valid.set(false);
        assertFalse(loop.step());
        assertFalse(loop.isActive());
        assertEquals(3, plays.get());

        LoopJob[] self = new LoopJob[1];
        self[0] = new LoopJob(1, 0, () -> true, () -> self[0].cancel(), j -> ends.incrementAndGet());
        assertFalse(self[0].step(), "cancel from inside a play ends it in the same tick");
        assertEquals(2, ends.get());
    }

    @Test
    void capsCountPerViewerAndGlobally() {
        ViewerJobs<LoopJob> jobs = new ViewerJobs<>();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        for (int i = 0; i < 3; i++) {
            assertTrue(jobs.hasRoom(a, 3, 5));
            LoopJob job = new LoopJob(1, 0, () -> true, () -> { }, j -> jobs.remove(a, j));
            jobs.add(a, job);
        }
        assertFalse(jobs.hasRoom(a, 3, 5), "per-viewer cap");
        assertTrue(jobs.hasRoom(b, 3, 5));
        for (int i = 0; i < 2; i++) {
            LoopJob job = new LoopJob(1, 0, () -> true, () -> { }, j -> jobs.remove(b, j));
            jobs.add(b, job);
        }
        assertFalse(jobs.hasRoom(b, 3, 5), "global cap");
        assertEquals(5, jobs.total());

        jobs.cancelViewer(a);
        assertEquals(0, jobs.count(a));
        assertEquals(2, jobs.total());
        assertTrue(jobs.hasRoom(a, 3, 5));
        jobs.cancelViewer(UUID.randomUUID()); // unknown viewer: no-op
        assertEquals(2, jobs.total());
    }

}
