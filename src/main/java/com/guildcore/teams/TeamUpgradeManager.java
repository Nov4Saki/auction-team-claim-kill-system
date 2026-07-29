package com.guildcore.teams;

import com.guildcore.database.DatabaseManager;
import com.guildcore.debug.DebugFlag;
import com.guildcore.debug.DebugManager;

import java.sql.Connection;
import java.sql.PreparedStatement;

public class TeamUpgradeManager {
    private final DatabaseManager dbManager;

    public enum UpgradeType {
        MAX_MEMBERS,
        MAX_CLAIMS,
        VAULT_PAGES
    }

    public TeamUpgradeManager(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    public boolean purchaseUpgrade(Team team, UpgradeType type, long costCoins, int valueIncrement) {
        if (team == null || team.getBankBalance() < costCoins) return false;

        team.setBankBalance(team.getBankBalance() - costCoins);

        if (type == UpgradeType.MAX_MEMBERS) {
            team.setMaxMembers(team.getMaxMembers() + valueIncrement);
        } else if (type == UpgradeType.MAX_CLAIMS) {
            team.setMaxClaims(team.getMaxClaims() + valueIncrement);
        }

        dbManager.executeAsync(() -> {
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "UPDATE teams SET bank_balance = ?, max_members = ?, max_claims = ? WHERE id = ?")) {
                ps.setLong(1, team.getBankBalance());
                ps.setInt(2, team.getMaxMembers());
                ps.setInt(3, team.getMaxClaims());
                ps.setInt(4, team.getId());
                ps.executeUpdate();
                DebugManager.log(DebugFlag.TEAM_UPGRADES, "Team " + team.getName() + " upgraded " + type + " (+ " + valueIncrement + ")");
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        return true;
    }
}
