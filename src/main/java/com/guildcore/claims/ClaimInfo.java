package com.guildcore.claims;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class ClaimInfo {
    private final String world;
    private final int chunkX;
    private final int chunkZ;
    private final UUID ownerUuid;
    private final Integer teamId;
    private final Set<String> flags;

    public ClaimInfo(String world, int chunkX, int chunkZ, UUID ownerUuid, Integer teamId, String flagsString) {
        this.world = world;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.ownerUuid = ownerUuid;
        this.teamId = teamId;
        this.flags = new HashSet<>();
        if (flagsString != null && !flagsString.trim().isEmpty()) {
            for (String f : flagsString.split(",")) {
                if (!f.trim().isEmpty()) this.flags.add(f.trim().toLowerCase());
            }
        }
    }

    public String getWorld() {
        return world;
    }

    public int getChunkX() {
        return chunkX;
    }

    public int getChunkZ() {
        return chunkZ;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public Integer getTeamId() {
        return teamId;
    }

    public boolean isTeamClaim() {
        return teamId != null && teamId > 0;
    }

    public boolean hasFlag(String flag) {
        return flags.contains(flag.toLowerCase());
    }

    public void setFlag(String flag, boolean enabled) {
        if (enabled) {
            flags.add(flag.toLowerCase());
        } else {
            flags.remove(flag.toLowerCase());
        }
    }

    public String getFlagsString() {
        return String.join(",", flags);
    }
}
