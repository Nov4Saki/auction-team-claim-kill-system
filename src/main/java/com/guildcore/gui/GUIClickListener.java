package com.guildcore.gui;

import com.guildcore.auction.AuctionItem;
import com.guildcore.auction.AuctionManager;
import com.guildcore.config.SettingsManager;
import com.guildcore.debug.DebugFlag;
import com.guildcore.debug.DebugManager;
import com.guildcore.economy.EconomyManager;
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

import java.util.List;

public class GUIClickListener implements Listener {

    private final GUIManager guiManager;
    private final AuctionManager auctionManager;
    private final TeamManager teamManager;
    private final TeamUpgradeManager upgradeManager;
    private final TeamVaultManager vaultManager;
    private final EconomyManager economyManager;
    private final SettingsManager settingsManager;
    private final SchedulerWrapper scheduler;

    public GUIClickListener(GUIManager guiManager, AuctionManager auctionManager, TeamManager teamManager, TeamUpgradeManager upgradeManager, TeamVaultManager vaultManager, EconomyManager economyManager, SettingsManager settingsManager, SchedulerWrapper scheduler) {
        this.guiManager = guiManager;
        this.auctionManager = auctionManager;
        this.teamManager = teamManager;
        this.upgradeManager = upgradeManager;
        this.vaultManager = vaultManager;
        this.economyManager = economyManager;
        this.settingsManager = settingsManager;
        this.scheduler = scheduler;
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory() == null) return;
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof VaultGUIHolder vaultHolder) {
            ItemStack[] contents = event.getInventory().getContents();
            if (contents.length > 53 && contents[53] != null && contents[53].getType() == Material.BARRIER) {
                contents[53] = null;
            }
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

        // Vault GUI Back Button handling
        if (holder instanceof VaultGUIHolder vaultHolder) {
            if (event.getSlot() == 53 && event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.BARRIER) {
                event.setCancelled(true);
                SoundUtil.playClick(player);
                Team team = teamManager.getTeam(vaultHolder.getTeamId());
                guiManager.openTeamMenu(player, team);
            }
            return;
        }

        // 1. Auction House Main GUI Navigation
        if (holder instanceof AuctionGUIHolder ahHolder) {
            event.setCancelled(true);
            SoundUtil.playClick(player);
            int slot = event.getSlot();

            if (slot == 0) { guiManager.openAuctionHouse(player, 1, "ALL", ""); return; }
            if (slot == 1) { guiManager.openAuctionHouse(player, 1, "WEAPONS", ""); return; }
            if (slot == 2) { guiManager.openAuctionHouse(player, 1, "ARMOR", ""); return; }
            if (slot == 3) { guiManager.openAuctionHouse(player, 1, "TOOLS", ""); return; }
            if (slot == 4) { guiManager.openAuctionHouse(player, 1, "BLOCKS", ""); return; }
            if (slot == 5) { guiManager.openAuctionHouse(player, 1, "POTIONS", ""); return; }
            if (slot == 6) { guiManager.openAuctionHouse(player, 1, "SHULKERS", ""); return; }

            if (slot == 45) { guiManager.openMyListings(player, 1); return; }

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

        // 2. My Listings GUI (Click to Cancel & Reclaim Item)
        if (holder instanceof AuctionMyListingsHolder listingsHolder) {
            event.setCancelled(true);
            SoundUtil.playClick(player);
            int slot = event.getSlot();

            if (slot == 49) {
                guiManager.openAuctionHouse(player);
                return;
            }

            List<AuctionItem> myListings = auctionManager.getPlayerListings(player.getUniqueId());
            if (slot >= 0 && slot < myListings.size()) {
                AuctionItem item = myListings.get(slot);
                auctionManager.cancelListing(player, item);
                guiManager.openMyListings(player, listingsHolder.getPage());
            }
            return;
        }

        // 3. Admin Main Settings Hub
        if (holder instanceof SettingsGUIHolder) {
            event.setCancelled(true);
            SoundUtil.playClick(player);
            int slot = event.getSlot();

            if (slot == 10) { guiManager.openAdminEconomySettings(player); return; }
            if (slot == 11) { guiManager.openAdminKillSettings(player); return; }
            if (slot == 12) { guiManager.openAdminClaimSettings(player); return; }
            if (slot == 13) { guiManager.openAdminTeamSettings(player); return; }
            if (slot == 14) { guiManager.openAdminCombatSettings(player); return; }
            if (slot == 15) { guiManager.openAdminScoreboardSettings(player); return; }
            if (slot == 16) { guiManager.openAdminAuctionSettings(player); return; }
            if (slot == 22) { guiManager.openAdminDebugPanel(player); return; }
            return;
        }

        // 4. Admin Economy Sub-GUI
        if (holder instanceof AdminEconomyHolder) {
            event.setCancelled(true);
            SoundUtil.playClick(player);
            int slot = event.getSlot();

            if (slot == 10) {
                long bal = settingsManager.getInt("economy.starting_balance", 100);
                long next = bal == 100 ? 500 : (bal == 500 ? 1000 : 100);
                settingsManager.set("economy.starting_balance", String.valueOf(next));
                guiManager.openAdminEconomySettings(player);
            } else if (slot == 12) {
                long reward = settingsManager.getInt("economy.pvp_kill_reward", 50);
                long next = reward == 10 ? 50 : (reward == 50 ? 100 : 10);
                settingsManager.set("economy.pvp_kill_reward", String.valueOf(next));
                guiManager.openAdminEconomySettings(player);
            } else if (slot == 14) {
                long tax = settingsManager.getInt("economy.sales_tax_percent", 5);
                long next = tax == 0 ? 5 : (tax == 5 ? 10 : 0);
                settingsManager.set("economy.sales_tax_percent", String.valueOf(next));
                guiManager.openAdminEconomySettings(player);
            } else if (slot == 16) {
                economyManager.deposit(player.getUniqueId(), 1000, "admin_give_self");
                player.sendMessage(TextUtil.format("<green>Received +$1,000 coins from Admin Panel!</green>"));
                guiManager.openAdminEconomySettings(player);
            } else if (slot == 26) {
                guiManager.openAdminSettings(player);
            }
            return;
        }

        // 5. Admin Combat Sub-GUI
        if (holder instanceof AdminCombatHolder) {
            event.setCancelled(true);
            SoundUtil.playClick(player);
            int slot = event.getSlot();

            if (slot == 10) {
                int tag = settingsManager.getInt("combat.tag_duration", 15);
                int next = tag == 10 ? 15 : (tag == 15 ? 30 : 10);
                settingsManager.set("combat.tag_duration", String.valueOf(next));
                guiManager.openAdminCombatSettings(player);
            } else if (slot == 11) {
                int cd = settingsManager.getInt("combat.enderpearl_cooldown", 15);
                int next = cd == 10 ? 15 : (cd == 15 ? 30 : 10);
                settingsManager.set("combat.enderpearl_cooldown", String.valueOf(next));
                guiManager.openAdminCombatSettings(player);
            } else if (slot == 12) {
                int cd = settingsManager.getInt("combat.windcharge_cooldown", 10);
                int next = cd == 5 ? 10 : (cd == 10 ? 20 : 5);
                settingsManager.set("combat.windcharge_cooldown", String.valueOf(next));
                guiManager.openAdminCombatSettings(player);
            } else if (slot == 13) {
                int cd = settingsManager.getInt("combat.mace_cooldown", 12);
                int next = cd == 5 ? 12 : (cd == 12 ? 20 : 5);
                settingsManager.set("combat.mace_cooldown", String.valueOf(next));
                guiManager.openAdminCombatSettings(player);
            } else if (slot == 14) {
                boolean val = settingsManager.getBoolean("combat.riptide_enabled", false);
                settingsManager.set("combat.riptide_enabled", String.valueOf(!val));
                guiManager.openAdminCombatSettings(player);
            } else if (slot == 15) {
                boolean val = settingsManager.getBoolean("combat.crystal_enabled", false);
                settingsManager.set("combat.crystal_enabled", String.valueOf(!val));
                guiManager.openAdminCombatSettings(player);
            } else if (slot == 16) {
                boolean val = settingsManager.getBoolean("combat.anchor_enabled", false);
                settingsManager.set("combat.anchor_enabled", String.valueOf(!val));
                guiManager.openAdminCombatSettings(player);
            } else if (slot == 26) {
                guiManager.openAdminSettings(player);
            }
            return;
        }

        // 6. Admin Auction Sub-GUI
        if (holder instanceof AdminAuctionHolder) {
            event.setCancelled(true);
            SoundUtil.playClick(player);
            int slot = event.getSlot();

            if (slot == 10) {
                int fee = settingsManager.getInt("auction.listing_fee", 50);
                int next = fee == 0 ? 50 : (fee == 50 ? 100 : 0);
                settingsManager.set("auction.listing_fee", String.valueOf(next));
                guiManager.openAdminAuctionSettings(player);
            } else if (slot == 12) {
                int dur = settingsManager.getInt("auction.duration_hours", 48);
                int next = dur == 24 ? 48 : (dur == 48 ? 72 : 24);
                settingsManager.set("auction.duration_hours", String.valueOf(next));
                guiManager.openAdminAuctionSettings(player);
            } else if (slot == 16) {
                int cd = settingsManager.getInt("auction.listing_cooldown_sec", 0);
                int next = cd == 0 ? 5 : (cd == 5 ? 10 : (cd == 10 ? 30 : (cd == 30 ? 60 : 0)));
                settingsManager.set("auction.listing_cooldown_sec", String.valueOf(next));
                guiManager.openAdminAuctionSettings(player);
            } else if (slot == 26) {
                guiManager.openAdminSettings(player);
            }
            return;
        }

        // 7. Admin Debug Sub-GUI
        if (holder instanceof AdminDebugHolder) {
            event.setCancelled(true);
            SoundUtil.playClick(player);
            int slot = event.getSlot();

            if (slot >= 0 && slot < 18) {
                DebugFlag[] flags = DebugFlag.values();
                if (slot < flags.length) {
                    DebugFlag flag = flags[slot];
                    DebugManager.toggle(flag);
                    guiManager.openAdminDebugPanel(player);
                }
            } else if (slot == 26) {
                guiManager.openAdminSettings(player);
            }
            return;
        }

        // 8. Simple Back-Button Sub-GUIs
        if (holder instanceof AdminKillHolder || holder instanceof AdminClaimHolder || holder instanceof AdminTeamHolder || holder instanceof AdminScoreboardHolder) {
            event.setCancelled(true);
            SoundUtil.playClick(player);
            if (event.getSlot() == 26) {
                guiManager.openAdminSettings(player);
            }
            return;
        }

        // 9. Auction Purchase Confirmation GUI
        if (holder instanceof AuctionConfirmHolder confirmHolder) {
            event.setCancelled(true);
            SoundUtil.playClick(player);
            int slot = event.getSlot();

            if (slot == 11) { // Confirm
                AuctionItem item = confirmHolder.getAuctionItem();
                if (auctionManager.buyItem(player, item)) {
                    player.sendMessage(TextUtil.format("<green>Successfully purchased " + item.getItem().getType() + " for $" + item.getPrice() + "!</green>"));
                } else {
                    player.sendMessage(TextUtil.format("<red>Could not complete purchase (insufficient funds, listing cooldown, or item sold).</red>"));
                }
                player.closeInventory();
            } else if (slot == 15) { // Cancel
                guiManager.openAuctionHouse(player);
            }
            return;
        }

        // 10. Main Team Control GUI Navigation
        if (holder instanceof TeamGUIHolder teamHolder) {
            event.setCancelled(true);
            SoundUtil.playClick(player);
            int slot = event.getSlot();

            Team team = teamManager.getTeam(teamHolder.getTeamId());
            if (team == null) return;

            if (slot == 12) { // Bank
                player.sendMessage(TextUtil.format("<gold>🏦 Team Bank: <green>$" + team.getBankBalance() + "</green> | Use /team bank deposit <amount> or withdraw</gold>"));
            } else if (slot == 14) { // Vault
                ItemStack[] contents = vaultManager.getVaultPage(team.getId(), 1);
                Inventory vaultInv = Bukkit.createInventory(new VaultGUIHolder(team.getId(), 1), 54, TextUtil.format("<gold>📦 Team Vault (Page 1)</gold>"));
                vaultInv.setContents(contents);
                vaultInv.setItem(53, new GUIItemBuilder(Material.BARRIER).name("<red>◀ Back to Team Menu</red>").build());
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

        // 11. Team Upgrades GUI
        if (holder instanceof TeamUpgradesHolder upgradesHolder) {
            event.setCancelled(true);
            SoundUtil.playClick(player);
            int slot = event.getSlot();

            Team team = teamManager.getTeam(upgradesHolder.getTeamId());
            if (team == null) return;

            if (slot == 26) {
                guiManager.openTeamMenu(player, team);
                return;
            }

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
