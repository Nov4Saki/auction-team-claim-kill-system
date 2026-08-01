package com.guildcore.gui.holders;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class ClaimItemSelectorHolder implements InventoryHolder {
    private int quantity = 1;

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = Math.max(1, Math.min(64, quantity));
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
