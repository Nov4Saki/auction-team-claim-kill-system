package com.guildcore.gui.holders;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

public class TradeGUIHolder implements InventoryHolder {
    private final UUID player1;
    private final UUID player2;

    public TradeGUIHolder(UUID player1, UUID player2) {
        this.player1 = player1;
        this.player2 = player2;
    }

    public UUID getPlayer1() {
        return player1;
    }

    public UUID getPlayer2() {
        return player2;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
