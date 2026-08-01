package com.guildcore.gui.holders;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class TeamMembersHolder implements InventoryHolder {
    private final int teamId;
    private final int page;

    public TeamMembersHolder(int teamId, int page) {
        this.teamId = teamId;
        this.page = page;
    }

    public int getTeamId() {
        return teamId;
    }

    public int getPage() {
        return page;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
