package com.guildcore.combat;

import com.guildcore.config.SettingsManager;
import com.guildcore.debug.DebugFlag;
import com.guildcore.debug.DebugManager;
import com.guildcore.raidtag.RaidTagManager;
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

    // Raid tag integration
    private RaidTagManager raidTagManager;

    public CombatTagManager(SettingsManager settingsManager, SchedulerWrapper scheduler) {
        this.settingsManager = settingsManager;
        this.scheduler = scheduler;
        startContinuousActionBarTask();
    }

    public void setRaidTagManager(RaidTagManager raidTagManager) {
        this.raidTagManager = raidTagManager;
    }

    private void startContinuousActionBarTask() {
        scheduler.runTaskTimer(() -> {
            long now = System.currentTimeMillis();
            for (Map.Entry<UUID, Long> entry : taggedPlayers.entrySet()) {
                UUID uuid = entry.getKey();
                long expiry = entry.getValue();
                Player player = Bukkit.getPlayer(uuid);
                if (player == null || !player.isOnline()) {
                    taggedPlayers.remove(uuid);
                    continue;
                }
                if (now > expiry) {
                    taggedPlayers.remove(uuid);
                    scheduler.runSync(player, () -> player.sendActionBar(Component.text("✔ Combat Tag Expired!", NamedTextColor.GREEN)));
                    DebugManager.log(DebugFlag.COMBAT_TAGGING, "Combat tag expired for " + player.getName());
                } else {
                    // Only show PvP combat tag bar if NOT raid tagged (raid tag has its own bar)
                    if (raidTagManager == null || !raidTagManager.isRaidTagged(uuid)) {
                        int remaining = (int) Math.max(0, (expiry - now) / 1000L);
                        scheduler.runSync(player, () -> player.sendActionBar(Component.text("⚔ COMBAT TAGGED: " + remaining + "s", NamedTextColor.RED)));
                    }
                }
            }
            return true;
        }, 0L, 20L);
    }

    public void tag(Player player) {
        if (player == null || !player.isOnline()) return;

        int duration = settingsManager.getInt("combat.tag_duration", 15);
        long expiry = System.currentTimeMillis() + (duration * 1000L);

        boolean newlyTagged = !isPvpTagged(player);
        taggedPlayers.put(player.getUniqueId(), expiry);

        if (newlyTagged) {
            scheduler.runSync(player, () -> player.sendActionBar(Component.text("⚔ COMBAT TAGGED: " + duration + "s", NamedTextColor.RED)));
            DebugManager.log(DebugFlag.COMBAT_TAGGING, "Tagged player " + player.getName() + " for " + duration + "s");
        }
    }

    /**
     * Returns true if player is tagged by EITHER basic PvP combat tag OR raid tag.
     * Used by ItemControlManager and ScoreboardManager.
     */
    public boolean isTagged(Player player) {
        if (player == null) return false;
        // Check raid tag first
        if (raidTagManager != null && raidTagManager.isRaidTagged(player.getUniqueId())) {
            return true;
        }
        // Then check basic PvP tag
        return isPvpTagged(player);
    }

    /**
     * Returns true if player has a basic PvP combat tag (not raid tag).
     */
    private boolean isPvpTagged(Player player) {
        if (player == null) return false;
        Long expiry = taggedPlayers.get(player.getUniqueId());
        if (expiry == null) return false;
        if (System.currentTimeMillis() > expiry) {
            taggedPlayers.remove(player.getUniqueId());
            return false;
        }
        return true;
    }

    public int getRemainingSeconds(Player player) {
        // If raid tagged, delegate to raid tag manager
        if (raidTagManager != null && raidTagManager.isRaidTagged(player.getUniqueId())) {
            int raidSec = raidTagManager.getRemainingSeconds(player.getUniqueId());
            return raidSec >= 0 ? raidSec : 99; // -1 means "inside territory" -> show high number
        }
        Long expiry = taggedPlayers.get(player.getUniqueId());
        if (expiry == null) return 0;
        long rem = (expiry - System.currentTimeMillis()) / 1000L;
        return (int) Math.max(0, rem);
    }

    private com.guildcore.teams.TeamManager teamManager;

    public void setTeamManager(com.guildcore.teams.TeamManager teamManager) {
        this.teamManager = teamManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player victim && event.getDamager() instanceof Player attacker) {
            if (teamManager != null) {
                com.guildcore.teams.Team vTeam = teamManager.getPlayerTeam(victim.getUniqueId());
                com.guildcore.teams.Team aTeam = teamManager.getPlayerTeam(attacker.getUniqueId());
                if (vTeam != null && aTeam != null && vTeam.getId() == aTeam.getId()) {
                    return;
                }
            }
            tag(victim);
            tag(attacker);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        // Only block for PvP combat tag — raid tag has its own command blocker in RaidTagManager
        if (isPvpTagged(player) && settingsManager.getBoolean("combat.disable_commands", true)) {
            String msg = event.getMessage().toLowerCase();
            String mainCmd = msg.split(" ")[0];

            if (!ALLOWED_COMBAT_COMMANDS.contains(mainCmd)) {
                event.setCancelled(true);
                scheduler.runSync(player, () -> player.sendActionBar(Component.text("🚫 Commands are disabled while in combat!", NamedTextColor.RED)));
            }
        }
    }
}
