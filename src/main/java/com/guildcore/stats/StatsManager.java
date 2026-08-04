package com.guildcore.stats;

import com.guildcore.database.DatabaseManager;
import com.guildcore.debug.DebugFlag;
import com.guildcore.debug.DebugManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class StatsManager {
    private final DatabaseManager dbManager;

    public record PlayerStats(int kills, int deaths, int killStreak, int bestStreak) {}

    private final Map<UUID, PlayerStats> statsCache = new ConcurrentHashMap<>();

    public StatsManager(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    public PlayerStats getStats(UUID uuid) {
        return statsCache.getOrDefault(uuid, new PlayerStats(0, 0, 0, 0));
    }

    public void recordKill(UUID killerUuid, UUID victimUuid) {
        PlayerStats killerOld = getStats(killerUuid);
        int newKills = killerOld.kills() + 1;
        int newStreak = killerOld.killStreak() + 1;
        int newBest = Math.max(killerOld.bestStreak(), newStreak);
        statsCache.put(killerUuid, new PlayerStats(newKills, killerOld.deaths(), newStreak, newBest));

        PlayerStats victimOld = getStats(victimUuid);
        statsCache.put(victimUuid, new PlayerStats(victimOld.kills(), victimOld.deaths() + 1, 0, victimOld.bestStreak()));

        dbManager.executeAsync(() -> {
            try (Connection conn = dbManager.getConnection()) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE players SET kills = ?, kill_streak = ?, best_streak = ? WHERE uuid = ?")) {
                    ps.setInt(1, newKills);
                    ps.setInt(2, newStreak);
                    ps.setInt(3, newBest);
                    ps.setString(4, killerUuid.toString());
                    ps.executeUpdate();
                }

                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE players SET deaths = deaths + 1, kill_streak = 0 WHERE uuid = ?")) {
                    ps.setString(1, victimUuid.toString());
                    ps.executeUpdate();
                }
                DebugManager.log(DebugFlag.MOB_SPAWN_GATING, "Recorded PvP kill: " + killerUuid + " killed " + victimUuid);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public void getTopKillersAsync(int limit, java.util.function.Consumer<java.util.List<String>> callback) {
        dbManager.executeAsync(() -> {
            java.util.List<String> results = new java.util.ArrayList<>();
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT username, kills, deaths FROM players ORDER BY kills DESC LIMIT ?")) {
                ps.setInt(1, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    int rank = 1;
                    while (rs.next()) {
                        String name = rs.getString("username");
                        int kills = rs.getInt("kills");
                        int deaths = rs.getInt("deaths");
                        results.add(rank++ + ". " + name + " - " + kills + " Kills (" + deaths + " Deaths)");
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            callback.accept(results);
        });
    }
}
