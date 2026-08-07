// FILE: src/main/java/com/guildcore/claims/ClaimChestManager.java
package com.guildcore.claims;

import com.guildcore.config.SettingsManager;
import com.guildcore.core.GuildCoreManager;
import com.guildcore.database.DatabaseManager;
import com.guildcore.debug.DebugFlag;
import com.guildcore.debug.DebugManager;
import com.guildcore.teams.Team;
import com.guildcore.teams.TeamManager;
import com.guildcore.util.TextUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ClaimChestManager {
    private final DatabaseManager dbManager;
    private final TeamManager teamManager;
    private final ClaimManager claimManager;
    private final GuildCoreManager guildCoreManager;
    private final SettingsManager settingsManager;

    // teamId -> LocationKey
    private final Map<Integer, String> placedChests = new ConcurrentHashMap<>();
    // LocationKey -> teamId
    private final Map<String, Integer> chestLocations = new ConcurrentHashMap<>();
    // LocationKey -> UUID of armorstand
    private final Map<String, UUID> chestStands = new ConcurrentHashMap<>();
    // Player UUID -> last claim chest given timestamp
    private final Map<UUID, Long> chestCooldowns = new ConcurrentHashMap<>();

    private static final NamespacedKey CLAIM_CHEST_KEY = new NamespacedKey("guildcore", "claim_chest");

    public ClaimChestManager(DatabaseManager dbManager, TeamManager teamManager,
                             ClaimManager claimManager, GuildCoreManager guildCoreManager,
                             SettingsManager settingsManager) {
        this.dbManager = dbManager;
        this.teamManager = teamManager;
        this.claimManager = claimManager;
        this.guildCoreManager = guildCoreManager;
        this.settingsManager = settingsManager;
    }

    public void loadClaimChests() {
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM claim_chests");
             ResultSet rs = ps.executeQuery()) {

            placedChests.clear();
            chestLocations.clear();
            chestStands.clear();

            while (rs.next()) {
                int teamId = rs.getInt("team_id");
                String world = rs.getString("world");
                int x = rs.getInt("x");
                int y = rs.getInt("y");
                int z = rs.getInt("z");
                String standUuidStr = rs.getString("armor_stand_uuid");
                UUID standUuid = standUuidStr != null ? UUID.fromString(standUuidStr) : null;

                String locKey = world + ":" + x + ":" + y + ":" + z;
                placedChests.put(teamId, locKey);
                chestLocations.put(locKey, teamId);
                if (standUuid != null) {
                    chestStands.put(locKey, standUuid);
                }
            }

            DebugManager.log(DebugFlag.GUILD_CORE, "Loaded " + placedChests.size() + " claim chests");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public ItemStack createClaimChestItem() {
        Material mat = Material.matchMaterial(
                settingsManager.getString("claims.chest_material", "CHEST"));
        if (mat == null) mat = Material.CHEST;

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(TextUtil.format("<gradient:#FFD700:#FFA500><b>🏰 Guild Claim Chest</b></gradient>"));
            List<Component> lore = new ArrayList<>();
            lore.add(TextUtil.format("<gray>Place this chest to establish your Guild's territory</gray>"));
            lore.add(TextUtil.format("<gray>Right-click the placed chest to manage your Guild</gray>"));
            lore.add(TextUtil.format("<yellow>⚠ This chest cannot be broken by enemies</yellow>"));
            lore.add(TextUtil.format("<red>⚠ Removing this chest will destroy ALL claims</red>"));
            meta.lore(lore);

            // Hide all vanilla attributes
            meta.setAttributeModifiers(null);

            // Tag the item with PDC before setItemMeta
            meta.getPersistentDataContainer().set(CLAIM_CHEST_KEY, org.bukkit.persistence.PersistentDataType.BOOLEAN, true);

            item.setItemMeta(meta);
        }

        return item;
    }

    public boolean isClaimChestItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer()
                .getOrDefault(CLAIM_CHEST_KEY, org.bukkit.persistence.PersistentDataType.BOOLEAN, false);
    }

    public boolean giveClaimChest(Player player) {
        if (player == null) return false;

        // Check cooldown
        int cooldownSec = settingsManager.getInt("claims.chest_cooldown_seconds", 600);
        boolean bypassCooldown = player.hasPermission("guildcore.admin") || player.isOp();

        if (!bypassCooldown && cooldownSec > 0) {
            Long lastGiven = chestCooldowns.get(player.getUniqueId());
            if (lastGiven != null) {
                long elapsed = (System.currentTimeMillis() - lastGiven) / 1000L;
                if (elapsed < cooldownSec) {
                    long remaining = cooldownSec - elapsed;
                    player.sendMessage(TextUtil.format(
                            "<red>⏳ Claim Chest is on cooldown! Please wait " + remaining + " seconds.</red>"));
                    return false;
                }
            }
        }

        // Check if team already has a chest placed
        Team team = teamManager.getPlayerTeam(player.getUniqueId());
        if (team == null) {
            player.sendMessage(TextUtil.format("<red>✖ You must be in a Guild to receive a Claim Chest!</red>"));
            return false;
        }

        if (hasClaimChest(team.getId())) {
            player.sendMessage(TextUtil.format("<yellow>⚠ Your Guild already has a Claim Chest placed!</yellow>"));
            player.sendMessage(TextUtil.format("<yellow>Use the existing chest or remove it before placing a new one.</yellow>"));
            return false;
        }

        // Check inventory space
        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage(TextUtil.format("<red>✖ Your inventory is full!</red>"));
            return false;
        }

        ItemStack chest = createClaimChestItem();
        player.getInventory().addItem(chest);
        chestCooldowns.put(player.getUniqueId(), System.currentTimeMillis());

        player.sendMessage(TextUtil.format("<green>✔ You received a Guild Claim Chest!</green>"));
        player.sendMessage(TextUtil.format("<gray>Place it anywhere to establish your Guild territory.</gray>"));

        return true;
    }

    public boolean placeClaimChest(Player player, Block block) {
        if (player == null || block == null) return false;

        Team team = teamManager.getPlayerTeam(player.getUniqueId());
        if (team == null) {
            player.sendMessage(TextUtil.format("<red>✖ You must be in a Guild to place a Claim Chest!</red>"));
            return false;
        }

        if (!team.getLeaderUuid().equals(player.getUniqueId()) && !player.hasPermission("guildcore.admin")) {
            player.sendMessage(TextUtil.format("<red>✖ Only the Guild Leader can place the Claim Chest!</red>"));
            return false;
        }

        if (hasClaimChest(team.getId())) {
            player.sendMessage(TextUtil.format("<red>✖ Your Guild already has a Claim Chest placed!</red>"));
            return false;
        }

        // Check if block underneath is solid
        Block floor = block.getRelative(BlockFace.DOWN);
        if (!floor.getType().isSolid()) {
            player.sendMessage(TextUtil.format("<red>✖ You must place the chest on top of a solid block!</red>"));
            return false;
        }

        // Check distance from other claim chests
        Location loc = block.getLocation();
        for (String existingKey : placedChests.values()) {
            String[] parts = existingKey.split(":");
            if (parts.length == 4 && parts[0].equals(loc.getWorld().getName())) {
                int ex = Integer.parseInt(parts[1]);
                int ey = Integer.parseInt(parts[2]);
                int ez = Integer.parseInt(parts[3]);
                double dist = loc.distance(new Location(loc.getWorld(), ex, ey, ez));
                if (dist < 16) {
                    player.sendMessage(TextUtil.format("<red>✖ Too close to another Guild's Claim Chest! (16 block minimum)</red>"));
                    return false;
                }
            }
        }

        // Place the chest at the targeted block
        block.setType(Material.CHEST);

        String locKey = loc.getWorld().getName() + ":" + loc.getBlockX() + ":" +
                loc.getBlockY() + ":" + loc.getBlockZ();

        placedChests.put(team.getId(), locKey);
        chestLocations.put(locKey, team.getId());

        // 1. Register core FIRST so hasCore(teamId) returns true for claim creation
        UUID standUuid = null;
        if (guildCoreManager != null) {
            guildCoreManager.placeCoreForChest(team.getId(), loc);
            var core = guildCoreManager.getCoreForTeam(team.getId());
            if (core != null) {
                standUuid = core.getArmorStandUuid();
            }
        }

        if (standUuid != null) {
            chestStands.put(locKey, standUuid);
        }

        // 2. Create initial claim on this chunk now that guild core is registered
        claimManager.createTeamClaim(player.getUniqueId(), team.getId(), block.getChunk());

        // Save to DB
        saveChest(team.getId(), loc, standUuid);

        player.sendMessage(TextUtil.format("<green>✔ Claim Chest placed! This chunk has been claimed.</green>"));
        player.sendMessage(TextUtil.format("<yellow>Right-click the chest to manage your Guild territory.</yellow>"));

        // Effects
        loc.getWorld().playSound(loc, Sound.BLOCK_END_PORTAL_FRAME_FILL, 1.0f, 1.0f);
        loc.getWorld().spawnParticle(Particle.PORTAL, loc.clone().add(0.5, 0.5, 0.5), 50, 0.5, 0.5, 0.5, 0.1);

        DebugManager.log(DebugFlag.GUILD_CORE, "Claim chest placed for team " + team.getId() +
                " at " + locKey + " by " + player.getName());

        return true;
    }

    public void removeClaimChest(int teamId) {
        String locKey = placedChests.get(teamId);
        if (locKey != null) {
            String[] parts = locKey.split(":");
            if (parts.length == 4) {
                String worldName = parts[0];
                int x = Integer.parseInt(parts[1]);
                int y = Integer.parseInt(parts[2]);
                int z = Integer.parseInt(parts[3]);

                World world = Bukkit.getWorld(worldName);
                if (world != null) {
                    Location loc = new Location(world, x, y, z);

                    // Remove chest block
                    Block block = world.getBlockAt(loc);
                    if (block.getType() == Material.CHEST) {
                        block.setType(Material.AIR);
                    }

                    // Remove armor stand
                    UUID standUuid = chestStands.remove(locKey);
                    if (standUuid != null) {
                        for (Entity entity : world.getNearbyEntities(loc.clone().add(0.5, 0.5, 0.5), 2, 2, 2)) {
                            if (entity.getUniqueId().equals(standUuid)) {
                                entity.remove();
                                break;
                            }
                        }
                    }

                    // Effects
                    world.playSound(loc, Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 0.5f);
                    world.spawnParticle(Particle.LARGE_SMOKE, loc.clone().add(0.5, 0.5, 0.5), 30, 0.5, 0.5, 0.5, 0.05);
                }
            }

            placedChests.remove(teamId);
            chestLocations.remove(locKey);

            // Delete from DB
            deleteChest(teamId);
        }

        // Always clean up all claims for the team
        if (claimManager != null) {
            claimManager.removeAllTeamClaims(teamId);
        }

        // Always clean up guild core for the team
        if (guildCoreManager != null) {
            guildCoreManager.removeCore(teamId, false);
        }

        DebugManager.log(DebugFlag.GUILD_CORE, "Claim chest removed for team " + teamId);
    }

    public boolean isClaimChest(Location loc) {
        if (loc == null) return false;
        String key = loc.getWorld().getName() + ":" + loc.getBlockX() + ":" +
                loc.getBlockY() + ":" + loc.getBlockZ();
        return chestLocations.containsKey(key);
    }

    public boolean isClaimChestStand(Entity entity) {
        if (!(entity instanceof ArmorStand)) return false;
        for (UUID standUuid : chestStands.values()) {
            if (entity.getUniqueId().equals(standUuid)) return true;
        }
        return false;
    }

    public int getChestTeamId(Location loc) {
        if (loc == null) return -1;
        String key = loc.getWorld().getName() + ":" + loc.getBlockX() + ":" +
                loc.getBlockY() + ":" + loc.getBlockZ();
        return chestLocations.getOrDefault(key, -1);
    }

    public boolean hasClaimChest(int teamId) {
        return placedChests.containsKey(teamId);
    }

    public Location getClaimChestLocation(int teamId) {
        String locKey = placedChests.get(teamId);
        if (locKey == null) return null;
        String[] parts = locKey.split(":");
        World world = Bukkit.getWorld(parts[0]);
        if (world == null) return null;
        return new Location(world, Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
    }

    private void saveChest(int teamId, Location loc, UUID standUuid) {
        dbManager.executeAsync(() -> {
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT OR REPLACE INTO claim_chests (team_id, world, x, y, z, armor_stand_uuid) VALUES (?, ?, ?, ?, ?, ?)")) {
                ps.setInt(1, teamId);
                ps.setString(2, loc.getWorld().getName());
                ps.setInt(3, loc.getBlockX());
                ps.setInt(4, loc.getBlockY());
                ps.setInt(5, loc.getBlockZ());
                ps.setString(6, standUuid != null ? standUuid.toString() : null);
                ps.executeUpdate();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void deleteChest(int teamId) {
        dbManager.executeAsync(() -> {
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement("DELETE FROM claim_chests WHERE team_id = ?")) {
                ps.setInt(1, teamId);
                ps.executeUpdate();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}