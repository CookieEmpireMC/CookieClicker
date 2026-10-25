"""Resolves the `(v?)` block ids of docs/research/farm-buildings.md (and every id the farm palettes need) against the 26.3
sources jar. Fails when one is missing. Result table: design/countryside.md ("Block ids").
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent / "lib"))
import block_ids  # noqa: E402

# every id of the research note marked (v?) or used by a later place; all must exist in 26.3
CHECK = [
    "resin_brick_stairs", "resin_brick_slab", "resin_bricks", "pale_oak_planks", "pale_oak_log", "pale_oak_stairs", "bee_nest",
    "beehive", "weathered_cut_copper_stairs", "deepslate_tile_stairs", "deepslate_tile_slab", "deepslate_tiles",
    "blackstone_stairs", "cobbled_deepslate_stairs", "tuff_bricks", "mud_brick_stairs", "mud_bricks", "packed_mud",
    "iron_chain", "grindstone", "hopper", "ladder", "lever", "cocoa", "jungle_log", "jungle_planks", "jungle_stairs",
    "jungle_trapdoor", "jungle_fence", "hay_block", "bone_block", "white_candle", "stonecutter", "cauldron", "composter",
    "mossy_stone_brick_wall", "stone_brick_wall", "cobblestone_wall", "mossy_cobblestone_wall", "moss_block", "moss_carpet",
    "sweet_berry_bush", "short_grass", "lily_pad", "vine", "red_nether_bricks", "polished_diorite", "calcite", "tuff",
    "rooted_dirt", "coarse_dirt", "podzol", "mud", "clay", "dirt_path", "water_cauldron", "lantern", "campfire",
]

if __name__ == "__main__":
    miss = block_ids.missing(CHECK)
    src = "sources jar" if block_ids.ids() is not None else "client jar blockstates (sources jar not found)"
    print(f"block ids checked against the {src}: {len(CHECK)} ids, {len(miss)} missing {miss}")
    sys.exit(1 if miss else 0)
