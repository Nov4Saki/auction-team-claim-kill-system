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

    public boolean deposit(Team team, UUID playerUuid, long amount) {
        if (amount <= 0 || team == null) return false;
        org.bukkit.entity.Player p = org.bukkit.Bukkit.getPlayer(playerUuid);
        if (economyManager != null && !economyManager.withdraw(playerUuid, amount, "team_bank_deposit")) {
            if (p != null) {
                p.sendMessage(com.guildcore.util.TextUtil.format("<red>✖ Insufficient funds! Required: $" + String.format("%,d", amount) + " Gold (Your balance: $" + String.format("%,d", economyManager.getBalance(playerUuid)) + ").</red>"));
            }
            return false;
        }

        team.setBankBalance(team.getBankBalance() + amount);
        logBankAction(team.getId(), playerUuid, amount, "DEPOSIT");
        saveBankBalance(team.getId(), team.getBankBalance());
        if (p != null) {
            p.sendMessage(com.guildcore.util.TextUtil.format("<green>✔ Successfully deposited $" + String.format("%,d", amount) + " Gold into the Guild Bank!</green>"));
        }
        DebugManager.log(DebugFlag.TEAM_UPGRADES, "Team " + team.getName() + " bank deposit: +" + amount + " by " + playerUuid);
        return true;
    }

    public boolean withdraw(Team team, UUID playerUuid, long amount) {
        if (amount <= 0 || team == null) return false;
        org.bukkit.entity.Player p = org.bukkit.Bukkit.getPlayer(playerUuid);
        if (team.getBankBalance() < amount) {
            if (p != null) {
                p.sendMessage(com.guildcore.util.TextUtil.format("<red>✖ Insufficient Team Bank balance! Required: $" + String.format("%,d", amount) + " Gold (Bank balance: $" + String.format("%,d", team.getBankBalance()) + ").</red>"));
            }
            return false;
        }

        team.setBankBalance(team.getBankBalance() - amount);
        if (economyManager != null) {
            economyManager.deposit(playerUuid, amount, "team_bank_withdraw");
        }
        logBankAction(team.getId(), playerUuid, amount, "WITHDRAW");
        saveBankBalance(team.getId(), team.getBankBalance());
        if (p != null) {
            p.sendMessage(com.guildcore.util.TextUtil.format("<green>✔ Successfully withdrew $" + String.format("%,d", amount) + " Gold from the Guild Bank!</green>"));
        }
        DebugManager.log(DebugFlag.TEAM_UPGRADES, "Team " + team.getName() + " bank withdraw: -" + amount + " by " + playerUuid);
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
