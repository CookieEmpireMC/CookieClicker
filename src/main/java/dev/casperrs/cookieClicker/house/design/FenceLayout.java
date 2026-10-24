package dev.casperrs.cookieClicker.house.design;

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
 * on the border column nearest {@code gateX, gateZ}, with a pillar on each side. Nothing lands inside the house region.
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
     * One block to set; level indexes the role's column from the bottom (0 for rails and toppers).
     */
    public record Piece(int x, int y, int z, Role role, int level, Set<Face> faces) {

        /** The style's block data for this piece. */
        public String blockData(FenceStyle style) {
            return switch (role) {
                case RAIL -> style.rail();
                case POST -> style.post().get(level);
                case CORNER -> style.corner().get(level);
                case TOPPER -> java.util.Objects.requireNonNull(style.topper());
                case GATE_LEFT -> style.gateLeft().get(level);
                case GATE_RIGHT -> style.gateRight().get(level);
                case ARCH -> style.arch().get(level);
            };
        }
    }

    /** entrance: the gate opening's centre column at ground height ({x, y, z}), or null without any border. */
    public record Plan(List<Piece> pieces, int @Nullable [] entrance) {
    }

    private FenceLayout() {
    }

    private static final int[][] DIRS = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}}; // N, E, S, W (same order as Face)

    public static Plan plan(int minX, int minZ, int maxX, int maxZ, Area area, Volume house, Ground ground,
                            FenceStyle style, int gateX, int gateZ) {
        Set<Long> border = border(minX, minZ, maxX, maxZ, area);
        if (border.isEmpty()) {
            return new Plan(List.of(), null);
        }
        long centre = nearest(border, gateX, gateZ);
        List<Long> opening = walk(border, centre, style.gateWidth());
        Set<Long> openSet = new HashSet<>(opening);
        Map<Long, Role> pillars = pillars(area, border, opening, openSet, centre);
        Map<Long, Integer> heights = new HashMap<>();
        for (long k : border) {
            int y = ground.at(kx(k), kz(k));
            if (y != SKIP) {
                heights.put(k, y);
            }
        }
        Integer gateY = heights.get(centre); // the whole gate stands on the entrance's ground, so the lintel is level
        if (gateY != null) {
            opening.forEach(k -> heights.put(k, gateY));
            pillars.keySet().forEach(k -> heights.put(k, gateY));
        }
        Map<Long, Role> fence = roles(border, openSet, pillars, heights, minX, minZ, style);

        List<Piece> pieces = new ArrayList<>();
        fencePieces(pieces, house, fence, pillars, heights, border, minX, minZ, style);
        for (Map.Entry<Long, Role> e : pillars.entrySet()) {
            Integer y = heights.get(e.getKey());
            if (y != null) {
                column(pieces, house, e.getKey(), y, e.getValue(), columnHeight(e.getValue(), style));
            }
        }
        for (long k : opening) {
            Integer y = heights.get(k);
            if (y != null) {
                column(pieces, house, k, y, Role.ARCH, style.arch().size());
            }
        }
        return new Plan(List.copyOf(pieces), gateY == null ? null : new int[] {kx(centre), gateY, kz(centre)});
    }

    /** Border columns in box order. */
    private static Set<Long> border(int minX, int minZ, int maxX, int maxZ, Area area) {
        Set<Long> border = new LinkedHashSet<>();
        for (int z = minZ; z <= maxZ; z++) {
            for (int x = minX; x <= maxX; x++) {
                if (area.contains(x, z) && isEdge(area, x, z)) {
                    border.add(key(x, z));
                }
            }
        }
        return border;
    }

    private static long nearest(Set<Long> border, int gateX, int gateZ) {
        long centre = 0;
        long best = Long.MAX_VALUE;
        for (long k : border) {
            long dx = kx(k) - gateX;
            long dz = kz(k) - gateZ;
            long d = dx * dx + dz * dz;
            if (d < best) {
                best = d;
                centre = k;
            }
        }
        return centre;
    }

    /** The border columns right beside the opening, each the left or right gate pillar (seen from outside). */
    private static Map<Long, Role> pillars(Area area, Set<Long> border, List<Long> opening, Set<Long> openSet,
                                           long centre) {
        int cx = kx(centre);
        int cz = kz(centre);
        int[] out = outward(area, cx, cz);
        int leftX = -out[1];  // left as seen from outside, looking in (-out): (dz, -dx) of the inward step
        int leftZ = out[0];
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
        return pillars;
    }

    /** Corners where the ring turns, posts every n along a side (not next to a corner or gate pillar), else rails. */
    private static Map<Long, Role> roles(Set<Long> border, Set<Long> openSet, Map<Long, Role> pillars,
                                         Map<Long, Integer> heights, int minX, int minZ, FenceStyle style) {
        Map<Long, Role> fence = new HashMap<>();
        for (long k : border) {
            if (!openSet.contains(k) && !pillars.containsKey(k) && heights.containsKey(k)) {
                fence.put(k, role(border, k, minX, minZ, style));
            }
        }
        for (Map.Entry<Long, Role> e : fence.entrySet()) {
            if (e.getValue() == Role.POST && nextToCornerOrPillar(e.getKey(), fence, pillars)) {
                e.setValue(Role.RAIL);
            }
        }
        return fence;
    }

    private static void fencePieces(List<Piece> pieces, Volume house, Map<Long, Role> fence, Map<Long, Role> pillars,
                                    Map<Long, Integer> heights, Set<Long> border, int minX, int minZ,
                                    FenceStyle style) {
        // column heights (for rail connections): rails 1, posts/corners/pillars their column
        Map<Long, Integer> tall = new HashMap<>();
        fence.forEach((k, r) -> tall.put(k, columnHeight(r, style)));
        pillars.forEach((k, r) -> tall.put(k, columnHeight(r, style)));
        for (Map.Entry<Long, Role> e : fence.entrySet()) {
            long k = e.getKey();
            int x = kx(k);
            int z = kz(k);
            int y = heights.get(k);
            Role r = e.getValue();
            if (r == Role.RAIL) {
                add(pieces, house, new Piece(x, y, z, r, 0, Collections.unmodifiableSet(faces(x, y, z, heights, tall))));
                continue;
            }
            int h = columnHeight(r, style);
            column(pieces, house, k, y, r, h);
            if (style.topper() != null && topped(r, x, z, minX, minZ, border, style)) {
                add(pieces, house, new Piece(x, y + h, z, Role.TOPPER, 0, Set.of()));
            }
        }
    }

    /** The neighbours a rail at {@code x y z} connects to: those whose column covers its height. */
    private static Set<Face> faces(int x, int y, int z, Map<Long, Integer> heights, Map<Long, Integer> tall) {
        Set<Face> faces = EnumSet.noneOf(Face.class);
        for (int i = 0; i < DIRS.length; i++) {
            long n = key(x + DIRS[i][0], z + DIRS[i][1]);
            Integer ny = heights.get(n);
            Integer nh = tall.get(n);
            if (ny != null && nh != null && y >= ny && y < ny + nh) {
                faces.add(Face.values()[i]);
            }
        }
        return faces;
    }

    private static void column(List<Piece> pieces, Volume house, long k, int y, Role role, int height) {
        for (int level = 0; level < height; level++) {
            add(pieces, house, new Piece(kx(k), y + level, kz(k), role, level, Set.of()));
        }
    }

    private static int columnHeight(Role r, FenceStyle style) {
        return switch (r) {
            case POST -> style.post().size();
            case CORNER -> style.corner().size();
            case GATE_LEFT -> style.gateLeft().size();
            case GATE_RIGHT -> style.gateRight().size();
            default -> 1;
        };
    }

    private static boolean nextToCornerOrPillar(long k, Map<Long, Role> fence, Map<Long, Role> pillars) {
        for (int[] d : DIRS) {
            long n = key(kx(k) + d[0], kz(k) + d[1]);
            if (pillars.containsKey(n) || fence.get(n) == Role.CORNER) {
                return true;
            }
        }
        return false;
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

    /** Corner when the border neighbours aren't one straight line; posts by the style's spacing along a side. */
    private static Role role(Set<Long> border, long k, int minX, int minZ, FenceStyle style) {
        int x = kx(k);
        int z = kz(k);
        boolean n = border.contains(key(x, z - 1));
        boolean s = border.contains(key(x, z + 1));
        boolean e = border.contains(key(x + 1, z));
        boolean w = border.contains(key(x - 1, z));
        boolean eastWest = e && w && !n && !s;
        boolean northSouth = n && s && !e && !w;
        if (eastWest) {
            return style.postAt(x - minX) ? Role.POST : Role.RAIL;
        }
        if (northSouth) {
            return style.postAt(z - minZ) ? Role.POST : Role.RAIL;
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
        boolean eastWest = border.contains(key(x + 1, z)) && border.contains(key(x - 1, z));
        return style.toppedPost(style.postIndex(eastWest ? x - minX : z - minZ));
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
