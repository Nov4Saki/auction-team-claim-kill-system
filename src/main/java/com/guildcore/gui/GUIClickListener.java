// FILE: src/main/java/com/guildcore/gui/GUIClickListener.java
// This replaces the entire existing GUIClickListener - sending key fix sections

package com.guildcore.gui;

import com.guildcore.auction.AuctionItem;
import com.guildcore.auction.AuctionManager;
import com.guildcore.config.SettingsManager;
import com.guildcore.crates.*;
import com.guildcore.debug.DebugFlag;
import com.guildcore.debug.DebugManager;
import com.guildcore.economy.EconomyManager;
import com.guildcore.gui.holders.*;
import com.guildcore.items.ProhibitedItemManager;
import com.guildcore.scheduler.SchedulerWrapper;
import com.guildcore.scoreboard.ScoreboardManager;
import com.guildcore.shop.ShopGUIHolder;
import com.guildcore.shop.ShopItem;
import com.guildcore.shop.ShopManager;
import com.guildcore.teams.Team;
import com.guildcore.teams.TeamManager;
import com.guildcore.teams.TeamUpgradeManager;
import com.guildcore.teams.TeamVaultManager;
import com.guildcore.util.SoundUtil;
import org.bukkit.World;
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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class GUIClickListener implements Listener {

    private final GUIManager guiManager;
    private final AuctionManager auctionManager;
    private final TeamManager teamManager;
    private final TeamUpgradeManager upgradeManager;
    private final TeamVaultManager vaultManager;
    private final EconomyManager economyManager;
    private final SettingsManager settingsManager;
    private final ScoreboardManager scoreboardManager;
    private final CrateManager crateManager;
    private final ShopManager shopManager;
    private final SchedulerWrapper scheduler;
    private ProhibitedItemManager prohibitedManager;
    private com.guildcore.trade.TradeManager tradeManager;

    public GUIClickListener(GUIManager guiManager, AuctionManager auctionManager, TeamManager teamManager,
                            TeamUpgradeManager upgradeManager, TeamVaultManager vaultManager,
                            EconomyManager economyManager, SettingsManager settingsManager,
                            ScoreboardManager scoreboardManager, CrateManager crateManager,
                            ShopManager shopManager, SchedulerWrapper scheduler) {
        this.guiManager = guiManager;
        this.auctionManager = auctionManager;
        this.teamManager = teamManager;
        this.upgradeManager = upgradeManager;
        this.vaultManager = vaultManager;
        this.economyManager = economyManager;
        this.settingsManager = settingsManager;
        this.scoreboardManager = scoreboardManager;
        this.crateManager = crateManager;
        this.shopManager = shopManager;
        this.scheduler = scheduler;
    }

    public void setProhibitedItemManager(ProhibitedItemManager prohibitedManager) {
        this.prohibitedManager = prohibitedManager;
    }

    public void setTradeManager(com.guildcore.trade.TradeManager tradeManager) {
        this.tradeManager = tradeManager;
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory() == null) return;
        InventoryHolder holder = event.getInventory().getHolder();

        if (holder instanceof VaultGUIHolder vaultHolder) {
            if (event.getPlayer() instanceof Player player) {
                vaultManager.handleVaultClose(vaultHolder.getTeamId(), vaultHolder.getPage(), player);
            }
            DebugManager.log(DebugFlag.VAULT_SERIALIZATION,
                    "Handled vault close for team " + vaultHolder.getTeamId() + " page " + vaultHolder.getPage());
        }

        if (holder instanceof TradeGUIHolder tradeHolder && tradeManager != null) {
            if (tradeHolder.isClosed()) return;
            tradeHolder.setClosed(true);

            UUID p1Uuid = tradeHolder.getPlayer1Uuid();
            UUID p2Uuid = tradeHolder.getPlayer2Uuid();

            tradeManager.getActiveTradeSessions().remove(p1Uuid);
            tradeManager.getActiveTradeSessions().remove(p2Uuid);

            Player p1 = Bukkit.getPlayer(p1Uuid);
            Player p2 = Bukkit.getPlayer(p2Uuid);
            Inventory inv = event.getInventory();

            if (!tradeHolder.isCompleted()) {
                if (p1 != null && p1.isOnline()) {
                    List<ItemStack> p1Items = getOfferItems(inv, new int[]{0,1,2,3,4,5,6,7,8});
                    for (ItemStack item : p1Items) p1.getInventory().addItem(item);
                    clearOfferSlots(inv, new int[]{0,1,2,3,4,5,6,7,8});
                }
                if (p2 != null && p2.isOnline()) {
                    List<ItemStack> p2Items = getOfferItems(inv, new int[]{18,19,20,21,22,23,24,25,26});
                    for (ItemStack item : p2Items) p2.getInventory().addItem(item);
                    clearOfferSlots(inv, new int[]{18,19,20,21,22,23,24,25,26});
                }
                Player closer = (Player) event.getPlayer();
                Player other = closer.equals(p1) ? p2 : p1;
                if (other != null && other.isOnline()) {
                    other.sendMessage(TextUtil.format("<red>Trade was cancelled because " + closer.getName() +
                            " closed the trade menu.</red>"));
                    scheduler.runLater(other, other::closeInventory, 1L);
                }
            }
        }

        // Handle claim chest management GUI close
        if (holder instanceof ClaimChestGUIHolder) {
            // No special cleanup needed
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory() == null) return;
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder == null) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // === CLAIM CHEST MANAGEMENT GUI ===
        if (holder instanceof ClaimChestGUIHolder chestHolder) {
            event.setCancelled(true);
            SoundUtil.playClick(player);
            int slot = event.getSlot();
            Team team = teamManager.getTeam(chestHolder.getTeamId());
            if (team == null) return;

            if (slot == 11) {
                // Remove Claim Chest button
                guiManager.openClaimChestRemoveConfirm(player, team);
            } else if (slot == 15) {
                player.closeInventory();
            }
            return;
        }

        // === CLAIM CHEST REMOVE CONFIRM ===
        if (holder instanceof ClaimChestRemoveConfirmHolder confirmHolder) {
            event.setCancelled(true);
            SoundUtil.playClick(player);
            int slot = event.getSlot();
            Team team = teamManager.getTeam(confirmHolder.getTeamId());
            if (team == null) return;

            if (slot == 11) {
                // Confirm removal
                player.sendMessage(TextUtil.format("<red><b>⚠ Your Guild Claim Chest has been destroyed!</b></red>"));
                player.sendMessage(TextUtil.format("<red>All territory claims have been lost. All upgrades stripped.</red>"));

                // Remove chest, claims, and core
                com.guildcore.claims.ClaimChestManager ccm =
                        com.guildcore.GuildCorePlugin.getInstance() != null ?
                                com.guildcore.GuildCorePlugin.getInstance().getClaimChestManager() : null;
                if (ccm != null) {
                    ccm.removeClaimChest(team.getId());
                }
                player.closeInventory();
            } else if (slot == 15) {
                player.sendMessage(TextUtil.format("<yellow>Claim Chest removal cancelled.</yellow>"));
                player.closeInventory();
            }
            return;
        }

        // === LEADERBOARD GUI ===
        if (holder instanceof LeaderboardGUIHolder lbHolder) {
            event.setCancelled(true);
            int slot = event.getSlot();
            if (slot == 2) { guiManager.openLeaderboardGUI(player, "PLAYER"); SoundUtil.playClick(player); }
            else if (slot == 6) { guiManager.openLeaderboardGUI(player, "GUILD"); SoundUtil.playClick(player); }
            else if (slot == 49) { guiManager.openLeaderboardGUI(player, lbHolder.getTab()); SoundUtil.playClick(player); }
            return;
        }

        // === TRADE GUI ===
        if (holder instanceof TradeGUIHolder tradeHolder && tradeManager != null) {
            event.setCancelled(true);
            if (tradeHolder.isClosed()) return;
            int rawSlot = event.getRawSlot();
            boolean isP1 = player.getUniqueId().equals(tradeHolder.getPlayer1Uuid());
            boolean isP2 = player.getUniqueId().equals(tradeHolder.getPlayer2Uuid());
            if (!isP1 && !isP2) return;

            Player p1 = Bukkit.getPlayer(tradeHolder.getPlayer1Uuid());
            Player p2 = Bukkit.getPlayer(tradeHolder.getPlayer2Uuid());
            if (p1 == null || p2 == null || !p1.isOnline() || !p2.isOnline()) {
                player.closeInventory();
                return;
            }
            Inventory inv = event.getInventory();

            if (event.getClickedInventory() != null && event.getClickedInventory().equals(player.getInventory())) {
                ItemStack item = event.getCurrentItem();
                if (item == null || item.getType() == Material.AIR) return;
                int[] targetSlots = isP1 ? new int[]{0,1,2,3,4,5,6,7,8} : new int[]{18,19,20,21,22,23,24,25,26};
                int emptySlot = -1;
                for (int s : targetSlots) {
                    ItemStack inSlot = inv.getItem(s);
                    if (inSlot == null || inSlot.getType() == Material.AIR) { emptySlot = s; break; }
                }
                if (emptySlot == -1) {
                    player.sendMessage(TextUtil.format("<red>Your trade offer is full (max 9 items).</red>"));
                    return;
                }
                resetReadyStates(tradeHolder, inv, p1, p2);
                inv.setItem(emptySlot, item.clone());
                event.getClickedInventory().setItem(event.getSlot(), null);
                SoundUtil.playClick(player);
                return;
            }

            if (rawSlot >= 0 && rawSlot < 27) {
                int p1ReadySlot = 10, p2ReadySlot = 16, cancelSlot = 13;
                if (rawSlot == cancelSlot) {
                    SoundUtil.playClick(player);
                    p1.sendMessage(TextUtil.format("<red>Trade was cancelled.</red>"));
                    p2.sendMessage(TextUtil.format("<red>Trade was cancelled.</red>"));
                    p1.closeInventory(); p2.closeInventory();
                    return;
                }
                if ((isP1 && rawSlot == p1ReadySlot) || (isP2 && rawSlot == p2ReadySlot)) {
                    if (isP1) tradeHolder.setP1Ready(!tradeHolder.isP1Ready());
                    if (isP2) tradeHolder.setP2Ready(!tradeHolder.isP2Ready());
                    SoundUtil.playClick(player);
                    tradeManager.updateTradeGUIControls(inv, tradeHolder, p1, p2);
                    if (tradeHolder.isP1Ready() && tradeHolder.isP2Ready()) {
                        startTradeCountdown(tradeHolder, inv, p1, p2);
                    } else {
                        tradeHolder.setCountdownSeconds(-1);
                    }
                    return;
                }
                int[] p1OfferSlots = {0,1,2,3,4,5,6,7,8};
                int[] p2OfferSlots = {18,19,20,21,22,23,24,25,26};
                boolean isOwnOffer = (isP1 && containsSlot(p1OfferSlots, rawSlot)) ||
                        (isP2 && containsSlot(p2OfferSlots, rawSlot));
                if (isOwnOffer) {
                    ItemStack offerItem = inv.getItem(rawSlot);
                    if (offerItem != null && offerItem.getType() != Material.AIR) {
                        resetReadyStates(tradeHolder, inv, p1, p2);
                        inv.setItem(rawSlot, null);
                        player.getInventory().addItem(offerItem);
                        SoundUtil.playClick(player);
                    }
                }
            }
            return;
        }

        // === CRATE CONFIRM ===
        if (holder instanceof CrateConfirmGUIHolder confirmHolder) {
            event.setCancelled(true);
            SoundUtil.playClick(player);
            int slot = event.getSlot();
            Crate crate = crateManager.getCrate(confirmHolder.getCrateName());
            if (crate == null) return;
            if (slot == 11) {
                if (crateManager.consumeKey(player, crate)) {
                    ItemStack selected = confirmHolder.getSelectedItem().clone();
                    player.getInventory().addItem(selected);
                    player.sendMessage(TextUtil.format("<green>🎁 You chose " + selected.getType() +
                            " from crate '" + crate.getDisplayName() + "'!</green>"));
                } else {
                    player.sendMessage(TextUtil.format("<red>No crate key found in inventory!</red>"));
                }
                player.closeInventory();
            } else if (slot == 15) {
                crateManager.openCrateChoiceMenu(player, crate);
            }
            return;
        }

        // === CRATE ADMIN HUB ===
        if (holder instanceof CrateAdminHubHolder) {
            event.setCancelled(true);
            SoundUtil.playClick(player);
            int slot = event.getSlot();
            if (slot == 45) {
                ChatInputListener.requestStringInput(player, "new_crate_name", p -> {
                    String name = settingsManager.getString("new_crate_name", "");
                    if (!name.isEmpty()) {
                        ItemStack inHand = p.getInventory().getItemInMainHand();
                        ItemStack keyItem = (inHand != null && !inHand.getType().isAir()) ?
                                inHand.clone() : new ItemStack(Material.TRIPWIRE_HOOK);
                        keyItem.setAmount(1);
                        crateManager.createCrate(name, name, keyItem);
                        p.sendMessage(TextUtil.format("<green>✔ Created choice crate '" + name + "'!</green>"));
                    }
                    crateManager.openCrateAdminHub(p);
                });
                return;
            }
            if (slot == 53) { player.closeInventory(); return; }
            List<Crate> list = crateManager.getAllCrates();
            if (slot >= 0 && slot < list.size()) {
                Crate crate = list.get(slot);
                ClickType click = event.getClick();
                if (click == ClickType.LEFT) {
                    crateManager.openCrateAdminEditor(player, crate);
                } else if (click == ClickType.RIGHT) {
                    ItemStack inHand = player.getInventory().getItemInMainHand();
                    if (inHand != null && !inHand.getType().isAir()) {
                        crateManager.setKeyItem(crate.getName(), inHand);
                        player.sendMessage(TextUtil.format("<green>✔ Updated key item for crate '" +
                                crate.getDisplayName() + "' to " + inHand.getType() + "!</green>"));
                    } else {
                        player.sendMessage(TextUtil.format("<red>Hold the new key item in your main hand!</red>"));
                    }
                    crateManager.openCrateAdminHub(player);
                } else if (click == ClickType.SHIFT_RIGHT) {
                    crateManager.deleteCrate(crate.getName());
                    player.sendMessage(TextUtil.format("<red>Deleted crate '" + crate.getDisplayName() + "'.</red>"));
                    crateManager.openCrateAdminHub(player);
                }
            }
            return;
        }

        // === CRATE GUI ===
        if (holder instanceof CrateGUIHolder crateHolder) {
            event.setCancelled(true);
            SoundUtil.playClick(player);
            int slot = event.getSlot();
            Crate crate = crateManager.getCrate(crateHolder.getCrateName());
            if (crate != null && slot >= 0 && slot < crate.getContents().size()) {
                if (crateManager.hasKey(player, crate)) {
                    ItemStack selected = crate.getContents().get(slot).clone();
                    crateManager.openCrateConfirmMenu(player, crate, slot, selected);
                } else {
                    player.sendMessage(TextUtil.format("<red>You need a matching Crate Key (" +
                            crate.getKeyItem().getType() + ") in your inventory to claim items!</red>"));
                }
            }
            return;
        }

        // === CRATE ADMIN EDITOR ===
        if (holder instanceof CrateAdminGUIHolder adminCrateHolder) {
            if (event.getSlot() == 53) {
                event.setCancelled(true);
                SoundUtil.playClick(player);
                List<ItemStack> newContents = new ArrayList<>();
                for (int i = 0; i < 53; i++) {
                    ItemStack item = event.getInventory().getItem(i);
                    if (item != null && !item.getType().isAir()) {
                        newContents.add(item.clone());
                    }
                }
                crateManager.saveCrateContents(adminCrateHolder.getCrateName(), newContents);
                player.sendMessage(TextUtil.format("<green>✔ Saved crate contents for '" +
                        adminCrateHolder.getCrateName() + "'!</green>"));
                player.closeInventory();
            }
            return;
        }

        // === SHOP GUI ===
        if (holder instanceof ShopGUIHolder shopHolder) {
            event.setCancelled(true);
            SoundUtil.playClick(player);
            int slot = event.getSlot();
            if (shopHolder.getCategoryId() == 0) {
                var categories = shopManager.getCategories();
                for (var cat : categories.values()) {
                    if (cat.getSlot() == slot) {
                        shopManager.openShopCategoryMenu(player, cat.getId());
                        return;
                    }
                }
            } else {
                if (slot == 49) { shopManager.openShopMainMenu(player); return; }
                List<ShopItem> items = shopManager.getCategoryItems(shopHolder.getCategoryId());
                for (ShopItem shopItem : items) {
                    if (shopItem.getSlot() == slot) {
                        ClickType click = event.getClick();
                        if (click == ClickType.LEFT) shopManager.buyItem(player, shopItem, 1);
                        else if (click == ClickType.SHIFT_LEFT) shopManager.buyItem(player, shopItem, 16);
                        else if (click == ClickType.RIGHT) shopManager.sellItem(player, shopItem, 1);
                        else if (click == ClickType.SHIFT_RIGHT) shopManager.sellItem(player, shopItem, 16);
                        return;
                    }
                }
            }
            return;
        }

        // === VAULT GUI ===
        if (holder instanceof VaultGUIHolder vaultHolder) {
            Team team = teamManager.getTeam(vaultHolder.getTeamId());
            if (team == null) return;
            event.setCancelled(true);
            org.bukkit.inventory.Inventory clickedInv = event.getClickedInventory();
            if (clickedInv == null) return;

            int page = vaultHolder.getPage();
            int vaultSlots = Math.max(9, team.getVaultSlots());
            int totalUnlockedPages = Math.max(1, (vaultSlots + 44) / 45);
            int globalStartIndex = (page - 1) * 45;
            int unlockedOnThisPage = Math.max(0, Math.min(45, vaultSlots - globalStartIndex));
            org.bukkit.inventory.Inventory topInv = event.getView().getTopInventory();

            if (clickedInv.equals(topInv)) {
                int slot = event.getSlot();
                if (slot == 45) {
                    if (page > 1) {
                        vaultManager.handleVaultClose(team.getId(), page, player);
                        SoundUtil.playClick(player);
                        guiManager.openTeamVault(player, team, page - 1);
                    } else SoundUtil.playError(player);
                    return;
                }
                if (slot == 49) {
                    vaultManager.handleVaultClose(team.getId(), page, player);
                    SoundUtil.playClick(player);
                    guiManager.openTeamMenu(player, team);
                    return;
                }
                if (slot == 53) {
                    if (page < totalUnlockedPages) {
                        vaultManager.handleVaultClose(team.getId(), page, player);
                        SoundUtil.playClick(player);
                        guiManager.openTeamVault(player, team, page + 1);
                    } else if (page == totalUnlockedPages && unlockedOnThisPage == 45) {
                        int targetGlobalSlot = vaultSlots + 1;
                        SoundUtil.playClick(player);
                        long cost = guiManager.getVaultSlotCost(targetGlobalSlot);
                        if (teamManager.purchaseVaultSlot(team, targetGlobalSlot, cost)) {
                            vaultManager.handleVaultClose(team.getId(), page, player);
                            SoundUtil.playSuccess(player);
                            player.sendMessage(TextUtil.format("<green>✔ Unlocked Team Vault Slot #" +
                                    targetGlobalSlot + " (Opening Page #" + (page + 1) + ")!</green>"));
                            guiManager.openTeamVault(player, team, page + 1);
                        } else {
                            SoundUtil.playError(player);
                            player.sendMessage(TextUtil.format("<red>✖ Purchase failed! Check Team Bank balance ($" +
                                    String.format("%,d", cost) + " required) or slot was already unlocked.</red>"));
                            guiManager.refreshTeamVault(team.getId(), page);
                        }
                    } else SoundUtil.playError(player);
                    return;
                }
                if (slot < 45) {
                    if (slot == unlockedOnThisPage) {
                        int targetGlobalSlot = globalStartIndex + slot + 1;
                        SoundUtil.playClick(player);
                        long cost = guiManager.getVaultSlotCost(targetGlobalSlot);
                        if (teamManager.purchaseVaultSlot(team, targetGlobalSlot, cost)) {
                            SoundUtil.playSuccess(player);
                            player.sendMessage(TextUtil.format("<green>✔ Unlocked Team Vault Slot #" +
                                    targetGlobalSlot + " for $" + String.format("%,d", cost) + " Team Bank!</green>"));
                            guiManager.refreshTeamVault(team.getId(), page);
                        } else {
                            SoundUtil.playError(player);
                            player.sendMessage(TextUtil.format("<red>✖ Purchase failed!</red>"));
                            guiManager.refreshTeamVault(team.getId(), page);
                        }
                        return;
                    }
                    if (slot < unlockedOnThisPage) {
                        boolean success = vaultManager.withdrawItem(player, team, page, slot);
                        if (success) SoundUtil.playClick(player);
                        else SoundUtil.playError(player);
                    } else SoundUtil.playError(player);
                }
                return;
            }
            if (clickedInv.equals(event.getView().getBottomInventory())) {
                if (unlockedOnThisPage <= 0) {
                    SoundUtil.playError(player);
                    player.sendMessage(TextUtil.format("<red>✖ Unlock Page #" + page + " slots before depositing items!</red>"));
                    return;
                }
                ItemStack itemToDeposit = event.getCurrentItem();
                if (itemToDeposit == null || itemToDeposit.getType() == Material.AIR) return;
                boolean success = vaultManager.depositItem(player, team, page, itemToDeposit, unlockedOnThisPage);
                if (success) SoundUtil.playClick(player);
                else {
                    SoundUtil.playError(player);
                    player.sendMessage(TextUtil.format("<red>✖ No free unlocked slots on Page #" + page +
                            "! Unlock slot #" + (globalStartIndex + unlockedOnThisPage + 1) + " or use Next Page ▶.</red>"));
                }
                return;
            }
            return;
        }

        // === AUCTION HOUSE ===
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
            // Shulker preview
            ItemStack clicked = event.getCurrentItem();
            if (clicked != null && event.getClick() == ClickType.RIGHT && clicked.getType().name().contains("SHULKER_BOX")) {
                if (clicked.getItemMeta() instanceof BlockStateMeta bsm && bsm.getBlockState() instanceof ShulkerBox shulker) {
                    Inventory preview = Bukkit.createInventory(new ShulkerPreviewHolder(), 27,
                            TextUtil.format("<gradient:#9D50BB:#6E48AA><b>📦 Shulker Vault Inspection</b></gradient> <gray>(Read-Only)</gray>"));
                    preview.setContents(shulker.getInventory().getContents());
                    scheduler.runSync(player, () -> player.openInventory(preview));
                    return;
                }
            }
            // Click listing to purchase
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

        // === MY LISTINGS ===
        if (holder instanceof AuctionMyListingsHolder listingsHolder) {
            event.setCancelled(true);
            SoundUtil.playClick(player);
            int slot = event.getSlot();
            if (slot == 49) { guiManager.openAuctionHouse(player); return; }
            List<AuctionItem> myListings = auctionManager.getPlayerListings(player.getUniqueId());
            if (slot >= 0 && slot < myListings.size()) {
                AuctionItem item = myListings.get(slot);
                auctionManager.cancelListing(player, item);
                guiManager.openMyListings(player, listingsHolder.getPage());
            }
            return;
        }

        // === RTP WORLD CHOICE ===
        if (holder instanceof RTPWorldGUIHolder) {
            event.setCancelled(true);
            SoundUtil.playClick(player);
            int slot = event.getSlot();
            if (slot == 11) {
                World normalWorld = Bukkit.getWorlds().stream()
                        .filter(w -> w.getEnvironment() == World.Environment.NORMAL).findFirst()
                        .orElse(player.getWorld());
                player.performCommand("rtp " + normalWorld.getName());
            } else if (slot == 13) {
                World netherWorld = Bukkit.getWorlds().stream()
                        .filter(w -> w.getEnvironment() == World.Environment.NETHER).findFirst().orElse(null);
                if (netherWorld != null) player.performCommand("rtp " + netherWorld.getName());
                else player.sendMessage(TextUtil.format("<red>Nether world not found!</red>"));
            } else if (slot == 15) {
                World endWorld = Bukkit.getWorlds().stream()
                        .filter(w -> w.getEnvironment() == World.Environment.THE_END).findFirst().orElse(null);
                if (endWorld != null) player.performCommand("rtp " + endWorld.getName());
                else player.sendMessage(TextUtil.format("<red>End world not found!</red>"));
            }
            player.closeInventory();
            return;
        }

        // === ADMIN SHOP HUB ===
        if (holder instanceof AdminShopHubHolder) {
            event.setCancelled(true);
            SoundUtil.playClick(player);
            int slot = event.getSlot();
            if (slot == 45) {
                ChatInputListener.requestStringInput(player, "new_shop_category_name", p -> {
                    String name = settingsManager.getString("new_shop_category_name", "");
                    if (!name.isEmpty()) {
                        ItemStack inHand = p.getInventory().getItemInMainHand();
                        Material mat = (inHand != null && !inHand.getType().isAir()) ? inHand.getType() : Material.CHEST;
                        shopManager.createCategory(name, mat, 10);
                        p.sendMessage(TextUtil.format("<green>✔ Created shop category '" + name + "'!</green>"));
                    }
                    guiManager.openAdminShopHub(p);
                });
                return;
            }
            if (slot == 49) { guiManager.openAdminSettings(player); return; }
            var categories = new ArrayList<>(shopManager.getCategories().values());
            for (var cat : categories) {
                if (cat.getSlot() == slot) {
                    ClickType click = event.getClick();
                    if (click == ClickType.LEFT) guiManager.openAdminShopCategoryEditor(player, cat.getId());
                    else if (click == ClickType.RIGHT) {
                        ItemStack inHand = player.getInventory().getItemInMainHand();
                        if (inHand != null && !inHand.getType().isAir()) {
                            shopManager.updateCategoryIcon(cat.getId(), inHand.getType());
                            player.sendMessage(TextUtil.format("<green>✔ Updated category icon for '" + cat.getName() + "'!</green>"));
                        } else player.sendMessage(TextUtil.format("<red>Hold an item in hand to set as icon!</red>"));
                        guiManager.openAdminShopHub(player);
                    } else if (click == ClickType.SHIFT_RIGHT) {
                        shopManager.deleteCategory(cat.getId());
                        player.sendMessage(TextUtil.format("<red>Deleted shop category '" + cat.getName() + "'.</red>"));
                        guiManager.openAdminShopHub(player);
                    }
                    return;
                }
            }
            return;
        }

        // === ADMIN SETTINGS MAIN HUB ===
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
            if (slot == 16) { guiManager.openAdminGuildCoreSettings(player); return; }
            if (slot == 19) { guiManager.openAdminAuctionSettings(player); return; }
            if (slot == 20) { guiManager.openAdminRtpSettings(player); return; }
            if (slot == 21) { guiManager.openAdminProhibitedItems(player, 1); return; }
            if (slot == 22) { guiManager.openAdminShopHub(player); return; }
            if (slot == 23) { guiManager.openAdminDebugPanel(player); return; }
            if (slot == 24) { crateManager.openCrateAdminHub(player); return; }
            if (slot == 25) { guiManager.openAdminRequestSettings(player); return; }
            if (slot == 30) { guiManager.openAdminShieldSettings(player); return; }
            if (slot == 31) { guiManager.openAdminRaidTagSettings(player); return; }
            if (slot == 32) { guiManager.openAdminRaidToolSettings(player); return; }
            if (slot == 49) { player.closeInventory(); return; }
            return;
        }

        // === GUILD CORE & RAID SETTINGS ===
        if (holder instanceof AdminGuildCoreHolder) {
            event.setCancelled(true);
            SoundUtil.playClick(player);
            int slot = event.getSlot();
            if (slot == 10) { ChatInputListener.requestInput(player, "core.place_cost", p -> guiManager.openAdminGuildCoreSettings(p)); }
            else if (slot == 11) { ChatInputListener.requestInput(player, "core.max_hp", p -> guiManager.openAdminGuildCoreSettings(p)); }
            else if (slot == 12) { ChatInputListener.requestInput(player, "core.break_cooldown_ticks", p -> guiManager.openAdminGuildCoreSettings(p)); }
            else if (slot == 13) { ChatInputListener.requestInput(player, "core.tier.max", p -> guiManager.openAdminGuildCoreSettings(p)); }
            else if (slot == 19) { ChatInputListener.requestInput(player, "core.sledgehammer_damage", p -> guiManager.openAdminGuildCoreSettings(p)); }
            else if (slot == 20) { ChatInputListener.requestInput(player, "core.sledgehammer_durability", p -> guiManager.openAdminGuildCoreSettings(p)); }
            else if (slot == 21) { ChatInputListener.requestInput(player, "core.raid_tnt_damage", p -> guiManager.openAdminGuildCoreSettings(p)); }
            else if (slot == 22) { ChatInputListener.requestInput(player, "core.creeper_damage", p -> guiManager.openAdminGuildCoreSettings(p)); }
            else if (slot >= 28 && slot <= 32) {
                int tier = slot - 27;
                if (event.isRightClick()) {
                    ChatInputListener.requestStringInput(player, "core.tier." + tier + ".item_cost", p -> guiManager.openAdminGuildCoreSettings(p));
                } else {
                    ChatInputListener.requestInput(player, "core.tier." + tier + ".money_cost", p -> guiManager.openAdminGuildCoreSettings(p));
                }
            }
            else if (slot == 49) { guiManager.openAdminSettings(player); }
            return;
        }

        // === OFFLINE SHIELD SETTINGS ===
        if (holder instanceof AdminShieldHolder) {
            event.setCancelled(true);
            SoundUtil.playClick(player);
            int slot = event.getSlot();
            if (slot == 10) { ChatInputListener.requestInput(player, "shield.charge_rate", p -> guiManager.openAdminShieldSettings(p)); }
            else if (slot == 11) { ChatInputListener.requestInput(player, "shield.max_minutes", p -> guiManager.openAdminShieldSettings(p)); }
            else if (slot == 12) { ChatInputListener.requestInput(player, "shield.drain_multiplier", p -> guiManager.openAdminShieldSettings(p)); }
            else if (slot == 13) { ChatInputListener.requestInput(player, "shield.activation_delay_sec", p -> guiManager.openAdminShieldSettings(p)); }
            else if (slot == 31) { guiManager.openAdminSettings(player); }
            return;
        }

        // === RAID TAG SETTINGS ===
        if (holder instanceof AdminRaidTagHolder) {
            event.setCancelled(true);
            SoundUtil.playClick(player);
            int slot = event.getSlot();
            if (slot == 10) { ChatInputListener.requestInput(player, "raidtag.exit_countdown_sec", p -> guiManager.openAdminRaidTagSettings(p)); }
            else if (slot == 11) { ChatInputListener.requestInput(player, "raidtag.disconnect_timer_sec", p -> guiManager.openAdminRaidTagSettings(p)); }
            else if (slot == 12) {
                settingsManager.set("raidtag.disable_commands", String.valueOf(!settingsManager.getBoolean("raidtag.disable_commands", true)));
                guiManager.openAdminRaidTagSettings(player);
            }
            else if (slot == 13) {
                settingsManager.set("raidtag.allow_cobweb", String.valueOf(!settingsManager.getBoolean("raidtag.allow_cobweb", true)));
                guiManager.openAdminRaidTagSettings(player);
            }
            else if (slot == 14) {
                settingsManager.set("raidtag.drop_inv_on_expire", String.valueOf(!settingsManager.getBoolean("raidtag.drop_inv_on_expire", true)));
                guiManager.openAdminRaidTagSettings(player);
            }
            else if (slot == 15) {
                settingsManager.set("raidtag.award_kill_credit", String.valueOf(!settingsManager.getBoolean("raidtag.award_kill_credit", true)));
                guiManager.openAdminRaidTagSettings(player);
            }
            else if (slot == 31) { guiManager.openAdminSettings(player); }
            return;
        }

        // === RAID TOOL CONFIG ===
        if (holder instanceof AdminRaidToolHolder) {
            event.setCancelled(true);
            SoundUtil.playClick(player);
            int slot = event.getSlot();
            if (slot == 10) {
                if (event.isRightClick()) { ChatInputListener.requestInput(player, "lockpick.weak.durability", p -> guiManager.openAdminRaidToolSettings(p)); }
                else { ChatInputListener.requestInput(player, "lockpick.weak.chance", p -> guiManager.openAdminRaidToolSettings(p)); }
            }
            else if (slot == 11) {
                if (event.isRightClick()) { ChatInputListener.requestInput(player, "lockpick.normal.durability", p -> guiManager.openAdminRaidToolSettings(p)); }
                else { ChatInputListener.requestInput(player, "lockpick.normal.chance", p -> guiManager.openAdminRaidToolSettings(p)); }
            }
            else if (slot == 12) {
                if (event.isRightClick()) { ChatInputListener.requestInput(player, "lockpick.fast.durability", p -> guiManager.openAdminRaidToolSettings(p)); }
                else { ChatInputListener.requestInput(player, "lockpick.fast.chance", p -> guiManager.openAdminRaidToolSettings(p)); }
            }
            else if (slot == 13) {
                if (event.isShiftClick()) { ChatInputListener.requestInput(player, "lockpick.reinforced.save_chance", p -> guiManager.openAdminRaidToolSettings(p)); }
                else if (event.isRightClick()) { ChatInputListener.requestInput(player, "lockpick.reinforced.durability", p -> guiManager.openAdminRaidToolSettings(p)); }
                else { ChatInputListener.requestInput(player, "lockpick.reinforced.chance", p -> guiManager.openAdminRaidToolSettings(p)); }
            }
            else if (slot == 19) {
                if (event.isRightClick()) { ChatInputListener.requestInput(player, "raidtnt.explosion_power", p -> guiManager.openAdminRaidToolSettings(p)); }
                else { ChatInputListener.requestInput(player, "raidtnt.fuse_seconds", p -> guiManager.openAdminRaidToolSettings(p)); }
            }
            else if (slot == 20) {
                if (event.isRightClick()) { ChatInputListener.requestInput(player, "creeper.explosion_power", p -> guiManager.openAdminRaidToolSettings(p)); }
                else { ChatInputListener.requestInput(player, "creeper.fuse_seconds", p -> guiManager.openAdminRaidToolSettings(p)); }
            }
            else if (slot == 49) { guiManager.openAdminSettings(player); }
            return;
        }

        // === ADMIN SHOP CATEGORY EDITOR ===
        if (holder instanceof AdminShopCategoryEditorHolder catEditorHolder) {
            event.setCancelled(true);
            SoundUtil.playClick(player);
            int slot = event.getSlot();
            int catId = catEditorHolder.getCategoryId();
            if (slot == 45) {
                ItemStack inHand = player.getInventory().getItemInMainHand();
                if (inHand != null && !inHand.getType().isAir()) {
                    ChatInputListener.requestInput(player, "new_shop_buy_price", p -> {
                        ChatInputListener.requestInput(p, "new_shop_sell_price", p2 -> {
                            long buy = settingsManager.getLong("new_shop_buy_price", 100);
                            long sell = settingsManager.getLong("new_shop_sell_price", 50);
                            ItemStack item = inHand.clone();
                            item.setAmount(1);
                            shopManager.addShopItem(catId, item, buy, sell, 10);
                            p2.sendMessage(TextUtil.format("<green>✔ Added " + item.getType() + " to shop category!</green>"));
                            guiManager.openAdminShopCategoryEditor(p2, catId);
                        });
                    });
                } else player.sendMessage(TextUtil.format("<red>Hold an item in main hand to add to category!</red>"));
                return;
            }
            if (slot == 49) { guiManager.openAdminShopHub(player); return; }
            var items = shopManager.getCategoryItems(catId);
            for (var shopItem : items) {
                if (shopItem.getSlot() == slot) {
                    ClickType click = event.getClick();
                    if (click == ClickType.LEFT) {
                        ChatInputListener.requestInput(player, "edit_shop_buy_price", p -> {
                            long buy = settingsManager.getLong("edit_shop_buy_price", shopItem.getBuyPrice());
                            shopManager.updateShopItemPrices(shopItem.getId(), catId, buy, shopItem.getSellPrice());
                            guiManager.openAdminShopCategoryEditor(p, catId);
                        });
                    } else if (click == ClickType.RIGHT) {
                        ChatInputListener.requestInput(player, "edit_shop_sell_price", p -> {
                            long sell = settingsManager.getLong("edit_shop_sell_price", shopItem.getSellPrice());
                            shopManager.updateShopItemPrices(shopItem.getId(), catId, shopItem.getBuyPrice(), sell);
                            guiManager.openAdminShopCategoryEditor(p, catId);
                        });
                    } else if (click == ClickType.SHIFT_RIGHT) {
                        shopManager.deleteShopItem(shopItem.getId(), catId);
                        player.sendMessage(TextUtil.format("<red>Removed item from category.</red>"));
                        guiManager.openAdminShopCategoryEditor(player, catId);
                    }
                    return;
                }
            }
            return;
        }

        // === TEAM MAP GUI (VIEW ONLY) ===
        if (holder instanceof TeamMapGUIHolder mapHolder) {
            event.setCancelled(true);
            SoundUtil.playClick(player);
            int slot = event.getSlot();
            if (slot == 1) { guiManager.openTeamMapGUI(player); return; }
            if (slot == 7) { player.closeInventory(); return; }
            // Map is view-only - no claiming from map anymore
            if (slot >= 9 && slot < 54) {
                // Show chunk info but don't allow claiming
                int r = slot / 9, c = slot % 9;
                int dx = c - 4, dz = r - 3;
                int cx = mapHolder.getCenterChunkX() + dx;
                int cz = mapHolder.getCenterChunkZ() + dz;
                var claim = guiManager.getClaimManager().getClaimAt(player.getWorld(), cx, cz);
                if (claim != null) {
                    player.sendMessage(TextUtil.format("<yellow>Chunk (" + cx + ", " + cz + ") - " +
                            guiManager.getForeignGuildDisplay(claim) + "</yellow>"));
                } else {
                    player.sendMessage(TextUtil.format("<gray>Chunk (" + cx + ", " + cz + ") - Wilderness</gray>"));
                }
            }
            return;
        }

        // === ADMIN PROHIBITED ITEMS ===
        if (holder instanceof AdminProhibitedHolder prohibitedHolder) {
            event.setCancelled(true);
            SoundUtil.playClick(player);
            int slot = event.getSlot();
            int currentPage = prohibitedHolder.getPage();
            if (slot == 4) {
                ItemStack inHand = player.getInventory().getItemInMainHand();
                if (inHand != null && !inHand.getType().isAir()) {
                    if (prohibitedManager != null) {
                        prohibitedManager.addProhibitedItem(inHand.getType(), "Prohibited by Royal Decree", player.getName());
                        player.sendMessage(TextUtil.format("<red>✔ Added " + inHand.getType().name() + " to Prohibited Items List!</red>"));
                    }
                } else player.sendMessage(TextUtil.format("<red>Hold an item in main hand to ban!</red>"));
                guiManager.openAdminProhibitedItems(player, currentPage);
                return;
            }
            if (slot == 45 && currentPage > 1) { guiManager.openAdminProhibitedItems(player, currentPage - 1); return; }
            if (slot == 53) { guiManager.openAdminProhibitedItems(player, currentPage + 1); return; }
            if (slot == 49) { guiManager.openAdminSettings(player); return; }
            if (prohibitedManager != null) {
                var mats = new ArrayList<>(prohibitedManager.getProhibitedMaterials());
                int pageSize = 28;
                int[] itemSlots = {10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43};
                int startIndex = (currentPage - 1) * pageSize;
                int endIndex = Math.min(startIndex + pageSize, mats.size());
                for (int i = startIndex; i < endIndex; i++) {
                    if (itemSlots[i - startIndex] == slot) {
                        Material mat = mats.get(i);
                        prohibitedManager.removeProhibitedItem(mat);
                        player.sendMessage(TextUtil.format("<green>✔ Unbanned item " + mat.name() + "!</green>"));
                        guiManager.openAdminProhibitedItems(player, currentPage);
                        return;
                    }
                }
            }
            return;
        }

        // === ADMIN REQUEST SETTINGS ===
        if (holder instanceof AdminRequestHolder) {
            event.setCancelled(true);
            SoundUtil.playClick(player);
            int slot = event.getSlot();
            if (slot == 10) ChatInputListener.requestInput(player, "requests.tpa-expire-seconds", p -> guiManager.openAdminRequestSettings(p));
            else if (slot == 12) ChatInputListener.requestInput(player, "requests.team-invite-expire-seconds", p -> guiManager.openAdminRequestSettings(p));
            else if (slot == 14) ChatInputListener.requestInput(player, "requests.trade-expire-seconds", p -> guiManager.openAdminRequestSettings(p));
            else if (slot == 26) guiManager.openAdminSettings(player);
            return;
        }

        // === ADMIN RTP SETTINGS ===
        if (holder instanceof AdminRtpHolder) {
            event.setCancelled(true);
            SoundUtil.playClick(player);
            int slot = event.getSlot();
            if (slot == 10) ChatInputListener.requestInput(player, "rtp.cooldown_sec", p -> guiManager.openAdminRtpSettings(p));
            else if (slot == 11) ChatInputListener.requestInput(player, "rtp.warmup_sec", p -> guiManager.openAdminRtpSettings(p));
            else if (slot == 12) ChatInputListener.requestInput(player, "rtp.range.min_x", p -> guiManager.openAdminRtpSettings(p));
            else if (slot == 13) ChatInputListener.requestInput(player, "rtp.range.max_x", p -> guiManager.openAdminRtpSettings(p));
            else if (slot == 14) ChatInputListener.requestInput(player, "rtp.range.min_z", p -> guiManager.openAdminRtpSettings(p));
            else if (slot == 15) ChatInputListener.requestInput(player, "rtp.range.max_z", p -> guiManager.openAdminRtpSettings(p));
            else if (slot == 26) guiManager.openAdminSettings(player);
            return;
        }

        // === ADMIN ECONOMY ===
        if (holder instanceof AdminEconomyHolder) {
            event.setCancelled(true);
            SoundUtil.playClick(player);
            int slot = event.getSlot();
            if (slot == 10) ChatInputListener.requestInput(player, "economy.starting_balance", p -> guiManager.openAdminEconomySettings(p));
            else if (slot == 12) ChatInputListener.requestInput(player, "economy.pvp_kill_reward", p -> guiManager.openAdminEconomySettings(p));
            else if (slot == 14) ChatInputListener.requestInput(player, "economy.sales_tax_percent", p -> guiManager.openAdminEconomySettings(p));
            else if (slot == 16) {
                economyManager.deposit(player.getUniqueId(), 1000, "admin_give_self");
                player.sendMessage(TextUtil.format("<green>Received +$1,000 coins from Admin Panel!</green>"));
                guiManager.openAdminEconomySettings(player);
            }
            else if (slot == 26) guiManager.openAdminSettings(player);
            return;
        }

        // === ADMIN CLAIM ===
        if (holder instanceof AdminClaimHolder) {
            event.setCancelled(true);
            SoundUtil.playClick(player);
            int slot = event.getSlot();
            if (slot == 9) {
                String current = settingsManager.getString("claims.cost.mode", "SCALER");
                String next = "CUSTOM".equalsIgnoreCase(current) ? "SCALER" : "CUSTOM";
                settingsManager.set("claims.cost.mode", next);
                guiManager.openAdminClaimSettings(player);
            }
            else if (slot == 10) ChatInputListener.requestInput(player, "claims.map.cost_coins", p -> guiManager.openAdminClaimSettings(p));
            else if (slot == 11) ChatInputListener.requestInput(player, "claims.map.cost_xp_levels", p -> guiManager.openAdminClaimSettings(p));
            else if (slot == 12) guiManager.openClaimItemSelectorGUI(player);
            else if (slot == 13) ChatInputListener.requestInput(player, "claims.cost.multiplier", p -> guiManager.openAdminClaimSettings(p));
            else if (slot == 14) {
                String current = settingsManager.getString("claims.map.coord_format", "CHUNK");
                String next = "BLOCK".equalsIgnoreCase(current) ? "CHUNK" : "BLOCK";
                settingsManager.set("claims.map.coord_format", next);
                guiManager.openAdminClaimSettings(player);
            }
            else if (slot == 15) {
                boolean disableExplosions = settingsManager.getBoolean("world.disable_explosions", false);
                settingsManager.set("world.disable_explosions", String.valueOf(!disableExplosions));
                guiManager.openAdminClaimSettings(player);
            }
            else if (slot == 16) {
                boolean current = settingsManager.getBoolean("claims.map.hide_foreign_guild_names", false);
                settingsManager.set("claims.map.hide_foreign_guild_names", String.valueOf(!current));
                guiManager.openAdminClaimSettings(player);
            }
            else if (slot == 17) {
                int purged = guiManager.getClaimManager().purgeLegacyClaims();
                player.sendMessage(TextUtil.format("<green>✔ Successfully purged " + purged + " legacy non-team claims!</green>"));
                guiManager.openAdminClaimSettings(player);
            }
            else if (slot == 26) guiManager.openAdminSettings(player);
            return;
        }

        // === CLAIM ITEM SELECTOR ===
        if (holder instanceof ClaimItemSelectorHolder) {
            int rawSlot = event.getRawSlot();
            int slot = event.getSlot();
            if (rawSlot >= 27 || (slot >= 10 && slot <= 16)) return; // Allow item placement
            event.setCancelled(true);
            SoundUtil.playClick(player);
            if (slot == 22) {
                StringBuilder sb = new StringBuilder();
                String firstMat = null;
                int firstAmt = 0;
                for (int s = 10; s <= 16; s++) {
                    ItemStack item = event.getInventory().getItem(s);
                    if (item != null && item.getType() != Material.AIR) {
                        if (sb.length() > 0) sb.append(";");
                        sb.append(item.getType().name()).append(":").append(item.getAmount());
                        if (firstMat == null) {
                            firstMat = item.getType().name();
                            firstAmt = item.getAmount();
                        }
                    }
                }
                if (sb.length() > 0) {
                    settingsManager.set("claims.map.cost_items", sb.toString());
                    if (firstMat != null) {
                        settingsManager.set("claims.map.cost_item_material", firstMat);
                        settingsManager.set("claims.map.cost_item_amount", String.valueOf(firstAmt));
                    }
                    player.sendMessage(TextUtil.format("<green>✔ Saved multi-item claim requirement!</green>"));
                } else {
                    player.sendMessage(TextUtil.format("<red>✖ Please place at least one item stack in slots 10-16!</red>"));
                }
                guiManager.openAdminClaimSettings(player);
            }
            else if (slot == 26) guiManager.openAdminClaimSettings(player);
            return;
        }

        // === ADMIN TEAM ===
        if (holder instanceof AdminTeamHolder) {
            event.setCancelled(true);
            SoundUtil.playClick(player);
            int slot = event.getSlot();
            if (slot == 9) ChatInputListener.requestInput(player, "teams.creation_cost", p -> guiManager.openAdminTeamSettings(p));
            else if (slot == 10) ChatInputListener.requestInput(player, "teams.base_max_members", p -> guiManager.openAdminTeamSettings(p));
            else if (slot == 11) {
                boolean current = settingsManager.getBoolean("teams.auto_transfer_leader_on_leave", true);
                settingsManager.set("teams.auto_transfer_leader_on_leave", String.valueOf(!current));
                guiManager.openAdminTeamSettings(player);
            }
            else if (slot == 12) ChatInputListener.requestInput(player, "teams.max_guild_level", p -> guiManager.openAdminTeamSettings(p));
            else if (slot == 13) {
                boolean current = settingsManager.getBoolean("teams.transfer_leader_allow_offline", true);
                settingsManager.set("teams.transfer_leader_allow_offline", String.valueOf(!current));
                guiManager.openAdminTeamSettings(player);
            }
            else if (slot == 14) guiManager.openAdminGuildLevelsGUI(player, 1);
            else if (slot == 26) guiManager.openAdminSettings(player);
            return;
        }

        // === ADMIN GUILD LEVELS ===
        if (holder instanceof AdminGuildLevelsHolder levelsHolder) {
            event.setCancelled(true);
            SoundUtil.playClick(player);
            int slot = event.getSlot();
            int[] itemSlots = {10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43};
            int clickedIdx = -1;
            for (int i = 0; i < itemSlots.length; i++) {
                if (itemSlots[i] == slot) { clickedIdx = i; break; }
            }
            if (clickedIdx != -1) {
                int targetLevel = (levelsHolder.getPage() - 1) * 28 + clickedIdx + 1;
                ChatInputListener.requestInput(player, "teams.max_claims_level_" + targetLevel,
                        p -> guiManager.openAdminGuildLevelsGUI(p, levelsHolder.getPage()));
                return;
            }
            if (slot == 45 && levelsHolder.getPage() > 1) guiManager.openAdminGuildLevelsGUI(player, levelsHolder.getPage() - 1);
            else if (slot == 53) {
                int maxLevel = settingsManager.getInt("teams.max_guild_level", 5);
                int totalPages = Math.max(1, (int) Math.ceil((double) maxLevel / 28.0));
                if (levelsHolder.getPage() < totalPages) guiManager.openAdminGuildLevelsGUI(player, levelsHolder.getPage() + 1);
            }
            else if (slot == 49) guiManager.openAdminTeamSettings(player);
            return;
        }

        // === ADMIN COMBAT ===
        if (holder instanceof AdminCombatHolder) {
            event.setCancelled(true);
            SoundUtil.playClick(player);
            int slot = event.getSlot();
            if (slot == 9) ChatInputListener.requestInput(player, "combat.tag_duration", p -> guiManager.openAdminCombatSettings(p));
            else if (slot == 10) {
                boolean disableCmds = settingsManager.getBoolean("combat.disable_commands", true);
                settingsManager.set("combat.disable_commands", String.valueOf(!disableCmds));
                guiManager.openAdminCombatSettings(player);
            }
            else if (slot == 11) ChatInputListener.requestInput(player, "combat.enderpearl_cooldown", p -> guiManager.openAdminCombatSettings(p));
            else if (slot == 12) ChatInputListener.requestInput(player, "combat.windcharge_cooldown", p -> guiManager.openAdminCombatSettings(p));
            else if (slot == 13) ChatInputListener.requestInput(player, "combat.mace_cooldown", p -> guiManager.openAdminCombatSettings(p));
            else if (slot == 14) {
                boolean val = settingsManager.getBoolean("item.disabled_global.shield", false);
                settingsManager.set("item.disabled_global.shield", String.valueOf(!val));
                guiManager.openAdminCombatSettings(player);
            }
            else if (slot == 15) {
                boolean val = settingsManager.getBoolean("combat.riptide_enabled", false);
                settingsManager.set("combat.riptide_enabled", String.valueOf(!val));
                guiManager.openAdminCombatSettings(player);
            }
            else if (slot == 16) {
                boolean val = settingsManager.getBoolean("combat.crystal_enabled", false);
                settingsManager.set("combat.crystal_enabled", String.valueOf(!val));
                guiManager.openAdminCombatSettings(player);
            }
            else if (slot == 17) {
                boolean val = settingsManager.getBoolean("combat.anchor_enabled", false);
                settingsManager.set("combat.anchor_enabled", String.valueOf(!val));
                guiManager.openAdminCombatSettings(player);
            }
            else if (slot == 26) guiManager.openAdminSettings(player);
            return;
        }

        // === ADMIN SCOREBOARD ===
        if (holder instanceof AdminScoreboardHolder) {
            event.setCancelled(true);
            SoundUtil.playClick(player);
            int slot = event.getSlot();
            if (slot == 11) ChatInputListener.requestInput(player, "scoreboard.update_ticks", p -> guiManager.openAdminScoreboardSettings(p));
            else if (slot == 13) {
                if (scoreboardManager.isScoreboardsDisabled()) {
                    scoreboardManager.enableScoreboards();
                    player.sendMessage(TextUtil.format("<green>Enabled server scoreboards!</green>"));
                } else {
                    scoreboardManager.clearServerScoreboards();
                    player.sendMessage(TextUtil.format("<green>🧹 Wiped & disabled all server scoreboards!</green>"));
                }
                guiManager.openAdminScoreboardSettings(player);
            }
            else if (slot == 26) guiManager.openAdminSettings(player);
            return;
        }

        // === ADMIN AUCTION ===
        if (holder instanceof AdminAuctionHolder) {
            event.setCancelled(true);
            SoundUtil.playClick(player);
            int slot = event.getSlot();
            if (slot == 10) ChatInputListener.requestInput(player, "auction.listing_fee", p -> guiManager.openAdminAuctionSettings(p));
            else if (slot == 12) ChatInputListener.requestInput(player, "auction.duration_hours_default", p -> guiManager.openAdminAuctionSettings(p));
            else if (slot == 14) ChatInputListener.requestInput(player, "auction.max_listing_price", p -> guiManager.openAdminAuctionSettings(p));
            else if (slot == 16) ChatInputListener.requestInput(player, "auction.listing_cooldown_sec", p -> guiManager.openAdminAuctionSettings(p));
            else if (slot == 26) guiManager.openAdminSettings(player);
            return;
        }

        // === ADMIN DEBUG ===
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
            }
            else if (slot == 26) guiManager.openAdminSettings(player);
            return;
        }

        // === SIMPLE BACK-BUTTON GUIS ===
        if (holder instanceof AdminKillHolder) {
            event.setCancelled(true);
            SoundUtil.playClick(player);
            if (event.getSlot() == 26) guiManager.openAdminSettings(player);
            return;
        }

        // === AUCTION PURCHASE CONFIRM ===
        if (holder instanceof AuctionConfirmHolder confirmHolder) {
            event.setCancelled(true);
            SoundUtil.playClick(player);
            int slot = event.getSlot();
            if (slot == 11) {
                AuctionItem item = confirmHolder.getAuctionItem();
                if (auctionManager.buyItem(player, item)) {
                    player.sendMessage(TextUtil.format("<green>Successfully purchased " + item.getItem().getType() +
                            " for $" + item.getPrice() + "!</green>"));
                } else {
                    player.sendMessage(TextUtil.format("<red>Could not complete purchase.</red>"));
                }
                player.closeInventory();
            }
            else if (slot == 15) guiManager.openAuctionHouse(player);
            return;
        }

        // === TEAM GUI ===
        if (holder instanceof TeamGUIHolder teamHolder) {
            event.setCancelled(true);
            SoundUtil.playClick(player);
            int slot = event.getSlot();
            Team team = teamManager.getTeam(teamHolder.getTeamId());
            if (team == null) return;
            if (slot == 11) guiManager.openTeamMembersGUI(player, team, 1);
            else if (slot == 12) {
                player.sendMessage(TextUtil.format("<gold>🏦 Team Bank: <green>$" + team.getBankBalance() +
                        "</green> | Use /team bank deposit <amount> or withdraw</gold>"));
            }
            else if (slot == 14) guiManager.openTeamVault(player, team, 1);
            else if (slot == 16) guiManager.openTeamUpgrades(player, team);
            else if (slot == 28) guiManager.openTeamPermissions(player, team, "MEMBER");
            else if (slot == 30) guiManager.openTeamMapGUI(player);
            else if (slot == 32) {
                if (team.getHomeLocation() != null) {
                    player.teleportAsync(team.getHomeLocation()).thenAccept(success -> {
                        if (success) player.sendMessage(TextUtil.format("<green>Teleported to team home!</green>"));
                    });
                } else player.sendMessage(TextUtil.format("<red>Team home location is not set.</red>"));
            }
            return;
        }

        // === TEAM MEMBERS ===
        if (holder instanceof TeamMembersHolder membersHolder) {
            event.setCancelled(true);
            SoundUtil.playClick(player);
            int slot = event.getSlot();
            Team team = teamManager.getTeam(membersHolder.getTeamId());
            if (team == null) return;
            if (slot == 45 && membersHolder.getPage() > 1) { guiManager.openTeamMembersGUI(player, team, membersHolder.getPage() - 1); return; }
            if (slot == 53) { guiManager.openTeamMembersGUI(player, team, membersHolder.getPage() + 1); return; }
            if (slot == 49) { guiManager.openTeamMenu(player, team); return; }
            int[] itemSlots = {10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43};
            int clickedIdx = -1;
            for (int i = 0; i < itemSlots.length; i++) {
                if (itemSlots[i] == slot) { clickedIdx = i; break; }
            }
            if (clickedIdx != -1) {
                List<UUID> memberUuids = teamManager.getTeamMembers(team.getId());
                int targetIndex = (membersHolder.getPage() - 1) * 28 + clickedIdx;
                if (targetIndex < memberUuids.size()) {
                    UUID targetUuid = memberUuids.get(targetIndex);
                    org.bukkit.OfflinePlayer targetOp = Bukkit.getPlayer(targetUuid) != null ?
                            Bukkit.getPlayer(targetUuid) : Bukkit.getOfflinePlayer(targetUuid);
                    String targetName = targetOp.getName();
                    if (targetName != null) {
                        if (event.isShiftClick() && event.isRightClick()) {
                            guiManager.openTeamKickConfirmGUI(player, team, targetOp);
                            return;
                        }
                        else if (event.isRightClick()) teamManager.demotePlayer(player, targetName);
                        else teamManager.promotePlayer(player, targetName);
                        guiManager.openTeamMembersGUI(player, team, membersHolder.getPage());
                    }
                }
            }
            return;
        }

        // === TEAM KICK CONFIRM ===
        if (holder instanceof TeamKickConfirmHolder confirmHolder) {
            event.setCancelled(true);
            SoundUtil.playClick(player);
            int slot = event.getSlot();
            Team team = teamManager.getTeam(confirmHolder.getTeamId());
            if (team == null) return;
            if (slot == 11) { teamManager.kickPlayer(player, confirmHolder.getTargetName()); guiManager.openTeamMembersGUI(player, team, 1); }
            else if (slot == 15) guiManager.openTeamMembersGUI(player, team, 1);
            return;
        }

        // === TEAM PERMISSIONS ===
        if (holder instanceof TeamPermissionsHolder permsHolder) {
            event.setCancelled(true);
            SoundUtil.playClick(player);
            int slot = event.getSlot();
            Team team = teamManager.getTeam(permsHolder.getTeamId());
            if (team == null) return;
            if (slot == 49) { guiManager.openTeamMenu(player, team); return; }
            if (!player.getUniqueId().equals(team.getLeaderUuid())) {
                player.sendMessage(TextUtil.format("<red>✖ Only the Guild Leader can modify team permissions!</red>"));
                return;
            }
            if (slot == 10) { guiManager.openTeamPermissions(player, team, "OFFICER"); return; }
            if (slot == 12) { guiManager.openTeamPermissions(player, team, "MEMBER"); return; }
            if (slot == 14) { guiManager.openTeamPermissions(player, team, "RECRUIT"); return; }
            String selectedRole = permsHolder.getSelectedRole();
            String[] nodes = {"BANK_DEPOSIT","BANK_WITHDRAW","VAULT_ACCESS","CLAIM_LAND","BUILD","INVITE_MEMBERS","KICK_MEMBERS","SET_HOME","UPGRADE_TEAM"};
            int[] slots = {19,20,21,22,23,28,29,30,31};
            for (int i = 0; i < slots.length; i++) {
                if (slots[i] == slot) {
                    String node = nodes[i];
                    guiManager.getPermissionManager().togglePermission(team.getId(), selectedRole, node);
                    guiManager.openTeamPermissions(player, team, selectedRole);
                    return;
                }
            }
            return;
        }

        // === TEAM BANK ===
        if (holder instanceof TeamBankGUIHolder bankHolder) {
            event.setCancelled(true);
            SoundUtil.playClick(player);
            int slot = event.getSlot();
            Team team = bankHolder.getTeam();
            if (team == null) return;
            if (slot == 49) { guiManager.openTeamMenu(player, team); return; }
            com.guildcore.teams.TeamBankManager bankMgr = guiManager.getTeamBankManager();
            if (slot == 19) bankMgr.deposit(team, player.getUniqueId(), 100);
            else if (slot == 20) bankMgr.deposit(team, player.getUniqueId(), 1000);
            else if (slot == 21) bankMgr.deposit(team, player.getUniqueId(), 10000);
            else if (slot == 22) bankMgr.deposit(team, player.getUniqueId(), 100000);
            else if (slot == 23) {
                ChatInputListener.requestInput(player, "team_bank_deposit_custom", p -> {
                    long amt = settingsManager.getLong("team_bank_deposit_custom", 0);
                    if (amt > 0) bankMgr.deposit(team, p.getUniqueId(), amt);
                    guiManager.openTeamBankGUI(p, team);
                });
                return;
            }
            else if (slot == 28) bankMgr.withdraw(team, player.getUniqueId(), 100);
            else if (slot == 29) bankMgr.withdraw(team, player.getUniqueId(), 1000);
            else if (slot == 30) bankMgr.withdraw(team, player.getUniqueId(), 10000);
            else if (slot == 31) bankMgr.withdraw(team, player.getUniqueId(), 100000);
            else if (slot == 32) {
                ChatInputListener.requestInput(player, "team_bank_withdraw_custom", p -> {
                    long amt = settingsManager.getLong("team_bank_withdraw_custom", 0);
                    if (amt > 0) bankMgr.withdraw(team, p.getUniqueId(), amt);
                    guiManager.openTeamBankGUI(p, team);
                });
                return;
            }
            guiManager.openTeamBankGUI(player, team);
            return;
        }

        // === TEAM TRANSFER LEADER CONFIRM ===
        if (holder instanceof TeamTransferLeaderConfirmHolder confirmHolder) {
            event.setCancelled(true);
            SoundUtil.playClick(player);
            int slot = event.getSlot();
            if (slot == 11) {
                teamManager.transferLeadership(player, confirmHolder.getTargetSuccessor().getUniqueId());
                player.closeInventory();
            }
            else if (slot == 15) {
                player.sendMessage(TextUtil.format("<yellow>Leadership transfer cancelled.</yellow>"));
                player.closeInventory();
            }
            return;
        }

        // === TEAM DISBAND CONFIRM ===
        if (holder instanceof TeamDisbandConfirmHolder disbandHolder) {
            event.setCancelled(true);
            SoundUtil.playClick(player);
            int slot = event.getSlot();
            if (slot == 11) {
                teamManager.disbandTeam(player);
                player.closeInventory();
            }
            else if (slot == 15) {
                player.sendMessage(TextUtil.format("<yellow>Guild disband cancelled.</yellow>"));
                player.closeInventory();
            }
            return;
        }

        // === TEAM UPGRADES ===
        if (holder instanceof TeamUpgradesHolder upgradesHolder) {
            event.setCancelled(true);
            SoundUtil.playClick(player);
            int slot = event.getSlot();
            Team team = teamManager.getTeam(upgradesHolder.getTeamId());
            if (team == null) return;
            if (slot == 26) { guiManager.openTeamMenu(player, team); return; }
            String role = teamManager.getPlayerRole(player.getUniqueId());
            if (!guiManager.getPermissionManager().hasPermission(team.getId(), role, "UPGRADE_TEAM")) {
                player.sendMessage(TextUtil.format("<red>✖ You do not have team permission to upgrade team features!</red>"));
                return;
            }
            if (slot == 11) {
                long memberCost = settingsManager.getLong("teams.upgrade.member_cap_cost", 5000);
                if (team.getBankBalance() >= memberCost) {
                    team.setBankBalance(team.getBankBalance() - memberCost);
                    team.setMaxMembers(team.getMaxMembers() + 2);
                    teamManager.saveTeamMaxMembers(team.getId(), team.getMaxMembers());
                    player.sendMessage(TextUtil.format("<green>Upgraded Team Member Cap to " + team.getMaxMembers() + "!</green>"));
                    guiManager.openTeamUpgrades(player, team);
                } else {
                    player.sendMessage(TextUtil.format("<red>✖ Insufficient Team Bank balance ($" +
                            String.format("%,d", memberCost) + " required).</red>"));
                }
            }
            return;
        }

        // === OTHER GUI HOLDERS PROTECTION ===
        if (holder instanceof ClaimFlagsGUIHolder || holder instanceof StatsGUIHolder ||
                holder instanceof ShulkerPreviewHolder || holder instanceof AnvilSearchHolder) {
            event.setCancelled(true);
            SoundUtil.playClick(player);
        }
    }

    // Helper methods
    private boolean containsSlot(int[] slots, int target) {
        for (int s : slots) if (s == target) return true;
        return false;
    }

    private List<ItemStack> getOfferItems(Inventory inv, int[] slots) {
        List<ItemStack> list = new ArrayList<>();
        for (int s : slots) {
            ItemStack item = inv.getItem(s);
            if (item != null && item.getType() != Material.AIR) list.add(item.clone());
        }
        return list;
    }

    private void clearOfferSlots(Inventory inv, int[] slots) {
        for (int s : slots) inv.setItem(s, null);
    }

    private int countFreeInventorySlots(Player player) {
        int count = 0;
        ItemStack[] storage = player.getInventory().getStorageContents();
        for (ItemStack item : storage) {
            if (item == null || item.getType() == Material.AIR) count++;
        }
        return count;
    }

    private void resetReadyStates(TradeGUIHolder holder, Inventory inv, Player p1, Player p2) {
        holder.setP1Ready(false);
        holder.setP2Ready(false);
        holder.setCountdownSeconds(-1);
        if (tradeManager != null) tradeManager.updateTradeGUIControls(inv, holder, p1, p2);
    }

    private void startTradeCountdown(TradeGUIHolder holder, Inventory inv, Player p1, Player p2) {
        holder.setCountdownSeconds(3);
        Runnable task = new Runnable() {
            int secondsLeft = 3;
            @Override
            public void run() {
                if (!p1.isOnline() || !p2.isOnline()) return;
                if (!holder.isP1Ready() || !holder.isP2Ready()) {
                    resetReadyStates(holder, inv, p1, p2);
                    return;
                }
                if (secondsLeft > 0) {
                    p1.sendActionBar(TextUtil.format("<gold>⌛ Trade completing in <green>" + secondsLeft + "s</green>...</gold>"));
                    p2.sendActionBar(TextUtil.format("<gold>⌛ Trade completing in <green>" + secondsLeft + "s</green>...</gold>"));
                    secondsLeft--;
                    scheduler.runLater(p1, this, 20L);
                } else {
                    List<ItemStack> p1Items = getOfferItems(inv, new int[]{0,1,2,3,4,5,6,7,8});
                    List<ItemStack> p2Items = getOfferItems(inv, new int[]{18,19,20,21,22,23,24,25,26});
                    int p1FreeSlots = countFreeInventorySlots(p1);
                    int p2FreeSlots = countFreeInventorySlots(p2);
                    if (p1FreeSlots < p2Items.size() || p2FreeSlots < p1Items.size()) {
                        p1.sendMessage(TextUtil.format("<red>✖ Trade failed: Not enough inventory space!</red>"));
                        p2.sendMessage(TextUtil.format("<red>✖ Trade failed: Not enough inventory space!</red>"));
                        resetReadyStates(holder, inv, p1, p2);
                        return;
                    }
                    holder.setCompleted(true);
                    holder.setClosed(true);
                    clearOfferSlots(inv, new int[]{0,1,2,3,4,5,6,7,8});
                    clearOfferSlots(inv, new int[]{18,19,20,21,22,23,24,25,26});
                    for (ItemStack item : p2Items) p1.getInventory().addItem(item);
                    for (ItemStack item : p1Items) p2.getInventory().addItem(item);
                    p1.sendMessage(TextUtil.format("<green>✔ Trade completed successfully!</green>"));
                    p2.sendMessage(TextUtil.format("<green>✔ Trade completed successfully!</green>"));
                    tradeManager.getActiveTradeSessions().remove(p1.getUniqueId());
                    tradeManager.getActiveTradeSessions().remove(p2.getUniqueId());
                    p1.closeInventory();
                    p2.closeInventory();
                }
            }
        };
        task.run();
    }
}