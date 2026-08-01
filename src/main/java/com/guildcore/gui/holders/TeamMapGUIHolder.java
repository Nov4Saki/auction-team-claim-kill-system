package com.guildcore.gui.holders;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class TeamMapGUIHolder implements InventoryHolder {
    private final int centerChunkX;
    private final int centerChunkZ;

    public TeamMapGUIHolder(int centerChunkX, int centerChunkZ) {
        this.centerChunkX = centerChunkX;
        this.centerChunkZ = centerChunkZ;
    }

    public int getCenterChunkX() {
        return centerChunkX;
    }

    public int getCenterChunkZ() {
        return centerChunkZ;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
