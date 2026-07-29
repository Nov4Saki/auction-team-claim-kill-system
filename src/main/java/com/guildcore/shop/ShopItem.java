package com.guildcore.shop;

import org.bukkit.inventory.ItemStack;

public class ShopItem {
    private final int id;
    private final int categoryId;
    private final ItemStack item;
    private long buyPrice;
    private long sellPrice;
    private int slot;

    public ShopItem(int id, int categoryId, ItemStack item, long buyPrice, long sellPrice, int slot) {
        this.id = id;
        this.categoryId = categoryId;
        this.item = item;
        this.buyPrice = buyPrice;
        this.sellPrice = sellPrice;
        this.slot = slot;
    }

    public int getId() { return id; }
    public int getCategoryId() { return categoryId; }
    public ItemStack getItem() { return item; }
    public long getBuyPrice() { return buyPrice; }
    public void setBuyPrice(long buyPrice) { this.buyPrice = buyPrice; }
    public long getSellPrice() { return sellPrice; }
    public void setSellPrice(long sellPrice) { this.sellPrice = sellPrice; }
    public int getSlot() { return slot; }
    public void setSlot(int slot) { this.slot = slot; }
}
