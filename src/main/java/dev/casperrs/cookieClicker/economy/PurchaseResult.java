package dev.casperrs.cookieClicker.economy;

public enum PurchaseResult {
    BOUGHT,
    CANT_AFFORD,
    /** Not unlocked yet (baked or building requirement not met). */
    LOCKED,
    /** One-time upgrade bought before; one-time upgrades can be bought once. */
    ALREADY_OWNED,
    /** Repeatable upgrade already at {@code max-level}, or the batch would go past it (nothing bought). */
    MAX_LEVEL,
    /** Count outside {@code 1..MAX_BULK} (nothing bought). */
    BAD_COUNT,
    /** No such building/upgrade in the current balance files. */
    UNKNOWN
}
