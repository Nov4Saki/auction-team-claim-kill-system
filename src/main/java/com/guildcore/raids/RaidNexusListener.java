package com.guildcore.raids;

import com.guildcore.claims.ClaimInfo;
import com.guildcore.claims.ClaimManager;
import com.guildcore.debug.DebugFlag;
import com.guildcore.debug.DebugManager;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

public class RaidNexusListener implements Listener {
    private final RaidManager raidManager;
    private final ClaimManager claimManager;

    public RaidNexusListener(RaidManager raidManager, ClaimManager claimManager) {
        this.raidManager = raidManager;
        this.claimManager = claimManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onNexusHit(BlockDamageEvent event) {
        Block block = event.getBlock();
        ClaimInfo claim = claimManager.getClaimAt(block.getChunk());
        if (claim == null || !claim.isTeamClaim()) return;

        RaidManager.ActiveRaidSession session = raidManager.getSessionForDefender(claim.getTeamId());
        if (session == null || session.state != RaidManager.RaidState.ACTIVE) return;

        if (block.getState() instanceof TileState tileState) {
            // Check if this block is the nexus anchor
            raidManager.damageNexus(session, 1);
            DebugManager.log(DebugFlag.RAID_DAMAGE, "Nexus damaged by melee hit from " + event.getPlayer().getName());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onNexusExplode(EntityExplodeEvent event) {
        for (Block block : event.blockList()) {
            ClaimInfo claim = claimManager.getClaimAt(block.getChunk());
            if (claim == null || !claim.isTeamClaim()) continue;

            RaidManager.ActiveRaidSession session = raidManager.getSessionForDefender(claim.getTeamId());
            if (session == null || session.state != RaidManager.RaidState.ACTIVE) continue;

            session.rollbackQueue.add(new RaidRollbackEngine.RollbackEntry(
                block.getWorld().getName(),
                block.getX(), block.getY(), block.getZ(),
                block.getType(),
                block.getBlockData().getAsString()
            ));

            if (block.getState() instanceof TileState) {
                raidManager.damageNexus(session, 10);
                DebugManager.log(DebugFlag.RAID_DAMAGE, "Nexus damaged by TNT explosion!");
            }
        }
    }
}
