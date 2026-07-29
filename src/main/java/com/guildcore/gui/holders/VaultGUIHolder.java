package com.guildcore.gui.holders;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class VaultGUIHolder implements InventoryHolder {
    private final int teamId;
    private final int page;

    public VaultGUIHolder(int teamId, int page) {
        this.teamId = teamId;
        this.page = page;
    }

    public int getTeamId() { return teamId; }
    public int getPage() { return page; }

    @Override public Inventory getInventory() { return null; }
}
