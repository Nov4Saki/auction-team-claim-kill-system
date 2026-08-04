package com.guildcore.commands;

import com.guildcore.trade.TradeManager;
import com.guildcore.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class TradeCommand implements TabExecutor {
    private final TradeManager tradeManager;

    public TradeCommand(TradeManager tradeManager) {
        this.tradeManager = tradeManager;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            if ("accept".startsWith(args[0].toLowerCase())) completions.add("accept");
            if ("deny".startsWith(args[0].toLowerCase())) completions.add("deny");
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(args[0].toLowerCase())) completions.add(p.getName());
            }
        }
        return completions;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }

        if (args.length < 1) {
            player.sendMessage(TextUtil.format("<red>Usage: /trade <player> | /trade accept</red>"));
            return true;
        }

        if (args[0].equalsIgnoreCase("accept")) {
            tradeManager.acceptTrade(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("deny")) {
            tradeManager.denyTrade(player);
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null || !target.isOnline()) {
            player.sendMessage(TextUtil.format("<red>Player not found online.</red>"));
            return true;
        }

        if (target.equals(player)) {
            player.sendMessage(TextUtil.format("<red>You cannot trade with yourself.</red>"));
            return true;
        }

        tradeManager.requestTrade(player, target);
        return true;
    }
}
