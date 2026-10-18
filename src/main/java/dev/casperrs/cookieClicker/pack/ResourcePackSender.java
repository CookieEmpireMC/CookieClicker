package dev.casperrs.cookieClicker.pack;

import dev.casperrs.cookieClicker.config.PluginSettings;
import dev.casperrs.cookieClicker.text.Messages;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Sends our resource pack (URL + SHA-1 from {@code config.yml}) a little after join, stacked next to other
 * plugins' packs ({@code replace(false)}, never the legacy {@code setResourcePack}). Main thread only.
 */
public final class ResourcePackSender implements Listener {

    /** Stable identity of our pack: the same UUID every time, so a new URL/hash replaces it on the client. */
    public static final UUID PACK_ID = UUID.nameUUIDFromBytes("cookieclicker:resourcepack".getBytes(StandardCharsets.UTF_8));

    private final Plugin plugin;
    private final Supplier<PluginSettings.ResourcePack> settings;
    private final Messages messages;

    public ResourcePackSender(Plugin plugin, Supplier<PluginSettings.ResourcePack> settings, Messages messages) {
        this.plugin = plugin;
        this.settings = settings;
        this.messages = messages;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        PluginSettings.ResourcePack cfg = settings.get();
        if (!cfg.sendable()) {
            return;
        }
        UUID id = event.getPlayer().getUniqueId();
        // not in the join event itself; the delay also lets other plugins' packs go first
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                send(p);
            }
        }, cfg.sendDelayTicks());
    }

    /** Sends (or re-sends, replacing ours only) the pack to one player; no-op when it isn't configured. */
    public void send(Player player) {
        PluginSettings.ResourcePack cfg = settings.get();
        if (!cfg.sendable()) {
            return;
        }
        ResourcePackRequest request = ResourcePackRequest.resourcePackRequest()
                .packs(ResourcePackInfo.resourcePackInfo(PACK_ID, URI.create(cfg.url()), cfg.sha1()))
                .prompt(messages.component(cfg.promptKey()))
                .required(cfg.required())
                .replace(false)
                .build();
        player.sendResourcePacks(request);
    }

    /** {@code /bakery reload}: re-send to everyone online if url or hash changed. */
    public void onReload(PluginSettings.ResourcePack before) {
        PluginSettings.ResourcePack now = settings.get();
        if (now.sendable() && (!now.url().equals(before.url()) || !now.sha1().equals(before.sha1()))) {
            Bukkit.getOnlinePlayers().forEach(this::send);
        }
    }
}
