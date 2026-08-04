package com.guildcore.core;

import com.guildcore.claims.ClaimManager;
import com.guildcore.config.SettingsManager;
import com.guildcore.database.DatabaseManager;
import com.guildcore.debug.DebugFlag;
import com.guildcore.debug.DebugManager;
import com.guildcore.scheduler.SchedulerWrapper;
import com.guildcore.teams.Team;
import com.guildcore.teams.TeamManager;
import com.guildcore.util.TextUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GuildCoreManager {
    private final DatabaseManager dbManager;
    private final ClaimManager claimManager;
    private final TeamManager teamManager;
    private final SettingsManager settingsManager;
    private final SchedulerWrapper scheduler;

    private com.guildcore.economy.EconomyManager economyManager;

    private final Map<Integer, GuildCoreBlock> coresByTeamId = new ConcurrentHashMap<>();
    private final Map<String, Integer> coresByLocationKey = new ConcurrentHashMap<>();

    public GuildCoreManager(DatabaseManager dbManager, ClaimManager claimManager, TeamManager teamManager, SettingsManager settingsManager, SchedulerWrapper scheduler) {
        this.dbManager = dbManager;
        this.claimManager = claimManager;
        this.teamManager = teamManager;
        this.settingsManager = settingsManager;
        this.scheduler = scheduler;
    }

    public void setEconomyManager(com.guildcore.economy.EconomyManager economyManager) {
        this.economyManager = economyManager;
    }

    // ═══════════════════════════════════════════════
    //  PLACE CORE
    // ═══════════════════════════════════════════════
    public boolean placeCore(Player leader, Location loc) {
        Team team = teamManager.getPlayerTeam(leader.getUniqueId());
        if (team == null) {
            leader.sendMessage(TextUtil.format("<red>✖ You must be in a Guild to place a core!</red>"));
            return false;
        }
        if (!team.getLeaderUuid().equals(leader.getUniqueId())) {
            leader.sendMessage(TextUtil.format("<red>✖ Only the Guild Leader can place the Guild Core!</red>"));
            return false;
        }
        if (coresByTeamId.containsKey(team.getId())) {
            leader.sendMessage(TextUtil.format("<red>✖ Your Guild already has a core placed! Destroy it first to relocate.</red>"));
            return false;
        }

        Chunk chunk = loc.getChunk();
        if (claimManager.isClaimed(chunk)) {
            leader.sendMessage(TextUtil.format("<red>✖ Guild Core can only be placed in UNCLAIMED chunks!</red>"));
            return false;
        }

        long cost = settingsManager.getLong("core.place_cost", 5000);
        if (economyManager != null) {
            long balance = economyManager.getBalance(leader.getUniqueId());
            if (balance < cost) {
                leader.sendMessage(TextUtil.format("<red>✖ Insufficient funds! Core placement costs $" + String.format("%,d", cost) + " Gold. Your balance: $" + String.format("%,d", balance) + "</red>"));
                return false;
            }
            economyManager.setBalance(leader.getUniqueId(), balance - cost);
        }

        // Place the chest block
        Block block = loc.getBlock();
        block.setType(Material.CHEST);

        int maxHp = settingsManager.getInt("core.max_hp", 100);

        // Spawn invisible ArmorStand above for display
        Location standLoc = loc.clone().add(0.5, 1.0, 0.5);
        World world = loc.getWorld();
        ArmorStand stand = world.spawn(standLoc, ArmorStand.class, as -> {
            as.setVisible(false);
            as.setGravity(false);
            as.setInvulnerable(false);
            as.setSmall(false);
            as.setMarker(false);
            as.setCustomNameVisible(true);
            as.customName(buildCoreName(team.getName(), maxHp, maxHp, 1));
            as.setBasePlate(false);
            as.setArms(false);
            // Put a beacon on its head for visual
            as.getEquipment().setHelmet(new ItemStack(Material.BEACON));
        });

        GuildCoreBlock core = new GuildCoreBlock(
                team.getId(), world.getName(),
                block.getX(), block.getY(), block.getZ(),
                1, maxHp, maxHp,
                stand.getUniqueId(),
                System.currentTimeMillis()
        );

        coresByTeamId.put(team.getId(), core);
        coresByLocationKey.put(core.getLocationKey(), team.getId());

        // Auto-claim the chunk for the team
        claimManager.createTeamClaim(team.getId(), chunk);

        saveCoreToDb(core);

        leader.sendMessage(TextUtil.format("<green>✔ Guild Core placed successfully! Cost: $" + String.format("%,d", cost) + " Gold.</green>"));
        leader.sendMessage(TextUtil.format("<yellow>⚡ The chunk has been auto-claimed for your Guild. Use the Core to claim adjacent territory!</yellow>"));

        // Effects
        world.playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 1.5f, 1.0f);
        world.spawnParticle(Particle.TOTEM_OF_UNDYING, loc.clone().add(0.5, 1.5, 0.5), 50, 0.5, 1.0, 0.5, 0.1);

        DebugManager.log(DebugFlag.GUILD_CORE, "Core placed for team " + team.getName() + " at " + core.getLocationKey() + " by " + leader.getName());
        return true;
    }

    // ═══════════════════════════════════════════════
    //  REMOVE CORE
    // ═══════════════════════════════════════════════
    public void removeCore(int teamId, boolean destroyed) {
        GuildCoreBlock core = coresByTeamId.remove(teamId);
        if (core == null) return;
        coresByLocationKey.remove(core.getLocationKey());

        // Remove physical blocks
        World world = Bukkit.getWorld(core.getWorld());
        if (world != null) {
            Block block = world.getBlockAt(core.getX(), core.getY(), core.getZ());
            if (block.getType() == Material.CHEST) {
                block.setType(Material.AIR);
            }

            // Kill ArmorStand
            if (core.getArmorStandUuid() != null) {
                for (Entity entity : world.getNearbyEntities(block.getLocation().add(0.5, 1.5, 0.5), 2, 2, 2)) {
                    if (entity instanceof ArmorStand && entity.getUniqueId().equals(core.getArmorStandUuid())) {
                        entity.remove();
                        break;
                    }
                }
            }

            if (destroyed) {
                world.playSound(block.getLocation(), Sound.ENTITY_ENDER_DRAGON_DEATH, 2.0f, 0.5f);
                world.spawnParticle(Particle.EXPLOSION, block.getLocation().add(0.5, 0.5, 0.5), 10, 1, 1, 1, 0);

                // Remove all team claims
                claimManager.removeAllTeamClaims(teamId);

                Team team = teamManager.getTeam(teamId);
                String teamName = team != null ? team.getName() : "Unknown";
                teamManager.broadcastToTeam(teamId, TextUtil.format("<red><b>⚠ YOUR GUILD CORE HAS BEEN DESTROYED!</b></red>"));
                teamManager.broadcastToTeam(teamId, TextUtil.format("<red>All territory claims have been removed. Place a new core to reclaim land.</red>"));

                // Broadcast server-wide
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.sendMessage(TextUtil.format("<gradient:#FF4500:#DC143C><b>⚔ The Guild Core of " + teamName + " has been destroyed!</b></gradient>"));
                }
            }
        }

        deleteCoreFromDb(teamId);
        DebugManager.log(DebugFlag.GUILD_CORE, "Core removed for team " + teamId + " (destroyed=" + destroyed + ")");
    }

    // ═══════════════════════════════════════════════
    //  DAMAGE CORE
    // ═══════════════════════════════════════════════
    public void damageCore(int teamId, int damage, Player attacker) {
        GuildCoreBlock core = coresByTeamId.get(teamId);
        if (core == null) return;

        core.setCurrentHp(core.getCurrentHp() - damage);

        // Update ArmorStand name
        updateArmorStandName(core);

        // Effects at core location
        World world = Bukkit.getWorld(core.getWorld());
        if (world != null) {
            Location loc = new Location(world, core.getX() + 0.5, core.getY() + 0.5, core.getZ() + 0.5);
            world.playSound(loc, Sound.ENTITY_IRON_GOLEM_HURT, 1.5f, 0.8f);
            world.spawnParticle(Particle.DAMAGE_INDICATOR, loc, 8, 0.3, 0.3, 0.3, 0.02);
        }

        // Notify defending team
        Team team = teamManager.getTeam(teamId);
        String attackerName = attacker != null ? attacker.getName() : "Explosion";
        if (team != null) {
            teamManager.broadcastToTeam(teamId, TextUtil.format("<red>⚠ Your Guild Core is under attack! HP: " + core.getCurrentHp() + "/" + core.getMaxHp() + " (Attacker: " + attackerName + ")</red>"));
        }

        DebugManager.log(DebugFlag.GUILD_CORE, "Core damaged: team=" + teamId + " damage=" + damage + " hp=" + core.getCurrentHp() + "/" + core.getMaxHp() + " by " + attackerName);

        if (core.getCurrentHp() <= 0) {
            removeCore(teamId, true);
        } else {
            saveCoreToDb(core);
        }
    }

    // ═══════════════════════════════════════════════
    //  REPAIR CORE
    // ═══════════════════════════════════════════════
    public boolean repairCore(int teamId, long cost) {
        GuildCoreBlock core = coresByTeamId.get(teamId);
        if (core == null) return false;

        Team team = teamManager.getTeam(teamId);
        if (team == null || team.getBankBalance() < cost) return false;

        team.setBankBalance(team.getBankBalance() - cost);
        core.setCurrentHp(core.getMaxHp());
        updateArmorStandName(core);
        saveCoreToDb(core);

        DebugManager.log(DebugFlag.GUILD_CORE, "Core repaired for team " + teamId + " (cost=" + cost + ")");
        return true;
    }

    // ═══════════════════════════════════════════════
    //  UPGRADE CORE TIER
    // ═══════════════════════════════════════════════
    public boolean upgradeCoreTier(int teamId, Player player) {
        GuildCoreBlock core = coresByTeamId.get(teamId);
        if (core == null) return false;

        int maxTier = settingsManager.getInt("core.tier.max", 5);
        if (core.getTier() >= maxTier) {
            player.sendMessage(TextUtil.format("<red>✖ Core is already at maximum tier!</red>"));
            return false;
        }

        int nextTier = core.getTier() + 1;
        long moneyCost = settingsManager.getLong("core.tier." + nextTier + ".money_cost", nextTier * 1000L);
        String itemCostStr = settingsManager.getString("core.tier." + nextTier + ".item_cost", "DIAMOND:" + (nextTier * 10));

        Team team = teamManager.getTeam(teamId);
        if (team == null) return false;

        if (team.getBankBalance() < moneyCost) {
            player.sendMessage(TextUtil.format("<red>✖ Insufficient Team Bank funds! Need $" + String.format("%,d", moneyCost) + " Gold.</red>"));
            return false;
        }

        // Parse item cost
        String[] parts = itemCostStr.split(":");
        Material itemMat = Material.matchMaterial(parts[0]);
        int itemAmount = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
        if (itemMat == null) itemMat = Material.DIAMOND;

        if (!player.getInventory().contains(itemMat, itemAmount)) {
            player.sendMessage(TextUtil.format("<red>✖ You need " + itemAmount + "x " + itemMat.name() + " in your inventory!</red>"));
            return false;
        }

        // Deduct costs
        team.setBankBalance(team.getBankBalance() - moneyCost);
        player.getInventory().removeItem(new ItemStack(itemMat, itemAmount));

        core.setTier(nextTier);
        int newMaxHp = settingsManager.getInt("core.max_hp", 100) + (nextTier * 20);
        core.setMaxHp(newMaxHp);
        core.setCurrentHp(newMaxHp);

        updateArmorStandName(core);
        saveCoreToDb(core);

        // Save bank balance
        teamManager.saveTeamBankBalance(team);

        player.sendMessage(TextUtil.format("<green>✔ Guild Core upgraded to Tier " + nextTier + "! New max claims: " + getMaxClaimsForTier(nextTier) + "</green>"));

        World world = Bukkit.getWorld(core.getWorld());
        if (world != null) {
            Location loc = new Location(world, core.getX() + 0.5, core.getY() + 1.5, core.getZ() + 0.5);
            world.playSound(loc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.5f, 1.2f);
            world.spawnParticle(Particle.TOTEM_OF_UNDYING, loc, 30, 0.5, 1.0, 0.5, 0.05);
        }

        DebugManager.log(DebugFlag.GUILD_CORE, "Core upgraded: team=" + teamId + " tier=" + nextTier + " by " + player.getName());
        return true;
    }

    // ═══════════════════════════════════════════════
    //  GETTERS
    // ═══════════════════════════════════════════════
    public GuildCoreBlock getCoreForTeam(int teamId) {
        return coresByTeamId.get(teamId);
    }

    public GuildCoreBlock getCoreAtLocation(Location loc) {
        String key = loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
        Integer teamId = coresByLocationKey.get(key);
        if (teamId == null) return null;
        return coresByTeamId.get(teamId);
    }

    public int getMaxClaimsForTier(int tier) {
        return settingsManager.getInt("core.tier." + tier + ".claims_granted", tier * 5);
    }

    public java.util.Collection<GuildCoreBlock> getAllCores() {
        return coresByTeamId.values();
    }

    // ═══════════════════════════════════════════════
    //  ARMOR STAND DISPLAY
    // ═══════════════════════════════════════════════
    private void updateArmorStandName(GuildCoreBlock core) {
        World world = Bukkit.getWorld(core.getWorld());
        if (world == null || core.getArmorStandUuid() == null) return;

        Team team = teamManager.getTeam(core.getTeamId());
        String teamName = team != null ? team.getName() : "Unknown";

        Location searchLoc = new Location(world, core.getX() + 0.5, core.getY() + 1.5, core.getZ() + 0.5);
        for (Entity entity : world.getNearbyEntities(searchLoc, 2, 2, 2)) {
            if (entity instanceof ArmorStand stand && entity.getUniqueId().equals(core.getArmorStandUuid())) {
                stand.customName(buildCoreName(teamName, core.getCurrentHp(), core.getMaxHp(), core.getTier()));
                break;
            }
        }
    }

    private Component buildCoreName(String teamName, int currentHp, int maxHp, int tier) {
        String hpColor = currentHp > maxHp / 2 ? "<green>" : currentHp > maxHp / 4 ? "<yellow>" : "<red>";
        return TextUtil.format("<gradient:#FFD700:#FFA500><b>⚔ " + teamName + " GUILD CORE</b></gradient> <gray>|</gray> " + hpColor + "HP: " + currentHp + "/" + maxHp + hpColor.replace("<", "</") + " <gray>|</gray> <yellow>T" + tier + "</yellow>");
    }

    // ═══════════════════════════════════════════════
    //  DATABASE
    // ═══════════════════════════════════════════════
    public void loadAllCores() {
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM guild_cores");
             ResultSet rs = ps.executeQuery()) {

            coresByTeamId.clear();
            coresByLocationKey.clear();

            while (rs.next()) {
                int teamId = rs.getInt("team_id");
                String worldName = rs.getString("world");
                int x = rs.getInt("x");
                int y = rs.getInt("y");
                int z = rs.getInt("z");
                int tier = rs.getInt("tier");
                int currentHp = rs.getInt("current_hp");
                int maxHp = rs.getInt("max_hp");
                String asUuidStr = rs.getString("armor_stand_uuid");
                UUID asUuid = asUuidStr != null && !asUuidStr.isEmpty() ? UUID.fromString(asUuidStr) : null;
                long placedAt = rs.getTimestamp("placed_at") != null ? rs.getTimestamp("placed_at").getTime() : System.currentTimeMillis();

                GuildCoreBlock core = new GuildCoreBlock(teamId, worldName, x, y, z, tier, currentHp, maxHp, asUuid, placedAt);
                coresByTeamId.put(teamId, core);
                coresByLocationKey.put(core.getLocationKey(), teamId);
            }

            DebugManager.log(DebugFlag.GUILD_CORE, "Loaded " + coresByTeamId.size() + " guild cores from database.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void saveAllCores() {
        for (GuildCoreBlock core : coresByTeamId.values()) {
            saveCoreToDbSync(core);
        }
        DebugManager.log(DebugFlag.GUILD_CORE, "Saved " + coresByTeamId.size() + " guild cores to database.");
    }

    private void saveCoreToDb(GuildCoreBlock core) {
        dbManager.executeAsync(() -> saveCoreToDbSync(core));
    }

    private void saveCoreToDbSync(GuildCoreBlock core) {
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT OR REPLACE INTO guild_cores (team_id, world, x, y, z, tier, current_hp, max_hp, armor_stand_uuid) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setInt(1, core.getTeamId());
            ps.setString(2, core.getWorld());
            ps.setInt(3, core.getX());
            ps.setInt(4, core.getY());
            ps.setInt(5, core.getZ());
            ps.setInt(6, core.getTier());
            ps.setInt(7, core.getCurrentHp());
            ps.setInt(8, core.getMaxHp());
            ps.setString(9, core.getArmorStandUuid() != null ? core.getArmorStandUuid().toString() : null);
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void deleteCoreFromDb(int teamId) {
        dbManager.executeAsync(() -> {
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement("DELETE FROM guild_cores WHERE team_id = ?")) {
                ps.setInt(1, teamId);
                ps.executeUpdate();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}
