package com.guildcore.shield;

import com.guildcore.config.SettingsManager;
import com.guildcore.database.DatabaseManager;
import com.guildcore.debug.DebugFlag;
import com.guildcore.debug.DebugManager;
import com.guildcore.scheduler.SchedulerWrapper;
import com.guildcore.teams.Team;
import com.guildcore.teams.TeamManager;
import com.guildcore.util.TextUtil;
import org.bukkit.Bukkit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class OfflineShieldManager {
    private final DatabaseManager dbManager;
    private final TeamManager teamManager;
    private final SettingsManager settingsManager;
    private final SchedulerWrapper scheduler;

    private final Map<Integer, Double> shieldChargeMinutes = new ConcurrentHashMap<>();
    private final Map<Integer, Set<UUID>> onlineTeamMembers = new ConcurrentHashMap<>();
    private final Map<Integer, Boolean> shieldActive = new ConcurrentHashMap<>();
    private final Map<Integer, Long> lastAllOfflineTime = new ConcurrentHashMap<>();

    public OfflineShieldManager(DatabaseManager dbManager, TeamManager teamManager,
                                SettingsManager settingsManager, SchedulerWrapper scheduler) {
        this.dbManager = dbManager;
        this.teamManager = teamManager;
        this.settingsManager = settingsManager;
        this.scheduler = scheduler;
    }

    public void onPlayerJoin(UUID playerUuid, int teamId) {
        Set<UUID> members = onlineTeamMembers.computeIfAbsent(teamId,
                k -> ConcurrentHashMap.newKeySet());
        boolean wasEmpty = members.isEmpty();
        members.add(playerUuid);

        if (wasEmpty || Boolean.TRUE.equals(shieldActive.get(teamId))) {
            shieldActive.put(teamId, false);

            Team team = teamManager.getTeam(teamId);
            String teamName = team != null ? team.getName() : "Unknown";
            String playerName = Bukkit.getOfflinePlayer(playerUuid).getName();

            DebugManager.log(DebugFlag.OFFLINE_SHIELD,
                    "Shield DEACTIVATED for team " + teamId + " (" + teamName +
                            ") - player " + playerName + " joined. " +
                            "Charge remaining: " + String.format("%.1f", getShieldCharge(teamId)) + " min");

            teamManager.broadcastToTeam(teamId, TextUtil.format(
                    "<green><b>🛡 Offline Shield deactivated!</b> " + playerName +
                            " has returned. Your territory is no longer invulnerable.</green>"));
            teamManager.broadcastToTeam(teamId, TextUtil.format(
                    "<gray>Shield charge remaining: " +
                            String.format("%.1f", getShieldCharge(teamId)) + " minutes</gray>"));

            saveShield(teamId);
        }
    }

    public void onPlayerQuit(UUID playerUuid, int teamId) {
        Set<UUID> members = onlineTeamMembers.get(teamId);
        if (members != null) {
            members.remove(playerUuid);

            if (members.isEmpty()) {
                lastAllOfflineTime.put(teamId, System.currentTimeMillis());

                int delaySec = settingsManager.getInt("shield.activation_delay_sec", 10);
                final int frozenTeamId = teamId;

                DebugManager.log(DebugFlag.OFFLINE_SHIELD,
                        "All members offline for team " + teamId +
                                ". Shield activation scheduled in " + delaySec + "s");

                scheduler.runTaskTimer(() -> {
                    checkAndActivateShield(frozenTeamId);
                    return false;
                }, delaySec * 20L, 20L);
            }
        }
    }

    private void checkAndActivateShield(int teamId) {
        Set<UUID> currentMembers = onlineTeamMembers.get(teamId);
        if (currentMembers == null || currentMembers.isEmpty()) {
            double charge = shieldChargeMinutes.getOrDefault(teamId, 0.0);

            if (charge > 0) {
                shieldActive.put(teamId, true);

                Team team = teamManager.getTeam(teamId);
                String teamName = team != null ? team.getName() : "Unknown";

                DebugManager.log(DebugFlag.OFFLINE_SHIELD,
                        "Shield ACTIVATED for team " + teamId + " (" + teamName +
                                "). Charge: " + String.format("%.1f", charge) + " min");

                teamManager.broadcastToTeam(teamId, TextUtil.format(
                        "<red><b>🛡 Offline Shield ACTIVATED!</b> Your territory is now " +
                                "protected for " + String.format("%.1f", charge) + " minutes.</red>"));
                teamManager.broadcastToTeam(teamId, TextUtil.format(
                        "<gray>While the shield is active, your claims cannot be raided. " +
                                "The shield will drain in real-time while all members are offline.</gray>"));

                saveShield(teamId);
            } else {
                DebugManager.log(DebugFlag.OFFLINE_SHIELD,
                        "Shield NOT activated for team " + teamId + " - no charge accumulated");

                teamManager.broadcastToTeam(teamId, TextUtil.format(
                        "<yellow><b>⚠ No Offline Shield available!</b> Your territory is " +
                                "vulnerable while you're offline. Stay online to accumulate shield charge.</yellow>"));
            }
        }
    }

    public void startChargeTask() {
        scheduler.runTaskTimer(() -> {
            double chargeRate = settingsManager.getDouble("shield.charge_rate", 2.0);
            double maxMinutes = settingsManager.getDouble("shield.max_minutes", 1080.0);

            for (Map.Entry<Integer, Set<UUID>> entry : onlineTeamMembers.entrySet()) {
                int teamId = entry.getKey();
                Set<UUID> members = entry.getValue();

                if (members != null && !members.isEmpty()) {
                    if (!Boolean.TRUE.equals(shieldActive.get(teamId))) {
                        double current = shieldChargeMinutes.getOrDefault(teamId, 0.0);
                        double added = chargeRate * members.size();
                        double newCharge = Math.min(current + added, maxMinutes);
                        shieldChargeMinutes.put(teamId, newCharge);

                        DebugManager.log(DebugFlag.OFFLINE_SHIELD,
                                "Charged team " + teamId + ": " +
                                        String.format("%.1f", current) + " -> " +
                                        String.format("%.1f", newCharge) + " min (+" +
                                        String.format("%.1f", added) + ", " + members.size() + " members online)");
                    }
                }
            }
            return true;
        }, 1200L, 1200L);
    }

    public void startDrainTask() {
        scheduler.runTaskTimer(() -> {
            double drainMultiplier = settingsManager.getDouble("shield.drain_multiplier", 1.0);
            List<Integer> depletedTeams = new ArrayList<>();

            for (Map.Entry<Integer, Boolean> entry : shieldActive.entrySet()) {
                if (Boolean.TRUE.equals(entry.getValue())) {
                    int teamId = entry.getKey();
                    double current = shieldChargeMinutes.getOrDefault(teamId, 0.0);
                    double drained = drainMultiplier;
                    double newCharge = Math.max(0, current - drained);

                    shieldChargeMinutes.put(teamId, newCharge);

                    DebugManager.log(DebugFlag.OFFLINE_SHIELD,
                            "Drained team " + teamId + " shield: " +
                                    String.format("%.1f", current) + " -> " +
                                    String.format("%.1f", newCharge) + " min (-" +
                                    String.format("%.1f", drained) + ")");

                    if (newCharge <= 0) {
                        depletedTeams.add(teamId);
                    }
                }
            }

            for (int teamId : depletedTeams) {
                shieldActive.put(teamId, false);
                shieldChargeMinutes.put(teamId, 0.0);

                Team team = teamManager.getTeam(teamId);
                String teamName = team != null ? team.getName() : "Unknown";

                DebugManager.log(DebugFlag.OFFLINE_SHIELD,
                        "Shield DEPLETED for team " + teamId + " (" + teamName + ")");

                teamManager.broadcastToTeam(teamId, TextUtil.format(
                        "<red><b>⚠ YOUR OFFLINE SHIELD HAS BEEN DEPLETED!</b></red>"));
                teamManager.broadcastToTeam(teamId, TextUtil.format(
                        "<red>Your territory is now VULNERABLE to raids! " +
                                "Log in to start accumulating shield charge again.</red>"));

                saveShield(teamId);
            }
            return true;
        }, 1200L, 1200L);
    }

    public boolean isShieldActive(int teamId) {
        return Boolean.TRUE.equals(shieldActive.get(teamId));
    }

    public double getShieldCharge(int teamId) {
        return shieldChargeMinutes.getOrDefault(teamId, 0.0);
    }

    public double getMaxShieldMinutes() {
        return settingsManager.getDouble("shield.max_minutes", 1080.0);
    }

    public String getShieldChargeFormatted(int teamId) {
        double minutes = getShieldCharge(teamId);
        if (minutes >= 60) {
            long hours = (long) (minutes / 60);
            long mins = (long) (minutes % 60);
            return hours + "h " + mins + "m";
        }
        return String.format("%.0f minutes", minutes);
    }

    public boolean hasOnlineMembers(int teamId) {
        Set<UUID> members = onlineTeamMembers.get(teamId);
        return members != null && !members.isEmpty();
    }

    public int getOnlineMemberCount(int teamId) {
        Set<UUID> members = onlineTeamMembers.get(teamId);
        return members != null ? members.size() : 0;
    }

    public void setShieldCharge(int teamId, double minutes) {
        double max = getMaxShieldMinutes();
        shieldChargeMinutes.put(teamId, Math.min(minutes, max));
        saveShield(teamId);

        DebugManager.log(DebugFlag.OFFLINE_SHIELD,
                "Admin set shield charge for team " + teamId + " to " +
                        String.format("%.1f", minutes) + " min");
    }

    public void activateShield(int teamId) {
        double charge = getShieldCharge(teamId);
        if (charge > 0) {
            shieldActive.put(teamId, true);
            saveShield(teamId);

            Team team = teamManager.getTeam(teamId);
            String teamName = team != null ? team.getName() : "Unknown";

            teamManager.broadcastToTeam(teamId, TextUtil.format(
                    "<red><b>🛡 Offline Shield FORCE ACTIVATED by admin!</b> " +
                            "Duration: " + getShieldChargeFormatted(teamId) + "</red>"));

            DebugManager.log(DebugFlag.OFFLINE_SHIELD,
                    "Admin force-activated shield for team " + teamId + " (" + teamName + ")");
        }
    }

    public void deactivateShield(int teamId) {
        shieldActive.put(teamId, false);
        saveShield(teamId);

        Team team = teamManager.getTeam(teamId);
        String teamName = team != null ? team.getName() : "Unknown";

        teamManager.broadcastToTeam(teamId, TextUtil.format(
                "<yellow><b>🛡 Offline Shield DEACTIVATED by admin!</b></yellow>"));

        DebugManager.log(DebugFlag.OFFLINE_SHIELD,
                "Admin force-deactivated shield for team " + teamId + " (" + teamName + ")");
    }

    public Map<String, Object> getShieldInfo(int teamId) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("active", isShieldActive(teamId));
        info.put("charge", getShieldCharge(teamId));
        info.put("max", getMaxShieldMinutes());
        info.put("online", getOnlineMemberCount(teamId));
        info.put("chargeFormatted", getShieldChargeFormatted(teamId));
        return info;
    }

    public void loadAllShields() {
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM offline_shields");
             ResultSet rs = ps.executeQuery()) {

            shieldChargeMinutes.clear();
            shieldActive.clear();
            lastAllOfflineTime.clear();

            int loaded = 0;
            while (rs.next()) {
                int teamId = rs.getInt("team_id");
                double charge = rs.getDouble("charge_minutes");
                long lastOffline = rs.getLong("last_all_offline_time");
                boolean active = rs.getBoolean("shield_active");

                shieldChargeMinutes.put(teamId, charge);
                lastAllOfflineTime.put(teamId, lastOffline);

                if (active) {
                    if (charge > 0) {
                        shieldActive.put(teamId, true);
                    } else {
                        shieldActive.put(teamId, false);
                        shieldChargeMinutes.put(teamId, 0.0);
                    }
                } else {
                    shieldActive.put(teamId, false);
                }
                loaded++;
            }

            DebugManager.log(DebugFlag.OFFLINE_SHIELD,
                    "Loaded " + loaded + " shield records from database. " +
                            "Active shields: " + shieldActive.values().stream().filter(Boolean.TRUE::equals).count());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void saveAllShields() {
        for (Map.Entry<Integer, Double> entry : shieldChargeMinutes.entrySet()) {
            saveShieldSync(entry.getKey());
        }
        DebugManager.log(DebugFlag.OFFLINE_SHIELD,
                "Saved " + shieldChargeMinutes.size() + " shield records to database.");
    }

    public void removeShieldData(int teamId) {
        shieldChargeMinutes.remove(teamId);
        lastAllOfflineTime.remove(teamId);
        shieldActive.remove(teamId);
        onlineTeamMembers.remove(teamId);

        dbManager.executeAsync(() -> {
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement("DELETE FROM offline_shields WHERE team_id = ?")) {
                ps.setInt(1, teamId);
                ps.executeUpdate();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public void saveShield(int teamId) {
        dbManager.executeAsync(() -> saveShieldSync(teamId));
    }

    private void saveShieldSync(int teamId) {
        // Prevent foreign key constraint failure if team was deleted/disbanded
        if (teamManager != null && teamManager.getTeam(teamId) == null) {
            shieldChargeMinutes.remove(teamId);
            lastAllOfflineTime.remove(teamId);
            shieldActive.remove(teamId);
            onlineTeamMembers.remove(teamId);
            return;
        }

        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT OR REPLACE INTO offline_shields " +
                             "(team_id, charge_minutes, last_all_offline_time, shield_active) " +
                             "VALUES (?, ?, ?, ?)")) {
            ps.setInt(1, teamId);
            ps.setDouble(2, shieldChargeMinutes.getOrDefault(teamId, 0.0));
            ps.setLong(3, lastAllOfflineTime.getOrDefault(teamId, 0L));
            ps.setBoolean(4, Boolean.TRUE.equals(shieldActive.get(teamId)));
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void shutdown() {
        saveAllShields();
        DebugManager.log(DebugFlag.OFFLINE_SHIELD, "OfflineShieldManager shut down cleanly.");
    }
}