package com.guildcore.shop;

import com.guildcore.database.DatabaseManager;
import com.guildcore.economy.EconomyManager;
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
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ShopManager {
    private final DatabaseManager dbManager;
    private final EconomyManager economyManager;
    private final SchedulerWrapper scheduler;

    private final Map<Integer, ShopCategory> categories = new ConcurrentHashMap<>();
    private final Map<Integer, List<ShopItem>> categoryItems = new ConcurrentHashMap<>();

    public ShopManager(DatabaseManager dbManager, EconomyManager economyManager, SchedulerWrapper scheduler) {
        this.dbManager = dbManager;
        this.economyManager = economyManager;
        this.scheduler = scheduler;
    }

    public void loadShop() {
        dbManager.executeAsync(() -> {
            try (Connection conn = dbManager.getConnection()) {
                // Load categories
                try (PreparedStatement ps = conn.prepareStatement("SELECT id, name, icon_material, slot FROM shop_categories");
                     ResultSet rs = ps.executeQuery()) {
                    categories.clear();
                    while (rs.next()) {
                        int id = rs.getInt("id");
                        String name = rs.getString("name");
                        Material mat = Material.matchMaterial(rs.getString("icon_material"));
                        if (mat == null) mat = Material.CHEST;
                        int slot = rs.getInt("slot");

                        categories.put(id, new ShopCategory(id, name, mat, slot));
                    }
                }

                // If empty, insert default categories
                if (categories.isEmpty()) {
                    seedDefaultCategories(conn);
                }

                // Load items
                try (PreparedStatement ps = conn.prepareStatement("SELECT id, category_id, item_data, buy_price, sell_price, slot FROM shop_items");
                     ResultSet rs = ps.executeQuery()) {
                    categoryItems.clear();
                    while (rs.next()) {
                        int id = rs.getInt("id");
                        int catId = rs.getInt("category_id");
                        ItemStack item = ItemSerializer.deserializeItem(rs.getString("item_data"));
                        long buy = rs.getLong("buy_price");
                        long sell = rs.getLong("sell_price");
                        int slot = rs.getInt("slot");

                        ShopItem shopItem = new ShopItem(id, catId, item, buy, sell, slot);
                        categoryItems.computeIfAbsent(catId, k -> new ArrayList<>()).add(shopItem);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void seedDefaultCategories(Connection conn) throws Exception {
        String[] defaultCats = {"Building Blocks", "Farming & Food", "Ores & Minerals", "Combat & Armor"};
        Material[] mats = {Material.GRASS_BLOCK, Material.GOLDEN_CARROT, Material.DIAMOND, Material.NETHERITE_SWORD};

        for (int i = 0; i < defaultCats.length; i++) {
            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO shop_categories (name, icon_material, slot) VALUES (?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, defaultCats[i]);
                ps.setString(2, mats[i].name());
                ps.setInt(3, 10 + (i * 2));
                ps.executeUpdate();

                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        int id = rs.getInt(1);
                        categories.put(id, new ShopCategory(id, defaultCats[i], mats[i], 10 + (i * 2)));
                    }
                }
            }
        }
    }

    public int getMaxQuantityLimitForPlayer(Player player) {
        if (player.hasPermission("guildcore.shop.limit.op") || player.isOp()) return Integer.MAX_VALUE;
        if (player.hasPermission("guildcore.shop.limit.5")) return 1024;
        if (player.hasPermission("guildcore.shop.limit.4")) return 512;
        if (player.hasPermission("guildcore.shop.limit.3")) return 256;
        if (player.hasPermission("guildcore.shop.limit.2")) return 128;
        if (player.hasPermission("guildcore.shop.limit.1")) return 64;
        return 32; // default
    }

    public boolean buyItem(Player buyer, ShopItem shopItem, int quantity) {
        if (shopItem == null || shopItem.getBuyPrice() <= 0 || quantity <= 0) return false;

        int limit = getMaxQuantityLimitForPlayer(buyer);
        if (quantity > limit) {
            buyer.sendMessage(TextUtil.format("<red>Quantity exceeds your rank transaction limit of " + limit + " items!</red>"));
            return false;
        }

        long totalCost = shopItem.getBuyPrice() * quantity;
        if (!economyManager.withdraw(buyer.getUniqueId(), totalCost, "shop_buy")) {
            buyer.sendMessage(TextUtil.format("<red>Insufficient coins (requires $" + totalCost + ").</red>"));
            return false;
        }

        ItemStack bought = shopItem.getItem().clone();
        bought.setAmount(quantity);
        buyer.getInventory().addItem(bought);
        buyer.sendMessage(TextUtil.format("<green>Purchased " + quantity + "x " + shopItem.getItem().getType() + " for $" + totalCost + "!</green>"));
        return true;
    }

    public boolean sellItem(Player seller, ShopItem shopItem, int quantity) {
        if (shopItem == null || shopItem.getSellPrice() <= 0 || quantity <= 0) return false;

        int limit = getMaxQuantityLimitForPlayer(seller);
        if (quantity > limit) {
            seller.sendMessage(TextUtil.format("<red>Quantity exceeds your rank transaction limit of " + limit + " items!</red>"));
            return false;
        }

        ItemStack sample = shopItem.getItem();
        int hasAmount = 0;
        for (ItemStack item : seller.getInventory().getContents()) {
            if (item != null && item.isSimilar(sample)) {
                hasAmount += item.getAmount();
            }
        }

        if (hasAmount < quantity) {
            seller.sendMessage(TextUtil.format("<red>You do not have " + quantity + "x of this item in your inventory!</red>"));
            return false;
        }

        int remainingToRemove = quantity;
        for (int i = 0; i < seller.getInventory().getSize(); i++) {
            ItemStack item = seller.getInventory().getItem(i);
            if (item != null && item.isSimilar(sample)) {
                int count = item.getAmount();
                if (count <= remainingToRemove) {
                    remainingToRemove -= count;
                    seller.getInventory().setItem(i, null);
                } else {
                    item.setAmount(count - remainingToRemove);
                    remainingToRemove = 0;
                }
                if (remainingToRemove <= 0) break;
            }
        }

        long totalEarned = shopItem.getSellPrice() * quantity;
        economyManager.deposit(seller.getUniqueId(), totalEarned, "shop_sell");
        seller.sendMessage(TextUtil.format("<green>Sold " + quantity + "x " + sample.getType() + " for $" + totalEarned + "!</green>"));
        return true;
    }

    public void openShopMainMenu(Player player) {
        Inventory inv = Bukkit.createInventory(new ShopGUIHolder(0), 27, TextUtil.format("<gold>🛒 Server Shop</gold>"));

        // Fill background border
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, new GUIItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name("<gray> </gray>").build());
        }

        // Dynamically center category slots
        List<ShopCategory> list = new ArrayList<>(categories.values());
        int count = Math.min(7, list.size());
        int startSlot = 10 + (7 - count) / 2;

        for (int i = 0; i < count; i++) {
            ShopCategory cat = list.get(i);
            int targetSlot = startSlot + i;
            cat.setSlot(targetSlot);
            inv.setItem(targetSlot, new GUIItemBuilder(cat.getIcon()).name("<gold>" + cat.getName() + "</gold>")
                    .lore(List.of("<yellow>Click to browse items</yellow>")).build());
        }

        scheduler.runSync(player, () -> player.openInventory(inv));
    }

    public void openShopCategoryMenu(Player player, int categoryId) {
        ShopCategory cat = categories.get(categoryId);
        if (cat == null) return;

        Inventory inv = Bukkit.createInventory(new ShopGUIHolder(categoryId), 54, TextUtil.format("<gold>🛒 Shop: " + cat.getName() + "</gold>"));

        // Fill border
        for (int i = 0; i < 54; i++) {
            if (i < 9 || i >= 45 || i % 9 == 0 || i % 9 == 8) {
                inv.setItem(i, new GUIItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name("<gray> </gray>").build());
            }
        }

        List<ShopItem> items = categoryItems.getOrDefault(categoryId, new ArrayList<>());

        // Centered grid slots inside 6-row inventory (Slots 10-16, 19-25, 28-34, 37-43)
        int[] innerSlots = {
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34,
                37, 38, 39, 40, 41, 42, 43
        };

        for (int i = 0; i < Math.min(items.size(), innerSlots.length); i++) {
            ShopItem shopItem = items.get(i);
            int slot = innerSlots[i];
            shopItem.setSlot(slot);

            ItemStack display = shopItem.getItem().clone();
            ItemMeta meta = display.getItemMeta();
            if (meta != null) {
                List<String> lore = meta.hasLore() && meta.getLore() != null ? meta.getLore() : new ArrayList<>();
                lore.add("§aBuy Price: §e$" + shopItem.getBuyPrice());
                lore.add("§cSell Price: §e$" + shopItem.getSellPrice());
                lore.add("§a[Left-Click] Buy 1 | [Shift-Left] Buy 16");
                lore.add("§c[Right-Click] Sell 1 | [Shift-Right] Sell 16");
                meta.setLore(lore);
                display.setItemMeta(meta);
            }
            inv.setItem(slot, display);
        }

        inv.setItem(49, new GUIItemBuilder(Material.BARRIER).name("<red>◀ Back to Shop Categories</red>").build());
        scheduler.runSync(player, () -> player.openInventory(inv));
    }

    public void addShopItem(int categoryId, ItemStack item, long buyPrice, long sellPrice, int slot) {
        dbManager.executeAsync(() -> {
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT INTO shop_items (category_id, item_data, buy_price, sell_price, slot) VALUES (?, ?, ?, ?, ?)",
                         Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, categoryId);
                ps.setString(2, ItemSerializer.serializeItem(item));
                ps.setLong(3, buyPrice);
                ps.setLong(4, sellPrice);
                ps.setInt(5, slot);
                ps.executeUpdate();

                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        int id = rs.getInt(1);
                        ShopItem shopItem = new ShopItem(id, categoryId, item, buyPrice, sellPrice, slot);
                        categoryItems.computeIfAbsent(categoryId, k -> new ArrayList<>()).add(shopItem);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public Map<Integer, ShopCategory> getCategories() {
        return categories;
    }

    public List<ShopItem> getCategoryItems(int categoryId) {
        return categoryItems.getOrDefault(categoryId, new ArrayList<>());
    }
}
