package dev.casperrs.cookieClicker.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.casperrs.cookieClicker.economy.CookiePouch;
import dev.casperrs.cookieClicker.economy.PouchSnapshot;
import dev.casperrs.cookieClicker.storage.LoadedPouch;
import dev.casperrs.cookieClicker.storage.PlayerStore;
import dev.casperrs.cookieClicker.storage.StorageException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SessionManagerTest {

    /** Records every call in order; can block or fail on demand. Touched only by the io thread (and the test after a flush). */
    static final class FakeStore implements PlayerStore {
        final Map<UUID, PouchSnapshot> data = new HashMap<>();
        final List<String> calls = Collections.synchronizedList(new ArrayList<>());
        volatile boolean failInit;
        volatile boolean failLoad;
        volatile boolean failSave;
        volatile CountDownLatch loadGate;
        volatile boolean closed;

        @Override
        public void init() throws StorageException {
            calls.add("init");
            if (failInit) {
                throw new StorageException("init failed");
            }
        }

        @Override
        public Optional<LoadedPouch> load(UUID player) throws StorageException {
            calls.add("load");
            CountDownLatch gate = loadGate;
            if (gate != null) {
                try {
                    gate.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            if (failLoad) {
                throw new StorageException("load failed");
            }
            return Optional.ofNullable(data.get(player)).map(s -> new LoadedPouch(s, List.of()));
        }

        @Override
        public void save(UUID player, PouchSnapshot snapshot) throws StorageException {
            calls.add("save");
            if (failSave) {
                throw new StorageException("save failed");
            }
            data.put(player, snapshot);
        }

        @Override
        public void close() {
            calls.add("close");
            closed = true;
        }
    }

    private final Queue<Runnable> mainQueue = new ConcurrentLinkedQueue<>();
    private final FakeStore store = new FakeStore();
    private final List<String> events = Collections.synchronizedList(new ArrayList<>());
    private final UUID alice = UUID.randomUUID();
    private ExecutorService io;
    private SessionManager sessions;

    @BeforeEach
    void setUp() {
        io = Executors.newSingleThreadExecutor();
        Logger quiet = Logger.getAnonymousLogger();
        quiet.setLevel(Level.OFF);
        sessions = new SessionManager(store, io, mainQueue::add, quiet);
        sessions.addListener(new SessionListener() {
            @Override
            public void onLoaded(UUID player, CookiePouch pouch) {
                events.add("loaded");
            }

            @Override
            public void onLoadFailed(UUID player) {
                events.add("failed");
            }

            @Override
            public void onUnloaded(UUID player) {
                events.add("unloaded");
            }
        });
        sessions.start();
    }

    @AfterEach
    void tearDown() {
        io.shutdownNow();
    }

    /** Waits for the io thread to go idle, then runs queued main-thread callbacks (like a server tick). */
    private void settle() throws Exception {
        io.submit(() -> { }).get(5, TimeUnit.SECONDS);
        Runnable r;
        while ((r = mainQueue.poll()) != null) {
            r.run();
        }
    }

    private long count(String call) {
        synchronized (store.calls) {
            return store.calls.stream().filter(call::equals).count();
        }
    }

    @Test
    void joinLoadsSavedProgress() throws Exception {
        store.data.put(alice, new PouchSnapshot(42, 100, Map.of("baker", 3), Set.of("u")));
        sessions.join(alice);
        assertFalse(sessions.isLoaded(alice), "nothing happens until the load comes back to main");
        settle();
        CookiePouch p = sessions.get(alice).orElseThrow();
        assertEquals(42, p.pouch());
        assertEquals(3, p.owned("baker"));
        assertEquals(List.of("loaded"), events);
    }

    @Test
    void joinWithoutSaveStartsFresh() throws Exception {
        sessions.join(alice);
        settle();
        assertEquals(0, sessions.get(alice).orElseThrow().pouch());
        assertEquals(Set.of(alice), Set.copyOf(sessions.loadedPlayers()));
    }

    @Test
    void quitBeforeLoadCompletesCreatesNoSessionAndNoSave() throws Exception {
        CountDownLatch gate = new CountDownLatch(1);
        store.loadGate = gate;
        sessions.join(alice);
        sessions.quit(alice);
        gate.countDown();
        settle();
        assertFalse(sessions.isLoaded(alice));
        assertEquals(0, count("save"));
        assertTrue(events.isEmpty(), "no loaded / unloaded events for a ghost session: " + events);
    }

    @Test
    void quitThenRejoinWhileFirstLoadInFlightUsesTheSecondLoad() throws Exception {
        CountDownLatch gate = new CountDownLatch(1);
        store.loadGate = gate;
        sessions.join(alice);
        sessions.quit(alice);
        sessions.join(alice);
        gate.countDown();
        settle();
        assertEquals(2, count("load"));
        assertEquals(List.of("loaded"), events, "exactly one session announced");
        assertTrue(sessions.isLoaded(alice));
    }

    @Test
    void failedLoadCreatesNoSessionAndNeverSaves() throws Exception {
        store.failLoad = true;
        sessions.join(alice);
        settle();
        assertFalse(sessions.isLoaded(alice));
        assertEquals(List.of("failed"), events);
        sessions.quit(alice);
        sessions.saveAll();
        settle();
        assertEquals(0, count("save"), "a broken read must never overwrite the real save");
    }

    @Test
    void failedLoadCanBeRetriedOnNextJoin() throws Exception {
        store.failLoad = true;
        sessions.join(alice);
        settle();
        store.failLoad = false;
        sessions.join(alice);
        settle();
        assertTrue(sessions.isLoaded(alice));
    }

    @Test
    void quitSavesTheSnapshotAndRemovesTheSession() throws Exception {
        store.data.put(alice, new PouchSnapshot(1, 1, Map.of(), Set.of()));
        sessions.join(alice);
        settle();
        sessions.quit(alice);
        assertFalse(sessions.isLoaded(alice));
        settle();
        assertEquals(1, count("save"));
        assertEquals(List.of("loaded", "unloaded"), events);
        sessions.quit(alice); // second quit is a no-op
        settle();
        assertEquals(1, count("save"));
    }

    @Test
    void fastRejoinSeesTheQuitSave() throws Exception {
        sessions.join(alice);
        settle();
        sessions.quit(alice);
        sessions.join(alice);
        settle();
        List<String> order;
        synchronized (store.calls) {
            order = List.copyOf(store.calls);
        }
        int firstSave = order.indexOf("save");
        int secondLoad = order.lastIndexOf("load");
        assertTrue(firstSave >= 0 && firstSave < secondLoad, "save must be queued before the rejoin load: " + order);
        assertTrue(sessions.isLoaded(alice));
    }

    @Test
    void saveFailureDoesNotKillTheIoThread() throws Exception {
        sessions.join(alice);
        settle();
        store.failSave = true;
        sessions.saveAll();
        settle();
        store.failSave = false;
        sessions.saveAll();
        settle();
        assertEquals(2, count("save"));
        assertTrue(store.data.containsKey(alice));
    }

    @Test
    void shutdownFlushesSavesThenClosesTheStoreAndStopsIo() throws Exception {
        UUID bob = UUID.randomUUID();
        sessions.join(alice);
        sessions.join(bob);
        settle();
        sessions.shutdown(5);
        assertTrue(store.data.containsKey(alice));
        assertTrue(store.data.containsKey(bob));
        assertTrue(store.closed);
        assertTrue(io.isTerminated());
        List<String> order;
        synchronized (store.calls) {
            order = List.copyOf(store.calls);
        }
        assertEquals("close", order.get(order.size() - 1), "close comes after every save: " + order);
        assertFalse(sessions.isLoaded(alice));
    }

    @Test
    void shutdownWithPendingLoadDoesNotCreateSessionsLater() throws Exception {
        CountDownLatch gate = new CountDownLatch(1);
        store.loadGate = gate;
        sessions.join(alice);
        gate.countDown();
        sessions.shutdown(5);
        // the load's callback may still be queued; the plugin's executor drops it after disable, but even
        // if it runs the manager must not announce a session twice or throw
        Runnable r;
        while ((r = mainQueue.poll()) != null) {
            r.run();
        }
        assertEquals(0, count("save"), "a player whose load never reached main had nothing to save");
    }

    @Test
    void saveAfterShutdownIsDroppedWithoutThrowing() throws Exception {
        sessions.join(alice);
        settle();
        sessions.shutdown(5);
        sessions.saveAll();
        sessions.quit(alice);
        assertEquals(1, count("save"));
    }

}
