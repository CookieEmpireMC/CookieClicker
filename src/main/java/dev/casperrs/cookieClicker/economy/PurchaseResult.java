package dev.casperrs.cookieClicker.economy;

public enum PurchaseResult {
    BOUGHT,
    CANT_AFFORD,
    /** Not unlocked yet (baked or building requirement not met). */
    LOCKED,
    /** Upgrade bought before; upgrades can be bought once. */
    ALREADY_OWNED,
    /** No such building/upgrade in the current balance files. */
    UNKNOWN
}
