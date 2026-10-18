package dev.casperrs.cookieClicker.economy;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A player's economy state (model.md "State per player"). Mutable and <b>not thread-safe</b>: only
 * touch it on the main thread, and hand {@link #snapshot()} to async code (saving).
 * Building/upgrade IDs that the current balance files don't know are kept, so a config change
 * never deletes progress.
 */
public final class CookiePouch {

    private double pouch;
    private double baked;
    private final Map<String, Integer> owned = new HashMap<>();
    private final Set<String> upgrades = new HashSet<>();

    public CookiePouch() {
    }

    public static CookiePouch fromSnapshot(PouchSnapshot snapshot) {
        CookiePouch p = new CookiePouch();
        p.pouch = SafeMath.sanitize(snapshot.pouch());
        p.baked = SafeMath.sanitize(snapshot.baked());
        snapshot.owned().forEach((id, n) -> {
            if (n != null && n > 0) {
                p.owned.put(id, n);
            }
        });
        p.upgrades.addAll(snapshot.upgrades());
        return p;
    }

    public PouchSnapshot snapshot() {
        return new PouchSnapshot(pouch, baked, owned, upgrades);
    }

    /** Spendable cookies. */
    public double pouch() {
        return pouch;
    }

    /** Cookies baked all time; never decreases. */
    public double baked() {
        return baked;
    }

    public int owned(String buildingId) {
        return owned.getOrDefault(buildingId, 0);
    }

    public Map<String, Integer> ownedView() {
        return Collections.unmodifiableMap(owned);
    }

    public boolean hasUpgrade(String upgradeId) {
        return upgrades.contains(upgradeId);
    }

    public Set<String> upgradesView() {
        return Collections.unmodifiableSet(upgrades);
    }

    /** Adds to both pouch and baked (clicks, production). Negative or non-finite amounts are ignored. */
    void earn(double amount) {
        if (!(amount > 0) || !Double.isFinite(amount)) {
            return;
        }
        pouch = SafeMath.add(pouch, amount);
        baked = SafeMath.add(baked, amount);
    }

    /** Subtracts from the pouch only; caller checked affordability. */
    void spend(double amount) {
        pouch = Math.max(0, pouch - amount);
    }

    void addBuilding(String buildingId, int count) {
        owned.merge(buildingId, count, (a, b) -> (int) Math.min(Integer.MAX_VALUE, (long) a + b));
    }

    void addUpgrade(String upgradeId) {
        upgrades.add(upgradeId);
    }
}
