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

        if (args.length == 0 || args[0].equalsIgnoreCase("map") || args[0].equalsIgnoreCase("gui")) {
            guiManager.openTeamMapGUI(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("flags")) {
            guiManager.openClaimFlags(player, chunk);
            return true;
        }

        // Handle direct chunk claim for Team
        com.guildcore.teams.Team team = guiManager.getTeamManager().getPlayerTeam(player.getUniqueId());
        if (team == null) {
            player.sendMessage(TextUtil.format("<red>✖ You must belong to a Guild/Team to claim land!</red>"));
            return true;
        }

        String role = guiManager.getTeamManager().getPlayerRole(player.getUniqueId());
        if (!guiManager.getPermissionManager().hasPermission(team.getId(), role, "CLAIM")) {
            player.sendMessage(TextUtil.format("<red>✖ You do not have team permission to claim land!</red>"));
            return true;
        }

        if (claimManager.isClaimed(chunk)) {
            player.sendMessage(TextUtil.format("<red>✖ This chunk is already claimed!</red>"));
            return true;
        }

        int currentClaims = claimManager.getTeamClaimsCount(team.getId());
        if (currentClaims >= team.getMaxClaims()) {
            player.sendMessage(TextUtil.format("<red>✖ Team claim capacity reached (" + currentClaims + "/" + team.getMaxClaims() + ")!</red>"));
            return true;
        }

        GUIManager.ClaimCostResult costRes = guiManager.calculateClaimCost(currentClaims);
        long costCoins = costRes.coins;
        int costXpLevels = costRes.xpLevels;
        org.bukkit.Material costItemMat = costRes.itemMat;
        int costItemAmount = costRes.itemAmount;

        if (team.getBankBalance() < costCoins) {
            player.sendMessage(TextUtil.format("<red>✖ Team Bank lacks funds! Required: $" + String.format("%,d", costCoins) + " Gold (Bank balance: $" + String.format("%,d", team.getBankBalance()) + ").</red>"));
            return true;
        }

        if (player.getLevel() < costXpLevels) {
            player.sendMessage(TextUtil.format("<red>✖ You need at least " + costXpLevels + " XP Levels to claim this chunk!</red>"));
            return true;
        }

        if (costItemAmount > 0 && !player.getInventory().containsAtLeast(new org.bukkit.inventory.ItemStack(costItemMat), costItemAmount)) {
            player.sendMessage(TextUtil.format("<red>✖ You need " + costItemAmount + "x " + costItemMat.name() + " in your inventory to claim this chunk!</red>"));
            return true;
        }

        if (claimManager.createTeamClaim(player.getUniqueId(), team.getId(), chunk)) {
            if (costCoins > 0) {
                team.setBankBalance(team.getBankBalance() - costCoins);
            }
            if (costXpLevels > 0) {
                player.setLevel(player.getLevel() - costXpLevels);
            }
            if (costItemAmount > 0) {
                player.getInventory().removeItem(new org.bukkit.inventory.ItemStack(costItemMat, costItemAmount));
            }
            visualizer.showBorder(player, chunk);
            player.sendMessage(TextUtil.format("<green>✔ Successfully claimed chunk (" + chunk.getX() + ", " + chunk.getZ() + ") for your Guild!</green>"));
        } else {
            player.sendMessage(TextUtil.format("<red>✖ Failed to claim chunk!</red>"));
        }
        return true;
    }
}
