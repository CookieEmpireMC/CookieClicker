package dev.casperrs.cookieClicker.vfx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.casperrs.cookieClicker.vfx.shape.DropTrack;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
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
    void cancelAfterImpactNeverCallsOnLanded() {
        DropJob job = drop();
        run(job, TRACK.impactTick() + 1);
        assertTrue(job.impacted());
        job.cancel();
        run(job, 20);
        assertFalse(job.landed());
        assertFalse(log.contains("landed"));
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
    void displayVanishingWhileTheViewerIsThereLandsNow() {
        DropJob job = drop();
        run(job, 4);
        body.valid = false; // chunk unloaded / removed by someone else
        assertFalse(job.step());
        assertEquals(List.of("send@2", "impact", "landed"), log);
        assertTrue(job.landed());
        assertEquals(1, ends.get());
    }

    @Test
    void impactCallbackCancellingTheDropSkipsOnLanded() {
        DropJob[] self = new DropJob[1];
        self[0] = drop(() -> {
            log.add("impact");
            self[0].cancel(); // e.g. the caller runs effects.cancelFor(viewer) from onImpact
        });
        run(self[0], 100);
        assertTrue(self[0].impacted());
        assertFalse(self[0].landed());
        assertEquals(List.of("send@2", "impact", "remove"), log);
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
        LoopJob loop = new LoopJob(10, () -> true, plays::incrementAndGet, j -> ends.incrementAndGet());
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
    void loopEndsWhenTheViewerIsGoneOrItCancelsItself() {
        AtomicBoolean valid = new AtomicBoolean(true);
        AtomicInteger plays = new AtomicInteger();
        LoopJob loop = new LoopJob(1, valid::get, plays::incrementAndGet, j -> ends.incrementAndGet());
        run(loop, 3);
        valid.set(false);
        assertFalse(loop.step());
        assertFalse(loop.isActive());
        assertEquals(3, plays.get());

        LoopJob[] self = new LoopJob[1];
        self[0] = new LoopJob(1, () -> true, () -> self[0].cancel(), j -> ends.incrementAndGet());
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
            LoopJob job = new LoopJob(1, () -> true, () -> { }, j -> jobs.remove(a, j));
            jobs.add(a, job);
        }
        assertFalse(jobs.hasRoom(a, 3, 5), "per-viewer cap");
        assertTrue(jobs.hasRoom(b, 3, 5));
        for (int i = 0; i < 2; i++) {
            LoopJob job = new LoopJob(1, () -> true, () -> { }, j -> jobs.remove(b, j));
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

    @Test
    void cancelViewerOnDropsNeverLandsAnyOfThem() {
        ViewerJobs<DropJob> drops = new ViewerJobs<>();
        UUID viewer = UUID.randomUUID();
        AtomicInteger landed = new AtomicInteger();
        List<DropJob> all = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            DropJob job = new DropJob(TRACK, new FakeBody(new ArrayList<>()), () -> true, () -> { },
                    landed::incrementAndGet, j -> drops.remove(viewer, j));
            drops.add(viewer, job);
            all.add(job);
            run(job, 3 + i);
        }
        drops.cancelViewer(viewer);
        for (DropJob job : all) {
            assertFalse(job.step());
        }
        assertEquals(0, landed.get());
        assertEquals(0, drops.total());
    }
}
