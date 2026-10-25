package dev.casperrs.cookieClicker.house.setup;

import dev.casperrs.cookieClicker.farm.Farm;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Plugin chunk tickets over a box, so its chunks stay loaded through a paste spread over many ticks. Only chunks that
 * were already loaded are held (callers check that first), so holding never loads anything. Main thread.
 */
final class ChunkHold {

    private final Plugin plugin;
    private @Nullable World world;
    private final List<long[]> held = new ArrayList<>();

    ChunkHold(Plugin plugin) {
        this.plugin = plugin;
    }

    /** Releases what was held before, then holds every chunk of {@code box}. */
    void hold(World w, Farm.Box box) {
        release();
        world = w;
        for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
            for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
                if (w.addPluginChunkTicket(cx, cz, plugin)) {
                    held.add(new long[] {cx, cz});
                }
            }
        }
    }

    void release() {
        if (world != null) {
            for (long[] c : held) {
                world.removePluginChunkTicket((int) c[0], (int) c[1], plugin);
            }
        }
        held.clear();
        world = null;
    }
}
