package dev.casperrs.cookieClicker.config;

import java.util.Map;

/**
 * What players without our resource pack see, per building / upgrade id, and each building's footprint.
 * A new building id in {@code buildings.yml} needs a line in {@link #BUILDING_ICONS} (and, when its model is bigger
 * than one cell, in {@link #FOOTPRINTS}); ids not listed fall back to the defaults.
 */
public final class VisualFallbacks {

    /** Row icon item for buildings and upgrades not listed. */
    public static final String DEFAULT_ICON = "COOKIE";
    /** Fallback block for bought buildings not in {@link #BUILDING_BLOCKS}. */
    public static final String DEFAULT_BLOCK = "minecraft:barrel";

    /** Building id -> vanilla {@code Material} shown as the panel row icon. */
    public static final Map<String, String> BUILDING_ICONS = Map.ofEntries(
            Map.entry("villager_baker", "BREAD"),
            Map.entry("wheat_farm", "WHEAT"),
            Map.entry("cow_barn", "MILK_BUCKET"),
            Map.entry("sugar_cane_field", "SUGAR_CANE"),
            Map.entry("industrial_oven", "BLAST_FURNACE"),
            Map.entry("extra_belt", "POWERED_RAIL"),
            Map.entry("beehive", "HONEYCOMB"),
            Map.entry("cocoa_plantation", "COCOA_BEANS"),
            Map.entry("shipping_centre", "BARREL"),
            Map.entry("alchemy_lab", "BREWING_STAND"),
            Map.entry("portal_bakery", "END_PORTAL_FRAME"));

    /** Upgrade id -> vanilla {@code Material} shown on the click upgrades panel and the shelf. */
    public static final Map<String, String> UPGRADE_ICONS = Map.ofEntries(
            Map.entry("bakers_strength", "COOKIE"),
            Map.entry("wooden_spatula", "WOODEN_SHOVEL"),
            Map.entry("stone_rolling_pin", "STONE_SHOVEL"),
            Map.entry("iron_rolling_pin", "IRON_INGOT"),
            Map.entry("golden_glove", "GOLDEN_CARROT"),
            Map.entry("diamond_fingers", "DIAMOND"),
            Map.entry("netherite_hands", "NETHERITE_INGOT"),
            Map.entry("handy_fingers_1", "STICK"),
            Map.entry("handy_fingers_2", "BLAZE_ROD"),
            Map.entry("handy_fingers_3", "AMETHYST_SHARD"));

    /** Building id -> block data of the bought building shown on the plot. */
    public static final Map<String, String> BUILDING_BLOCKS = Map.of(
            "villager_baker", "minecraft:crafting_table",
            "industrial_oven", "minecraft:blast_furnace",
            "extra_belt", "minecraft:smooth_stone_slab",
            "shipping_centre", "minecraft:barrel",
            "alchemy_lab", "minecraft:brewing_stand",
            "portal_bakery", "minecraft:crying_obsidian");

    /**
     * Building id -> side of its centred footprint square in blocks (design/buildings-world.md); decor is kept off
     * every cell it covers. A building not listed keeps only its slot cell free.
     */
    public static final Map<String, Double> FOOTPRINTS = Map.of(
            "villager_baker", 0.82,
            "industrial_oven", 1.55,
            "extra_belt", 1.56,
            "shipping_centre", 1.60,
            "alchemy_lab", 1.50,
            "portal_bakery", 1.41);

    private VisualFallbacks() {
    }
}
