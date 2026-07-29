package com.guildcore.commands;

import com.guildcore.economy.EconomyManager;
import com.guildcore.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CoinsCommand implements TabExecutor {
    private final EconomyManager economyManager;

    public CoinsCommand(EconomyManager economyManager) {
        this.economyManager = economyManager;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        String label = alias.toLowerCase();

        if (label.contains("eco")) {
            if (args.length == 1) {
                for (String sub : Arrays.asList("give", "take", "set")) {
                    if (sub.startsWith(args[0].toLowerCase())) completions.add(sub);
                }
            } else if (args.length == 2) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getName().toLowerCase().startsWith(args[1].toLowerCase())) completions.add(player.getName());
                }
            } else if (args.length == 3) {
                completions.addAll(Arrays.asList("100", "500", "1000", "5000", "10000"));
            }
        } else if (label.contains("pay")) {
            if (args.length == 1) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getName().toLowerCase().startsWith(args[0].toLowerCase())) completions.add(player.getName());
                }
            } else if (args.length == 2) {
                completions.addAll(Arrays.asList("50", "100", "500", "1000"));
            }
        }

        return completions;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = label.toLowerCase();
        if (cmd.contains("pay")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Players only.");
                return true;
            }
            if (args.length < 2) {
                player.sendMessage(TextUtil.format("<red>Usage: /gcpay <player> <amount></red>"));
                return true;
            }
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                player.sendMessage(TextUtil.format("<red>Player not found.</red>"));
                return true;
            }
            try {
                long amount = Long.parseLong(args[1]);
                if (amount <= 0) {
                    player.sendMessage(TextUtil.format("<red>Amount must be positive.</red>"));
                    return true;
                }
                if (economyManager.transfer(player.getUniqueId(), target.getUniqueId(), amount, "pay_command")) {
                    player.sendMessage(TextUtil.format("<green>Paid $" + amount + " to " + target.getName() + ".</green>"));
                    target.sendMessage(TextUtil.format("<green>Received $" + amount + " from " + player.getName() + ".</green>"));
                } else {
                    player.sendMessage(TextUtil.format("<red>Insufficient balance.</red>"));
                }
            } catch (NumberFormatException e) {
                player.sendMessage(TextUtil.format("<red>Invalid amount.</red>"));
            }
            return true;
        }

        if (cmd.contains("eco")) {
            if (!sender.hasPermission("guildcore.admin")) {
                sender.sendMessage(TextUtil.format("<red>No permission.</red>"));
                return true;
            }
            if (args.length < 3) {
                sender.sendMessage(TextUtil.format("<red>Usage: /gceco <give|take|set> <player> <amount></red>"));
                return true;
            }
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(TextUtil.format("<red>Player not found.</red>"));
                return true;
            }
            try {
                long amount = Long.parseLong(args[2]);
                String sub = args[0].toLowerCase();
                if (sub.equals("give")) {
                    economyManager.deposit(target.getUniqueId(), amount, "admin_give");
                    sender.sendMessage(TextUtil.format("<green>Gave $" + amount + " to " + target.getName() + ".</green>"));
                } else if (sub.equals("take")) {
                    economyManager.withdraw(target.getUniqueId(), amount, "admin_take");
                    sender.sendMessage(TextUtil.format("<green>Took $" + amount + " from " + target.getName() + ".</green>"));
                }
            } catch (NumberFormatException e) {
                sender.sendMessage(TextUtil.format("<red>Invalid amount.</red>"));
            }
            return true;
        }

        if (sender instanceof Player player) {
            long bal = economyManager.getBalance(player.getUniqueId());
            player.sendMessage(TextUtil.format("<gold>💰 Balance: <green>$" + bal + "</green></gold>"));
        }
        return true;
    }
}
