// FILE: src/main/java/com/guildcore/gui/holders/ClaimChestGUIHolder.java
package com.guildcore.gui.holders;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class ClaimChestGUIHolder implements InventoryHolder {
    private final int teamId;

    public ClaimChestGUIHolder(int teamId) {
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