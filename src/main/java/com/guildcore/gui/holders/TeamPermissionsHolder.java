package com.guildcore.gui.holders;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class TeamPermissionsHolder implements InventoryHolder {
    private final int teamId;

    public TeamPermissionsHolder(int teamId) {
        this.teamId = teamId;
    }

    public int getTeamId() { return teamId; }

    @Override
    public Inventory getInventory() { return null; }
}
