package dev.casperrs.cookieClicker.house.cinematic;

import dev.casperrs.cookieClicker.plot.PlotTracker;
import dev.casperrs.cookieClicker.text.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * {@code /bakery house rebuild <player>}: forgets that a player has seen a plot's house and plays the cinematic right
 * away when they are online and in that plot.
 */
final class HouseRebuild {

    private final HouseCinematics cinematics;
    private final HouseSeen seen;
    private final PlotTracker tracker;
    private final Messages messages;

    HouseRebuild(HouseCinematics cinematics, HouseSeen seen, PlotTracker tracker, Messages messages) {
        this.cinematics = cinematics;
        this.seen = seen;
        this.tracker = tracker;
        this.messages = messages;
    }

    void rebuild(CommandSender sender, String plotId, String name) {
        Player target = Bukkit.getPlayerExact(name);
        OfflinePlayer offline = target != null ? target : Bukkit.getOfflinePlayerIfCached(name);
        if (offline == null) {
            messages.send(sender, "house.rebuild-unknown-player", Placeholder.unparsed("name", name));
            return;
        }
        UUID id = offline.getUniqueId();
        String shown = offline.getName() != null ? offline.getName() : name;
        cinematics.forget(id);
        seen.clear(id, plotId, () -> {
            Player p = Bukkit.getPlayer(id);
            if (p == null || !tracker.presence(id).inPlot(plotId)) {
                messages.send(sender, "house.rebuild-cleared", Placeholder.unparsed("name", shown),
                        Placeholder.unparsed("id", plotId));
                return;
            }
            String key = switch (cinematics.start(p, plotId)) {
                case STARTED -> "house.rebuild-started";
                case INSIDE -> "house.rebuild-inside";
                case NOT_LOADED -> "house.rebuild-not-loaded";
                case NO_HOUSE, WRONG_WORLD, NOTHING_TO_BUILD -> "house.rebuild-no-house";
            };
            cinematics.startAmbience(p, plotId);
            messages.send(sender, key, Placeholder.unparsed("name", shown), Placeholder.unparsed("id", plotId));
        });
    }
}
