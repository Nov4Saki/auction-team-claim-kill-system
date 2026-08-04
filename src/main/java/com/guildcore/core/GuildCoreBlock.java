package com.guildcore.core;

import java.util.UUID;

public class GuildCoreBlock {
    private final int teamId;
    private final String world;
    private final int x;
    private final int y;
    private final int z;
    private int tier;
    private int currentHp;
    private int maxHp;
    private UUID armorStandUuid;
    private final long placedAt;

    public GuildCoreBlock(int teamId, String world, int x, int y, int z, int tier, int currentHp, int maxHp, UUID armorStandUuid, long placedAt) {
        this.teamId = teamId;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.tier = tier;
        this.currentHp = currentHp;
        this.maxHp = maxHp;
        this.armorStandUuid = armorStandUuid;
        this.placedAt = placedAt;
    }

    public int getTeamId() { return teamId; }
    public String getWorld() { return world; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }

    public int getTier() { return tier; }
    public void setTier(int tier) { this.tier = tier; }

    public int getCurrentHp() { return currentHp; }
    public void setCurrentHp(int currentHp) { this.currentHp = Math.max(0, currentHp); }

    public int getMaxHp() { return maxHp; }
    public void setMaxHp(int maxHp) { this.maxHp = maxHp; }

    public UUID getArmorStandUuid() { return armorStandUuid; }
    public void setArmorStandUuid(UUID armorStandUuid) { this.armorStandUuid = armorStandUuid; }

    public long getPlacedAt() { return placedAt; }

    public String getLocationKey() {
        return world + ":" + x + ":" + y + ":" + z;
    }
}
