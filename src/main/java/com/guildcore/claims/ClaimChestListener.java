// FILE: src/main/java/com/guildcore/claims/ClaimChestListener.java
package com.guildcore.claims;

import com.guildcore.gui.GUIManager;
import com.guildcore.teams.Team;
import com.guildcore.teams.TeamManager;
import com.guildcore.util.TextUtil;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class ClaimChestListener implements Listener {
    private final ClaimChestManager claimChestManager;
    private final TeamManager teamManager;
    private final GUIManager guiManager;

    public ClaimChestListener(ClaimChestManager claimChestManager, TeamManager teamManager, GUIManager guiManager) {
        this.claimChestManager = claimChestManager;
        this.teamManager = teamManager;
        this.guiManager = guiManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        ItemStack itemInHand = event.getItemInHand();

        if (!claimChestManager.isClaimChestItem(itemInHand)) return;

        // Cancel vanilla placement immediately
        event.setCancelled(true);

        // Get the block where the chest would be placed (the air block the player targeted)
        Block chestLocation = event.getBlock();

        // Make sure it's air
        if (!chestLocation.getType().isAir()) {
            player.sendMessage(TextUtil.format("<red>✖ Cannot place Claim Chest here!</red>"));
            return;
        }

        if (claimChestManager.placeClaimChest(player, chestLocation)) {
            itemInHand.setAmount(itemInHand.getAmount() - 1);
            if (itemInHand.getAmount() <= 0) {
                player.getInventory().setItemInMainHand(null);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();

        if (claimChestManager.isClaimChest(block.getLocation())) {
            int teamId = claimChestManager.getChestTeamId(block.getLocation());
            Team playerTeam = teamManager.getPlayerTeam(player.getUniqueId());

            if (playerTeam != null && playerTeam.getId() == teamId) {
                event.setCancelled(true);
                player.sendMessage(TextUtil.format("<yellow>Right-click the Claim Chest to open the management GUI.</yellow>"));
            } else if (player.hasPermission("guildcore.admin")) {
                event.setCancelled(true);
                player.sendMessage(TextUtil.format("<yellow>Admin: Right-click for GUI or use commands to manage.</yellow>"));
            } else {
                event.setCancelled(true);
                player.sendMessage(TextUtil.format("<red>You cannot break Guild Claim Chests!</red>"));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;

        Block clicked = event.getClickedBlock();
        Player player = event.getPlayer();

        if (claimChestManager.isClaimChest(clicked.getLocation())) {
            event.setCancelled(true);
            int teamId = claimChestManager.getChestTeamId(clicked.getLocation());
            Team playerTeam = teamManager.getPlayerTeam(player.getUniqueId());

            if (playerTeam != null && playerTeam.getId() == teamId) {
                guiManager.openClaimChestManagement(player, teamId);
            } else {
                Team ownerTeam = teamManager.getTeam(teamId);
                String ownerName = ownerTeam != null ? ownerTeam.getName() : "Unknown";
                player.sendMessage(TextUtil.format("<red>This is " + ownerName + "'s Guild Claim Chest!</red>"));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof ArmorStand) {
            if (claimChestManager.isClaimChestStand(event.getEntity())) {
                event.setCancelled(true);
                if (event.getDamager() instanceof Player player) {
                    player.sendMessage(TextUtil.format("<red>You cannot damage Guild Claim Chests!</red>"));
                }
            }
        }
    }
}