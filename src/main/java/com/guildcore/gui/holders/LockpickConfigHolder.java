package com.guildcore.gui.holders;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class LockpickConfigHolder implements InventoryHolder {
    private final int page;

    public LockpickConfigHolder() {
        this(1);
    }

    public LockpickConfigHolder(int page) {
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
