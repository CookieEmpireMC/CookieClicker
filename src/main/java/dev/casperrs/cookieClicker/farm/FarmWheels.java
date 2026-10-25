package dev.casperrs.cookieClicker.farm;

import dev.casperrs.cookieClicker.plot.PlotService;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;
import java.util.function.Predicate;

/**
 * The mills' waterwheels: one shared, non-persistent item display per {@code wheel} point, seen only by players
 * with our resource pack (everyone else sees the mill's block wheel, and the model would be a missing-model cube for
 * them). A wheel exists while such a player is within {@code farm.wheel.view-range} and turns at
 * {@code farm.wheel.rpm} by display interpolation: every {@value #STEP_TICKS} ticks one new rotation, interpolated
 * over the same {@value #STEP_TICKS} ticks. One shared task, running only while some farm has a wheel. Main thread.
 */
public final class FarmWheels {

    public static final Key MODEL = Key.key("cookieclicker", "waterwheel");
    private static final long STEP_TICKS = 20;
    private static final double TICKS_PER_MINUTE = 20 * 60;
    /** Display view range 1.0 = 64 blocks (scaled by the client's entity distance). */
    private static final float BLOCKS_PER_VIEW_RANGE = 64f;
    private static final float TWO_PI = (float) (Math.PI * 2);

    private final Plugin plugin;
    private final FarmRegistry farms;
    private final Predicate<Player> hasPack;
    private final DoubleSupplier rpm;
    private final IntSupplier viewRange;
    private List<Wheel> wheels = List.of();
    private @Nullable BukkitTask task;

    /** One wheel point and its display while someone is near. */
    private static final class Wheel {
        final World world;
        final Farm.Wheel at;
        @Nullable ItemDisplay display;
        float angle;
        final Set<UUID> shown = new HashSet<>();

        Wheel(World world, Farm.Wheel at) {
            this.world = world;
            this.at = at;
        }
    }

    public FarmWheels(Plugin plugin, FarmRegistry farms, Predicate<Player> hasPack, DoubleSupplier rpm,
                      IntSupplier viewRange) {
        this.plugin = plugin;
        this.farms = farms;
        this.hasPack = hasPack;
        this.rpm = rpm;
        this.viewRange = viewRange;
    }

    /** Re-reads the wheel points (farm placed or undone, reload) and starts or stops the task. */
    public void restart() {
        stopAll();
        List<Wheel> found = new ArrayList<>();
        for (Farm farm : farms.all()) {
            World world = PlotService.worldByKey(farm.worldKey()).orElse(null);
            if (world == null) {
                continue;
            }
            for (Farm.FarmPlace place : farm.places().values()) {
                if (place.wheel() != null) {
                    found.add(new Wheel(world, place.wheel()));
                }
            }
        }
        wheels = List.copyOf(found);
        if (!wheels.isEmpty()) {
            task = Bukkit.getScheduler().runTaskTimer(plugin, this::check, STEP_TICKS, STEP_TICKS);
        }
    }

    private void check() {
        double range = viewRange.getAsInt();
        double r2 = range * range;
        float step = (float) (rpm.getAsDouble() * TWO_PI * STEP_TICKS / TICKS_PER_MINUTE);
        for (Wheel w : wheels) {
            List<Player> near = new ArrayList<>();
            for (Player p : w.world.getPlayers()) {
                if (hasPack.test(p) && distanceSquared(p.getLocation(), w.at) <= r2) {
                    near.add(p);
                }
            }
            if (near.isEmpty()) {
                despawn(w);
                continue;
            }
            boolean fresh = false;
            if (w.display == null || !w.display.isValid()) {
                w.shown.clear();
                w.display = spawn(w, (float) range);
                fresh = true;
                if (w.display == null) {
                    continue;
                }
            }
            for (Player p : near) {
                if (w.shown.add(p.getUniqueId())) {
                    p.showEntity(plugin, w.display);
                }
            }
            w.shown.removeIf(id -> Bukkit.getPlayer(id) == null);
            if (!fresh && step != 0) {
                w.angle = (w.angle + step) % TWO_PI;
                w.display.setInterpolationDelay(0);
                w.display.setInterpolationDuration((int) STEP_TICKS);
                w.display.setTransformation(turned(w.angle));
            }
        }
    }

    /** Null while the wheel's chunk is not loaded. */
    private @Nullable ItemDisplay spawn(Wheel w, float range) {
        if (!w.world.isChunkLoaded((int) Math.floor(w.at.at().x()) >> 4, (int) Math.floor(w.at.at().z()) >> 4)) {
            return null;
        }
        ItemStack stack = ItemStack.of(Material.PAPER);
        stack.setData(DataComponentTypes.ITEM_MODEL, MODEL);
        // the model turns about its local x axis; a z axle turns the whole display a quarter
        float yaw = w.at.axis() == PlacePoints.Axis.Z ? 90f : 0f;
        Location loc = new Location(w.world, w.at.at().x(), w.at.at().y(), w.at.at().z(), yaw, 0);
        return w.world.spawn(loc, ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.setVisibleByDefault(false);
            d.setViewRange(range / BLOCKS_PER_VIEW_RANGE);
            d.setItemStack(stack);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setTransformation(turned(w.angle));
        });
    }

    private static Transformation turned(float angle) {
        return new Transformation(new Vector3f(), new Quaternionf().rotateX(angle), new Vector3f(1, 1, 1),
                new Quaternionf());
    }

    private static double distanceSquared(Location loc, Farm.Wheel w) {
        double dx = loc.getX() - w.at().x();
        double dy = loc.getY() - w.at().y();
        double dz = loc.getZ() - w.at().z();
        return dx * dx + dy * dy + dz * dz;
    }

    private static void despawn(Wheel w) {
        if (w.display != null) {
            w.display.remove();
            w.display = null;
        }
        w.shown.clear();
    }

    /** Reload and disable: cancels the task and removes every wheel. */
    public void stopAll() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        wheels.forEach(FarmWheels::despawn);
    }
}
