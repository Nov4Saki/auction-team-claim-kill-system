package com.guildcore.gui;

import com.guildcore.auction.AuctionItem;
import com.guildcore.auction.AuctionManager;
import com.guildcore.config.SettingsManager;
import com.guildcore.crates.Crate;
import com.guildcore.crates.CrateAdminGUIHolder;
import com.guildcore.crates.CrateAdminHubHolder;
import com.guildcore.crates.CrateConfirmGUIHolder;
import com.guildcore.crates.CrateGUIHolder;
import com.guildcore.crates.CrateManager;
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

    public GUIClickListener(GUIManager guiManager, AuctionManager auctionManager, TeamManager teamManager, TeamUpgradeManager upgradeManager, TeamVaultManager vaultManager, EconomyManager economyManager, SettingsManager settingsManager, ScoreboardManager scoreboardManager, CrateManager crateManager, ShopManager shopManager, SchedulerWrapper scheduler) {
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

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory() == null) return;
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof VaultGUIHolder vaultHolder) {
            ItemStack[] contents = new ItemStack[54];
            ItemStack[] invContents = event.getInventory().getContents();
            for (int i = 0; i < Math.min(54, invContents.length); i++) {
                ItemStack item = invContents[i];
                if (item != null && item.getType() != Material.AIR && item.getType() != Material.YELLOW_STAINED_GLASS_PANE && item.getType() != Material.GRAY_STAINED_GLASS_PANE && item.getType() != Material.BARRIER) {
                    contents[i] = item.clone();
                }
            }
            vaultManager.saveVaultPage(vaultHolder.getTeamId(), 1, contents);
            if (event.getPlayer() instanceof Player player) {
                vaultManager.logVaultAction(vaultHolder.getTeamId(), player.getUniqueId(), "VAULT_CLOSE", 1, "SAVE");
            }
            DebugManager.log(DebugFlag.VAULT_SERIALIZATION, "Saved team vault on close for team " + vaultHolder.getTeamId());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory() == null) return;
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder == null) return;

        if (!(event.getWhoClicked() instanceof Player player)) return;

        // Crate Choice Confirmation GUI
        if (holder instanceof CrateConfirmGUIHolder confirmHolder) {
            event.setCancelled(true);
            SoundUtil.playClick(player);
            int slot = event.getSlot();

            Crate crate = crateManager.getCrate(confirmHolder.getCrateName());
            if (crate == null) return;

            if (slot == 11) { // Confirm
                if (crateManager.consumeKey(player, crate)) {
                    ItemStack selected = confirmHolder.getSelectedItem().clone();
                    player.getInventory().addItem(selected);
                    player.sendMessage(TextUtil.format("<green>🎁 You chose " + selected.getType() + " from crate '" + crate.getDisplayName() + "'!</green>"));
                } else {
                    player.sendMessage(TextUtil.format("<red>No crate key found in inventory!</red>"));
                }
                player.closeInventory();
            } else if (slot == 15) { // Cancel
                crateManager.openCrateChoiceMenu(player, crate);
            }
            return;
        }

        // Crate Admin Hub GUI Navigation & Actions
        if (holder instanceof CrateAdminHubHolder) {
            event.setCancelled(true);
            SoundUtil.playClick(player);
            int slot = event.getSlot();

            if (slot == 45) { // Create New Crate
                ChatInputListener.requestStringInput(player, "new_crate_name", p -> {
                    String name = settingsManager.getString("new_crate_name", "");
                    if (!name.isEmpty()) {
                        ItemStack inHand = p.getInventory().getItemInMainHand();
                        ItemStack keyItem = (inHand != null && !inHand.getType().isAir()) ? inHand.clone() : new ItemStack(Material.TRIPWIRE_HOOK);
                        keyItem.setAmount(1);
                        crateManager.createCrate(name, name, keyItem);
                        p.sendMessage(TextUtil.format("<green>✔ Created choice crate '" + name + "'!</green>"));
                    }
                    crateManager.openCrateAdminHub(p);
                });
                return;
            }

            if (slot == 53) {
                player.closeInventory();
                return;
            }

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
                        player.sendMessage(TextUtil.format("<green>✔ Updated key item for crate '" + crate.getDisplayName() + "' to " + inHand.getType() + "!</green>"));
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

        // Choice Crate GUI Selection & Inspection
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
                    player.sendMessage(TextUtil.format("<red>You need a matching Crate Key (" + crate.getKeyItem().getType() + ") in your inventory to claim items!</red>"));
                }
            }
            return;
        }

        // Choice Crate Admin Content Editor
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
                player.sendMessage(TextUtil.format("<green>✔ Saved crate contents for '" + adminCrateHolder.getCrateName() + "'!</green>"));
                player.closeInventory();
            }
            return;
        }

        // Server Shop Buy/Sell GUI
        if (holder instanceof ShopGUIHolder shopHolder) {
            event.setCancelled(true);
            SoundUtil.playClick(player);
            int slot = event.getSlot();

            if (shopHolder.getCategoryId() == 0) { // Category Selection
                var categories = shopManager.getCategories();
                for (var cat : categories.values()) {
                    if (cat.getSlot() == slot) {
                        shopManager.openShopCategoryMenu(player, cat.getId());
                        return;
                    }
                }
            } else { // Category Items Buy/Sell
                if (slot == 49) {
                    shopManager.openShopMainMenu(player);
                    return;
                }

                List<ShopItem> items = shopManager.getCategoryItems(shopHolder.getCategoryId());
                for (ShopItem shopItem : items) {
                    if (shopItem.getSlot() == slot) {
                        ClickType click = event.getClick();
                        if (click == ClickType.LEFT) {
                            shopManager.buyItem(player, shopItem, 1);
                        } else if (click == ClickType.SHIFT_LEFT) {
                            shopManager.buyItem(player, shopItem, 16);
                        } else if (click == ClickType.RIGHT) {
                            shopManager.sellItem(player, shopItem, 1);
                        } else if (click == ClickType.SHIFT_RIGHT) {
                            shopManager.sellItem(player, shopItem, 16);
                        }
                        return;
                    }
                }
            }
            return;
        }

        // Shared Single-Page Team Vault GUI Click & Inventory Handling
        if (holder instanceof VaultGUIHolder vaultHolder) {
            Team team = teamManager.getTeam(vaultHolder.getTeamId());
            if (team == null) return;

            event.setCancelled(true);

            org.bukkit.inventory.Inventory clickedInv = event.getClickedInventory();
            if (clickedInv == null) return;

            int unlocked = Math.min(54, Math.max(9, team.getVaultSlots()));
            org.bukkit.inventory.Inventory topInv = event.getView().getTopInventory();

            // Case A: Clicked inside top inventory (Team Vault)
            if (clickedInv.equals(topInv)) {
                int slot = event.getSlot();

                // 1. Clicked Yellow Glass Pane (Next Unlockable Slot)
                if (slot == unlocked && unlocked < 54) {
                    SoundUtil.playClick(player);
                    long baseCost = settingsManager.getLong("teams.vault.base_slot_cost", 500L);
                    long stepCost = settingsManager.getLong("teams.vault.slot_cost_step", 250L);
                    long cost = baseCost + (unlocked - 9) * stepCost;

                    if (team.getBankBalance() >= cost) {
                        team.setBankBalance(team.getBankBalance() - cost);
                        team.setVaultSlots(unlocked + 1);
                        teamManager.saveTeamVaultSlots(team.getId(), team.getVaultSlots());
                        SoundUtil.playSuccess(player);
                        player.sendMessage(TextUtil.format("<green>✔ Unlocked Team Vault Slot #" + (unlocked + 1) + " for $" + String.format("%,d", cost) + " Team Bank!</green>"));
                        guiManager.refreshTeamVault(team.getId());
                    } else {
                        SoundUtil.playError(player);
                        player.sendMessage(TextUtil.format("<red>✖ Insufficient Team Bank balance ($" + String.format("%,d", cost) + " required). Current Bank: $" + String.format("%,d", team.getBankBalance()) + "</red>"));
                    }
                    return;
                }

                // 2. Clicked Locked Slot (Dark Grey Glass Pane)
                if (slot >= unlocked) {
                    SoundUtil.playError(player);
                    return;
                }

                // 3. Clicked Unlocked Slot (Slots 0 to unlocked - 1)
                ItemStack item = topInv.getItem(slot);
                if (item != null && item.getType() != Material.AIR && item.getType() != Material.YELLOW_STAINED_GLASS_PANE && item.getType() != Material.GRAY_STAINED_GLASS_PANE) {
                    SoundUtil.playClick(player);
                    ItemStack itemToGive = item.clone();
                    java.util.HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(itemToGive);
                    if (leftover.isEmpty()) {
                        topInv.setItem(slot, null);
                    } else {
                        topInv.setItem(slot, leftover.get(0));
                    }
                    saveVaultTopInventory(team.getId(), topInv, unlocked);
                    vaultManager.logVaultAction(team.getId(), player.getUniqueId(), itemToGive.getType().name(), itemToGive.getAmount() - (leftover.isEmpty() ? 0 : leftover.get(0).getAmount()), "WITHDRAW");
                    guiManager.refreshTeamVault(team.getId());
                }
                return;
            }

            // Case B: Clicked inside bottom inventory (Player's Inventory)
            if (clickedInv.equals(event.getView().getBottomInventory())) {
                ItemStack itemToDeposit = event.getCurrentItem();
                if (itemToDeposit == null || itemToDeposit.getType() == Material.AIR) return;

                SoundUtil.playClick(player);
                ItemStack stackToDeposit = itemToDeposit.clone();
                int originalAmount = stackToDeposit.getAmount();

                // Merge into existing matching stacks in unlocked slots (0 to unlocked - 1)
                for (int i = 0; i < unlocked; i++) {
                    ItemStack slotItem = topInv.getItem(i);
                    if (slotItem != null && slotItem.getType() != Material.AIR && slotItem.isSimilar(stackToDeposit)) {
                        int maxStack = slotItem.getMaxStackSize();
                        int space = maxStack - slotItem.getAmount();
                        if (space > 0) {
                            int toAdd = Math.min(space, stackToDeposit.getAmount());
                            slotItem.setAmount(slotItem.getAmount() + toAdd);
                            stackToDeposit.setAmount(stackToDeposit.getAmount() - toAdd);
                            topInv.setItem(i, slotItem);
                            if (stackToDeposit.getAmount() <= 0) break;
                        }
                    }
                }

                // Place remaining stack into first empty unlocked slot (0 to unlocked - 1)
                if (stackToDeposit.getAmount() > 0) {
                    for (int i = 0; i < unlocked; i++) {
                        ItemStack slotItem = topInv.getItem(i);
                        if (slotItem == null || slotItem.getType() == Material.AIR) {
                            topInv.setItem(i, stackToDeposit.clone());
                            stackToDeposit.setAmount(0);
                            break;
                        }
                    }
                }

                int depositedAmount = originalAmount - stackToDeposit.getAmount();
                if (depositedAmount > 0) {
                    itemToDeposit.setAmount(stackToDeposit.getAmount());
                    saveVaultTopInventory(team.getId(), topInv, unlocked);
                    vaultManager.logVaultAction(team.getId(), player.getUniqueId(), itemToDeposit.getType().name(), depositedAmount, "DEPOSIT");
                    guiManager.refreshTeamVault(team.getId());
                } else {
                    SoundUtil.playError(player);
                    player.sendMessage(TextUtil.format("<red>✖ No free unlocked vault slots available!</red>"));
                }
                return;
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
                    Inventory preview = Bukkit.createInventory(new ShulkerPreviewHolder(), 27, TextUtil.format("<gradient:#9D50BB:#6E48AA><b>📦 Shulker Vault Inspection</b></gradient> <gray>(Read-Only)</gray>"));
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

        // RTP World Choice GUI
        if (holder instanceof com.guildcore.gui.holders.RTPWorldGUIHolder) {
            event.setCancelled(true);
            SoundUtil.playClick(player);
            int slot = event.getSlot();

            if (slot == 11) {
                World normalWorld = Bukkit.getWorlds().stream().filter(w -> w.getEnvironment() == World.Environment.NORMAL).findFirst().orElse(player.getWorld());
                player.performCommand("rtp " + normalWorld.getName());
            } else if (slot == 13) {
                World netherWorld = Bukkit.getWorlds().stream().filter(w -> w.getEnvironment() == World.Environment.NETHER).findFirst().orElse(null);
                if (netherWorld != null) {
                    player.performCommand("rtp " + netherWorld.getName());
                } else {
                    player.sendMessage(TextUtil.format("<red>Nether world not found!</red>"));
                }
            } else if (slot == 15) {
                World endWorld = Bukkit.getWorlds().stream().filter(w -> w.getEnvironment() == World.Environment.THE_END).findFirst().orElse(null);
                if (endWorld != null) {
                    player.performCommand("rtp " + endWorld.getName());
                } else {
                    player.sendMessage(TextUtil.format("<red>End world not found!</red>"));
                }
            }
            player.closeInventory();
            return;
        }

        // Admin Shop Hub GUI
        if (holder instanceof com.guildcore.gui.holders.AdminShopHubHolder) {
            event.setCancelled(true);
            SoundUtil.playClick(player);
            int slot = event.getSlot();

            if (slot == 45) { // Create Category
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

            if (slot == 49) {
                guiManager.openAdminSettings(player);
                return;
            }

            var categories = new ArrayList<>(shopManager.getCategories().values());
            for (var cat : categories) {
                if (cat.getSlot() == slot) {
                    ClickType click = event.getClick();
                    if (click == ClickType.LEFT) {
                        guiManager.openAdminShopCategoryEditor(player, cat.getId());
                    } else if (click == ClickType.RIGHT) {
                        ItemStack inHand = player.getInventory().getItemInMainHand();
                        if (inHand != null && !inHand.getType().isAir()) {
                            shopManager.updateCategoryIcon(cat.getId(), inHand.getType());
                            player.sendMessage(TextUtil.format("<green>✔ Updated category icon for '" + cat.getName() + "'!</green>"));
                        } else {
                            player.sendMessage(TextUtil.format("<red>Hold an item in hand to set as icon!</red>"));
                        }
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

        // Admin Shop Category Item Editor
        if (holder instanceof com.guildcore.gui.holders.AdminShopCategoryEditorHolder catEditorHolder) {
            event.setCancelled(true);
            SoundUtil.playClick(player);
            int slot = event.getSlot();
            int catId = catEditorHolder.getCategoryId();

            if (slot == 45) { // Add item in hand
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
                } else {
                    player.sendMessage(TextUtil.format("<red>Hold an item in main hand to add to category!</red>"));
                }
                return;
            }

            if (slot == 49) {
                guiManager.openAdminShopHub(player);
                return;
            }

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

        // Interactive Glass Pane Team Map GUI
        if (holder instanceof com.guildcore.gui.holders.TeamMapGUIHolder mapHolder) {
            event.setCancelled(true);
            SoundUtil.playClick(player);
            int slot = event.getSlot();

            if (slot == 1) { // Refresh
                guiManager.openTeamMapGUI(player);
                return;
            }

            if (slot == 7) { // Close
                player.closeInventory();
                return;
            }

            if (slot >= 9 && slot < 54) {
                int r = slot / 9; // 1 to 5
                int c = slot % 9; // 0 to 8
                int dx = c - 4;   // -4 to +4
                int dz = r - 3;   // -2 to +2

                int targetCx = mapHolder.getCenterChunkX() + dx;
                int targetCz = mapHolder.getCenterChunkZ() + dz;
                org.bukkit.Chunk targetChunk = player.getWorld().getChunkAt(targetCx, targetCz);

                Team team = teamManager.getPlayerTeam(player.getUniqueId());
                if (team == null) {
                    player.sendMessage(TextUtil.format("<red>✖ You must belong to a Guild/Team to claim land!</red>"));
                    return;
                }

                String role = teamManager.getPlayerRole(player.getUniqueId());

                if (event.isRightClick()) {
                    com.guildcore.claims.ClaimInfo existing = guiManager.getClaimManager().getClaimAt(player.getWorld(), targetCx, targetCz);
                    if (existing != null && guiManager.isGuildClaim(player, team, existing)) {
                        if (!guiManager.getPermissionManager().hasPermission(team.getId(), role, "CLAIM")) {
                            player.sendMessage(TextUtil.format("<red>✖ You do not have team permission to unclaim land!</red>"));
                            return;
                        }
                        if (guiManager.getClaimManager().unclaim(targetChunk)) {
                            SoundUtil.playSuccess(player);
                            player.sendMessage(TextUtil.format("<yellow>✔ Unclaimed chunk (" + targetCx + ", " + targetCz + ") for your Guild.</yellow>"));
                            guiManager.openTeamMapGUI(player);
                        } else {
                            player.sendMessage(TextUtil.format("<red>✖ Failed to unclaim chunk!</red>"));
                        }
                    } else if (existing != null && player.hasPermission("guildcore.admin") && (existing.getTeamId() == null || existing.getTeamId() <= 0)) {
                        if (guiManager.getClaimManager().unclaim(targetChunk)) {
                            SoundUtil.playSuccess(player);
                            player.sendMessage(TextUtil.format("<green>✔ Admin purged legacy claim at (" + targetCx + ", " + targetCz + ")!</green>"));
                            guiManager.openTeamMapGUI(player);
                        } else {
                            player.sendMessage(TextUtil.format("<red>✖ Failed to unclaim legacy chunk!</red>"));
                        }
                    } else {
                        player.sendMessage(TextUtil.format("<red>✖ You can only unclaim chunks owned by your Guild!</red>"));
                    }
                    return;
                }

                if (!guiManager.getPermissionManager().hasPermission(team.getId(), role, "CLAIM")) {
                    player.sendMessage(TextUtil.format("<red>✖ You do not have team permission to claim land!</red>"));
                    return;
                }

                com.guildcore.claims.ClaimInfo existing = guiManager.getClaimManager().getClaimAt(player.getWorld(), targetCx, targetCz);
                if (existing != null) {
                    if (guiManager.isGuildClaim(player, team, existing)) {
                        player.sendMessage(TextUtil.format("<yellow>This chunk is already claimed by your team!</yellow>"));
                    } else {
                        player.sendMessage(TextUtil.format("<red>✖ This chunk belongs to another team (" + guiManager.getForeignGuildDisplay(existing) + ")!</red>"));
                    }
                    return;
                }

                int currentClaims = guiManager.getClaimManager().getTeamClaimsCount(team.getId());
                int maxClaims = teamManager.getMaxClaimsForTeam(team, settingsManager);
                if (currentClaims >= maxClaims) {
                    player.sendMessage(TextUtil.format("<red>✖ Team claim capacity reached (" + currentClaims + "/" + maxClaims + ")!</red>"));
                    return;
                }

                GUIManager.ClaimCostResult costRes = guiManager.calculateClaimCost(currentClaims);
                long costCoins = costRes.coins;
                int costXpLevels = costRes.xpLevels;
                Material costItemMat = costRes.itemMat;
                int costItemAmount = costRes.itemAmount;

                if (team.getBankBalance() < costCoins) {
                    player.sendMessage(TextUtil.format("<red>✖ Team Bank lacks funds! Required: $" + String.format("%,d", costCoins) + " Gold (Bank balance: $" + String.format("%,d", team.getBankBalance()) + ").</red>"));
                    return;
                }

                if (player.getLevel() < costXpLevels) {
                    player.sendMessage(TextUtil.format("<red>✖ You need at least " + costXpLevels + " XP Levels to claim this chunk!</red>"));
                    return;
                }

                if (costItemAmount > 0 && !player.getInventory().containsAtLeast(new ItemStack(costItemMat), costItemAmount)) {
                    player.sendMessage(TextUtil.format("<red>✖ You need " + costItemAmount + "x " + costItemMat.name() + " in your inventory to claim this chunk!</red>"));
                    return;
                }

                boolean success = guiManager.getClaimManager().createTeamClaim(player.getUniqueId(), team.getId(), targetChunk);
                if (success) {
                    if (costCoins > 0) {
                        team.setBankBalance(team.getBankBalance() - costCoins);
                    }
                    if (costXpLevels > 0) {
                        player.setLevel(player.getLevel() - costXpLevels);
                    }
                    if (costItemAmount > 0) {
                        player.getInventory().removeItem(new ItemStack(costItemMat, costItemAmount));
                    }
                    SoundUtil.playSuccess(player);
                    player.sendMessage(TextUtil.format("<green>✔ Successfully claimed chunk (" + targetCx + ", " + targetCz + ") for your Guild!</green>"));
                    guiManager.openTeamMapGUI(player);
                } else {
                    player.sendMessage(TextUtil.format("<red>✖ Failed to claim chunk!</red>"));
                }
            }
            return;
        }

        // Admin Prohibited Items GUI
        if (holder instanceof com.guildcore.gui.holders.AdminProhibitedHolder prohibitedHolder) {
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
                        prohibitedManager.purgePlayerFull(player);
                    }
                } else {
                    player.sendMessage(TextUtil.format("<red>Hold an item in main hand to ban!</red>"));
                }
                guiManager.openAdminProhibitedItems(player, currentPage);
                return;
            }

            if (slot == 45) {
                if (currentPage > 1) {
                    guiManager.openAdminProhibitedItems(player, currentPage - 1);
                }
                return;
            }

            if (slot == 53) {
                guiManager.openAdminProhibitedItems(player, currentPage + 1);
                return;
            }

            if (slot == 49) {
                guiManager.openAdminSettings(player);
                return;
            }

            if (prohibitedManager != null) {
                var mats = new ArrayList<>(prohibitedManager.getProhibitedMaterials());
                int pageSize = 28;
                int[] itemSlots = {
                    10, 11, 12, 13, 14, 15, 16,
                    19, 20, 21, 22, 23, 24, 25,
                    28, 29, 30, 31, 32, 33, 34,
                    37, 38, 39, 40, 41, 42, 43
                };

                int startIndex = (currentPage - 1) * pageSize;
                int endIndex = Math.min(startIndex + pageSize, mats.size());

                for (int i = startIndex; i < endIndex; i++) {
                    int slotForIndex = itemSlots[i - startIndex];
                    if (slotForIndex == slot) {
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

            if (slot == 19) { guiManager.openAdminAuctionSettings(player); return; }
            if (slot == 20) { guiManager.openAdminRtpSettings(player); return; }
            if (slot == 21) { guiManager.openAdminProhibitedItems(player, 1); return; }
            if (slot == 22) { guiManager.openAdminShopHub(player); return; }
            if (slot == 23) { guiManager.openAdminDebugPanel(player); return; }
            if (slot == 24) { crateManager.openCrateAdminHub(player); return; }
            if (slot == 49) { player.closeInventory(); return; }
            return;
        }

        // Admin RTP Settings Sub-GUI
        if (holder instanceof com.guildcore.gui.holders.AdminRtpHolder) {
            event.setCancelled(true);
            SoundUtil.playClick(player);
            int slot = event.getSlot();

            if (slot == 10) {
                ChatInputListener.requestInput(player, "rtp.cooldown_sec", p -> guiManager.openAdminRtpSettings(p));
            } else if (slot == 11) {
                ChatInputListener.requestInput(player, "rtp.warmup_sec", p -> guiManager.openAdminRtpSettings(p));
            } else if (slot == 12) {
                ChatInputListener.requestInput(player, "rtp.range.min_x", p -> guiManager.openAdminRtpSettings(p));
            } else if (slot == 13) {
                ChatInputListener.requestInput(player, "rtp.range.max_x", p -> guiManager.openAdminRtpSettings(p));
            } else if (slot == 14) {
                ChatInputListener.requestInput(player, "rtp.range.min_z", p -> guiManager.openAdminRtpSettings(p));
            } else if (slot == 15) {
                ChatInputListener.requestInput(player, "rtp.range.max_z", p -> guiManager.openAdminRtpSettings(p));
            } else if (slot == 26) {
                guiManager.openAdminSettings(player);
            }
            return;
        }

        // 4. Admin Economy Sub-GUI
        if (holder instanceof AdminEconomyHolder) {
            event.setCancelled(true);
            SoundUtil.playClick(player);
            int slot = event.getSlot();

            if (slot == 10) {
                ChatInputListener.requestInput(player, "economy.starting_balance", p -> guiManager.openAdminEconomySettings(p));
            } else if (slot == 12) {
                ChatInputListener.requestInput(player, "economy.pvp_kill_reward", p -> guiManager.openAdminEconomySettings(p));
            } else if (slot == 14) {
                ChatInputListener.requestInput(player, "economy.sales_tax_percent", p -> guiManager.openAdminEconomySettings(p));
            } else if (slot == 16) {
                economyManager.deposit(player.getUniqueId(), 1000, "admin_give_self");
                player.sendMessage(TextUtil.format("<green>Received +$1,000 coins from Admin Panel!</green>"));
                guiManager.openAdminEconomySettings(player);
            } else if (slot == 26) {
                guiManager.openAdminSettings(player);
            }
            return;
        }

        // 5. Admin Claim Sub-GUI
        if (holder instanceof AdminClaimHolder) {
            event.setCancelled(true);
            SoundUtil.playClick(player);
            int slot = event.getSlot();

            if (slot == 9) { // Pricing mode toggle
                String current = settingsManager.getString("claims.cost.mode", "SCALER");
                String next = "CUSTOM".equalsIgnoreCase(current) ? "SCALER" : "CUSTOM";
                settingsManager.set("claims.cost.mode", next);
                guiManager.openAdminClaimSettings(player);
            } else if (slot == 10) {
                ChatInputListener.requestInput(player, "claims.map.cost_coins", p -> guiManager.openAdminClaimSettings(p));
            } else if (slot == 11) {
                ChatInputListener.requestInput(player, "claims.map.cost_xp_levels", p -> guiManager.openAdminClaimSettings(p));
            } else if (slot == 12) {
                guiManager.openClaimItemSelectorGUI(player);
            } else if (slot == 13) {
                ChatInputListener.requestInput(player, "claims.cost.multiplier", p -> guiManager.openAdminClaimSettings(p));
            } else if (slot == 14) { // Coord format toggle
                String current = settingsManager.getString("claims.map.coord_format", "CHUNK");
                String next = "BLOCK".equalsIgnoreCase(current) ? "CHUNK" : "BLOCK";
                settingsManager.set("claims.map.coord_format", next);
                guiManager.openAdminClaimSettings(player);
            } else if (slot == 15) { // Global explosions toggle
                boolean disableExplosions = settingsManager.getBoolean("world.disable_explosions", false);
                settingsManager.set("world.disable_explosions", String.valueOf(!disableExplosions));
                guiManager.openAdminClaimSettings(player);
            } else if (slot == 16) { // Hide foreign guild names toggle
                boolean current = settingsManager.getBoolean("claims.map.hide_foreign_guild_names", false);
                settingsManager.set("claims.map.hide_foreign_guild_names", String.valueOf(!current));
                guiManager.openAdminClaimSettings(player);
            } else if (slot == 17) { // Purge all legacy claims
                int purged = guiManager.getClaimManager().purgeLegacyClaims();
                player.sendMessage(TextUtil.format("<green>✔ Successfully purged " + purged + " legacy non-team claims!</green>"));
                guiManager.openAdminClaimSettings(player);
            } else if (slot == 26) {
                guiManager.openAdminSettings(player);
            }
            return;
        }

        // Claim Item Requirement Selector GUI
        if (holder instanceof com.guildcore.gui.holders.ClaimItemSelectorHolder) {
            int rawSlot = event.getRawSlot();
            int slot = event.getSlot();

            // Allow dragging/placing items in target slots (10-16) or moving items from player's inventory
            if (rawSlot >= 27 || (slot >= 10 && slot <= 16)) {
                return; // DO NOT cancel event! Allow item placement/drag
            }

            event.setCancelled(true);
            SoundUtil.playClick(player);

            if (slot == 22) { // Save & Apply Multi-Item Requirement
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
            } else if (slot == 26) {
                guiManager.openAdminClaimSettings(player);
            }
            return;
        }

        // Admin Team Sub-GUI
        if (holder instanceof AdminTeamHolder) {
            event.setCancelled(true);
            SoundUtil.playClick(player);
            int slot = event.getSlot();

            if (slot == 9) {
                ChatInputListener.requestInput(player, "teams.creation_cost", p -> guiManager.openAdminTeamSettings(p));
            } else if (slot == 10) {
                ChatInputListener.requestInput(player, "teams.base_max_members", p -> guiManager.openAdminTeamSettings(p));
            } else if (slot == 11) { // Leader auto-transfer toggle
                boolean current = settingsManager.getBoolean("teams.auto_transfer_leader_on_leave", true);
                settingsManager.set("teams.auto_transfer_leader_on_leave", String.valueOf(!current));
                guiManager.openAdminTeamSettings(player);
            } else if (slot == 12) { // Max Guild Level
                ChatInputListener.requestInput(player, "teams.max_guild_level", p -> guiManager.openAdminTeamSettings(p));
            } else if (slot == 13) { // Leader transfer offline mode toggle
                boolean current = settingsManager.getBoolean("teams.transfer_leader_allow_offline", true);
                settingsManager.set("teams.transfer_leader_allow_offline", String.valueOf(!current));
                guiManager.openAdminTeamSettings(player);
            } else if (slot == 14) { // Level Claim Caps GUI
                guiManager.openAdminGuildLevelsGUI(player, 1);
            } else if (slot == 26) {
                guiManager.openAdminSettings(player);
            }
            return;
        }

        // Admin Guild Levels Sub-GUI
        if (holder instanceof com.guildcore.gui.holders.AdminGuildLevelsHolder levelsHolder) {
            event.setCancelled(true);
            SoundUtil.playClick(player);
            int slot = event.getSlot();

            int[] itemSlots = new int[]{
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34,
                37, 38, 39, 40, 41, 42, 43
            };

            int clickedIdx = -1;
            for (int i = 0; i < itemSlots.length; i++) {
                if (itemSlots[i] == slot) {
                    clickedIdx = i;
                    break;
                }
            }

            if (clickedIdx != -1) {
                int targetLevel = (levelsHolder.getPage() - 1) * 28 + clickedIdx + 1;
                ChatInputListener.requestInput(player, "teams.max_claims_level_" + targetLevel, p -> guiManager.openAdminGuildLevelsGUI(p, levelsHolder.getPage()));
                return;
            }

            if (slot == 45 && levelsHolder.getPage() > 1) {
                guiManager.openAdminGuildLevelsGUI(player, levelsHolder.getPage() - 1);
            } else if (slot == 53) {
                int maxLevel = settingsManager.getInt("teams.max_guild_level", 5);
                int totalPages = Math.max(1, (int) Math.ceil((double) maxLevel / 28.0));
                if (levelsHolder.getPage() < totalPages) {
                    guiManager.openAdminGuildLevelsGUI(player, levelsHolder.getPage() + 1);
                }
            } else if (slot == 49) {
                guiManager.openAdminTeamSettings(player);
            }
            return;
        }

        // 10. Simple Back-Button Sub-GUIs
        if (holder instanceof AdminKillHolder) {
            event.setCancelled(true);
            SoundUtil.playClick(player);
            if (event.getSlot() == 26) {
                guiManager.openAdminSettings(player);
            }
            return;
        }

        // 6. Admin Combat Sub-GUI
        if (holder instanceof AdminCombatHolder) {
            event.setCancelled(true);
            SoundUtil.playClick(player);
            int slot = event.getSlot();

            if (slot == 9) {
                ChatInputListener.requestInput(player, "combat.tag_duration", p -> guiManager.openAdminCombatSettings(p));
            } else if (slot == 10) {
                boolean disableCmds = settingsManager.getBoolean("combat.disable_commands", true);
                settingsManager.set("combat.disable_commands", String.valueOf(!disableCmds));
                guiManager.openAdminCombatSettings(player);
            } else if (slot == 11) {
                ChatInputListener.requestInput(player, "combat.enderpearl_cooldown", p -> guiManager.openAdminCombatSettings(p));
            } else if (slot == 12) {
                ChatInputListener.requestInput(player, "combat.windcharge_cooldown", p -> guiManager.openAdminCombatSettings(p));
            } else if (slot == 13) {
                ChatInputListener.requestInput(player, "combat.mace_cooldown", p -> guiManager.openAdminCombatSettings(p));
            } else if (slot == 14) {
                boolean val = settingsManager.getBoolean("item.disabled_global.shield", false);
                settingsManager.set("item.disabled_global.shield", String.valueOf(!val));
                guiManager.openAdminCombatSettings(player);
            } else if (slot == 15) {
                boolean val = settingsManager.getBoolean("combat.riptide_enabled", false);
                settingsManager.set("combat.riptide_enabled", String.valueOf(!val));
                guiManager.openAdminCombatSettings(player);
            } else if (slot == 16) {
                boolean val = settingsManager.getBoolean("combat.crystal_enabled", false);
                settingsManager.set("combat.crystal_enabled", String.valueOf(!val));
                guiManager.openAdminCombatSettings(player);
            } else if (slot == 17) {
                boolean val = settingsManager.getBoolean("combat.anchor_enabled", false);
                settingsManager.set("combat.anchor_enabled", String.valueOf(!val));
                guiManager.openAdminCombatSettings(player);
            } else if (slot == 26) {
                guiManager.openAdminSettings(player);
            }
            return;
        }

        // 7. Admin Scoreboard Sub-GUI
        if (holder instanceof AdminScoreboardHolder) {
            event.setCancelled(true);
            SoundUtil.playClick(player);
            int slot = event.getSlot();

            if (slot == 11) {
                ChatInputListener.requestInput(player, "scoreboard.update_ticks", p -> guiManager.openAdminScoreboardSettings(p));
            } else if (slot == 13) {
                if (scoreboardManager.isScoreboardsDisabled()) {
                    scoreboardManager.enableScoreboards();
                    player.sendMessage(TextUtil.format("<green>Enabled server scoreboards!</green>"));
                } else {
                    scoreboardManager.clearServerScoreboards();
                    player.sendMessage(TextUtil.format("<green>🧹 Wiped & disabled all server scoreboards across all players!</green>"));
                }
                guiManager.openAdminScoreboardSettings(player);
            } else if (slot == 26) {
                guiManager.openAdminSettings(player);
            }
            return;
        }

        // 8. Admin Auction Sub-GUI
        if (holder instanceof AdminAuctionHolder) {
            event.setCancelled(true);
            SoundUtil.playClick(player);
            int slot = event.getSlot();

            if (slot == 10) {
                ChatInputListener.requestInput(player, "auction.listing_fee", p -> guiManager.openAdminAuctionSettings(p));
            } else if (slot == 12) {
                ChatInputListener.requestInput(player, "auction.duration_hours_default", p -> guiManager.openAdminAuctionSettings(p));
            } else if (slot == 14) {
                ChatInputListener.requestInput(player, "auction.max_listing_price", p -> guiManager.openAdminAuctionSettings(p));
            } else if (slot == 16) {
                ChatInputListener.requestInput(player, "auction.listing_cooldown_sec", p -> guiManager.openAdminAuctionSettings(p));
            } else if (slot == 26) {
                guiManager.openAdminSettings(player);
            }
            return;
        }

        // 9. Admin Debug Sub-GUI
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

        // 10. Simple Back-Button Sub-GUIs
        if (holder instanceof AdminKillHolder || holder instanceof AdminTeamHolder) {
            event.setCancelled(true);
            SoundUtil.playClick(player);
            if (event.getSlot() == 26) {
                guiManager.openAdminSettings(player);
            }
            return;
        }

        // 11. Auction Purchase Confirmation GUI
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

        // 12. Main Team Control GUI Navigation
        if (holder instanceof TeamGUIHolder teamHolder) {
            event.setCancelled(true);
            SoundUtil.playClick(player);
            int slot = event.getSlot();

            Team team = teamManager.getTeam(teamHolder.getTeamId());
            if (team == null) return;

            if (slot == 11) { // Guild Members Roster
                guiManager.openTeamMembersGUI(player, team, 1);
            } else if (slot == 12) { // Bank
                player.sendMessage(TextUtil.format("<gold>🏦 Team Bank: <green>$" + team.getBankBalance() + "</green> | Use /team bank deposit <amount> or withdraw</gold>"));
            } else if (slot == 14) { // Vault
                ItemStack[] contents = vaultManager.getVaultPage(team.getId(), 1);
                Inventory vaultInv = Bukkit.createInventory(new VaultGUIHolder(team.getId(), 1), 54, TextUtil.format("<gold>📦 Team Vault (Page 1)</gold>"));
                vaultInv.setContents(contents);
                vaultInv.setItem(53, new GUIItemBuilder(Material.BARRIER).name("<red>◀ Back to Team Menu</red>").build());
                scheduler.runSync(player, () -> player.openInventory(vaultInv));
            } else if (slot == 16) { // Upgrades
                guiManager.openTeamUpgrades(player, team);
            } else if (slot == 28) { // Permissions Codex
                guiManager.openTeamPermissions(player, team, "MEMBER");
            } else if (slot == 30) { // Territory Claims Map
                guiManager.openTeamMapGUI(player);
            } else if (slot == 32) { // Teleport Home
                if (team.getHomeLocation() != null) {
                    player.teleportAsync(team.getHomeLocation()).thenAccept(success -> {
                        if (success) {
                            player.sendMessage(TextUtil.format("<green>Teleported to team home!</green>"));
                        }
                    });
                } else {
                    player.sendMessage(TextUtil.format("<red>Team home location is not set.</red>"));
                }
            }
            return;
        }

        // Guild Members Roster GUI
        if (holder instanceof com.guildcore.gui.holders.TeamMembersHolder membersHolder) {
            event.setCancelled(true);
            SoundUtil.playClick(player);
            int slot = event.getSlot();

            Team team = teamManager.getTeam(membersHolder.getTeamId());
            if (team == null) return;

            if (slot == 45 && membersHolder.getPage() > 1) {
                guiManager.openTeamMembersGUI(player, team, membersHolder.getPage() - 1);
                return;
            }
            if (slot == 53) {
                guiManager.openTeamMembersGUI(player, team, membersHolder.getPage() + 1);
                return;
            }
            if (slot == 49) {
                guiManager.openTeamMenu(player, team);
                return;
            }

            int[] itemSlots = {
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34,
                37, 38, 39, 40, 41, 42, 43
            };

            int clickedIdx = -1;
            for (int i = 0; i < itemSlots.length; i++) {
                if (itemSlots[i] == slot) {
                    clickedIdx = i;
                    break;
                }
            }

            if (clickedIdx != -1) {
                List<UUID> memberUuids = teamManager.getTeamMembers(team.getId());
                int targetIndex = (membersHolder.getPage() - 1) * 28 + clickedIdx;
                if (targetIndex < memberUuids.size()) {
                    UUID targetUuid = memberUuids.get(targetIndex);
                    org.bukkit.OfflinePlayer targetOp = Bukkit.getPlayer(targetUuid) != null ? Bukkit.getPlayer(targetUuid) : Bukkit.getOfflinePlayer(targetUuid);
                    String targetName = targetOp.getName();
                    if (targetName != null) {
                        if (event.isShiftClick() && event.isRightClick()) {
                            guiManager.openTeamKickConfirmGUI(player, team, targetOp);
                            return;
                        } else if (event.isRightClick()) {
                            teamManager.demotePlayer(player, targetName);
                        } else {
                            teamManager.promotePlayer(player, targetName);
                        }
                        guiManager.openTeamMembersGUI(player, team, membersHolder.getPage());
                    }
                }
            }
            return;
        }

        // Team Kick Confirmation GUI
        if (holder instanceof com.guildcore.gui.holders.TeamKickConfirmHolder confirmHolder) {
            event.setCancelled(true);
            SoundUtil.playClick(player);
            int slot = event.getSlot();

            Team team = teamManager.getTeam(confirmHolder.getTeamId());
            if (team == null) return;

            if (slot == 11) { // Confirm Kick
                teamManager.kickPlayer(player, confirmHolder.getTargetName());
                guiManager.openTeamMembersGUI(player, team, 1);
            } else if (slot == 15) { // Cancel
                guiManager.openTeamMembersGUI(player, team, 1);
            }
            return;
        }

        // Team Permissions GUI
        if (holder instanceof com.guildcore.gui.holders.TeamPermissionsHolder permsHolder) {
            event.setCancelled(true);
            SoundUtil.playClick(player);
            int slot = event.getSlot();

            Team team = teamManager.getTeam(permsHolder.getTeamId());
            if (team == null) return;

            if (slot == 49) {
                guiManager.openTeamMenu(player, team);
                return;
            }

            if (!player.getUniqueId().equals(team.getLeaderUuid())) {
                player.sendMessage(TextUtil.format("<red>✖ Only the Guild Leader can modify team permissions!</red>"));
                return;
            }

            if (slot == 10) { guiManager.openTeamPermissions(player, team, "OFFICER"); return; }
            if (slot == 12) { guiManager.openTeamPermissions(player, team, "MEMBER"); return; }
            if (slot == 14) { guiManager.openTeamPermissions(player, team, "RECRUIT"); return; }

            String selectedRole = permsHolder.getSelectedRole();
            String[] nodes = {"BANK_DEPOSIT", "BANK_WITHDRAW", "VAULT_ACCESS", "CLAIM_LAND", "BUILD", "INVITE_MEMBERS", "KICK_MEMBERS", "SET_HOME", "UPGRADE_TEAM"};
            int[] slots = {19, 20, 21, 22, 23, 28, 29, 30, 31};

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

        // Team Bank GUI
        if (holder instanceof com.guildcore.gui.holders.TeamBankGUIHolder bankHolder) {
            event.setCancelled(true);
            SoundUtil.playClick(player);
            int slot = event.getSlot();
            Team team = bankHolder.getTeam();
            if (team == null) return;

            if (slot == 49) {
                guiManager.openTeamMenu(player, team);
                return;
            }

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
            } else if (slot == 28) bankMgr.withdraw(team, player.getUniqueId(), 100);
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

        // Team Leadership Transfer Confirm GUI
        if (holder instanceof com.guildcore.gui.holders.TeamTransferLeaderConfirmHolder confirmHolder) {
            event.setCancelled(true);
            SoundUtil.playClick(player);
            int slot = event.getSlot();

            if (slot == 11) {
                teamManager.transferLeadership(player, confirmHolder.getTargetSuccessor().getUniqueId());
                player.closeInventory();
            } else if (slot == 15) {
                player.sendMessage(TextUtil.format("<yellow>Leadership transfer cancelled.</yellow>"));
                player.closeInventory();
            }
            return;
        }

        // 13. Team Upgrades GUI
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

            String role = teamManager.getPlayerRole(player.getUniqueId());
            if (!guiManager.getPermissionManager().hasPermission(team.getId(), role, "UPGRADE_TEAM")) {
                player.sendMessage(TextUtil.format("<red>✖ You do not have team permission to upgrade team features!</red>"));
                return;
            }

            if (slot == 11) { // Member Cap Upgrade
                long memberCost = settingsManager.getLong("teams.upgrade.member_cap_cost", 5000);
                if (team.getBankBalance() >= memberCost) {
                    team.setBankBalance(team.getBankBalance() - memberCost);
                    team.setMaxMembers(team.getMaxMembers() + 2);
                    teamManager.saveTeamMaxMembers(team.getId(), team.getMaxMembers());
                    player.sendMessage(TextUtil.format("<green>Upgraded Team Member Cap to " + team.getMaxMembers() + "!</green>"));
                    guiManager.openTeamUpgrades(player, team);
                } else {
                    player.sendMessage(TextUtil.format("<red>✖ Insufficient Team Bank balance ($" + String.format("%,d", memberCost) + " required). Your Bank: $" + String.format("%,d", team.getBankBalance()) + "</red>"));
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

    private void saveVaultTopInventory(int teamId, org.bukkit.inventory.Inventory topInv, int unlockedSlots) {
        ItemStack[] contents = new ItemStack[54];
        for (int i = 0; i < Math.min(54, unlockedSlots); i++) {
            ItemStack item = topInv.getItem(i);
            if (item != null && item.getType() != Material.AIR && item.getType() != Material.YELLOW_STAINED_GLASS_PANE && item.getType() != Material.GRAY_STAINED_GLASS_PANE) {
                contents[i] = item.clone();
            }
        }
        vaultManager.saveVaultPage(teamId, 1, contents);
    }
}
