package dev.casperrs.cookieClicker.vfx;

import java.util.function.Consumer;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

/** The engine's handle on {@link PrivateDisplays}: holds the plugin and the PDC key so jobs don't pass them around. */
final class Spawner {

    static final String SCOREBOARD_TAG = PrivateDisplays.SCOREBOARD_TAG;

    private final Plugin plugin;
    private final NamespacedKey key;

    Spawner(Plugin plugin) {
        this.plugin = plugin;
        this.key = PrivateDisplays.key(plugin);
    }

    NamespacedKey key() {
        return key;
    }

    <T extends Display> T spawn(Location at, Class<T> type, @Nullable Player viewer, Consumer<? super T> setup) {
        return PrivateDisplays.spawn(plugin, at, type, viewer, setup);
    }

    boolean isOurs(Entity entity) {
        return PrivateDisplays.isOurs(plugin, entity);
    }
}
