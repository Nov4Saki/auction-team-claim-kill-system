package com.guildcore.claims;

import com.guildcore.config.SettingsManager;
import org.bukkit.Chunk;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.Iterator;

public class ClaimProtectionListener implements Listener {
    private final ClaimManager claimManager;
    private final SettingsManager settingsManager;

    private com.guildcore.teams.TeamManager teamManager;

    public ClaimProtectionListener(ClaimManager claimManager, SettingsManager settingsManager) {
        this.claimManager = claimManager;
        this.settingsManager = settingsManager;
    }

    public void setTeamManager(com.guildcore.teams.TeamManager teamManager) {
        this.teamManager = teamManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Chunk chunk = event.getBlock().getChunk();

        if (!claimManager.canBuild(player, chunk)) {
            event.setCancelled(true);
            player.sendActionBar(net.kyori.adventure.text.Component.text("🚫 This chunk is claimed territory!", net.kyori.adventure.text.format.NamedTextColor.RED));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Chunk chunk = event.getBlock().getChunk();

        if (!claimManager.canBuild(player, chunk)) {
            event.setCancelled(true);
            player.sendActionBar(net.kyori.adventure.text.Component.text("🚫 This chunk is claimed territory!", net.kyori.adventure.text.format.NamedTextColor.RED));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;
        Player player = event.getPlayer();
        Block clicked = event.getClickedBlock();
        Chunk chunk = clicked.getChunk();

        // Exempt Crafting Table and Ender Chest from claim interaction protection
        org.bukkit.Material type = clicked.getType();
        if (type == org.bukkit.Material.CRAFTING_TABLE || type == org.bukkit.Material.ENDER_CHEST) {
            return;
        }

        if (!claimManager.canBuild(player, chunk)) {
            event.setCancelled(true);
            player.sendActionBar(net.kyori.adventure.text.Component.text("🚫 You do not have permission in this claim!", net.kyori.adventure.text.format.NamedTextColor.RED));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        Chunk chunk = event.getBlock().getChunk();
        if (claimManager.isClaimed(chunk)) {
            if (!(event.getEntity() instanceof Player player && claimManager.canBuild(player, chunk))) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPvPDamage(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player victim && event.getDamager() instanceof Player attacker) {
            if (teamManager != null) {
                com.guildcore.teams.Team vTeam = teamManager.getPlayerTeam(victim.getUniqueId());
                com.guildcore.teams.Team aTeam = teamManager.getPlayerTeam(attacker.getUniqueId());
                if (vTeam != null && aTeam != null && vTeam.getId() == aTeam.getId()) {
                    boolean friendlyFire = settingsManager.getBoolean("teams.friendly_fire", false);
                    if (!friendlyFire) {
                        event.setCancelled(true);
                        attacker.sendActionBar(net.kyori.adventure.text.Component.text("🛡 Friendly fire is disabled for your Guild!", net.kyori.adventure.text.format.NamedTextColor.YELLOW));
                        return;
                    }
                }
            }

            Chunk chunk = victim.getLocation().getChunk();
            ClaimInfo claim = claimManager.getClaimAt(chunk);
            if (claim != null) {
                boolean globalClaimPvp = settingsManager.getBoolean("claims.pvp_enabled", true);
                if (!globalClaimPvp || !claim.hasFlag("pvp")) {
                    event.setCancelled(true);
                    attacker.sendActionBar(net.kyori.adventure.text.Component.text("🛡 PvP is disabled in claimed territory!", net.kyori.adventure.text.format.NamedTextColor.RED));
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        boolean globalDisable = settingsManager.getBoolean("world.disable_explosions", false);
        if (globalDisable) {
            event.setCancelled(true);
            return;
        }

        Iterator<Block> it = event.blockList().iterator();
        while (it.hasNext()) {
            Block block = it.next();
            if (claimManager.isClaimed(block.getChunk())) {
                it.remove();
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        boolean globalDisable = settingsManager.getBoolean("world.disable_explosions", false);
        if (globalDisable) {
            event.setCancelled(true);
            return;
        }

        Iterator<Block> it = event.blockList().iterator();
        while (it.hasNext()) {
            Block block = it.next();
            if (claimManager.isClaimed(block.getChunk())) {
                it.remove();
            }
        }
    }
}
