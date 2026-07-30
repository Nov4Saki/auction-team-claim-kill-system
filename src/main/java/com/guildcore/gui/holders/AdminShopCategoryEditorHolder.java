package com.guildcore.gui.holders;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class AdminShopCategoryEditorHolder implements InventoryHolder {
    private final int categoryId;

    public AdminShopCategoryEditorHolder(int categoryId) {
        this.categoryId = categoryId;
    }

    public int getCategoryId() {
        return categoryId;
    }

    @Override public Inventory getInventory() { return null; }
}
