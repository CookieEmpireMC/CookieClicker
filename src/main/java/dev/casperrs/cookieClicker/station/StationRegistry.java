package dev.casperrs.cookieClicker.station;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * All bakery stations, with add/remove listeners. Pure Java; main thread only.
 * Listener exceptions are logged and don't stop other listeners or the registry change.
 */
public final class StationRegistry {

    private static final String ID_PREFIX = "station-";

    private final Map<String, Station> stations = new LinkedHashMap<>();
    private final List<StationListener> listeners = new CopyOnWriteArrayList<>();
    private final Logger log;

    public StationRegistry(Logger log) {
        this.log = log;
    }

    /** Adds the listener and immediately tells it about every existing station. */
    public void addListener(StationListener listener) {
        listeners.add(listener);
        for (Station s : List.copyOf(stations.values())) {
            notify(listener, s, true);
        }
    }

    public void removeListener(StationListener listener) {
        listeners.remove(listener);
    }

    public Collection<Station> all() {
        return Collections.unmodifiableCollection(stations.values());
    }

    public Optional<Station> get(String id) {
        return Optional.ofNullable(stations.get(id));
    }

    /** Adds or replaces (same id: remove event for the old one first). */
    public void add(Station station) {
        Station old = stations.put(station.id(), station);
        if (old != null) {
            fire(old, false);
        }
        fire(station, true);
    }

    public Optional<Station> remove(String id) {
        Station old = stations.remove(id);
        if (old != null) {
            fire(old, false);
        }
        return Optional.ofNullable(old);
    }

    /** Removes every station, firing remove events (plugin disable). */
    public void clear() {
        for (Station s : new ArrayList<>(stations.values())) {
            remove(s.id());
        }
    }

    /**
     * Reload: makes the registry equal {@code fresh}. Unchanged stations stay (no events), changed ones are
     * removed and re-added, missing ones removed, new ones added.
     */
    public void replaceAll(Collection<Station> fresh) {
        Map<String, Station> next = new LinkedHashMap<>();
        fresh.forEach(s -> next.put(s.id(), s));
        for (Station s : new ArrayList<>(stations.values())) {
            if (!s.equals(next.get(s.id()))) {
                remove(s.id());
            }
        }
        for (Station s : next.values()) {
            if (!stations.containsKey(s.id())) {
                add(s);
            }
        }
    }

    /** Nearest station in {@code worldKey} within {@code maxDistance} blocks. */
    public Optional<Station> nearest(String worldKey, double x, double y, double z, double maxDistance) {
        Station best = null;
        double bestSq = maxDistance * maxDistance;
        for (Station s : stations.values()) {
            double d = s.distanceSquared(worldKey, x, y, z);
            if (d <= bestSq) {
                best = s;
                bestSq = d;
            }
        }
        return Optional.ofNullable(best);
    }

    /** {@code station-<n>} with n one above the highest numbered id in use. */
    public String nextId() {
        int max = 0;
        for (String id : stations.keySet()) {
            if (id.startsWith(ID_PREFIX)) {
                try {
                    max = Math.max(max, Integer.parseInt(id.substring(ID_PREFIX.length())));
                } catch (NumberFormatException ignored) {
                    // custom id, not numbered
                }
            }
        }
        return ID_PREFIX + (max + 1);
    }

    private void fire(Station station, boolean added) {
        for (StationListener l : listeners) {
            notify(l, station, added);
        }
    }

    private void notify(StationListener l, Station station, boolean added) {
        try {
            if (added) {
                l.onStationAdded(station);
            } else {
                l.onStationRemoved(station);
            }
        } catch (RuntimeException e) {
            log.log(Level.SEVERE, "Station listener failed for " + station.id(), e);
        }
    }
}
