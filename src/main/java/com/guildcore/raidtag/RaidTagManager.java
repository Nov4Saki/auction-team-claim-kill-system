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
    //  TAG STATE
    // ═══════════════════════════════════════════════
    public static class RaidTagState {
        public int defendingTeamId;
        public String claimWorld;
        public int claimChunkX, claimChunkZ;
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
    }

    public static class CombatLogEntry {
        public UUID playerUuid;
        public Location disconnectLocation;
        public int teamId;
        public long disconnectTime;
        public UUID armorStandUuid;
        public ItemStack[] inventorySnapshot;
        public boolean resolved;

        public CombatLogEntry(UUID playerUuid, Location disconnectLocation, int teamId, ItemStack[] inventorySnapshot) {
            this.playerUuid = playerUuid;
            this.disconnectLocation = disconnectLocation;
            this.teamId = teamId;
            this.disconnectTime = System.currentTimeMillis();
            this.inventorySnapshot = inventorySnapshot;
            this.resolved = false;
        }
    }

    // ═══════════════════════════════════════════════
    //  APPLY / REMOVE TAG
    // ═══════════════════════════════════════════════
    public void applyRaidTag(Player attacker, int defendingTeamId, Chunk raidedChunk) {
        UUID uuid = attacker.getUniqueId();

        // Refresh existing tag
        RaidTagState existing = taggedPlayers.get(uuid);
        if (existing != null) {
            existing.tagAppliedAt = System.currentTimeMillis();
            existing.defendingTeamId = defendingTeamId;
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
        attacker.sendMessage(TextUtil.format("<red>Commands are disabled. Leave the territory to start the exit countdown.</red>"));

        // Notify defending team
        teamManager.broadcastToTeam(defendingTeamId, TextUtil.format("<red><b>⚠ RAID ALERT!</b> " + attacker.getName() + " is raiding your territory!</red>"));

        DebugManager.log(DebugFlag.RAID_TAG, "Raid tag applied to " + attacker.getName() + " (defending team: " + defendingTeamId + ")");
    }

    public boolean isRaidTagged(UUID uuid) {
        return taggedPlayers.containsKey(uuid);
    }

    public void removeRaidTag(UUID uuid) {
        RaidTagState removed = taggedPlayers.remove(uuid);
        if (removed != null) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.sendMessage(TextUtil.format("<green>✔ Your raid tag has expired. You are free.</green>"));
                player.sendActionBar(Component.empty());
            }
            DebugManager.log(DebugFlag.RAID_TAG, "Raid tag removed from " + uuid);
        }
    }

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

                player.sendActionBar(TextUtil.format("<red><b>⚔ RAID TAGGED</b></red> <gray>|</gray> <yellow>" + timerInfo + "</yellow>"));
            }

            for (UUID uuid : expired) {
                removeRaidTag(uuid);
            }

            return true;
        }, 0L, 20L);
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
        boolean inDefenderTerritory = claim != null && claim.isTeamClaim() && claim.getTeamId() == state.defendingTeamId;

        if (inDefenderTerritory) {
            state.insideRaidedChunk = true;
            state.exitTimerStart = null;
        } else {
            if (state.insideRaidedChunk) {
                state.insideRaidedChunk = false;
                state.exitTimerStart = System.currentTimeMillis();
                player.sendMessage(TextUtil.format("<yellow>⚔ You left raided territory. Exit countdown started!</yellow>"));
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!isRaidTagged(player.getUniqueId())) return;
        if (!settingsManager.getBoolean("raidtag.disable_commands", true)) return;

        String cmd = event.getMessage().toLowerCase();
        // Allow /msg and /r
        if (cmd.startsWith("/msg ") || cmd.startsWith("/r ") || cmd.startsWith("/reply ")) return;

        event.setCancelled(true);
        player.sendMessage(TextUtil.format("<red>✖ Commands are disabled while raid tagged!</red>"));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        RaidTagState state = taggedPlayers.get(player.getUniqueId());
        if (state == null) return;

        // Allow cobweb in raided claim
        if (settingsManager.getBoolean("raidtag.allow_cobweb", true)) {
            if (event.getBlock().getType() == Material.COBWEB) {
                ClaimInfo claim = claimManager.getClaimAt(event.getBlock().getChunk());
                if (claim != null && claim.isTeamClaim() && claim.getTeamId() == state.defendingTeamId) {
                    return; // Allow cobweb
                }
            }
        }

        event.setCancelled(true);
        player.sendActionBar(TextUtil.format("<red>You cannot place blocks while raid tagged!</red>"));
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
        CombatLogEntry entry = new CombatLogEntry(uuid, player.getLocation().clone(), state.defendingTeamId, invSnapshot);

        // Spawn armor stand
        Location loc = player.getLocation();
        ArmorStand stand = loc.getWorld().spawn(loc, ArmorStand.class, as -> {
            as.setVisible(true);
            as.setGravity(false);
            as.setInvulnerable(false);
            as.setCustomNameVisible(true);
            as.customName(TextUtil.format("<red><b>⚔ COMBAT LOG: " + player.getName() + "</b></red>"));
            as.setBasePlate(true);
            // Set player head
            as.getEquipment().setHelmet(new ItemStack(Material.PLAYER_HEAD));
        });

        entry.armorStandUuid = stand.getUniqueId();
        combatLogEntries.put(uuid, entry);

        // Schedule punishment
        int disconnectTimer = settingsManager.getInt("raidtag.disconnect_timer_sec", 60);
        scheduler.runLater(null, () -> {
            CombatLogEntry logEntry = combatLogEntries.remove(uuid);
            if (logEntry == null || logEntry.resolved) return;
            logEntry.resolved = true;

            // Drop inventory at location
            if (settingsManager.getBoolean("raidtag.drop_inv_on_expire", true)) {
                if (logEntry.inventorySnapshot != null) {
                    for (ItemStack item : logEntry.inventorySnapshot) {
                        if (item != null && item.getType() != Material.AIR) {
                            logEntry.disconnectLocation.getWorld().dropItemNaturally(logEntry.disconnectLocation, item);
                        }
                    }
                }

                // Clear the player's inventory if they're offline
                Player offlineCheck = Bukkit.getPlayer(uuid);
                if (offlineCheck != null && offlineCheck.isOnline()) {
                    offlineCheck.getInventory().clear();
                }
            }

            // Kill the armor stand
            killCombatLogStand(logEntry);

            // Award kill credit
            if (settingsManager.getBoolean("raidtag.award_kill_credit", true) && state.lastDamagerUuid != null) {
                Player killer = Bukkit.getPlayer(state.lastDamagerUuid);
                if (killer != null && killer.isOnline()) {
                    killer.sendMessage(TextUtil.format("<green>⚔ " + player.getName() + " combat logged and their items were dropped!</green>"));
                }
                DebugManager.log(DebugFlag.RAID_TAG, "Combat log penalty applied to " + player.getName() + " (kill credit to " + state.lastDamagerUuid + ")");
            }

            taggedPlayers.remove(uuid);
        }, disconnectTimer * 20L);

        teamManager.broadcastToTeam(state.defendingTeamId, TextUtil.format("<red>⚔ " + player.getName() + " COMBAT LOGGED! Their stand will drop loot in " + disconnectTimer + "s.</red>"));
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
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (!(event.getDamager() instanceof Player attacker)) return;

        // Check if victim is standing in their own team's claim
        Chunk victimChunk = victim.getLocation().getChunk();
        ClaimInfo claim = claimManager.getClaimAt(victimChunk);
        if (claim == null || !claim.isTeamClaim()) return;

        Team victimTeam = teamManager.getPlayerTeam(victim.getUniqueId());
        if (victimTeam == null || victimTeam.getId() != claim.getTeamId()) return;

        // Don't tag same-team members
        if (teamManager.areSameTeam(attacker.getUniqueId(), victim.getUniqueId())) return;

        // Check shield
        if (offlineShieldManager.isShieldActive(claim.getTeamId())) return;

        // Apply raid tag to the attacker
        applyRaidTag(attacker, claim.getTeamId(), victimChunk);

        // Update damager on existing tag for victim's defense
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
                        for (ItemStack item : logEntry.inventorySnapshot) {
                            if (item != null && item.getType() != Material.AIR) {
                                stand.getLocation().getWorld().dropItemNaturally(stand.getLocation(), item);
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

    private void killCombatLogStand(CombatLogEntry entry) {
        if (entry.armorStandUuid == null || entry.disconnectLocation == null) return;
        for (Entity entity : entry.disconnectLocation.getWorld().getNearbyEntities(entry.disconnectLocation, 5, 5, 5)) {
            if (entity instanceof ArmorStand && entity.getUniqueId().equals(entry.armorStandUuid)) {
                entity.remove();
                break;
            }
        }
    }
}
