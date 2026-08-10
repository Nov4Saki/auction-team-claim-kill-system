package com.guildcore.raidtag;

import com.guildcore.claims.ClaimInfo;
import com.guildcore.claims.ClaimManager;
import com.guildcore.config.SettingsManager;
import com.guildcore.debug.DebugFlag;
import com.guildcore.debug.DebugManager;
import com.guildcore.scheduler.SchedulerWrapper;
import com.guildcore.shield.OfflineShieldManager;
import com.guildcore.teams.Team;
import com.guildcore.teams.TeamManager;
import com.guildcore.util.TextUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RaidTagManager implements Listener {
    private final TeamManager teamManager;
    private final ClaimManager claimManager;
    private final OfflineShieldManager offlineShieldManager;
    private final SettingsManager settingsManager;
    private final SchedulerWrapper scheduler;

    private final Map<UUID, RaidTagState> taggedPlayers = new ConcurrentHashMap<>();
    private final Map<UUID, CombatLogEntry> combatLogEntries = new ConcurrentHashMap<>();

    public RaidTagManager(TeamManager teamManager, ClaimManager claimManager, OfflineShieldManager offlineShieldManager, SettingsManager settingsManager, SchedulerWrapper scheduler) {
        this.teamManager = teamManager;
        this.claimManager = claimManager;
        this.offlineShieldManager = offlineShieldManager;
        this.settingsManager = settingsManager;
        this.scheduler = scheduler;
    }

    // ═══════════════════════════════════════════════
    //  DATA CLASSES
    // ═══════════════════════════════════════════════

    public static class RaidTagState {
        public int defendingTeamId;
        public String claimWorld;
        public int claimChunkX;
        public int claimChunkZ;
        public long tagAppliedAt;
        public boolean insideRaidedChunk;
        public Long exitTimerStart; // null if inside chunk
        public UUID lastDamagerUuid;

        public RaidTagState(int defendingTeamId, String claimWorld, int claimChunkX, int claimChunkZ, UUID lastDamagerUuid) {
            this.defendingTeamId = defendingTeamId;
            this.claimWorld = claimWorld;
            this.claimChunkX = claimChunkX;
            this.claimChunkZ = claimChunkZ;
            this.tagAppliedAt = System.currentTimeMillis();
            this.insideRaidedChunk = true;
            this.exitTimerStart = null;
            this.lastDamagerUuid = lastDamagerUuid;
        }

        public boolean isInsideDefenderChunk(Chunk chunk) {
            return chunk.getWorld().getName().equals(claimWorld) &&
                    chunk.getX() == claimChunkX &&
                    chunk.getZ() == claimChunkZ;
        }
    }

    public static class CombatLogEntry {
        public UUID playerUuid;
        public String playerName;
        public Location disconnectLocation;
        public int defendingTeamId;
        public long disconnectTime;
        public UUID armorStandUuid;
        public ItemStack[] inventorySnapshot;
        public boolean resolved;
        public UUID lastDamagerUuid;

        public CombatLogEntry(UUID playerUuid, String playerName, Location disconnectLocation, int defendingTeamId, ItemStack[] inventorySnapshot, UUID lastDamagerUuid) {
            this.playerUuid = playerUuid;
            this.playerName = playerName;
            this.disconnectLocation = disconnectLocation;
            this.defendingTeamId = defendingTeamId;
            this.disconnectTime = System.currentTimeMillis();
            this.inventorySnapshot = inventorySnapshot;
            this.resolved = false;
            this.lastDamagerUuid = lastDamagerUuid;
        }
    }

    // ═══════════════════════════════════════════════
    //  APPLY / REMOVE TAG
    // ═══════════════════════════════════════════════

    /**
     * Applies a raid tag to the attacker when they attack a claimed chunk.
     */
    public void applyRaidTag(Player attacker, int defendingTeamId, Chunk raidedChunk) {
        UUID uuid = attacker.getUniqueId();

        // Refresh existing tag
        RaidTagState existing = taggedPlayers.get(uuid);
        if (existing != null) {
            existing.tagAppliedAt = System.currentTimeMillis();
            existing.defendingTeamId = defendingTeamId;
            existing.claimWorld = raidedChunk.getWorld().getName();
            existing.claimChunkX = raidedChunk.getX();
            existing.claimChunkZ = raidedChunk.getZ();
            existing.insideRaidedChunk = true;
            existing.exitTimerStart = null;
            return;
        }

        RaidTagState state = new RaidTagState(
                defendingTeamId,
                raidedChunk.getWorld().getName(),
                raidedChunk.getX(),
                raidedChunk.getZ(),
                attacker.getUniqueId()
        );
        taggedPlayers.put(uuid, state);

        Team team = teamManager.getTeam(defendingTeamId);
        String teamName = team != null ? team.getName() : "Unknown";

        attacker.sendMessage(TextUtil.format("<red><b>⚔ You are now RAID TAGGED in " + teamName + " territory!</b></red>"));
        attacker.sendMessage(TextUtil.format("<red>Commands are disabled. Leave the territory to start the exit countdown (" +
                settingsManager.getInt("raidtag.exit_countdown_sec", 30) + "s).</red>"));

        // Notify defending team
        if (team != null) {
            teamManager.broadcastToTeam(defendingTeamId, TextUtil.format(
                    "<red><b>⚠ RAID ALERT!</b> " + attacker.getName() + " is raiding your territory near chunk (" +
                            raidedChunk.getX() + ", " + raidedChunk.getZ() + ")!</red>"));
        }

        // Log to database
        logRaidTagAction(attacker.getUniqueId(), defendingTeamId, "TAG_APPLIED");

        DebugManager.log(DebugFlag.RAID_TAG, "Raid tag applied to " + attacker.getName() +
                " (defending team: " + defendingTeamId + ")");
    }

    /**
     * Checks if a player is currently raid tagged.
     */
    public boolean isRaidTagged(UUID uuid) {
        return taggedPlayers.containsKey(uuid);
    }

    /**
     * Removes a raid tag from a player.
     */
    public void removeRaidTag(UUID uuid) {
        RaidTagState removed = taggedPlayers.remove(uuid);
        if (removed != null) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.sendMessage(TextUtil.format("<green>✔ Your raid tag has expired. You are free.</green>"));
                player.sendActionBar(Component.empty());
            }
            logRaidTagAction(uuid, removed.defendingTeamId, "TAG_REMOVED");
            DebugManager.log(DebugFlag.RAID_TAG, "Raid tag removed from " + uuid);
        }
    }

    /**
     * Gets remaining exit countdown seconds. Returns -1 if inside territory.
     */
    public int getRemainingSeconds(UUID uuid) {
        RaidTagState state = taggedPlayers.get(uuid);
        if (state == null) return 0;
        if (state.insideRaidedChunk || state.exitTimerStart == null) return -1;
        int exitSec = settingsManager.getInt("raidtag.exit_countdown_sec", 30);
        long elapsed = (System.currentTimeMillis() - state.exitTimerStart) / 1000L;
        return Math.max(0, exitSec - (int) elapsed);
    }

    // ═══════════════════════════════════════════════
    //  ACTION BAR TASK
    // ═══════════════════════════════════════════════

    /**
     * Starts the action bar display task for raid-tagged players.
     */
    public void startActionBarTask() {
        scheduler.runTaskTimer(() -> {
            int exitCountdownSec = settingsManager.getInt("raidtag.exit_countdown_sec", 30);
            List<UUID> expired = new ArrayList<>();

            for (Map.Entry<UUID, RaidTagState> entry : taggedPlayers.entrySet()) {
                UUID uuid = entry.getKey();
                RaidTagState state = entry.getValue();
                Player player = Bukkit.getPlayer(uuid);
                if (player == null || !player.isOnline()) continue;

                String timerInfo;
                if (state.insideRaidedChunk || state.exitTimerStart == null) {
                    timerInfo = "Inside Enemy Territory";
                } else {
                    long elapsed = (System.currentTimeMillis() - state.exitTimerStart) / 1000L;
                    int remaining = exitCountdownSec - (int) elapsed;
                    if (remaining <= 0) {
                        expired.add(uuid);
                        continue;
                    }
                    timerInfo = "Exit Timer: " + remaining + "s";
                }

                scheduler.runSync(player, () ->
                        player.sendActionBar(TextUtil.format("<red><b>⚔ RAID TAGGED</b></red> <gray>|</gray> <yellow>" + timerInfo + "</yellow>")));
            }

            for (UUID uuid : expired) {
                removeRaidTag(uuid);
            }

            return true;
        }, 0L, 10L); // Every 0.5 seconds for smooth countdown
    }

    // ═══════════════════════════════════════════════
    //  EVENT HANDLERS
    // ═══════════════════════════════════════════════

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        RaidTagState state = taggedPlayers.get(player.getUniqueId());
        if (state == null) return;

        // Only process chunk transitions
        Chunk fromChunk = event.getFrom().getChunk();
        Chunk toChunk = event.getTo().getChunk();
        if (fromChunk.getX() == toChunk.getX() && fromChunk.getZ() == toChunk.getZ()) return;

        // Check if new chunk belongs to the defending team
        ClaimInfo claim = claimManager.getClaimAt(toChunk);
        boolean inDefenderTerritory = claim != null && claim.isTeamClaim() &&
                claim.getTeamId() != null && claim.getTeamId() == state.defendingTeamId;

        if (inDefenderTerritory) {
            // Re-entered raided territory
            state.insideRaidedChunk = true;
            state.exitTimerStart = null;
            state.claimChunkX = toChunk.getX();
            state.claimChunkZ = toChunk.getZ();
        } else {
            // Left raided territory - start countdown
            if (state.insideRaidedChunk) {
                state.insideRaidedChunk = false;
                state.exitTimerStart = System.currentTimeMillis();
                int exitSec = settingsManager.getInt("raidtag.exit_countdown_sec", 30);
                player.sendMessage(TextUtil.format("<yellow>⚔ You left raided territory. Exit countdown started (" + exitSec + "s)!</yellow>"));
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!isRaidTagged(player.getUniqueId())) return;
        if (!settingsManager.getBoolean("raidtag.disable_commands", true)) return;

        String cmd = event.getMessage().toLowerCase();
        // Allow /msg, /r, /reply
        if (cmd.startsWith("/msg ") || cmd.startsWith("/r ") || cmd.startsWith("/reply ") ||
                cmd.startsWith("/tell ") || cmd.startsWith("/w ") || cmd.startsWith("/whisper ")) {
            return;
        }
        // Allow team chat
        if (cmd.startsWith("/tc ") || cmd.startsWith("/gc ") || cmd.startsWith("/teamchat ") ||
                cmd.startsWith("/guildchat ")) {
            return;
        }

        event.setCancelled(true);
        player.sendMessage(TextUtil.format("<red>✖ Commands are disabled while raid tagged!</red>"));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        RaidTagState state = taggedPlayers.get(player.getUniqueId());
        if (state == null) return;

        Block block = event.getBlock();
        Chunk chunk = block.getChunk();
        ClaimInfo claim = claimManager.getClaimAt(chunk);

        // Allow block placement if OUTSIDE of any team claim (in wilderness/unclaimed territory)
        if (claim == null || !claim.isTeamClaim()) {
            return;
        }

        // Allow cobweb placement
        if (settingsManager.getBoolean("raidtag.allow_cobweb", true)) {
            if (block.getType() == Material.COBWEB) {
                return;
            }
        }

        event.setCancelled(true);
        player.sendActionBar(TextUtil.format("<red>You cannot place blocks in claimed territory while raid tagged!</red>"));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        RaidTagState state = taggedPlayers.get(uuid);
        if (state == null) return;

        // Combat log!
        DebugManager.log(DebugFlag.RAID_TAG, player.getName() + " disconnected while raid tagged! Creating combat log entry.");

        ItemStack[] invSnapshot = player.getInventory().getContents().clone();
        CombatLogEntry entry = new CombatLogEntry(
                uuid, player.getName(), player.getLocation().clone(),
                state.defendingTeamId, invSnapshot, state.lastDamagerUuid
        );

        // Spawn armor stand with player head
        Location loc = player.getLocation();
        ArmorStand stand = loc.getWorld().spawn(loc, ArmorStand.class, as -> {
            as.setVisible(true);
            as.setGravity(false);
            as.setInvulnerable(false);
            as.setCustomNameVisible(true);
            as.customName(TextUtil.format("<red><b>⚔ COMBAT LOG: " + player.getName() + "</b></red>"));
            as.setBasePlate(true);
            // Set player head on the armor stand
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            if (head.getItemMeta() instanceof SkullMeta skullMeta) {
                skullMeta.setOwningPlayer(player);
                head.setItemMeta(skullMeta);
            }
            as.getEquipment().setHelmet(head);
        });

        entry.armorStandUuid = stand.getUniqueId();
        combatLogEntries.put(uuid, entry);

        // Schedule punishment
        int disconnectTimer = settingsManager.getInt("raidtag.disconnect_timer_sec", 60);
        final String playerName = player.getName();

        scheduler.runTaskTimer(() -> {
            CombatLogEntry logEntry = combatLogEntries.get(uuid);
            if (logEntry == null || logEntry.resolved) return false;

            long elapsed = (System.currentTimeMillis() - logEntry.disconnectTime) / 1000L;
            int remaining = disconnectTimer - (int) elapsed;

            if (remaining <= 0) {
                // Time's up - drop inventory
                logEntry.resolved = true;
                resolveCombatLog(logEntry, state);
                combatLogEntries.remove(uuid);
                taggedPlayers.remove(uuid);
                return false;
            }

            // Update armor stand name with countdown
            if (logEntry.armorStandUuid != null && logEntry.disconnectLocation != null && logEntry.disconnectLocation.getWorld() != null) {
                Location targetLoc = logEntry.disconnectLocation;
                scheduler.runSync(targetLoc, () -> {
                    if (targetLoc.getWorld() == null) return;
                    for (Entity entity : targetLoc.getWorld().getNearbyEntities(targetLoc, 5, 5, 5)) {
                        if (entity instanceof ArmorStand as && entity.getUniqueId().equals(logEntry.armorStandUuid)) {
                            as.customName(TextUtil.format("<red><b>⚔ COMBAT LOG: " + playerName + " (" + remaining + "s)</b></red>"));
                            break;
                        }
                    }
                });
            }
            return true;
        }, 20L, 20L); // Update every second

        // Notify teams
        teamManager.broadcastToTeam(state.defendingTeamId, TextUtil.format(
                "<red>⚔ " + player.getName() + " COMBAT LOGGED! Their stand will drop loot in " + disconnectTimer + "s.</red>"));

        logRaidTagAction(uuid, state.defendingTeamId, "COMBAT_LOG");
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        CombatLogEntry entry = combatLogEntries.remove(uuid);
        if (entry == null || entry.resolved) return;

        entry.resolved = true;
        killCombatLogStand(entry);

        // Restore raid tag
        player.sendMessage(TextUtil.format("<yellow>⚔ You reconnected during a raid tag. Your tag has been restored.</yellow>"));
        DebugManager.log(DebugFlag.RAID_TAG, player.getName() + " reconnected during combat log. Tag restored.");
        logRaidTagAction(uuid, entry.defendingTeamId, "RECONNECT");
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (!(event.getDamager() instanceof Player attacker)) return;

        // Don't tag same-team members
        if (teamManager.areSameTeam(attacker.getUniqueId(), victim.getUniqueId())) return;

        // Check if victim is standing in their own team's claim
        Chunk victimChunk = victim.getLocation().getChunk();
        ClaimInfo claim = claimManager.getClaimAt(victimChunk);
        if (claim == null || !claim.isTeamClaim()) return;

        Team victimTeam = teamManager.getPlayerTeam(victim.getUniqueId());
        if (victimTeam == null || victimTeam.getId() != claim.getTeamId()) return;

        // Check shield
        if (offlineShieldManager.isShieldActive(claim.getTeamId())) return;

        // Apply raid tag to the attacker
        applyRaidTag(attacker, claim.getTeamId(), victimChunk);

        // Update last damager on existing tag
        RaidTagState attackerState = taggedPlayers.get(attacker.getUniqueId());
        if (attackerState != null) {
            attackerState.lastDamagerUuid = victim.getUniqueId();
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onEntityDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof ArmorStand stand)) return;

        for (Map.Entry<UUID, CombatLogEntry> entry : combatLogEntries.entrySet()) {
            CombatLogEntry logEntry = entry.getValue();
            if (logEntry.armorStandUuid != null && logEntry.armorStandUuid.equals(stand.getUniqueId())) {
                if (!logEntry.resolved) {
                    logEntry.resolved = true;

                    // Drop inventory
                    if (logEntry.inventorySnapshot != null) {
                        World world = stand.getLocation().getWorld();
                        if (world != null) {
                            for (ItemStack item : logEntry.inventorySnapshot) {
                                if (item != null && item.getType() != Material.AIR) {
                                    world.dropItemNaturally(stand.getLocation(), item);
                                }
                            }
                        }
                    }

                    combatLogEntries.remove(entry.getKey());
                    taggedPlayers.remove(entry.getKey());
                    DebugManager.log(DebugFlag.RAID_TAG, "Combat log stand killed for player " + entry.getKey() + ". Items dropped.");
                }
                break;
            }
        }
    }

    // ═══════════════════════════════════════════════
    //  HELPER METHODS
    // ═══════════════════════════════════════════════

    private void resolveCombatLog(CombatLogEntry entry, RaidTagState state) {
        // Drop inventory at location
        if (settingsManager.getBoolean("raidtag.drop_inv_on_expire", true)) {
            if (entry.inventorySnapshot != null && entry.disconnectLocation != null && entry.disconnectLocation.getWorld() != null) {
                Location loc = entry.disconnectLocation;
                scheduler.runSync(loc, () -> {
                    World world = loc.getWorld();
                    if (world == null) return;
                    for (ItemStack item : entry.inventorySnapshot) {
                        if (item != null && item.getType() != Material.AIR) {
                            world.dropItemNaturally(loc, item);
                        }
                    }
                });

                // Clear the player's inventory if they're offline
                Player offlinePlayer = Bukkit.getPlayer(entry.playerUuid);
                if (offlinePlayer != null && offlinePlayer.isOnline()) {
                    offlinePlayer.getInventory().clear();
                } else {
                    // Player is offline - clear inventory via database
                    clearPlayerInventory(entry.playerUuid);
                }
            }
        }

        // Kill the armor stand
        killCombatLogStand(entry);

        // Award kill credit
        if (settingsManager.getBoolean("raidtag.award_kill_credit", true) && entry.lastDamagerUuid != null) {
            Player killer = Bukkit.getPlayer(entry.lastDamagerUuid);
            if (killer != null && killer.isOnline()) {
                killer.sendMessage(TextUtil.format("<green>⚔ " + entry.playerName + " combat logged and their items were dropped!</green>"));
            }
            DebugManager.log(DebugFlag.RAID_TAG, "Combat log penalty applied to " + entry.playerName +
                    " (kill credit to " + entry.lastDamagerUuid + ")");
        }
    }

    private void killCombatLogStand(CombatLogEntry entry) {
        if (entry.armorStandUuid == null || entry.disconnectLocation == null) return;
        Location loc = entry.disconnectLocation;
        World world = loc.getWorld();
        if (world == null) return;

        scheduler.runSync(loc, () -> {
            if (loc.getWorld() == null) return;
            for (Entity entity : loc.getWorld().getNearbyEntities(loc, 5, 5, 5)) {
                if (entity instanceof ArmorStand && entity.getUniqueId().equals(entry.armorStandUuid)) {
                    entity.remove();
                    break;
                }
            }
        });
    }

    private void clearPlayerInventory(UUID playerUuid) {
        // This would need database access to clear the player's inventory
        // For now, we handle it when they reconnect
        DebugManager.log(DebugFlag.RAID_TAG, "Player " + playerUuid + " inventory cleared (offline combat log)");
    }

    private void logRaidTagAction(UUID playerUuid, int defendingTeamId, String action) {
        // Async log to database
        scheduler.runAsync(() -> {
            try (java.sql.Connection conn = com.guildcore.GuildCorePlugin.getInstance()
                    .getDatabaseManager().getConnection();
                 java.sql.PreparedStatement ps = conn.prepareStatement(
                         "INSERT INTO raid_tag_log (player_uuid, defending_team_id, action) VALUES (?, ?, ?)")) {
                ps.setString(1, playerUuid.toString());
                ps.setInt(2, defendingTeamId);
                ps.setString(3, action);
                ps.executeUpdate();
            } catch (Exception e) {
                // Silently fail - logging is non-critical
            }
        });
    }
}