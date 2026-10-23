package dev.casperrs.cookieClicker.view;

import dev.casperrs.cookieClicker.config.StationVisualSettings;
import dev.casperrs.cookieClicker.economy.CookiePouch;
import dev.casperrs.cookieClicker.economy.Economy;
import dev.casperrs.cookieClicker.log.WarnOnce;
import dev.casperrs.cookieClicker.view.panel.PanelKind;
import dev.casperrs.cookieClicker.view.panel.PanelRows;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * The upgrade shelf under the click upgrades panel: one icon per bought one-time upgrade. A fresh buy flies its icon
 * out of the panel row (one client-interpolated teleport plus a scale interpolation, no per-tick teleports) and gives
 * it a hitbox when it lands. Main thread.
 */
final class ShelfController {

    /** Ticks between a buy and the start of its icon's flight (the {@code upgrade_bought} sparkle comes first). */
    private static final int FLY_DELAY_TICKS = 2;

    private final Supplier<StationVisualSettings> settings;
    private final Economy economy;
    private final StationSpawner spawner;
    private final ViewIndex index;
    private final WarnOnce warnings;
    private BiConsumer<Player, Location> landing = (player, at) -> { };

    ShelfController(Supplier<StationVisualSettings> settings, Economy economy, StationSpawner spawner,
                    ViewIndex index, WarnOnce warnings) {
        this.settings = settings;
        this.economy = economy;
        this.spawner = spawner;
        this.index = index;
        this.warnings = warnings;
    }

    void setLanding(BiConsumer<Player, Location> landing) {
        this.landing = landing;
    }

    private double[] slotAt(StationView view, int slot) {
        StationView.Panel clicks = view.clicks;
        return clicks.frame.point(clicks.anchor, settings.get().shelf().slot(slot));
    }

    /**
     * Makes the shelf match the bought one-time upgrades: icons no longer shown go, missing ones appear in place (no
     * flight: a fresh view, or bought elsewhere), and icons whose slot changed glide there.
     */
    void sync(Player owner, StationView view, CookiePouch pouch) {
        StationVisualSettings.Shelf s = settings.get().shelf();
        List<String> wanted = PanelRows.shelf(economy.config(), pouch.upgradeLevelsView(), s.maxIcons());
        for (StationView.ShelfIcon icon : List.copyOf(view.shelf.values())) {
            if (!wanted.contains(icon.upgradeId)) {
                remove(view, icon);
            }
        }
        for (int slot = 0; slot < wanted.size(); slot++) {
            String id = wanted.get(slot);
            StationView.ShelfIcon icon = view.shelf.get(id);
            if (icon == null) {
                icon = spawnIcon(owner, view, id, slot, slotAt(view, slot), s.scale());
                if (icon != null) {
                    addHitbox(owner, view, icon);
                }
            } else if (icon.slot != slot) {
                move(view, icon, slot);
            }
        }
    }

    /**
     * A one-time upgrade was just bought from {@code row}: its icon pops out of the row and glides to its slot after
     * a short delay. Nothing happens when its slot is past {@code max-icons}.
     */
    void launch(Player owner, StationView view, StationView.Row row, String upgradeId, CookiePouch pouch) {
        StationVisualSettings s = settings.get();
        int slot = PanelRows.shelf(economy.config(), pouch.upgradeLevelsView(), s.shelf().maxIcons()).indexOf(upgradeId);
        if (slot < 0 || view.shelf.containsKey(upgradeId)) {
            return;
        }
        StationView.ShelfIcon icon = spawnIcon(owner, view, upgradeId, slot, row.iconAt, s.panel().iconScale());
        if (icon != null) {
            icon.flightStart = Bukkit.getCurrentTick() + FLY_DELAY_TICKS;
            icon.landTick = icon.flightStart + s.shelf().flyTicks();
        }
    }

    private StationView.@Nullable ShelfIcon spawnIcon(Player owner, StationView view, String id, int slot,
                                                      double[] at, float scale) {
        StationVisualSettings s = settings.get();
        List<Entity> spawned = new ArrayList<>(1);
        try {
            ItemDisplay d = spawner.spawn(owner, at, view.clicks.iconYaw, ItemDisplay.class, spawned, e -> {
                e.setItemStack(spawner.iconStack(PanelKind.CLICKS, id, view.withPack));
                e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                e.setBillboard(Display.Billboard.FIXED);
                e.setViewRange(s.panel().viewRange());
                e.setBrightness(new Display.Brightness(15, 15));
                // every move of a shelf icon (its flight, a slot change) is one interpolated teleport
                e.setTeleportDuration(s.shelf().flyTicks());
                e.setTransformation(scaled(scale));
            });
            StationView.ShelfIcon icon = new StationView.ShelfIcon(id, d, slot);
            view.shelf.put(id, icon);
            index.track(d, view);
            return icon;
        } catch (RuntimeException e) {
            spawned.forEach(Entity::remove);
            warnings.warn("shelf", "Could not spawn a shelf icon: " + e);
            return null;
        }
    }

    private void addHitbox(Player owner, StationView view, StationView.ShelfIcon icon) {
        float size = settings.get().shelf().hitboxSize();
        double[] at = slotAt(view, icon.slot);
        List<Entity> spawned = new ArrayList<>(1);
        try {
            Interaction box = spawner.spawnBox(owner, new double[] {at[0], at[1] - size / 2, at[2]}, size, size,
                    spawned);
            icon.hitbox = box;
            index.track(box, view, ClickTarget.shelf(view.owner, view.station.id(), icon.upgradeId));
        } catch (RuntimeException e) {
            spawned.forEach(Entity::remove);
            warnings.warn("shelf", "Could not spawn a shelf icon hitbox: " + e);
        }
    }

    private void move(StationView view, StationView.ShelfIcon icon, int slot) {
        icon.slot = slot;
        if (icon.flightStart != Long.MIN_VALUE) {
            return; // not launched yet: it takes off toward its current slot
        }
        double[] at = slotAt(view, slot);
        World world = icon.icon.getWorld();
        icon.icon.teleport(new Location(world, at[0], at[1], at[2], view.clicks.iconYaw, 0));
        if (icon.hitbox != null) {
            float size = settings.get().shelf().hitboxSize();
            icon.hitbox.teleport(new Location(world, at[0], at[1] - size / 2, at[2]));
        }
    }

    private void remove(StationView view, StationView.ShelfIcon icon) {
        view.shelf.remove(icon.upgradeId);
        index.untrack(icon.icon);
        icon.icon.remove();
        if (icon.hitbox != null) {
            index.untrack(icon.hitbox);
            icon.hitbox.remove();
        }
    }

    /** Animation tick: start due flights, land finished ones (hitbox + landing feedback). */
    void animate(StationView view, long now) {
        if (view.shelf.isEmpty()) {
            return;
        }
        // no copy: landing only adds a hitbox and plays feedback, it never adds or removes icons
        for (StationView.ShelfIcon icon : view.shelf.values()) {
            if (!icon.flying() || !icon.icon.isValid()) {
                continue;
            }
            if (icon.flightStart != Long.MIN_VALUE && now >= icon.flightStart) {
                takeOff(view, icon);
            } else if (icon.flightStart == Long.MIN_VALUE && now >= icon.landTick) {
                land(view, icon);
            }
        }
    }

    private void takeOff(StationView view, StationView.ShelfIcon icon) {
        StationVisualSettings.Shelf s = settings.get().shelf();
        icon.flightStart = Long.MIN_VALUE;
        double[] at = slotAt(view, icon.slot);
        icon.icon.teleport(new Location(icon.icon.getWorld(), at[0], at[1], at[2], view.clicks.iconYaw, 0));
        icon.icon.setInterpolationDelay(0);
        icon.icon.setInterpolationDuration(s.flyTicks());
        icon.icon.setTransformation(scaled(s.scale()));
    }

    private void land(StationView view, StationView.ShelfIcon icon) {
        icon.landTick = Long.MIN_VALUE;
        Player owner = Bukkit.getPlayer(view.owner);
        if (owner != null) {
            addHitbox(owner, view, icon);
            double[] at = slotAt(view, icon.slot);
            landing.accept(owner, new Location(owner.getWorld(), at[0], at[1], at[2]));
        }
    }

    private static Transformation scaled(float scale) {
        return new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(scale, scale, scale),
                new Quaternionf());
    }
}
