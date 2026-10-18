package dev.casperrs.cookieClicker.pack;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Remembers which players have <b>our</b> resource pack loaded (status events for {@link ResourcePackSender#PACK_ID}
 * only; ItemsAdder sends its own pack on the same server), so sounds, the HUD, the cookie and the panel icons can
 * fall back to vanilla for everyone else. Main thread only.
 *
 * <p>With {@link #setAcceptAnyPack(boolean) accept-any-pack} (used when {@code resource-pack.enabled: false}, i.e.
 * our assets were merged into another plugin's pack) any successfully loaded pack counts.
 */
public final class ResourcePackTracker implements Listener {

    private final UUID packId;
    private final Set<UUID> loaded = new HashSet<>();
    private boolean acceptAnyPack;
    private Consumer<Player> onDeclined = p -> {
    };

    public ResourcePackTracker(UUID packId) {
        this.packId = packId;
    }

    public boolean hasPack(Player player) {
        return loaded.contains(player.getUniqueId());
    }

    public void setAcceptAnyPack(boolean acceptAnyPack) {
        this.acceptAnyPack = acceptAnyPack;
    }

    /** Called once when a player declines our pack (for the friendly "vanilla stand-ins" message). */
    public void onDeclined(Consumer<Player> callback) {
        this.onDeclined = callback;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onStatus(PlayerResourcePackStatusEvent event) {
        boolean ours = packId.equals(event.getID());
        if (!ours && !acceptAnyPack) {
            return; // another plugin's pack (ItemsAdder): says nothing about ours
        }
        UUID id = event.getPlayer().getUniqueId();
        switch (event.getStatus()) {
            case SUCCESSFULLY_LOADED -> loaded.add(id);
            case ACCEPTED, DOWNLOADED -> {
                // still in progress
            }
            case DECLINED -> {
                if (ours) {
                    loaded.remove(id);
                    onDeclined.accept(event.getPlayer());
                }
            }
            default -> {
                if (ours) {
                    loaded.remove(id);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        loaded.remove(event.getPlayer().getUniqueId());
    }
}
