package dev.casperrs.cookieClicker.sound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import java.util.random.RandomGenerator;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The shared ambient loop task keeps one beat per loop: no doubles, no missing beat, stops when idle. */
class LoopPlayerTest {

    private static final int PERIOD = 20;

    private final UUID id = UUID.randomUUID();
    private final List<Integer> played = new ArrayList<>();
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private int tick;
    private int scheduled;
    private Runnable shared;
    private LoopPlayer loops;
    private Player player;

    @BeforeEach
    void setUp() {
        player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[] {Player.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getUniqueId" -> {
                            return id;
                        }
                        case "playSound" -> {
                            played.add(tick);
                            return null;
                        }
                        case "hashCode" -> {
                            return id.hashCode();
                        }
                        case "equals" -> {
                            return proxy == args[0];
                        }
                        default -> throw new UnsupportedOperationException(method.getName());
                    }
                });
        SoundService sounds = new SoundService(Logger.getAnonymousLogger(), RandomGenerator.getDefault(), p -> false);
        sounds.load(Map.of("hum", new SoundDef("hum", "cookieclicker:hum", "minecraft:block.note_block.bass",
                1f, 1f, 1f, "master")));
        BukkitTask task = (BukkitTask) Proxy.newProxyInstance(BukkitTask.class.getClassLoader(),
                new Class<?>[] {BukkitTask.class}, (proxy, method, args) -> {
                    if (method.getName().equals("cancel")) {
                        cancelled.set(true);
                    }
                    return null;
                });
        loops = new LoopPlayer(sounds, () -> tick, r -> {
            scheduled++;
            shared = r;
            return task;
        });
    }

    private void run(int ticks) {
        for (int i = 0; i < ticks; i++) {
            tick++;
            shared.run();
        }
    }

    @Test
    void firstRunOneTickLateKeepsAFullPeriodBetweenBeats() {
        tick = 10;
        loops.start(player, "hum", new Location(null, 0, 0, 0), PERIOD, () -> true);
        run(1 + 2 * PERIOD);

        assertEquals(List.of(11, 11 + PERIOD, 11 + 2 * PERIOD), played,
                "the next beat is due one period after the tick it really played on, so the pacer never refuses it");
    }

    @Test
    void aBeatIsSkippedWhileThePlayerIsAwayAndResumesOnTheNextOne() {
        AtomicBoolean here = new AtomicBoolean(true);
        tick = 10;
        loops.start(player, "hum", new Location(null, 0, 0, 0), PERIOD, here::get);
        run(1);
        here.set(false);
        run(PERIOD);
        here.set(true);
        run(PERIOD);

        assertEquals(List.of(11, 11 + 2 * PERIOD), played);
    }

    @Test
    void stopsWhenTheLastLoopIsCancelledAndRestartsWithTheNext() {
        tick = 10;
        LoopPlayer.Loop loop = loops.start(player, "hum", new Location(null, 0, 0, 0), PERIOD, () -> true);
        run(1);
        loop.cancel();
        run(PERIOD);

        assertTrue(cancelled.get(), "the shared task cancels itself when no loop is left");
        assertEquals(List.of(11), played);

        loops.start(player, "hum", new Location(null, 0, 0, 0), PERIOD, () -> true);
        assertEquals(2, scheduled, "a new loop schedules the task again");
    }
}
