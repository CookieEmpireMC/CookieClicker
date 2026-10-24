package dev.casperrs.cookieClicker.house.setup;

import org.bukkit.Bukkit;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Leaves;

/**
 * Block data for blocks a design places (house, ground, fence, belt frame, decor). Leaves are always
 * {@code persistent=true}, whatever the file says, so a pasted tree or hedge never decays. Restores of a backup
 * don't come through here: they put back exactly what was there.
 */
final class DesignBlocks {

    private DesignBlocks() {
    }

    /** Parses {@code data} like {@link Bukkit#createBlockData(String)}; throws IllegalArgumentException when invalid. */
    static BlockData create(String data) {
        BlockData parsed = Bukkit.createBlockData(data);
        if (parsed instanceof Leaves leaves && !leaves.isPersistent()) {
            leaves.setPersistent(true);
        }
        return parsed;
    }
}
