package dev.casperrs.cookieClicker.view;

import org.bukkit.entity.Entity;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Live views by owner and station, and by entity (for clicks, chunk unloads and the leftover sweep). Main thread. */
final class ViewIndex {

    private final Map<UUID, Map<String, StationView>> byOwner = new HashMap<>();
    private final Map<UUID, StationView> byEntity = new HashMap<>();
    private final Map<UUID, ClickTarget> targets = new HashMap<>();

    void register(StationView view) {
        byOwner.computeIfAbsent(view.owner, k -> new HashMap<>()).put(view.station.id(), view);
        for (Entity e : view.entities()) {
            byEntity.put(e.getUniqueId(), view);
        }
        String id = view.station.id();
        targets.put(view.cookieHitbox.getUniqueId(), ClickTarget.cookie(view.owner, id));
        for (StationView.Panel panel : view.panels()) {
            targets.put(panel.hitbox.getUniqueId(), ClickTarget.panel(view.owner, id, panel.kind));
        }
    }

    /** Removes the view from the index (its entities stay; the caller removes them). */
    void unregister(StationView view) {
        Map<String, StationView> views = byOwner.get(view.owner);
        if (views != null && views.get(view.station.id()) == view) {
            views.remove(view.station.id());
            if (views.isEmpty()) {
                byOwner.remove(view.owner);
            }
        }
        for (Entity e : view.entities()) {
            untrack(e);
        }
    }

    void track(Entity entity, StationView view) {
        byEntity.put(entity.getUniqueId(), view);
    }

    void track(Entity entity, StationView view, ClickTarget target) {
        byEntity.put(entity.getUniqueId(), view);
        targets.put(entity.getUniqueId(), target);
    }

    void untrack(Entity entity) {
        byEntity.remove(entity.getUniqueId());
        targets.remove(entity.getUniqueId());
    }

    @Nullable StationView view(UUID owner, String stationId) {
        Map<String, StationView> views = byOwner.get(owner);
        return views == null ? null : views.get(stationId);
    }

    @Nullable StationView byEntity(UUID entityId) {
        return byEntity.get(entityId);
    }

    @Nullable ClickTarget target(UUID entityId) {
        return targets.get(entityId);
    }

    boolean isTracked(UUID entityId) {
        return byEntity.containsKey(entityId);
    }

    boolean hasEntities() {
        return !byEntity.isEmpty();
    }

    int entityCount() {
        return byEntity.size();
    }

    boolean isEmpty() {
        return byOwner.isEmpty();
    }

    /** The owner's live views; the live collection, so callers must not add or remove views while iterating. */
    Collection<StationView> viewsOf(UUID owner) {
        Map<String, StationView> views = byOwner.get(owner);
        return views == null ? List.of() : views.values();
    }

    Collection<Map<String, StationView>> byOwner() {
        return byOwner.values();
    }

    List<UUID> owners() {
        return List.copyOf(byOwner.keySet());
    }
}
