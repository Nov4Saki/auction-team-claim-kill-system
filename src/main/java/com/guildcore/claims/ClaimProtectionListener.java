package com.guildcore.claims;

import com.guildcore.config.SettingsManager;
import com.guildcore.shield.OfflineShieldManager;
import com.guildcore.core.GuildCoreManager;
import com.guildcore.teams.Team;
import com.guildcore.teams.TeamManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
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

    private TeamManager teamManager;
    private OfflineShieldManager offlineShieldManager;
    private GuildCoreManager guildCoreManager;

    public ClaimProtectionListener(ClaimManager claimManager, SettingsManager settingsManager) {
        this.claimManager = claimManager;
        this.settingsManager = settingsManager;
    }

    public void setTeamManager(TeamManager teamManager) {
        this.teamManager = teamManager;
    }

    public void setOfflineShieldManager(OfflineShieldManager offlineShieldManager) {
        this.offlineShieldManager = offlineShieldManager;
    }

    public void setGuildCoreManager(GuildCoreManager guildCoreManager) {
        this.guildCoreManager = guildCoreManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Chunk chunk = event.getBlock().getChunk();
        ClaimInfo claim = claimManager.getClaimAt(chunk);

        if (claim == null) return;

        // If offline shield is active, block ALL breaking (even by outsiders)
        if (claim.isTeamClaim() && claim.getTeamId() != null &&
                offlineShieldManager != null && offlineShieldManager.isShieldActive(claim.getTeamId())) {
            event.setCancelled(true);
            player.sendActionBar(Component.text(
                    "🛡 This territory is protected by an Offline Shield!", NamedTextColor.AQUA));
            return;
        }

        // Normal permission check
        if (!claimManager.canBuild(player, chunk)) {
            event.setCancelled(true);
            player.sendActionBar(Component.text(
                    "🚫 This chunk is claimed territory!", NamedTextColor.RED));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Chunk chunk = event.getBlock().getChunk();
        ClaimInfo claim = claimManager.getClaimAt(chunk);

        if (claim == null) return;

        // Shield check
        if (claim.isTeamClaim() && claim.getTeamId() != null &&
                offlineShieldManager != null && offlineShieldManager.isShieldActive(claim.getTeamId())) {
            event.setCancelled(true);
            player.sendActionBar(Component.text(
                    "🛡 This territory is protected by an Offline Shield!", NamedTextColor.AQUA));
            return;
        }

        if (!claimManager.canBuild(player, chunk)) {
            event.setCancelled(true);
            player.sendActionBar(Component.text(
                    "🚫 This chunk is claimed territory!", NamedTextColor.RED));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;

        Player player = event.getPlayer();
        Block clicked = event.getClickedBlock();
        Chunk chunk = clicked.getChunk();
        ClaimInfo claim = claimManager.getClaimAt(chunk);

        if (claim == null) return;

        // Exempt Crafting Table and Ender Chest from claim interaction protection
        Material type = clicked.getType();
        if (type == Material.CRAFTING_TABLE || type == Material.ENDER_CHEST) {
            return;
        }

        // Shield check - block container access when shield is active
        if (claim.isTeamClaim() && claim.getTeamId() != null &&
                offlineShieldManager != null && offlineShieldManager.isShieldActive(claim.getTeamId())) {

            // Check if player is not a team member trying to access containers
            Team playerTeam = teamManager != null ? teamManager.getPlayerTeam(player.getUniqueId()) : null;
            boolean isTeamMember = playerTeam != null && playerTeam.getId() == claim.getTeamId();

            if (!isTeamMember && clicked.getState() instanceof Container) {
                event.setCancelled(true);
                player.sendActionBar(Component.text(
                        "🛡 This container is protected by an Offline Shield!", NamedTextColor.AQUA));
                return;
            }
        }

        // Normal permission check
        if (!claimManager.canBuild(player, chunk)) {
            event.setCancelled(true);
            player.sendActionBar(Component.text(
                    "🚫 You do not have permission in this claim!", NamedTextColor.RED));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        Chunk chunk = event.getBlock().getChunk();
        ClaimInfo claim = claimManager.getClaimAt(chunk);

        if (claim != null) {
            // Check shield
            if (claim.isTeamClaim() && claim.getTeamId() != null &&
                    offlineShieldManager != null && offlineShieldManager.isShieldActive(claim.getTeamId())) {
                event.setCancelled(true);
                return;
            }

            if (!(event.getEntity() instanceof Player player && claimManager.canBuild(player, chunk))) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPvPDamage(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player victim && event.getDamager() instanceof Player attacker) {
            // Friendly fire check
            if (teamManager != null && teamManager.areSameTeam(victim.getUniqueId(), attacker.getUniqueId())) {
                boolean friendlyFire = settingsManager.getBoolean("teams.friendly_fire", false);
                if (!friendlyFire) {
                    event.setCancelled(true);
                    attacker.sendActionBar(Component.text(
                            "🛡 Friendly fire is disabled for your Guild!", NamedTextColor.YELLOW));
                    return;
                }
            }

            // PvP in claims check
            Chunk chunk = victim.getLocation().getChunk();
            ClaimInfo claim = claimManager.getClaimAt(chunk);
            if (claim != null) {
                boolean globalClaimPvp = settingsManager.getBoolean("claims.pvp_enabled", true);
                if (!globalClaimPvp || !claim.hasFlag("pvp")) {
                    event.setCancelled(true);
                    attacker.sendActionBar(Component.text(
                            "🛡 PvP is disabled in claimed territory!", NamedTextColor.RED));
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
            ClaimInfo claim = claimManager.getClaimAt(block.getChunk());

            if (claim != null) {
                if (claim.isTeamClaim() && claim.getTeamId() != null &&
                        offlineShieldManager != null && offlineShieldManager.isShieldActive(claim.getTeamId())) {
                    it.remove();
                    continue;
                }
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
            ClaimInfo claim = claimManager.getClaimAt(block.getChunk());

            if (claim != null) {
                if (claim.isTeamClaim() && claim.getTeamId() != null &&
                        offlineShieldManager != null && offlineShieldManager.isShieldActive(claim.getTeamId())) {
                    it.remove();
                    continue;
                }
                it.remove();
            }
        }
    }
}