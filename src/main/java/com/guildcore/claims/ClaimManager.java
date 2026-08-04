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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ClaimManager {
    private final DatabaseManager dbManager;
    // Key format: "world:chunkX:chunkZ" -> ClaimInfo
    private final Map<String, ClaimInfo> claimsCache = new ConcurrentHashMap<>();

    private com.guildcore.teams.TeamManager teamManager;
    private com.guildcore.teams.TeamPermissionManager permissionManager;
    private com.guildcore.core.GuildCoreManager guildCoreManager;

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

    public void loadClaims() {
        dbManager.executeAsync(() -> {
            try (Connection conn = dbManager.getConnection()) {
                try (PreparedStatement ps = conn.prepareStatement("SELECT world, chunk_x, chunk_z, owner_uuid, team_id, flags FROM claims");
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
                    DebugManager.log(DebugFlag.CLAIM_PROTECTION, "Loaded " + claimsCache.size() + " full-chunk claims from database.");
                }


            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public String makeKey(String world, int cx, int cz) {
        return world + ":" + cx + ":" + cz;
    }

    private String makeTrustKey(String world, int cx, int cz, String uuidStr) {
        return world + ":" + cx + ":" + cz + ":" + uuidStr;
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

    public boolean createPersonalClaim(Player player, Chunk chunk) {
        if (isClaimed(chunk)) return false;

        String worldName = chunk.getWorld().getName();
        int cx = chunk.getX();
        int cz = chunk.getZ();
        UUID owner = player.getUniqueId();

        ClaimInfo claim = new ClaimInfo(worldName, cx, cz, owner, null, "");
        claimsCache.put(makeKey(worldName, cx, cz), claim);

        dbManager.executeAsync(() -> {
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement("INSERT INTO claims (world, chunk_x, chunk_z, owner_uuid) VALUES (?, ?, ?, ?)")) {
                ps.setString(1, worldName);
                ps.setInt(2, cx);
                ps.setInt(3, cz);
                ps.setString(4, owner.toString());
                ps.executeUpdate();
                DebugManager.log(DebugFlag.CLAIM_PROTECTION, "Created personal claim at " + cx + "," + cz + " for " + player.getName());
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        return true;
    }

    public boolean createTeamClaim(UUID ownerUuid, int teamId, Chunk chunk) {
        if (isClaimed(chunk)) return false;

        String worldName = chunk.getWorld().getName();
        int cx = chunk.getX();
        int cz = chunk.getZ();

        ClaimInfo claim = new ClaimInfo(worldName, cx, cz, ownerUuid, teamId, "");
        claimsCache.put(makeKey(worldName, cx, cz), claim);

        dbManager.executeAsync(() -> {
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement("INSERT INTO claims (world, chunk_x, chunk_z, owner_uuid, team_id) VALUES (?, ?, ?, ?, ?)")) {
                ps.setString(1, worldName);
                ps.setInt(2, cx);
                ps.setInt(3, cz);
                ps.setString(4, ownerUuid != null ? ownerUuid.toString() : null);
                ps.setInt(5, teamId);
                ps.executeUpdate();
                DebugManager.log(DebugFlag.CLAIM_PROTECTION, "Created team claim at " + cx + "," + cz + " for team " + teamId);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        return true;
    }

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
                 PreparedStatement ps = conn.prepareStatement("DELETE FROM claims WHERE world = ? AND chunk_x = ? AND chunk_z = ?")) {
                ps.setString(1, worldName);
                ps.setInt(2, cx);
                ps.setInt(3, cz);
                ps.executeUpdate();
                DebugManager.log(DebugFlag.CLAIM_PROTECTION, "Unclaimed chunk at " + cx + "," + cz);
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

        if (claim.getOwnerUuid() != null && claim.getOwnerUuid().equals(player.getUniqueId())) {
            return true;
        }

        return false;
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

    public void removeAllTeamClaims(int teamId) {
        claimsCache.values().removeIf(info -> info.isTeamClaim() && info.getTeamId() != null && info.getTeamId() == teamId);
        dbManager.executeAsync(() -> {
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement("DELETE FROM claims WHERE team_id = ?")) {
                ps.setInt(1, teamId);
                ps.executeUpdate();
                DebugManager.log(DebugFlag.CLAIM_PROTECTION, "Removed all claims for disbanded team " + teamId);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public int purgeLegacyClaims() {
        int count = 0;
        java.util.List<String> keysToRemove = new java.util.ArrayList<>();
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
                 PreparedStatement ps = conn.prepareStatement("DELETE FROM claims WHERE team_id IS NULL OR team_id <= 0")) {
                ps.executeUpdate();
                DebugManager.log(DebugFlag.CLAIM_PROTECTION, "Purged " + finalCount + " legacy non-team claims.");
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        return count;
    }

    public boolean isAdjacentToTeamClaim(int teamId, String world, int cx, int cz) {
        int[][] offsets = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] off : offsets) {
            String key = makeKey(world, cx + off[0], cz + off[1]);
            ClaimInfo neighbor = claimsCache.get(key);
            if (neighbor != null && neighbor.isTeamClaim() && neighbor.getTeamId() != null && neighbor.getTeamId() == teamId) {
                return true;
            }
        }
        return false;
    }

    public java.util.List<ClaimInfo> getTeamClaimChunks(int teamId) {
        java.util.List<ClaimInfo> result = new java.util.ArrayList<>();
        for (ClaimInfo info : claimsCache.values()) {
            if (info.isTeamClaim() && info.getTeamId() != null && info.getTeamId() == teamId) {
                result.add(info);
            }
        }
        return result;
    }
}
