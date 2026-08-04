package com.guildcore.teams;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.UUID;

public class Team {
    private final int id;
    private String name;
    private UUID leaderUuid;
    private int level;
    private long exp;
    private long bankBalance;
    private int maxMembers;
    private int maxClaims;
    private int vaultSlots = 9;
    private Location homeLocation;
    private Location nexusLocation;

    public Team(int id, String name, UUID leaderUuid, int level, long exp, long bankBalance, int maxMembers, int maxClaims) {
        this(id, name, leaderUuid, level, exp, bankBalance, maxMembers, maxClaims, 9);
    }

    public Team(int id, String name, UUID leaderUuid, int level, long exp, long bankBalance, int maxMembers, int maxClaims, int vaultSlots) {
        this.id = id;
        this.name = name;
        this.leaderUuid = leaderUuid;
        this.level = level;
        this.exp = exp;
        this.bankBalance = bankBalance;
        this.maxMembers = maxMembers;
        this.maxClaims = maxClaims;
        this.vaultSlots = Math.max(9, vaultSlots);
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public UUID getLeaderUuid() {
        return leaderUuid;
    }

    public void setLeaderUuid(UUID leaderUuid) {
        this.leaderUuid = leaderUuid;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public long getExp() {
        return exp;
    }

    public void addExp(long amount) {
        this.exp += amount;
    }

    public long getBankBalance() {
        return bankBalance;
    }

    public void setBankBalance(long bankBalance) {
        this.bankBalance = bankBalance;
    }

    public int getMaxMembers() {
        return maxMembers;
    }

    public void setMaxMembers(int maxMembers) {
        this.maxMembers = maxMembers;
    }

    public int getMaxClaims() {
        return maxClaims;
    }

    public void setMaxClaims(int maxClaims) {
        this.maxClaims = maxClaims;
    }

    public int getVaultSlots() {
        return vaultSlots;
    }

    public void setVaultSlots(int vaultSlots) {
        this.vaultSlots = Math.min(54, Math.max(9, vaultSlots));
    }

    public Location getHomeLocation() {
        return homeLocation;
    }

    public void setHomeLocation(Location homeLocation) {
        this.homeLocation = homeLocation;
    }

    public Location getNexusLocation() {
        return nexusLocation;
    }

    public void setNexusLocation(Location nexusLocation) {
        this.nexusLocation = nexusLocation;
    }
}
