package com.guildcore.teams;

import com.guildcore.database.DatabaseManager;
import com.guildcore.debug.DebugFlag;
import com.guildcore.debug.DebugManager;
import com.guildcore.util.ItemSerializer;
import org.bukkit.inventory.ItemStack;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TeamVaultManager {
    private final DatabaseManager dbManager;
    // Key format: "teamId:page" -> ItemStack[]
    private final Map<String, ItemStack[]> vaultCache = new ConcurrentHashMap<>();

    public TeamVaultManager(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    private String makeKey(int teamId, int page) {
        return teamId + ":" + page;
    }

    public ItemStack[] getVaultPage(int teamId, int page) {
        String key = makeKey(teamId, page);
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

    public void saveVaultPage(int teamId, int page, ItemStack[] contents) {
        String key = makeKey(teamId, page);
        vaultCache.put(key, contents);

        String base64 = ItemSerializer.serializeInventory(contents);
        dbManager.executeAsync(() -> {
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
        });
    }
}
