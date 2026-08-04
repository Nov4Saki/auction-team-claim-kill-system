package com.guildcore.commands;

import com.guildcore.config.SettingsManager;
import com.guildcore.database.DatabaseManager;
import com.guildcore.gui.GUIManager;
import com.guildcore.scheduler.SchedulerWrapper;
import com.guildcore.util.TextUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
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
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class TeleportCommand implements TabExecutor {
    private final DatabaseManager dbManager;
    private final SettingsManager settingsManager;
    private final SchedulerWrapper scheduler;
    private final GUIManager guiManager;
    private final Map<UUID, TpaRequest> tpaRequests = new ConcurrentHashMap<>();
    private final Map<UUID, Long> rtpCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> spawnCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> warpCooldowns = new ConcurrentHashMap<>();
    private final Random random = new Random();

    public static class TpaRequest {
        public final UUID requesterUuid;
        public final String requesterName;
        public final long timestamp;

        public TpaRequest(UUID requesterUuid, String requesterName) {
            this.requesterUuid = requesterUuid;
            this.requesterName = requesterName;
            this.timestamp = System.currentTimeMillis();
        }
    }

    public TeleportCommand(DatabaseManager dbManager, SettingsManager settingsManager, SchedulerWrapper scheduler, GUIManager guiManager) {
        this.dbManager = dbManager;
        this.settingsManager = settingsManager;
        this.scheduler = scheduler;
        this.guiManager = guiManager;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        String cmd = command.getName().toLowerCase();

        if (cmd.equals("tpa")) {
            if (args.length == 1) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(args[0].toLowerCase())) completions.add(p.getName());
                }
            }
        } else if (cmd.equals("rtp")) {
            if (args.length == 1) {
                for (World w : Bukkit.getWorlds()) {
                    if (w.getEnvironment() == World.Environment.NORMAL && w.getName().toLowerCase().startsWith(args[0].toLowerCase())) {
                        completions.add(w.getName());
                    }
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

        String cmd = command.getName().toLowerCase();

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

            int expireSec = settingsManager.getInt("requests.tpa-expire-seconds", 60);
            TpaRequest existing = tpaRequests.get(target.getUniqueId());
            if (existing != null && (System.currentTimeMillis() - existing.timestamp) < (expireSec * 1000L)) {
                player.sendMessage(TextUtil.format("<red>Player already has a pending TPA request.</red>"));
                return true;
            }

            tpaRequests.put(target.getUniqueId(), new TpaRequest(player.getUniqueId(), player.getName()));
            player.sendMessage(TextUtil.format("<green>Sent teleport request to " + target.getName() + " (expires in " + expireSec + "s).</green>"));

            Component tpaMsg = Component.text("⚡ ")
                    .color(NamedTextColor.GOLD)
                    .append(Component.text(player.getName()).color(NamedTextColor.YELLOW))
                    .append(Component.text(" requested to teleport to you (Expires in " + expireSec + "s). ").color(NamedTextColor.GOLD))
                    .append(Component.text("[ACCEPT]")
                            .color(NamedTextColor.GREEN)
                            .decorate(TextDecoration.BOLD)
                            .clickEvent(ClickEvent.runCommand("/tpaccept"))
                            .hoverEvent(HoverEvent.showText(Component.text("Click to accept teleport request from " + player.getName()).color(NamedTextColor.GREEN))))
                    .append(Component.text("  "))
                    .append(Component.text("[DENY]")
                            .color(NamedTextColor.RED)
                            .decorate(TextDecoration.BOLD)
                            .clickEvent(ClickEvent.runCommand("/tpdeny"))
                            .hoverEvent(HoverEvent.showText(Component.text("Click to deny teleport request from " + player.getName()).color(NamedTextColor.RED))));

            target.sendMessage(tpaMsg);

            // Expiration Task
            scheduler.runLater(target, () -> {
                TpaRequest req = tpaRequests.get(target.getUniqueId());
                if (req != null && req.requesterUuid.equals(player.getUniqueId())) {
                    tpaRequests.remove(target.getUniqueId());
                    if (player.isOnline()) {
                        player.sendMessage(TextUtil.format("<yellow>Your TPA request to " + target.getName() + " has expired.</yellow>"));
                    }
                }
            }, expireSec * 20L);
            return true;
        }

        // 2. /tpaccept
        if (cmd.equals("tpaccept")) {
            TpaRequest req = tpaRequests.remove(player.getUniqueId());
            int expireSec = settingsManager.getInt("requests.tpa-expire-seconds", 60);
            if (req == null || (System.currentTimeMillis() - req.timestamp) > (expireSec * 1000L)) {
                player.sendMessage(TextUtil.format("<red>You have no pending or unexpired TPA requests.</red>"));
                return true;
            }

            Player requester = Bukkit.getPlayer(req.requesterUuid);
            if (requester != null && requester.isOnline()) {
                player.sendMessage(TextUtil.format("<green>Accepted teleport request from " + requester.getName() + "!</green>"));
                int tpaWarmup = settingsManager.getInt("tpa.warmup-seconds", 3);
                startWarmup(requester, player.getName(), tpaWarmup, () -> {
                    requester.teleportAsync(player.getLocation()).thenAccept(success -> {
                        if (success) {
                            requester.sendMessage(TextUtil.format("<green>Teleported to " + player.getName() + "!</green>"));
                        } else {
                            requester.sendMessage(TextUtil.format("<red>Teleport failed.</red>"));
                        }
                    });
                });
            } else {
                player.sendMessage(TextUtil.format("<red>Requester is no longer online.</red>"));
            }
            return true;
        }

        // 3. /tpdeny
        if (cmd.equals("tpdeny")) {
            TpaRequest req = tpaRequests.remove(player.getUniqueId());
            if (req != null) {
                Player requester = Bukkit.getPlayer(req.requesterUuid);
                if (requester != null && requester.isOnline()) {
                    requester.sendMessage(TextUtil.format("<red>" + player.getName() + " denied your TPA request.</red>"));
                }
                player.sendMessage(TextUtil.format("<yellow>Denied TPA request.</yellow>"));
            } else {
                player.sendMessage(TextUtil.format("<red>You have no pending TPA requests.</red>"));
            }
            return true;
        }

        // 4. /rtp [world_name]
        if (cmd.equals("rtp")) {
            if (args.length < 1) {
                guiManager.openRtpWorldMenu(player);
                return true;
            }

            World targetWorld = Bukkit.getWorld(args[0]);
            if (targetWorld == null) {
                player.sendMessage(TextUtil.format("<red>World '" + args[0] + "' not found!</red>"));
                return true;
            }

            boolean isBypass = player.hasPermission("guildcore.admin.bypasscooldown") || player.hasPermission("guildcore.admin.bypass") || player.isOp();
            int cooldownSec = settingsManager.getInt("rtp.cooldown_sec", 60);
            if (!isBypass && cooldownSec > 0) {
                long lastUse = rtpCooldowns.getOrDefault(player.getUniqueId(), 0L);
                long elapsedSec = (System.currentTimeMillis() - lastUse) / 1000L;
                if (elapsedSec < cooldownSec) {
                    long remaining = cooldownSec - elapsedSec;
                    player.sendMessage(TextUtil.format("<red>🎲 RTP is on cooldown! Please wait " + remaining + " more seconds.</red>"));
                    return true;
                }
            }

            int warmupSec = settingsManager.getInt("rtp.warmup_sec", 3);
            World finalWorld = targetWorld;
            startWarmup(player, "Wilderness RTP", warmupSec, () -> executeRTP(player, finalWorld));
            return true;
        }

        // 5. /spawn
        if (cmd.equals("spawn")) {
            boolean isBypass = player.hasPermission("guildcore.admin.bypasscooldown") || player.hasPermission("guildcore.admin.bypass") || player.isOp();
            int cooldownSec = settingsManager.getInt("spawn.cooldown-seconds", 60);
            if (!isBypass && cooldownSec > 0) {
                long lastUse = spawnCooldowns.getOrDefault(player.getUniqueId(), 0L);
                long elapsedSec = (System.currentTimeMillis() - lastUse) / 1000L;
                if (elapsedSec < cooldownSec) {
                    long remaining = cooldownSec - elapsedSec;
                    player.sendMessage(TextUtil.format("<red>Spawn teleport is on cooldown! Please wait " + remaining + " more seconds.</red>"));
                    return true;
                }
            }

            Location spawn = player.getWorld().getSpawnLocation();
            int warmupSec = settingsManager.getInt("spawn.warmup-seconds", 3);

            startWarmup(player, "Spawn", warmupSec, () -> {
                spawnCooldowns.put(player.getUniqueId(), System.currentTimeMillis());
                player.teleportAsync(spawn).thenAccept(success -> {
                    if (success) {
                        player.sendMessage(TextUtil.format("<green>Teleported to spawn!</green>"));
                    } else {
                        player.sendMessage(TextUtil.format("<red>Teleport failed.</red>"));
                    }
                });
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

        // 6. /warp & /setwarp & /delwarp
        if (cmd.equals("warp")) {
            if (args.length < 1) {
                player.sendMessage(TextUtil.format("<gold>Usage: /warp <name></gold>"));
                return true;
            }
            String name = args[0].toLowerCase();
            boolean isBypass = player.hasPermission("guildcore.admin.bypasscooldown") || player.hasPermission("guildcore.admin.bypass") || player.isOp();
            int cooldownSec = settingsManager.getInt("warp.cooldown-seconds", 60);
            if (!isBypass && cooldownSec > 0) {
                long lastUse = warpCooldowns.getOrDefault(player.getUniqueId(), 0L);
                long elapsedSec = (System.currentTimeMillis() - lastUse) / 1000L;
                if (elapsedSec < cooldownSec) {
                    long remaining = cooldownSec - elapsedSec;
                    player.sendMessage(TextUtil.format("<red>Warp teleport is on cooldown! Please wait " + remaining + " more seconds.</red>"));
                    return true;
                }
            }

            dbManager.executeAsync(() -> {
                try (Connection conn = dbManager.getConnection();
                     PreparedStatement ps = conn.prepareStatement("SELECT world, x, y, z, yaw, pitch FROM warps WHERE name = ?")) {
                    ps.setString(1, name);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            World w = Bukkit.getWorld(rs.getString("world"));
                            if (w != null) {
                                Location loc = new Location(w, rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"), rs.getFloat("yaw"), rs.getFloat("pitch"));
                                int warmupSec = settingsManager.getInt("warp.warmup-seconds", 3);
                                startWarmup(player, "Warp " + name, warmupSec, () -> {
                                    warpCooldowns.put(player.getUniqueId(), System.currentTimeMillis());
                                    player.teleportAsync(loc).thenAccept(success -> {
                                        if (success) {
                                            player.sendMessage(TextUtil.format("<green>Teleported to warp '" + name + "'!</green>"));
                                        } else {
                                            player.sendMessage(TextUtil.format("<red>Teleport failed.</red>"));
                                        }
                                    });
                                });
                            } else {
                                player.sendMessage(TextUtil.format("<red>Warp world is unloaded.</red>"));
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

        if (cmd.equals("delwarp")) {
            if (!player.hasPermission("guildcore.admin")) {
                player.sendMessage(TextUtil.format("<red>No permission.</red>"));
                return true;
            }
            if (args.length < 1) {
                player.sendMessage(TextUtil.format("<red>Usage: /delwarp <name></red>"));
                return true;
            }
            String name = args[0].toLowerCase();
            dbManager.executeAsync(() -> {
                try (Connection conn = dbManager.getConnection();
                     PreparedStatement ps = conn.prepareStatement("DELETE FROM warps WHERE name = ?")) {
                    ps.setString(1, name);
                    int rows = ps.executeUpdate();
                    if (rows > 0) {
                        player.sendMessage(TextUtil.format("<green>Deleted warp '" + name + "'.</green>"));
                    } else {
                        player.sendMessage(TextUtil.format("<red>Warp '" + name + "' not found!</red>"));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            return true;
        }

        return true;
    }

    private void startWarmup(Player player, String teleportName, int warmupSec, Runnable onComplete) {
        boolean isBypass = player.hasPermission("guildcore.admin.bypasscooldown") || player.hasPermission("guildcore.admin.bypass") || player.isOp();
        if (isBypass || warmupSec <= 0) {
            onComplete.run();
            return;
        }

        Location startLoc = player.getLocation().clone();
        player.sendMessage(TextUtil.format("<yellow>⌛ Teleporting to " + teleportName + " in <gold>" + warmupSec + "s</gold>... Stay completely still!</yellow>"));

        scheduler.runLater(player, () -> {
            if (!player.isOnline()) return;
            Location loc = player.getLocation();
            if (!startLoc.getWorld().equals(loc.getWorld()) || startLoc.distanceSquared(loc) > 0.01) {
                player.sendMessage(TextUtil.format("<red>✖ Teleport cancelled! You moved during warmup.</red>"));
                return;
            }
            onComplete.run();
        }, warmupSec * 20L);
    }

    private void executeRTP(Player player, World targetWorld) {
        rtpCooldowns.put(player.getUniqueId(), System.currentTimeMillis());
        player.sendMessage(TextUtil.format("<yellow>Searching for safe surface location (Y>=63) in " + targetWorld.getName() + "...</yellow>"));
        findSafeRTPLocation(targetWorld, 0, loc -> {
            if (loc != null) {
                player.teleportAsync(loc).thenAccept(success -> {
                    if (success) {
                        player.sendMessage(TextUtil.format("<green>🎲 Randomly teleported to safe surface at (" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + ") in " + loc.getWorld().getName() + "!</green>"));
                    }
                });
            } else {
                player.sendMessage(TextUtil.format("<red>Could not find a safe surface location. Please try /rtp again!</red>"));
            }
        });
    }

    private void findSafeRTPLocation(World world, int attempts, Consumer<Location> callback) {
        if (attempts >= 30) {
            callback.accept(null);
            return;
        }

        int minX = settingsManager.getInt("rtp.range.min_x", -3000);
        int maxX = settingsManager.getInt("rtp.range.max_x", 3000);
        int minZ = settingsManager.getInt("rtp.range.min_z", -3000);
        int maxZ = settingsManager.getInt("rtp.range.max_z", 3000);

        int boundMinX = Math.min(minX, maxX);
        int boundMaxX = Math.max(minX, maxX);
        int boundMinZ = Math.min(minZ, maxZ);
        int boundMaxZ = Math.max(minZ, maxZ);

        int rangeX = Math.max(1, boundMaxX - boundMinX);
        int rangeZ = Math.max(1, boundMaxZ - boundMinZ);

        int x = boundMinX + random.nextInt(rangeX + 1);
        int z = boundMinZ + random.nextInt(rangeZ + 1);
        int chunkX = x >> 4;
        int chunkZ = z >> 4;

        world.getChunkAtAsync(chunkX, chunkZ).thenAccept(chunk -> {
            int topY;
            int minY;

            World.Environment env = world.getEnvironment();
            if (env == World.Environment.NETHER) {
                topY = 120; // Below Nether bedrock ceiling (Y=127)
                minY = 32;  // Above Nether lava sea level (Y=31)
            } else if (env == World.Environment.THE_END) {
                topY = 120; // Island height threshold
                minY = 45;  // Safe height above End void
            } else {
                topY = Math.min(319, world.getMaxHeight() - 1);
                minY = Math.max(63, world.getMinHeight() + 5); // Overworld surface above Y=63
            }

            int targetY = -1;
            for (int y = topY; y >= minY; y--) {
                Block b = world.getBlockAt(x, y, z);
                Material m = b.getType();
                if (m.isSolid() && m != Material.LAVA && m != Material.WATER && m != Material.MAGMA_BLOCK && m != Material.FIRE && m != Material.CACTUS && m != Material.BEDROCK && m != Material.POWDER_SNOW) {
                    Block feet = world.getBlockAt(x, y + 1, z);
                    Block head = world.getBlockAt(x, y + 2, z);
                    if (feet.isPassable() && head.isPassable() && feet.getType() != Material.WATER && feet.getType() != Material.LAVA && head.getType() != Material.WATER && head.getType() != Material.LAVA) {
                        targetY = y;
                        break;
                    }
                }
            }

            if (targetY != -1) {
                Location safeLoc = new Location(world, x + 0.5, targetY + 1.0, z + 0.5);
                callback.accept(safeLoc);
            } else {
                findSafeRTPLocation(world, attempts + 1, callback);
            }
        });
    }
}


