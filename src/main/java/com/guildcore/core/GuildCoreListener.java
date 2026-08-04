package com.guildcore.core;

import com.guildcore.claims.ClaimManager;
import com.guildcore.debug.DebugFlag;
import com.guildcore.debug.DebugManager;
import com.guildcore.teams.TeamManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.Iterator;

public class GuildCoreListener implements Listener {
    private final GuildCoreManager guildCoreManager;
    private final ClaimManager claimManager;
    private final TeamManager teamManager;

    public GuildCoreListener(GuildCoreManager guildCoreManager, ClaimManager claimManager, TeamManager teamManager) {
        this.guildCoreManager = guildCoreManager;
        this.claimManager = claimManager;
        this.teamManager = teamManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractAtEntity(PlayerInteractAtEntityEvent event) {
        if (!(event.getRightClicked() instanceof ArmorStand stand)) return;

        for (GuildCoreBlock core : getAllCores()) {
            if (core.getArmorStandUuid() != null && core.getArmorStandUuid().equals(stand.getUniqueId())) {
                event.setCancelled(true);
                Player player = event.getPlayer();
                com.guildcore.teams.Team playerTeam = teamManager.getPlayerTeam(player.getUniqueId());

                if (playerTeam != null && playerTeam.getId() == core.getTeamId()) {
                    player.sendMessage(Component.text("⚔ Use /team to manage your Guild Core or right-click the chest below!", NamedTextColor.YELLOW));
                } else {
                    player.sendMessage(Component.text("⚔ Guild Core | Tier " + core.getTier() + " | HP: " + core.getCurrentHp() + "/" + core.getMaxHp(), NamedTextColor.GOLD));
                }
                DebugManager.log(DebugFlag.GUILD_CORE, player.getName() + " interacted with core ArmorStand for team " + core.getTeamId());
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
            event.getPlayer().sendActionBar(Component.text("🛡 This is a Guild Core! It cannot be opened.", NamedTextColor.GOLD));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        GuildCoreBlock core = guildCoreManager.getCoreAtLocation(event.getBlock().getLocation());
        if (core != null) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(Component.text("⚔ Guild Cores can only be damaged with raid tools!", NamedTextColor.RED));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block placed = event.getBlock();
        Location placedLoc = placed.getLocation();

        // Check 6 cardinal directions + self
        int[][] offsets = {{0,0,0},{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
        for (int[] offset : offsets) {
            Location check = placedLoc.clone().add(offset[0], offset[1], offset[2]);
            GuildCoreBlock core = guildCoreManager.getCoreAtLocation(check);
            if (core != null) {
                event.setCancelled(true);
                event.getPlayer().sendActionBar(Component.text("🛡 You cannot place blocks adjacent to a Guild Core!", NamedTextColor.RED));
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        Iterator<Block> it = event.blockList().iterator();
        while (it.hasNext()) {
            Block block = it.next();
            if (guildCoreManager.getCoreAtLocation(block.getLocation()) != null) {
                it.remove();
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        Iterator<Block> it = event.blockList().iterator();
        while (it.hasNext()) {
            Block block = it.next();
            if (guildCoreManager.getCoreAtLocation(block.getLocation()) != null) {
                it.remove();
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block block : event.getBlocks()) {
            if (guildCoreManager.getCoreAtLocation(block.getLocation()) != null) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block block : event.getBlocks()) {
            if (guildCoreManager.getCoreAtLocation(block.getLocation()) != null) {
                event.setCancelled(true);
                return;
            }
        }
    }

    private java.util.Collection<GuildCoreBlock> getAllCores() {
        return guildCoreManager.getAllCores();
    }
}
