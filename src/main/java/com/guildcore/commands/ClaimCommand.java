package com.guildcore.commands;

import com.guildcore.claims.ClaimInfo;
import com.guildcore.claims.ClaimManager;
import com.guildcore.claims.ClaimVisualizer;
import com.guildcore.gui.GUIManager;
import com.guildcore.teams.Team;
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

        if (cmd.contains("claim") && !cmd.contains("unclaim")) {
            if (args.length == 1) {
                for (String sub : Arrays.asList("auto", "map", "flags", "border")) {
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

        if (args.length >= 1 && (args[0].equalsIgnoreCase("border") || args[0].equalsIgnoreCase("visual") || args[0].equalsIgnoreCase("visualize"))) {
            visualizer.showBorder(player, chunk);
            player.sendMessage(TextUtil.format("<cyan>✨ Displaying claim border particles for chunk (" + chunk.getX() + ", " + chunk.getZ() + ").</cyan>"));
            return true;
        }

        if (cmd.contains("unclaim")) {
            Team team = guiManager.getTeamManager().getPlayerTeam(player.getUniqueId());
            if (team == null) {
                player.sendMessage(TextUtil.format("<red>✖ You must belong to a Guild to unclaim land!</red>"));
                return true;
            }
            String role = guiManager.getTeamManager().getPlayerRole(player.getUniqueId());
            if (!guiManager.getPermissionManager().hasPermission(team.getId(), role, "CLAIM")) {
                player.sendMessage(TextUtil.format("<red>✖ You do not have team permission to unclaim land!</red>"));
                return true;
            }
            ClaimInfo claim = claimManager.getClaimAt(chunk);
            if (claim == null || !claim.isTeamClaim() || claim.getTeamId() == null || claim.getTeamId() != team.getId()) {
                player.sendMessage(TextUtil.format("<red>✖ This chunk is not claimed by your Guild!</red>"));
                return true;
            }
            if (claimManager.unclaim(chunk)) {
                player.sendMessage(TextUtil.format("<green>✔ Unclaimed chunk (" + chunk.getX() + ", " + chunk.getZ() + ") for your Guild.</green>"));
            } else {
                player.sendMessage(TextUtil.format("<red>✖ Failed to unclaim chunk.</red>"));
            }
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("map") || args[0].equalsIgnoreCase("gui")) {
            guiManager.openTeamMapGUI(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("flags")) {
            guiManager.openClaimFlags(player, chunk);
            return true;
        }

        Team team = guiManager.getTeamManager().getPlayerTeam(player.getUniqueId());
        if (team == null) {
            player.sendMessage(TextUtil.format("<red>✖ You must belong to a Guild/Team to claim land!</red>"));
            return true;
        }

        if (guiManager.attemptClaim(player, team, chunk)) {
            visualizer.showBorder(player, chunk);
        }
        return true;
    }
}
