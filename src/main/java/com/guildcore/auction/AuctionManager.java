package com.guildcore.auction;

import com.guildcore.config.SettingsManager;
import com.guildcore.database.DatabaseManager;
import com.guildcore.debug.DebugFlag;
import com.guildcore.debug.DebugManager;
import com.guildcore.economy.EconomyManager;
import com.guildcore.util.ItemSerializer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AuctionManager {
    private final DatabaseManager dbManager;
    private final EconomyManager economyManager;
    private final SettingsManager settingsManager;
    private final Map<Integer, AuctionItem> auctionItems = new ConcurrentHashMap<>();

    public AuctionManager(DatabaseManager dbManager, EconomyManager economyManager, SettingsManager settingsManager) {
        this.dbManager = dbManager;
        this.economyManager = economyManager;
        this.settingsManager = settingsManager;
    }

    public void loadAuctions() {
        dbManager.executeAsync(() -> {
            try (Connection conn = dbManager.getConnection()) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT id, seller_uuid, seller_name, category, price, is_bid, current_bid, bidder_uuid, item_data, created_at, purchasable_at, expires_at, is_sold, is_expired FROM auction_items WHERE is_claimed = 0");
                     ResultSet rs = ps.executeQuery()) {

                    auctionItems.clear();
                    long now = System.currentTimeMillis();
                    while (rs.next()) {
                        int id = rs.getInt("id");
                        UUID seller = UUID.fromString(rs.getString("seller_uuid"));
                        String sellerName = rs.getString("seller_name");
                        String cat = rs.getString("category");
                        long price = rs.getLong("price");
                        boolean isBid = rs.getBoolean("is_bid");
                        long currentBid = rs.getLong("current_bid");
                        String bidderStr = rs.getString("bidder_uuid");
                        UUID bidder = bidderStr != null ? UUID.fromString(bidderStr) : null;
                        ItemStack item = ItemSerializer.deserializeItem(rs.getString("item_data"));

                        long createdMs = parseTimestampToMs(rs, "created_at", now);
                        long purchasableMs = parseTimestampToMs(rs, "purchasable_at", now);
                        long expiresAt = parseTimestampToMs(rs, "expires_at", now + (48 * 3600 * 1000L));
                        boolean isSold = rs.getBoolean("is_sold");
                        boolean isExpired = rs.getBoolean("is_expired");

                        AuctionItem auction = new AuctionItem(id, seller, sellerName, cat, price, isBid, currentBid, bidder, item, createdMs, purchasableMs, expiresAt, isSold, isExpired);
                        auctionItems.put(id, auction);
                    }
                    DebugManager.log(DebugFlag.AUCTION_PURCHASES, "Loaded " + auctionItems.size() + " active auction listings from database.");
                    checkAndSweepExpiredItems();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private long parseTimestampToMs(ResultSet rs, String columnName, long fallbackMs) {
        try {
            java.sql.Timestamp ts = rs.getTimestamp(columnName);
            if (ts != null) return ts.getTime();
        } catch (Exception ignored) {}
        try {
            long val = rs.getLong(columnName);
            if (val > 0) return val > 2000000000L ? val : val * 1000L;
        } catch (Exception ignored) {}
        return fallbackMs;
    }

    public void checkAndSweepExpiredItems() {
        long now = System.currentTimeMillis();
        for (AuctionItem item : new ArrayList<>(auctionItems.values())) {
            boolean swept = false;
            synchronized (item) {
                if (!item.isSold() && !item.isExpired() && now >= item.getExpiresAtMs()) {
                    item.setExpired(true);
                    auctionItems.remove(item.getId());
                    swept = true;
                }
            }

            if (swept) {
                addToStash(item.getSellerUuid(), item.getItem());
                dbManager.executeAsync(() -> {
                    try (Connection conn = dbManager.getConnection();
                         PreparedStatement ps = conn.prepareStatement("UPDATE auction_items SET is_expired = 1 WHERE id = ?")) {
                        ps.setInt(1, item.getId());
                        ps.executeUpdate();
                        DebugManager.log(DebugFlag.AUCTION_PURCHASES, "Swept expired auction item #" + item.getId() + " to stash for " + item.getSellerName());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });

                Player seller = org.bukkit.Bukkit.getPlayer(item.getSellerUuid());
                if (seller != null && seller.isOnline()) {
                    seller.sendMessage(com.guildcore.util.TextUtil.format("<gold>📜 [Auction] Your listing for <yellow>" + item.getItem().getType() + "</yellow> expired and was moved to your Auction Stash (/ah stash).</gold>"));
                }
            }
        }
    }

    public int getMaxListingsForPlayer(Player player) {
        if (player.hasPermission("guildcore.auction.limit.op") || player.isOp()) return Integer.MAX_VALUE;
        if (player.hasPermission("guildcore.auction.limit.5")) return 30;
        if (player.hasPermission("guildcore.auction.limit.4")) return 20;
        if (player.hasPermission("guildcore.auction.limit.3")) return 15;
        if (player.hasPermission("guildcore.auction.limit.2")) return 10;
        if (player.hasPermission("guildcore.auction.limit.1")) return 5;
        return settingsManager.getInt("auction.max_listings_default", 3);
    }

    public int getListingDurationHoursForPlayer(Player player) {
        if (player.hasPermission("guildcore.auction.duration.op") || player.isOp()) return settingsManager.getInt("auction.duration_hours.op", 168);
        if (player.hasPermission("guildcore.auction.duration.5")) return settingsManager.getInt("auction.duration_hours.tier5", 120);
        if (player.hasPermission("guildcore.auction.duration.4")) return settingsManager.getInt("auction.duration_hours.tier4", 96);
        if (player.hasPermission("guildcore.auction.duration.3")) return settingsManager.getInt("auction.duration_hours.tier3", 72);
        if (player.hasPermission("guildcore.auction.duration.2")) return settingsManager.getInt("auction.duration_hours.tier2", 48);
        if (player.hasPermission("guildcore.auction.duration.1")) return settingsManager.getInt("auction.duration_hours.tier1", 24);
        return settingsManager.getInt("auction.duration_hours_default", 48);
    }

    public int getPlayerActiveListingCount(UUID playerUuid) {
        checkAndSweepExpiredItems();
        int count = 0;
        for (AuctionItem item : auctionItems.values()) {
            if (item.getSellerUuid().equals(playerUuid) && !item.isSold() && !item.isExpired()) {
                count++;
            }
        }
        return count;
    }

    public List<AuctionItem> getPlayerListings(UUID playerUuid) {
        checkAndSweepExpiredItems();
        List<AuctionItem> list = new ArrayList<>();
        for (AuctionItem item : auctionItems.values()) {
            if (item.getSellerUuid().equals(playerUuid) && !item.isSold() && !item.isExpired()) {
                list.add(item);
            }
        }
        return list;
    }

    public boolean listItem(Player seller, ItemStack item, long price, boolean isBid) {
        if (item == null || item.getType().isAir() || price <= 0) return false;

        long maxPrice = settingsManager.getLong("auction.max_listing_price", 1000000000L);
        if (price > maxPrice) {
            seller.sendMessage(com.guildcore.util.TextUtil.format("<red>[Auction] Price exceeds max listing price limit of $" + String.format("%,d", maxPrice) + "!</red>"));
            return false;
        }

        int maxListings = getMaxListingsForPlayer(seller);
        if (getPlayerActiveListingCount(seller.getUniqueId()) >= maxListings) {
            seller.sendMessage(com.guildcore.util.TextUtil.format("<red>[Auction] You have reached your maximum active listings limit (" + maxListings + ")!</red>"));
            return false;
        }

        UUID sellerUuid = seller.getUniqueId();
        String sellerName = seller.getName();
        String category = AuctionCategoryUtil.getCategory(item.getType());
        String itemData = ItemSerializer.serializeItem(item);
        long now = System.currentTimeMillis();
        int cooldownSec = settingsManager.getInt("auction.listing_cooldown_sec", 30);
        long purchasableAtMs = now + (cooldownSec * 1000L);
        int durationHours = getListingDurationHoursForPlayer(seller);
        long durationMs = durationHours * 3600 * 1000L;
        long expiresAtMs = now + durationMs;

        dbManager.executeAsync(() -> {
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT INTO auction_items (seller_uuid, seller_name, category, price, is_bid, current_bid, item_data, purchasable_at, expires_at) VALUES (?, ?, ?, ?, ?, ?, ?, datetime(?, 'unixepoch'), datetime(?, 'unixepoch'))",
                         Statement.RETURN_GENERATED_KEYS)) {

                ps.setString(1, sellerUuid.toString());
                ps.setString(2, sellerName);
                ps.setString(3, category);
                ps.setLong(4, price);
                ps.setBoolean(5, isBid);
                ps.setLong(6, isBid ? price : 0);
                ps.setString(7, itemData);
                ps.setLong(8, purchasableAtMs / 1000L);
                ps.setLong(9, expiresAtMs / 1000L);
                ps.executeUpdate();

                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        int id = rs.getInt(1);
                        AuctionItem auction = new AuctionItem(id, sellerUuid, sellerName, category, price, isBid, isBid ? price : 0, null, item, now, purchasableAtMs, expiresAtMs, false, false);
                        auctionItems.put(id, auction);
                        DebugManager.log(DebugFlag.AUCTION_PURCHASES, "Listed auction item #" + id + " (" + item.getType() + ") for $" + price + " by " + sellerName + " (Duration: " + durationHours + "h)");
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        if (cooldownSec > 0) {
            seller.sendMessage(com.guildcore.util.TextUtil.format("<green>✔ Offered item to Grand Bazaar for $" + String.format("%,d", price) + " Gold!</green>"));
            seller.sendMessage(com.guildcore.util.TextUtil.format("<yellow>⏳ In " + cooldownSec + "s grace period. View/cancel in <gold>/ah list</gold> before it becomes publicly purchasable!</yellow>"));
        } else {
            seller.sendMessage(com.guildcore.util.TextUtil.format("<green>✔ Listed item on Grand Bazaar for $" + String.format("%,d", price) + " Gold!</green>"));
        }

        return true;
    }

    public boolean cancelListing(Player seller, AuctionItem item) {
        if (item == null) return false;
        synchronized (item) {
            if (item.isSold() || item.isExpired()) return false;
            if (!seller.getUniqueId().equals(item.getSellerUuid()) && !seller.isOp()) return false;

            item.setSold(true);
            auctionItems.remove(item.getId());
        }

        if (seller.getInventory().firstEmpty() == -1) {
            addToStash(seller.getUniqueId(), item.getItem());
            seller.sendMessage(com.guildcore.util.TextUtil.format("<yellow>[Auction] Your inventory was full! Cancelled item sent to your Auction Stash (/ah stash).</yellow>"));
        } else {
            seller.getInventory().addItem(item.getItem());
            seller.sendMessage(com.guildcore.util.TextUtil.format("<green>[Auction] Listing cancelled! Item returned to your inventory.</green>"));
        }

        dbManager.executeAsync(() -> {
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement("UPDATE auction_items SET is_sold = 1, is_claimed = 1 WHERE id = ?")) {
                ps.setInt(1, item.getId());
                ps.executeUpdate();
                DebugManager.log(DebugFlag.AUCTION_PURCHASES, "Cancelled auction listing #" + item.getId() + " by " + seller.getName());
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        return true;
    }

    public boolean buyItem(Player buyer, AuctionItem item) {
        if (item == null) return false;

        long price = item.getPrice();
        synchronized (item) {
            if (item.isSold() || item.isExpired()) return false;
            if (buyer.getUniqueId().equals(item.getSellerUuid())) return false;

            if (!item.isPurchasable()) {
                buyer.sendMessage(com.guildcore.util.TextUtil.format("<red>[Auction] This listing is currently in its seller grace period and cannot be purchased for another " + item.getRemainingCooldownSec() + " seconds!</red>"));
                return false;
            }

            if (!economyManager.withdraw(buyer.getUniqueId(), price, "auction_buy")) {
                return false;
            }

            long taxPercent = settingsManager.getInt("economy.sales_tax_percent", 5);
            long tax = (long) (price * (taxPercent / 100.0));
            long sellerProceeds = price - tax;
            economyManager.deposit(item.getSellerUuid(), sellerProceeds, "auction_sale");

            item.setSold(true);
            auctionItems.remove(item.getId());
        }

        if (buyer.getInventory().firstEmpty() == -1) {
            addToStash(buyer.getUniqueId(), item.getItem());
            buyer.sendMessage(com.guildcore.util.TextUtil.format("<yellow>[Auction] Your inventory was full! Purchased item sent to your Auction Stash (/ah stash).</yellow>"));
        } else {
            buyer.getInventory().addItem(item.getItem());
        }

        dbManager.executeAsync(() -> {
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement("UPDATE auction_items SET is_sold = 1, is_claimed = 1 WHERE id = ?")) {
                ps.setInt(1, item.getId());
                ps.executeUpdate();
                DebugManager.log(DebugFlag.AUCTION_PURCHASES, "Auction item #" + item.getId() + " bought by " + buyer.getName() + " for $" + price);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        return true;
    }

    public void addToStash(UUID playerUuid, ItemStack item) {
        String data = ItemSerializer.serializeItem(item);
        dbManager.executeAsync(() -> {
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement("INSERT INTO auction_stash (player_uuid, item_data) VALUES (?, ?)")) {
                ps.setString(1, playerUuid.toString());
                ps.setString(2, data);
                ps.executeUpdate();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public List<AuctionItem> getActiveListings() {
        checkAndSweepExpiredItems();
        List<AuctionItem> active = new ArrayList<>();
        for (AuctionItem item : auctionItems.values()) {
            if (!item.isSold() && !item.isExpired()) {
                active.add(item);
            }
        }
        return active;
    }
}
