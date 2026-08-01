package com.guildcore.gui.holders;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

public class TeamKickConfirmHolder implements InventoryHolder {
    private final int teamId;
    private final UUID targetUuid;
    private final String targetName;

    public TeamKickConfirmHolder(int teamId, UUID targetUuid, String targetName) {
        this.teamId = teamId;
        this.targetUuid = targetUuid;
        this.targetName = targetName;
    }

    public int getTeamId() {
        return teamId;
    }

    public UUID getTargetUuid() {
        return targetUuid;
    }

    public String getTargetName() {
        return targetName;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
