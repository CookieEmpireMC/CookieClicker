package dev.casperrs.cookieClicker.worldguard;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.StringFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedPolygonalRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import dev.casperrs.cookieClicker.plot.PlotRegions;
import dev.casperrs.cookieClicker.plot.Presence;
import dev.casperrs.cookieClicker.plot.RegionShape;
import org.bukkit.Location;
import org.bukkit.World;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

/**
 * The only class that imports WorldGuard/WorldEdit (docs/integrations.md, WorldGuard section). Registers our two
 * {@link StringFlag}s in {@code onLoad} and answers {@link PlotRegions} queries through WorldGuard's
 * {@code RegionContainer} / {@code RegionQuery}. Never keeps {@code ProtectedRegion} objects. Read-only: the plugin
 * never creates regions or sets flags. Main thread only.
 */
public final class WorldGuardHook implements PlotRegions {

    public static final String PLOT_FLAG_NAME = "bakery-plot";
    public static final String HOUSE_FLAG_NAME = "bakery-house";
    /** The oldest WorldGuard release confirmed for Paper 26.3 (docs/research/worldguard-plot.md). */
    public static final String MIN_VERSION = "7.0.20";

    private static @Nullable StringFlag plotFlag;
    private static @Nullable StringFlag houseFlag;

    private final Logger log;
    private final Set<String> warned = new HashSet<>();

    private WorldGuardHook(Logger log) {
        this.log = log;
    }

    /** Compares the leading dotted numbers ("7.0.20-SNAPSHOT;abc" -> 7, 0, 20). Unparseable = not older. */
    public static boolean olderThan(String version, String min) {
        String[] a = version.split("[^0-9.]", 2)[0].split("\\.");
        String[] b = min.split("\\.");
        for (int i = 0; i < b.length; i++) {
            int x;
            try {
                x = i < a.length ? Integer.parseInt(a[i]) : 0;
            } catch (NumberFormatException e) {
                return false;
            }
            int y = Integer.parseInt(b[i]);
            if (x != y) {
                return x < y;
            }
        }
        return false;
    }

    /**
     * Registers {@code bakery-plot} and {@code bakery-house}. Must run in {@code onLoad} (WorldGuard locks its flag
     * registry when it enables). A flag of the same name and type that is already there (a second {@code onLoad}) is
     * reused; one of another type is a hard conflict.
     */
    public static void registerFlags() {
        FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();
        plotFlag = register(registry, PLOT_FLAG_NAME);
        houseFlag = register(registry, HOUSE_FLAG_NAME);
    }

    private static StringFlag register(FlagRegistry registry, String name) {
        StringFlag flag = new StringFlag(name);
        try {
            registry.register(flag);
            return flag;
        } catch (FlagConflictException e) {
            Flag<?> existing = registry.get(name);
            if (existing instanceof StringFlag sf) {
                return sf;
            }
            throw new IllegalStateException("WorldGuard flag '" + name + "' is already registered by another plugin "
                    + "with a different type (" + (existing == null ? "?" : existing.getClass().getSimpleName()) + ")", e);
        }
    }

    /** The hook, or empty when the flags were not registered in {@code onLoad} (then plots can't work). */
    public static Optional<WorldGuardHook> create(Logger log) {
        return plotFlag == null || houseFlag == null ? Optional.empty() : Optional.of(new WorldGuardHook(log));
    }

    @Override
    public Presence at(World world, int x, int y, int z) {
        ApplicableRegionSet set = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery()
                .getApplicableRegions(BukkitAdapter.adapt(new Location(world, x, y, z)));
        if (set.isVirtual()) { // protection disabled or region data failed to load in this world
            warnOnce("virtual:" + world.getKey(), "WorldGuard has no region data for " + world.getKey()
                    + " (regions disabled or failed to load); no bakery plots there");
            return Presence.NONE;
        }
        String plot = null;
        String house = null;
        int plotPriority = Integer.MIN_VALUE;
        int housePriority = Integer.MIN_VALUE;
        for (ProtectedRegion r : set) {
            String p = value(r, plotFlag);
            if (p != null && r.getPriority() > plotPriority) {
                plot = p;
                plotPriority = r.getPriority();
            }
            String h = value(r, houseFlag);
            if (h != null && r.getPriority() > housePriority) {
                house = h;
                housePriority = r.getPriority();
            }
        }
        return plot == null && house == null ? Presence.NONE : new Presence(plot, house);
    }

    @Override
    public List<RegionInfo> regions(World world) {
        RegionManager rm = manager(world);
        if (rm == null) {
            return List.of();
        }
        List<RegionInfo> out = new ArrayList<>();
        for (ProtectedRegion r : rm.getRegions().values()) {
            out.add(new RegionInfo(r.getId().toLowerCase(Locale.ROOT), r.getPriority(), value(r, plotFlag),
                    value(r, houseFlag)));
        }
        return out;
    }

    @Override
    public Optional<RegionShape> shape(World world, String regionId) {
        RegionManager rm = manager(world);
        ProtectedRegion r = rm == null ? null : rm.getRegion(regionId);
        if (r == null || !r.isPhysicalArea()) {
            return Optional.empty();
        }
        BlockVector3 min = r.getMinimumPoint();
        BlockVector3 max = r.getMaximumPoint();
        List<int[]> points = new ArrayList<>();
        for (BlockVector2 v : r.getPoints()) {
            points.add(new int[] {v.x(), v.z()});
        }
        return Optional.of(new RegionShape(r.getId().toLowerCase(Locale.ROOT), r instanceof ProtectedPolygonalRegion,
                min.x(), min.y(), min.z(), max.x(), max.y(), max.z(), List.copyOf(points), r::contains));
    }

    private @Nullable RegionManager manager(World world) {
        RegionManager rm = WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(world));
        if (rm == null) {
            warnOnce("manager:" + world.getKey(), "WorldGuard has no region manager for " + world.getKey()
                    + " (regions disabled there?); no bakery plots in that world");
        }
        return rm;
    }

    private static @Nullable String value(ProtectedRegion r, @Nullable StringFlag flag) {
        String v = flag == null ? null : r.getFlag(flag);
        return v == null || v.isBlank() ? null : v.trim().toLowerCase(Locale.ROOT);
    }

    private void warnOnce(String key, String message) {
        if (warned.add(key)) {
            log.warning(message);
        }
    }
}
