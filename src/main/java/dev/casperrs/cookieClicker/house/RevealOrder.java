package dev.casperrs.cookieClicker.house;

import dev.casperrs.cookieClicker.plot.RegionShape;

import java.util.Arrays;
import java.util.Comparator;

/**
 * The order the build cinematic reveals a house in: layer by layer from the bottom up, and inside every layer outward
 * from the centre of the house (the centre of all blocks' XZ bounding box). Ties are broken by angle around the centre
 * (a gentle spiral), then by x and z, so the order is fully deterministic. Pure.
 *
 * @param positions   the blocks in reveal order, packed with {@link RegionShape#pack}
 * @param layerStarts index into {@code positions} where each layer (one y level) starts; ascending, first is 0
 */
public record RevealOrder(long[] positions, int[] layerStarts) {

    /** Orders {@code blocks} (packed positions, any order, no duplicates expected). */
    public static RevealOrder of(long[] blocks) {
        int n = blocks.length;
        if (n == 0) {
            return new RevealOrder(new long[0], new int[0]);
        }
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (long b : blocks) {
            int x = RegionShape.unpackX(b);
            int z = RegionShape.unpackZ(b);
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minZ = Math.min(minZ, z);
            maxZ = Math.max(maxZ, z);
        }
        // doubled coordinates keep the centre integral: 2 * centre = min + max
        long cx2 = (long) minX + maxX;
        long cz2 = (long) minZ + maxZ;
        Key[] keys = new Key[n];
        for (int i = 0; i < n; i++) {
            long b = blocks[i];
            int x = RegionShape.unpackX(b);
            int z = RegionShape.unpackZ(b);
            long dx = 2L * x - cx2;
            long dz = 2L * z - cz2;
            keys[i] = new Key(b, RegionShape.unpackY(b), dx * dx + dz * dz, Math.atan2(dz, dx), x, z);
        }
        Arrays.sort(keys, Comparator.comparingInt(Key::y)
                .thenComparingLong(Key::dist2)
                .thenComparingDouble(Key::angle)
                .thenComparingInt(Key::x)
                .thenComparingInt(Key::z));
        long[] out = new long[n];
        int[] starts = new int[n];
        int layers = 0;
        for (int i = 0; i < n; i++) {
            out[i] = keys[i].packed();
            if (i == 0 || keys[i].y() != keys[i - 1].y()) {
                starts[layers++] = i;
            }
        }
        return new RevealOrder(out, Arrays.copyOf(starts, layers));
    }

    public int size() {
        return positions.length;
    }

    public int layers() {
        return layerStarts.length;
    }

    /** First index after layer {@code layer}. */
    public int layerEnd(int layer) {
        return layer + 1 < layerStarts.length ? layerStarts[layer + 1] : positions.length;
    }

    private record Key(long packed, int y, long dist2, double angle, int x, int z) {
    }
}
