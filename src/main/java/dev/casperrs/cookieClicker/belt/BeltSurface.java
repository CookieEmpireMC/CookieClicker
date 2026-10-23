package dev.casperrs.cookieClicker.belt;

import dev.casperrs.cookieClicker.config.BeltSettings;
import dev.casperrs.cookieClicker.plot.Plot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One plot's moving belt surface, shared by everyone: one item display per cell in two sets (our pack model and the
 * vanilla fallback item); each player is shown one set. Main thread.
 */
final class BeltSurface {

    final BeltPath path;
    private final List<ItemDisplay> withPack = new ArrayList<>();
    private final List<ItemDisplay> vanilla = new ArrayList<>();
    /** Which set each player was shown (true = pack set). */
    private final Map<UUID, Boolean> shown = new HashMap<>();

    private BeltSurface(BeltPath path) {
        this.path = path;
    }

    /** The plot's surface, or null while its chunks aren't loaded (or the belt has no valid path). */
    static @Nullable BeltSurface spawn(Plot plot, World world, BeltSettings s, Material fallback) {
        BeltPath path = BeltPath.of(plot.belt(), plot.pedestal());
        if (path == null) {
            return null;
        }
        for (Plot.Cell c : plot.belt().cells()) {
            if (!world.isChunkLoaded(c.x() >> 4, c.z() >> 4)) {
                return null;
            }
        }
        BeltSurface surface = new BeltSurface(path);
        // Model north (-z) turned to the travel direction (+ surface yaw). An item display renders its item turned
        // 180 degrees about y, so that turn is added back: the slats roll toward model north = toward the pedestal.
        // Our model's bottom is at its origin, a vanilla block item is centred on it: the fallback is lifted half a
        // block onto the frame.
        Quaternionf turn = new Quaternionf().rotateY((float) (Math.atan2(-path.dirX(), -path.dirZ()) + Math.PI
                - Math.toRadians(s.surfaceYaw())));
        Transformation model = new Transformation(new Vector3f(), turn, new Vector3f(1, 1, 1), new Quaternionf());
        Transformation lifted = new Transformation(new Vector3f(0, 0.5f, 0), turn, new Vector3f(1, 1, 1),
                new Quaternionf());
        try {
            for (int i = 0; i < path.length(); i++) {
                double[] c = path.cellCentre(i);
                Location at = new Location(world, c[0], c[1], c[2]);
                surface.withPack.add(BeltEntities.display(world, at,
                        BeltEntities.item(fallback, BeltEntities.BELT_MODEL), model));
                surface.vanilla.add(BeltEntities.display(world, at, ItemStack.of(fallback), lifted));
            }
        } catch (RuntimeException e) {
            surface.remove();
            throw e;
        }
        return surface;
    }

    boolean healthy() {
        for (ItemDisplay d : withPack) {
            if (!d.isValid()) {
                return false;
            }
        }
        for (ItemDisplay d : vanilla) {
            if (!d.isValid()) {
                return false;
            }
        }
        return true;
    }

    /** Shows {@code player} the set that fits their pack status (no packets when it already does). */
    void showTo(Plugin plugin, Player player, boolean pack) {
        Boolean before = shown.put(player.getUniqueId(), pack);
        if (before == null || before != pack) {
            (pack ? withPack : vanilla).forEach(e -> player.showEntity(plugin, e));
            (pack ? vanilla : withPack).forEach(e -> player.hideEntity(plugin, e));
        }
    }

    void forget(UUID player) {
        shown.remove(player);
    }

    void remove() {
        withPack.forEach(Entity::remove);
        vanilla.forEach(Entity::remove);
        withPack.clear();
        vanilla.clear();
        shown.clear();
    }
}
