// FILE: src/main/java/com/guildcore/raiditems/ChargedCreeperListener.java
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
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
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

public class ChargedCreeperListener implements Listener {
    private final RaidItemManager raidItemManager;
    private final ClaimManager claimManager;
    private final OfflineShieldManager offlineShieldManager;
    private final SettingsManager settingsManager;
    private final SchedulerWrapper scheduler;
    private RaidTagManager raidTagManager;
    private GuildCoreManager guildCoreManager;
    private TeamManager teamManager;

    private final Set<UUID> trackedCreeperUuids = ConcurrentHashMap.newKeySet();
    private static final NamespacedKey CREEPER_MARKER = new NamespacedKey("guildcore", "raid_creeper");
    private static final NamespacedKey CREEPER_TEAM_KEY = new NamespacedKey("guildcore", "creeper_team_id");

    public ChargedCreeperListener(RaidItemManager raidItemManager, ClaimManager claimManager,
                                  OfflineShieldManager offlineShieldManager, SettingsManager settingsManager,
                                  SchedulerWrapper scheduler) {
        this.raidItemManager = raidItemManager;
        this.claimManager = claimManager;
        this.offlineShieldManager = offlineShieldManager;
        this.settingsManager = settingsManager;
        this.scheduler = scheduler;
    }

    public void setRaidTagManager(RaidTagManager raidTagManager) { this.raidTagManager = raidTagManager; }
    public void setGuildCoreManager(GuildCoreManager guildCoreManager) { this.guildCoreManager = guildCoreManager; }
    public void setTeamManager(TeamManager teamManager) { this.teamManager = teamManager; }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        RaidItemManager.RaidItemType type = raidItemManager.getRaidItemType(item);
        if (type != RaidItemManager.RaidItemType.CHARGED_CREEPER_EGG) return;

        // ALWAYS cancel
        event.setCancelled(true);

        Block clicked = event.getClickedBlock();
        Chunk chunk = clicked.getChunk();
        ClaimInfo claim = claimManager.getClaimAt(chunk);

        int defendingTeamId = -1;

        if (claim != null && claim.isTeamClaim()) {
            defendingTeamId = claim.getTeamId() != null ? claim.getTeamId() : -1;

            // Check not own team
            if (teamManager != null) {
                Team playerTeam = teamManager.getPlayerTeam(player.getUniqueId());
                if (playerTeam != null && playerTeam.getId() == defendingTeamId) {
                    player.sendActionBar(Component.text("⚡ You can't use Creeper Eggs on your own territory!", NamedTextColor.RED));
                    return;
                }
            }

            if (offlineShieldManager.isShieldActive(defendingTeamId)) {
                player.sendActionBar(Component.text("🛡 This territory is protected by an Offline Shield!", NamedTextColor.AQUA));
                return;
            }
        }

        // Consume 1 item
        item.setAmount(item.getAmount() - 1);

        // Spawn charged creeper entity
        double fuseSeconds = settingsManager.getDouble("creeper.fuse_seconds", 3.0);
        Location spawnLoc = clicked.getLocation().add(0.5, 1.0, 0.5);

        final int finalTeamId = defendingTeamId;
        Creeper creeper = clicked.getWorld().spawn(spawnLoc, Creeper.class, c -> {
            c.setPowered(true);
            c.setMaxFuseTicks((int) (fuseSeconds * 20));
            c.setFuseTicks((int) (fuseSeconds * 20));
            c.setAI(false);
            c.setInvulnerable(true);
            c.setSilent(true);
            c.getPersistentDataContainer().set(CREEPER_MARKER, PersistentDataType.BOOLEAN, true);
            c.getPersistentDataContainer().set(CREEPER_TEAM_KEY, PersistentDataType.INTEGER, finalTeamId);
        });

        trackedCreeperUuids.add(creeper.getUniqueId());

        // Ignite after a short delay to allow spawn
        scheduler.runLater(spawnLoc, () -> {
            if (creeper.isValid() && !creeper.isDead()) {
                creeper.ignite();
            }
        }, 5L);

        player.sendActionBar(TextUtil.format("<green>⚡ Charged Creeper deployed! Detonation in " +
                String.format("%.1f", fuseSeconds) + "s</green>"));

        // Apply raid tag only if in enemy claim
        if (raidTagManager != null && defendingTeamId > 0) {
            raidTagManager.applyRaidTag(player, defendingTeamId, chunk);
        }

        DebugManager.log(DebugFlag.RAID_ITEMS, player.getName() + " deployed Charged Creeper at " +
                spawnLoc + " (team: " + defendingTeamId + ")");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        Entity entity = event.getEntity();
        if (!trackedCreeperUuids.remove(entity.getUniqueId())) return;

        event.setYield(0);

        int defendingTeamId = entity.getPersistentDataContainer()
                .getOrDefault(CREEPER_TEAM_KEY, PersistentDataType.INTEGER, -1);

        Iterator<Block> it = event.blockList().iterator();
        while (it.hasNext()) {
            Block block = it.next();

            // Protect claim chests
            if (isClaimChest(block)) {
                it.remove();
                continue;
            }

            // Protect core blocks
            if (guildCoreManager != null) {
                GuildCoreBlock core = guildCoreManager.getCoreAtLocation(block.getLocation());
                if (core != null) {
                    defendingTeamId = core.getTeamId();
                    it.remove();
                    continue;
                }
            }

            // Hard blocks survive, soft blocks destroyed
            if (isHardBlock(block.getType())) {
                it.remove();
            }
        }

        // Apply core damage if in enemy claim
        if (defendingTeamId > 0 && guildCoreManager != null) {
            int coreDamage = settingsManager.getInt("core.creeper_damage", 0);
            if (coreDamage > 0) {
                GuildCoreBlock core = guildCoreManager.getCoreForTeam(defendingTeamId);
                if (core != null) {
                    Location coreLoc = new Location(event.getLocation().getWorld(),
                            core.getX(), core.getY(), core.getZ());
                    if (coreLoc.distance(event.getLocation()) <= 10.0) {
                        guildCoreManager.damageCore(defendingTeamId, coreDamage, null);
                        DebugManager.log(DebugFlag.RAID_ITEMS,
                                "Charged Creeper dealt " + coreDamage + " damage to core of team " + defendingTeamId);
                    }
                }
            }
        }
    }

    private boolean isClaimChest(Block block) {
        try {
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

    private boolean isHardBlock(Material type) {
        return type == Material.OBSIDIAN ||
                type == Material.CRYING_OBSIDIAN ||
                type == Material.REINFORCED_DEEPSLATE ||
                type == Material.BEDROCK ||
                type == Material.END_PORTAL_FRAME ||
                type == Material.BARRIER ||
                type == Material.NETHERITE_BLOCK;
    }
}