package dev.casperrs.cookieClicker.house.cinematic;

import dev.casperrs.cookieClicker.config.HouseSettings;
import io.papermc.paper.event.packet.PlayerChunkLoadEvent;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Keeps a running cinematic consistent for its player: no stepping or teleporting into a still-hidden block (the
 * client thinks it is air, the server would rubber-band), no spectator teleports out of the camera, and fake air
 * re-sent after the client lost it (interaction rollbacks, chunk resends). Reads run state only. Main thread.
 */
final class CinematicGuards implements Listener {

    private final Map<UUID, CinematicRun> running;
    private final Set<UUID> selfTeleport;
    private final Supplier<HouseSettings> settings;

    CinematicGuards(Map<UUID, CinematicRun> running, Set<UUID> selfTeleport, Supplier<HouseSettings> settings) {
        this.running = running;
        this.selfTeleport = selfTeleport;
        this.settings = settings;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onTeleportGuard(PlayerTeleportEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        CinematicRun run = running.get(id);
        if (run == null || selfTeleport.contains(id)) {
            return;
        }
        Location to = event.getTo();
        if (run.camera && event.getCause() == PlayerTeleportEvent.TeleportCause.SPECTATE) {
            event.setCancelled(true);
            return;
        }
        if (run.world.equals(to.getWorld())
                && HouseBox.bodyOverlaps(to.getX(), to.getY(), to.getZ(), run.plan::isHidden)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (running.isEmpty()) {
            return;
        }
        CinematicRun run = running.get(event.getPlayer().getUniqueId());
        if (run == null) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ()) {
            return;
        }
        if (HouseBox.bodyOverlaps(to.getX(), to.getY(), to.getZ(), run.plan::isHidden)
                && !HouseBox.bodyOverlaps(from.getX(), from.getY(), from.getZ(), run.plan::isHidden)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent event) {
        Block b = event.getClickedBlock();
        if (b != null) {
            reassertNear(event.getPlayer(), b.getWorld(), b.getX(), b.getY(), b.getZ());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Location l = event.getRightClicked().getLocation();
        reassertNear(event.getPlayer(), l.getWorld(), l.getBlockX(), l.getBlockY(), l.getBlockZ());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBreak(BlockBreakEvent event) {
        Block b = event.getBlock();
        reassertNear(event.getPlayer(), b.getWorld(), b.getX(), b.getY(), b.getZ());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlace(BlockPlaceEvent event) {
        Block b = event.getBlockPlaced();
        reassertNear(event.getPlayer(), b.getWorld(), b.getX(), b.getY(), b.getZ());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkSent(PlayerChunkLoadEvent event) {
        CinematicRun run = running.get(event.getPlayer().getUniqueId());
        if (run != null && run.world.equals(event.getWorld())
                && run.box.overlapsChunk(event.getChunk().getX(), event.getChunk().getZ())) {
            run.plan.requestResend(); // sent on the next tick, after the chunk
        }
    }

    private void reassertNear(Player player, World world, int x, int y, int z) {
        CinematicRun run = running.get(player.getUniqueId());
        if (run != null && run.world.equals(world)
                && run.box.near(x, y, z, settings.get().cinematic().reassertRadius())) {
            run.plan.requestResend(); // next tick: after the server's rollback packets
        }
    }
}
