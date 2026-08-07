package com.guildcore.raiditems;

import com.guildcore.config.SettingsManager;
import com.guildcore.core.GuildCoreBlock;
import com.guildcore.core.GuildCoreManager;
import com.guildcore.debug.DebugFlag;
import com.guildcore.debug.DebugManager;
import com.guildcore.raidtag.RaidTagManager;
import com.guildcore.shield.OfflineShieldManager;
import com.guildcore.teams.Team;
import com.guildcore.teams.TeamManager;
import com.guildcore.util.TextUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;

// FILE: src/main/java/com/guildcore/raiditems/SledgeHammerListener.java
// Only showing modified parts - add teamManager field and own team check

import com.guildcore.claims.ClaimChestManager;
import org.bukkit.block.Block;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

public class SledgeHammerListener implements Listener {
    private final RaidItemManager raidItemManager;
    private final GuildCoreManager guildCoreManager;
    private final OfflineShieldManager offlineShieldManager;
    private final SettingsManager settingsManager;
    private RaidTagManager raidTagManager;
    private TeamManager teamManager;
    private ClaimChestManager claimChestManager;

    public SledgeHammerListener(RaidItemManager raidItemManager, GuildCoreManager guildCoreManager,
                                OfflineShieldManager offlineShieldManager, SettingsManager settingsManager) {
        this.raidItemManager = raidItemManager;
        this.guildCoreManager = guildCoreManager;
        this.offlineShieldManager = offlineShieldManager;
        this.settingsManager = settingsManager;
    }

    public void setRaidTagManager(RaidTagManager raidTagManager) { this.raidTagManager = raidTagManager; }
    public void setTeamManager(TeamManager teamManager) { this.teamManager = teamManager; }
    public void setClaimChestManager(ClaimChestManager claimChestManager) { this.claimChestManager = claimChestManager; }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = false)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        RaidItemManager.RaidItemType type = raidItemManager.getRaidItemType(item);
        if (type != RaidItemManager.RaidItemType.SLEDGE_HAMMER) return;

        Block block = event.getClickedBlock();
        GuildCoreBlock targetCore = guildCoreManager.getCoreAtLocation(block.getLocation());

        if (targetCore == null && claimChestManager != null && claimChestManager.isClaimChest(block.getLocation())) {
            int teamId = claimChestManager.getChestTeamId(block.getLocation());
            if (teamId > 0) {
                targetCore = guildCoreManager.getCoreForTeam(teamId);
            }
        }

        if (targetCore == null) return;

        event.setCancelled(true);
        handleSledgeHammerHit(player, item, targetCore);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (!(event.getEntity() instanceof ArmorStand stand)) return;

        ItemStack item = player.getInventory().getItemInMainHand();
        RaidItemManager.RaidItemType type = raidItemManager.getRaidItemType(item);
        if (type != RaidItemManager.RaidItemType.SLEDGE_HAMMER) return;

        GuildCoreBlock targetCore = null;
        for (GuildCoreBlock core : guildCoreManager.getAllCores()) {
            if (core.getArmorStandUuid() != null && core.getArmorStandUuid().equals(stand.getUniqueId())) {
                targetCore = core;
                break;
            }
        }

        if (targetCore == null) return;
        event.setCancelled(true);
        handleSledgeHammerHit(player, item, targetCore);
    }

    private void handleSledgeHammerHit(Player player, ItemStack item, GuildCoreBlock targetCore) {
        // Check if own team
        if (teamManager != null) {
            Team playerTeam = teamManager.getPlayerTeam(player.getUniqueId());
            if (playerTeam != null && playerTeam.getId() == targetCore.getTeamId()) {
                player.sendActionBar(Component.text("⚔ You cannot damage your own Guild Core!", NamedTextColor.RED));
                return;
            }
        }

        // Check shield
        if (offlineShieldManager.isShieldActive(targetCore.getTeamId())) {
            player.sendActionBar(Component.text("🛡 This Guild Core is protected by an Offline Shield!", NamedTextColor.AQUA));
            return;
        }

        int damage = settingsManager.getInt("core.sledgehammer_damage", 5);
        guildCoreManager.damageCore(targetCore.getTeamId(), damage, player);
        raidItemManager.consumeDurability(item, player);

        if (raidTagManager != null) {
            raidTagManager.applyRaidTag(player, targetCore.getTeamId(), player.getLocation().getChunk());
        }

        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 1.0f, 0.8f);
        GuildCoreBlock updated = guildCoreManager.getCoreForTeam(targetCore.getTeamId());
        int curHp = updated != null ? updated.getCurrentHp() : 0;
        player.sendActionBar(TextUtil.format("<red>🔨 Sledge Hammer dealt " + damage + " damage to the Guild Core! [" +
                curHp + "/" + targetCore.getMaxHp() + " HP]</red>"));

        DebugManager.log(DebugFlag.RAID_ITEMS, player.getName() + " sledge-hammered core of team " +
                targetCore.getTeamId() + " for " + damage + " damage");
    }
}