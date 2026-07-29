package com.guildcore.gui.holders;

import com.guildcore.auction.AuctionItem;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class AuctionConfirmHolder implements InventoryHolder {
    private final AuctionItem auctionItem;

    public AuctionConfirmHolder(AuctionItem auctionItem) {
        this.auctionItem = auctionItem;
    }

    public AuctionItem getAuctionItem() {
        return auctionItem;
    }

    @Override
    public Inventory getInventory() { return null; }
}
