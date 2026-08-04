package com.guildcore.gui.holders;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class GuildCoreGUIHolder implements InventoryHolder {
    private final int teamId;

    public GuildCoreGUIHolder(int teamId) {
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
