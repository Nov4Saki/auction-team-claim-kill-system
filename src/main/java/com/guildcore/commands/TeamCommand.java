package com.guildcore.commands;

import com.guildcore.claims.ClaimManager;
import com.guildcore.config.SettingsManager;
import com.guildcore.gui.GUIItemBuilder;
import com.guildcore.gui.GUIManager;
import com.guildcore.core.GuildCoreManager;
import com.guildcore.teams.Team;
import com.guildcore.teams.TeamBankManager;
import com.guildcore.teams.TeamManager;
import com.guildcore.teams.TeamVaultManager;
import com.guildcore.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TeamCommand implements TabExecutor {
    private final TeamManager teamManager;
    private final TeamBankManager bankManager;
    private final TeamVaultManager vaultManager;
    private final ClaimManager claimManager;
    private final GuildCoreManager guildCoreManager;
    private final SettingsManager settingsManager;
    private final GUIManager guiManager;
    private final Map<UUID, Long> guildHomeCooldowns = new ConcurrentHashMap<>();

    private static final List<String> SUBCOMMANDS = Arrays.asList(
            "create", "invite", "add", "join", "deny", "leave", "kick", "promote", "demote",
            "info", "list", "bank", "vault", "deposit", "withdraw", "claim", "unclaim", "map", "members", "roster",
            "home", "sethome", "placecore", "permissions", "upgrade", "disband", "rename", "transferleader", "leader"
    );

    public TeamCommand(TeamManager teamManager, TeamBankManager bankManager, TeamVaultManager vaultManager, ClaimManager claimManager, GuildCoreManager guildCoreManager, SettingsManager settingsManager, GUIManager guiManager) {
        this.teamManager = teamManager;
        this.bankManager = bankManager;
        this.vaultManager = vaultManager;
        this.claimManager = claimManager;
        this.guildCoreManager = guildCoreManager;
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
            int recipients = 0;
            for (Player target : Bukkit.getOnlinePlayers()) {
                Team targetTeam = teamManager.getPlayerTeam(target.getUniqueId());
                if (targetTeam != null && targetTeam.getId() == team.getId()) {
                    target.sendMessage(TextUtil.format("<aqua>[Team] " + player.getName() + ": " + msg + "</aqua>"));
                    if (!target.equals(player)) recipients++;
                }
            }
            if (recipients == 0) {
                player.sendMessage(TextUtil.format("<yellow>No other team members online.</yellow>"));
            }
            return true;
        }

        if (args.length == 0) {
            Team team = teamManager.getPlayerTeam(player.getUniqueId());
            if (team == null) {
                player.sendMessage(TextUtil.format("<yellow>=== Team Commands ===</yellow>"));
                player.sendMessage(TextUtil.format("<gray>/team create <name> | /team join | /team info</gray>"));
                player.sendMessage(TextUtil.format("<gray>/team invite <player> | /team list | /team bank | /team claim</gray>"));
            } else {
                guiManager.openTeamMenu(player, team);
            }
            return true;
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("info")) {
            Team team = null;
            if (args.length >= 2) {
                team = teamManager.getTeamByName(args[1]);
                if (team == null) {
                    player.sendMessage(TextUtil.format("<red>Team '" + args[1] + "' not found!</red>"));
                    return true;
                }
            } else {
                team = teamManager.getPlayerTeam(player.getUniqueId());
                if (team == null) {
                    player.sendMessage(TextUtil.format("<red>You are not in a team! Usage: /team info <team_name></red>"));
                    return true;
                }
            }
            OfflinePlayer leaderOp = Bukkit.getOfflinePlayer(team.getLeaderUuid());
            String leaderName = leaderOp.getName() != null ? leaderOp.getName() : team.getLeaderUuid().toString();
            int claimCount = claimManager.getTeamClaimsCount(team.getId());
            player.sendMessage(TextUtil.format("<gold>=== Guild Info: <yellow>" + team.getName() + "</yellow> ===</gold>"));
            player.sendMessage(TextUtil.format("<yellow>👑 Guild Leader: <white>" + leaderName + "</white></yellow>"));
            player.sendMessage(TextUtil.format("<yellow>⭐ Guild Level: <green>" + team.getLevel() + "</green> | Max Members: <green>" + team.getMaxMembers() + "</green></yellow>"));
            player.sendMessage(TextUtil.format("<yellow>💰 Bank Balance: <gold>$" + String.format("%,d", team.getBankBalance()) + "</gold></yellow>"));
            player.sendMessage(TextUtil.format("<yellow>🗺 Claimed Chunks: <aqua>" + claimCount + "</aqua></yellow>"));
            return true;
        }

        if (sub.equals("upgrade")) {
            Team team = teamManager.getPlayerTeam(player.getUniqueId());
            if (team == null) {
                player.sendMessage(TextUtil.format("<red>You must be in a team to upgrade features.</red>"));
                return true;
            }
            guiManager.openTeamUpgrades(player, team);
            return true;
        }

        if (sub.equals("rename")) {
            if (args.length < 2) {
                player.sendMessage(TextUtil.format("<red>Usage: /team rename <new_name></red>"));
                return true;
            }
            teamManager.renameTeam(player, args[1]);
            return true;
        }

        if (sub.equals("create")) {
            if (args.length < 2) {
                player.sendMessage(TextUtil.format("<red>Usage: /team create <name></red>"));
                return true;
            }
            if (teamManager.getPlayerTeam(player.getUniqueId()) != null) {
                player.sendMessage(TextUtil.format("<red>You are already in a team!</red>"));
                return true;
            }
            if (teamManager.getTeamByName(args[1]) != null) {
                player.sendMessage(TextUtil.format("<red>Team name '" + args[1] + "' is already taken!</red>"));
                return true;
            }
            int baseCap = settingsManager.getInt("teams.base_max_members", 3);
            if (teamManager.createTeam(player, args[1], baseCap)) {
                player.sendMessage(TextUtil.format("<green>Created team '" + args[1] + "' with max " + baseCap + " members!</green>"));
            } else {
                player.sendMessage(TextUtil.format("<red>Could not create team.</red>"));
            }
            return true;
        }

        if (sub.equals("invite") || sub.equals("add")) {
            if (args.length < 2) {
                player.sendMessage(TextUtil.format("<red>Usage: /team invite <player></red>"));
                return true;
            }
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null || !target.isOnline()) {
                player.sendMessage(TextUtil.format("<red>Player not found online.</red>"));
                return true;
            }
            if (teamManager.getPlayerTeam(target.getUniqueId()) != null) {
                player.sendMessage(TextUtil.format("<red>Player " + target.getName() + " is already in a team!</red>"));
                return true;
            }
            if (teamManager.invitePlayer(player, target)) {
                player.sendMessage(TextUtil.format("<green>Invited " + target.getName() + " to your team!</green>"));
            } else {
                player.sendMessage(TextUtil.format("<red>Could not invite player (team full or insufficient permission).</red>"));
            }
            return true;
        }

        if (sub.equals("kick")) {
            if (args.length < 2) {
                player.sendMessage(TextUtil.format("<red>Usage: /team kick <player></red>"));
                return true;
            }
            Team team = teamManager.getPlayerTeam(player.getUniqueId());
            if (team == null) {
                player.sendMessage(TextUtil.format("<red>You are not in a team!</red>"));
                return true;
            }
            if (!teamManager.kickPlayer(player, args[1])) {
                player.sendMessage(TextUtil.format("<red>Could not kick member '" + args[1] + "' (player not in your team or insufficient rank).</red>"));
            }
            return true;
        }

        if (sub.equals("promote")) {
            if (args.length < 2) {
                player.sendMessage(TextUtil.format("<red>Usage: /team promote <player></red>"));
                return true;
            }
            teamManager.promotePlayer(player, args[1]);
            return true;
        }

        if (sub.equals("demote")) {
            if (args.length < 2) {
                player.sendMessage(TextUtil.format("<red>Usage: /team demote <player></red>"));
                return true;
            }
            teamManager.demotePlayer(player, args[1]);
            return true;
        }

        if (sub.equals("leave")) {
            if (teamManager.leaveTeam(player)) {
                player.sendMessage(TextUtil.format("<yellow>⚠️ You left your team. Note: You have lost access to team vault, bank, and claims.</yellow>"));
            }
            return true;
        }

        if (sub.equals("disband")) {
            Team team = teamManager.getPlayerTeam(player.getUniqueId());
            if (team == null) {
                player.sendMessage(TextUtil.format("<red>You are not in a team.</red>"));
                return true;
            }
            if (!team.getLeaderUuid().equals(player.getUniqueId())) {
                player.sendMessage(TextUtil.format("<red>Only the Guild Leader can disband the guild!</red>"));
                return true;
            }
            player.sendMessage(TextUtil.format("<red><b>⚠️ WARNING: Disbanding your guild will permanently wipe your Team Bank balance, Vault items, and unclaim all territory land! Please confirm below:</b></red>"));
            guiManager.openTeamDisbandConfirmGUI(player, team);
            return true;
        }

        if (sub.equals("permissions") || sub.equals("perms")) {
            Team team = teamManager.getPlayerTeam(player.getUniqueId());
            if (team == null) {
                player.sendMessage(TextUtil.format("<red>You must be in a team to inspect permissions.</red>"));
                return true;
            }
            guiManager.openTeamPermissions(player, team, "MEMBER");
            return true;
        }

        if (sub.equals("list")) {
            var teams = teamManager.getAllTeams();
            if (teams.isEmpty()) {
                player.sendMessage(TextUtil.format("<yellow>No teams currently exist on the server.</yellow>"));
                return true;
            }
            player.sendMessage(TextUtil.format("<gold>=== Server Teams (" + teams.size() + ") ===</gold>"));
            for (Team t : teams) {
                player.sendMessage(TextUtil.format("<yellow>• <gold>" + t.getName() + "</gold> (Level " + t.getLevel() + ") - Max: " + t.getMaxMembers() + " members</yellow>"));
            }
            return true;
        }

        if (sub.equals("join")) {
            if (!teamManager.hasPendingInvite(player)) {
                player.sendMessage(TextUtil.format("<red>You have no pending team invite!</red>"));
                return true;
            }
            if (teamManager.joinTeam(player)) {
                player.sendMessage(TextUtil.format("<green>Successfully joined team!</green>"));
            } else {
                player.sendMessage(TextUtil.format("<red>Could not join team (team is full)!</red>"));
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
            guiManager.openTeamVault(player, team, page);
            return true;
        }

        if (sub.equals("transferleader") || sub.equals("leader")) {
            Team team = teamManager.getPlayerTeam(player.getUniqueId());
            if (team == null || !team.getLeaderUuid().equals(player.getUniqueId())) {
                player.sendMessage(TextUtil.format("<red>✖ Only the Guild Leader can transfer leadership!</red>"));
                return true;
            }
            if (args.length < 2) {
                player.sendMessage(TextUtil.format("<red>Usage: /team transferleader <player></red>"));
                return true;
            }

            boolean allowOffline = settingsManager.getBoolean("teams.transfer_leader_allow_offline", true);
            String targetName = args[1];
            OfflinePlayer targetOp = null;

            Player onlineTarget = Bukkit.getPlayer(targetName);
            if (onlineTarget != null) {
                targetOp = onlineTarget;
            } else if (allowOffline) {
                UUID u = teamManager.findTeamMemberUuid(team.getId(), targetName);
                if (u != null) {
                    targetOp = Bukkit.getOfflinePlayer(u);
                }
            }

            if (targetOp == null) {
                if (!allowOffline) {
                    player.sendMessage(TextUtil.format("<red>✖ Offline leadership transfer is disabled in Admin Settings! Target player must be online.</red>"));
                } else {
                    player.sendMessage(TextUtil.format("<red>✖ Player '" + targetName + "' was not found in your Guild!</red>"));
                }
                return true;
            }

            Team targetTeam = teamManager.getPlayerTeam(targetOp.getUniqueId());
            if (targetTeam == null || targetTeam.getId() != team.getId()) {
                player.sendMessage(TextUtil.format("<red>✖ Player '" + targetName + "' is not a member of your Guild!</red>"));
                return true;
            }

            guiManager.openTeamTransferLeaderConfirmGUI(player, targetOp);
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
                    bankManager.deposit(team, player.getUniqueId(), amount);
                } catch (NumberFormatException ignored) {}
                return true;
            }
            if (args.length >= 3 && args[1].equalsIgnoreCase("withdraw")) {
                try {
                    long amount = Long.parseLong(args[2]);
                    bankManager.withdraw(team, player.getUniqueId(), amount);
                } catch (NumberFormatException ignored) {}
                return true;
            }
            guiManager.openTeamBankGUI(player, team);
            return true;
        }

        if (sub.equals("claim")) {
            guiManager.openTeamMapGUI(player);
            return true;
        }

        if (sub.equals("unclaim")) {
            Team team = teamManager.getPlayerTeam(player.getUniqueId());
            if (team == null) {
                player.sendMessage(TextUtil.format("<red>✖ You must belong to a Guild to unclaim land.</red>"));
                return true;
            }
            String role = teamManager.getPlayerRole(player.getUniqueId());
            if (!guiManager.getPermissionManager().hasPermission(team.getId(), role, "CLAIM")) {
                player.sendMessage(TextUtil.format("<red>✖ You do not have team permission to unclaim land!</red>"));
                return true;
            }
            Chunk chunk = player.getLocation().getChunk();
            com.guildcore.claims.ClaimInfo claim = claimManager.getClaimAt(chunk);
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

        if (sub.equals("map")) {
            guiManager.openTeamMapGUI(player);
            return true;
        }

        if (sub.equals("members") || sub.equals("roster")) {
            Team team = teamManager.getPlayerTeam(player.getUniqueId());
            if (team == null) {
                player.sendMessage(TextUtil.format("<red>You must belong to a Guild to view the members roster.</red>"));
                return true;
            }
            guiManager.openTeamMembersGUI(player, team, 1);
            return true;
        }

        if (sub.equals("placecore")) {
            Team team = teamManager.getPlayerTeam(player.getUniqueId());
            if (team == null) {
                player.sendMessage(TextUtil.format("<red>You must be in a Guild to place a core.</red>"));
                return true;
            }
            if (!team.getLeaderUuid().equals(player.getUniqueId())) {
                player.sendMessage(TextUtil.format("<red>Only the Guild Leader can place the Guild Core!</red>"));
                return true;
            }
            Block target = player.getTargetBlockExact(5);
            if (target == null) {
                player.sendMessage(TextUtil.format("<red>Look at a valid block to place the Guild Core on top of.</red>"));
                return true;
            }
            Location coreLoc = target.getLocation().add(0, 1, 0);
            guildCoreManager.placeCore(player, coreLoc);
            return true;
        }

        if (sub.equals("deny")) {
            if (teamManager.denyInvite(player)) {
                player.sendMessage(TextUtil.format("<yellow>Declined Guild invitation.</yellow>"));
            } else {
                player.sendMessage(TextUtil.format("<red>You have no pending Guild invitations.</red>"));
            }
            return true;
        }

        if (sub.equals("sethome")) {
            Team team = teamManager.getPlayerTeam(player.getUniqueId());
            if (team == null) {
                player.sendMessage(TextUtil.format("<red>You must be in a Guild to set a Guild home.</red>"));
                return true;
            }
            if (!team.getLeaderUuid().equals(player.getUniqueId())) {
                player.sendMessage(TextUtil.format("<red>Only the Guild Leader can set the Guild home!</red>"));
                return true;
            }

            String unsafeReason = checkGuildHomeSafety(player, player.getLocation(), team);
            if (unsafeReason != null) {
                player.sendMessage(TextUtil.format("<red>✖ Cannot set Guild home: " + unsafeReason + "</red>"));
                return true;
            }

            team.setHomeLocation(player.getLocation());
            player.sendMessage(TextUtil.format("<green>✔ Guild home successfully set at your location!</green>"));
            return true;
        }

        if (sub.equals("home")) {
            Team team = teamManager.getPlayerTeam(player.getUniqueId());
            if (team == null || team.getHomeLocation() == null) {
                player.sendMessage(TextUtil.format("<red>Guild home is not set.</red>"));
                return true;
            }

            boolean isBypass = player.hasPermission("guildcore.home.bypasscooldown") || player.hasPermission("guildcore.admin.bypasscooldown") || player.hasPermission("guildcore.admin.bypass") || player.isOp();
            int cooldownSec = settingsManager.getInt("guild-home.cooldown-seconds", 60);
            if (!isBypass && cooldownSec > 0) {
                long lastUse = guildHomeCooldowns.getOrDefault(player.getUniqueId(), 0L);
                long elapsedSec = (System.currentTimeMillis() - lastUse) / 1000L;
                if (elapsedSec < cooldownSec) {
                    long remaining = cooldownSec - elapsedSec;
                    player.sendMessage(TextUtil.format("<red>Guild home teleport is on cooldown! Please wait " + remaining + " more seconds.</red>"));
                    return true;
                }
            }

            int warmupSec = settingsManager.getInt("guild-home.warmup-seconds", 5);
            Location homeLoc = team.getHomeLocation();
            if (homeLoc.getWorld() == null) {
                player.sendMessage(TextUtil.format("<red>Guild home world is unloaded.</red>"));
                return true;
            }

            if (!isBypass && warmupSec > 0) {
                Location startLoc = player.getLocation().clone();
                player.sendMessage(TextUtil.format("<yellow>⌛ Teleporting to Guild home in <gold>" + warmupSec + "s</gold>... Stay completely still!</yellow>"));
                guiManager.getScheduler().runLater(player, () -> {
                    if (!player.isOnline()) return;
                    Location currentLoc = player.getLocation();
                    if (!startLoc.getWorld().equals(currentLoc.getWorld()) || startLoc.distanceSquared(currentLoc) > 0.01) {
                        player.sendMessage(TextUtil.format("<red>✖ Teleport cancelled! You moved during warmup.</red>"));
                        return;
                    }
                    guildHomeCooldowns.put(player.getUniqueId(), System.currentTimeMillis());
                    player.teleportAsync(homeLoc).thenAccept(success -> {
                        if (success) player.sendMessage(TextUtil.format("<green>Teleported to Guild home!</green>"));
                        else player.sendMessage(TextUtil.format("<red>Teleport to Guild home failed.</red>"));
                    });
                }, warmupSec * 20L);
            } else {
                guildHomeCooldowns.put(player.getUniqueId(), System.currentTimeMillis());
                player.teleportAsync(homeLoc).thenAccept(success -> {
                    if (success) player.sendMessage(TextUtil.format("<green>Teleported to Guild home!</green>"));
                    else player.sendMessage(TextUtil.format("<red>Teleport to Guild home failed.</red>"));
                });
            }
            return true;
        }

        // Raid declare removed — raids are now emergent through raid tools and guild cores

        return true;
    }

    private String checkGuildHomeSafety(Player player, Location loc, Team team) {
        boolean requireClaim = settingsManager.getBoolean("guild-home.require-claim", true);
        Chunk chunk = loc.getChunk();
        com.guildcore.claims.ClaimInfo claim = claimManager.getClaimAt(chunk);
        if (requireClaim) {
            if (claim == null || !claim.isTeamClaim() || claim.getTeamId() == null || claim.getTeamId() != team.getId()) {
                return "Location is not inside a Guild claim owned by your Guild!";
            }
        }

        // Removed: old raid active check. Shield protection is handled by ClaimProtectionListener.

        Location below = loc.clone().subtract(0, 1, 0);
        if (!below.getBlock().getType().isSolid()) {
            return "Location does not have a solid block underfoot!";
        }

        Block feet = loc.getBlock();
        Block head = loc.clone().add(0, 1, 0).getBlock();
        if (!feet.isPassable() || !head.isPassable()) {
            return "Location is obstructed (head/feet space must be clear)!";
        }

        int radius = settingsManager.getInt("guild-home.safety-check-radius", 3);
        World world = loc.getWorld();
        int bx = loc.getBlockX();
        int by = loc.getBlockY();
        int bz = loc.getBlockZ();

        for (int x = -2; x <= 2; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -2; z <= 2; z++) {
                    Material mat = world.getBlockAt(bx + x, by + y, bz + z).getType();
                    if (mat == Material.LAVA || mat == Material.WATER || mat == Material.FIRE || mat == Material.SOUL_FIRE || mat == Material.CACTUS || mat == Material.POWDER_SNOW) {
                        return "Location is unsafe: hazardous block (" + mat.name() + ") nearby!";
                    }
                }
            }
        }

        for (org.bukkit.entity.Entity entity : world.getNearbyEntities(loc, radius, radius, radius)) {
            if (entity instanceof org.bukkit.entity.Monster) {
                return "Location is not safe: hostile mob (" + entity.getType().name() + ") nearby!";
            }
        }

        return null;
    }
}
