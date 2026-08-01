package com.guildcore.teams;

import com.guildcore.database.DatabaseManager;
import com.guildcore.debug.DebugFlag;
import com.guildcore.debug.DebugManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.UUID;

public class TeamBankManager {
    private final DatabaseManager dbManager;
    private final com.guildcore.economy.EconomyManager economyManager;

    public TeamBankManager(DatabaseManager dbManager, com.guildcore.economy.EconomyManager economyManager) {
        this.dbManager = dbManager;
        this.economyManager = economyManager;
    }

    public boolean deposit(Team team, UUID player, long amount) {
        if (amount <= 0 || team == null) return false;
        if (economyManager != null && !economyManager.withdraw(player, amount, "team_bank_deposit")) {
            return false;
        }

        team.setBankBalance(team.getBankBalance() + amount);
        logBankAction(team.getId(), player, amount, "DEPOSIT");
        saveBankBalance(team.getId(), team.getBankBalance());
        DebugManager.log(DebugFlag.TEAM_UPGRADES, "Team " + team.getName() + " bank deposit: +" + amount + " by " + player);
        return true;
    }

    public boolean withdraw(Team team, UUID player, long amount) {
        if (amount <= 0 || team == null) return false;
        if (team.getBankBalance() < amount) return false;

        team.setBankBalance(team.getBankBalance() - amount);
        if (economyManager != null) {
            economyManager.deposit(player, amount, "team_bank_withdraw");
        }
        logBankAction(team.getId(), player, amount, "WITHDRAW");
        saveBankBalance(team.getId(), team.getBankBalance());
        DebugManager.log(DebugFlag.TEAM_UPGRADES, "Team " + team.getName() + " bank withdraw: -" + amount + " by " + player);
        return true;
    }

    public void saveBankBalance(int teamId, long balance) {
        dbManager.executeAsync(() -> {
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement("UPDATE teams SET bank_balance = ? WHERE id = ?")) {
                ps.setLong(1, balance);
                ps.setInt(2, teamId);
                ps.executeUpdate();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void logBankAction(int teamId, UUID player, long amount, String action) {
        dbManager.executeAsync(() -> {
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT INTO team_bank_log (team_id, player, amount, action) VALUES (?, ?, ?, ?)")) {
                ps.setInt(1, teamId);
                ps.setString(2, player.toString());
                ps.setLong(3, amount);
                ps.setString(4, action);
                ps.executeUpdate();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}
