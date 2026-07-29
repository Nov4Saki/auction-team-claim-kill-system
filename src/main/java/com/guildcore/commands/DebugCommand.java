package com.guildcore.commands;

import com.guildcore.debug.DebugFlag;
import com.guildcore.debug.DebugManager;
import com.guildcore.util.TextUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DebugCommand implements TabExecutor {

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            if ("debug".startsWith(args[0].toLowerCase())) completions.add("debug");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("debug")) {
            for (String sub : Arrays.asList("toggle", "all")) {
                if (sub.startsWith(args[1].toLowerCase())) completions.add(sub);
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("debug") && args[1].equalsIgnoreCase("toggle")) {
            for (DebugFlag flag : DebugFlag.values()) {
                if (flag.name().toLowerCase().startsWith(args[2].toLowerCase())) completions.add(flag.name());
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("debug") && args[1].equalsIgnoreCase("all")) {
            completions.add("true");
            completions.add("false");
        }
        return completions;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("guildcore.admin")) {
            sender.sendMessage(TextUtil.format("<red>No permission.</red>"));
            return true;
        }

        if (args.length >= 2 && args[0].equalsIgnoreCase("debug") && args[1].equalsIgnoreCase("all")) {
            boolean mode = args.length >= 3 && Boolean.parseBoolean(args[2]);
            DebugManager.setDebugAll(mode);
            sender.sendMessage(TextUtil.format("<green>Debug ALL mode set to: " + mode + "</green>"));
            return true;
        }

        if (args.length >= 3 && args[0].equalsIgnoreCase("debug") && args[1].equalsIgnoreCase("toggle")) {
            try {
                DebugFlag flag = DebugFlag.valueOf(args[2].toUpperCase());
                DebugManager.toggle(flag);
                boolean state = DebugManager.isEnabled(flag);
                sender.sendMessage(TextUtil.format("<green>Toggled debug flag " + flag.name() + ": " + (state ? "<green>ENABLED</green>" : "<red>DISABLED</red>") + "</green>"));
            } catch (IllegalArgumentException e) {
                sender.sendMessage(TextUtil.format("<red>Invalid debug flag name.</red>"));
            }
            return true;
        }

        sender.sendMessage(TextUtil.format("<yellow>Usage: /guildcore debug toggle <flag> | /guildcore debug all <true|false></yellow>"));
        return true;
    }
}
