package com.guildcore.core;

import com.guildcore.claims.ClaimManager;
import com.guildcore.config.SettingsManager;
import com.guildcore.database.DatabaseManager;
import com.guildcore.debug.DebugFlag;
import com.guildcore.debug.DebugManager;
import com.guildcore.economy.EconomyManager;
import com.guildcore.scheduler.SchedulerWrapper;
import com.guildcore.teams.Team;
import com.guildcore.teams.TeamManager;
import com.guildcore.util.TextUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GuildCoreManager {
    private final DatabaseManager dbManager;
    private final ClaimManager claimManager;
    private final TeamManager teamManager;
    private final SettingsManager settingsManager;
    private final SchedulerWrapper scheduler;
    private EconomyManager economyManager;

    private final Map<Integer, GuildCoreBlock> coresByTeamId = new ConcurrentHashMap<>();
    private final Map<String, Integer> teamIdByLocationKey = new ConcurrentHashMap<>();
    private final Map<Integer, Long> lastDamageTime = new ConcurrentHashMap<>();

    public GuildCoreManager(DatabaseManager dbManager, ClaimManager claimManager,
                            TeamManager teamManager, SettingsManager settingsManager,
                            SchedulerWrapper scheduler) {
        this.dbManager = dbManager;
        this.claimManager = claimManager;
        this.teamManager = teamManager;
        this.settingsManager = settingsManager;
        this.scheduler = scheduler;
    }

    public void setEconomyManager(EconomyManager economyManager) {
        this.economyManager = economyManager;
    }

    // ═══════════════════════════════════════════════
    //  PLACE CORE
    // ═══════════════════════════════════════════════
    public boolean placeCore(Player leader, Location loc) {
        if (leader == null || loc == null) return false;

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

        Block targetBlock = loc.getBlock();
        Block above = targetBlock.getRelative(BlockFace.UP);
        if (!targetBlock.getType().isSolid() || !above.getType().isAir()) {
            leader.sendMessage(TextUtil.format("<red>✖ Invalid placement location! Need a solid block with air above.</red>"));
            return false;
        }

        for (GuildCoreBlock existing : coresByTeamId.values()) {
            if (existing.getWorld().equals(loc.getWorld().getName())) {
                double distance = Math.sqrt(
                        Math.pow(existing.getX() - loc.getBlockX(), 2) +
                                Math.pow(existing.getY() - loc.getBlockY(), 2) +
                                Math.pow(existing.getZ() - loc.getBlockZ(), 2)
                );
                if (distance < 16) {
                    leader.sendMessage(TextUtil.format("<red>✖ Too close to another Guild Core! Minimum distance: 16 blocks.</red>"));
                    return false;
                }
            }
        }

        long cost = settingsManager.getLong("core.place_cost", 5000);
        if (economyManager != null) {
            long balance = economyManager.getBalance(leader.getUniqueId());
            if (balance < cost) {
                leader.sendMessage(TextUtil.format("<red>✖ Insufficient funds! Core placement costs $" +
                        String.format("%,d", cost) + " Gold. Your balance: $" + String.format("%,d", balance) + "</red>"));
                return false;
            }
            economyManager.withdraw(leader.getUniqueId(), cost, "core_placement");
        }

        targetBlock.setType(Material.CHEST);

        World world = loc.getWorld();
        int baseMaxHp = settingsManager.getInt("core.max_hp", 100);

        Location standLoc = loc.clone().add(0.5, 1.5, 0.5);
        ArmorStand stand = world.spawn(standLoc, ArmorStand.class, as -> {
            as.setVisible(true);
            as.setGravity(false);
            as.setInvulnerable(false);
            as.setSmall(false);
            as.setMarker(false);
            as.setCustomNameVisible(true);
            as.customName(buildCoreName(team.getName(), baseMaxHp, baseMaxHp, 1));
            as.setBasePlate(false);
            as.setArms(false);
            ItemStack helmet = new ItemStack(Material.BEACON);
            ItemMeta meta = helmet.getItemMeta();
            if (meta != null) {
                meta.setUnbreakable(true);
                helmet.setItemMeta(meta);
            }
            as.getEquipment().setHelmet(helmet);
            as.setRemoveWhenFarAway(false);
        });

        GuildCoreBlock core = new GuildCoreBlock(
                team.getId(), world.getName(),
                targetBlock.getX(), targetBlock.getY(), targetBlock.getZ(),
                1, baseMaxHp, baseMaxHp,
                stand.getUniqueId(),
                System.currentTimeMillis()
        );

        coresByTeamId.put(team.getId(), core);
        teamIdByLocationKey.put(core.getLocationKey(), team.getId());

        claimManager.createTeamClaim(team.getId(), chunk);

        saveCoreToDb(core);

        teamManager.broadcastToTeam(team.getId(), TextUtil.format(
                "<gradient:#FFD700:#FFA500><b>⚔ Guild Core has been placed at " +
                        loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() +
                        "!</b></gradient>"
        ));
        leader.sendMessage(TextUtil.format("<green>✔ Guild Core placed successfully! Cost: $" +
                String.format("%,d", cost) + " Gold.</green>"));
        leader.sendMessage(TextUtil.format("<yellow>⚡ The chunk has been auto-claimed. Interact with the core to manage territory!</yellow>"));

        world.playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 1.5f, 1.0f);
        world.spawnParticle(Particle.TOTEM_OF_UNDYING, loc.clone().add(0.5, 1.5, 0.5),
                50, 0.5, 1.0, 0.5, 0.1);

        DebugManager.log(DebugFlag.GUILD_CORE, "Core placed for team " + team.getName() +
                " at " + core.getLocationKey() + " by " + leader.getName());
        return true;
    }

    // ═══════════════════════════════════════════════
    //  REMOVE CORE
    // ═══════════════════════════════════════════════
    public void removeCore(int teamId, boolean destroyed) {
        GuildCoreBlock core = coresByTeamId.remove(teamId);
        if (core == null) return;

        teamIdByLocationKey.remove(core.getLocationKey());
        lastDamageTime.remove(teamId);

        World world = Bukkit.getWorld(core.getWorld());
        Team team = teamManager.getTeam(teamId);
        final String teamName = team != null ? team.getName() : "Unknown";

        if (world != null) {
            Location coreLoc = new Location(world, core.getX(), core.getY(), core.getZ());
            Block block = world.getBlockAt(coreLoc);
            if (block.getType() == Material.CHEST) {
                block.setType(Material.AIR);
            }

            if (core.getArmorStandUuid() != null) {
                for (Entity entity : world.getNearbyEntities(
                        coreLoc.clone().add(0.5, 1.5, 0.5), 2, 2, 2)) {
                    if (entity instanceof ArmorStand &&
                            entity.getUniqueId().equals(core.getArmorStandUuid())) {
                        entity.remove();
                        break;
                    }
                }
            }

            if (destroyed) {
                world.playSound(coreLoc, Sound.ENTITY_ENDER_DRAGON_DEATH, 2.0f, 0.5f);
                world.spawnParticle(Particle.EXPLOSION, coreLoc.clone().add(0.5, 0.5, 0.5),
                        15, 1.5, 1.5, 1.5, 0);
                world.spawnParticle(Particle.FLAME, coreLoc.clone().add(0.5, 1.5, 0.5),
                        30, 0.5, 1.0, 0.5, 0.02);
                world.strikeLightningEffect(coreLoc.clone().add(0.5, 0, 0.5));

                claimManager.removeAllTeamClaims(teamId);

                if (team != null) {
                    teamManager.broadcastToTeam(teamId, TextUtil.format(
                            "<red><b>⚠ YOUR GUILD CORE HAS BEEN DESTROYED!</b></red>"));
                    teamManager.broadcastToTeam(teamId, TextUtil.format(
                            "<red>All territory claims have been lost. Place a new core to reclaim land.</red>"));
                }

                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.sendMessage(TextUtil.format(
                            "<gradient:#FF4500:#DC143C><b>⚔ The Guild Core of " + teamName +
                                    " has been destroyed!</b></gradient>"));
                }
            } else {
                if (world != null) {
                    world.playSound(coreLoc, Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 1.0f);
                }
            }
        }

        deleteCoreFromDb(teamId);
        DebugManager.log(DebugFlag.GUILD_CORE, "Core removed for team " + teamId +
                " (destroyed=" + destroyed + ")");
    }

    // ═══════════════════════════════════════════════
    //  DAMAGE CORE
    // ═══════════════════════════════════════════════
    public boolean damageCore(int teamId, int damage, Player attacker) {
        GuildCoreBlock core = coresByTeamId.get(teamId);
        if (core == null || core.isDestroyed()) return false;

        long now = System.currentTimeMillis();
        long lastDamage = lastDamageTime.getOrDefault(teamId, 0L);
        int cooldownTicks = settingsManager.getInt("core.break_cooldown_ticks", 5);
        long cooldownMs = cooldownTicks * 50L;

        if (now - lastDamage < cooldownMs) {
            if (attacker != null) {
                attacker.sendActionBar(Component.text("⏳ Core damage cooldown active!", NamedTextColor.RED));
            }
            return false;
        }

        lastDamageTime.put(teamId, now);

        core.setCurrentHp(core.getCurrentHp() - damage);

        updateArmorStandName(core);

        World world = Bukkit.getWorld(core.getWorld());
        if (world != null) {
            Location loc = new Location(world, core.getX() + 0.5, core.getY() + 0.5, core.getZ() + 0.5);
            world.playSound(loc, Sound.ENTITY_IRON_GOLEM_HURT, 1.5f, 0.8f);
            world.spawnParticle(Particle.DAMAGE_INDICATOR, loc, 8, 0.3, 0.3, 0.3, 0.02);
            world.spawnParticle(Particle.BLOCK_CRUMBLE, loc.clone().add(0, 1, 0),
                    10, 0.3, 0.3, 0.3, Material.REDSTONE_BLOCK.createBlockData());
        }

        Team team = teamManager.getTeam(teamId);
        final String attackerName = attacker != null ? attacker.getName() : "Unknown";
        if (team != null) {
            teamManager.broadcastToTeam(teamId, TextUtil.format(
                    "<red>⚠ Your Guild Core is under attack! HP: " + core.getCurrentHp() +
                            "/" + core.getMaxHp() + " (Attacker: " + attackerName + ")</red>"));
        }

        DebugManager.log(DebugFlag.GUILD_CORE, "Core damaged: team=" + teamId +
                " damage=" + damage + " hp=" + core.getCurrentHp() + "/" + core.getMaxHp() +
                " by " + attackerName);

        if (core.isDestroyed()) {
            final int targetTeamId = teamId;
            final Player finalAttacker = attacker;
            final String teamName = team != null ? team.getName() : "Unknown";
            final boolean awardCredit = settingsManager.getBoolean("raidtag.award_kill_credit", true);

            // Use location-based scheduler to avoid ambiguity
            Location destructionLoc = world != null ?
                    new Location(world, core.getX() + 0.5, core.getY() + 0.5, core.getZ() + 0.5) : null;

            Runnable destructionTask = () -> {
                removeCore(targetTeamId, true);

                if (finalAttacker != null && awardCredit) {
                    finalAttacker.sendMessage(TextUtil.format(
                            "<gradient:#FFD700:#FFA500><b>🏆 You destroyed " + teamName +
                                    "'s Guild Core!</b></gradient>"));
                }
            };

            if (destructionLoc != null) {
                scheduler.runLater(destructionLoc, destructionTask, 40L);
            } else {
                scheduler.runSync(destructionTask);
            }
        } else {
            saveCoreToDb(core);
        }

        return true;
    }

    // ═══════════════════════════════════════════════
    //  REPAIR CORE
    // ═══════════════════════════════════════════════
    public boolean repairCore(int teamId, long cost) {
        GuildCoreBlock core = coresByTeamId.get(teamId);
        if (core == null || core.isDestroyed()) return false;

        Team team = teamManager.getTeam(teamId);
        if (team == null) return false;

        if (team.getBankBalance() < cost) {
            return false;
        }

        team.setBankBalance(team.getBankBalance() - cost);
        core.setCurrentHp(core.getMaxHp());
        updateArmorStandName(core);
        saveCoreToDb(core);

        teamManager.saveTeamBankBalance(team);

        DebugManager.log(DebugFlag.GUILD_CORE, "Core repaired for team " + teamId +
                " (cost=" + cost + ")");
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
            if (player != null) {
                player.sendMessage(TextUtil.format("<red>✖ Core is already at maximum tier (Tier " + maxTier + ")!</red>"));
            }
            return false;
        }

        int nextTier = core.getTier() + 1;
        long moneyCost = settingsManager.getLong("core.tier." + nextTier + ".money_cost", nextTier * 1000L);
        String itemCostStr = settingsManager.getString("core.tier." + nextTier + ".item_cost", "DIAMOND:" + (nextTier * 10));

        Team team = teamManager.getTeam(teamId);
        if (team == null) return false;

        if (team.getBankBalance() < moneyCost) {
            if (player != null) {
                player.sendMessage(TextUtil.format("<red>✖ Insufficient Team Bank funds! Need $" +
                        String.format("%,d", moneyCost) + " Gold. Bank: $" +
                        String.format("%,d", team.getBankBalance()) + "</red>"));
            }
            return false;
        }

        String[] parts = itemCostStr.split(":");
        Material itemMat = Material.matchMaterial(parts[0]);
        int itemAmount = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
        if (itemMat == null) itemMat = Material.DIAMOND;

        if (player != null && !player.getInventory().contains(itemMat, itemAmount)) {
            player.sendMessage(TextUtil.format("<red>✖ You need " + itemAmount + "x " +
                    itemMat.name() + " in your inventory!</red>"));
            return false;
        }

        team.setBankBalance(team.getBankBalance() - moneyCost);
        if (player != null) {
            player.getInventory().removeItem(new ItemStack(itemMat, itemAmount));
        }

        core.setTier(nextTier);
        int newMaxHp = settingsManager.getInt("core.max_hp", 100) + (nextTier * 20);
        core.setMaxHp(newMaxHp);
        core.setCurrentHp(newMaxHp);

        updateArmorStandName(core);
        saveCoreToDb(core);
        teamManager.saveTeamBankBalance(team);

        World world = Bukkit.getWorld(core.getWorld());
        if (world != null && player != null) {
            Location loc = new Location(world, core.getX() + 0.5, core.getY() + 1.5, core.getZ() + 0.5);
            world.playSound(loc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.5f, 1.2f);
            world.spawnParticle(Particle.TOTEM_OF_UNDYING, loc, 50, 0.5, 1.5, 0.5, 0.05);
            world.spawnParticle(Particle.HAPPY_VILLAGER, loc, 20, 0.5, 1.0, 0.5, 0);

            player.sendMessage(TextUtil.format("<green>✔ Guild Core upgraded to Tier " + nextTier +
                    "! New max claims: " + getMaxClaimsForTier(nextTier) + "</green>"));
            player.sendMessage(TextUtil.format("<green>Core HP: " + newMaxHp + "/" + newMaxHp + "</green>"));
        }

        DebugManager.log(DebugFlag.GUILD_CORE, "Core upgraded: team=" + teamId +
                " tier=" + nextTier + " by " + (player != null ? player.getName() : "system"));
        return true;
    }

    // ═══════════════════════════════════════════════
    //  GETTERS
    // ═══════════════════════════════════════════════
    public GuildCoreBlock getCoreForTeam(int teamId) {
        return coresByTeamId.get(teamId);
    }

    public GuildCoreBlock getCoreAtLocation(Location loc) {
        if (loc == null) return null;
        String key = loc.getWorld().getName() + ":" + loc.getBlockX() + ":" +
                loc.getBlockY() + ":" + loc.getBlockZ();
        Integer teamId = teamIdByLocationKey.get(key);
        if (teamId == null) return null;
        return coresByTeamId.get(teamId);
    }

    public boolean isCoreBlock(Location loc) {
        return getCoreAtLocation(loc) != null;
    }

    public int getMaxClaimsForTier(int tier) {
        return settingsManager.getInt("core.tier." + tier + ".claims_granted", tier * 5);
    }

    public Collection<GuildCoreBlock> getAllCores() {
        return Collections.unmodifiableCollection(coresByTeamId.values());
    }

    public boolean hasCore(int teamId) {
        GuildCoreBlock core = coresByTeamId.get(teamId);
        return core != null && !core.isDestroyed();
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
        for (Entity entity : world.getNearbyEntities(searchLoc, 3, 3, 3)) {
            if (entity instanceof ArmorStand stand &&
                    entity.getUniqueId().equals(core.getArmorStandUuid())) {
                stand.customName(buildCoreName(teamName, core.getCurrentHp(),
                        core.getMaxHp(), core.getTier()));
                break;
            }
        }
    }

    private Component buildCoreName(String teamName, int currentHp, int maxHp, int tier) {
        String hpColor;
        float hpPercent = maxHp > 0 ? (float) currentHp / maxHp : 0f;

        if (hpPercent > 0.66f) {
            hpColor = "green";
        } else if (hpPercent > 0.33f) {
            hpColor = "yellow";
        } else if (hpPercent > 0f) {
            hpColor = "red";
        } else {
            hpColor = "dark_red";
        }

        String miniMessage = "<gradient:#FFD700:#FFA500><b>⚔ " + teamName + " GUILD CORE</b></gradient> " +
                "<gray>|</gray> " +
                "<" + hpColor + ">HP: " + currentHp + "/" + maxHp + "</" + hpColor + "> " +
                "<gray>|</gray> <yellow>T" + tier + "</yellow>";

        return TextUtil.format(miniMessage);
    }

    public void refreshAllCoreDisplays() {
        for (GuildCoreBlock core : coresByTeamId.values()) {
            updateArmorStandName(core);
        }
    }

    // ═══════════════════════════════════════════════
    //  DATABASE OPERATIONS
    // ═══════════════════════════════════════════════
    public void loadAllCores() {
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM guild_cores");
             ResultSet rs = ps.executeQuery()) {

            coresByTeamId.clear();
            teamIdByLocationKey.clear();

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
                UUID asUuid = (asUuidStr != null && !asUuidStr.isEmpty()) ?
                        UUID.fromString(asUuidStr) : null;
                long placedAt = rs.getTimestamp("placed_at") != null ?
                        rs.getTimestamp("placed_at").getTime() :
                        System.currentTimeMillis();

                GuildCoreBlock core = new GuildCoreBlock(
                        teamId, worldName, x, y, z, tier, currentHp, maxHp, asUuid, placedAt
                );
                coresByTeamId.put(teamId, core);
                teamIdByLocationKey.put(core.getLocationKey(), teamId);
            }

            DebugManager.log(DebugFlag.GUILD_CORE, "Loaded " + coresByTeamId.size() +
                    " guild cores from database.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void saveAllCores() {
        for (GuildCoreBlock core : coresByTeamId.values()) {
            saveCoreToDbSync(core);
        }
        DebugManager.log(DebugFlag.GUILD_CORE, "Saved " + coresByTeamId.size() +
                " guild cores to database.");
    }

    private void saveCoreToDb(GuildCoreBlock core) {
        dbManager.executeAsync(() -> saveCoreToDbSync(core));
    }

    private void saveCoreToDbSync(GuildCoreBlock core) {
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT OR REPLACE INTO guild_cores (team_id, world, x, y, z, tier, current_hp, max_hp, armor_stand_uuid) " +
                             "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setInt(1, core.getTeamId());
            ps.setString(2, core.getWorld());
            ps.setInt(3, core.getX());
            ps.setInt(4, core.getY());
            ps.setInt(5, core.getZ());
            ps.setInt(6, core.getTier());
            ps.setInt(7, core.getCurrentHp());
            ps.setInt(8, core.getMaxHp());
            ps.setString(9, core.getArmorStandUuid() != null ?
                    core.getArmorStandUuid().toString() : null);
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void deleteCoreFromDb(int teamId) {
        dbManager.executeAsync(() -> {
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "DELETE FROM guild_cores WHERE team_id = ?")) {
                ps.setInt(1, teamId);
                ps.executeUpdate();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}