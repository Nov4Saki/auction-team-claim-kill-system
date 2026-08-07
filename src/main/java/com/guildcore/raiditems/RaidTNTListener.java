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
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
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
    private static final org.bukkit.NamespacedKey TNT_MARKER = new org.bukkit.NamespacedKey("guildcore", "raid_tnt");

    public RaidTNTListener(RaidItemManager raidItemManager, ClaimManager claimManager, GuildCoreManager guildCoreManager, OfflineShieldManager offlineShieldManager, SettingsManager settingsManager, SchedulerWrapper scheduler) {
        this.raidItemManager = raidItemManager;
        this.claimManager = claimManager;
        this.guildCoreManager = guildCoreManager;
        this.offlineShieldManager = offlineShieldManager;
        this.settingsManager = settingsManager;
        this.scheduler = scheduler;
    }

    public void setRaidTagManager(RaidTagManager raidTagManager) {
        this.raidTagManager = raidTagManager;
    }

    public void setTeamManager(TeamManager teamManager) {
        this.teamManager = teamManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        RaidItemManager.RaidItemType type = raidItemManager.getRaidItemType(item);
        if (type != RaidItemManager.RaidItemType.RAID_TNT) return;

        Block clicked = event.getClickedBlock();
        Chunk chunk = clicked.getChunk();
        ClaimInfo claim = claimManager.getClaimAt(chunk);

        if (claim == null || !claim.isTeamClaim()) {
            player.sendActionBar(Component.text("💣 Raid TNT can only be placed in claimed territory!", NamedTextColor.RED));
            return;
        }

        // Check not own team
        if (teamManager != null) {
            Team playerTeam = teamManager.getPlayerTeam(player.getUniqueId());
            if (playerTeam != null && playerTeam.getId() == claim.getTeamId()) {
                player.sendActionBar(Component.text("💣 You can't use Raid TNT on your own territory!", NamedTextColor.RED));
                return;
            }
        }

        if (offlineShieldManager.isShieldActive(claim.getTeamId())) {
            event.setCancelled(true);
            player.sendActionBar(Component.text("🛡 This territory is protected by an Offline Shield!", NamedTextColor.AQUA));
            return;
        }

        event.setCancelled(true);

        // Consume 1 item
        item.setAmount(item.getAmount() - 1);

        // Spawn primed TNT
        double fuseSeconds = settingsManager.getDouble("raidtnt.fuse_seconds", 3.0);
        Location spawnLoc = clicked.getLocation().add(0.5, 1.0, 0.5);
        TNTPrimed tnt = clicked.getWorld().spawn(spawnLoc, TNTPrimed.class, t -> {
            t.setFuseTicks((int) (fuseSeconds * 20));
            t.setSource(player);
            // Mark the TNT entity
            t.getPersistentDataContainer().set(TNT_MARKER, PersistentDataType.BOOLEAN, true);
        });

        trackedTntUuids.add(tnt.getUniqueId());

        player.playSound(player.getLocation(), Sound.ENTITY_TNT_PRIMED, 1.5f, 1.0f);
        player.sendActionBar(TextUtil.format("<red>💣 Raid TNT placed! Fuse: " + String.format("%.1f", fuseSeconds) + "s</red>"));

        // Apply raid tag
        if (raidTagManager != null) {
            raidTagManager.applyRaidTag(player, claim.getTeamId(), chunk);
        }

        DebugManager.log(DebugFlag.RAID_ITEMS, player.getName() + " placed Raid TNT at " + spawnLoc + " in team " + claim.getTeamId() + " territory");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        Entity entity = event.getEntity();
        if (!trackedTntUuids.remove(entity.getUniqueId())) return;

        // No player damage from Raid TNT
        event.setYield(0);

        // Apply block transformation chain
        Iterator<Block> it = event.blockList().iterator();
        Location explosionCenter = event.getLocation();
        int defendingTeamId = -1;

        while (it.hasNext()) {
            Block block = it.next();
            // Check if this is a core location - protect it
            GuildCoreBlock core = guildCoreManager.getCoreAtLocation(block.getLocation());
            if (core != null) {
                defendingTeamId = core.getTeamId();
                it.remove();
                continue;
            }

            ClaimInfo claim = claimManager.getClaimAt(block.getChunk());
            if (claim != null && claim.isTeamClaim()) {
                if (defendingTeamId < 0) defendingTeamId = claim.getTeamId();
            }

            // Apply transformation chain instead of destroying
            Material blockType = block.getType();
            Material transformed = getTransformation(blockType);
            it.remove(); // Remove from vanilla explosion list

            if (transformed != null) {
                final Block targetBlock = block;
                final Material targetMat = transformed;
                scheduler.runSync(block.getLocation(), () -> targetBlock.setType(targetMat));
            }
        }

        // Apply core damage if nearby
        if (defendingTeamId > 0) {
            int coreDamage = settingsManager.getInt("core.raid_tnt_damage", 10);
            GuildCoreBlock core = guildCoreManager.getCoreForTeam(defendingTeamId);
            if (core != null) {
                Location coreLoc = new Location(explosionCenter.getWorld(), core.getX(), core.getY(), core.getZ());
                if (coreLoc.distance(explosionCenter) <= 8.0) {
                    guildCoreManager.damageCore(defendingTeamId, coreDamage, null);
                    DebugManager.log(DebugFlag.RAID_ITEMS, "Raid TNT dealt " + coreDamage + " damage to core of team " + defendingTeamId);
                }
            }
        }
    }

    private Material getTransformation(Material type) {
        return switch (type) {
            case REINFORCED_DEEPSLATE -> Material.OBSIDIAN;
            case OBSIDIAN -> Material.CRYING_OBSIDIAN;
            case CRYING_OBSIDIAN -> Material.COBBLESTONE;
            case COBBLESTONE -> Material.AIR;
            default -> Material.AIR;
        };
    }
}