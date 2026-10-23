package dev.casperrs.cookieClicker.house.cinematic;

import com.destroystokyo.paper.event.player.PlayerStopSpectatingEntityEvent;
import dev.casperrs.cookieClicker.plot.PlotService;
import dev.casperrs.cookieClicker.view.StationViews;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * The raised camera of the build cinematic: the player is put in spectator mode and locked to a camera entity (an
 * empty item display only they can see) with {@link Player#setSpectatorTarget}. Sneaking out is refused, and
 * {@link CinematicGuards} refuses spectator teleports. The original game mode and the return spot are stored in the
 * player's PDC ({@link CameraReturn}) <b>before</b> the game mode changes, so a crash, quit or disable mid-build is
 * undone ({@link #exit} on every abort path, {@link #onJoin} for leftovers). The only class that changes game modes.
 * Main thread; {@link HouseCinematics} drives it.
 *
 * <p>Order on enter: game mode, teleport to the camera point, spawn + show the camera entity (now within tracking
 * range), and one tick later {@code setSpectatorTarget}, so the client knows the entity when the camera packet
 * arrives.
 */
public final class CinematicCamera implements Listener {

    /**
     * A player's eye height: the camera point is a feet cell like the viewpoint (design/conveyor-belt.md), and the
     * camera entity (an item display, eye height 0) sits at the eye.
     */
    public static final double EYE_HEIGHT = 1.62;

    private final Plugin plugin;
    private final NamespacedKey key;
    /** Shared with {@link HouseCinematics}: our own teleports, which must not abort anything. */
    private final Set<UUID> selfTeleport;
    /** Called when the camera is lost from outside (another game mode set, the entity gone): abort the cinematic. */
    private final Consumer<UUID> onLost;
    private final Map<UUID, Shot> shots = new HashMap<>();
    /** Players whose game mode we are changing right now. */
    private final Set<UUID> changing = new HashSet<>();
    private @Nullable BukkitTask task;

    /** One player in the camera. */
    private static final class Shot {
        final ItemDisplay entity;
        final CameraReturn back;
        boolean targeted;
        boolean keepMode;

        Shot(ItemDisplay entity, CameraReturn back) {
            this.entity = entity;
            this.back = back;
        }
    }

    public CinematicCamera(Plugin plugin, Set<UUID> selfTeleport, Consumer<UUID> onLost) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "camera_return");
        this.selfTeleport = selfTeleport;
        this.onLost = onLost;
    }

    /** Whether the player is in the camera now. */
    public boolean active(UUID player) {
        return shots.containsKey(player);
    }

    /**
     * Puts the player in the camera at {@code camera} (feet; the view is {@link #EYE_HEIGHT} above); afterwards they get their game mode back at {@code back}.
     *
     * @return false when nothing changed (already in it, or the camera point's chunk isn't loaded)
     */
    public boolean enter(Player player, Location camera, Location back) {
        UUID id = player.getUniqueId();
        World world = camera.getWorld();
        if (shots.containsKey(id) || world == null || !world.isChunkLoaded(camera.getBlockX() >> 4,
                camera.getBlockZ() >> 4)) {
            return false;
        }
        GameMode original = player.getGameMode();
        CameraReturn ret = new CameraReturn(original.name(), back.getWorld().getKey().asString(), back.getX(),
                back.getY(), back.getZ(), back.getYaw(), back.getPitch());
        player.getPersistentDataContainer().set(key, PersistentDataType.STRING, ret.encode()); // first: crash-safe
        ItemDisplay entity = null;
        try {
            setMode(player, GameMode.SPECTATOR);
            teleport(player, camera);
            entity = world.spawn(camera.clone().add(0, EYE_HEIGHT, 0), ItemDisplay.class, e -> {
                e.setPersistent(false);
                e.setVisibleByDefault(false);
                e.addScoreboardTag(StationViews.TAG);
                e.setRotation(camera.getYaw(), camera.getPitch());
            });
            player.showEntity(plugin, entity);
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not put " + player.getName() + " in the build camera", e);
            if (entity != null) {
                entity.remove();
            }
            restore(player, ret, true);
            return false;
        }
        shots.put(id, new Shot(entity, ret));
        ensureTask();
        return true;
    }

    /**
     * Ends the camera: target off, game mode back, entity gone, stored return removed; and the player back at the
     * return spot when {@code online} and {@code goBack}. Not online (quit): only the game mode; the spot stays in
     * the PDC and is used on the next join.
     *
     * @param goBack false when the player is being teleported somewhere else right now
     * @return whether they were in the camera
     */
    public boolean exit(Player player, boolean online, boolean goBack) {
        Shot shot = shots.remove(player.getUniqueId());
        if (shot == null) {
            return false;
        }
        shot.entity.remove();
        if (shot.keepMode) { // someone else set a game mode: keep theirs, just go back
            player.getPersistentDataContainer().remove(key);
            if (online && goBack && !player.isDead()) {
                teleport(player, location(shot.back));
            }
            return true;
        }
        if (online) {
            restore(player, shot.back, goBack && !player.isDead());
        } else {
            setMode(player, mode(shot.back)); // the PDC stays: the next join puts them back at the spot
        }
        return true;
    }

    /** Disable / reload safety net: every camera ends (the cinematics normally did that already). */
    public void exitAll() {
        for (UUID id : Set.copyOf(shots.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                exit(p, true, true);
            } else {
                Shot s = shots.remove(id);
                s.entity.remove();
            }
        }
        stopTask();
    }

    private void restore(Player player, CameraReturn back, boolean teleport) {
        if (player.getGameMode() == GameMode.SPECTATOR && player.getSpectatorTarget() != null) {
            selfTeleport.add(player.getUniqueId());
            changing.add(player.getUniqueId());
            try {
                player.setSpectatorTarget(null);
            } finally {
                selfTeleport.remove(player.getUniqueId());
                changing.remove(player.getUniqueId());
            }
        }
        setMode(player, mode(back));
        if (teleport) {
            player.setFallDistance(0f);
            teleport(player, location(back));
        }
        player.getPersistentDataContainer().remove(key);
    }

    private void ensureTask() {
        if (task == null) {
            task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
        }
    }

    private void stopTask() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        for (UUID id : Set.copyOf(shots.keySet())) {
            Shot shot = shots.get(id);
            Player p = Bukkit.getPlayer(id);
            if (shot == null || p == null) {
                continue; // the quit handler ends it
            }
            if (!shot.entity.isValid() || p.getGameMode() != GameMode.SPECTATOR) {
                onLost.accept(id);
                continue;
            }
            if (!shot.targeted || !shot.entity.equals(p.getSpectatorTarget())) {
                shot.targeted = true;
                selfTeleport.add(id);
                try {
                    p.setSpectatorTarget(shot.entity);
                } catch (IllegalStateException e) {
                    onLost.accept(id);
                } finally {
                    selfTeleport.remove(id);
                }
            }
        }
        if (shots.isEmpty()) {
            stopTask();
        }
    }

    /** Sneaking out of the camera is refused. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onStopSpectating(PlayerStopSpectatingEntityEvent event) {
        Shot shot = shots.get(event.getPlayer().getUniqueId());
        if (shot != null && !changing.contains(event.getPlayer().getUniqueId())
                && shot.entity.equals(event.getSpectatorTarget())) {
            event.setCancelled(true);
        }
    }

    /** Someone else changed the game mode: keep theirs and end the cinematic. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        Shot shot = shots.get(id);
        if (shot != null && !changing.contains(id)) {
            shot.keepMode = true;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (shots.get(id) == shot) {
                    onLost.accept(id);
                }
            });
        }
    }

    /** Left over from a quit, crash or kill mid-build: game mode back, back at the spot. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        if (!p.getPersistentDataContainer().has(key, PersistentDataType.STRING)) {
            return;
        }
        CameraReturn back = CameraReturn.decode(p.getPersistentDataContainer().get(key, PersistentDataType.STRING));
        if (back == null) {
            p.getPersistentDataContainer().remove(key);
            if (p.getGameMode() == GameMode.SPECTATOR) {
                setMode(p, Bukkit.getDefaultGameMode());
            }
            return;
        }
        plugin.getLogger().info("Restoring " + p.getName() + " from an unfinished build camera (game mode "
                + back.gameMode().toLowerCase(Locale.ROOT) + ")");
        restore(p, back, true);
    }

    private void setMode(Player player, GameMode mode) {
        if (player.getGameMode() == mode) {
            return;
        }
        changing.add(player.getUniqueId());
        try {
            player.setGameMode(mode);
        } finally {
            changing.remove(player.getUniqueId());
        }
    }

    private void teleport(Player player, @Nullable Location to) {
        if (to == null) {
            return;
        }
        selfTeleport.add(player.getUniqueId());
        try {
            player.teleport(to);
        } finally {
            selfTeleport.remove(player.getUniqueId());
        }
    }

    private static GameMode mode(CameraReturn back) {
        try {
            return GameMode.valueOf(back.gameMode());
        } catch (IllegalArgumentException e) {
            return Bukkit.getDefaultGameMode();
        }
    }

    /** The return spot; when its world is gone, the main world's spawn (never left hanging at the camera point). */
    private static Location location(CameraReturn back) {
        World world = PlotService.worldByKey(back.worldKey()).orElse(null);
        return world == null ? Bukkit.getWorlds().getFirst().getSpawnLocation()
                : new Location(world, back.x(), back.y(), back.z(), back.yaw(), back.pitch());
    }

    /** Camera entities alive (debug). */
    public int activeCameras() {
        return shots.size();
    }
}
