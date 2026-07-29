package com.guildcore.auction;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class AuctionItem {
    private final int id;
    private final UUID sellerUuid;
    private final String sellerName;
    private final String category;
    private final long price;
    private final boolean isBid;
    private long currentBid;
    private UUID bidderUuid;
    private final ItemStack item;
    private final long createdMs;
    private final long purchasableAtMs;
    private final long expiresAtMs;
    private boolean isSold;
    private boolean isExpired;

    public AuctionItem(int id, UUID sellerUuid, String sellerName, String category, long price, boolean isBid, long currentBid, UUID bidderUuid, ItemStack item, long createdMs, long purchasableAtMs, long expiresAtMs, boolean isSold, boolean isExpired) {
        this.id = id;
        this.sellerUuid = sellerUuid;
        this.sellerName = sellerName != null ? sellerName : "Unknown";
        this.category = category;
        this.price = price;
        this.isBid = isBid;
        this.currentBid = currentBid;
        this.bidderUuid = bidderUuid;
        this.item = item;
        this.createdMs = createdMs;
        this.purchasableAtMs = purchasableAtMs;
        this.expiresAtMs = expiresAtMs;
        this.isSold = isSold;
        this.isExpired = isExpired;
    }

    public int getId() {
        return id;
    }

    public UUID getSellerUuid() {
        return sellerUuid;
    }

    public String getSellerName() {
        return sellerName;
    }

    public String getCategory() {
        return category;
    }

    public long getPrice() {
        return price;
    }

    public boolean isBid() {
        return isBid;
    }

    public long getCurrentBid() {
        return currentBid;
    }

    public void setCurrentBid(long currentBid) {
        this.currentBid = currentBid;
    }

    public UUID getBidderUuid() {
        return bidderUuid;
    }

    public void setBidderUuid(UUID bidderUuid) {
        this.bidderUuid = bidderUuid;
    }

    public ItemStack getItem() {
        return item != null ? item.clone() : null;
    }

    public long getCreatedMs() {
        return createdMs;
    }

    public long getPurchasableAtMs() {
        return purchasableAtMs;
    }

    public boolean isPurchasable() {
        return System.currentTimeMillis() >= purchasableAtMs;
    }

    public long getRemainingCooldownSec() {
        return Math.max(0, (purchasableAtMs - System.currentTimeMillis()) / 1000L);
    }

    public long getExpiresAtMs() {
        return expiresAtMs;
    }

    public boolean isSold() {
        return isSold;
    }

    public void setSold(boolean sold) {
        isSold = sold;
    }

    public boolean isExpired() {
        return isExpired || System.currentTimeMillis() > expiresAtMs;
    }

    public void setExpired(boolean expired) {
        isExpired = expired;
    }
}
