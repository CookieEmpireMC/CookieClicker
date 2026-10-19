package dev.casperrs.cookieClicker.vfx;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Running jobs of one kind (falling blocks, loops) per viewer, for the caps and for {@link Effects#cancelFor}.
 * Jobs are added when they start and removed in their cleanup. Plain Java, unit-tested.
 */
final class ViewerJobs<J extends Job> {

    private final Map<UUID, Set<J>> byViewer = new HashMap<>();
    private int total;

    void add(UUID viewer, J job) {
        if (byViewer.computeIfAbsent(viewer, k -> new LinkedHashSet<>()).add(job)) {
            total++;
        }
    }

    /** Forgets a job. Idempotent. */
    void remove(UUID viewer, J job) {
        Set<J> set = byViewer.get(viewer);
        if (set != null && set.remove(job)) {
            total--;
            if (set.isEmpty()) {
                byViewer.remove(viewer);
            }
        }
    }

    int count(UUID viewer) {
        Set<J> set = byViewer.get(viewer);
        return set == null ? 0 : set.size();
    }

    int total() {
        return total;
    }

    /** True when one more job for this viewer stays within both caps. */
    boolean hasRoom(UUID viewer, int perViewer, int global) {
        return count(viewer) < perViewer && total < global;
    }

    /** Cancels every job of this viewer (each one's cleanup removes it here). */
    void cancelViewer(UUID viewer) {
        Set<J> set = byViewer.get(viewer);
        if (set != null) {
            for (J job : new ArrayList<>(set)) {
                job.cancel();
            }
        }
    }
}
