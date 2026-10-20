package dev.casperrs.cookieClicker.house;

import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Where the plot fence goes (pure, so it is testable without a server). The fence runs along the <b>inside</b> edge
 * of the plot region: every column that is inside and has a 4-neighbour outside (works for polygons too), at the
 * ground height of that column. The gate opening ({@link FenceStyle#gateWidth()} columns along the border) is centred
 * on the border column nearest the admin, with a pillar on each side. Nothing lands inside the house region.
 */
public final class FenceLayout {

    /** Column test in x/z (the region's 2D outline). */
    @FunctionalInterface
    public interface Area {
        boolean contains(int x, int z);
    }

    /** Block test (the house region). */
    @FunctionalInterface
    public interface Volume {
        boolean contains(int x, int y, int z);
    }

    /** The y the fence stands on in a column (the first free block above the surface), or {@link #SKIP}. */
    @FunctionalInterface
    public interface Ground {
        int at(int x, int z);
    }

    public static final int SKIP = Integer.MIN_VALUE;

    public enum Face { NORTH, EAST, SOUTH, WEST }

    public enum Role { RAIL, POST, CORNER, TOPPER, GATE_LEFT, GATE_RIGHT, ARCH }

    /**
     * One block to set.
     *
     * @param archLevel for {@link Role#ARCH}: index into {@link FenceStyle#arch()}; else -1
     * @param faces     for fence pieces: the neighbours it connects to
     */
    public record Piece(int x, int y, int z, Role role, int archLevel, Set<Face> faces) {

        /** The style's block data for this piece. */
        public String blockData(FenceStyle style) {
            return switch (role) {
                case RAIL -> style.rail();
                case POST -> style.post();
                case CORNER -> style.corner();
                case TOPPER -> java.util.Objects.requireNonNull(style.topper());
                case GATE_LEFT -> style.gateLeft();
                case GATE_RIGHT -> style.gateRight();
                case ARCH -> style.arch().get(archLevel);
            };
        }
    }

    /**
     * @param pieces   every block to set
     * @param entrance centre column of the gate opening at its ground height ({x, y, z}), or null when there is no
     *                 border at all
     */
    public record Plan(List<Piece> pieces, int @Nullable [] entrance) {
    }

    private FenceLayout() {
    }

    private static final int[][] DIRS = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}}; // N, E, S, W (same order as Face)

    public static Plan plan(int minX, int minZ, int maxX, int maxZ, Area area, Volume house, Ground ground,
                            FenceStyle style, int adminX, int adminZ) {
        // border columns, in box order
        Set<Long> border = new LinkedHashSet<>();
        for (int z = minZ; z <= maxZ; z++) {
            for (int x = minX; x <= maxX; x++) {
                if (area.contains(x, z) && isEdge(area, x, z)) {
                    border.add(key(x, z));
                }
            }
        }
        if (border.isEmpty()) {
            return new Plan(List.of(), null);
        }

        // gate: nearest border column to the admin, then the closest border columns along the border
        long centre = 0;
        long best = Long.MAX_VALUE;
        for (long k : border) {
            long dx = kx(k) - adminX;
            long dz = kz(k) - adminZ;
            long d = dx * dx + dz * dz;
            if (d < best) {
                best = d;
                centre = k;
            }
        }
        List<Long> opening = walk(border, centre, style.gateWidth());
        Set<Long> openSet = new HashSet<>(opening);
        int cx = kx(centre);
        int cz = kz(centre);
        int[] out = outward(area, cx, cz);
        int leftX = out[1];   // left when looking outward (x east, z south): (dz, -dx)
        int leftZ = -out[0];
        Map<Long, Role> pillars = new HashMap<>();
        for (long k : opening) {
            for (int[] d : DIRS) {
                long n = key(kx(k) + d[0], kz(k) + d[1]);
                if (border.contains(n) && !openSet.contains(n) && !pillars.containsKey(n)) {
                    int dot = (kx(n) - cx) * leftX + (kz(n) - cz) * leftZ;
                    pillars.put(n, dot >= 0 ? Role.GATE_LEFT : Role.GATE_RIGHT);
                }
            }
        }

        Map<Long, Integer> heights = new HashMap<>();
        for (long k : border) {
            int y = ground.at(kx(k), kz(k));
            if (y != SKIP) {
                heights.put(k, y);
            }
        }

        // fence pieces (rails, posts, corners), keyed by column; connections need the full set first
        Map<Long, Role> fence = new HashMap<>();
        for (long k : border) {
            if (openSet.contains(k) || pillars.containsKey(k) || !heights.containsKey(k)) {
                continue;
            }
            fence.put(k, role(border, k, minX, minZ, style.postEvery()));
        }

        List<Piece> pieces = new ArrayList<>();
        for (Map.Entry<Long, Role> e : fence.entrySet()) {
            long k = e.getKey();
            int x = kx(k);
            int z = kz(k);
            int y = heights.get(k);
            Set<Face> faces = EnumSet.noneOf(Face.class);
            for (int i = 0; i < DIRS.length; i++) {
                long n = key(x + DIRS[i][0], z + DIRS[i][1]);
                Integer ny = heights.get(n);
                boolean fenceThere = fence.containsKey(n) && ny != null && ny == y;
                boolean pillarThere = pillars.containsKey(n) && ny != null && y >= ny && y < ny + style.pillarHeight();
                if (fenceThere || pillarThere) {
                    faces.add(Face.values()[i]);
                }
            }
            add(pieces, house, new Piece(x, y, z, e.getValue(), -1, Collections.unmodifiableSet(faces)));
            if (style.topper() != null && style.topperEvery() > 0 && topped(e.getValue(), x, z, minX, minZ,
                    border, style)) {
                add(pieces, house, new Piece(x, y + 1, z, Role.TOPPER, -1, Set.of()));
            }
        }
        for (Map.Entry<Long, Role> e : pillars.entrySet()) {
            Integer y = heights.get(e.getKey());
            if (y == null) {
                continue;
            }
            for (int h = 0; h < style.pillarHeight(); h++) {
                add(pieces, house, new Piece(kx(e.getKey()), y + h, kz(e.getKey()), e.getValue(), -1, Set.of()));
            }
        }
        for (long k : opening) {
            Integer y = heights.get(k);
            if (y == null) {
                continue;
            }
            for (int h = 0; h < style.arch().size(); h++) {
                add(pieces, house, new Piece(kx(k), y + h, kz(k), Role.ARCH, h, Set.of()));
            }
        }
        Integer cy = heights.get(centre);
        return new Plan(List.copyOf(pieces), cy == null ? null : new int[] {cx, cy, cz});
    }

    private static void add(List<Piece> pieces, Volume house, Piece p) {
        if (!house.contains(p.x(), p.y(), p.z())) {
            pieces.add(p);
        }
    }

    private static boolean isEdge(Area area, int x, int z) {
        for (int[] d : DIRS) {
            if (!area.contains(x + d[0], z + d[1])) {
                return true;
            }
        }
        return false;
    }

    /** Unit step out of the plot from a border column ({dx, dz}); the first outside neighbour. */
    private static int[] outward(Area area, int x, int z) {
        for (int[] d : DIRS) {
            if (!area.contains(x + d[0], z + d[1])) {
                return d;
            }
        }
        return DIRS[0];
    }

    /** Up to {@code width} border columns closest to {@code start} along the border (breadth first). */
    private static List<Long> walk(Set<Long> border, long start, int width) {
        List<Long> out = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        ArrayDeque<Long> queue = new ArrayDeque<>();
        queue.add(start);
        seen.add(start);
        while (!queue.isEmpty() && out.size() < width) {
            long k = queue.poll();
            out.add(k);
            for (int[] d : DIRS) {
                long n = key(kx(k) + d[0], kz(k) + d[1]);
                if (border.contains(n) && seen.add(n)) {
                    queue.add(n);
                }
            }
        }
        return out;
    }

    /** Corner when the border neighbours aren't one straight line; posts every n along a straight side. */
    private static Role role(Set<Long> border, long k, int minX, int minZ, int every) {
        int x = kx(k);
        int z = kz(k);
        boolean n = border.contains(key(x, z - 1));
        boolean s = border.contains(key(x, z + 1));
        boolean e = border.contains(key(x + 1, z));
        boolean w = border.contains(key(x - 1, z));
        boolean eastWest = e && w && !n && !s;
        boolean northSouth = n && s && !e && !w;
        if (eastWest) {
            return Math.floorMod(x - minX, every) == 0 ? Role.POST : Role.RAIL;
        }
        if (northSouth) {
            return Math.floorMod(z - minZ, every) == 0 ? Role.POST : Role.RAIL;
        }
        return Role.CORNER;
    }

    private static boolean topped(Role role, int x, int z, int minX, int minZ, Set<Long> border, FenceStyle style) {
        if (role == Role.CORNER) {
            return true;
        }
        if (role != Role.POST) {
            return false;
        }
        int step = style.postEvery() * style.topperEvery();
        boolean eastWest = border.contains(key(x + 1, z)) && border.contains(key(x - 1, z));
        return Math.floorMod(eastWest ? x - minX : z - minZ, step) == 0;
    }

    static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    static int kx(long k) {
        return (int) (k >> 32);
    }

    static int kz(long k) {
        return (int) k;
    }
}
