package com.guildcore.gui;

import com.guildcore.auction.AuctionItem;
import com.guildcore.auction.AuctionManager;
import com.guildcore.debug.DebugFlag;
import com.guildcore.debug.DebugManager;
import com.guildcore.gui.holders.*;
import com.guildcore.scheduler.SchedulerWrapper;
import com.guildcore.teams.Team;
import com.guildcore.teams.TeamManager;
import com.guildcore.teams.TeamUpgradeManager;
import com.guildcore.teams.TeamVaultManager;
import com.guildcore.util.SoundUtil;
import com.guildcore.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;

public class GUIClickListener implements Listener {

    private final GUIManager guiManager;
    private final AuctionManager auctionManager;
    private final TeamManager teamManager;
    private final TeamUpgradeManager upgradeManager;
    private final TeamVaultManager vaultManager;
    private final SchedulerWrapper scheduler;

    public GUIClickListener(GUIManager guiManager, AuctionManager auctionManager, TeamManager teamManager, TeamUpgradeManager upgradeManager, TeamVaultManager vaultManager, SchedulerWrapper scheduler) {
        this.guiManager = guiManager;
        this.auctionManager = auctionManager;
        this.teamManager = teamManager;
        this.upgradeManager = upgradeManager;
        this.vaultManager = vaultManager;
        this.scheduler = scheduler;
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory() == null) return;
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof VaultGUIHolder vaultHolder) {
            ItemStack[] contents = event.getInventory().getContents();
            vaultManager.saveVaultPage(vaultHolder.getTeamId(), vaultHolder.getPage(), contents);
            DebugManager.log(DebugFlag.VAULT_SERIALIZATION, "Saved team vault on close for team " + vaultHolder.getTeamId() + " page " + vaultHolder.getPage());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory() == null) return;
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder == null) return;

        if (!(event.getWhoClicked() instanceof Player player)) return;

        // ALLOW item placement/taking inside Team Vaults!
        if (holder instanceof VaultGUIHolder) {
            return;
        }

        // 1. Auction House Main GUI Navigation
        if (holder instanceof AuctionGUIHolder ahHolder) {
            event.setCancelled(true);
            SoundUtil.playClick(player);
            int slot = event.getSlot();

            // Category Filters (Slots 0-6)
            if (slot == 0) { guiManager.openAuctionHouse(player, 1, "ALL", ""); return; }
            if (slot == 1) { guiManager.openAuctionHouse(player, 1, "WEAPONS", ""); return; }
            if (slot == 2) { guiManager.openAuctionHouse(player, 1, "ARMOR", ""); return; }
            if (slot == 3) { guiManager.openAuctionHouse(player, 1, "TOOLS", ""); return; }
            if (slot == 4) { guiManager.openAuctionHouse(player, 1, "BLOCKS", ""); return; }
            if (slot == 5) { guiManager.openAuctionHouse(player, 1, "POTIONS", ""); return; }
            if (slot == 6) { guiManager.openAuctionHouse(player, 1, "SHULKERS", ""); return; }

            // Pagination (Slots 48, 50)
            if (slot == 48 && ahHolder.getPage() > 1) {
                guiManager.openAuctionHouse(player, ahHolder.getPage() - 1, ahHolder.getCategory(), ahHolder.getSearchQuery());
                return;
            }
            if (slot == 50) {
                guiManager.openAuctionHouse(player, ahHolder.getPage() + 1, ahHolder.getCategory(), ahHolder.getSearchQuery());
                return;
            }

            // Right-Click Shulker Box Preview
            ItemStack clicked = event.getCurrentItem();
            if (clicked != null && event.getClick() == ClickType.RIGHT && clicked.getType().name().contains("SHULKER_BOX")) {
                if (clicked.getItemMeta() instanceof BlockStateMeta bsm && bsm.getBlockState() instanceof ShulkerBox shulker) {
                    Inventory preview = Bukkit.createInventory(new ShulkerPreviewHolder(), 27, TextUtil.format("<gold>📦 Shulker Preview (Read-Only)</gold>"));
                    preview.setContents(shulker.getInventory().getContents());
                    scheduler.runSync(player, () -> player.openInventory(preview));
                    return;
                }
            }

            // Click Listing to Purchase (Slots 9-44)
            if (slot >= 9 && slot <= 44 && clicked != null && clicked.getType() != Material.AIR) {
                int listingIndex = (ahHolder.getPage() - 1) * 36 + (slot - 9);
                var activeListings = auctionManager.getActiveListings();
                if (listingIndex < activeListings.size()) {
                    AuctionItem item = activeListings.get(listingIndex);
                    guiManager.openConfirmPurchase(player, item);
                }
            }
            return;
        }

        // 2. Admin Settings GUI & Sub-Panels
        if (holder instanceof SettingsGUIHolder) {
            event.setCancelled(true);
            SoundUtil.playClick(player);
            int slot = event.getSlot();

            if (slot == 10) { guiManager.openAdminEconomySettings(player); return; }
            if (slot == 14) { guiManager.openAdminCombatSettings(player); return; }
            if (slot == 22) { guiManager.openAdminDebugPanel(player); return; }
            if (slot == 26) { guiManager.openAdminSettings(player); return; }

            // Debug Flag Wool Toggles (Slots 0-17)
            if (slot >= 0 && slot < 18) {
                DebugFlag[] flags = DebugFlag.values();
                if (slot < flags.length) {
                    DebugFlag flag = flags[slot];
                    DebugManager.toggle(flag);
                    guiManager.openAdminDebugPanel(player);
                }
            }
            return;
        }

        // 3. Auction Purchase Confirmation GUI
        if (holder instanceof AuctionConfirmHolder confirmHolder) {
            event.setCancelled(true);
            SoundUtil.playClick(player);
            int slot = event.getSlot();

            if (slot == 11) { // Confirm
                AuctionItem item = confirmHolder.getAuctionItem();
                if (auctionManager.buyItem(player, item)) {
                    player.sendMessage(TextUtil.format("<green>Successfully purchased " + item.getItem().getType() + " for $" + item.getPrice() + "!</green>"));
                } else {
                    player.sendMessage(TextUtil.format("<red>Could not complete purchase (insufficient funds or item sold).</red>"));
                }
                player.closeInventory();
            } else if (slot == 15) { // Cancel
                guiManager.openAuctionHouse(player);
            }
            return;
        }

        // 4. Main Team Control GUI Navigation
        if (holder instanceof TeamGUIHolder teamHolder) {
            event.setCancelled(true);
            SoundUtil.playClick(player);
            int slot = event.getSlot();

            Team team = teamManager.getTeam(teamHolder.getTeamId());
            if (team == null) return;

            if (slot == 12) { // Bank
                player.sendMessage(TextUtil.format("<gold>🏦 Team Bank: <green>$" + team.getBankBalance() + "</green> | Use /gcteam bank deposit <amount> or withdraw</gold>"));
            } else if (slot == 14) { // Vault
                ItemStack[] contents = vaultManager.getVaultPage(team.getId(), 1);
                Inventory vaultInv = Bukkit.createInventory(new VaultGUIHolder(team.getId(), 1), 54, TextUtil.format("<gold>📦 Team Vault (Page 1)</gold>"));
                vaultInv.setContents(contents);
                scheduler.runSync(player, () -> player.openInventory(vaultInv));
            } else if (slot == 16) { // Upgrades
                guiManager.openTeamUpgrades(player, team);
            } else if (slot == 32) { // Teleport Home
                if (team.getHomeLocation() != null) {
                    player.teleport(team.getHomeLocation());
                    player.sendMessage(TextUtil.format("<green>Teleported to team home!</green>"));
                } else {
                    player.sendMessage(TextUtil.format("<red>Team home location is not set.</red>"));
                }
            }
            return;
        }

        // 5. Team Upgrades GUI Interactions
        if (holder instanceof TeamUpgradesHolder upgradesHolder) {
            event.setCancelled(true);
            SoundUtil.playClick(player);
            int slot = event.getSlot();

            Team team = teamManager.getTeam(upgradesHolder.getTeamId());
            if (team == null) return;

            if (slot == 11) { // Member Cap Upgrade
                if (team.getBankBalance() >= 5000) {
                    team.setBankBalance(team.getBankBalance() - 5000);
                    team.setMaxMembers(team.getMaxMembers() + 2);
                    player.sendMessage(TextUtil.format("<green>Upgraded Team Member Cap to " + team.getMaxMembers() + "!</green>"));
                    guiManager.openTeamUpgrades(player, team);
                } else {
                    player.sendMessage(TextUtil.format("<red>Insufficient Team Bank balance ($5,000 required).</red>"));
                }
            }
            return;
        }

        // Other GUI Holders protection
        if (holder instanceof ClaimFlagsGUIHolder ||
            holder instanceof StatsGUIHolder ||
            holder instanceof ShulkerPreviewHolder ||
            holder instanceof AnvilSearchHolder) {

            event.setCancelled(true);
            SoundUtil.playClick(player);
        }
    }
}
