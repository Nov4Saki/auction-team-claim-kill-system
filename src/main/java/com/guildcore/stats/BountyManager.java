package com.guildcore.stats;

import com.guildcore.database.DatabaseManager;
import com.guildcore.debug.DebugFlag;
import com.guildcore.debug.DebugManager;
import com.guildcore.economy.EconomyManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BountyManager {
    private final DatabaseManager dbManager;
    private final EconomyManager economyManager;
    // targetUUID -> stacked bounty amount
    private final Map<UUID, Long> bountyCache = new ConcurrentHashMap<>();

    public BountyManager(DatabaseManager dbManager, EconomyManager economyManager) {
        this.dbManager = dbManager;
        this.economyManager = economyManager;
    }

    public void loadBounties() {
        dbManager.executeAsync(() -> {
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT target_uuid, SUM(amount) AS total FROM bounties WHERE collected = 0 AND expires_at > CURRENT_TIMESTAMP GROUP BY target_uuid");
                 ResultSet rs = ps.executeQuery()) {

                bountyCache.clear();
                while (rs.next()) {
                    UUID target = UUID.fromString(rs.getString("target_uuid"));
                    long total = rs.getLong("total");
                    bountyCache.put(target, total);
                }
                DebugManager.log(DebugFlag.BOUNTY_COLLECTION, "Loaded " + bountyCache.size() + " active stacked bounties.");
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public long getBounty(UUID target) {
        return bountyCache.getOrDefault(target, 0L);
    }

    public Map<UUID, Long> getActiveBounties() {
        return new ConcurrentHashMap<>(bountyCache);
    }

    public boolean placeBounty(UUID placer, UUID target, long amount) {
        if (amount <= 0 || placer.equals(target)) return false;
        if (!economyManager.withdraw(placer, amount, "bounty_place")) return false;

        bountyCache.merge(target, amount, Long::sum);

        dbManager.executeAsync(() -> {
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT INTO bounties (target_uuid, placer_uuid, amount, expires_at) VALUES (?, ?, ?, datetime('now', '+7 days'))")) {
                ps.setString(1, target.toString());
                ps.setString(2, placer.toString());
                ps.setLong(3, amount);
                ps.executeUpdate();
                DebugManager.log(DebugFlag.BOUNTY_COLLECTION, "Placed bounty of $" + amount + " on " + target + " by " + placer);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        return true;
    }

    public long claimBounty(UUID killer, UUID victim) {
        Long bounty = bountyCache.remove(victim);
        if (bounty == null || bounty <= 0) return 0L;

        economyManager.deposit(killer, bounty, "bounty_claim");

        dbManager.executeAsync(() -> {
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement("UPDATE bounties SET collected = 1 WHERE target_uuid = ? AND collected = 0")) {
                ps.setString(1, victim.toString());
                ps.executeUpdate();
                DebugManager.log(DebugFlag.BOUNTY_COLLECTION, "Claimed bounty of $" + bounty + " on " + victim + " by " + killer);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        return bounty;
    }
}
