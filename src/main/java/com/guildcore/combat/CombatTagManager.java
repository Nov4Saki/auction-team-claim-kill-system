package com.guildcore.combat;

import com.guildcore.config.SettingsManager;
import com.guildcore.debug.DebugFlag;
import com.guildcore.debug.DebugManager;
import com.guildcore.scheduler.SchedulerWrapper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CombatTagManager implements Listener {
    private final SettingsManager settingsManager;
    private final SchedulerWrapper scheduler;
    private final Map<UUID, Long> taggedPlayers = new ConcurrentHashMap<>();

    private static final List<String> ALLOWED_COMBAT_COMMANDS = Arrays.asList("/tc", "/teamchat", "/gc", "/guildchat", "/msg", "/tell", "/r");

    public CombatTagManager(SettingsManager settingsManager, SchedulerWrapper scheduler) {
        this.settingsManager = settingsManager;
        this.scheduler = scheduler;
        startContinuousActionBarTask();
    }

    private void startContinuousActionBarTask() {
        scheduler.runTaskTimer(() -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (isTagged(player)) {
                    int remaining = getRemainingSeconds(player);
                    scheduler.runSync(player, () -> player.sendActionBar(Component.text("⚔ COMBAT TAGGED: " + remaining + "s", NamedTextColor.RED)));
                }
            }
            return true;
        }, 0L, 20L); // Every 1 second (20 ticks)
    }

    public void tag(Player player) {
        if (player == null || !player.isOnline()) return;

        int duration = settingsManager.getInt("combat.tag_duration", 15);
        long expiry = System.currentTimeMillis() + (duration * 1000L);

        boolean newlyTagged = !isTagged(player);
        taggedPlayers.put(player.getUniqueId(), expiry);

        if (newlyTagged) {
            scheduler.runSync(player, () -> player.sendActionBar(Component.text("⚔ COMBAT TAGGED: " + duration + "s", NamedTextColor.RED)));
            DebugManager.log(DebugFlag.COMBAT_TAGGING, "Tagged player " + player.getName() + " for " + duration + "s");
        }
    }

    public boolean isTagged(Player player) {
        if (player == null) return false;
        Long expiry = taggedPlayers.get(player.getUniqueId());
        if (expiry == null) return false;
        if (System.currentTimeMillis() > expiry) {
            taggedPlayers.remove(player.getUniqueId());
            scheduler.runSync(player, () -> player.sendActionBar(Component.text("✔ Combat Tag Expired!", NamedTextColor.GREEN)));
            DebugManager.log(DebugFlag.COMBAT_TAGGING, "Combat tag expired for " + player.getName());
            return false;
        }
        return true;
    }

    public int getRemainingSeconds(Player player) {
        Long expiry = taggedPlayers.get(player.getUniqueId());
        if (expiry == null) return 0;
        long rem = (expiry - System.currentTimeMillis()) / 1000L;
        return (int) Math.max(0, rem);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player victim && event.getDamager() instanceof Player attacker) {
            tag(victim);
            tag(attacker);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (isTagged(player) && settingsManager.getBoolean("combat.disable_commands", true)) {
            String msg = event.getMessage().toLowerCase();
            String mainCmd = msg.split(" ")[0];

            if (!ALLOWED_COMBAT_COMMANDS.contains(mainCmd)) {
                event.setCancelled(true);
                scheduler.runSync(player, () -> player.sendActionBar(Component.text("🚫 Commands are disabled while in combat!", NamedTextColor.RED)));
            }
        }
    }
}
