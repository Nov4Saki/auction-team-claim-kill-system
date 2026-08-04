package com.guildcore.gui.holders;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class ShieldInfoHolder implements InventoryHolder {
    private final int teamId;

    public ShieldInfoHolder(int teamId) {
        this.teamId = teamId;
    }

    public int getTeamId() {
        return teamId;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
