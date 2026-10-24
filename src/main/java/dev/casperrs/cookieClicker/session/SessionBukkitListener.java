package dev.casperrs.cookieClicker.session;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Join → load, quit → save. */
public final class SessionBukkitListener implements Listener {

    private final SessionManager sessions;

    public SessionBukkitListener(SessionManager sessions) {
        this.sessions = sessions;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        sessions.join(event.getPlayer().getUniqueId(), event.getPlayer().getName());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        sessions.quit(event.getPlayer().getUniqueId());
    }
}
