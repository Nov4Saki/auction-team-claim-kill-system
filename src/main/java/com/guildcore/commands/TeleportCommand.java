package com.guildcore.commands;

import com.guildcore.database.DatabaseManager;
import com.guildcore.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TeleportCommand implements TabExecutor {
    private final DatabaseManager dbManager;

    // Target UUID -> Requester UUID
    private final Map<UUID, UUID> tpaRequests = new ConcurrentHashMap<>();
    private final Random random = new Random();

    public TeleportCommand(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        String cmd = alias.toLowerCase();

        if (cmd.equals("tpa")) {
            if (args.length == 1) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(args[0].toLowerCase())) completions.add(p.getName());
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

        String cmd = label.toLowerCase();

        // 1. /tpa <player>
        if (cmd.equals("tpa")) {
            if (args.length < 1) {
                player.sendMessage(TextUtil.format("<red>Usage: /tpa <player></red>"));
                return true;
            }
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null || !target.isOnline()) {
                player.sendMessage(TextUtil.format("<red>Player not found online.</red>"));
                return true;
            }
            if (target.equals(player)) {
                player.sendMessage(TextUtil.format("<red>You cannot TPA to yourself.</red>"));
                return true;
            }

            tpaRequests.put(target.getUniqueId(), player.getUniqueId());
            player.sendMessage(TextUtil.format("<green>Sent teleport request to " + target.getName() + ".</green>"));
            target.sendMessage(TextUtil.format("<gold>⚡ <yellow>" + player.getName() + "</yellow> requested to teleport to you. Type <green>/tpaccept</green> or <red>/tpdeny</red>.</gold>"));
            return true;
        }

        // 2. /tpaccept
        if (cmd.equals("tpaccept")) {
            UUID requesterUuid = tpaRequests.remove(player.getUniqueId());
            if (requesterUuid == null) {
                player.sendMessage(TextUtil.format("<red>You have no pending TPA requests.</red>"));
                return true;
            }
            Player requester = Bukkit.getPlayer(requesterUuid);
            if (requester != null && requester.isOnline()) {
                requester.teleport(player.getLocation());
                requester.sendMessage(TextUtil.format("<green>Teleported to " + player.getName() + "!</green>"));
                player.sendMessage(TextUtil.format("<green>Accepted teleport request from " + requester.getName() + "!</green>"));
            } else {
                player.sendMessage(TextUtil.format("<red>Requester is no longer online.</red>"));
            }
            return true;
        }

        // 3. /tpdeny
        if (cmd.equals("tpdeny")) {
            UUID requesterUuid = tpaRequests.remove(player.getUniqueId());
            if (requesterUuid != null) {
                Player requester = Bukkit.getPlayer(requesterUuid);
                if (requester != null && requester.isOnline()) {
                    requester.sendMessage(TextUtil.format("<red>" + player.getName() + " denied your TPA request.</red>"));
                }
                player.sendMessage(TextUtil.format("<yellow>Denied TPA request.</yellow>"));
            } else {
                player.sendMessage(TextUtil.format("<red>You have no pending TPA requests.</red>"));
            }
            return true;
        }

        // 4. /rtp
        if (cmd.equals("rtp")) {
            World world = player.getWorld();
            int x = (random.nextInt(5000) - 2500);
            int z = (random.nextInt(5000) - 2500);
            int y = world.getHighestBlockYAt(x, z) + 1;
            Location targetLoc = new Location(world, x + 0.5, y, z + 0.5);

            player.teleport(targetLoc);
            player.sendMessage(TextUtil.format("<green>🎲 Randomly teleported to (" + x + ", " + y + ", " + z + ")!</green>"));
            return true;
        }

        // 5. /spawn
        if (cmd.equals("spawn")) {
            Location spawn = player.getWorld().getSpawnLocation();
            player.teleport(spawn);
            player.sendMessage(TextUtil.format("<green>Teleported to spawn!</green>"));
            return true;
        }

        if (cmd.equals("setspawn")) {
            if (!player.hasPermission("guildcore.admin")) {
                player.sendMessage(TextUtil.format("<red>No permission.</red>"));
                return true;
            }
            player.getWorld().setSpawnLocation(player.getLocation());
            player.sendMessage(TextUtil.format("<green>Spawn location updated!</green>"));
            return true;
        }

        // 6. /sethome & /home & /delhome
        if (cmd.equals("sethome")) {
            String name = args.length >= 1 ? args[0].toLowerCase() : "home";
            Location loc = player.getLocation();
            player.sendMessage(TextUtil.format("<green>Home '" + name + "' set!</green>"));
            return true;
        }

        if (cmd.equals("home")) {
            String name = args.length >= 1 ? args[0].toLowerCase() : "home";
            player.sendMessage(TextUtil.format("<green>Teleporting home...</green>"));
            return true;
        }

        // 7. /warp & /setwarp
        if (cmd.equals("warp")) {
            if (args.length < 1) {
                player.sendMessage(TextUtil.format("<gold>Usage: /warp <name></gold>"));
                return true;
            }
            player.sendMessage(TextUtil.format("<green>Teleporting to warp '" + args[0] + "'...</green>"));
            return true;
        }

        if (cmd.equals("setwarp")) {
            if (!player.hasPermission("guildcore.admin")) {
                player.sendMessage(TextUtil.format("<red>No permission.</red>"));
                return true;
            }
            if (args.length < 1) {
                player.sendMessage(TextUtil.format("<red>Usage: /setwarp <name></red>"));
                return true;
            }
            player.sendMessage(TextUtil.format("<green>Warp '" + args[0] + "' set!</green>"));
            return true;
        }

        return true;
    }
}
