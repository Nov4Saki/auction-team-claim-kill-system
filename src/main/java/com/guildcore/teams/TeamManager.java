package com.guildcore.teams;

import com.guildcore.database.DatabaseManager;
import com.guildcore.debug.DebugFlag;
import com.guildcore.debug.DebugManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TeamManager {
    private final DatabaseManager dbManager;
    private final Map<Integer, Team> teamsById = new ConcurrentHashMap<>();
    private final Map<String, Integer> teamIdByName = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> playerTeamMap = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerRoleMap = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> pendingInvites = new ConcurrentHashMap<>();

    public TeamManager(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    public void loadTeams() {
        dbManager.executeAsync(() -> {
            try (Connection conn = dbManager.getConnection()) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT id, name, leader_uuid, level, exp, bank_balance, max_members, max_claims, " +
                        "home_world, home_x, home_y, home_z, home_yaw, home_pitch, nexus_world, nexus_x, nexus_y, nexus_z FROM teams");
                     ResultSet rs = ps.executeQuery()) {

                    teamsById.clear();
                    teamIdByName.clear();

                    while (rs.next()) {
                        int id = rs.getInt("id");
                        String name = rs.getString("name");
                        UUID leader = UUID.fromString(rs.getString("leader_uuid"));
                        int level = rs.getInt("level");
                        long exp = rs.getLong("exp");
                        long bank = rs.getLong("bank_balance");
                        int maxMembers = rs.getInt("max_members");
                        int maxClaims = rs.getInt("max_claims");

                        Team team = new Team(id, name, leader, level, exp, bank, maxMembers, maxClaims);

                        String hWorld = rs.getString("home_world");
                        if (hWorld != null && Bukkit.getWorld(hWorld) != null) {
                            World w = Bukkit.getWorld(hWorld);
                            double x = rs.getDouble("home_x");
                            double y = rs.getDouble("home_y");
                            double z = rs.getDouble("home_z");
                            float yaw = rs.getFloat("home_yaw");
                            float pitch = rs.getFloat("home_pitch");
                            team.setHomeLocation(new Location(w, x, y, z, yaw, pitch));
                        }

                        String nWorld = rs.getString("nexus_world");
                        if (nWorld != null && Bukkit.getWorld(nWorld) != null) {
                            World w = Bukkit.getWorld(nWorld);
                            int nx = rs.getInt("nexus_x");
                            int ny = rs.getInt("nexus_y");
                            int nz = rs.getInt("nexus_z");
                            team.setNexusLocation(new Location(w, nx, ny, nz));
                        }

                        teamsById.put(id, team);
                        teamIdByName.put(name.toLowerCase(), id);
                    }
                }

                try (PreparedStatement ps = conn.prepareStatement("SELECT team_id, player_uuid, role FROM team_members");
                     ResultSet rs = ps.executeQuery()) {
                    playerTeamMap.clear();
                    playerRoleMap.clear();
                    while (rs.next()) {
                        int teamId = rs.getInt("team_id");
                        UUID playerUuid = UUID.fromString(rs.getString("player_uuid"));
                        String role = rs.getString("role");

                        playerTeamMap.put(playerUuid, teamId);
                        playerRoleMap.put(playerUuid, role);
                    }
                }
                DebugManager.log(DebugFlag.TEAM_UPGRADES, "Loaded " + teamsById.size() + " teams into memory.");
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public Team getTeam(int teamId) {
        return teamsById.get(teamId);
    }

    public Team getTeamByName(String name) {
        Integer id = teamIdByName.get(name.toLowerCase());
        return id != null ? teamsById.get(id) : null;
    }

    public Team getPlayerTeam(UUID playerUuid) {
        Integer id = playerTeamMap.get(playerUuid);
        return id != null ? teamsById.get(id) : null;
    }

    public String getPlayerRole(UUID playerUuid) {
        return playerRoleMap.getOrDefault(playerUuid, "RECRUIT");
    }

    public boolean createTeam(Player leader, String name, int defaultMaxMembers) {
        if (playerTeamMap.containsKey(leader.getUniqueId())) return false;
        if (teamIdByName.containsKey(name.toLowerCase())) return false;

        UUID leaderUuid = leader.getUniqueId();
        dbManager.executeAsync(() -> {
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT INTO teams (name, leader_uuid, max_members) VALUES (?, ?, ?)",
                         Statement.RETURN_GENERATED_KEYS)) {

                ps.setString(1, name);
                ps.setString(2, leaderUuid.toString());
                ps.setInt(3, defaultMaxMembers);
                ps.executeUpdate();

                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        int id = rs.getInt(1);
                        Team team = new Team(id, name, leaderUuid, 1, 0L, 0L, defaultMaxMembers, 5);
                        teamsById.put(id, team);
                        teamIdByName.put(name.toLowerCase(), id);

                        playerTeamMap.put(leaderUuid, id);
                        playerRoleMap.put(leaderUuid, "LEADER");

                        try (PreparedStatement memberPs = conn.prepareStatement(
                                "INSERT INTO team_members (team_id, player_uuid, role) VALUES (?, ?, 'LEADER')")) {
                            memberPs.setInt(1, id);
                            memberPs.setString(2, leaderUuid.toString());
                            memberPs.executeUpdate();
                        }

                        DebugManager.log(DebugFlag.TEAM_UPGRADES, "Created team " + name + " (ID: " + id + ") with leader " + leader.getName());
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        return true;
    }

    public boolean invitePlayer(Player inviter, Player target) {
        Team team = getPlayerTeam(inviter.getUniqueId());
        if (team == null) return false;
        pendingInvites.put(target.getUniqueId(), team.getId());
        return true;
    }

    public boolean joinTeam(Player player) {
        Integer teamId = pendingInvites.remove(player.getUniqueId());
        if (teamId == null) return false;
        Team team = getTeam(teamId);
        if (team == null) return false;

        int currentMembers = (int) playerTeamMap.values().stream().filter(id -> id == teamId).count();
        if (currentMembers >= team.getMaxMembers()) {
            return false;
        }

        UUID playerUuid = player.getUniqueId();
        playerTeamMap.put(playerUuid, teamId);
        playerRoleMap.put(playerUuid, "RECRUIT");

        dbManager.executeAsync(() -> {
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement("INSERT INTO team_members (team_id, player_uuid, role) VALUES (?, ?, 'RECRUIT')")) {
                ps.setInt(1, teamId);
                ps.setString(2, playerUuid.toString());
                ps.executeUpdate();
                DebugManager.log(DebugFlag.TEAM_UPGRADES, "Player " + player.getName() + " joined team " + team.getName());
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        return true;
    }

    public int getTeamMembersCount(int teamId) {
        return (int) playerTeamMap.values().stream().filter(id -> id == teamId).count();
    }
}
