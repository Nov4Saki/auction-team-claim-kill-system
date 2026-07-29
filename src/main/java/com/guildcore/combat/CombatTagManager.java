package com.guildcore.combat;

import com.guildcore.config.SettingsManager;
import com.guildcore.debug.DebugFlag;
import com.guildcore.debug.DebugManager;
import com.guildcore.scheduler.SchedulerWrapper;
import com.guildcore.util.TextUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CombatTagManager implements Listener {
    private final Map<UUID, Long> combatTags = new ConcurrentHashMap<>();
    private final SettingsManager settingsManager;
    private final SchedulerWrapper scheduler;

    public CombatTagManager(SettingsManager settingsManager, SchedulerWrapper scheduler) {
        this.settingsManager = settingsManager;
        this.scheduler = scheduler;
    }

    public void tagPlayer(Player player, int durationSeconds) {
        long expireTime = System.currentTimeMillis() + (durationSeconds * 1000L);
        combatTags.put(player.getUniqueId(), expireTime);
        DebugManager.log(DebugFlag.COMBAT_TAGGING, "Tagged " + player.getName() + " for " + durationSeconds + "s");

        scheduler.runSync(player, () -> {
            player.sendTitle(
                TextUtil.toLegacy(TextUtil.format("<red>⚔ COMBAT</red>")),
                TextUtil.toLegacy(TextUtil.format("<gray>Do not log out or use restricted items.</gray>")),
                10, 30, 10
            );
        });
    }

    public boolean isTagged(Player player) {
        if (player == null) return false;
        Long expireTime = combatTags.get(player.getUniqueId());
        if (expireTime == null) return false;

        if (System.currentTimeMillis() > expireTime) {
            combatTags.remove(player.getUniqueId());
            DebugManager.log(DebugFlag.COMBAT_TAGGING, "Combat tag expired for " + player.getName());
            return false;
        }
        return true;
    }

    public long getRemainingSeconds(Player player) {
        Long expireTime = combatTags.get(player.getUniqueId());
        if (expireTime == null) return 0;
        long rem = (expireTime - System.currentTimeMillis()) / 1000L;
        return Math.max(0, rem);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player victim && event.getDamager() instanceof Player attacker) {
            int duration = settingsManager.getInt("combat.tag_duration", 15);
            tagPlayer(victim, duration);
            tagPlayer(attacker, duration);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (isTagged(player)) {
            DebugManager.log(DebugFlag.ANTI_LOGOUT, "Blocked combat logout for " + player.getName());
            scheduler.runSync(player, () -> {
                player.sendActionBar(TextUtil.format("<red>🚫 Combat Log Punished!</red>"));
                player.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 200, 1));
            });
        }
    }
}
