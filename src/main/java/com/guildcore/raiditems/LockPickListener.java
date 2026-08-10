package com.guildcore.raiditems;

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
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class LockPickListener implements Listener {
    private final RaidItemManager raidItemManager;
    private final ClaimManager claimManager;
    private final OfflineShieldManager offlineShieldManager;
    private final SettingsManager settingsManager;
    private final SchedulerWrapper scheduler;

    private TeamManager teamManager;

    public LockPickListener(RaidItemManager raidItemManager, ClaimManager claimManager, OfflineShieldManager offlineShieldManager, SettingsManager settingsManager, SchedulerWrapper scheduler) {
        this.raidItemManager = raidItemManager;
        this.claimManager = claimManager;
        this.offlineShieldManager = offlineShieldManager;
        this.settingsManager = settingsManager;
        this.scheduler = scheduler;
    }

    public void setTeamManager(TeamManager teamManager) {
        this.teamManager = teamManager;
    }

    private com.guildcore.claims.ClaimChestManager claimChestManager;

    public void setClaimChestManager(com.guildcore.claims.ClaimChestManager claimChestManager) {
        this.claimChestManager = claimChestManager;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = false)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;

        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null || !raidItemManager.isRaidItem(item)) {
            item = player.getInventory().getItemInMainHand();
            if (!raidItemManager.isRaidItem(item)) {
                item = player.getInventory().getItemInOffHand();
            }
        }
        RaidItemManager.RaidItemType type = raidItemManager.getRaidItemType(item);
        if (type == null) return;

        // Only lock picks
        if (type != RaidItemManager.RaidItemType.LOCK_PICK_WEAK &&
                type != RaidItemManager.RaidItemType.LOCK_PICK_NORMAL &&
                type != RaidItemManager.RaidItemType.LOCK_PICK_FAST &&
                type != RaidItemManager.RaidItemType.LOCK_PICK_REINFORCED) return;

        Block clicked = event.getClickedBlock();

        // Protect Guild Claim Chest from lock picking
        if (claimChestManager != null && claimChestManager.isClaimChest(clicked.getLocation())) {
            event.setCancelled(true);
            player.sendActionBar(TextUtil.format("<red>🚫 You cannot lockpick a Guild Claim Chest!</red>"));
            return;
        }

        Chunk chunk = clicked.getChunk();
        ClaimInfo claim = claimManager.getClaimAt(chunk);

        if (claim == null || !claim.isTeamClaim()) {
            player.sendActionBar(Component.text("🔑 Lock picks only work on claimed territory!", NamedTextColor.RED));
            return;
        }

        // Check not own team
        if (teamManager != null) {
            Team playerTeam = teamManager.getPlayerTeam(player.getUniqueId());
            if (playerTeam != null && playerTeam.getId() == claim.getTeamId()) {
                player.sendActionBar(Component.text("🔑 You don't need lock picks on your own territory!", NamedTextColor.RED));
                return;
            }
        }
        // Check shield
        if (offlineShieldManager.isShieldActive(claim.getTeamId())) {
            event.setCancelled(true);
            player.sendActionBar(Component.text("🛡 This territory is protected by an Offline Shield!", NamedTextColor.AQUA));
            return;
        }

        event.setCancelled(true);

        // Get success chance
        int successChance = getSuccessChance(type);
        boolean success = Math.random() * 100 < successChance;

        Material blockType = clicked.getType();

        // Check if container type is prohibited by admin configuration
        if (settingsManager.isContainerLockpickProhibited(blockType)) {
            player.sendActionBar(TextUtil.format("<red>🚫 Lock picks are prohibited on " + blockType.name() + "!</red>"));
            return;
        }

        if (isContainer(clicked)) {
            if (success) {
                player.playSound(player.getLocation(), Sound.BLOCK_IRON_DOOR_OPEN, 1.0f, 1.5f);
                player.sendActionBar(TextUtil.format("<green>🔓 Lock pick SUCCESS! Container opened.</green>"));

                // Open the container for the player
                if (clicked.getState() instanceof Container container) {
                    scheduler.runSync(player, () -> player.openInventory(container.getInventory()));
                }

                DebugManager.log(DebugFlag.LOCK_PICK, player.getName() + " picked " + blockType + " at " + clicked.getLocation() + " (SUCCESS, type=" + type + ")");
            } else {
                player.playSound(player.getLocation(), Sound.BLOCK_IRON_DOOR_CLOSE, 1.0f, 0.5f);
                player.sendActionBar(TextUtil.format("<red>🔒 Lock pick FAILED!</red>"));
                DebugManager.log(DebugFlag.LOCK_PICK, player.getName() + " picked " + blockType + " at " + clicked.getLocation() + " (FAILED, type=" + type + ")");
            }
            raidItemManager.consumeDurability(item, player);

        } else if (isOpenable(blockType)) {
            if (success) {
                player.playSound(player.getLocation(), Sound.BLOCK_IRON_DOOR_OPEN, 1.0f, 1.2f);

                if (clicked.getBlockData() instanceof org.bukkit.block.data.Openable openable) {
                    openable.setOpen(!openable.isOpen());
                    clicked.setBlockData(openable);
                    String state = openable.isOpen() ? "opened" : "closed";
                    player.sendActionBar(TextUtil.format("<green>🔓 Lock pick SUCCESS! Door " + state + ".</green>"));
                }

                DebugManager.log(DebugFlag.LOCK_PICK, player.getName() + " toggled " + blockType + " at " + clicked.getLocation() + " (SUCCESS, type=" + type + ")");
            } else {
                player.playSound(player.getLocation(), Sound.BLOCK_IRON_DOOR_CLOSE, 1.0f, 0.5f);
                player.sendActionBar(TextUtil.format("<red>🔒 Lock pick FAILED!</red>"));
                DebugManager.log(DebugFlag.LOCK_PICK, player.getName() + " toggled " + blockType + " at " + clicked.getLocation() + " (FAILED, type=" + type + ")");
            }
            raidItemManager.consumeDurability(item, player);
        }
        // No raid tag applied — stealth mechanic
    }

    private int getSuccessChance(RaidItemManager.RaidItemType type) {
        return switch (type) {
            case LOCK_PICK_WEAK -> settingsManager.getInt("lockpick.weak.chance", 10);
            case LOCK_PICK_NORMAL -> settingsManager.getInt("lockpick.normal.chance", 20);
            case LOCK_PICK_FAST -> settingsManager.getInt("lockpick.fast.chance", 75);
            case LOCK_PICK_REINFORCED -> settingsManager.getInt("lockpick.reinforced.chance", 20);
            default -> 0;
        };
    }

    private boolean isContainer(Block block) {
        if (block == null) return false;
        if (block.getState() instanceof Container) return true;
        Material type = block.getType();
        return type == Material.CHEST || type == Material.TRAPPED_CHEST ||
                type == Material.BARREL || type == Material.HOPPER ||
                type == Material.DROPPER || type == Material.DISPENSER ||
                type == Material.FURNACE || type == Material.BLAST_FURNACE ||
                type == Material.SMOKER || type.name().contains("SHULKER_BOX") ||
                type == Material.BREWING_STAND || type == Material.JUKEBOX ||
                type == Material.CHISELED_BOOKSHELF || type == Material.DECORATED_POT;
    }

    private boolean isOpenable(Material type) {
        String name = type.name();
        return name.contains("DOOR") || name.contains("TRAPDOOR") || name.contains("FENCE_GATE");
    }
}