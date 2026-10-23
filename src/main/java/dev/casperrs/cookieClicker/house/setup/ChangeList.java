package dev.casperrs.cookieClicker.house.setup;

import org.bukkit.World;
import org.bukkit.block.TileState;

import java.util.ArrayList;
import java.util.List;

/** The blocks a setup will change, each with its current data for the backup. Identical blocks are skipped. */
final class ChangeList {

    private final World world;
    private final List<HouseBackup.Cell> cells = new ArrayList<>();
    private final List<HouseBackup.Change> changes = new ArrayList<>();

    ChangeList(World world) {
        this.world = world;
    }

    void set(int x, int y, int z, String data) {
        String now = world.getBlockData(x, y, z).getAsString();
        if (!now.equals(data)) {
            cells.add(new HouseBackup.Cell(x, y, z, now));
            changes.add(new HouseBackup.Change(x, y, z, data));
        }
    }

    /** As {@link #set}, but never outside the world's height and never over a block entity (chest, sign, ...). */
    void setUnlessTile(int x, int y, int z, String data) {
        if (y < world.getMinHeight() || y >= world.getMaxHeight()
                || world.getBlockAt(x, y, z).getState(false) instanceof TileState) {
            return;
        }
        set(x, y, z, data);
    }

    List<HouseBackup.Cell> cells() {
        return List.copyOf(cells);
    }

    List<HouseBackup.Change> changes() {
        return List.copyOf(changes);
    }
}
