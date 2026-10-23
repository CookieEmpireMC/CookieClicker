package dev.casperrs.cookieClicker.house.design;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A parsed house design ({@code houses/<name>.yml}), in design-local block coordinates: x = column within a row
 * (west to east), y = layer (bottom to top), z = row within a layer (north to south). Pure data.
 *
 * @param origin  the design cell that lands on the house region's minimum corner
 * @param palette char -&gt; block data string (validated against the server when pasting)
 * @param grid    {@code grid[y][z][x]}; {@link HouseDesignParser#KEEP} = leave the world's block alone
 * @param points  named single points ({@code chimney}, {@code door}, {@code belt_out}, ...) as design-local
 *                {@code [x, y, z]}; {@code chimney} is the cell above the chimney top where the smoke starts
 * @param smoke   every smoke start cell ({@code points.smoke}, or the one {@code points.chimney}); may be empty
 * @param steam   every steam vent cell ({@code points.steam}); may be empty
 * @param signs   text for sign blocks, set on their front side after pasting
 * @param layout  the fixed plot layout ({@code /bakery plot setup}), or null (place-only designs like the cottage)
 */
public record HouseDesign(String name, int sizeX, int sizeY, int sizeZ, int[] origin, Map<Character, String> palette,
                          char[][][] grid, Map<String, int[]> points, List<int[]> smoke, List<int[]> steam,
                          List<SignText> signs, @Nullable HouseLayout layout) {

    /** Plain text (not MiniMessage) for the sign at design cell {@code at}, front side, up to 4 lines. */
    public record SignText(int[] at, List<String> lines) {
    }

    /** One block to set: design-local position and block data string. */
    public record Cell(int x, int y, int z, String blockData) {
    }

    /** Every non-keep cell, bottom layer first. */
    public List<Cell> cells() {
        List<Cell> out = new ArrayList<>();
        for (int y = 0; y < sizeY; y++) {
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++) {
                    char c = grid[y][z][x];
                    if (!HouseDesignParser.isKeep(c)) {
                        out.add(new Cell(x, y, z, palette.get(c)));
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
}
