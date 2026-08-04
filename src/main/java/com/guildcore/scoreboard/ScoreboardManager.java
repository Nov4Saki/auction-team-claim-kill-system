package com.guildcore.scoreboard;

import com.guildcore.claims.ClaimInfo;
import com.guildcore.claims.ClaimManager;
import com.guildcore.combat.CombatTagManager;
import com.guildcore.config.SettingsManager;
import com.guildcore.debug.DebugFlag;
import com.guildcore.debug.DebugManager;
import com.guildcore.economy.EconomyManager;
import com.guildcore.config.SettingsManager;
import com.guildcore.scheduler.SchedulerWrapper;
import com.guildcore.stats.BountyManager;
import com.guildcore.stats.StatsManager;
import com.guildcore.teams.Team;
import com.guildcore.teams.TeamManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;

import java.util.ArrayList;
import java.util.List;

public class ScoreboardManager {
    private final EconomyManager economyManager;
    private final StatsManager statsManager;
    private final BountyManager bountyManager;
    private final TeamManager teamManager;
    private final ClaimManager claimManager;
    private final CombatTagManager combatTagManager;
    private final SettingsManager settingsManager;
    private final SchedulerWrapper scheduler;

    private boolean scoreboardsDisabled = false;

    public ScoreboardManager(EconomyManager economyManager, StatsManager statsManager, BountyManager bountyManager, TeamManager teamManager, ClaimManager claimManager, CombatTagManager combatTagManager, SettingsManager settingsManager, SchedulerWrapper scheduler) {
        this.economyManager = economyManager;
        this.statsManager = statsManager;
        this.bountyManager = bountyManager;
        this.teamManager = teamManager;
        this.claimManager = claimManager;
        this.combatTagManager = combatTagManager;
        this.settingsManager = settingsManager;
        this.scheduler = scheduler;
    }

    public void clearServerScoreboards() {
        this.scoreboardsDisabled = true;
        try {
            Scoreboard mainBoard = Bukkit.getScoreboardManager().getMainScoreboard();

            // Clear all slots from main scoreboard
            for (DisplaySlot slot : DisplaySlot.values()) {
                try { mainBoard.clearSlot(slot); } catch (Exception ignored) {}
            }

            // Unregister all objectives on main scoreboard
            for (Objective obj : new ArrayList<>(mainBoard.getObjectives())) {
                try { obj.unregister(); } catch (Exception ignored) {}
            }

            // Wipe scoreboards for all online players
            for (Player player : Bukkit.getOnlinePlayers()) {
                scheduler.runSync(player, () -> {
                    try {
                        Scoreboard pBoard = player.getScoreboard();
                        if (pBoard != null) {
                            for (DisplaySlot slot : DisplaySlot.values()) {
                                try { pBoard.clearSlot(slot); } catch (Exception ignored) {}
                            }
                            for (Objective obj : new ArrayList<>(pBoard.getObjectives())) {
                                try { obj.unregister(); } catch (Exception ignored) {}
                            }
                        }
                        player.setScoreboard(mainBoard);
                    } catch (Exception ignored) {}
                });
            }
            DebugManager.log(DebugFlag.SCOREBOARD_UPDATES, "Wiped all server scoreboards and objectives cleanly.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void enableScoreboards() {
        this.scoreboardsDisabled = false;
    }

    public boolean isScoreboardsDisabled() {
        return scoreboardsDisabled;
    }

    public void updateBoard(Player player) {
        if (player == null || !player.isOnline() || scoreboardsDisabled) return;

        scheduler.runSync(player, () -> {
            try {
                Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
                String objName = "gc_" + player.getUniqueId().toString().substring(0, 8);

                Objective oldObj = board.getObjective(objName);
                if (oldObj != null) {
                    try { oldObj.unregister(); } catch (Exception ignored) {}
                }

                String titleText = settingsManager.getString("scoreboard.title", "⚡ MY SERVER");
                Objective obj = board.registerNewObjective(objName, Criteria.DUMMY, Component.text(titleText, NamedTextColor.YELLOW, TextDecoration.BOLD));
                obj.setDisplaySlot(DisplaySlot.SIDEBAR);

                List<String> lines = buildLines(player);
                for (int i = 0; i < lines.size(); i++) {
                    String uniqueLine = lines.get(i) + getUniqueSuffix(i);
                    Score score = obj.getScore(uniqueLine);
                    score.setScore(lines.size() - i);
                }

                player.setScoreboard(board);
                DebugManager.log(DebugFlag.SCOREBOARD_UPDATES, "Updated sidebar scoreboard for " + player.getName());
            } catch (Exception e) {
                DebugManager.log(DebugFlag.SCOREBOARD_UPDATES, "Failed to update scoreboard for " + player.getName() + ": " + e.getMessage());
            }
        });
    }

    private List<String> buildLines(Player player) {
        List<String> lines = new ArrayList<>();
        lines.add(ChatColor.GRAY + "━━━━━━━━━━━━━━━");

        if (combatTagManager.isTagged(player)) {
            lines.add(ChatColor.RED + "" + ChatColor.BOLD + "⚔ COMBAT TAGGED");
            lines.add(ChatColor.WHITE + "Tag Timer: " + ChatColor.YELLOW + combatTagManager.getRemainingSeconds(player) + "s");
            lines.add(ChatColor.RED + "Restricted Items Locked!");
        } else {
            long coins = economyManager.getBalance(player.getUniqueId());
            lines.add(ChatColor.WHITE + "💰 Coins: " + ChatColor.GREEN + "$" + coins);

            StatsManager.PlayerStats stats = statsManager.getStats(player.getUniqueId());
            lines.add(ChatColor.WHITE + "⚔ Kills: " + ChatColor.RED + stats.kills() + ChatColor.GRAY + " | " + ChatColor.WHITE + "☠ Deaths: " + ChatColor.RED + stats.deaths());

            long bounty = bountyManager.getBounty(player.getUniqueId());
            if (bounty > 0) {
                lines.add(ChatColor.RED + "⚠ BOUNTY: " + ChatColor.YELLOW + "$" + bounty);
            }

            Team team = teamManager.getPlayerTeam(player.getUniqueId());
            lines.add(ChatColor.WHITE + "🏰 Team: " + (team != null ? ChatColor.AQUA + "[" + team.getName() + "]" : ChatColor.GRAY + "None"));

            ClaimInfo claim = claimManager.getClaimAt(player.getLocation().getChunk());
            if (claim != null) {
                lines.add(ChatColor.WHITE + "📍 Claim: " + (claim.isTeamClaim() ? ChatColor.LIGHT_PURPLE + "Team Territory" : ChatColor.GREEN + "Claimed"));
            } else {
                lines.add(ChatColor.WHITE + "📍 Claim: " + ChatColor.GRAY + "Wilderness");
            }
        }

        lines.add(ChatColor.GRAY + "━━━━━━━━━━━━━━━");
        return lines;
    }

    private String getUniqueSuffix(int index) {
        ChatColor[] colors = ChatColor.values();
        return colors[index % colors.length].toString() + ChatColor.RESET;
    }

    public void startUpdateTask() {
        int ticks = settingsManager.getInt("scoreboard.update_ticks", 20);
        scheduler.runTaskTimer(() -> {
            if (!scoreboardsDisabled) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    updateBoard(player);
                }
            }
            return true;
        }, 0L, ticks);
    }

    public void updateTeamMembers(int teamId) {
        if (scoreboardsDisabled || teamManager == null) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            Team team = teamManager.getPlayerTeam(player.getUniqueId());
            if (team != null && team.getId() == teamId) {
                updateBoard(player);
            }
        }
    }
}
