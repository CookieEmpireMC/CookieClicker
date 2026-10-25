package dev.casperrs.cookieClicker.economy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Building ids that were renamed: a save with the old id loads under the new one (and is written back that way). */
public final class LegacyIds {

    /** Old building id → new building id. */
    public static final Map<String, String> BUILDING_RENAMES = Map.of("beehive", "chicken_farm");

    private LegacyIds() {
    }

    /**
     * {@code owned} with renamed ids moved to their new id. When a save has both (should not happen), the larger
     * count wins so nothing bought is lost; each rename adds a line to {@code notes}.
     */
    public static Map<String, Integer> migrateBuildings(Map<String, Integer> owned, List<String> notes) {
        if (BUILDING_RENAMES.keySet().stream().noneMatch(owned::containsKey)) {
            return owned;
        }
        Map<String, Integer> out = new HashMap<>(owned);
        BUILDING_RENAMES.forEach((oldId, newId) -> {
            Integer old = out.remove(oldId);
            if (old != null) {
                Integer existing = out.get(newId);
                out.put(newId, existing == null ? old : Math.max(existing, old));
                notes.add("building '" + oldId + "' (" + old + ") renamed to '" + newId + "'"
                        + (existing == null ? "" : ", kept the larger of " + existing + " and " + old));
            }
        });
        return out;
    }
}
