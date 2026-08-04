package com.guildcore.commands;

import com.guildcore.gui.GUIManager;
import com.guildcore.stats.BountyManager;
import com.guildcore.stats.StatsManager;
import com.guildcore.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class StatsCommand implements TabExecutor {
    private final StatsManager statsManager;
    private final BountyManager bountyManager;
    private final GUIManager guiManager;

    public StatsCommand(StatsManager statsManager, BountyManager bountyManager, GUIManager guiManager) {
        this.statsManager = statsManager;
        this.bountyManager = bountyManager;
        this.guiManager = guiManager;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        String cmd = alias.toLowerCase();

        if (cmd.contains("bounty")) {
            if (args.length == 1) {
                for (String sub : Arrays.asList("set", "list")) {
                    if (sub.startsWith(args[0].toLowerCase())) completions.add(sub);
                }
            } else if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) completions.add(p.getName());
                }
            } else if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
                completions.addAll(Arrays.asList("500", "1000", "5000", "10000"));
            }
        } else {
            if (args.length == 1) {
                for (String sub : Arrays.asList("top", "me")) {
                    if (sub.startsWith(args[0].toLowerCase())) completions.add(sub);
                }
            }
        }
        return completions;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = label.toLowerCase();

        if (cmd.contains("bounty")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Players only.");
                return true;
            }

            if (args.length == 0) {
                long ownBounty = bountyManager.getBounty(player.getUniqueId());
                player.sendMessage(TextUtil.format("<gold>☠ Bounty on your head: <red>$" + String.format("%,d", ownBounty) + "</red></gold>"));
                player.sendMessage(TextUtil.format("<gray>Use /bounty set <player> <amount> or /bounty list</gray>"));
                return true;
            }

            if (args[0].equalsIgnoreCase("list")) {
                var bounties = bountyManager.getActiveBounties();
                if (bounties.isEmpty()) {
                    player.sendMessage(TextUtil.format("<yellow>There are currently no active bounties.</yellow>"));
                    return true;
                }
                player.sendMessage(TextUtil.format("<gold>=== Active Server Bounties ===</gold>"));
                for (var entry : bounties.entrySet()) {
                    org.bukkit.OfflinePlayer p = Bukkit.getOfflinePlayer(entry.getKey());
                    String name = p.getName() != null ? p.getName() : entry.getKey().toString();
                    player.sendMessage(TextUtil.format("<yellow>☠ <red>" + name + "</red>: <green>$" + String.format("%,d", entry.getValue()) + "</green></yellow>"));
                }
                return true;
            }

            if (args.length >= 3 && args[0].equalsIgnoreCase("set")) {
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    player.sendMessage(TextUtil.format("<red>Target player not found.</red>"));
                    return true;
                }
                try {
                    long amount = Long.parseLong(args[2]);
                    if (bountyManager.placeBounty(player.getUniqueId(), target.getUniqueId(), amount)) {
                        player.sendMessage(TextUtil.format("<green>Placed $" + amount + " bounty on " + target.getName() + "!</green>"));
                        Bukkit.broadcast(TextUtil.format("<gold>☠ BOUNTY! <yellow>" + player.getName() + "</yellow> placed $" + amount + " bounty on <red>" + target.getName() + "</red>!</gold>"));
                    } else {
                        player.sendMessage(TextUtil.format("<red>Could not place bounty (insufficient funds).</red>"));
                    }
                } catch (NumberFormatException ignored) {
                    player.sendMessage(TextUtil.format("<red>Invalid amount.</red>"));
                }
                return true;
            }
            player.sendMessage(TextUtil.format("<gold>Usage: /bounty set <player> <amount> | /bounty list</gold>"));
            return true;
        }

        if (cmd.contains("leaderboard") || cmd.equals("lb") || (args.length >= 1 && args[0].equalsIgnoreCase("top"))) {
            if (sender instanceof Player player) {
                if (!player.hasPermission("guildcore.leaderboard")) {
                    player.sendMessage(TextUtil.format("<red>No permission to view leaderboards.</red>"));
                    return true;
                }
                guiManager.openLeaderboardGUI(player, "PLAYER");
                return true;
            }
        }

        if (sender instanceof Player player) {
            StatsManager.PlayerStats stats = statsManager.getStats(player.getUniqueId());
            double kd = stats.deaths() == 0 ? stats.kills() : (double) stats.kills() / stats.deaths();
            player.sendMessage(TextUtil.format("<gold>=== Stats for " + player.getName() + " ===</gold>"));
            player.sendMessage(TextUtil.format("<yellow>⚔ Kills: <green>" + stats.kills() + "</green> | ☠ Deaths: <red>" + stats.deaths() + "</red></yellow>"));
            player.sendMessage(TextUtil.format("<yellow>📊 K/D: <aqua>" + String.format("%.2f", kd) + "</aqua> | 🔥 Streak: <orange>" + stats.killStreak() + "</orange> (Best: " + stats.bestStreak() + ")</yellow>"));
        }
        return true;
    }
}
