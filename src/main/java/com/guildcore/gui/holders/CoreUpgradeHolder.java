package com.guildcore.gui.holders;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class CoreUpgradeHolder implements InventoryHolder {
    private final int teamId;
    private final int currentTier;

    public CoreUpgradeHolder(int teamId, int currentTier) {
        this.teamId = teamId;
        this.currentTier = currentTier;
    }

    public int getTeamId() {
        return teamId;
    }

    public int getCurrentTier() {
        return currentTier;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
