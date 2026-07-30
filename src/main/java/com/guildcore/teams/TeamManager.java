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

    private final Map<UUID, String> pendingInvitesWithInviter = new ConcurrentHashMap<>();

    public boolean invitePlayer(Player inviter, Player target) {
        Team team = getPlayerTeam(inviter.getUniqueId());
        if (team == null) return false;
        pendingInvites.put(target.getUniqueId(), team.getId());
        pendingInvitesWithInviter.put(target.getUniqueId(), inviter.getName());
        return true;
    }

    public boolean joinTeam(Player player) {
        Integer teamId = pendingInvites.remove(player.getUniqueId());
        String inviterName = pendingInvitesWithInviter.remove(player.getUniqueId());
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

        String displayInviter = inviterName != null ? inviterName : "a Guild Member";
        for (Player member : org.bukkit.Bukkit.getOnlinePlayers()) {
            if (playerTeamMap.containsKey(member.getUniqueId()) && playerTeamMap.get(member.getUniqueId()) == teamId) {
                member.sendMessage(com.guildcore.util.TextUtil.format("<gradient:#00c6ff:#0072ff><b>🏰 [Guild] Please welcome <yellow>" + player.getName() + "</yellow> to the guild! (Invited by <gold>" + displayInviter + "</gold>)</b></gradient>"));
            }
        }

        return true;
    }

    public int getTeamMembersCount(int teamId) {
        return (int) playerTeamMap.values().stream().filter(id -> id == teamId).count();
    }

    public boolean kickPlayer(Player kicker, String targetName) {
        Team team = getPlayerTeam(kicker.getUniqueId());
        if (team == null) return false;

        UUID targetUuid = null;
        Player targetPlayer = Bukkit.getPlayer(targetName);
        if (targetPlayer != null) {
            targetUuid = targetPlayer.getUniqueId();
        } else {
            for (Map.Entry<UUID, Integer> entry : playerTeamMap.entrySet()) {
                if (entry.getValue() == team.getId()) {
                    org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(entry.getKey());
                    if (op.getName() != null && op.getName().equalsIgnoreCase(targetName)) {
                        targetUuid = entry.getKey();
                        break;
                    }
                }
            }
        }

        if (targetUuid == null) return false;
        if (!playerTeamMap.containsKey(targetUuid) || playerTeamMap.get(targetUuid) != team.getId()) return false;

        String kickerRole = getPlayerRole(kicker.getUniqueId());
        String targetRole = getPlayerRole(targetUuid);

        if (!kickerRole.equalsIgnoreCase("LEADER")) {
            if (targetRole.equalsIgnoreCase("LEADER") || targetRole.equalsIgnoreCase("OFFICER")) {
                kicker.sendMessage(com.guildcore.util.TextUtil.format("<red>You cannot kick higher or equal ranked members!</red>"));
                return false;
            }
        }

        final UUID finalTargetUuid = targetUuid;
        final String finalTargetName = targetPlayer != null ? targetPlayer.getName() : targetName;

        playerTeamMap.remove(targetUuid);
        playerRoleMap.remove(targetUuid);

        dbManager.executeAsync(() -> {
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement("DELETE FROM team_members WHERE team_id = ? AND player_uuid = ?")) {
                ps.setInt(1, team.getId());
                ps.setString(2, finalTargetUuid.toString());
                ps.executeUpdate();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        if (targetPlayer != null && targetPlayer.isOnline()) {
            targetPlayer.sendMessage(com.guildcore.util.TextUtil.format("<gradient:#FF416C:#FF4B2B><b>✖ You were kicked from team " + team.getName() + " by " + kicker.getName() + "!</b></gradient>"));
        }

        broadcastToTeam(team.getId(), com.guildcore.util.TextUtil.format("<gradient:#FF416C:#FF4B2B><b>🏰 [Guild] Member <yellow>" + finalTargetName + "</yellow> was kicked from the guild by <gold>" + kicker.getName() + "</gold>!</b></gradient>"));
        return true;
    }

    public boolean promotePlayer(Player actor, String targetName) {
        Team team = getPlayerTeam(actor.getUniqueId());
        if (team == null) return false;

        Player target = Bukkit.getPlayer(targetName);
        if (target == null) return false;
        UUID targetUuid = target.getUniqueId();
        if (!playerTeamMap.containsKey(targetUuid) || playerTeamMap.get(targetUuid) != team.getId()) return false;

        String currentRole = getPlayerRole(targetUuid);
        String newRole = currentRole;
        if (currentRole.equalsIgnoreCase("RECRUIT")) newRole = "MEMBER";
        else if (currentRole.equalsIgnoreCase("MEMBER")) newRole = "OFFICER";

        if (newRole.equals(currentRole)) {
            actor.sendMessage(com.guildcore.util.TextUtil.format("<red>Player is already at maximum rank!</red>"));
            return false;
        }

        final String finalRole = newRole;
        playerRoleMap.put(targetUuid, finalRole);

        dbManager.executeAsync(() -> {
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement("UPDATE team_members SET role = ? WHERE team_id = ? AND player_uuid = ?")) {
                ps.setString(1, finalRole);
                ps.setInt(2, team.getId());
                ps.setString(3, targetUuid.toString());
                ps.executeUpdate();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        broadcastToTeam(team.getId(), com.guildcore.util.TextUtil.format("<gradient:#00FF87:#60EFFF><b>🏰 [Guild] Member <yellow>" + target.getName() + "</yellow> was promoted to <gold>" + finalRole + "</gold> by <gold>" + actor.getName() + "</gold>!</b></gradient>"));
        return true;
    }

    public boolean demotePlayer(Player actor, String targetName) {
        Team team = getPlayerTeam(actor.getUniqueId());
        if (team == null) return false;

        Player target = Bukkit.getPlayer(targetName);
        if (target == null) return false;
        UUID targetUuid = target.getUniqueId();
        if (!playerTeamMap.containsKey(targetUuid) || playerTeamMap.get(targetUuid) != team.getId()) return false;

        String currentRole = getPlayerRole(targetUuid);
        String newRole = currentRole;
        if (currentRole.equalsIgnoreCase("OFFICER")) newRole = "MEMBER";
        else if (currentRole.equalsIgnoreCase("MEMBER")) newRole = "RECRUIT";

        if (newRole.equals(currentRole)) {
            actor.sendMessage(com.guildcore.util.TextUtil.format("<red>Player is already at lowest rank!</red>"));
            return false;
        }

        final String finalRole = newRole;
        playerRoleMap.put(targetUuid, finalRole);

        dbManager.executeAsync(() -> {
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement("UPDATE team_members SET role = ? WHERE team_id = ? AND player_uuid = ?")) {
                ps.setString(1, finalRole);
                ps.setInt(2, team.getId());
                ps.setString(3, targetUuid.toString());
                ps.executeUpdate();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        broadcastToTeam(team.getId(), com.guildcore.util.TextUtil.format("<gradient:#FF416C:#FF4B2B><b>🏰 [Guild] Member <yellow>" + target.getName() + "</yellow> was demoted to <gold>" + finalRole + "</gold> by <gold>" + actor.getName() + "</gold>.</b></gradient>"));
        return true;
    }

    public boolean leaveTeam(Player player) {
        Team team = getPlayerTeam(player.getUniqueId());
        if (team == null) return false;

        if (team.getLeaderUuid().equals(player.getUniqueId())) {
            player.sendMessage(com.guildcore.util.TextUtil.format("<red>Guild Leaders cannot leave! Transfer leadership or use /team disband.</red>"));
            return false;
        }

        UUID uuid = player.getUniqueId();
        playerTeamMap.remove(uuid);
        playerRoleMap.remove(uuid);

        dbManager.executeAsync(() -> {
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement("DELETE FROM team_members WHERE team_id = ? AND player_uuid = ?")) {
                ps.setInt(1, team.getId());
                ps.setString(2, uuid.toString());
                ps.executeUpdate();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        player.sendMessage(com.guildcore.util.TextUtil.format("<yellow>You left team " + team.getName() + ".</yellow>"));
        broadcastToTeam(team.getId(), com.guildcore.util.TextUtil.format("<gradient:#FF416C:#FF4B2B><b>🏰 [Guild] Member <yellow>" + player.getName() + "</yellow> has left the guild.</b></gradient>"));
        return true;
    }

    public boolean disbandTeam(Player leader) {
        Team team = getPlayerTeam(leader.getUniqueId());
        if (team == null || !team.getLeaderUuid().equals(leader.getUniqueId())) {
            leader.sendMessage(com.guildcore.util.TextUtil.format("<red>Only the Guild Leader can disband the guild!</red>"));
            return false;
        }

        int teamId = team.getId();
        teamsById.remove(teamId);
        teamIdByName.remove(team.getName().toLowerCase());

        broadcastToTeam(teamId, com.guildcore.util.TextUtil.format("<gradient:#FF416C:#FF4B2B><b>💥 [Guild] Guild " + team.getName() + " was disbanded by Guild Leader " + leader.getName() + "!</b></gradient>"));

        for (Map.Entry<UUID, Integer> entry : new ConcurrentHashMap<>(playerTeamMap).entrySet()) {
            if (entry.getValue() == teamId) {
                playerTeamMap.remove(entry.getKey());
                playerRoleMap.remove(entry.getKey());
            }
        }

        dbManager.executeAsync(() -> {
            try (Connection conn = dbManager.getConnection()) {
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM team_members WHERE team_id = ?")) { ps.setInt(1, teamId); ps.executeUpdate(); }
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM teams WHERE id = ?")) { ps.setInt(1, teamId); ps.executeUpdate(); }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        return true;
    }

    public void broadcastToTeam(int teamId, net.kyori.adventure.text.Component message) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Integer pTeamId = playerTeamMap.get(p.getUniqueId());
            if (pTeamId != null && pTeamId == teamId) {
                p.sendMessage(message);
            }
        }
    }
}
