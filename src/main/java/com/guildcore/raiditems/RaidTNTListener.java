// FILE: src/main/java/com/guildcore/raiditems/RaidTNTListener.java
package com.guildcore.raiditems;

import com.guildcore.claims.ClaimInfo;
import com.guildcore.claims.ClaimManager;
import com.guildcore.config.SettingsManager;
import com.guildcore.core.GuildCoreBlock;
import com.guildcore.core.GuildCoreManager;
import com.guildcore.debug.DebugFlag;
import com.guildcore.debug.DebugManager;
import com.guildcore.raidtag.RaidTagManager;
import com.guildcore.scheduler.SchedulerWrapper;
import com.guildcore.shield.OfflineShieldManager;
import com.guildcore.teams.Team;
import com.guildcore.teams.TeamManager;
import com.guildcore.util.TextUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.Iterator;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RaidTNTListener implements Listener {
    private final RaidItemManager raidItemManager;
    private final ClaimManager claimManager;
    private final GuildCoreManager guildCoreManager;
    private final OfflineShieldManager offlineShieldManager;
    private final SettingsManager settingsManager;
    private final SchedulerWrapper scheduler;
    private RaidTagManager raidTagManager;
    private TeamManager teamManager;

    private final Set<UUID> trackedTntUuids = ConcurrentHashMap.newKeySet();
    private static final NamespacedKey TNT_MARKER = new NamespacedKey("guildcore", "raid_tnt");
    private static final NamespacedKey TNT_TEAM_KEY = new NamespacedKey("guildcore", "tnt_team_id");

    public RaidTNTListener(RaidItemManager raidItemManager, ClaimManager claimManager,
                           GuildCoreManager guildCoreManager, OfflineShieldManager offlineShieldManager,
                           SettingsManager settingsManager, SchedulerWrapper scheduler) {
        this.raidItemManager = raidItemManager;
        this.claimManager = claimManager;
        this.guildCoreManager = guildCoreManager;
        this.offlineShieldManager = offlineShieldManager;
        this.settingsManager = settingsManager;
        this.scheduler = scheduler;
    }

    public void setRaidTagManager(RaidTagManager raidTagManager) { this.raidTagManager = raidTagManager; }
    public void setTeamManager(TeamManager teamManager) { this.teamManager = teamManager; }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = false)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;

        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null || raidItemManager.getRaidItemType(item) != RaidItemManager.RaidItemType.RAID_TNT) {
            item = player.getInventory().getItemInMainHand();
            if (raidItemManager.getRaidItemType(item) != RaidItemManager.RaidItemType.RAID_TNT) {
                item = player.getInventory().getItemInOffHand();
            }
        }
        RaidItemManager.RaidItemType type = raidItemManager.getRaidItemType(item);
        if (type != RaidItemManager.RaidItemType.RAID_TNT) return;

        // ALWAYS cancel the event to prevent vanilla TNT placement
        event.setCancelled(true);

        Block clicked = event.getClickedBlock();
        Chunk chunk = clicked.getChunk();
        ClaimInfo claim = claimManager.getClaimAt(chunk);

        int defendingTeamId = -1;
        boolean isOwnClaim = false;

        if (claim != null && claim.isTeamClaim()) {
            defendingTeamId = claim.getTeamId() != null ? claim.getTeamId() : -1;

            // Check if own team
            if (teamManager != null) {
                Team playerTeam = teamManager.getPlayerTeam(player.getUniqueId());
                if (playerTeam != null && playerTeam.getId() == defendingTeamId) {
                    player.sendActionBar(Component.text("💣 You cannot use Raid TNT on your own territory!", NamedTextColor.RED));
                    return;
                }
            }

            // Check shield
            if (offlineShieldManager.isShieldActive(defendingTeamId)) {
                player.sendActionBar(Component.text("🛡 This territory is protected by an Offline Shield!", NamedTextColor.AQUA));
                return;
            }
        }

        // Consume 1 item
        item.setAmount(item.getAmount() - 1);

        // Spawn primed TNT
        double fuseSeconds = settingsManager.getDouble("raidtnt.fuse_seconds", 3.0);
        Location spawnLoc = clicked.getLocation().add(0.5, 1.0, 0.5);

        final int finalTeamId = defendingTeamId;
        TNTPrimed tnt = clicked.getWorld().spawn(spawnLoc, TNTPrimed.class, t -> {
            t.setFuseTicks((int) (fuseSeconds * 20));
            t.setSource(player);
            t.getPersistentDataContainer().set(TNT_MARKER, PersistentDataType.BOOLEAN, true);
            t.getPersistentDataContainer().set(TNT_TEAM_KEY, PersistentDataType.INTEGER, finalTeamId);
        });

        trackedTntUuids.add(tnt.getUniqueId());

        player.playSound(player.getLocation(), Sound.ENTITY_TNT_PRIMED, 1.5f, 1.0f);
        player.sendActionBar(TextUtil.format("<red>💣 Raid TNT placed! Fuse: " + String.format("%.1f", fuseSeconds) + "s</red>"));

        // Apply raid tag only if in enemy claim
        if (raidTagManager != null && defendingTeamId > 0) {
            raidTagManager.applyRaidTag(player, defendingTeamId, chunk);
        }

        DebugManager.log(DebugFlag.RAID_ITEMS, player.getName() + " placed Raid TNT at " + spawnLoc +
                " (team: " + defendingTeamId + ")");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        Entity entity = event.getEntity();
        if (!trackedTntUuids.remove(entity.getUniqueId())) return;

        // No player damage
        event.setYield(0);

        int defendingTeamId = entity.getPersistentDataContainer()
                .getOrDefault(TNT_TEAM_KEY, PersistentDataType.INTEGER, -1);

        Location explosionCenter = event.getLocation();

        // Apply block transformation chain
        Iterator<Block> it = event.blockList().iterator();
        while (it.hasNext()) {
            Block block = it.next();

            // Always protect claim chests
            if (isClaimChest(block)) {
                it.remove();
                continue;
            }

            // Protect core blocks
            GuildCoreBlock core = guildCoreManager.getCoreAtLocation(block.getLocation());
            if (core != null) {
                defendingTeamId = core.getTeamId();
                it.remove();
                continue;
            }

            // Apply transformation chain
            Material blockType = block.getType();
            Material transformed = getTransformation(blockType);
            it.remove();

            if (transformed != null) {
                block.setType(transformed);
                World w = block.getWorld();
                Location loc = block.getLocation();
                if (transformed != Material.AIR) {
                    w.spawnParticle(Particle.BLOCK_CRUMBLE, loc.clone().add(0.5, 0.5, 0.5), 12, 0.3, 0.3, 0.3, transformed.createBlockData());
                    w.playSound(loc, Sound.BLOCK_STONE_BREAK, 0.8f, 0.9f);
                } else {
                    w.spawnParticle(Particle.BLOCK_CRUMBLE, loc.clone().add(0.5, 0.5, 0.5), 8, 0.3, 0.3, 0.3, blockType.createBlockData());
                }
            }
        }

        // Apply core damage if in enemy claim
        if (defendingTeamId > 0) {
            int coreDamage = settingsManager.getInt("core.raid_tnt_damage", 10);
            GuildCoreBlock core = guildCoreManager.getCoreForTeam(defendingTeamId);
            if (core != null) {
                Location coreLoc = new Location(explosionCenter.getWorld(),
                        core.getX(), core.getY(), core.getZ());
                if (coreLoc.distance(explosionCenter) <= 8.0) {
                    guildCoreManager.damageCore(defendingTeamId, coreDamage, null);
                    DebugManager.log(DebugFlag.RAID_ITEMS,
                            "Raid TNT dealt " + coreDamage + " damage to core of team " + defendingTeamId);
                }
            }
        }
    }

    private boolean isClaimChest(Block block) {
        try {
            Class<?> ccmClass = Class.forName("com.guildcore.claims.ClaimChestManager");
            Object plugin = com.guildcore.GuildCorePlugin.getInstance();
            if (plugin != null) {
                java.lang.reflect.Method m = plugin.getClass().getMethod("getClaimChestManager");
                Object ccm = m.invoke(plugin);
                if (ccm != null) {
                    java.lang.reflect.Method isCc = ccm.getClass().getMethod("isClaimChest", Location.class);
                    return (boolean) isCc.invoke(ccm, block.getLocation());
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    private Material getTransformation(Material type) {
        if (type == null || type.isAir()) return null;
        String name = type.name();

        if (type == Material.REINFORCED_DEEPSLATE) return Material.OBSIDIAN;
        if (type == Material.OBSIDIAN) return Material.CRYING_OBSIDIAN;
        if (type == Material.CRYING_OBSIDIAN) return Material.COBBLESTONE;
        if (name.contains("DEEPSLATE") && !name.contains("COBBLED")) return Material.COBBLED_DEEPSLATE;
        if (type == Material.COBBLED_DEEPSLATE) return Material.AIR;
        if (name.contains("STONE_BRICK") || name.contains("SMOOTH_STONE") || type == Material.STONE) return Material.COBBLESTONE;
        if (type == Material.COBBLESTONE) return Material.AIR;

        return Material.AIR;
    }
}