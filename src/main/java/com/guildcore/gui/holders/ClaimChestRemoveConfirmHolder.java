// FILE: src/main/java/com/guildcore/gui/holders/ClaimChestRemoveConfirmHolder.java
package com.guildcore.gui.holders;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class ClaimChestRemoveConfirmHolder implements InventoryHolder {
    private final int teamId;

    public ClaimChestRemoveConfirmHolder(int teamId) {
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