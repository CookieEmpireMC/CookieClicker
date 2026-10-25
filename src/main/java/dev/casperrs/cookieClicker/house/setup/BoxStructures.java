package dev.casperrs.cookieClicker.house.setup;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.structure.Structure;
import org.bukkit.util.BlockVector;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Random;

/** Structure snapshots of a box, with tile contents (chests, signs): the exact half of a backup. Main thread. */
final class BoxStructures {

    private BoxStructures() {
    }

    static byte[] snapshot(World world, int[] corner, int[] size) throws IOException {
        Structure snapshot = Bukkit.getStructureManager().createStructure();
        snapshot.fill(new Location(world, corner[0], corner[1], corner[2]),
                new BlockVector(size[0], size[1], size[2]), false);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Bukkit.getStructureManager().saveStructure(out, snapshot);
        return out.toByteArray();
    }

    /** Main thread: copies the box into a structure (no serialisation). */
    static Structure capture(World world, int[] corner, int[] size) {
        Structure snapshot = Bukkit.getStructureManager().createStructure();
        snapshot.fill(new Location(world, corner[0], corner[1], corner[2]),
                new BlockVector(size[0], size[1], size[2]), false);
        return snapshot;
    }

    /** Any thread: a captured structure as compressed NBT. It is a detached copy, so no world access happens. */
    static byte[] serialize(Structure structure) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Bukkit.getStructureManager().saveStructure(out, structure);
        return out.toByteArray();
    }

    /** Any thread: compressed NBT back to a structure, ready for {@link #place(World, int[], Structure)}. */
    static Structure deserialize(byte[] nbt) throws IOException {
        return Bukkit.getStructureManager().loadStructure(new ByteArrayInputStream(nbt));
    }

    /** Main thread. */
    static void place(World world, int[] corner, Structure structure) {
        structure.place(new Location(world, corner[0], corner[1], corner[2]), false, StructureRotation.NONE,
                Mirror.NONE, 0, 1f, new Random());
    }

    static void place(World world, int[] corner, byte[] nbt) throws IOException {
        Structure structure = Bukkit.getStructureManager().loadStructure(new ByteArrayInputStream(nbt));
        structure.place(new Location(world, corner[0], corner[1], corner[2]), false, StructureRotation.NONE,
                Mirror.NONE, 0, 1f, new Random());
    }
}
