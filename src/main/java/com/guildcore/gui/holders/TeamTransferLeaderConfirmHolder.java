package com.guildcore.gui.holders;

import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class TeamTransferLeaderConfirmHolder implements InventoryHolder {
    private final OfflinePlayer targetSuccessor;

    public TeamTransferLeaderConfirmHolder(OfflinePlayer targetSuccessor) {
        this.targetSuccessor = targetSuccessor;
    }

    public OfflinePlayer getTargetSuccessor() {
        return targetSuccessor;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
