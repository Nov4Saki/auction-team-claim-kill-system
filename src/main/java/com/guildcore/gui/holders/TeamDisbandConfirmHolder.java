package com.guildcore.gui.holders;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class TeamDisbandConfirmHolder implements InventoryHolder {
    private final int teamId;

    public TeamDisbandConfirmHolder(int teamId) {
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
