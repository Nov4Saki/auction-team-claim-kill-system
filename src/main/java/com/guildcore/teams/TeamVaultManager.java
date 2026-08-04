package com.guildcore.teams;

import com.guildcore.database.DatabaseManager;
import com.guildcore.debug.DebugFlag;
import com.guildcore.debug.DebugManager;
import com.guildcore.gui.GUIItemBuilder;
import com.guildcore.gui.holders.VaultGUIHolder;
import com.guildcore.util.ItemSerializer;
import com.guildcore.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class TeamVaultManager {
    private final DatabaseManager dbManager;

    // Key format: "teamId:page" -> Inventory (Shared inventory instance per page)
    private final Map<String, Inventory> activeInventories = new ConcurrentHashMap<>();
    // Key format: "teamId:page" -> AtomicInteger (Active viewer count)
    private final Map<String, AtomicInteger> activeViewerCounts = new ConcurrentHashMap<>();
    // Key format: "teamId:page" -> Object (Sync lock)
    private final Map<String, Object> vaultLocks = new ConcurrentHashMap<>();
    // Key format: "teamId:page" -> ItemStack[] (Cached items)
    private final Map<String, ItemStack[]> vaultCache = new ConcurrentHashMap<>();

    private com.guildcore.gui.GUIManager guiManager;

    public TeamVaultManager(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    public void setGuiManager(com.guildcore.gui.GUIManager guiManager) {
        this.guiManager = guiManager;
    }

    private String makeKey(int teamId, int page) {
        return teamId + ":" + page;
    }

    public Object getVaultLock(int teamId, int page) {
        return vaultLocks.computeIfAbsent(makeKey(teamId, page), k -> new Object());
    }

    public Inventory getOrCreateSharedInventory(Team team, int page, int unlockedOnThisPage) {
        String key = makeKey(team.getId(), page);
        synchronized (getVaultLock(team.getId(), page)) {
            Inventory inv = activeInventories.get(key);
            if (inv == null) {
                inv = Bukkit.createInventory(
                        new VaultGUIHolder(team.getId(), page),
                        54,
                        TextUtil.format("<gradient:#FFD700:#FFA500><b>📦 Team Vault (" + team.getName() + " - Page " + page + ")</b></gradient>")
                );
                ItemStack[] items = getVaultPage(team.getId(), page);
                for (int i = 0; i < Math.min(45, items.length); i++) {
                    if (i < unlockedOnThisPage && items[i] != null && items[i].getType() != Material.AIR) {
                        inv.setItem(i, items[i].clone());
                    }
                }
                updateVaultGlassPanes(team, page, unlockedOnThisPage, inv);
                activeInventories.put(key, inv);
            } else {
                updateVaultGlassPanes(team, page, unlockedOnThisPage, inv);
            }
            activeViewerCounts.computeIfAbsent(key, k -> new AtomicInteger(0)).incrementAndGet();
            return inv;
        }
    }

    public void refreshSharedInventoryPanes(Team team, int page, int unlockedOnThisPage) {
        String key = makeKey(team.getId(), page);
        synchronized (getVaultLock(team.getId(), page)) {
            Inventory inv = activeInventories.get(key);
            if (inv != null) {
                updateVaultGlassPanes(team, page, unlockedOnThisPage, inv);
            }
        }
    }

    public void updateVaultGlassPanes(Team team, int page, int unlockedOnThisPage, Inventory inv) {
        int vaultSlots = Math.max(9, team.getVaultSlots());
        int totalUnlockedPages = Math.max(1, (vaultSlots + 44) / 45);
        int globalStartIndex = (page - 1) * 45;

        for (int i = 0; i < 45; i++) {
            if (i >= unlockedOnThisPage) {
                if (i == unlockedOnThisPage) {
                    int targetGlobalSlot = globalStartIndex + i + 1;
                    long cost = getVaultSlotCost(targetGlobalSlot);
                    boolean canAfford = team.getBankBalance() >= cost;

                    ItemStack yellowPane = new GUIItemBuilder(Material.YELLOW_STAINED_GLASS_PANE)
                            .name("<yellow><b>🔓 Unlock Vault Slot #" + targetGlobalSlot + "</b></yellow>")
                            .lore(
                                    "<gray>▪ Target Slot: <white>#" + targetGlobalSlot + " (Page " + page + ", Slot " + (i + 1) + ")</white></gray>",
                                    "<gray>▪ Unlock Cost: <gold>$" + String.format("%,d", cost) + " Team Bank</gold></gray>",
                                    "<gray>▪ Team Bank: <white>$" + String.format("%,d", team.getBankBalance()) + "</white></gray>",
                                    "",
                                    canAfford ? "<gradient:#00FF87:#60EFFF><b>✔ CLICK TO UNLOCK SLOT #" + targetGlobalSlot + "</b></gradient>" : "<gradient:#FF416C:#FF4B2B><b>✖ INSUFFICIENT TEAM BANK</b></gradient>"
                            ).build();
                    inv.setItem(i, yellowPane);
                } else {
                    ItemStack grayPane = new GUIItemBuilder(Material.GRAY_STAINED_GLASS_PANE)
                            .name("<gray>🔒 Locked Vault Slot</gray>")
                            .lore("<dark_gray>Unlock slot #" + (globalStartIndex + unlockedOnThisPage + 1) + " first.</dark_gray>")
                            .build();
                    inv.setItem(i, grayPane);
                }
            }
        }

        for (int i = 45; i < 54; i++) {
            inv.setItem(i, new GUIItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build());
        }

        if (page > 1) {
            inv.setItem(45, new GUIItemBuilder(Material.ARROW).name("<yellow><b>◀ Previous Page (Page " + (page - 1) + ")</b></yellow>").build());
        } else {
            inv.setItem(45, new GUIItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name("<gray><b>◀ First Page</b></gray>").build());
        }

        inv.setItem(49, new GUIItemBuilder(Material.BOOK)
                .name("<gold><b>📦 Vault Page " + page + " (Unlocked: " + unlockedOnThisPage + "/45)</b></gold>")
                .lore(
                        "<gray>▪ Team: <white>" + team.getName() + "</white></gray>",
                        "<gray>▪ Total Vault Slots Unlocked: <green>" + vaultSlots + "</green></gray>",
                        "<gray>▪ Team Bank: <gold>$" + String.format("%,d", team.getBankBalance()) + "</gold></gray>",
                        "",
                        "<yellow>▶ Click to return to Main Menu</yellow>"
                ).build());

        if (page < totalUnlockedPages) {
            inv.setItem(53, new GUIItemBuilder(Material.ARROW).name("<yellow><b>Next Page (Page " + (page + 1) + ") ▶</b></yellow>").build());
        } else if (page == totalUnlockedPages) {
            if (unlockedOnThisPage == 45) {
                int targetGlobalSlot = vaultSlots + 1;
                long cost = getVaultSlotCost(targetGlobalSlot);
                boolean canAfford = team.getBankBalance() >= cost;

                ItemStack nextUnlock = new GUIItemBuilder(Material.YELLOW_STAINED_GLASS_PANE)
                        .name("<yellow><b>🔓 Unlock Page #" + (page + 1) + " (Click to Open)</b></yellow>")
                        .lore(
                                "<gray>▪ Target Slot: <white>#" + targetGlobalSlot + "</white></gray>",
                                "<gray>▪ Unlock Cost: <gold>$" + String.format("%,d", cost) + " Team Bank</gold></gray>",
                                "<gray>▪ Team Bank: <white>$" + String.format("%,d", team.getBankBalance()) + "</white></gray>",
                                "",
                                canAfford ? "<gradient:#00FF87:#60EFFF><b>✔ CLICK TO UNLOCK & OPEN PAGE #" + (page + 1) + "</b></gradient>" : "<gradient:#FF416C:#FF4B2B><b>✖ INSUFFICIENT TEAM BANK</b></gradient>"
                        ).build();
                inv.setItem(53, nextUnlock);
            } else {
                inv.setItem(53, new GUIItemBuilder(Material.GRAY_STAINED_GLASS_PANE)
                        .name("<gray>🔒 Page #" + (page + 1) + " Locked</gray>")
                        .lore("<dark_gray>Unlock all 45 slots of Page #" + page + " first.</dark_gray>")
                        .build());
            }
        }
    }

    private long getVaultSlotCost(int targetSlot) {
        if (targetSlot <= 9) return 0;
        if (guiManager != null) {
            return guiManager.getVaultSlotCost(targetSlot);
        }
        return 500L + Math.max(0, targetSlot - 10) * 250L;
    }

    public void handleVaultClose(int teamId, int page, Player player) {
        String key = makeKey(teamId, page);
        synchronized (getVaultLock(teamId, page)) {
            AtomicInteger count = activeViewerCounts.get(key);
            if (count != null) {
                int remaining = count.decrementAndGet();
                if (remaining <= 0) {
                    activeViewerCounts.remove(key);
                    Inventory sharedInv = activeInventories.remove(key);
                    if (sharedInv != null) {
                        ItemStack[] contents = extractVaultItemContents(sharedInv);
                        saveVaultPageSync(teamId, page, contents);
                    }
                }
            }
        }
        logVaultAction(teamId, player.getUniqueId(), "NONE", 0, "CLOSE_PAGE_" + page);
    }

    public ItemStack[] extractVaultItemContents(Inventory inv) {
        ItemStack[] contents = new ItemStack[45];
        for (int i = 0; i < 45; i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && item.getType() != Material.AIR &&
                item.getType() != Material.YELLOW_STAINED_GLASS_PANE &&
                item.getType() != Material.GRAY_STAINED_GLASS_PANE &&
                item.getType() != Material.BLACK_STAINED_GLASS_PANE &&
                item.getType() != Material.ARROW &&
                item.getType() != Material.BOOK &&
                item.getType() != Material.BARRIER) {
                contents[i] = item.clone();
            }
        }
        return contents;
    }

    public ItemStack[] getVaultPage(int teamId, int page) {
        String key = makeKey(teamId, page);
        synchronized (getVaultLock(teamId, page)) {
            Inventory sharedInv = activeInventories.get(key);
            if (sharedInv != null) {
                return extractVaultItemContents(sharedInv);
            }

            if (vaultCache.containsKey(key)) {
                return vaultCache.get(key);
            }

            try (Connection conn = dbManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT inventory_data FROM team_vaults WHERE team_id = ? AND page = ?")) {
                ps.setInt(1, teamId);
                ps.setInt(2, page);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String base64 = rs.getString("inventory_data");
                        ItemStack[] items = ItemSerializer.deserializeInventory(base64);
                        vaultCache.put(key, items);
                        return items;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            ItemStack[] empty = new ItemStack[54];
            vaultCache.put(key, empty);
            return empty;
        }
    }

    public void saveVaultPage(int teamId, int page, ItemStack[] contents) {
        String key = makeKey(teamId, page);
        synchronized (getVaultLock(teamId, page)) {
            vaultCache.put(key, contents);
            String base64 = ItemSerializer.serializeInventory(contents);
            dbManager.executeAsync(() -> saveToDatabase(teamId, page, base64));
        }
    }

    public void saveVaultPageSync(int teamId, int page, ItemStack[] contents) {
        String key = makeKey(teamId, page);
        synchronized (getVaultLock(teamId, page)) {
            vaultCache.put(key, contents);
            String base64 = ItemSerializer.serializeInventory(contents);
            saveToDatabase(teamId, page, base64);
        }
    }

    private void saveToDatabase(int teamId, int page, String base64) {
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT OR REPLACE INTO team_vaults (team_id, page, inventory_data) VALUES (?, ?, ?)")) {
            ps.setInt(1, teamId);
            ps.setInt(2, page);
            ps.setString(3, base64);
            ps.executeUpdate();
            DebugManager.log(DebugFlag.VAULT_SERIALIZATION, "Saved vault page " + page + " for team ID " + teamId + " (" + base64.length() + " chars)");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean withdrawItem(Player player, Team team, int page, int slot) {
        if (player == null || team == null || slot < 0 || slot >= 45) return false;

        synchronized (getVaultLock(team.getId(), page)) {
            String key = makeKey(team.getId(), page);
            Inventory sharedInv = activeInventories.get(key);

            ItemStack[] items;
            if (sharedInv != null) {
                items = extractVaultItemContents(sharedInv);
            } else {
                items = getVaultPage(team.getId(), page);
            }

            ItemStack slotItem = items[slot];
            if (slotItem == null || slotItem.getType() == Material.AIR ||
                slotItem.getType() == Material.YELLOW_STAINED_GLASS_PANE ||
                slotItem.getType() == Material.GRAY_STAINED_GLASS_PANE) {
                return false;
            }

            ItemStack itemToGive = slotItem.clone();
            java.util.HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(itemToGive);
            int givenAmount = itemToGive.getAmount() - (leftover.isEmpty() ? 0 : leftover.get(0).getAmount());

            if (givenAmount <= 0) {
                return false;
            }

            if (leftover.isEmpty()) {
                items[slot] = null;
                if (sharedInv != null) sharedInv.setItem(slot, null);
            } else {
                items[slot].setAmount(leftover.get(0).getAmount());
                if (sharedInv != null) sharedInv.setItem(slot, items[slot].clone());
            }

            saveVaultPage(team.getId(), page, items);
            logVaultAction(team.getId(), player.getUniqueId(), itemToGive.getType().name(), givenAmount, "WITHDRAW");
            return true;
        }
    }

    public boolean depositItem(Player player, Team team, int page, ItemStack itemToDeposit, int unlockedOnThisPage) {
        if (player == null || team == null || itemToDeposit == null || itemToDeposit.getType() == Material.AIR || unlockedOnThisPage <= 0) {
            return false;
        }

        synchronized (getVaultLock(team.getId(), page)) {
            String key = makeKey(team.getId(), page);
            Inventory sharedInv = activeInventories.get(key);

            ItemStack stackToDeposit = itemToDeposit.clone();
            int originalAmount = stackToDeposit.getAmount();

            // 1. Merge into existing matching stacks in unlocked slots (0..unlockedOnThisPage-1)
            for (int i = 0; i < Math.min(45, unlockedOnThisPage); i++) {
                ItemStack slotItem = sharedInv != null ? sharedInv.getItem(i) : null;
                if (slotItem != null && slotItem.getType() != Material.AIR &&
                    slotItem.getType() != Material.YELLOW_STAINED_GLASS_PANE &&
                    slotItem.getType() != Material.GRAY_STAINED_GLASS_PANE &&
                    slotItem.isSimilar(stackToDeposit)) {

                    int maxStack = slotItem.getMaxStackSize();
                    int space = maxStack - slotItem.getAmount();
                    if (space > 0) {
                        int toAdd = Math.min(space, stackToDeposit.getAmount());
                        slotItem.setAmount(slotItem.getAmount() + toAdd);
                        stackToDeposit.setAmount(stackToDeposit.getAmount() - toAdd);
                        if (sharedInv != null) sharedInv.setItem(i, slotItem.clone());
                        if (stackToDeposit.getAmount() <= 0) break;
                    }
                }
            }

            // 2. Place remaining stack into first empty unlocked slot
            if (stackToDeposit.getAmount() > 0) {
                for (int i = 0; i < Math.min(45, unlockedOnThisPage); i++) {
                    ItemStack slotItem = sharedInv != null ? sharedInv.getItem(i) : null;
                    if (slotItem == null || slotItem.getType() == Material.AIR) {
                        if (sharedInv != null) sharedInv.setItem(i, stackToDeposit.clone());
                        stackToDeposit.setAmount(0);
                        break;
                    }
                }
            }

            int depositedAmount = originalAmount - stackToDeposit.getAmount();
            if (depositedAmount > 0) {
                itemToDeposit.setAmount(stackToDeposit.getAmount());
                if (sharedInv != null) {
                    ItemStack[] contents = extractVaultItemContents(sharedInv);
                    saveVaultPage(team.getId(), page, contents);
                }
                logVaultAction(team.getId(), player.getUniqueId(), itemToDeposit.getType().name(), depositedAmount, "DEPOSIT");
                return true;
            }
            return false;
        }
    }

    public void saveAllVaultsSync() {
        for (Map.Entry<String, Inventory> entry : activeInventories.entrySet()) {
            String[] parts = entry.getKey().split(":");
            if (parts.length == 2) {
                try {
                    int teamId = Integer.parseInt(parts[0]);
                    int page = Integer.parseInt(parts[1]);
                    ItemStack[] contents = extractVaultItemContents(entry.getValue());
                    saveVaultPageSync(teamId, page, contents);
                } catch (NumberFormatException ignored) {}
            }
        }

        for (Map.Entry<String, ItemStack[]> entry : vaultCache.entrySet()) {
            String[] parts = entry.getKey().split(":");
            if (parts.length == 2) {
                try {
                    int teamId = Integer.parseInt(parts[0]);
                    int page = Integer.parseInt(parts[1]);
                    saveVaultPageSync(teamId, page, entry.getValue());
                } catch (NumberFormatException ignored) {}
            }
        }
    }

    public void logVaultAction(int teamId, UUID playerUuid, String itemType, int quantity, String action) {
        if (playerUuid == null || itemType == null) return;
        dbManager.executeAsync(() -> {
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT INTO team_vault_log (team_id, player_uuid, item_type, quantity, action) VALUES (?, ?, ?, ?, ?)")) {
                ps.setInt(1, teamId);
                ps.setString(2, playerUuid.toString());
                ps.setString(3, itemType);
                ps.setInt(4, quantity);
                ps.setString(5, action != null ? action : "UPDATE");
                ps.executeUpdate();
                DebugManager.log(DebugFlag.VAULT_SERIALIZATION, "Logged vault action " + action + " for team " + teamId + " by " + playerUuid);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}
