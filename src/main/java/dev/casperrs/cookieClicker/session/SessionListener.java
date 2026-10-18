package dev.casperrs.cookieClicker.session;

import dev.casperrs.cookieClicker.economy.CookiePouch;

import java.util.UUID;

/** Session lifecycle hooks, called on the main thread (pass 2: show/hide the player's station UI). */
public interface SessionListener {

    default void onLoaded(UUID player, CookiePouch pouch) {
    }

    default void onLoadFailed(UUID player) {
    }

    /** Before the session is saved and removed (player quit). */
    default void onUnloaded(UUID player) {
    }
}
