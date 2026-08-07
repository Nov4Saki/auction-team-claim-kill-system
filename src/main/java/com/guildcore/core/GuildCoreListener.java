package com.guildcore.core;

import com.guildcore.claims.ClaimManager;
import com.guildcore.debug.DebugFlag;
import com.guildcore.debug.DebugManager;
import com.guildcore.teams.Team;
import com.guildcore.teams.TeamManager;
import com.guildcore.util.TextUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.*;

public class GuildCoreListener implements Listener {
    private final GuildCoreManager guildCoreManager;
    private final ClaimManager claimManager;
    private final TeamManager teamManager;

    // Track players who recently interacted to prevent spam
    private final Map<UUID, Long> lastCoreInteraction = new HashMap<>();
    private static final long INTERACTION_COOLDOWN_MS = 500;

    public GuildCoreListener(GuildCoreManager guildCoreManager, ClaimManager claimManager,
                             TeamManager teamManager) {
        this.guildCoreManager = guildCoreManager;
        this.claimManager = claimManager;
        this.teamManager = teamManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractAtEntity(PlayerInteractAtEntityEvent event) {
        if (!(event.getRightClicked() instanceof ArmorStand stand)) return;

        Player player = event.getPlayer();

        // Check if this armor stand belongs to a guild core
        for (GuildCoreBlock core : guildCoreManager.getAllCores()) {
            if (core.getArmorStandUuid() != null &&
                    core.getArmorStandUuid().equals(stand.getUniqueId())) {

                event.setCancelled(true);

                // Prevent spam
                long now = System.currentTimeMillis();
                Long lastInteraction = lastCoreInteraction.get(player.getUniqueId());
                if (lastInteraction != null && now - lastInteraction < INTERACTION_COOLDOWN_MS) {
                    return;
                }
                lastCoreInteraction.put(player.getUniqueId(), now);

                Team playerTeam = teamManager.getPlayerTeam(player.getUniqueId());

                if (playerTeam != null && playerTeam.getId() == core.getTeamId()) {
                    // Team member - show core management info
                    player.sendMessage(TextUtil.format(
                            "<gradient:#FFD700:#FFA500><b>⚔ " + playerTeam.getName() + " Guild Core</b></gradient>"));
                    player.sendMessage(TextUtil.format(
                            "<gray>Tier: <yellow>" + core.getTier() + "</yellow> | " +
                                    "HP: " + getHpColor(core.getCurrentHp(), core.getMaxHp()) +
                                    core.getCurrentHp() + "/" + core.getMaxHp() + "</" +
                                    getHpColorTag(core.getCurrentHp(), core.getMaxHp()) + "></gray>"));
                    player.sendMessage(TextUtil.format(
                            "<gray>Use <gold>/team</gold> to manage your Guild or interact with core GUI.</gray>"));
                } else {
                    // Outsider - show basic info
                    Team ownerTeam = teamManager.getTeam(core.getTeamId());
                    String ownerName = ownerTeam != null ? ownerTeam.getName() : "Unknown";
                    player.sendMessage(TextUtil.format(
                            "<gradient:#FF4500:#DC143C><b>⚔ " + ownerName + " Guild Core</b></gradient>"));
                    player.sendMessage(TextUtil.format(
                            "<gray>Tier: <yellow>" + core.getTier() + "</yellow> | " +
                                    "HP: " + getHpColor(core.getCurrentHp(), core.getMaxHp()) +
                                    core.getCurrentHp() + "/" + core.getMaxHp() + "</" +
                                    getHpColorTag(core.getCurrentHp(), core.getMaxHp()) + "></gray>"));
                    player.sendMessage(TextUtil.format(
                            "<gray>Use raid tools to damage this core!</gray>"));
                }

                DebugManager.log(DebugFlag.GUILD_CORE, player.getName() +
                        " interacted with core ArmorStand for team " + core.getTeamId());
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;

        Block clicked = event.getClickedBlock();
        GuildCoreBlock core = guildCoreManager.getCoreAtLocation(clicked.getLocation());

        if (core != null) {
            event.setCancelled(true);

            Player player = event.getPlayer();
            Team playerTeam = teamManager.getPlayerTeam(player.getUniqueId());

            if (playerTeam != null && playerTeam.getId() == core.getTeamId()) {
                player.sendActionBar(Component.text(
                        "🛡 This is your Guild Core! It cannot be opened directly.",
                        NamedTextColor.GOLD));
            } else {
                player.sendActionBar(Component.text(
                        "🛡 This is a Guild Core! Use raid tools to damage it.",
                        NamedTextColor.RED));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        GuildCoreBlock core = guildCoreManager.getCoreAtLocation(block.getLocation());

        if (core != null) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(Component.text(
                    "⚔ Guild Cores can only be damaged with raid tools (Sledge Hammer, Raid TNT, Charged Creeper)!",
                    NamedTextColor.RED));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block placed = event.getBlock();
        Location placedLoc = placed.getLocation();

        // Check all adjacent positions including the block itself
        int[][] offsets = {
                {0, 0, 0}, {1, 0, 0}, {-1, 0, 0},
                {0, 1, 0}, {0, -1, 0},
                {0, 0, 1}, {0, 0, -1}
        };

        for (int[] offset : offsets) {
            Location check = placedLoc.clone().add(offset[0], offset[1], offset[2]);
            GuildCoreBlock core = guildCoreManager.getCoreAtLocation(check);

            if (core != null) {
                event.setCancelled(true);
                event.getPlayer().sendActionBar(Component.text(
                        "🛡 You cannot place blocks on or adjacent to a Guild Core!",
                        NamedTextColor.RED));
                return;
            }
        }

        // Also prevent placing directly above the armor stand
        Location aboveCheck = placedLoc.clone().add(0, -1, 0);
        GuildCoreBlock coreBelow = guildCoreManager.getCoreAtLocation(aboveCheck);
        if (coreBelow != null && placed.getType().isSolid()) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(Component.text(
                    "🛡 You cannot place blocks above a Guild Core!",
                    NamedTextColor.RED));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        Iterator<Block> it = event.blockList().iterator();
        while (it.hasNext()) {
            Block block = it.next();
            if (guildCoreManager.isCoreBlock(block.getLocation())) {
                it.remove();
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        Iterator<Block> it = event.blockList().iterator();
        while (it.hasNext()) {
            Block block = it.next();
            if (guildCoreManager.isCoreBlock(block.getLocation())) {
                it.remove();
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block block : event.getBlocks()) {
            if (guildCoreManager.isCoreBlock(block.getLocation())) {
                event.setCancelled(true);
                return;
            }
            // Also check if piston would push blocks into core
            Block pushed = block.getRelative(event.getDirection());
            if (guildCoreManager.isCoreBlock(pushed.getLocation())) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block block : event.getBlocks()) {
            if (guildCoreManager.isCoreBlock(block.getLocation())) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        // Prevent accidentally damaging the armor stand with non-raid items
        if (event.getEntity() instanceof ArmorStand stand) {
            for (GuildCoreBlock core : guildCoreManager.getAllCores()) {
                if (core.getArmorStandUuid() != null &&
                        core.getArmorStandUuid().equals(stand.getUniqueId())) {

                    // Check if damager is using a sledge hammer (handled by SledgeHammerListener)
                    // If not using raid tools, cancel the damage
                    if (event.getDamager() instanceof Player player) {
                        // We'll let SledgeHammerListener handle actual damage
                        // This just provides feedback for non-raid tool hits
                        event.setCancelled(true);
                        player.sendActionBar(Component.text(
                                "⚔ Use a Sledge Hammer to damage this Guild Core!",
                                NamedTextColor.RED));
                    } else {
                        event.setCancelled(true);
                    }
                    return;
                }
            }
        }
    }

    // ═══════════════════════════════════════════════
    //  HELPER METHODS
    // ═══════════════════════════════════════════════
    private String getHpColor(int current, int max) {
        float percent = max > 0 ? (float) current / max : 0f;
        if (percent > 0.66f) return "<green>";
        if (percent > 0.33f) return "<yellow>";
        if (percent > 0f) return "<red>";
        return "<dark_red>";
    }

    private String getHpColorTag(int current, int max) {
        float percent = max > 0 ? (float) current / max : 0f;
        if (percent > 0.66f) return "green";
        if (percent > 0.33f) return "yellow";
        if (percent > 0f) return "red";
        return "dark_red";
    }
}