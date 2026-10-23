package dev.casperrs.cookieClicker.log;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

class WarnOnceTest {

    private static Logger capturing(List<String> out) {
        Logger log = Logger.getAnonymousLogger();
        log.setUseParentHandlers(false);
        log.addHandler(new Handler() {
            @Override
            public void publish(LogRecord r) {
                out.add(r.getMessage());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
        return log;
    }

    @Test
    void warnsOncePerKeyEvenWhenManyThreadsRaceAndAgainAfterClear() throws Exception {
        List<String> out = new CopyOnWriteArrayList<>();
        WarnOnce warn = new WarnOnce(capturing(out));
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch go = new CountDownLatch(1);
        for (int i = 0; i < 64; i++) {
            pool.execute(() -> {
                try {
                    go.await();
                } catch (InterruptedException e) {
                    return;
                }
                warn.warn("same", "boom");
            });
        }
        go.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        assertEquals(1, out.size());
        assertTrue(warn.warn("other", "x", new RuntimeException()));
        warn.clear();
        assertTrue(warn.warn("same", "boom"), "a cleared cause may warn again");
    }
}
