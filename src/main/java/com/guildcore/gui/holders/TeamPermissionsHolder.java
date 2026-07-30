package com.guildcore.gui.holders;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class TeamPermissionsHolder implements InventoryHolder {
    private final int teamId;
    private final String selectedRole;

    public TeamPermissionsHolder(int teamId, String selectedRole) {
        this.teamId = teamId;
        this.selectedRole = selectedRole != null ? selectedRole.toUpperCase() : "MEMBER";
    }

    public int getTeamId() { return teamId; }
    public String getSelectedRole() { return selectedRole; }

    @Override
    public Inventory getInventory() { return null; }
}
