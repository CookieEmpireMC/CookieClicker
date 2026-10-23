package dev.casperrs.cookieClicker.log;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Logs a warning once per cause key until {@link #clear()} (call it on reload, so fixed causes can warn again). */
public final class WarnOnce {

    private final Logger log;
    private final Set<String> warned = ConcurrentHashMap.newKeySet();

    public WarnOnce(Logger log) {
        this.log = log;
    }

    /** True when this call logged (the first time for {@code key}). */
    public boolean warn(String key, String message) {
        if (!warned.add(key)) {
            return false;
        }
        log.warning(message);
        return true;
    }

    /** True when this call logged (the first time for {@code key}). */
    public boolean warn(String key, String message, Throwable error) {
        if (!warned.add(key)) {
            return false;
        }
        log.log(Level.WARNING, message, error);
        return true;
    }

    public void clear() {
        warned.clear();
    }
}
