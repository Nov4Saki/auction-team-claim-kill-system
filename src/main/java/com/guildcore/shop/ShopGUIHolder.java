package com.guildcore.shop;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class ShopGUIHolder implements InventoryHolder {
    private final int categoryId;

    public ShopGUIHolder(int categoryId) {
        this.categoryId = categoryId;
    }

    public int getCategoryId() {
        return categoryId;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
