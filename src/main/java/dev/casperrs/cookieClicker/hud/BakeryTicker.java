package dev.casperrs.cookieClicker.hud;

import dev.casperrs.cookieClicker.economy.CookiePouch;
import dev.casperrs.cookieClicker.economy.Economy;
import dev.casperrs.cookieClicker.session.SessionManager;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * Runs once per second (every 20 ticks) on the main thread: applies one second of production
 * ({@code pouch += total_cps}) to every loaded, online player, then refreshes the HUD every
 * {@code hud.refresh-seconds}.
 */
public final class BakeryTicker implements Runnable {

    private final SessionManager sessions;
    private final Economy economy;
    private final Hud hud;
    private final Function<UUID, Player> onlinePlayer;
    private int hudEverySeconds = 1;
    private long seconds;

    /** @param onlinePlayer {@code Bukkit::getPlayer} in the plugin; tests can return null to skip the HUD */
    public BakeryTicker(SessionManager sessions, Economy economy, Hud hud, Function<UUID, Player> onlinePlayer) {
        this.sessions = sessions;
        this.economy = economy;
        this.hud = hud;
        this.onlinePlayer = onlinePlayer;
    }

    public void setHudEverySeconds(int hudEverySeconds) {
        this.hudEverySeconds = Math.max(1, hudEverySeconds);
    }

    @Override
    public void run() {
        seconds++;
        boolean refreshHud = seconds % hudEverySeconds == 0;
        for (UUID id : List.copyOf(sessions.loadedPlayers())) {
            CookiePouch pouch = sessions.get(id).orElse(null);
            if (pouch == null) {
                continue;
            }
            // sessions exist only between join and quit, so every loaded player is online
            economy.produce(pouch, 1);
            if (refreshHud) {
                Player player = onlinePlayer.apply(id);
                if (player != null) {
                    hud.refresh(player, pouch);
                }
            }
        }
    }
}
