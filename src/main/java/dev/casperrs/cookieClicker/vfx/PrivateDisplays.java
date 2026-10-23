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
 * Spawns display entities that only one player sees. Everything is set up inside the spawn consumer, before the
 * entity joins the world, so no other player ever receives it: non-persistent, hidden by default when there is an
 * owner, tagged with the {@code cookieclicker} scoreboard tag and the {@code cookieclicker:vfx} PDC key (so the
 * startup sweep finds leftovers). Then it is shown to the owner. Main thread only.
 */
public final class PrivateDisplays {

    /** Shared sweep tag of every plugin entity (see docs/research/display-cookie.md). */
    public static final String SCOREBOARD_TAG = "cookieclicker";

    private PrivateDisplays() {
    }

    /** The PDC key {@code <plugin>:vfx} that marks the entities spawned here. */
    public static NamespacedKey key(Plugin plugin) {
        return new NamespacedKey(plugin, "vfx");
    }

    /**
     * Spawns a display at {@code at}, hidden for everyone but {@code owner} (everyone sees it when {@code owner}
     * is null). {@code setup} runs before the entity is added to the world; the default brightness is full light.
     */
    public static <T extends Display> T spawn(Plugin plugin, Location at, Class<T> type, @Nullable Player owner,
                                              Consumer<? super T> setup) {
        World world = at.getWorld();
        NamespacedKey key = key(plugin);
        T entity = world.spawn(at, type, e -> {
            e.setPersistent(false);
            e.setVisibleByDefault(owner == null);
            e.addScoreboardTag(SCOREBOARD_TAG);
            e.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
            e.setBrightness(new Display.Brightness(15, 15));
            setup.accept(e);
        });
        if (owner != null) {
            owner.showEntity(plugin, entity);
        }
        return entity;
    }

    /**
     * True for entities spawned by {@link #spawn} (they always carry both the tag and the PDC key). The tag is
     * checked first because it is a cheap set lookup; only plugin entities pay for the PDC read.
     */
    public static boolean isOurs(Plugin plugin, Entity entity) {
        return entity.getScoreboardTags().contains(SCOREBOARD_TAG)
                && entity.getPersistentDataContainer().has(key(plugin), PersistentDataType.BYTE);
    }
}
