package com.guildcore.commands;

import com.guildcore.debug.DebugFlag;
import com.guildcore.debug.DebugManager;
import com.guildcore.raiditems.RaidItemManager;
import com.guildcore.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DebugCommand implements TabExecutor {

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            for (String sub : Arrays.asList("debug", "giveraid", "setcorehp", "setshield", "info")) {
                if (sub.startsWith(args[0].toLowerCase())) completions.add(sub);
            }
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
        } else if (args.length == 2 && args[0].equalsIgnoreCase("giveraid")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) completions.add(p.getName());
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("giveraid")) {
            for (RaidItemManager.RaidItemType type : RaidItemManager.RaidItemType.values()) {
                if (type.name().toLowerCase().startsWith(args[2].toLowerCase())) completions.add(type.name());
            }
        } else if (args.length == 4 && args[0].equalsIgnoreCase("giveraid")) {
            completions.addAll(Arrays.asList("1", "5", "10", "16", "32", "64"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("setcorehp")) {
            completions.addAll(Arrays.asList("10", "50", "100", "200"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("setshield")) {
            completions.addAll(Arrays.asList("0", "60", "120", "300", "1080"));
        }
        return completions;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("guildcore.admin")) {
            sender.sendMessage(TextUtil.format("<red>No permission.</red>"));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(TextUtil.format("<gold>=== GuildCore Admin Commands ===</gold>"));
            sender.sendMessage(TextUtil.format("<gray>/guildcore debug toggle <flag> - Toggle debug flag</gray>"));
            sender.sendMessage(TextUtil.format("<gray>/guildcore debug all <true|false> - Toggle all debug</gray>"));
            sender.sendMessage(TextUtil.format("<gray>/guildcore giveraid <player> <type> [amount] - Give raid items</gray>"));
            sender.sendMessage(TextUtil.format("<gray>/guildcore setcorehp <team> <hp> - Set core HP</gray>"));
            sender.sendMessage(TextUtil.format("<gray>/guildcore setshield <team> <minutes> - Set shield charge</gray>"));
            sender.sendMessage(TextUtil.format("<gray>/guildcore info - Show system status</gray>"));
            sender.sendMessage(TextUtil.format("<gray>Raid Types: LOCK_PICK_WEAK, LOCK_PICK_NORMAL, LOCK_PICK_FAST, LOCK_PICK_REINFORCED, SLEDGE_HAMMER, RAID_TNT, CHARGED_CREEPER_EGG</gray>"));
            return true;
        }

        if (args[0].equalsIgnoreCase("debug")) {
            return handleDebugSubcommand(sender, args);
        }

        if (args[0].equalsIgnoreCase("giveraid")) {
            return handleGiveRaidSubcommand(sender, args);
        }

        if (args[0].equalsIgnoreCase("setcorehp")) {
            return handleSetCoreHpSubcommand(sender, args);
        }

        if (args[0].equalsIgnoreCase("setshield")) {
            return handleSetShieldSubcommand(sender, args);
        }

        if (args[0].equalsIgnoreCase("info")) {
            return handleInfoSubcommand(sender);
        }

        sender.sendMessage(TextUtil.format("<red>Unknown subcommand. Use /guildcore for help.</red>"));
        return true;
    }

    private boolean handleDebugSubcommand(CommandSender sender, String[] args) {
        if (args.length >= 2 && args[1].equalsIgnoreCase("all")) {
            boolean mode = args.length >= 3 && Boolean.parseBoolean(args[2]);
            DebugManager.setDebugAll(mode);
            sender.sendMessage(TextUtil.format("<green>Debug ALL mode set to: " + mode + "</green>"));
            return true;
        }

        if (args.length >= 3 && args[1].equalsIgnoreCase("toggle")) {
            try {
                DebugFlag flag = DebugFlag.valueOf(args[2].toUpperCase());
                DebugManager.toggle(flag);
                boolean state = DebugManager.isEnabled(flag);
                sender.sendMessage(TextUtil.format("<green>Toggled debug flag " + flag.name() + ": " +
                        (state ? "<green>ENABLED</green>" : "<red>DISABLED</red>") + "</green>"));
            } catch (IllegalArgumentException e) {
                sender.sendMessage(TextUtil.format("<red>Invalid debug flag name. Valid flags:</red>"));
                for (DebugFlag flag : DebugFlag.values()) {
                    sender.sendMessage(TextUtil.format("<gray>  - " + flag.name() + "</gray>"));
                }
            }
            return true;
        }

        sender.sendMessage(TextUtil.format("<yellow>Usage: /guildcore debug toggle <flag> | /guildcore debug all <true|false></yellow>"));
        return true;
    }

    private boolean handleGiveRaidSubcommand(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(TextUtil.format("<red>Usage: /guildcore giveraid <player> <type> [amount]</red>"));
            sender.sendMessage(TextUtil.format("<gray>Types: LOCK_PICK_WEAK, LOCK_PICK_NORMAL, LOCK_PICK_FAST, LOCK_PICK_REINFORCED, SLEDGE_HAMMER, RAID_TNT, CHARGED_CREEPER_EGG</gray>"));
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null || !target.isOnline()) {
            sender.sendMessage(TextUtil.format("<red>Player not found online.</red>"));
            return true;
        }

        RaidItemManager.RaidItemType type;
        try {
            type = RaidItemManager.RaidItemType.valueOf(args[2].toUpperCase());
        } catch (IllegalArgumentException e) {
            sender.sendMessage(TextUtil.format("<red>Invalid raid item type: " + args[2] + "</red>"));
            return true;
        }

        int amount = 1;
        if (args.length >= 4) {
            try {
                amount = Integer.parseInt(args[3]);
                if (amount < 1) amount = 1;
                if (amount > 64) amount = 64;
            } catch (NumberFormatException e) {
                sender.sendMessage(TextUtil.format("<red>Invalid amount.</red>"));
                return true;
            }
        }

        com.guildcore.GuildCorePlugin plugin = com.guildcore.GuildCorePlugin.getInstance();
        if (plugin == null) {
            sender.sendMessage(TextUtil.format("<red>Plugin instance not available.</red>"));
            return true;
        }

        RaidItemManager manager = new RaidItemManager(plugin.getSettingsManager());
        target.getInventory().addItem(manager.createItem(type, amount));
        sender.sendMessage(TextUtil.format("<green>Gave " + amount + "x " + type.name() + " to " + target.getName() + "!</green>"));
        target.sendMessage(TextUtil.format("<green>You received " + amount + "x " + type.name() + " from an admin!</green>"));
        return true;
    }

    private boolean handleSetCoreHpSubcommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(TextUtil.format("<red>Usage: /guildcore setcorehp <hp> - Set HP of the core you're looking at</red>"));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(TextUtil.format("<red>Players only.</red>"));
            return true;
        }

        try {
            int hp = Integer.parseInt(args[1]);
            if (hp < 0) hp = 0;

            com.guildcore.GuildCorePlugin plugin = com.guildcore.GuildCorePlugin.getInstance();
            if (plugin == null) {
                sender.sendMessage(TextUtil.format("<red>Plugin instance not available.</red>"));
                return true;
            }

            // Find core the player is looking at
            var targetBlock = player.getTargetBlockExact(10);
            if (targetBlock == null) {
                sender.sendMessage(TextUtil.format("<red>Look at a Guild Core to set its HP.</red>"));
                return true;
            }

            var core = plugin.getGuildCoreManager().getCoreAtLocation(targetBlock.getLocation());
            if (core == null) {
                // Also check one block above (armor stand position)
                core = plugin.getGuildCoreManager().getCoreAtLocation(targetBlock.getLocation().add(0, 1, 0));
            }
            if (core == null) {
                // Check one block below
                core = plugin.getGuildCoreManager().getCoreAtLocation(targetBlock.getLocation().add(0, -1, 0));
            }

            if (core == null) {
                sender.sendMessage(TextUtil.format("<red>No Guild Core found at that location.</red>"));
                return true;
            }

            core.setCurrentHp(hp);
            if (hp > core.getMaxHp()) {
                core.setMaxHp(hp);
            }

            plugin.getGuildCoreManager().refreshAllCoreDisplays();
            sender.sendMessage(TextUtil.format("<green>Set core HP for team " + core.getTeamId() + " to " + hp + ".</green>"));

            if (hp <= 0) {
                sender.sendMessage(TextUtil.format("<yellow>Core HP set to 0. It will be destroyed on next update.</yellow>"));
            }

        } catch (NumberFormatException e) {
            sender.sendMessage(TextUtil.format("<red>Invalid HP value.</red>"));
        }
        return true;
    }

    private boolean handleSetShieldSubcommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(TextUtil.format("<red>Usage: /guildcore setshield <team_name> <minutes></red>"));
            return true;
        }

        if (!(sender instanceof Player player) && args.length < 2) {
            sender.sendMessage(TextUtil.format("<red>Specify a team name.</red>"));
            return true;
        }

        try {
            String teamIdentifier = args[1];
            double minutes = args.length >= 3 ? Double.parseDouble(args[2]) : 0;

            com.guildcore.GuildCorePlugin plugin = com.guildcore.GuildCorePlugin.getInstance();
            if (plugin == null) {
                sender.sendMessage(TextUtil.format("<red>Plugin instance not available.</red>"));
                return true;
            }

            com.guildcore.teams.Team team = null;

            // Try parsing as team ID
            try {
                int teamId = Integer.parseInt(teamIdentifier);
                team = plugin.getTeamManager().getTeam(teamId);
            } catch (NumberFormatException e) {
                // Try as team name
                team = plugin.getTeamManager().getTeamByName(teamIdentifier);
            }

            // If sender is a player and no team specified, use their team
            if (team == null && sender instanceof Player p) {
                team = plugin.getTeamManager().getPlayerTeam(p.getUniqueId());
                if (team != null && args.length >= 2) {
                    try {
                        minutes = Double.parseDouble(args[1]);
                    } catch (NumberFormatException ignored) {}
                }
            }

            if (team == null) {
                sender.sendMessage(TextUtil.format("<red>Team not found.</red>"));
                return true;
            }

            plugin.getOfflineShieldManager().setShieldCharge(team.getId(), minutes);
            sender.sendMessage(TextUtil.format("<green>Set shield charge for team " + team.getName() +
                    " to " + String.format("%.1f", minutes) + " minutes.</green>"));

        } catch (NumberFormatException e) {
            sender.sendMessage(TextUtil.format("<red>Invalid minutes value.</red>"));
        }
        return true;
    }

    private boolean handleInfoSubcommand(CommandSender sender) {
        com.guildcore.GuildCorePlugin plugin = com.guildcore.GuildCorePlugin.getInstance();
        if (plugin == null) {
            sender.sendMessage(TextUtil.format("<red>Plugin instance not available.</red>"));
            return true;
        }

        sender.sendMessage(TextUtil.format("<gold>=== GuildCore v6 System Status ===</gold>"));
        sender.sendMessage(TextUtil.format("<yellow>Debug Mode: " + (DebugManager.isDebugAll() ? "<green>ALL ON</green>" : "<gray>Selective</gray>") + "</yellow>"));

        int teamsCount = plugin.getTeamManager().getAllTeams().size();
        sender.sendMessage(TextUtil.format("<yellow>Active Teams: <white>" + teamsCount + "</white></yellow>"));

        int coresCount = plugin.getGuildCoreManager().getAllCores().size();
        sender.sendMessage(TextUtil.format("<yellow>Placed Guild Cores: <white>" + coresCount + "</white></yellow>"));

        if (sender instanceof Player player) {
            var team = plugin.getTeamManager().getPlayerTeam(player.getUniqueId());
            if (team != null) {
                sender.sendMessage(TextUtil.format("<yellow>Your Team: <white>" + team.getName() + " (ID: " + team.getId() + ")</white></yellow>"));

                var core = plugin.getGuildCoreManager().getCoreForTeam(team.getId());
                if (core != null) {
                    sender.sendMessage(TextUtil.format("<yellow>Your Core: <white>Tier " + core.getTier() +
                            " | HP: " + core.getCurrentHp() + "/" + core.getMaxHp() + "</white></yellow>"));
                } else {
                    sender.sendMessage(TextUtil.format("<yellow>Your Core: <red>Not placed</red></yellow>"));
                }

                var shieldInfo = plugin.getOfflineShieldManager().getShieldInfo(team.getId());
                sender.sendMessage(TextUtil.format("<yellow>Shield: " +
                        (Boolean.TRUE.equals(shieldInfo.get("active")) ? "<red>ACTIVE</red>" : "<green>Inactive</green>") +
                        " | Charge: <white>" + shieldInfo.get("chargeFormatted") + "</white></yellow>"));
            }
        }

        return true;
    }
}