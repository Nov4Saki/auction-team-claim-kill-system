package com.guildcore.auction;

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
    private final Map<Integer, AuctionItem> auctionItems = new ConcurrentHashMap<>();

    public AuctionManager(DatabaseManager dbManager, EconomyManager economyManager) {
        this.dbManager = dbManager;
        this.economyManager = economyManager;
    }

    public void loadAuctions() {
        dbManager.executeAsync(() -> {
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT id, seller_uuid, category, price, is_bid, current_bid, bidder_uuid, item_data, expires_at, is_sold, is_expired FROM auction_items WHERE is_claimed = 0");
                 ResultSet rs = ps.executeQuery()) {

                auctionItems.clear();
                while (rs.next()) {
                    int id = rs.getInt("id");
                    UUID seller = UUID.fromString(rs.getString("seller_uuid"));
                    String cat = rs.getString("category");
                    long price = rs.getLong("price");
                    boolean isBid = rs.getBoolean("is_bid");
                    long currentBid = rs.getLong("current_bid");
                    String bidderStr = rs.getString("bidder_uuid");
                    UUID bidder = bidderStr != null ? UUID.fromString(bidderStr) : null;
                    ItemStack item = ItemSerializer.deserializeItem(rs.getString("item_data"));
                    long expiresAt = rs.getTimestamp("expires_at").getTime();
                    boolean isSold = rs.getBoolean("is_sold");
                    boolean isExpired = rs.getBoolean("is_expired");

                    AuctionItem auction = new AuctionItem(id, seller, cat, price, isBid, currentBid, bidder, item, expiresAt, isSold, isExpired);
                    auctionItems.put(id, auction);
                }
                DebugManager.log(DebugFlag.AUCTION_PURCHASES, "Loaded " + auctionItems.size() + " active auction listings from database.");
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public boolean listItem(Player seller, ItemStack item, long price, boolean isBid) {
        if (item == null || item.getType().isAir() || price <= 0) return false;

        UUID sellerUuid = seller.getUniqueId();
        String category = AuctionCategoryUtil.getCategory(item.getType());
        String itemData = ItemSerializer.serializeItem(item);
        long durationMs = 48 * 3600 * 1000L;
        long expiresAtMs = System.currentTimeMillis() + durationMs;

        dbManager.executeAsync(() -> {
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT INTO auction_items (seller_uuid, category, price, is_bid, current_bid, item_data, expires_at) VALUES (?, ?, ?, ?, ?, ?, datetime(?, 'unixepoch', 'localtime'))",
                         Statement.RETURN_GENERATED_KEYS)) {

                ps.setString(1, sellerUuid.toString());
                ps.setString(2, category);
                ps.setLong(3, price);
                ps.setBoolean(4, isBid);
                ps.setLong(5, isBid ? price : 0);
                ps.setString(6, itemData);
                ps.setLong(7, expiresAtMs / 1000L);
                ps.executeUpdate();

                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        int id = rs.getInt(1);
                        AuctionItem auction = new AuctionItem(id, sellerUuid, category, price, isBid, isBid ? price : 0, null, item, expiresAtMs, false, false);
                        auctionItems.put(id, auction);
                        DebugManager.log(DebugFlag.AUCTION_PURCHASES, "Listed auction item #" + id + " (" + item.getType() + ") for $" + price + " by " + seller.getName());
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        return true;
    }

    public boolean buyItem(Player buyer, AuctionItem item) {
        if (item == null || item.isSold() || item.isExpired()) return false;
        if (buyer.getUniqueId().equals(item.getSellerUuid())) return false;

        long price = item.getPrice();
        if (!economyManager.withdraw(buyer.getUniqueId(), price, "auction_buy")) {
            return false;
        }

        long tax = (long) (price * 0.05);
        long sellerProceeds = price - tax;
        economyManager.deposit(item.getSellerUuid(), sellerProceeds, "auction_sale");

        item.setSold(true);
        auctionItems.remove(item.getId());

        // Give item to buyer or add to stash if inventory is full
        if (buyer.getInventory().firstEmpty() == -1) {
            addToStash(buyer.getUniqueId(), item.getItem());
            buyer.sendMessage("§e[Auction] Your inventory was full! Item sent to your Auction Stash (/gcah stash).");
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
        List<AuctionItem> active = new ArrayList<>();
        for (AuctionItem item : auctionItems.values()) {
            if (!item.isSold() && !item.isExpired()) {
                active.add(item);
            }
        }
        return active;
    }
}
