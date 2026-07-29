package com.guildcore.economy;

import com.guildcore.database.DatabaseManager;
import com.guildcore.debug.DebugFlag;
import com.guildcore.debug.DebugManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class EconomyManager {
    private final DatabaseManager dbManager;
    private final ConcurrentHashMap<UUID, AtomicLong> balanceCache = new ConcurrentHashMap<>();

    public EconomyManager(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    public void loadPlayer(UUID uuid, String username) {
        dbManager.executeAsync(() -> {
            try (Connection conn = dbManager.getConnection()) {
                long startingBal = 100;
                try (PreparedStatement ps = conn.prepareStatement("SELECT coins FROM players WHERE uuid = ?")) {
                    ps.setString(1, uuid.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            balanceCache.put(uuid, new AtomicLong(rs.getLong("coins")));
                        } else {
                            // New player
                            try (PreparedStatement insert = conn.prepareStatement(
                                    "INSERT INTO players (uuid, username, coins) VALUES (?, ?, ?)")) {
                                insert.setString(1, uuid.toString());
                                insert.setString(2, username);
                                insert.setLong(3, startingBal);
                                insert.executeUpdate();
                            }
                            balanceCache.put(uuid, new AtomicLong(startingBal));
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public long getBalance(UUID uuid) {
        AtomicLong bal = balanceCache.get(uuid);
        return bal != null ? bal.get() : 0L;
    }

    public boolean hasBalance(UUID uuid, long amount) {
        return getBalance(uuid) >= amount;
    }

    public boolean withdraw(UUID uuid, long amount, String reason) {
        if (amount <= 0) return false;
        AtomicLong bal = balanceCache.get(uuid);
        if (bal == null || bal.get() < amount) return false;

        bal.addAndGet(-amount);
        logTransaction(uuid, -amount, reason, null);
        savePlayerCoins(uuid, bal.get());
        DebugManager.log(DebugFlag.ECONOMY_TRANSACTIONS, "Withdrew " + amount + " from " + uuid + " (" + reason + ")");
        return true;
    }

    public void deposit(UUID uuid, long amount, String reason) {
        if (amount <= 0) return;
        AtomicLong bal = balanceCache.computeIfAbsent(uuid, k -> new AtomicLong(0));
        long newBal = bal.addAndGet(amount);
        logTransaction(uuid, amount, reason, null);
        savePlayerCoins(uuid, newBal);
        DebugManager.log(DebugFlag.ECONOMY_TRANSACTIONS, "Deposited " + amount + " to " + uuid + " (" + reason + ")");
    }

    public boolean transfer(UUID from, UUID to, long amount, String reason) {
        if (amount <= 0) return false;
        if (!withdraw(from, amount, reason)) return false;
        deposit(to, amount, reason);
        logTransaction(from, -amount, reason, to.toString());
        return true;
    }

    private void savePlayerCoins(UUID uuid, long amount) {
        dbManager.executeAsync(() -> {
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement("UPDATE players SET coins = ?, last_seen = CURRENT_TIMESTAMP WHERE uuid = ?")) {
                ps.setLong(1, amount);
                ps.setString(2, uuid.toString());
                ps.executeUpdate();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void logTransaction(UUID player, long amount, String reason, String target) {
        dbManager.executeAsync(() -> {
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement("INSERT INTO economy_log (player, amount, reason, target) VALUES (?, ?, ?, ?)")) {
                ps.setString(1, player.toString());
                ps.setLong(2, amount);
                ps.setString(3, reason);
                ps.setString(4, target);
                ps.executeUpdate();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}
