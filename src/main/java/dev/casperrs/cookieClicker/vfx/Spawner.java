package dev.casperrs.cookieClicker.vfx;

import java.util.function.Consumer;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

/**
 * The only place the engine spawns entities. Everything is set up inside the spawn consumer (before the entity
 * joins the world, so no other player ever receives it): non-persistent, hidden by default when there is a viewer,
 * tagged with the {@code cookieclicker} scoreboard tag and the {@code cookieclicker:vfx} PDC key. Then it's shown
 * to the viewer only.
 */
final class Spawner {

    /** Shared sweep tag of every plugin entity (see docs/research/display-cookie.md). */
    static final String SCOREBOARD_TAG = "cookieclicker";

    private final Plugin plugin;
    private final NamespacedKey key;

    Spawner(Plugin plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "vfx");
    }

    NamespacedKey key() {
        return key;
    }

    <T extends Display> T spawn(Location at, Class<T> type, @Nullable Player viewer, Consumer<? super T> setup) {
        World world = at.getWorld();
        T entity = world.spawn(at, type, e -> {
            e.setPersistent(false);
            e.setVisibleByDefault(viewer == null);
            e.addScoreboardTag(SCOREBOARD_TAG);
            e.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
            e.setBrightness(new Display.Brightness(15, 15));
            setup.accept(e);
        });
        if (viewer != null) {
            viewer.showEntity(plugin, entity);
        }
        return entity;
    }

    boolean isOurs(Entity entity) {
        return entity.getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }
}
