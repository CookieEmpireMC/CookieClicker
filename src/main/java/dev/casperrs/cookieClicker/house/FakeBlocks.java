package dev.casperrs.cookieClicker.house;

import dev.casperrs.cookieClicker.plot.RegionShape;
import io.papermc.paper.math.Position;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * The <b>only</b> class that sends per-player fake blocks (research: fake-blocks Q5). Everything is a packet to one
 * player; the server world is never changed. Uses {@code sendMultiBlockChange} (one packet per chunk section, never a
 * packet per block) and {@code sendBlockUpdate} for tile entities (sign text) after their block is real again.
 * Positions are packed with {@link RegionShape#pack}. Nothing is sent to a player in another world, and blocks in
 * unloaded chunks are skipped (the client gets them with the chunk). {@link Position} is {@code @Experimental}, so it
 * stays in this one class. Main thread only.
 */
public final class FakeBlocks {

    private BlockData air;
    /** Whether a material has a block entity (sign, chest, ...), found once per material. */
    private final Map<Material, Boolean> tileTypes = new EnumMap<>(Material.class);

    /** Sends fake air at {@code packed[0..count)} to {@code player}. */
    public void sendAir(Player player, World world, long[] packed, int count) {
        if (count <= 0 || !player.getWorld().equals(world)) {
            return;
        }
        if (air == null) {
            air = Material.AIR.createBlockData();
        }
        Map<Position, BlockData> batch = new HashMap<>(count * 2);
        for (int i = 0; i < count; i++) {
            long p = packed[i];
            batch.put(Position.block(RegionShape.unpackX(p), RegionShape.unpackY(p), RegionShape.unpackZ(p)), air);
        }
        player.sendMultiBlockChange(batch);
    }

    /**
     * Sends the <b>real</b> blocks (read from the world right now) at {@code packed[0..count)} to {@code player}, then
     * the block entity data of those in {@code tiles} (sign text would be blank otherwise).
     */
    public void sendReal(Player player, World world, long[] packed, int count, Set<Long> tiles) {
        if (count <= 0 || !player.getWorld().equals(world)) {
            return;
        }
        Map<Position, BlockData> batch = new HashMap<>(count * 2);
        for (int i = 0; i < count; i++) {
            long p = packed[i];
            int x = RegionShape.unpackX(p);
            int y = RegionShape.unpackY(p);
            int z = RegionShape.unpackZ(p);
            if (world.isChunkLoaded(x >> 4, z >> 4)) {
                batch.put(Position.block(x, y, z), world.getBlockData(x, y, z));
            }
        }
        if (batch.isEmpty()) {
            return;
        }
        player.sendMultiBlockChange(batch);
        if (tiles.isEmpty()) {
            return;
        }
        for (int i = 0; i < count; i++) {
            long p = packed[i];
            if (!tiles.contains(p)) {
                continue;
            }
            int x = RegionShape.unpackX(p);
            int y = RegionShape.unpackY(p);
            int z = RegionShape.unpackZ(p);
            if (world.isChunkLoaded(x >> 4, z >> 4)) {
                BlockState state = world.getBlockAt(x, y, z).getState();
                if (state instanceof TileState tile) {
                    player.sendBlockUpdate(new Location(world, x, y, z), tile);
                }
            }
        }
    }

    /** Whether the block at {@code x y z} has a block entity (needs {@code sendBlockUpdate} when revealed). */
    public boolean isTile(World world, int x, int y, int z) {
        Material type = world.getType(x, y, z);
        Boolean known = tileTypes.get(type);
        if (known == null) {
            known = world.getBlockAt(x, y, z).getState(false) instanceof TileState;
            tileTypes.put(type, known);
        }
        return known;
    }
}
