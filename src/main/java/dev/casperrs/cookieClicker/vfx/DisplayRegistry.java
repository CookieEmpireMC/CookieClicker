package dev.casperrs.cookieClicker.vfx;

import dev.casperrs.cookieClicker.vfx.config.EffectsConfig.Limits;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.Entity;
import org.jspecify.annotations.Nullable;

/**
 * Every display entity the engine has spawned and is still animating, by entity and by viewer, plus the caps.
 * When a viewer is at {@code max-display-entities-per-player}, their oldest display effect is removed early so the
 * newest feedback always shows; when the server is at {@code max-display-entities-global}, new ones are dropped.
 */
final class DisplayRegistry {

    private final Map<UUID, DisplayJob> byEntity = new HashMap<>();
    private final Map<UUID, ArrayDeque<DisplayJob>> byViewer = new HashMap<>();
    private final Map<UUID, Integer> viewerEntities = new HashMap<>();
    private int globalEntities;
    private Limits limits = Limits.DEFAULTS;

    void limits(Limits newLimits) {
        this.limits = newLimits;
    }

    /**
     * Makes room for {@code count} new entities for this viewer (evicting their oldest display effects if needed).
     * Returns false when the effect must be dropped.
     */
    boolean reserve(@Nullable UUID viewer, int count) {
        if (globalEntities + count > limits.maxDisplayEntitiesGlobal()) {
            return false;
        }
        if (viewer == null) {
            return true;
        }
        if (count > limits.maxDisplayEntitiesPerPlayer()) {
            return false;
        }
        ArrayDeque<DisplayJob> queue = byViewer.get(viewer);
        while (queue != null && !queue.isEmpty() && viewerEntities.getOrDefault(viewer, 0) + count
                > limits.maxDisplayEntitiesPerPlayer()) {
            queue.pollFirst().cancel(); // removes its entities and releases them
        }
        return viewerEntities.getOrDefault(viewer, 0) + count <= limits.maxDisplayEntitiesPerPlayer();
    }

    void register(DisplayJob job, Entity entity) {
        byEntity.put(entity.getUniqueId(), job);
        globalEntities++;
        UUID viewer = job.viewer();
        if (viewer != null) {
            ArrayDeque<DisplayJob> queue = byViewer.computeIfAbsent(viewer, k -> new ArrayDeque<>());
            if (!queue.contains(job)) {
                queue.addLast(job);
            }
            viewerEntities.merge(viewer, 1, Integer::sum);
        }
    }

    /** Forgets every entity of this job. Idempotent. */
    void release(DisplayJob job, List<? extends Entity> entities) {
        for (Entity e : entities) {
            if (byEntity.remove(e.getUniqueId()) != null) {
                globalEntities--;
                UUID viewer = job.viewer();
                if (viewer != null) {
                    viewerEntities.computeIfPresent(viewer, (k, v) -> v <= 1 ? null : v - 1);
                }
            }
        }
        UUID viewer = job.viewer();
        if (viewer != null) {
            ArrayDeque<DisplayJob> queue = byViewer.get(viewer);
            if (queue != null) {
                queue.remove(job);
                if (queue.isEmpty()) {
                    byViewer.remove(viewer);
                }
            }
        }
    }

    /** True when no display effect entity is tracked. */
    boolean isEmpty() {
        return byEntity.isEmpty();
    }

    boolean tracks(UUID entity) {
        return byEntity.containsKey(entity);
    }

    @Nullable DisplayJob job(UUID entity) {
        return byEntity.get(entity);
    }

    /** Cancels every display effect shown to this viewer (they quit). */
    void cancelViewer(UUID viewer) {
        ArrayDeque<DisplayJob> queue = byViewer.get(viewer);
        if (queue != null) {
            for (DisplayJob job : new ArrayList<>(queue)) {
                job.cancel();
            }
        }
    }

    int globalEntities() {
        return globalEntities;
    }
}
