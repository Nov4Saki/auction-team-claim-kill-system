package com.guildcore.combat;

import com.guildcore.config.SettingsManager;
import com.guildcore.debug.DebugFlag;
import com.guildcore.debug.DebugManager;
import com.guildcore.scheduler.SchedulerWrapper;
import com.guildcore.util.TextUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ItemControlManager implements Listener {
    private final CombatTagManager combatTagManager;
    private final SettingsManager settingsManager;
    private final SchedulerWrapper scheduler;

    // UUID -> (Material -> ExpiryTime ms)
    private final Map<UUID, Map<Material, Long>> itemCooldowns = new ConcurrentHashMap<>();

    public ItemControlManager(CombatTagManager combatTagManager, SettingsManager settingsManager, SchedulerWrapper scheduler) {
        this.combatTagManager = combatTagManager;
        this.settingsManager = settingsManager;
        this.scheduler = scheduler;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null || item.getType().isAir()) return;

        Material type = item.getType();

        // 1. Combat Item Disable (e.g. ENDER_PEARL)
        if (combatTagManager.isTagged(player) && isCombatDisabled(type)) {
            event.setCancelled(true);
            DebugManager.log(DebugFlag.ITEM_DISABLE, "Blocked combat-disabled item: " + type + " for " + player.getName());
            scheduler.runSync(player, () -> player.sendActionBar(TextUtil.format("<red>🚫 Cannot use " + type + " in combat!</red>")));
            return;
        }

        // 2. Custom Combat Cooldowns (e.g. CHORUS_FRUIT)
        long cooldownMs = getCombatCooldownMs(type);
        if (cooldownMs > 0 && combatTagManager.isTagged(player)) {
            Map<Material, Long> playerCooldowns = itemCooldowns.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>());
            long now = System.currentTimeMillis();
            Long expiry = playerCooldowns.get(type);

            if (expiry != null && expiry > now) {
                event.setCancelled(true);
                long remaining = (expiry - now) / 1000L;
                DebugManager.log(DebugFlag.ITEM_COOLDOWN, "Blocked " + type + " due to cooldown (" + remaining + "s) for " + player.getName());
                scheduler.runSync(player, () -> player.sendActionBar(TextUtil.format("<red>⏳ " + type + " cooldown: " + remaining + "s</red>")));
                return;
            }

            playerCooldowns.put(type, now + cooldownMs);
            DebugManager.log(DebugFlag.ITEM_COOLDOWN, "Applied " + cooldownMs + "ms cooldown to " + type + " for " + player.getName());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        Material type = event.getItem().getType();

        if (combatTagManager.isTagged(player) && isCombatDisabled(type)) {
            event.setCancelled(true);
            DebugManager.log(DebugFlag.ITEM_DISABLE, "Blocked combat-disabled consumable: " + type + " for " + player.getName());
            scheduler.runSync(player, () -> player.sendActionBar(TextUtil.format("<red>🚫 Cannot consume " + type + " in combat!</red>")));
        }
    }

    private boolean isCombatDisabled(Material material) {
        String key = "combat.disabled_item." + material.name().toLowerCase();
        return settingsManager.getBoolean(key, false);
    }

    private long getCombatCooldownMs(Material material) {
        String key = "combat.cooldown_sec." + material.name().toLowerCase();
        int seconds = settingsManager.getInt(key, 0);
        return seconds * 1000L;
    }
}
