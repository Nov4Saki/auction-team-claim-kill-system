package com.guildcore.economy;

import com.guildcore.config.SettingsManager;
import com.guildcore.debug.DebugFlag;
import com.guildcore.debug.DebugManager;
import com.guildcore.util.TextUtil;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class EconomyListener implements Listener {
    private final EconomyManager economyManager;
    private final SettingsManager settingsManager;

    // Track spawner mobs to prevent infinite mob-spawner coin farming
    private final Map<UUID, Boolean> spawnerMobs = new ConcurrentHashMap<>();

    public EconomyListener(EconomyManager economyManager, SettingsManager settingsManager) {
        this.economyManager = economyManager;
        this.settingsManager = settingsManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER ||
            event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER_EGG) {
            spawnerMobs.put(event.getEntity().getUniqueId(), true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();

        if (killer == null) return;

        if (spawnerMobs.remove(entity.getUniqueId()) != null) {
            DebugManager.log(DebugFlag.MOB_SPAWN_GATING, "Denied kill reward to " + killer.getName() + ": mob was from spawner");
            return;
        }

        if (entity instanceof Player victim) {
            long pvpReward = settingsManager.getLong("economy.pvp_kill_reward", 50);
            economyManager.deposit(killer.getUniqueId(), pvpReward, "pvp_kill");
            killer.sendActionBar(TextUtil.format("<green>+ $" + pvpReward + " PvP Kill Reward!</green>"));
        } else {
            String mobKey = "economy.mob." + entity.getType().name().toLowerCase();
            long mobReward = settingsManager.getLong(mobKey, 5);
            if (mobReward > 0) {
                economyManager.deposit(killer.getUniqueId(), mobReward, "mob_kill");
                killer.sendActionBar(TextUtil.format("<green>+ $" + mobReward + " (" + entity.getName() + ")</green>"));
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        String blockKey = "economy.ore." + block.getType().name().toLowerCase();

        long reward = settingsManager.getLong(blockKey, 0);
        if (reward > 0) {
            economyManager.deposit(player.getUniqueId(), reward, "mining");
            player.sendActionBar(TextUtil.format("<green>+ $" + reward + " (" + block.getType().name() + ")</green>"));
        }
    }
}
