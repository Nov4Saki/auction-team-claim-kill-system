package com.guildcore.crates;

import com.guildcore.database.DatabaseManager;
import com.guildcore.gui.GUIItemBuilder;
import com.guildcore.scheduler.SchedulerWrapper;
import com.guildcore.util.ItemSerializer;
import com.guildcore.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CrateManager {
    private final DatabaseManager dbManager;
    private final SchedulerWrapper scheduler;
    private final Map<String, Crate> crates = new ConcurrentHashMap<>();

    public CrateManager(DatabaseManager dbManager, SchedulerWrapper scheduler) {
        this.dbManager = dbManager;
        this.scheduler = scheduler;
    }

    public void loadCrates() {
        dbManager.executeAsync(() -> {
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT name, display_name, key_item_data, contents_data FROM crates");
                 ResultSet rs = ps.executeQuery()) {

                crates.clear();
                while (rs.next()) {
                    String name = rs.getString("name");
                    String displayName = rs.getString("display_name");
                    ItemStack key = ItemSerializer.deserializeItem(rs.getString("key_item_data"));
                    List<ItemStack> contents = ItemSerializer.deserializeItemList(rs.getString("contents_data"));

                    crates.put(name.toLowerCase(), new Crate(name, displayName, key, contents));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public Crate getCrate(String name) {
        return crates.get(name.toLowerCase());
    }

    public List<Crate> getAllCrates() {
        return new ArrayList<>(crates.values());
    }

    public void createCrate(String name, String displayName, ItemStack keyItem) {
        Crate crate = new Crate(name, displayName, keyItem, new ArrayList<>());
        crates.put(name.toLowerCase(), crate);

        dbManager.executeAsync(() -> {
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement("INSERT OR REPLACE INTO crates (name, display_name, key_item_data, contents_data) VALUES (?, ?, ?, ?)")) {
                ps.setString(1, name);
                ps.setString(2, displayName);
                ps.setString(3, ItemSerializer.serializeItem(keyItem));
                ps.setString(4, ItemSerializer.serializeItemList(new ArrayList<>()));
                ps.executeUpdate();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public void setKeyItem(String name, ItemStack keyItem) {
        Crate crate = getCrate(name);
        if (crate == null || keyItem == null) return;

        crate.setKeyItem(keyItem.clone());
        dbManager.executeAsync(() -> {
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement("UPDATE crates SET key_item_data = ? WHERE name = ?")) {
                ps.setString(1, ItemSerializer.serializeItem(keyItem));
                ps.setString(2, name);
                ps.executeUpdate();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public void deleteCrate(String name) {
        crates.remove(name.toLowerCase());
        dbManager.executeAsync(() -> {
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement("DELETE FROM crates WHERE name = ?")) {
                ps.setString(1, name);
                ps.executeUpdate();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public void saveCrateContents(String name, List<ItemStack> contents) {
        Crate crate = getCrate(name);
        if (crate == null) return;

        crate.setContents(contents);
        dbManager.executeAsync(() -> {
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement("UPDATE crates SET contents_data = ? WHERE name = ?")) {
                ps.setString(1, ItemSerializer.serializeItemList(contents));
                ps.setString(2, name);
                ps.executeUpdate();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public boolean hasKey(Player player, Crate crate) {
        if (player == null || crate == null) return false;
        ItemStack key = crate.getKeyItem();
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.isSimilar(key)) return true;
        }
        return false;
    }

    public boolean consumeKey(Player player, Crate crate) {
        if (!hasKey(player, crate)) return false;
        ItemStack key = crate.getKeyItem();
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item != null && item.isSimilar(key)) {
                if (item.getAmount() > 1) {
                    item.setAmount(item.getAmount() - 1);
                } else {
                    player.getInventory().setItem(i, null);
                }
                return true;
            }
        }
        return false;
    }

    public void giveKey(Player player, String crateName, int amount) {
        Crate crate = getCrate(crateName);
        if (crate == null || player == null) return;

        ItemStack key = crate.getKeyItem().clone();
        key.setAmount(amount);
        player.getInventory().addItem(key);
        player.sendMessage(TextUtil.format("<green>Received " + amount + "x Key for crate '" + crate.getDisplayName() + "'!</green>"));
    }

    public void openCrateChoiceMenu(Player player, Crate crate) {
        if (crate == null) return;

        List<ItemStack> contents = crate.getContents();
        int size = Math.min(54, (int) Math.ceil((contents.size() + 9) / 9.0) * 9);
        if (size < 27) size = 27;

        boolean hasKey = hasKey(player, crate);
        String title = hasKey ? "<gold>🎁 Choice Crate: " + crate.getDisplayName() + "</gold>" : "<gold>🔍 Inspecting Crate: " + crate.getDisplayName() + "</gold>";
        Inventory inv = Bukkit.createInventory(new CrateGUIHolder(crate.getName()), size, TextUtil.format(title));

        for (int i = 0; i < contents.size(); i++) {
            ItemStack item = contents.get(i).clone();
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                List<String> lore = meta.hasLore() && meta.getLore() != null ? meta.getLore() : new ArrayList<>();
                if (hasKey) {
                    lore.add("§a§l[Click to Claim This Item]");
                } else {
                    lore.add("§c§l[Crate Key Required to Claim]");
                }
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
            inv.setItem(i, item);
        }

        scheduler.runSync(player, () -> player.openInventory(inv));
    }

    public void openCrateConfirmMenu(Player player, Crate crate, int slotIndex, ItemStack selectedItem) {
        Inventory inv = Bukkit.createInventory(new CrateConfirmGUIHolder(crate.getName(), slotIndex, selectedItem), 27, TextUtil.format("<gold>🎁 Confirm Crate Choice?</gold>"));

        for (int i = 0; i < 27; i++) {
            inv.setItem(i, new GUIItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name("<gray> </gray>").build());
        }

        inv.setItem(11, new GUIItemBuilder(Material.GREEN_WOOL).name("<green>✔ CONFIRM CLAIM</green>").lore(List.of("<gray>Click to consume key and receive item!</gray>")).build());
        inv.setItem(13, selectedItem.clone());
        inv.setItem(15, new GUIItemBuilder(Material.RED_WOOL).name("<red>✖ CANCEL</red>").lore(List.of("<gray>Return to crate inspection</gray>")).build());

        scheduler.runSync(player, () -> player.openInventory(inv));
    }

    public void openCrateAdminHub(Player player) {
        Inventory inv = Bukkit.createInventory(new CrateAdminHubHolder(), 54, TextUtil.format("<red>⚙ Modular Choice Crates Admin Hub</red>"));

        List<Crate> list = getAllCrates();
        for (int i = 0; i < Math.min(45, list.size()); i++) {
            Crate crate = list.get(i);
            inv.setItem(i, new GUIItemBuilder(Material.CHEST).name("<gold>" + crate.getDisplayName() + "</gold>")
                    .lore(List.of(
                            "<gray>Items Inside: <white>" + crate.getContents().size() + "</white></gray>",
                            "<gray>Key Item: <yellow>" + crate.getKeyItem().getType() + "</yellow></gray>",
                            "<green>[Left-Click] Edit Crate Items</green>",
                            "<yellow>[Right-Click] Set Key (Item in Main Hand)</yellow>",
                            "<red>[Shift-Right-Click] Delete Crate</red>"
                    )).build());
        }

        inv.setItem(45, new GUIItemBuilder(Material.EMERALD_BLOCK).name("<green>➕ Create New Choice Crate</green>").lore(List.of("<gray>Click to type crate name in chat</gray>")).build());
        inv.setItem(53, new GUIItemBuilder(Material.BARRIER).name("<red>✖ Close Menu</red>").build());

        scheduler.runSync(player, () -> player.openInventory(inv));
    }

    public void openCrateAdminEditor(Player player, Crate crate) {
        if (crate == null) return;

        Inventory inv = Bukkit.createInventory(new CrateAdminGUIHolder(crate.getName()), 54, TextUtil.format("<red>⚙ Edit Crate: " + crate.getDisplayName() + "</red>"));
        for (int i = 0; i < crate.getContents().size(); i++) {
            if (i < 53) inv.setItem(i, crate.getContents().get(i).clone());
        }
        inv.setItem(53, new GUIItemBuilder(Material.GREEN_WOOL).name("<green>✔ SAVE CRATE CONTENTS</green>").build());

        scheduler.runSync(player, () -> player.openInventory(inv));
    }
}
