package com.guildcore.crates;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public class CrateConfirmGUIHolder implements InventoryHolder {
    private final String crateName;
    private final int slotIndex;
    private final ItemStack selectedItem;

    public CrateConfirmGUIHolder(String crateName, int slotIndex, ItemStack selectedItem) {
        this.crateName = crateName;
        this.slotIndex = slotIndex;
        this.selectedItem = selectedItem;
    }

    public String getCrateName() {
        return crateName;
    }

    public int getSlotIndex() {
        return slotIndex;
    }

    public ItemStack getSelectedItem() {
        return selectedItem;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
