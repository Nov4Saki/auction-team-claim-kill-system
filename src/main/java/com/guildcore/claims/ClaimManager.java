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
    // Key format: "world:chunkX:chunkZ:uuid" -> trustLevel ("ACCESS", "CONTAINER", "BUILD", "MANAGER")
    private final Map<String, String> trustCache = new ConcurrentHashMap<>();

    public ClaimManager(DatabaseManager dbManager) {
        this.dbManager = dbManager;
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

                try (PreparedStatement ps = conn.prepareStatement("SELECT world, chunk_x, chunk_z, player_uuid, trust_level FROM claim_trust");
                     ResultSet rs = ps.executeQuery()) {
                    trustCache.clear();
                    while (rs.next()) {
                        String key = makeTrustKey(rs.getString("world"), rs.getInt("chunk_x"), rs.getInt("chunk_z"), rs.getString("player_uuid"));
                        trustCache.put(key, rs.getString("trust_level"));
                    }
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

    public boolean createTeamClaim(int teamId, Chunk chunk) {
        if (isClaimed(chunk)) return false;

        String worldName = chunk.getWorld().getName();
        int cx = chunk.getX();
        int cz = chunk.getZ();

        ClaimInfo claim = new ClaimInfo(worldName, cx, cz, null, teamId, "");
        claimsCache.put(makeKey(worldName, cx, cz), claim);

        dbManager.executeAsync(() -> {
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement("INSERT INTO claims (world, chunk_x, chunk_z, team_id) VALUES (?, ?, ?, ?)")) {
                ps.setString(1, worldName);
                ps.setInt(2, cx);
                ps.setInt(3, cz);
                ps.setInt(4, teamId);
                ps.executeUpdate();
                DebugManager.log(DebugFlag.CLAIM_PROTECTION, "Created team claim at " + cx + "," + cz + " for team " + teamId);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        return true;
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

    public void setTrust(Chunk chunk, UUID target, String trustLevel) {
        ClaimInfo claim = getClaimAt(chunk);
        if (claim == null) return;

        String key = makeTrustKey(chunk.getWorld().getName(), chunk.getX(), chunk.getZ(), target.toString());
        trustCache.put(key, trustLevel);

        dbManager.executeAsync(() -> {
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement("INSERT OR REPLACE INTO claim_trust (world, chunk_x, chunk_z, player_uuid, trust_level) VALUES (?, ?, ?, ?, ?)")) {
                ps.setString(1, chunk.getWorld().getName());
                ps.setInt(2, chunk.getX());
                ps.setInt(3, chunk.getZ());
                ps.setString(4, target.toString());
                ps.setString(5, trustLevel);
                ps.executeUpdate();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public String getTrustLevel(Chunk chunk, UUID playerUuid) {
        if (chunk == null || playerUuid == null) return null;
        return trustCache.get(makeTrustKey(chunk.getWorld().getName(), chunk.getX(), chunk.getZ(), playerUuid.toString()));
    }

    public boolean canBuild(Player player, Chunk chunk) {
        if (player.hasPermission("guildcore.admin")) return true;
        ClaimInfo claim = getClaimAt(chunk);
        if (claim == null) return true;

        if (!claim.isTeamClaim() && player.getUniqueId().equals(claim.getOwnerUuid())) return true;

        String trust = getTrustLevel(chunk, player.getUniqueId());
        return "BUILD".equalsIgnoreCase(trust) || "MANAGER".equalsIgnoreCase(trust);
    }
}
