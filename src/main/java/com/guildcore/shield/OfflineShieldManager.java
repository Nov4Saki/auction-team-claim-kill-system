package com.guildcore.shield;

import com.guildcore.config.SettingsManager;
import com.guildcore.database.DatabaseManager;
import com.guildcore.debug.DebugFlag;
import com.guildcore.debug.DebugManager;
import com.guildcore.scheduler.SchedulerWrapper;
import com.guildcore.teams.TeamManager;
import com.guildcore.util.TextUtil;

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

    public OfflineShieldManager(DatabaseManager dbManager, TeamManager teamManager, SettingsManager settingsManager, SchedulerWrapper scheduler) {
        this.dbManager = dbManager;
        this.teamManager = teamManager;
        this.settingsManager = settingsManager;
        this.scheduler = scheduler;
    }

    // ═══════════════════════════════════════════════
    //  PLAYER JOIN / QUIT
    // ═══════════════════════════════════════════════
    public void onPlayerJoin(UUID playerUuid, int teamId) {
        onlineTeamMembers.computeIfAbsent(teamId, k -> ConcurrentHashMap.newKeySet()).add(playerUuid);

        if (Boolean.TRUE.equals(shieldActive.get(teamId))) {
            shieldActive.put(teamId, false);
            DebugManager.log(DebugFlag.OFFLINE_SHIELD, "Shield deactivated for team " + teamId + " (player joined: " + playerUuid + ")");
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
                scheduler.runLater(null, () -> {
                    Set<UUID> currentMembers = onlineTeamMembers.get(frozenTeamId);
                    if (currentMembers == null || currentMembers.isEmpty()) {
                        double charge = shieldChargeMinutes.getOrDefault(frozenTeamId, 0.0);
                        if (charge > 0) {
                            shieldActive.put(frozenTeamId, true);
                            DebugManager.log(DebugFlag.OFFLINE_SHIELD, "Shield ACTIVATED for team " + frozenTeamId + " (charge: " + String.format("%.1f", charge) + " min)");
                            saveShield(frozenTeamId);
                        } else {
                            DebugManager.log(DebugFlag.OFFLINE_SHIELD, "Shield NOT activated for team " + frozenTeamId + " (no charge)");
                        }
                    }
                }, delaySec * 20L);
            }
        }
    }

    // ═══════════════════════════════════════════════
    //  CHARGE & DRAIN TASKS
    // ═══════════════════════════════════════════════
    public void startChargeTask() {
        scheduler.runTaskTimer(() -> {
            double chargeRate = settingsManager.getDouble("shield.charge_rate", 2.0);
            double maxMinutes = settingsManager.getDouble("shield.max_minutes", 1080.0);

            for (Map.Entry<Integer, Set<UUID>> entry : onlineTeamMembers.entrySet()) {
                int teamId = entry.getKey();
                Set<UUID> members = entry.getValue();
                if (members != null && !members.isEmpty()) {
                    double current = shieldChargeMinutes.getOrDefault(teamId, 0.0);
                    double added = chargeRate;
                    double newCharge = Math.min(current + added, maxMinutes);
                    shieldChargeMinutes.put(teamId, newCharge);
                    DebugManager.log(DebugFlag.OFFLINE_SHIELD, "Charged team " + teamId + ": " + String.format("%.1f", current) + " -> " + String.format("%.1f", newCharge) + " min (+" + String.format("%.1f", added) + ")");
                }
            }
            return true;
        }, 1200L, 1200L);
    }

    public void startDrainTask() {
        scheduler.runTaskTimer(() -> {
            double drainMultiplier = settingsManager.getDouble("shield.drain_multiplier", 1.0);

            List<Integer> depleted = new ArrayList<>();
            for (Map.Entry<Integer, Boolean> entry : shieldActive.entrySet()) {
                if (Boolean.TRUE.equals(entry.getValue())) {
                    int teamId = entry.getKey();
                    double current = shieldChargeMinutes.getOrDefault(teamId, 0.0);
                    double drained = drainMultiplier;
                    double newCharge = current - drained;

                    if (newCharge <= 0) {
                        newCharge = 0;
                        depleted.add(teamId);
                    }
                    shieldChargeMinutes.put(teamId, newCharge);
                    DebugManager.log(DebugFlag.OFFLINE_SHIELD, "Drained team " + teamId + " shield: " + String.format("%.1f", current) + " -> " + String.format("%.1f", newCharge) + " min");
                }
            }

            for (int teamId : depleted) {
                shieldActive.put(teamId, false);
                teamManager.broadcastToTeam(teamId, TextUtil.format("<red><b>⚠ Your offline shield has been depleted! Your territory is now vulnerable.</b></red>"));
                DebugManager.log(DebugFlag.OFFLINE_SHIELD, "Shield DEPLETED for team " + teamId);
                saveShield(teamId);
            }
            return true;
        }, 1200L, 1200L);
    }

    // ═══════════════════════════════════════════════
    //  GETTERS
    // ═══════════════════════════════════════════════
    public boolean isShieldActive(int teamId) {
        return Boolean.TRUE.equals(shieldActive.get(teamId));
    }

    public double getShieldCharge(int teamId) {
        return shieldChargeMinutes.getOrDefault(teamId, 0.0);
    }

    public double getMaxShieldMinutes() {
        return settingsManager.getDouble("shield.max_minutes", 1080.0);
    }

    public void setShieldCharge(int teamId, double minutes) {
        double max = getMaxShieldMinutes();
        shieldChargeMinutes.put(teamId, Math.min(minutes, max));
        saveShield(teamId);
    }

    // ═══════════════════════════════════════════════
    //  DATABASE
    // ═══════════════════════════════════════════════
    public void loadAllShields() {
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM offline_shields");
             ResultSet rs = ps.executeQuery()) {

            shieldChargeMinutes.clear();
            shieldActive.clear();
            lastAllOfflineTime.clear();

            while (rs.next()) {
                int teamId = rs.getInt("team_id");
                double charge = rs.getDouble("charge_minutes");
                long lastOffline = rs.getLong("last_all_offline_time");
                boolean active = rs.getBoolean("shield_active");

                shieldChargeMinutes.put(teamId, charge);
                lastAllOfflineTime.put(teamId, lastOffline);
                shieldActive.put(teamId, active);
            }

            DebugManager.log(DebugFlag.OFFLINE_SHIELD, "Loaded " + shieldChargeMinutes.size() + " shield records from database.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void saveAllShields() {
        for (Map.Entry<Integer, Double> entry : shieldChargeMinutes.entrySet()) {
            saveShieldSync(entry.getKey());
        }
        DebugManager.log(DebugFlag.OFFLINE_SHIELD, "Saved " + shieldChargeMinutes.size() + " shield records to database.");
    }

    public void saveShield(int teamId) {
        dbManager.executeAsync(() -> saveShieldSync(teamId));
    }

    private void saveShieldSync(int teamId) {
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT OR REPLACE INTO offline_shields (team_id, charge_minutes, last_all_offline_time, shield_active) VALUES (?, ?, ?, ?)")) {
            ps.setInt(1, teamId);
            ps.setDouble(2, shieldChargeMinutes.getOrDefault(teamId, 0.0));
            ps.setLong(3, lastAllOfflineTime.getOrDefault(teamId, 0L));
            ps.setBoolean(4, Boolean.TRUE.equals(shieldActive.get(teamId)));
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
