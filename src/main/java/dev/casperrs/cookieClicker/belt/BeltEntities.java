package dev.casperrs.cookieClicker.belt;

import dev.casperrs.cookieClicker.config.BeltSettings;
import dev.casperrs.cookieClicker.log.WarnOnce;
import dev.casperrs.cookieClicker.view.StationViews;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.key.Key;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** The item displays of the belt (surface cells and cookies) and the cookie's transform maths. */
final class BeltEntities {

    /** The belt rubber is 1/16 thick: cookies ride this far above the surface (design/conveyor-belt.md). */
    static final double RUBBER = 0.0625;
    /** Thickness of our cookie model (z 6..10 of 16, design/cookie.md) and of a vanilla item sprite (1/16). */
    static final double PACK_COOKIE_THICKNESS = 0.25;
    static final double VANILLA_COOKIE_THICKNESS = 0.0625;

    static final Key BELT_MODEL = Key.key("cookieclicker", "conveyor_belt");
    static final Key COOKIE_MODEL = Key.key("cookieclicker", "cookie");

    private BeltEntities() {
    }

    /** Hidden from everyone at spawn (shown per player), non-persistent, tagged for the enable sweep. */
    static ItemDisplay display(World world, Location at, ItemStack stack, Transformation t) {
        return world.spawn(at, ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.setVisibleByDefault(false);
            d.addScoreboardTag(StationViews.TAG);
            d.setItemStack(stack);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setTransformation(t);
        });
    }

    static ItemStack item(Material base, Key model) {
        ItemStack stack = ItemStack.of(base);
        stack.setData(DataComponentTypes.ITEM_MODEL, model);
        return stack;
    }

    static Material fallbackItem(BeltSettings s, WarnOnce warnings) {
        Material m = Material.matchMaterial(s.fallbackItem());
        if (m == null || !m.isItem() || m.isAir()) {
            warnings.warn("belt-fallback-item", "Belt fallback item '" + s.fallbackItem()
                    + "' is not an item; using BLACK_CARPET");
            return Material.BLACK_CARPET;
        }
        return m;
    }

    /**
     * The cookie {@code along} blocks down the belt, its bottom on the rubber. The item model is centred on the
     * display (NONE transform) and stands upright with its face along z (ours and the vanilla sprite alike): it is
     * turned to face the travel direction, then tipped {@code pitchDegrees} about the belt's cross axis (90 = flat).
     *
     * @param thickness the model's thickness in blocks at scale 1
     */
    static Transformation cookieTransform(BeltPath path, double scale, double pitchDegrees, double thickness,
                                          double along) {
        float sc = (float) scale;
        float lift = (float) (RUBBER + halfHeight(scale, pitchDegrees, thickness));
        Quaternionf rotation = new Quaternionf().rotateY((float) path.travelAngle())
                .rotateX((float) Math.toRadians(pitchDegrees));
        return new Transformation(
                new Vector3f((float) (path.dirX() * along), lift, (float) (path.dirZ() * along)),
                rotation, new Vector3f(sc, sc, sc), new Quaternionf());
    }

    /** Half the height of a cookie (a 1 x 1 x thickness slab at scale 1) tipped {@code pitchDegrees}. */
    static double halfHeight(double scale, double pitchDegrees, double thickness) {
        double p = Math.toRadians(pitchDegrees);
        return scale * (Math.abs(Math.cos(p)) * 0.5 + Math.abs(Math.sin(p)) * thickness / 2);
    }
}
