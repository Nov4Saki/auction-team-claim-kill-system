package com.guildcore.teams;

import com.guildcore.database.DatabaseManager;
import com.guildcore.debug.DebugFlag;
import com.guildcore.debug.DebugManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.OfflinePlayer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TeamManager {
    private final DatabaseManager dbManager;
    private com.guildcore.claims.ClaimManager claimManager;
    private com.guildcore.claims.ClaimChestManager claimChestManager;
    private com.guildcore.config.SettingsManager settingsManager;
    private com.guildcore.core.GuildCoreManager guildCoreManager;
    private com.guildcore.scheduler.SchedulerWrapper scheduler;
    private com.guildcore.economy.EconomyManager economyManager;
    private com.guildcore.shield.OfflineShieldManager offlineShieldManager;

    public void setOfflineShieldManager(com.guildcore.shield.OfflineShieldManager offlineShieldManager) {
        this.offlineShieldManager = offlineShieldManager;
    }
    private final Map<Integer, Team> teamsById = new ConcurrentHashMap<>();
    private final Map<String, Integer> teamIdByName = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> playerTeamMap = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerRoleMap = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> pendingInvites = new ConcurrentHashMap<>();
    private final Map<UUID, String> pendingInvitesWithInviter = new ConcurrentHashMap<>();
    private final Map<UUID, Long> pendingInviteTimestamps = new ConcurrentHashMap<>();

    public TeamManager(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    public void setScheduler(com.guildcore.scheduler.SchedulerWrapper scheduler) {
        this.scheduler = scheduler;
    }

    public void setEconomyManager(com.guildcore.economy.EconomyManager economyManager) {
        this.economyManager = economyManager;
    }

    public void setGuildCoreManager(com.guildcore.core.GuildCoreManager guildCoreManager) {
        this.guildCoreManager = guildCoreManager;
    }

    public void setClaimManager(com.guildcore.claims.ClaimManager claimManager) {
        this.claimManager = claimManager;
    }

    public void setSettingsManager(com.guildcore.config.SettingsManager settingsManager) {
        this.settingsManager = settingsManager;
    }

    public void setClaimChestManager(com.guildcore.claims.ClaimChestManager claimChestManager) {
        this.claimChestManager = claimChestManager;
    }

    public void saveTeamBankBalance(Team team) {
        if (team == null) return;
        dbManager.executeAsync(() -> {
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement("UPDATE teams SET bank_balance = ? WHERE id = ?")) {
                ps.setLong(1, team.getBankBalance());
                ps.setInt(2, team.getId());
                ps.executeUpdate();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public void loadTeams() {
        dbManager.executeAsync(() -> {
            try (Connection conn = dbManager.getConnection()) {
                try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM teams");
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

                        int vaultSlots = 9;
                        try { vaultSlots = rs.getInt("vault_slots"); } catch (Exception ignored) {}
                        if (vaultSlots <= 0) vaultSlots = 9;

                        int vaultPages = 1;
                        try { vaultPages = rs.getInt("vault_pages"); } catch (Exception ignored) {}
                        if (vaultPages <= 0) vaultPages = 1;

                        Team team = new Team(id, name, leader, level, exp, bank, maxMembers, maxClaims, vaultSlots, vaultPages);

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

    public boolean areSameTeam(UUID p1, UUID p2) {
        if (p1 == null || p2 == null) return false;
        Integer t1 = playerTeamMap.get(p1);
        Integer t2 = playerTeamMap.get(p2);
        return t1 != null && t1.equals(t2);
    }

    public List<UUID> getOnlineMembers(int teamId) {
        List<UUID> online = new ArrayList<>();
        for (Map.Entry<UUID, Integer> entry : playerTeamMap.entrySet()) {
            if (entry.getValue() == teamId) {
                Player player = Bukkit.getPlayer(entry.getKey());
                if (player != null && player.isOnline()) {
                    online.add(entry.getKey());
                }
            }
        }
        return online;
    }

    public boolean hasPendingInvite(Player player) {
        if (player == null) return false;
        Long expiry = pendingInviteTimestamps.get(player.getUniqueId());
        Integer teamId = pendingInvites.get(player.getUniqueId());
        return teamId != null && expiry != null && System.currentTimeMillis() <= expiry;
    }

    public boolean createTeam(Player leader, String name, int defaultMaxMembers) {
        if (leader == null || name == null || name.trim().isEmpty()) return false;
        String cleanName = name.trim();
        UUID leaderUuid = leader.getUniqueId();

        if (playerTeamMap.containsKey(leaderUuid)) return false;
        if (teamIdByName.containsKey(cleanName.toLowerCase())) return false;

        // Check and deduct team creation cost
        if (settingsManager != null && economyManager != null) {
            long creationCost = settingsManager.getLong("teams.creation_cost", 1000);
            if (economyManager.getBalance(leader.getUniqueId()) < creationCost) {
                leader.sendMessage(com.guildcore.util.TextUtil.format(
                        "<red>✖ Insufficient funds! Team creation costs $" +
                                String.format("%,d", creationCost) + " Gold. Your balance: $" +
                                String.format("%,d", economyManager.getBalance(leader.getUniqueId())) + "</red>"));
                return false;
            }
            economyManager.withdraw(leader.getUniqueId(), creationCost, "team_creation");
            leader.sendMessage(com.guildcore.util.TextUtil.format(
                    "<green>✔ Charged $" + String.format("%,d", creationCost) + " Gold for team creation.</green>"));
        }

        try (Connection conn = dbManager.getConnection()) {
            try (PreparedStatement purgePs = conn.prepareStatement(
                    "DELETE FROM team_members WHERE player_uuid = ? AND team_id NOT IN (SELECT id FROM teams)")) {
                purgePs.setString(1, leaderUuid.toString());
                purgePs.executeUpdate();
            }

            try (PreparedStatement checkNamePs = conn.prepareStatement("SELECT 1 FROM teams WHERE LOWER(name) = LOWER(?)")) {
                checkNamePs.setString(1, cleanName);
                try (ResultSet rs = checkNamePs.executeQuery()) {
                    if (rs.next()) return false;
                }
            }

            try (PreparedStatement checkMemPs = conn.prepareStatement("SELECT team_id FROM team_members WHERE player_uuid = ?")) {
                checkMemPs.setString(1, leaderUuid.toString());
                try (ResultSet rs = checkMemPs.executeQuery()) {
                    if (rs.next()) {
                        int activeTeamId = rs.getInt("team_id");
                        if (teamsById.containsKey(activeTeamId)) {
                            playerTeamMap.put(leaderUuid, activeTeamId);
                            return false;
                        } else {
                            try (PreparedStatement delStale = conn.prepareStatement("DELETE FROM team_members WHERE player_uuid = ?")) {
                                delStale.setString(1, leaderUuid.toString());
                                delStale.executeUpdate();
                            }
                        }
                    }
                }
            }

            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO teams (name, leader_uuid, max_members) VALUES (?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {

                ps.setString(1, cleanName);
                ps.setString(2, leaderUuid.toString());
                ps.setInt(3, defaultMaxMembers);
                ps.executeUpdate();

                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        int id = rs.getInt(1);

                        try (PreparedStatement memberPs = conn.prepareStatement(
                                "INSERT INTO team_members (team_id, player_uuid, role) VALUES (?, ?, 'LEADER')")) {
                            memberPs.setInt(1, id);
                            memberPs.setString(2, leaderUuid.toString());
                            memberPs.executeUpdate();
                        }

                        conn.commit();
                        conn.setAutoCommit(true);

                        Team team = new Team(id, cleanName, leaderUuid, 1, 0L, 0L, defaultMaxMembers, 5, 9);
                        teamsById.put(id, team);
                        teamIdByName.put(cleanName.toLowerCase(), id);
                        playerTeamMap.put(leaderUuid, id);
                        playerRoleMap.put(leaderUuid, "LEADER");

                        DebugManager.log(DebugFlag.TEAM_UPGRADES, "Created team " + cleanName + " (ID: " + id + ") with leader " + leader.getName());
                        return true;
                    }
                }
            } catch (Exception ex) {
                conn.rollback();
                conn.setAutoCommit(true);
                DebugManager.log(DebugFlag.TEAM_UPGRADES, "Team creation rolled back for " + cleanName + ": " + ex.getMessage());
                return false;
            }
        } catch (Exception e) {
            DebugManager.log(DebugFlag.TEAM_UPGRADES, "Database error during team creation: " + e.getMessage());
        }
        return false;
    }

    public boolean invitePlayer(Player inviter, Player target) {
        Team team = getPlayerTeam(inviter.getUniqueId());
        if (team == null) return false;
        int currentMembers = (int) playerTeamMap.values().stream().filter(id -> id == team.getId()).count();
        if (currentMembers >= team.getMaxMembers()) {
            return false;
        }

        int expireSec = settingsManager != null ? settingsManager.getInt("requests.team-invite-expire-seconds", 120) : 120;
        pendingInvites.put(target.getUniqueId(), team.getId());
        pendingInvitesWithInviter.put(target.getUniqueId(), inviter.getName());
        pendingInviteTimestamps.put(target.getUniqueId(), System.currentTimeMillis() + (expireSec * 1000L));

        net.kyori.adventure.text.Component inviteMsg = net.kyori.adventure.text.Component.text("🏰 ")
                .color(net.kyori.adventure.text.format.NamedTextColor.GOLD)
                .append(net.kyori.adventure.text.Component.text(inviter.getName()).color(net.kyori.adventure.text.format.NamedTextColor.YELLOW))
                .append(net.kyori.adventure.text.Component.text(" invited you to join Guild ").color(net.kyori.adventure.text.format.NamedTextColor.GOLD))
                .append(net.kyori.adventure.text.Component.text(team.getName()).color(net.kyori.adventure.text.format.NamedTextColor.GREEN))
                .append(net.kyori.adventure.text.Component.text(" (Expires in " + expireSec + "s). ").color(net.kyori.adventure.text.format.NamedTextColor.GOLD))
                .append(net.kyori.adventure.text.Component.text("[ACCEPT]")
                        .color(net.kyori.adventure.text.format.NamedTextColor.GREEN)
                        .decorate(net.kyori.adventure.text.format.TextDecoration.BOLD)
                        .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/guild join"))
                        .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(net.kyori.adventure.text.Component.text("Click to join " + team.getName()).color(net.kyori.adventure.text.format.NamedTextColor.GREEN))))
                .append(net.kyori.adventure.text.Component.text("  "))
                .append(net.kyori.adventure.text.Component.text("[DENY]")
                        .color(net.kyori.adventure.text.format.NamedTextColor.RED)
                        .decorate(net.kyori.adventure.text.format.TextDecoration.BOLD)
                        .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/guild deny"))
                        .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(net.kyori.adventure.text.Component.text("Click to decline invite from " + team.getName()).color(net.kyori.adventure.text.format.NamedTextColor.RED))));

        target.sendMessage(inviteMsg);
        return true;
    }

    public boolean denyInvite(Player player) {
        Integer teamId = pendingInvites.remove(player.getUniqueId());
        String inviterName = pendingInvitesWithInviter.remove(player.getUniqueId());
        pendingInviteTimestamps.remove(player.getUniqueId());

        if (teamId == null) return false;

        if (inviterName != null) {
            Player inviter = Bukkit.getPlayer(inviterName);
            if (inviter != null && inviter.isOnline()) {
                inviter.sendMessage(com.guildcore.util.TextUtil.format("<yellow>" + player.getName() + " declined your Guild invitation.</yellow>"));
            }
        }
        return true;
    }

    public boolean joinTeam(Player player) {
        Long expiry = pendingInviteTimestamps.remove(player.getUniqueId());
        Integer teamId = pendingInvites.remove(player.getUniqueId());
        String inviterName = pendingInvitesWithInviter.remove(player.getUniqueId());

        if (teamId == null || expiry == null) return false;

        if (System.currentTimeMillis() > expiry) {
            player.sendMessage(com.guildcore.util.TextUtil.format("<red>✖ Your team invite has expired!</red>"));
            return false;
        }

        Team team = getTeam(teamId);
        if (team == null) return false;

        int currentMembers = (int) playerTeamMap.values().stream().filter(id -> id == teamId).count();
        if (currentMembers >= team.getMaxMembers()) {
            return false;
        }

        UUID playerUuid = player.getUniqueId();
        if (playerTeamMap.containsKey(playerUuid)) {
            player.sendMessage(com.guildcore.util.TextUtil.format("<red>✖ You are already in a team! Leave your current team first.</red>"));
            return false;
        }

        playerTeamMap.put(playerUuid, teamId);
        playerRoleMap.put(playerUuid, "RECRUIT");

        dbManager.executeAsync(() -> {
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement("INSERT OR REPLACE INTO team_members (team_id, player_uuid, role) VALUES (?, ?, 'RECRUIT')")) {
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

        if (targetUuid.equals(team.getLeaderUuid())) {
            kicker.sendMessage(com.guildcore.util.TextUtil.format("<red>✖ You cannot kick the Guild Owner / Leader!</red>"));
            return false;
        }

        String kickerRole = getPlayerRole(kicker.getUniqueId());
        String targetRole = getPlayerRole(targetUuid);

        if (!kickerRole.equalsIgnoreCase("LEADER")) {
            if (targetRole.equalsIgnoreCase("LEADER") || targetRole.equalsIgnoreCase("OFFICER")) {
                kicker.sendMessage(com.guildcore.util.TextUtil.format("<red>✖ You cannot kick higher or equal ranked members!</red>"));
                return false;
            }
        }

        final UUID finalTargetUuid = targetUuid;
        final String finalTargetName = targetPlayer != null ? targetPlayer.getName() : targetName;

        playerTeamMap.remove(targetUuid);
        playerRoleMap.remove(targetUuid);

        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM team_members WHERE team_id = ? AND player_uuid = ?")) {
            ps.setInt(1, team.getId());
            ps.setString(2, finalTargetUuid.toString());
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (targetPlayer != null && targetPlayer.isOnline()) {
            targetPlayer.closeInventory();
            targetPlayer.sendMessage(com.guildcore.util.TextUtil.format("<gradient:#FF416C:#FF4B2B><b>✖ You were kicked from team " + team.getName() + " by " + kicker.getName() + "!</b></gradient>"));
        }

        broadcastToTeam(team.getId(), com.guildcore.util.TextUtil.format("<gradient:#FF416C:#FF4B2B><b>🏰 [Guild] Member <yellow>" + finalTargetName + "</yellow> was kicked from the guild by <gold>" + kicker.getName() + "</gold>!</b></gradient>"));
        return true;
    }

    public UUID findHighestRankingSuccessor(int teamId, UUID currentLeaderUuid) {
        List<UUID> members = getTeamMembers(teamId);
        members.remove(currentLeaderUuid);
        if (members.isEmpty()) return null;

        UUID bestOfficer = null;
        UUID bestMember = null;
        UUID bestRecruit = null;

        for (UUID memberUuid : members) {
            String role = getPlayerRole(memberUuid);
            if (role.equalsIgnoreCase("OFFICER") && bestOfficer == null) {
                bestOfficer = memberUuid;
            } else if (role.equalsIgnoreCase("MEMBER") && bestMember == null) {
                bestMember = memberUuid;
            } else if (bestRecruit == null) {
                bestRecruit = memberUuid;
            }
        }

        if (bestOfficer != null) return bestOfficer;
        if (bestMember != null) return bestMember;
        return bestRecruit;
    }

    public boolean leaveTeam(Player player) {
        Team team = getPlayerTeam(player.getUniqueId());
        if (team == null) return false;

        UUID uuid = player.getUniqueId();
        boolean autoTransfer = settingsManager == null || settingsManager.getBoolean("teams.auto_transfer_leader_on_leave", true);

        if (team.getLeaderUuid().equals(uuid)) {
            UUID successorUuid = findHighestRankingSuccessor(team.getId(), uuid);
            if (successorUuid != null && autoTransfer) {
                org.bukkit.OfflinePlayer successorOp = Bukkit.getOfflinePlayer(successorUuid);
                String successorName = successorOp.getName() != null ? successorOp.getName() : "Citizen";

                team.setLeaderUuid(successorUuid);
                playerRoleMap.put(successorUuid, "LEADER");

                dbManager.executeAsync(() -> {
                    try (Connection conn = dbManager.getConnection()) {
                        try (PreparedStatement ps = conn.prepareStatement("UPDATE teams SET leader_uuid = ? WHERE id = ?")) {
                            ps.setString(1, successorUuid.toString());
                            ps.setInt(2, team.getId());
                            ps.executeUpdate();
                        }
                        try (PreparedStatement ps = conn.prepareStatement("UPDATE team_members SET role = 'LEADER' WHERE team_id = ? AND player_uuid = ?")) {
                            ps.setInt(1, team.getId());
                            ps.setString(2, successorUuid.toString());
                            ps.executeUpdate();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });

                broadcastToTeam(team.getId(), com.guildcore.util.TextUtil.format("<gradient:#FFD700:#FFA500><b>👑 [Guild] Leader " + player.getName() + " left the guild! Leadership has been passed to " + successorName + "!</b></gradient>"));
            } else if (successorUuid == null) {
                disbandTeam(player);
                return true;
            } else {
                player.sendMessage(com.guildcore.util.TextUtil.format("<red>✖ Guild Leaders cannot leave without transferring leadership first or disbanding! (/team disband)</red>"));
                return false;
            }
        }

        playerTeamMap.remove(uuid);
        playerRoleMap.remove(uuid);

        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM team_members WHERE team_id = ? AND player_uuid = ?")) {
            ps.setInt(1, team.getId());
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }

        player.closeInventory();
        player.sendMessage(com.guildcore.util.TextUtil.format("<yellow>You left team " + team.getName() + ".</yellow>"));
        broadcastToTeam(team.getId(), com.guildcore.util.TextUtil.format("<gradient:#FF416C:#FF4B2B><b>🏰 [Guild] Member <yellow>" + player.getName() + "</yellow> has left the guild.</b></gradient>"));
        return true;
    }

    public UUID findTeamMemberUuid(int teamId, String targetName) {
        if (targetName == null) return null;
        Player online = Bukkit.getPlayer(targetName);
        if (online != null) {
            UUID u = online.getUniqueId();
            if (playerTeamMap.containsKey(u) && playerTeamMap.get(u) == teamId) {
                return u;
            }
        }
        for (Map.Entry<UUID, Integer> entry : playerTeamMap.entrySet()) {
            if (entry.getValue() == teamId) {
                OfflinePlayer op = Bukkit.getOfflinePlayer(entry.getKey());
                if (op.getName() != null && op.getName().equalsIgnoreCase(targetName)) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    public boolean promotePlayer(Player actor, String targetName) {
        Team team = getPlayerTeam(actor.getUniqueId());
        if (team == null) {
            actor.sendMessage(com.guildcore.util.TextUtil.format("<red>✖ You do not belong to a Guild!</red>"));
            return false;
        }

        UUID targetUuid = findTeamMemberUuid(team.getId(), targetName);
        if (targetUuid == null) {
            actor.sendMessage(com.guildcore.util.TextUtil.format("<red>✖ Player '" + targetName + "' was not found in your Guild!</red>"));
            return false;
        }

        if (actor.getUniqueId().equals(targetUuid)) {
            actor.sendMessage(com.guildcore.util.TextUtil.format("<red>✖ You cannot promote yourself!</red>"));
            return false;
        }

        String actorRole = getPlayerRole(actor.getUniqueId());
        if ("RECRUIT".equalsIgnoreCase(actorRole) || "MEMBER".equalsIgnoreCase(actorRole)) {
            actor.sendMessage(com.guildcore.util.TextUtil.format("<red>✖ Only Officers and Guild Leaders can promote members!</red>"));
            return false;
        }

        String currentRole = getPlayerRole(targetUuid);
        if ("LEADER".equalsIgnoreCase(currentRole)) {
            actor.sendMessage(com.guildcore.util.TextUtil.format("<red>✖ Guild Leaders cannot be promoted!</red>"));
            return false;
        }

        if ("OFFICER".equalsIgnoreCase(actorRole) && !"RECRUIT".equalsIgnoreCase(currentRole)) {
            actor.sendMessage(com.guildcore.util.TextUtil.format("<red>✖ Officers can only promote Recruits to Members!</red>"));
            return false;
        }

        String newRole = currentRole;
        if (currentRole.equalsIgnoreCase("RECRUIT")) newRole = "MEMBER";
        else if (currentRole.equalsIgnoreCase("MEMBER")) newRole = "OFFICER";

        if (newRole.equalsIgnoreCase(currentRole)) {
            actor.sendMessage(com.guildcore.util.TextUtil.format("<red>✖ Player is already at maximum rank!</red>"));
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

        OfflinePlayer targetOp = Bukkit.getOfflinePlayer(targetUuid);
        String displayName = targetOp.getName() != null ? targetOp.getName() : targetName;
        broadcastToTeam(team.getId(), com.guildcore.util.TextUtil.format("<gradient:#00FF87:#60EFFF><b>🏰 [Guild] Member <yellow>" + displayName + "</yellow> was promoted to <gold>" + finalRole + "</gold> by <gold>" + actor.getName() + "</gold>!</b></gradient>"));
        return true;
    }

    public boolean demotePlayer(Player actor, String targetName) {
        Team team = getPlayerTeam(actor.getUniqueId());
        if (team == null) {
            actor.sendMessage(com.guildcore.util.TextUtil.format("<red>✖ You do not belong to a Guild!</red>"));
            return false;
        }

        UUID targetUuid = findTeamMemberUuid(team.getId(), targetName);
        if (targetUuid == null) {
            actor.sendMessage(com.guildcore.util.TextUtil.format("<red>✖ Player '" + targetName + "' was not found in your Guild!</red>"));
            return false;
        }

        if (actor.getUniqueId().equals(targetUuid)) {
            actor.sendMessage(com.guildcore.util.TextUtil.format("<red>✖ You cannot demote yourself!</red>"));
            return false;
        }

        String actorRole = getPlayerRole(actor.getUniqueId());
        if (!"LEADER".equalsIgnoreCase(actorRole)) {
            actor.sendMessage(com.guildcore.util.TextUtil.format("<red>✖ Only the Guild Leader can demote members!</red>"));
            return false;
        }

        String currentRole = getPlayerRole(targetUuid);
        if ("LEADER".equalsIgnoreCase(currentRole)) {
            actor.sendMessage(com.guildcore.util.TextUtil.format("<red>✖ Guild Leaders cannot be demoted!</red>"));
            return false;
        }

        String newRole = currentRole;
        if (currentRole.equalsIgnoreCase("OFFICER")) newRole = "MEMBER";
        else if (currentRole.equalsIgnoreCase("MEMBER")) newRole = "RECRUIT";

        if (newRole.equalsIgnoreCase(currentRole)) {
            actor.sendMessage(com.guildcore.util.TextUtil.format("<red>✖ Player is already at lowest rank!</red>"));
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

        OfflinePlayer targetOp = Bukkit.getOfflinePlayer(targetUuid);
        String displayName = targetOp.getName() != null ? targetOp.getName() : targetName;
        broadcastToTeam(team.getId(), com.guildcore.util.TextUtil.format("<gradient:#FF416C:#FF4B2B><b>🏰 [Guild] Member <yellow>" + displayName + "</yellow> was demoted to <gold>" + finalRole + "</gold> by <gold>" + actor.getName() + "</gold>.</b></gradient>"));
        return true;
    }

    public boolean transferLeadership(Player leader, UUID successorUuid) {
        Team team = getPlayerTeam(leader.getUniqueId());
        if (team == null || !team.getLeaderUuid().equals(leader.getUniqueId())) {
            leader.sendMessage(com.guildcore.util.TextUtil.format("<red>✖ Only the Guild Leader can transfer leadership!</red>"));
            return false;
        }
        if (leader.getUniqueId().equals(successorUuid)) {
            leader.sendMessage(com.guildcore.util.TextUtil.format("<red>✖ You are already the Guild Leader!</red>"));
            return false;
        }

        UUID oldLeaderUuid = leader.getUniqueId();
        team.setLeaderUuid(successorUuid);
        playerRoleMap.put(oldLeaderUuid, "OFFICER");
        playerRoleMap.put(successorUuid, "LEADER");

        dbManager.executeAsync(() -> {
            try (Connection conn = dbManager.getConnection()) {
                try (PreparedStatement ps = conn.prepareStatement("UPDATE teams SET leader_uuid = ? WHERE id = ?")) {
                    ps.setString(1, successorUuid.toString());
                    ps.setInt(2, team.getId());
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement("UPDATE team_members SET role = 'OFFICER' WHERE team_id = ? AND player_uuid = ?")) {
                    ps.setInt(1, team.getId());
                    ps.setString(2, oldLeaderUuid.toString());
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement("UPDATE team_members SET role = 'LEADER' WHERE team_id = ? AND player_uuid = ?")) {
                    ps.setInt(1, team.getId());
                    ps.setString(2, successorUuid.toString());
                    ps.executeUpdate();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        org.bukkit.OfflinePlayer successorPlayer = Bukkit.getOfflinePlayer(successorUuid);
        String successorName = successorPlayer.getName() != null ? successorPlayer.getName() : "a Guild Member";
        broadcastToTeam(team.getId(), com.guildcore.util.TextUtil.format("<gradient:#FFD700:#FFA500><b>👑 [Guild] Leader " + leader.getName() + " has transferred Guild Ownership to <yellow>" + successorName + "</yellow>!</b></gradient>"));
        return true;
    }

    public boolean renameTeam(Player player, String newName) {
        Team team = getPlayerTeam(player.getUniqueId());
        if (team == null) {
            player.sendMessage(com.guildcore.util.TextUtil.format("<red>You are not in a team.</red>"));
            return false;
        }
        if (!team.getLeaderUuid().equals(player.getUniqueId())) {
            player.sendMessage(com.guildcore.util.TextUtil.format("<red>Only the Guild Leader can rename the guild!</red>"));
            return false;
        }
        if (getTeamByName(newName) != null) {
            player.sendMessage(com.guildcore.util.TextUtil.format("<red>Team name '" + newName + "' is already taken!</red>"));
            return false;
        }
        String oldName = team.getName();
        teamIdByName.remove(oldName.toLowerCase());
        team.setName(newName);
        teamIdByName.put(newName.toLowerCase(), team.getId());

        dbManager.executeAsync(() -> {
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement("UPDATE teams SET name = ? WHERE id = ?")) {
                ps.setString(1, newName);
                ps.setInt(2, team.getId());
                ps.executeUpdate();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        broadcastToTeam(team.getId(), com.guildcore.util.TextUtil.format("<gradient:#00FF87:#60EFFF><b>✔ [Guild] Guild renamed from " + oldName + " to " + newName + "!</b></gradient>"));
        return true;
    }

    public java.util.Collection<Team> getAllTeams() {
        return teamsById.values();
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

        if (offlineShieldManager != null) {
            offlineShieldManager.removeShieldData(teamId);
        }

        if (claimChestManager != null) {
            claimChestManager.removeClaimChest(teamId);
        } else {
            if (claimManager != null) {
                claimManager.removeAllTeamClaims(teamId);
            }
            if (guildCoreManager != null) {
                guildCoreManager.removeCore(teamId, false);
            }
        }

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online != null && online.isOnline() && online.getOpenInventory() != null && online.getOpenInventory().getTopInventory() != null) {
                org.bukkit.inventory.InventoryHolder holder = online.getOpenInventory().getTopInventory().getHolder();
                if (holder != null && (holder.getClass().getName().contains("Team") || holder instanceof com.guildcore.gui.holders.VaultGUIHolder)) {
                    if (scheduler != null) {
                        scheduler.runSync(online, online::closeInventory);
                    } else {
                        online.closeInventory();
                    }
                }
            }
        }

        try (Connection conn = dbManager.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM team_members WHERE team_id = ?")) { ps.setInt(1, teamId); ps.executeUpdate(); }
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM teams WHERE id = ?")) { ps.setInt(1, teamId); ps.executeUpdate(); }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return true;
    }

    public java.util.List<UUID> getTeamMembers(int teamId) {
        java.util.List<UUID> members = new java.util.ArrayList<>();
        for (Map.Entry<UUID, Integer> entry : playerTeamMap.entrySet()) {
            if (entry.getValue() == teamId) {
                members.add(entry.getKey());
            }
        }
        return members;
    }

    public int getMaxClaimsForTeam(Team team, com.guildcore.config.SettingsManager settingsManager) {
        if (team == null) return 5;
        if (guildCoreManager != null) {
            var core = guildCoreManager.getCoreForTeam(team.getId());
            if (core != null) {
                return guildCoreManager.getMaxClaimsForTier(core.getTier());
            }
        }
        int lvl = Math.min(Math.max(1, team.getLevel()), 5);
        int defaultMax = team.getMaxClaims();
        switch (lvl) {
            case 1: return settingsManager.getInt("teams.max_claims_level_1", Math.max(defaultMax, 5));
            case 2: return settingsManager.getInt("teams.max_claims_level_2", Math.max(defaultMax, 12));
            case 3: return settingsManager.getInt("teams.max_claims_level_3", Math.max(defaultMax, 25));
            case 4: return settingsManager.getInt("teams.max_claims_level_4", Math.max(defaultMax, 40));
            case 5: return settingsManager.getInt("teams.max_claims_level_5", Math.max(defaultMax, 60));
            default: return defaultMax;
        }
    }

    public void broadcastToTeam(int teamId, net.kyori.adventure.text.Component message) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Integer pTeamId = playerTeamMap.get(p.getUniqueId());
            if (pTeamId != null && pTeamId == teamId) {
                p.sendMessage(message);
            }
        }
    }

    public void saveTeamMaxMembers(int teamId, int maxMembers) {
        dbManager.executeAsync(() -> {
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement("UPDATE teams SET max_members = ? WHERE id = ?")) {
                ps.setInt(1, maxMembers);
                ps.setInt(2, teamId);
                ps.executeUpdate();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public void saveTeamVaultSlots(int teamId, int vaultSlots) {
        dbManager.executeAsync(() -> {
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement("UPDATE teams SET vault_slots = ? WHERE id = ?")) {
                ps.setInt(1, vaultSlots);
                ps.setInt(2, teamId);
                ps.executeUpdate();
                DebugManager.log(DebugFlag.TEAM_UPGRADES, "Saved vault_slots=" + vaultSlots + " for team " + teamId);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public void saveTeamVaultPages(int teamId, int vaultPages) {
        dbManager.executeAsync(() -> {
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement("UPDATE teams SET vault_pages = ? WHERE id = ?")) {
                ps.setInt(1, vaultPages);
                ps.setInt(2, teamId);
                ps.executeUpdate();
                DebugManager.log(DebugFlag.TEAM_UPGRADES, "Saved vault_pages=" + vaultPages + " for team " + teamId);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public synchronized boolean purchaseVaultSlot(Team team, int targetGlobalSlot, long cost) {
        if (team == null || targetGlobalSlot <= 0 || cost < 0) return false;

        if (team.getVaultSlots() >= targetGlobalSlot) {
            return false;
        }

        if (team.getBankBalance() < cost) {
            return false;
        }

        team.setBankBalance(team.getBankBalance() - cost);
        team.setVaultSlots(targetGlobalSlot);

        dbManager.executeAsync(() -> {
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement("UPDATE teams SET bank_balance = ?, vault_slots = ? WHERE id = ?")) {
                ps.setLong(1, team.getBankBalance());
                ps.setInt(2, targetGlobalSlot);
                ps.setInt(3, team.getId());
                ps.executeUpdate();
                DebugManager.log(DebugFlag.TEAM_UPGRADES, "Atomically saved bank_balance=" + team.getBankBalance() + " and vault_slots=" + targetGlobalSlot + " for team " + team.getId());
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        return true;
    }
}