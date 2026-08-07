package com.guildcore.scoreboard;

import com.guildcore.claims.ClaimInfo;
import com.guildcore.claims.ClaimManager;
import com.guildcore.config.SettingsManager;
import com.guildcore.debug.DebugFlag;
import com.guildcore.debug.DebugManager;
import com.guildcore.economy.EconomyManager;
import com.guildcore.raidtag.RaidTagManager;
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
    private final RaidTagManager raidTagManager;
    private final SettingsManager settingsManager;
    private final SchedulerWrapper scheduler;

    private boolean scoreboardsDisabled = false;

    public ScoreboardManager(EconomyManager economyManager, StatsManager statsManager, BountyManager bountyManager,
                             TeamManager teamManager, ClaimManager claimManager, RaidTagManager raidTagManager,
                             SettingsManager settingsManager, SchedulerWrapper scheduler) {
        this.economyManager = economyManager;
        this.statsManager = statsManager;
        this.bountyManager = bountyManager;
        this.teamManager = teamManager;
        this.claimManager = claimManager;
        this.raidTagManager = raidTagManager;
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

                String titleText = settingsManager.getString("scoreboard.title", "⚡ GUILDCORE");
                Objective obj = board.registerNewObjective(objName, Criteria.DUMMY,
                        Component.text(titleText, NamedTextColor.YELLOW, TextDecoration.BOLD));
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

        // Check if player is raid tagged
        if (raidTagManager != null && raidTagManager.isRaidTagged(player.getUniqueId())) {
            lines.add(ChatColor.RED + "" + ChatColor.BOLD + "⚔ RAID TAGGED");
            int remaining = raidTagManager.getRemainingSeconds(player.getUniqueId());
            if (remaining < 0) {
                lines.add(ChatColor.WHITE + "Status: " + ChatColor.YELLOW + "Inside Enemy Territory");
            } else {
                lines.add(ChatColor.WHITE + "Exit Timer: " + ChatColor.YELLOW + remaining + "s");
            }
            lines.add(ChatColor.RED + "Commands Locked!");
            lines.add(ChatColor.WHITE + "Leave territory to escape!");
        } else {
            // Normal stats display
            long coins = economyManager.getBalance(player.getUniqueId());
            lines.add(ChatColor.WHITE + "💰 Coins: " + ChatColor.GREEN + "$" + String.format("%,d", coins));

            StatsManager.PlayerStats stats = statsManager.getStats(player.getUniqueId());
            double kd = stats.deaths() == 0 ? stats.kills() : (double) stats.kills() / stats.deaths();
            lines.add(ChatColor.WHITE + "⚔ K/D: " + ChatColor.RED + String.format("%.1f", kd) +
                    ChatColor.GRAY + " (" + stats.kills() + "/" + stats.deaths() + ")");

            long bounty = bountyManager.getBounty(player.getUniqueId());
            if (bounty > 0) {
                lines.add(ChatColor.RED + "⚠ BOUNTY: " + ChatColor.YELLOW + "$" + String.format("%,d", bounty));
            }

            Team team = teamManager.getPlayerTeam(player.getUniqueId());
            if (team != null) {
                lines.add(ChatColor.WHITE + "🏰 Guild: " + ChatColor.AQUA + team.getName());

                // Show core status if exists
                if (claimManager != null) {
                    var core = getGuildCoreManager() != null ? getGuildCoreManager().getCoreForTeam(team.getId()) : null;
                    if (core != null) {
                        float hpPercent = core.getHpPercentage();
                        String hpColor = hpPercent > 0.66f ? ChatColor.GREEN.toString() :
                                hpPercent > 0.33f ? ChatColor.YELLOW.toString() : ChatColor.RED.toString();
                        lines.add(ChatColor.WHITE + "❤ Core: " + hpColor + core.getCurrentHp() + "/" + core.getMaxHp());
                    }
                }
            } else {
                lines.add(ChatColor.WHITE + "🏰 Guild: " + ChatColor.GRAY + "None");
            }

            ClaimInfo claim = claimManager.getClaimAt(player.getLocation().getChunk());
            if (claim != null) {
                if (claim.isTeamClaim()) {
                    Team claimTeam = teamManager.getTeam(claim.getTeamId());
                    String claimName = claimTeam != null ? claimTeam.getName() : "Unknown";
                    lines.add(ChatColor.WHITE + "📍 Location: " + ChatColor.LIGHT_PURPLE + claimName + " Territory");
                } else {
                    lines.add(ChatColor.WHITE + "📍 Location: " + ChatColor.GREEN + "Claimed");
                }
            } else {
                lines.add(ChatColor.WHITE + "📍 Location: " + ChatColor.GRAY + "Wilderness");
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

    private com.guildcore.core.GuildCoreManager getGuildCoreManager() {
        com.guildcore.GuildCorePlugin plugin = com.guildcore.GuildCorePlugin.getInstance();
        return plugin != null ? plugin.getGuildCoreManager() : null;
    }
}