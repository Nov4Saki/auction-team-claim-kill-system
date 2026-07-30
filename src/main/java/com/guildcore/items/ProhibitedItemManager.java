package com.guildcore.items;

import com.guildcore.database.DatabaseManager;
import com.guildcore.debug.DebugFlag;
import com.guildcore.debug.DebugManager;
import com.guildcore.util.TextUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ProhibitedItemManager {
    private final DatabaseManager dbManager;
    private final Set<Material> prohibitedMaterials = ConcurrentHashMap.newKeySet();

    public ProhibitedItemManager(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    public void loadProhibitedItems() {
        dbManager.executeAsync(() -> {
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT item_type FROM prohibited_items");
                 ResultSet rs = ps.executeQuery()) {

                prohibitedMaterials.clear();
                while (rs.next()) {
                    String typeStr = rs.getString("item_type");
                    Material mat = Material.matchMaterial(typeStr);
                    if (mat != null) {
                        prohibitedMaterials.add(mat);
                    }
                }
                DebugManager.log(DebugFlag.ITEM_DISABLE, "Loaded " + prohibitedMaterials.size() + " prohibited items from database.");
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public boolean isProhibited(Material material) {
        if (material == null || material.isAir()) return false;
        return prohibitedMaterials.contains(material);
    }

    public boolean isProhibited(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        return isProhibited(item.getType());
    }

    public void addProhibitedItem(Material mat, String reason, String addedBy) {
        if (mat == null || mat.isAir()) return;
        prohibitedMaterials.add(mat);
        dbManager.executeAsync(() -> {
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement("INSERT OR REPLACE INTO prohibited_items (item_type, reason, added_by) VALUES (?, ?, ?)")) {
                ps.setString(1, mat.name());
                ps.setString(2, reason != null ? reason : "Prohibited by Royal Decree");
                ps.setString(3, addedBy != null ? addedBy : "ADMIN");
                ps.executeUpdate();
                DebugManager.log(DebugFlag.ITEM_DISABLE, "Added prohibited item: " + mat.name());
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public void removeProhibitedItem(Material mat) {
        if (mat == null) return;
        prohibitedMaterials.remove(mat);
        dbManager.executeAsync(() -> {
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement("DELETE FROM prohibited_items WHERE item_type = ?")) {
                ps.setString(1, mat.name());
                ps.executeUpdate();
                DebugManager.log(DebugFlag.ITEM_DISABLE, "Removed prohibited item: " + mat.name());
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public Set<Material> getProhibitedMaterials() {
        return prohibitedMaterials;
    }

    public boolean purgeInventory(Player player, Inventory inventory) {
        if (inventory == null) return false;
        boolean purged = false;
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (item != null && isProhibited(item)) {
                inventory.setItem(i, null);
                purged = true;
                player.sendMessage(TextUtil.format("<red>⚠ Prohibited item (" + item.getType().name() + ") destroyed by Royal Decree!</red>"));
            }
        }
        return purged;
    }

    public boolean purgePlayerFull(Player player) {
        boolean purged = purgeInventory(player, player.getInventory());
        if (purgeInventory(player, player.getEnderChest())) purged = true;
        return purged;
    }
}
