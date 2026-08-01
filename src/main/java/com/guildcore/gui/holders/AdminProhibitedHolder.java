package com.guildcore.gui.holders;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class AdminProhibitedHolder implements InventoryHolder {
    private final int page;

    public AdminProhibitedHolder() {
        this(1);
    }

    public AdminProhibitedHolder(int page) {
        this.page = page;
    }

    public int getPage() {
        return page;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}

