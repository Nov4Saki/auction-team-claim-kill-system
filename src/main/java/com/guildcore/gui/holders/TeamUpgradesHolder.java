package com.guildcore.gui.holders;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class TeamUpgradesHolder implements InventoryHolder {
    private final int teamId;

    public TeamUpgradesHolder(int teamId) {
        this.teamId = teamId;
    }

    public int getTeamId() { return teamId; }

    @Override
    public Inventory getInventory() { return null; }
}
