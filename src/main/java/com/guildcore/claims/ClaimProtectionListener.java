package com.guildcore.claims;

import com.guildcore.debug.DebugFlag;
import com.guildcore.debug.DebugManager;
import com.guildcore.util.TextUtil;
import org.bukkit.Chunk;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public class ClaimProtectionListener implements Listener {
    private final ClaimManager claimManager;

    public ClaimProtectionListener(ClaimManager claimManager) {
        this.claimManager = claimManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Chunk chunk = event.getBlock().getChunk();

        if (!claimManager.canBuild(player, chunk)) {
            event.setCancelled(true);
            DebugManager.log(DebugFlag.CLAIM_PROTECTION, "Blocked block break at (" + chunk.getX() + "," + chunk.getZ() + ") by " + player.getName());
            player.sendActionBar(TextUtil.format("<red>🚫 Protected Territory</red>"));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Chunk chunk = event.getBlockPlaced().getChunk();

        if (!claimManager.canBuild(player, chunk)) {
            event.setCancelled(true);
            DebugManager.log(DebugFlag.CLAIM_PROTECTION, "Blocked block place at (" + chunk.getX() + "," + chunk.getZ() + ") by " + player.getName());
            player.sendActionBar(TextUtil.format("<red>🚫 Protected Territory</red>"));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;
        Player player = event.getPlayer();
        Chunk chunk = event.getClickedBlock().getChunk();

        ClaimInfo claim = claimManager.getClaimAt(chunk);
        if (claim != null && !claimManager.canBuild(player, chunk)) {
            // Container / interact check
            if (event.getClickedBlock().getType().name().contains("CHEST") ||
                event.getClickedBlock().getType().name().contains("SHULKER") ||
                event.getClickedBlock().getType().name().contains("DOOR") ||
                event.getClickedBlock().getType().name().contains("ANVIL")) {
                event.setCancelled(true);
                DebugManager.log(DebugFlag.CLAIM_PROTECTION, "Blocked container interact at (" + chunk.getX() + "," + chunk.getZ() + ") by " + player.getName());
                player.sendActionBar(TextUtil.format("<red>🚫 Protected Container</red>"));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> {
            ClaimInfo claim = claimManager.getClaimAt(block.getChunk());
            if (claim != null) {
                // If claim explosion flag disabled, prevent block destruction
                boolean disableExplosions = !claim.hasFlag("explosions");
                if (disableExplosions) {
                    DebugManager.log(DebugFlag.CLAIM_PROTECTION, "Blocked explosion damage in claim at (" + block.getChunk().getX() + "," + block.getChunk().getZ() + ")");
                }
                return disableExplosions;
            }
            return false;
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        Chunk pistonChunk = event.getBlock().getChunk();
        for (Block block : event.getBlocks()) {
            Chunk targetChunk = block.getRelative(event.getDirection()).getChunk();
            if (!pistonChunk.equals(targetChunk) && claimManager.isClaimed(targetChunk)) {
                event.setCancelled(true);
                DebugManager.log(DebugFlag.PISTON_EVENTS, "Blocked cross-chunk piston extend into claimed chunk (" + targetChunk.getX() + "," + targetChunk.getZ() + ")");
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        Chunk pistonChunk = event.getBlock().getChunk();
        for (Block block : event.getBlocks()) {
            Chunk targetChunk = block.getChunk();
            if (!pistonChunk.equals(targetChunk) && claimManager.isClaimed(targetChunk)) {
                event.setCancelled(true);
                DebugManager.log(DebugFlag.PISTON_EVENTS, "Blocked cross-chunk piston retract from claimed chunk (" + targetChunk.getX() + "," + targetChunk.getZ() + ")");
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHopperMove(InventoryMoveItemEvent event) {
        if (event.getSource().getLocation() != null && event.getDestination().getLocation() != null) {
            Chunk sourceChunk = event.getSource().getLocation().getChunk();
            Chunk destChunk = event.getDestination().getLocation().getChunk();
            if (!sourceChunk.equals(destChunk) && claimManager.isClaimed(destChunk)) {
                ClaimInfo sourceClaim = claimManager.getClaimAt(sourceChunk);
                ClaimInfo destClaim = claimManager.getClaimAt(destChunk);
                if (destClaim != null && (sourceClaim == null || !sourceClaim.equals(destClaim))) {
                    event.setCancelled(true);
                    DebugManager.log(DebugFlag.HOPPER_EVENTS, "Blocked cross-claim hopper transfer");
                }
            }
        }
    }
}
