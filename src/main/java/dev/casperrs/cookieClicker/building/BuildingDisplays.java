package dev.casperrs.cookieClicker.building;

import dev.casperrs.cookieClicker.config.BuildingVisualSettings;
import dev.casperrs.cookieClicker.house.design.BuildingSlots;
import dev.casperrs.cookieClicker.log.WarnOnce;
import dev.casperrs.cookieClicker.view.StationViews;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * Spawns one building instance for its owner only: an item display {@code cookieclicker:building_world/<id>}
 * ({@code custom_model_data} strings[0] = stage) with our pack, a vanilla block display without it. Main thread.
 */
final class BuildingDisplays {

    private final Plugin plugin;
    private final WarnOnce warnings;

    BuildingDisplays(Plugin plugin, WarnOnce warnings) {
        this.plugin = plugin;
        this.warnings = warnings;
    }

    /** The display on {@code slot}, shown to {@code owner}; null while that chunk is not loaded. */
    @Nullable Display spawn(Player owner, World world, int[] origin, boolean withPack, String building,
                            BuildingSlots.Slot slot, int stage, BuildingVisualSettings s) {
        double[] at = BuildingLayout.floorPoint(origin, slot);
        if (!world.isChunkLoaded((int) Math.floor(at[0]) >> 4, (int) Math.floor(at[2]) >> 4)) {
            return null;
        }
        Location loc = new Location(world, at[0], at[1], at[2], slot.yaw() + s.yawOffset(), 0);
        Display d;
        if (withPack) {
            ItemStack stack = modelStack(building, stage, s);
            d = world.spawn(loc, ItemDisplay.class, e -> {
                prepare(e, s);
                e.setItemStack(stack);
                // model bottom at model y 8 = the display position (design/buildings-world.md)
                e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            });
        } else {
            BlockData block = fallbackBlock(building, s);
            float sc = s.fallbackScale();
            d = world.spawn(loc, BlockDisplay.class, e -> {
                prepare(e, s);
                e.setBlock(block);
                // a block display grows from its corner: centre it on the floor point
                e.setTransformation(new Transformation(new Vector3f(-sc / 2, 0, -sc / 2), new Quaternionf(),
                        new Vector3f(sc, sc, sc), new Quaternionf()));
            });
        }
        owner.showEntity(plugin, d);
        return d;
    }

    ItemStack modelStack(String building, int stage, BuildingVisualSettings s) {
        Material base = fallbackBlock(building, s).getMaterial();
        ItemStack stack = ItemStack.of(base.isItem() && !base.isAir() ? base : Material.PAPER);
        stack.setData(DataComponentTypes.ITEM_MODEL, Key.key("cookieclicker", "building_world/" + building));
        stack.setData(DataComponentTypes.CUSTOM_MODEL_DATA,
                CustomModelData.customModelData().addString(Integer.toString(stage)).build());
        return stack;
    }

    private BlockData fallbackBlock(String building, BuildingVisualSettings s) {
        String name = s.fallbackFor(building);
        try {
            BlockData data = Bukkit.createBlockData(name);
            if (!data.getMaterial().isAir()) {
                return data;
            }
        } catch (IllegalArgumentException ignored) {
            // not a block: warned below
        }
        warnings.warn("building-fallback:" + building, "Fallback block '" + name + "' for " + building
                + " is not a block; using barrel");
        return Material.BARREL.createBlockData();
    }

    private static void prepare(Display d, BuildingVisualSettings s) {
        d.setPersistent(false);
        d.setVisibleByDefault(false);
        d.addScoreboardTag(StationViews.TAG);
        d.setViewRange(s.viewRange());
    }
}
