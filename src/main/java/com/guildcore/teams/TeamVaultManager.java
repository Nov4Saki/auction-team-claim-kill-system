package com.guildcore.teams;

import com.guildcore.database.DatabaseManager;
import com.guildcore.debug.DebugFlag;
import com.guildcore.debug.DebugManager;
import com.guildcore.gui.GUIManager;
import com.guildcore.util.ItemSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TeamVaultManager {
    private final DatabaseManager dbManager;
    // Key format: "teamId:page" -> ItemStack[]
    private final Map<String, ItemStack[]> vaultCache = new ConcurrentHashMap<>();
    private final Map<String, Object> vaultLocks = new ConcurrentHashMap<>();

    public TeamVaultManager(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    private String makeKey(int teamId, int page) {
        return teamId + ":" + page;
    }

    private Object getVaultLock(int teamId, int page) {
        return vaultLocks.computeIfAbsent(makeKey(teamId, page), k -> new Object());
    }

    public ItemStack[] getVaultPage(int teamId, int page) {
        String key = makeKey(teamId, page);
        synchronized (getVaultLock(teamId, page)) {
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

    public boolean withdrawItem(Player player, Team team, int page, int slot, GUIManager guiManager) {
        if (player == null || team == null || slot < 0 || slot >= 45) return false;

        synchronized (getVaultLock(team.getId(), page)) {
            ItemStack[] items = getVaultPage(team.getId(), page);
            ItemStack slotItem = items[slot];

            if (slotItem == null || slotItem.getType() == Material.AIR ||
                slotItem.getType() == Material.YELLOW_STAINED_GLASS_PANE ||
                slotItem.getType() == Material.GRAY_STAINED_GLASS_PANE ||
                slotItem.getType() == Material.BLACK_STAINED_GLASS_PANE ||
                slotItem.getType() == Material.ARROW ||
                slotItem.getType() == Material.BOOK ||
                slotItem.getType() == Material.BARRIER) {
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
            } else {
                items[slot].setAmount(leftover.get(0).getAmount());
            }

            saveVaultPage(team.getId(), page, items);
            logVaultAction(team.getId(), player.getUniqueId(), itemToGive.getType().name(), givenAmount, "WITHDRAW");
            if (guiManager != null) {
                guiManager.refreshTeamVault(team.getId(), page);
            }
            return true;
        }
    }

    public boolean depositItem(Player player, Team team, int page, ItemStack itemToDeposit, int unlockedOnThisPage, GUIManager guiManager) {
        if (player == null || team == null || itemToDeposit == null || itemToDeposit.getType() == Material.AIR || unlockedOnThisPage <= 0) {
            return false;
        }

        synchronized (getVaultLock(team.getId(), page)) {
            ItemStack[] items = getVaultPage(team.getId(), page);
            ItemStack stackToDeposit = itemToDeposit.clone();
            int originalAmount = stackToDeposit.getAmount();

            // 1. Merge into existing matching stacks in unlocked slots
            for (int i = 0; i < Math.min(45, unlockedOnThisPage); i++) {
                ItemStack slotItem = items[i];
                if (slotItem != null && slotItem.getType() != Material.AIR && slotItem.isSimilar(stackToDeposit)) {
                    int maxStack = slotItem.getMaxStackSize();
                    int space = maxStack - slotItem.getAmount();
                    if (space > 0) {
                        int toAdd = Math.min(space, stackToDeposit.getAmount());
                        slotItem.setAmount(slotItem.getAmount() + toAdd);
                        stackToDeposit.setAmount(stackToDeposit.getAmount() - toAdd);
                        items[i] = slotItem;
                        if (stackToDeposit.getAmount() <= 0) break;
                    }
                }
            }

            // 2. Place remaining stack into first empty unlocked slot
            if (stackToDeposit.getAmount() > 0) {
                for (int i = 0; i < Math.min(45, unlockedOnThisPage); i++) {
                    ItemStack slotItem = items[i];
                    if (slotItem == null || slotItem.getType() == Material.AIR) {
                        items[i] = stackToDeposit.clone();
                        stackToDeposit.setAmount(0);
                        break;
                    }
                }
            }

            int depositedAmount = originalAmount - stackToDeposit.getAmount();
            if (depositedAmount > 0) {
                itemToDeposit.setAmount(stackToDeposit.getAmount());
                saveVaultPage(team.getId(), page, items);
                logVaultAction(team.getId(), player.getUniqueId(), itemToDeposit.getType().name(), depositedAmount, "DEPOSIT");
                if (guiManager != null) {
                    guiManager.refreshTeamVault(team.getId(), page);
                }
                return true;
            }
            return false;
        }
    }

    public void saveAllVaultsSync() {
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
