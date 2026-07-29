package com.guildcore.commands;

import com.guildcore.database.DatabaseManager;
import com.guildcore.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
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
                requester.teleportAsync(player.getLocation()).thenAccept(success -> {
                    if (success) {
                        requester.sendMessage(TextUtil.format("<green>Teleported to " + player.getName() + "!</green>"));
                        player.sendMessage(TextUtil.format("<green>Accepted teleport request from " + requester.getName() + "!</green>"));
                    }
                });
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

            player.teleportAsync(targetLoc).thenAccept(success -> {
                if (success) {
                    player.sendMessage(TextUtil.format("<green>🎲 Randomly teleported to (" + x + ", " + y + ", " + z + ")!</green>"));
                }
            });
            return true;
        }

        // 5. /spawn
        if (cmd.equals("spawn")) {
            Location spawn = player.getWorld().getSpawnLocation();
            player.teleportAsync(spawn).thenAccept(success -> {
                if (success) {
                    player.sendMessage(TextUtil.format("<green>Teleported to spawn!</green>"));
                }
            });
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
            dbManager.executeAsync(() -> {
                try (Connection conn = dbManager.getConnection();
                     PreparedStatement ps = conn.prepareStatement("INSERT OR REPLACE INTO homes (player_uuid, name, world, x, y, z, yaw, pitch) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                    ps.setString(1, player.getUniqueId().toString());
                    ps.setString(2, name);
                    ps.setString(3, loc.getWorld().getName());
                    ps.setDouble(4, loc.getX());
                    ps.setDouble(5, loc.getY());
                    ps.setDouble(6, loc.getZ());
                    ps.setFloat(7, loc.getYaw());
                    ps.setFloat(8, loc.getPitch());
                    ps.executeUpdate();
                    player.sendMessage(TextUtil.format("<green>Home '" + name + "' set!</green>"));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            return true;
        }

        if (cmd.equals("home")) {
            String name = args.length >= 1 ? args[0].toLowerCase() : "home";
            dbManager.executeAsync(() -> {
                try (Connection conn = dbManager.getConnection();
                     PreparedStatement ps = conn.prepareStatement("SELECT world, x, y, z, yaw, pitch FROM homes WHERE player_uuid = ? AND name = ?")) {
                    ps.setString(1, player.getUniqueId().toString());
                    ps.setString(2, name);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            World w = Bukkit.getWorld(rs.getString("world"));
                            if (w != null) {
                                Location loc = new Location(w, rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"), rs.getFloat("yaw"), rs.getFloat("pitch"));
                                player.teleportAsync(loc).thenAccept(success -> {
                                    if (success) {
                                        player.sendMessage(TextUtil.format("<green>Teleported home (" + name + ")!</green>"));
                                    }
                                });
                            }
                        } else {
                            player.sendMessage(TextUtil.format("<red>Home '" + name + "' not found!</red>"));
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            return true;
        }

        if (cmd.equals("delhome")) {
            String name = args.length >= 1 ? args[0].toLowerCase() : "home";
            dbManager.executeAsync(() -> {
                try (Connection conn = dbManager.getConnection();
                     PreparedStatement ps = conn.prepareStatement("DELETE FROM homes WHERE player_uuid = ? AND name = ?")) {
                    ps.setString(1, player.getUniqueId().toString());
                    ps.setString(2, name);
                    ps.executeUpdate();
                    player.sendMessage(TextUtil.format("<green>Deleted home '" + name + "'.</green>"));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            return true;
        }

        // 7. /warp & /setwarp & /delwarp
        if (cmd.equals("warp")) {
            if (args.length < 1) {
                player.sendMessage(TextUtil.format("<gold>Usage: /warp <name></gold>"));
                return true;
            }
            String name = args[0].toLowerCase();
            dbManager.executeAsync(() -> {
                try (Connection conn = dbManager.getConnection();
                     PreparedStatement ps = conn.prepareStatement("SELECT world, x, y, z, yaw, pitch FROM warps WHERE name = ?")) {
                    ps.setString(1, name);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            World w = Bukkit.getWorld(rs.getString("world"));
                            if (w != null) {
                                Location loc = new Location(w, rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"), rs.getFloat("yaw"), rs.getFloat("pitch"));
                                player.teleportAsync(loc).thenAccept(success -> {
                                    if (success) {
                                        player.sendMessage(TextUtil.format("<green>Teleported to warp '" + name + "'!</green>"));
                                    }
                                });
                            }
                        } else {
                            player.sendMessage(TextUtil.format("<red>Warp '" + name + "' not found!</red>"));
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
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
            String name = args[0].toLowerCase();
            Location loc = player.getLocation();
            dbManager.executeAsync(() -> {
                try (Connection conn = dbManager.getConnection();
                     PreparedStatement ps = conn.prepareStatement("INSERT OR REPLACE INTO warps (name, world, x, y, z, yaw, pitch) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                    ps.setString(1, name);
                    ps.setString(2, loc.getWorld().getName());
                    ps.setDouble(3, loc.getX());
                    ps.setDouble(4, loc.getY());
                    ps.setDouble(5, loc.getZ());
                    ps.setFloat(6, loc.getYaw());
                    ps.setFloat(7, loc.getPitch());
                    ps.executeUpdate();
                    player.sendMessage(TextUtil.format("<green>Warp '" + name + "' set!</green>"));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            return true;
        }

        return true;
    }
}
