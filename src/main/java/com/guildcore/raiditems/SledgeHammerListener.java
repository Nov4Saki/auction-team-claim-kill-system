package com.guildcore.raiditems;

import com.guildcore.config.SettingsManager;
import com.guildcore.core.GuildCoreBlock;
import com.guildcore.core.GuildCoreManager;
import com.guildcore.debug.DebugFlag;
import com.guildcore.debug.DebugManager;
import com.guildcore.raidtag.RaidTagManager;
import com.guildcore.shield.OfflineShieldManager;
import com.guildcore.util.TextUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;

public class SledgeHammerListener implements Listener {
    private final RaidItemManager raidItemManager;
    private final GuildCoreManager guildCoreManager;
    private final OfflineShieldManager offlineShieldManager;
    private final SettingsManager settingsManager;
    private RaidTagManager raidTagManager;

    public SledgeHammerListener(RaidItemManager raidItemManager, GuildCoreManager guildCoreManager, OfflineShieldManager offlineShieldManager, SettingsManager settingsManager) {
        this.raidItemManager = raidItemManager;
        this.guildCoreManager = guildCoreManager;
        this.offlineShieldManager = offlineShieldManager;
        this.settingsManager = settingsManager;
    }

    public void setRaidTagManager(RaidTagManager raidTagManager) {
        this.raidTagManager = raidTagManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (!(event.getEntity() instanceof ArmorStand stand)) return;

        ItemStack item = player.getInventory().getItemInMainHand();
        RaidItemManager.RaidItemType type = raidItemManager.getRaidItemType(item);
        if (type != RaidItemManager.RaidItemType.SLEDGE_HAMMER) return;

        // Find the core this armor stand belongs to
        GuildCoreBlock targetCore = null;
        for (GuildCoreBlock core : guildCoreManager.getAllCores()) {
            if (core.getArmorStandUuid() != null && core.getArmorStandUuid().equals(stand.getUniqueId())) {
                targetCore = core;
                break;
            }
        }

        if (targetCore == null) return;
        event.setCancelled(true);

        // Check shield
        if (offlineShieldManager.isShieldActive(targetCore.getTeamId())) {
            player.sendActionBar(Component.text("🛡 This Guild Core is protected by an Offline Shield!", NamedTextColor.AQUA));
            return;
        }

        int damage = settingsManager.getInt("core.sledgehammer_damage", 5);
        guildCoreManager.damageCore(targetCore.getTeamId(), damage, player);
        raidItemManager.consumeDurability(item, player);

        // Apply raid tag
        if (raidTagManager != null) {
            raidTagManager.applyRaidTag(player, targetCore.getTeamId(), player.getLocation().getChunk());
        }

        DebugManager.log(DebugFlag.RAID_ITEMS, player.getName() + " sledge-hammered core of team " + targetCore.getTeamId() + " for " + damage + " damage");
    }
}
