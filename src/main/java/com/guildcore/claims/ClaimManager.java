package com.guildcore.claims;

import com.guildcore.database.DatabaseManager;
import com.guildcore.debug.DebugFlag;
import com.guildcore.debug.DebugManager;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ClaimManager {
    private final DatabaseManager dbManager;
    private final Map<String, ClaimInfo> claimsCache = new ConcurrentHashMap<>();

    private com.guildcore.teams.TeamManager teamManager;
    private com.guildcore.teams.TeamPermissionManager permissionManager;
    private com.guildcore.core.GuildCoreManager guildCoreManager;
    private com.guildcore.config.SettingsManager settingsManager;

    public ClaimManager(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    public void setTeamManager(com.guildcore.teams.TeamManager teamManager) {
        this.teamManager = teamManager;
    }

    public void setPermissionManager(com.guildcore.teams.TeamPermissionManager permissionManager) {
        this.permissionManager = permissionManager;
    }

    public void setGuildCoreManager(com.guildcore.core.GuildCoreManager guildCoreManager) {
        this.guildCoreManager = guildCoreManager;
    }

    public void setSettingsManager(com.guildcore.config.SettingsManager settingsManager) {
        this.settingsManager = settingsManager;
    }

    public void loadClaims() {
        dbManager.executeAsync(() -> {
            try (Connection conn = dbManager.getConnection()) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT world, chunk_x, chunk_z, owner_uuid, team_id, flags FROM claims");
                     ResultSet rs = ps.executeQuery()) {
                    claimsCache.clear();
                    while (rs.next()) {
                        String world = rs.getString("world");
                        int cx = rs.getInt("chunk_x");
                        int cz = rs.getInt("chunk_z");
                        String ownerStr = rs.getString("owner_uuid");
                        UUID owner = ownerStr != null ? UUID.fromString(ownerStr) : null;
                        int teamIdVal = rs.getInt("team_id");
                        Integer teamId = rs.wasNull() ? null : teamIdVal;
                        String flags = rs.getString("flags");

                        String key = makeKey(world, cx, cz);
                        claimsCache.put(key, new ClaimInfo(world, cx, cz, owner, teamId, flags));
                    }
                    DebugManager.log(DebugFlag.CLAIM_PROTECTION,
                            "Loaded " + claimsCache.size() + " full-chunk claims from database.");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public String makeKey(String world, int cx, int cz) {
        return world + ":" + cx + ":" + cz;
    }

    public ClaimInfo getClaimAt(World world, int cx, int cz) {
        if (world == null) return null;
        return claimsCache.get(makeKey(world.getName(), cx, cz));
    }

    public ClaimInfo getClaimAt(Chunk chunk) {
        if (chunk == null) return null;
        return getClaimAt(chunk.getWorld(), chunk.getX(), chunk.getZ());
    }

    public boolean isClaimed(Chunk chunk) {
        return getClaimAt(chunk) != null;
    }

    /**
     * Creates a team claim for the given chunk.
     * Validates: guild core exists, contiguous if required, under claim limit.
     */
    public boolean createTeamClaim(UUID ownerUuid, int teamId, Chunk chunk) {
        if (isClaimed(chunk)) return false;

        // Validate guild core exists
        if (guildCoreManager != null && !guildCoreManager.hasCore(teamId)) {
            return false;
        }

        String worldName = chunk.getWorld().getName();
        int cx = chunk.getX();
        int cz = chunk.getZ();

        // Check contiguous requirement
        boolean requireContiguous = settingsManager != null &&
                settingsManager.getBoolean("claims.require_contiguous", true);

        if (requireContiguous) {
            // Allow if this is the chunk containing the guild core
            boolean isCoreChunk = false;
            if (guildCoreManager != null) {
                var core = guildCoreManager.getCoreForTeam(teamId);
                if (core != null && core.getWorld().equals(worldName) &&
                        (core.getX() >> 4) == cx && (core.getZ() >> 4) == cz) {
                    isCoreChunk = true;
                }
            }

            if (!isCoreChunk && !isAdjacentToTeamClaim(teamId, worldName, cx, cz)) {
                return false;
            }
        }

        // Check max claims limit
        int maxClaims = getMaxClaimsForTeam(teamId);
        int currentClaims = getTeamClaimsCount(teamId);
        if (currentClaims >= maxClaims) {
            return false;
        }

        ClaimInfo claim = new ClaimInfo(worldName, cx, cz, ownerUuid, teamId, "");
        claimsCache.put(makeKey(worldName, cx, cz), claim);

        dbManager.executeAsync(() -> {
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT INTO claims (world, chunk_x, chunk_z, owner_uuid, team_id) VALUES (?, ?, ?, ?, ?)")) {
                ps.setString(1, worldName);
                ps.setInt(2, cx);
                ps.setInt(3, cz);
                ps.setString(4, ownerUuid != null ? ownerUuid.toString() : null);
                ps.setInt(5, teamId);
                ps.executeUpdate();
                DebugManager.log(DebugFlag.CLAIM_PROTECTION,
                        "Created team claim at " + cx + "," + cz + " for team " + teamId);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        return true;
    }

    /**
     * Creates a team claim using the team leader's UUID.
     */
    public boolean createTeamClaim(int teamId, Chunk chunk) {
        UUID leaderUuid = null;
        if (teamManager != null) {
            com.guildcore.teams.Team team = teamManager.getTeam(teamId);
            if (team != null) leaderUuid = team.getLeaderUuid();
        }
        return createTeamClaim(leaderUuid, teamId, chunk);
    }

    public boolean unclaim(Chunk chunk) {
        ClaimInfo claim = getClaimAt(chunk);
        if (claim == null) return false;

        String worldName = chunk.getWorld().getName();
        int cx = chunk.getX();
        int cz = chunk.getZ();

        claimsCache.remove(makeKey(worldName, cx, cz));

        dbManager.executeAsync(() -> {
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "DELETE FROM claims WHERE world = ? AND chunk_x = ? AND chunk_z = ?")) {
                ps.setString(1, worldName);
                ps.setInt(2, cx);
                ps.setInt(3, cz);
                ps.executeUpdate();
                DebugManager.log(DebugFlag.CLAIM_PROTECTION,
                        "Unclaimed chunk at " + cx + "," + cz);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        return true;
    }

    public boolean canBuild(Player player, Chunk chunk) {
        if (player == null || chunk == null) return true;
        if (player.hasPermission("guildcore.admin")) return true;

        ClaimInfo claim = getClaimAt(chunk);
        if (claim == null) return true;

        // Team member check
        if (claim.getTeamId() != null && claim.getTeamId() > 0 && teamManager != null) {
            com.guildcore.teams.Team playerTeam = teamManager.getPlayerTeam(player.getUniqueId());
            if (playerTeam != null && playerTeam.getId() == claim.getTeamId()) {
                String role = teamManager.getPlayerRole(player.getUniqueId());
                if (permissionManager != null) {
                    return permissionManager.hasPermission(playerTeam.getId(), role, "BUILD");
                }
                return true;
            }
        }

        // Personal claim owner
        if (claim.getOwnerUuid() != null && claim.getOwnerUuid().equals(player.getUniqueId())) {
            return true;
        }

        return false;
    }

    /**
     * Checks if a chunk is adjacent (N/S/E/W) to any existing team claim.
     */
    public boolean isAdjacentToTeamClaim(int teamId, String world, int cx, int cz) {
        int[][] offsets = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] off : offsets) {
            String key = makeKey(world, cx + off[0], cz + off[1]);
            ClaimInfo neighbor = claimsCache.get(key);
            if (neighbor != null && neighbor.isTeamClaim() &&
                    neighbor.getTeamId() != null && neighbor.getTeamId() == teamId) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gets the maximum claims allowed for a team based on core tier.
     */
    public int getMaxClaimsForTeam(int teamId) {
        if (guildCoreManager != null) {
            var core = guildCoreManager.getCoreForTeam(teamId);
            if (core != null) {
                return guildCoreManager.getMaxClaimsForTier(core.getTier());
            }
        }
        if (settingsManager != null) {
            return settingsManager.getInt("claims.max_per_team", 32);
        }
        return 32;
    }

    public int getTeamClaimsCount(int teamId) {
        int count = 0;
        for (ClaimInfo info : claimsCache.values()) {
            if (info.isTeamClaim() && info.getTeamId() != null && info.getTeamId() == teamId) {
                count++;
            }
        }
        return count;
    }

    /**
     * Removes ALL claims belonging to a team (called on core destruction or disband).
     */
    public void removeAllTeamClaims(int teamId) {
        int removed = 0;
        Iterator<Map.Entry<String, ClaimInfo>> it = claimsCache.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, ClaimInfo> entry = it.next();
            ClaimInfo info = entry.getValue();
            if (info.isTeamClaim() && info.getTeamId() != null && info.getTeamId() == teamId) {
                it.remove();
                removed++;
            }
        }

        final int finalRemoved = removed;
        dbManager.executeAsync(() -> {
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "DELETE FROM claims WHERE team_id = ?")) {
                ps.setInt(1, teamId);
                int dbRemoved = ps.executeUpdate();
                DebugManager.log(DebugFlag.CLAIM_PROTECTION,
                        "Removed " + dbRemoved + " claims for team " + teamId +
                                " (cache had " + finalRemoved + ")");
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * Returns all claimed chunks for a team.
     */
    public List<ClaimInfo> getTeamClaimChunks(int teamId) {
        List<ClaimInfo> result = new ArrayList<>();
        for (ClaimInfo info : claimsCache.values()) {
            if (info.isTeamClaim() && info.getTeamId() != null && info.getTeamId() == teamId) {
                result.add(info);
            }
        }
        return result;
    }

    public int purgeLegacyClaims() {
        int count = 0;
        List<String> keysToRemove = new ArrayList<>();
        for (Map.Entry<String, ClaimInfo> entry : claimsCache.entrySet()) {
            ClaimInfo info = entry.getValue();
            if (info.getTeamId() == null || info.getTeamId() <= 0) {
                keysToRemove.add(entry.getKey());
                count++;
            }
        }
        for (String key : keysToRemove) {
            claimsCache.remove(key);
        }
        final int finalCount = count;
        dbManager.executeAsync(() -> {
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "DELETE FROM claims WHERE team_id IS NULL OR team_id <= 0")) {
                ps.executeUpdate();
                DebugManager.log(DebugFlag.CLAIM_PROTECTION,
                        "Purged " + finalCount + " legacy non-team claims.");
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        return count;
    }
}