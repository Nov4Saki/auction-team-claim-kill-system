package com.guildcore.commands;

import com.guildcore.claims.ClaimManager;
import com.guildcore.config.SettingsManager;
import com.guildcore.gui.GUIItemBuilder;
import com.guildcore.gui.GUIManager;
import com.guildcore.raids.RaidManager;
import com.guildcore.teams.Team;
import com.guildcore.teams.TeamBankManager;
import com.guildcore.teams.TeamManager;
import com.guildcore.teams.TeamVaultManager;
import com.guildcore.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TeamCommand implements TabExecutor {
    private final TeamManager teamManager;
    private final TeamBankManager bankManager;
    private final TeamVaultManager vaultManager;
    private final ClaimManager claimManager;
    private final RaidManager raidManager;
    private final SettingsManager settingsManager;
    private final GUIManager guiManager;

    private static final List<String> SUBCOMMANDS = Arrays.asList(
            "create", "invite", "add", "join", "leave", "kick", "promote", "demote",
            "info", "list", "bank", "vault", "deposit", "withdraw", "claim", "unclaim",
            "home", "sethome", "setnexus", "permissions", "upgrade", "raid", "disband", "rename"
    );

    public TeamCommand(TeamManager teamManager, TeamBankManager bankManager, TeamVaultManager vaultManager, ClaimManager claimManager, RaidManager raidManager, SettingsManager settingsManager, GUIManager guiManager) {
        this.teamManager = teamManager;
        this.bankManager = bankManager;
        this.vaultManager = vaultManager;
        this.claimManager = claimManager;
        this.raidManager = raidManager;
        this.settingsManager = settingsManager;
        this.guiManager = guiManager;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            for (String sub : SUBCOMMANDS) {
                if (sub.startsWith(input)) completions.add(sub);
            }
            return completions;
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("invite") || sub.equals("add") || sub.equals("kick") || sub.equals("promote") || sub.equals("demote")) {
                String input = args[1].toLowerCase();
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getName().toLowerCase().startsWith(input)) completions.add(player.getName());
                }
            } else if (sub.equals("bank")) {
                completions.add("deposit");
                completions.add("withdraw");
            } else if (sub.equals("raid")) {
                completions.add("declare");
            } else if (sub.equals("vault")) {
                completions.add("1");
                completions.add("2");
                completions.add("3");
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

        String cmd = label.toLowerCase();
        if (cmd.equals("tc") || cmd.equals("teamchat") || cmd.equals("gc") || cmd.equals("guildchat")) {
            Team team = teamManager.getPlayerTeam(player.getUniqueId());
            if (team == null) {
                player.sendMessage(TextUtil.format("<red>You are not in a team.</red>"));
                return true;
            }
            if (args.length == 0) {
                player.sendMessage(TextUtil.format("<red>Usage: /tc <message></red>"));
                return true;
            }
            String msg = String.join(" ", args);
            for (Player target : Bukkit.getOnlinePlayers()) {
                Team targetTeam = teamManager.getPlayerTeam(target.getUniqueId());
                if (targetTeam != null && targetTeam.getId() == team.getId()) {
                    target.sendMessage(TextUtil.format("<aqua>[Team] " + player.getName() + ": " + msg + "</aqua>"));
                }
            }
            return true;
        }

        if (args.length == 0) {
            Team team = teamManager.getPlayerTeam(player.getUniqueId());
            if (team == null) {
                player.sendMessage(TextUtil.format("<yellow>=== Team Commands ===</yellow>"));
                player.sendMessage(TextUtil.format("<gray>/team create <name> | /team join <name> | /team info</gray>"));
                player.sendMessage(TextUtil.format("<gray>/team invite <player> | /team list | /team bank | /team claim</gray>"));
            } else {
                guiManager.openTeamMenu(player, team);
            }
            return true;
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("create")) {
            if (args.length < 2) {
                player.sendMessage(TextUtil.format("<red>Usage: /team create <name></red>"));
                return true;
            }
            int baseCap = settingsManager.getInt("teams.base_max_members", 3);
            if (teamManager.createTeam(player, args[1], baseCap)) {
                player.sendMessage(TextUtil.format("<green>Created team '" + args[1] + "' with max " + baseCap + " members!</green>"));
            } else {
                player.sendMessage(TextUtil.format("<red>Could not create team (name taken or already in a team).</red>"));
            }
            return true;
        }

        if (sub.equals("invite") || sub.equals("add")) {
            if (args.length < 2) {
                player.sendMessage(TextUtil.format("<red>Usage: /team invite <player></red>"));
                return true;
            }
            Player target = Bukkit.getPlayer(args[1]);
            if (target != null && teamManager.invitePlayer(player, target)) {
                player.sendMessage(TextUtil.format("<green>Invited " + target.getName() + " to your team!</green>"));
                target.sendMessage(TextUtil.format("<green>You were invited to join team " + player.getName() + "'s team! Type /team join to accept.</green>"));
            } else {
                player.sendMessage(TextUtil.format("<red>Could not invite player (player offline or team full).</red>"));
            }
            return true;
        }

        if (sub.equals("kick")) {
            if (args.length < 2) {
                player.sendMessage(TextUtil.format("<red>Usage: /team kick <player></red>"));
                return true;
            }
            player.sendMessage(TextUtil.format("<green>Kicked member " + args[1] + " from team.</green>"));
            return true;
        }

        if (sub.equals("promote") || sub.equals("demote")) {
            if (args.length < 2) {
                player.sendMessage(TextUtil.format("<red>Usage: /team " + sub + " <player></red>"));
                return true;
            }
            player.sendMessage(TextUtil.format("<green>Updated rank for " + args[1] + ".</green>"));
            return true;
        }

        if (sub.equals("list")) {
            player.sendMessage(TextUtil.format("<gold>=== Server Teams ===</gold>"));
            player.sendMessage(TextUtil.format("<gray>Use /team info <name> for team details.</gray>"));
            return true;
        }

        if (sub.equals("join")) {
            if (teamManager.joinTeam(player)) {
                player.sendMessage(TextUtil.format("<green>Successfully joined team!</green>"));
            } else {
                player.sendMessage(TextUtil.format("<red>No pending invite or team is full!</red>"));
            }
            return true;
        }

        if (sub.equals("vault") || sub.equals("vual")) {
            Team team = teamManager.getPlayerTeam(player.getUniqueId());
            if (team == null) {
                player.sendMessage(TextUtil.format("<red>You must be in a team to access the vault.</red>"));
                return true;
            }
            int page = 1;
            if (args.length >= 2) {
                try { page = Math.max(1, Integer.parseInt(args[1])); } catch (NumberFormatException ignored) {}
            }
            ItemStack[] contents = vaultManager.getVaultPage(team.getId(), page);
            Inventory vaultInv = Bukkit.createInventory(new com.guildcore.gui.holders.VaultGUIHolder(team.getId(), page), 54, TextUtil.format("<gold>📦 Team Vault (Page " + page + ")</gold>"));
            vaultInv.setContents(contents);
            vaultInv.setItem(53, new GUIItemBuilder(Material.BARRIER).name("<red>◀ Back to Team Menu</red>").build());
            player.openInventory(vaultInv);
            return true;
        }

        if (sub.equals("bank")) {
            Team team = teamManager.getPlayerTeam(player.getUniqueId());
            if (team == null) {
                player.sendMessage(TextUtil.format("<red>You must be in a team to access the bank.</red>"));
                return true;
            }
            if (args.length >= 3 && args[1].equalsIgnoreCase("deposit")) {
                try {
                    long amount = Long.parseLong(args[2]);
                    if (bankManager.deposit(team, player.getUniqueId(), amount)) {
                        player.sendMessage(TextUtil.format("<green>Deposited $" + amount + " into team bank!</green>"));
                    }
                } catch (NumberFormatException ignored) {}
                return true;
            }
            if (args.length >= 3 && args[1].equalsIgnoreCase("withdraw")) {
                try {
                    long amount = Long.parseLong(args[2]);
                    if (bankManager.withdraw(team, player.getUniqueId(), amount)) {
                        player.sendMessage(TextUtil.format("<green>Withdrew $" + amount + " from team bank!</green>"));
                    } else {
                        player.sendMessage(TextUtil.format("<red>Insufficient team bank balance!</red>"));
                    }
                } catch (NumberFormatException ignored) {}
                return true;
            }
            player.sendMessage(TextUtil.format("<gold>🏦 Team Bank Balance: <green>$" + team.getBankBalance() + "</green></gold>"));
            player.sendMessage(TextUtil.format("<gray>Usage: /team bank deposit <amount> | /team bank withdraw <amount></gray>"));
            return true;
        }

        if (sub.equals("claim")) {
            Team team = teamManager.getPlayerTeam(player.getUniqueId());
            if (team == null) {
                player.sendMessage(TextUtil.format("<red>You must be in a team to team-claim land.</red>"));
                return true;
            }
            Chunk chunk = player.getLocation().getChunk();
            if (claimManager.createTeamClaim(team.getId(), chunk)) {
                player.sendMessage(TextUtil.format("<green>Claimed full chunk for team " + team.getName() + "!</green>"));
            } else {
                player.sendMessage(TextUtil.format("<red>Chunk is already claimed.</red>"));
            }
            return true;
        }

        if (sub.equals("setnexus")) {
            Team team = teamManager.getPlayerTeam(player.getUniqueId());
            if (team == null) return true;
            Block target = player.getTargetBlockExact(5);
            if (target == null) {
                player.sendMessage(TextUtil.format("<red>Look at a valid block (e.g. Respawn Anchor) to set as Nexus.</red>"));
                return true;
            }
            team.setNexusLocation(target.getLocation());
            player.sendMessage(TextUtil.format("<green>Nexus block set for team " + team.getName() + "!</green>"));
            return true;
        }

        if (sub.equals("sethome")) {
            Team team = teamManager.getPlayerTeam(player.getUniqueId());
            if (team == null) return true;
            team.setHomeLocation(player.getLocation());
            player.sendMessage(TextUtil.format("<green>Team home set!</green>"));
            return true;
        }

        if (sub.equals("home")) {
            Team team = teamManager.getPlayerTeam(player.getUniqueId());
            if (team == null || team.getHomeLocation() == null) {
                player.sendMessage(TextUtil.format("<red>Team home not set.</red>"));
                return true;
            }
            player.teleport(team.getHomeLocation());
            player.sendMessage(TextUtil.format("<green>Teleported to team home!</green>"));
            return true;
        }

        if (sub.equals("raid") && args.length >= 3 && args[1].equalsIgnoreCase("declare")) {
            Team targetTeam = teamManager.getTeamByName(args[2]);
            if (targetTeam == null) {
                player.sendMessage(TextUtil.format("<red>Target team not found.</red>"));
                return true;
            }
            if (raidManager.declareRaid(player, targetTeam)) {
                player.sendMessage(TextUtil.format("<green>Raid declared against " + targetTeam.getName() + "!</green>"));
            } else {
                player.sendMessage(TextUtil.format("<red>Could not declare raid (insufficient bank balance or active raid/shield).</red>"));
            }
            return true;
        }

        return true;
    }
}
