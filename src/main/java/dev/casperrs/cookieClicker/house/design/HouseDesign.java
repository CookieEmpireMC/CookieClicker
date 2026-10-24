package dev.casperrs.cookieClicker.house.design;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A parsed house design ({@code houses/<name>.yml}), in design-local block coordinates: x = column within a row
 * (west to east), y = layer (bottom to top), z = row within a layer (north to south). Pure data. {@code grid[layer][z][x]}
 * ({@link HouseDesignParser#KEEP} = leave the world's block); origin lands on the house region's minimum corner;
 * {@code chimney} is the cell above the chimney top; smoke/steam may be empty; layout is null for place-only designs.
 *
 * <p>Ground: the first {@code groundDepth} layers (0..{@link HouseDesignParser#MAX_GROUND_DEPTH}) lie <b>below</b>
 * the house region's minimum y. Every design y used by this record (cells, points, signs, origin, {@link #toWorld})
 * counts from the first layer above the ground, so ground cells have y {@code -groundDepth..-1}; {@code sizeY}
 * counts the ground layers too, and {@code grid} is indexed by file layer ({@code layer = y + groundDepth}).
 */
public record HouseDesign(String name, int sizeX, int sizeY, int sizeZ, int[] origin, Map<Character, String> palette,
                          char[][][] grid, Map<String, int[]> points, List<int[]> smoke, List<int[]> steam,
                          List<SignText> signs, @Nullable HouseLayout layout, int groundDepth) {

    /** A design without ground layers (the format before ground-depth). */
    public HouseDesign(String name, int sizeX, int sizeY, int sizeZ, int[] origin, Map<Character, String> palette,
                       char[][][] grid, Map<String, int[]> points, List<int[]> smoke, List<int[]> steam,
                       List<SignText> signs, @Nullable HouseLayout layout) {
        this(name, sizeX, sizeY, sizeZ, origin, palette, grid, points, smoke, steam, signs, layout, 0);
    }

    /** Plain text (not MiniMessage) for the sign at design cell {@code at}, front side, up to 4 lines. */
    public record SignText(int[] at, List<String> lines) {
    }

    /** One block to set: design-local position and block data string. */
    public record Cell(int x, int y, int z, String blockData) {
    }

    /** Every non-keep cell, bottom layer (the deepest ground layer) first; y is the design y (ground negative). */
    public List<Cell> cells() {
        List<Cell> out = new ArrayList<>();
        for (int layer = 0; layer < sizeY; layer++) {
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++) {
                    char c = grid[layer][z][x];
                    if (!HouseDesignParser.isKeep(c)) {
                        out.add(new Cell(x, layer - groundDepth, z, palette.get(c)));
                    }
                }
            }
        }
        return out;
    }

    /**
     * World position of a design cell when the design is pasted with its {@link #origin} on {@code regionMin}.
     * Returns {@code [x, y, z]}.
     */
    public int[] toWorld(int[] regionMin, int x, int y, int z) {
        return new int[] {regionMin[0] + x - origin[0], regionMin[1] + y - origin[1], regionMin[2] + z - origin[2]};
    }

    /** World minimum corner of the whole design box, ground layers included (what a backup must cover). */
    public int[] corner(int[] regionMin) {
        return toWorld(regionMin, 0, -groundDepth, 0);
    }

    /** {@code [sizeX, sizeY, sizeZ]}: the whole design box, ground layers included. */
    public int[] size() {
        return new int[] {sizeX, sizeY, sizeZ};
    }

    /** Layers from the house region's minimum y up (the house box height, ground excluded). */
    public int houseHeight() {
        return sizeY - groundDepth;
    }
}
