package com.guildcore.gui.holders;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class LeaderboardGUIHolder implements InventoryHolder {
    private final String tab; // "PLAYER" or "GUILD"
    private final String sortBy;

    public LeaderboardGUIHolder(String tab, String sortBy) {
        this.tab = tab;
        this.sortBy = sortBy;
    }

    public String getTab() {
        return tab;
    }

    public String getSortBy() {
        return sortBy;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
