package com.guildcore.gui.holders;

import com.guildcore.teams.Team;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class TeamBankGUIHolder implements InventoryHolder {
    private final Team team;

    public TeamBankGUIHolder(Team team) {
        this.team = team;
    }

    public Team getTeam() {
        return team;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
