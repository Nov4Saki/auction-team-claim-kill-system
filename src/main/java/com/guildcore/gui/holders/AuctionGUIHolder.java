package com.guildcore.gui.holders;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class AuctionGUIHolder implements InventoryHolder {
    private final int page;
    private final String category;
    private final String searchQuery;

    public AuctionGUIHolder(int page, String category, String searchQuery) {
        this.page = page;
        this.category = category;
        this.searchQuery = searchQuery;
    }

    public AuctionGUIHolder() {
        this(1, "ALL", "");
    }

    public int getPage() { return page; }
    public String getCategory() { return category; }
    public String getSearchQuery() { return searchQuery; }

    @Override
    public Inventory getInventory() { return null; }
}
