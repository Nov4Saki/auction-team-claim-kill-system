package com.guildcore.crates;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class CrateAdminGUIHolder implements InventoryHolder {
    private final String crateName;

    public CrateAdminGUIHolder(String crateName) {
        this.crateName = crateName;
    }

    public String getCrateName() {
        return crateName;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
