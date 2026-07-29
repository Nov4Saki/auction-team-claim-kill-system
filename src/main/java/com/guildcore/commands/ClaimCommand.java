package com.guildcore.commands;

import com.guildcore.claims.ClaimManager;
import com.guildcore.claims.ClaimVisualizer;
import com.guildcore.gui.GUIManager;
import com.guildcore.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ClaimCommand implements TabExecutor {
    private final ClaimManager claimManager;
    private final ClaimVisualizer visualizer;
    private final GUIManager guiManager;

    public ClaimCommand(ClaimManager claimManager, ClaimVisualizer visualizer, GUIManager guiManager) {
        this.claimManager = claimManager;
        this.visualizer = visualizer;
        this.guiManager = guiManager;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        String cmd = alias.toLowerCase();

        if (cmd.contains("trust") && !cmd.contains("untrust")) {
            if (args.length == 1) {
                for (String level : Arrays.asList("access", "container", "build", "manager")) {
                    if (level.startsWith(args[0].toLowerCase())) completions.add(level);
                }
            } else if (args.length == 2) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) completions.add(p.getName());
                }
            }
        } else if (cmd.contains("untrust")) {
            if (args.length == 1) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(args[0].toLowerCase())) completions.add(p.getName());
                }
            }
        } else if (cmd.contains("claim") && !cmd.contains("unclaim")) {
            if (args.length == 1) {
                for (String sub : Arrays.asList("auto", "map", "flags")) {
                    if (sub.startsWith(args[0].toLowerCase())) completions.add(sub);
                }
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

        Chunk chunk = player.getLocation().getChunk();
        String cmd = label.toLowerCase();

        if (cmd.contains("unclaim")) {
            if (claimManager.unclaim(chunk)) {
                player.sendMessage(TextUtil.format("<green>Unclaimed this chunk.</green>"));
            } else {
                player.sendMessage(TextUtil.format("<red>This chunk is not claimed or you do not own it.</red>"));
            }
            return true;
        }

        if (cmd.contains("untrust")) {
            if (args.length < 1) {
                player.sendMessage(TextUtil.format("<red>Usage: /gcuntrust <player></red>"));
                return true;
            }
            Player target = Bukkit.getPlayer(args[0]);
            if (target != null) {
                claimManager.setTrust(chunk, target.getUniqueId(), "NONE");
                player.sendMessage(TextUtil.format("<green>Removed trust from " + target.getName() + ".</green>"));
            }
            return true;
        }

        if (cmd.contains("trust")) {
            if (args.length < 2) {
                player.sendMessage(TextUtil.format("<red>Usage: /gctrust <access|container|build|manager> <player></red>"));
                return true;
            }
            String level = args[0].toUpperCase();
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                player.sendMessage(TextUtil.format("<red>Player not found.</red>"));
                return true;
            }
            claimManager.setTrust(chunk, target.getUniqueId(), level);
            player.sendMessage(TextUtil.format("<green>Granted " + level + " trust to " + target.getName() + " in this chunk.</green>"));
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("map")) {
            visualizer.sendAsciiMap(player);
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("flags")) {
            guiManager.openClaimFlags(player, chunk);
            return true;
        }

        if (claimManager.createPersonalClaim(player, chunk)) {
            visualizer.showBorder(player, chunk);
            player.sendMessage(TextUtil.format("<green>Successfully claimed full chunk (" + chunk.getX() + ", " + chunk.getZ() + ")!</green>"));
        } else {
            player.sendMessage(TextUtil.format("<red>This chunk is already claimed!</red>"));
        }
        return true;
    }
}
