package dev.casperrs.cookieClicker.view;

import dev.casperrs.cookieClicker.config.StationVisualSettings;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.Vector;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * The one place clicks on our hitboxes come in. Right click: {@link PlayerInteractAtEntityEvent} (main hand).
 * Left click: {@link EntityDamageByEntityEvent} and {@link PrePlayerAttackEntityEvent} (whichever fires on 26.3 for
 * an Interaction; both are handled and deduped). One click per player per tick, owner and distance checked
 * server-side, the event is cancelled. With {@code debug.log-click-events} every event on one of our hitboxes is
 * logged, so the in-game test can see which events fire. Main thread only.
 */
public final class ClickRouter implements Listener {

    private final StationViews views;
    private final StationInteractions interactions;
    private final Supplier<StationVisualSettings> settings;
    private final Logger log;
    private final Map<UUID, Integer> lastClickTick = new HashMap<>();

    public ClickRouter(StationViews views, StationInteractions interactions, Supplier<StationVisualSettings> settings,
                       Logger log) {
        this.views = views;
        this.interactions = interactions;
        this.settings = settings;
        this.log = log;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractAt(PlayerInteractAtEntityEvent event) {
        Entity clicked = event.getRightClicked();
        if (views.target(clicked.getUniqueId()).isEmpty()) {
            return;
        }
        event.setCancelled(true);
        debug("PlayerInteractAtEntityEvent(" + event.getHand() + ")", event.getPlayer(), clicked);
        if (event.getHand() != EquipmentSlot.HAND) {
            return; // the off-hand fires a second event for the same click
        }
        Vector hit = clicked.getLocation().toVector().add(event.getClickedPosition());
        route(event.getPlayer(), clicked, hit);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            leftClick(player, event.getEntity(), event, "EntityDamageByEntityEvent");
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPreAttack(PrePlayerAttackEntityEvent event) {
        leftClick(event.getPlayer(), event.getAttacked(), event, "PrePlayerAttackEntityEvent");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        lastClickTick.remove(event.getPlayer().getUniqueId());
    }

    private void leftClick(Player player, Entity target, Cancellable event, String name) {
        if (views.target(target.getUniqueId()).isEmpty()) {
            return;
        }
        event.setCancelled(true); // never let a left click "damage" our hitbox
        debug(name, player, target);
        route(player, target, null);
    }

    private void route(Player player, Entity hitbox, @Nullable Vector hit) {
        ClickTarget target = views.target(hitbox.getUniqueId()).orElse(null);
        if (target == null || !player.getUniqueId().equals(target.owner())) {
            return; // never trust visibility alone: only the owner may click
        }
        int tick = Bukkit.getCurrentTick();
        Integer last = lastClickTick.put(player.getUniqueId(), tick);
        if (last != null && last == tick) {
            return; // the same click reported by a second event
        }
        double max = settings.get().clickDistance();
        if (!player.getWorld().equals(hitbox.getWorld())
                || player.getEyeLocation().distanceSquared(hitbox.getLocation()) > max * max) {
            return;
        }
        StationView view = views.view(target.owner(), target.stationId());
        if (view == null) {
            return;
        }
        switch (target.kind()) {
            case COOKIE -> interactions.clickCookie(player, view, hit);
            case ROW -> {
                if (target.buildingId() != null) {
                    interactions.clickRow(player, view, target.buildingId());
                }
            }
        }
    }

    private void debug(String event, Player player, Entity target) {
        if (!settings.get().debugClicks()) {
            return;
        }
        String trackedBy = target.getTrackedBy().stream().map(Player::getName).collect(Collectors.joining(","));
        log.info("[click-debug] tick " + Bukkit.getCurrentTick() + " " + event + " by " + player.getName()
                + " on " + target.getType() + " " + views.target(target.getUniqueId()).map(ClickTarget::kind).orElse(null)
                + " trackedBy=[" + trackedBy + "]");
    }
}
