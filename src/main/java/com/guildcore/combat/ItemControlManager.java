package com.guildcore.combat;

import com.guildcore.config.SettingsManager;
import com.guildcore.debug.DebugFlag;
import com.guildcore.debug.DebugManager;
import com.guildcore.raidtag.RaidTagManager;
import com.guildcore.scheduler.SchedulerWrapper;
import com.guildcore.util.TextUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ItemControlManager implements Listener {
    private final RaidTagManager raidTagManager;
    private final SettingsManager settingsManager;
    private final SchedulerWrapper scheduler;

    // UUID -> (Material -> ExpiryTime ms)
    private final Map<UUID, Map<Material, Long>> itemCooldowns = new ConcurrentHashMap<>();

    // Basic PvP combat tag tracking
    private final Map<UUID, Long> pvpTaggedPlayers = new ConcurrentHashMap<>();

    // Combat log tracking (for PvP combat tag disconnects)
    private final Map<UUID, PvPCombatLogEntry> pvpCombatLogEntries = new ConcurrentHashMap<>();

    private static final List<String> ALLOWED_COMBAT_COMMANDS = Arrays.asList(
            "/tc", "/teamchat", "/gc", "/guildchat", "/msg", "/tell", "/w", "/whisper", "/r", "/reply"
    );

    private com.guildcore.teams.TeamManager teamManager;

    public ItemControlManager(RaidTagManager raidTagManager, SettingsManager settingsManager, SchedulerWrapper scheduler) {
        this.raidTagManager = raidTagManager;
        this.settingsManager = settingsManager;
        this.scheduler = scheduler;
        startCombatTagActionBarTask();
    }

    public void setTeamManager(com.guildcore.teams.TeamManager teamManager) {
        this.teamManager = teamManager;
    }

    // ═══════════════════════════════════════════════
    //  PVP COMBAT TAG DATA CLASS
    // ═══════════════════════════════════════════════

    public static class PvPCombatLogEntry {
        public UUID playerUuid;
        public String playerName;
        public Location disconnectLocation;
        public long disconnectTime;
        public UUID armorStandUuid;
        public ItemStack[] inventorySnapshot;
        public boolean resolved;
        public UUID lastDamagerUuid;

        public PvPCombatLogEntry(UUID playerUuid, String playerName, Location disconnectLocation,
                                 ItemStack[] inventorySnapshot, UUID lastDamagerUuid) {
            this.playerUuid = playerUuid;
            this.playerName = playerName;
            this.disconnectLocation = disconnectLocation;
            this.disconnectTime = System.currentTimeMillis();
            this.inventorySnapshot = inventorySnapshot;
            this.resolved = false;
            this.lastDamagerUuid = lastDamagerUuid;
        }
    }

    // ═══════════════════════════════════════════════
    //  PVP COMBAT TAG METHODS
    // ═══════════════════════════════════════════════

    public void tagPlayer(Player player) {
        if (player == null || !player.isOnline()) return;

        int duration = settingsManager.getInt("combat.tag_duration", 15);
        long expiry = System.currentTimeMillis() + (duration * 1000L);

        boolean newlyTagged = !isPvpTagged(player);
        pvpTaggedPlayers.put(player.getUniqueId(), expiry);

        if (newlyTagged) {
            scheduler.runSync(player, () ->
                    player.sendActionBar(Component.text("⚔ COMBAT TAGGED: " + duration + "s", NamedTextColor.RED)));
            DebugManager.log(DebugFlag.COMBAT_TAGGING, "PvP tagged player " + player.getName() + " for " + duration + "s");
        }
    }

    public boolean isPvpTagged(Player player) {
        if (player == null) return false;
        Long expiry = pvpTaggedPlayers.get(player.getUniqueId());
        if (expiry == null) return false;
        if (System.currentTimeMillis() > expiry) {
            pvpTaggedPlayers.remove(player.getUniqueId());
            return false;
        }
        return true;
    }

    public boolean isTagged(Player player) {
        if (player == null) return false;
        if (raidTagManager != null && raidTagManager.isRaidTagged(player.getUniqueId())) {
            return true;
        }
        return isPvpTagged(player);
    }

    public int getPvpTagRemaining(Player player) {
        if (player == null) return 0;
        Long expiry = pvpTaggedPlayers.get(player.getUniqueId());
        if (expiry == null) return 0;
        return (int) Math.max(0, (expiry - System.currentTimeMillis()) / 1000L);
    }

    public int getRemainingSeconds(Player player) {
        if (player == null) return 0;
        if (raidTagManager != null && raidTagManager.isRaidTagged(player.getUniqueId())) {
            int raidSec = raidTagManager.getRemainingSeconds(player.getUniqueId());
            return raidSec >= 0 ? raidSec : 99;
        }
        return getPvpTagRemaining(player);
    }

    // ═══════════════════════════════════════════════
    //  COMBAT TAG ACTION BAR TASK
    // ═══════════════════════════════════════════════

    private void startCombatTagActionBarTask() {
        scheduler.runTaskTimer(() -> {
            long now = System.currentTimeMillis();
            for (Map.Entry<UUID, Long> entry : new HashMap<>(pvpTaggedPlayers).entrySet()) {
                UUID uuid = entry.getKey();
                long expiry = entry.getValue();
                Player player = Bukkit.getPlayer(uuid);

                if (player == null || !player.isOnline()) {
                    pvpTaggedPlayers.remove(uuid);
                    continue;
                }

                if (now > expiry) {
                    pvpTaggedPlayers.remove(uuid);
                    scheduler.runSync(player, () ->
                            player.sendActionBar(Component.text("✔ Combat Tag Expired!", NamedTextColor.GREEN)));
                    DebugManager.log(DebugFlag.COMBAT_TAGGING, "Combat tag expired for " + player.getName());
                } else {
                    if (raidTagManager == null || !raidTagManager.isRaidTagged(uuid)) {
                        int remaining = (int) Math.max(0, (expiry - now) / 1000L);
                        scheduler.runSync(player, () ->
                                player.sendActionBar(Component.text("⚔ COMBAT TAGGED: " + remaining + "s", NamedTextColor.RED)));
                    }
                }
            }
            return true;
        }, 0L, 20L);
    }

    // ═══════════════════════════════════════════════
    //  PVP DAMAGE HANDLER (Tag application)
    // ═══════════════════════════════════════════════

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player victim && event.getDamager() instanceof Player attacker) {
            if (teamManager != null && teamManager.areSameTeam(victim.getUniqueId(), attacker.getUniqueId())) {
                return;
            }

            if (raidTagManager != null && raidTagManager.isRaidTagged(victim.getUniqueId())) {
                return;
            }
            if (raidTagManager != null && raidTagManager.isRaidTagged(attacker.getUniqueId())) {
                return;
            }

            tagPlayer(victim);
            tagPlayer(attacker);

            PvPCombatLogEntry victimEntry = pvpCombatLogEntries.get(victim.getUniqueId());
            if (victimEntry != null) {
                victimEntry.lastDamagerUuid = attacker.getUniqueId();
            }

            DebugManager.log(DebugFlag.COMBAT_TAGGING,
                    attacker.getName() + " tagged " + victim.getName() + " in PvP");
        }
    }

    // ═══════════════════════════════════════════════
    //  COMMAND BLOCKING (PvP tag only)
    // ═══════════════════════════════════════════════

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();

        if (isPvpTagged(player) && settingsManager.getBoolean("combat.disable_commands", true)) {
            String msg = event.getMessage().toLowerCase();
            String mainCmd = msg.split(" ")[0];

            if (!ALLOWED_COMBAT_COMMANDS.contains(mainCmd)) {
                event.setCancelled(true);
                scheduler.runSync(player, () ->
                        player.sendActionBar(Component.text("🚫 Commands are disabled while in combat!", NamedTextColor.RED)));
            }
        }
    }

    // ═══════════════════════════════════════════════
    //  COMBAT LOG (PvP tag disconnect)
    // ═══════════════════════════════════════════════

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (!isPvpTagged(player)) return;
        if (raidTagManager != null && raidTagManager.isRaidTagged(uuid)) return;

        DebugManager.log(DebugFlag.COMBAT_TAGGING, player.getName() + " disconnected while PvP combat tagged!");

        ItemStack[] invSnapshot = player.getInventory().getContents().clone();

        UUID lastDamagerUuid = null;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online != player && isPvpTagged(online)) {
                lastDamagerUuid = online.getUniqueId();
                break;
            }
        }

        PvPCombatLogEntry entry = new PvPCombatLogEntry(
                uuid, player.getName(), player.getLocation().clone(),
                invSnapshot, lastDamagerUuid
        );

        Location loc = player.getLocation();
        World world = loc.getWorld();
        if (world != null) {
            ArmorStand stand = world.spawn(loc, ArmorStand.class, as -> {
                as.setVisible(true);
                as.setGravity(false);
                as.setInvulnerable(false);
                as.setCustomNameVisible(true);
                as.customName(TextUtil.format("<red><b>⚔ COMBAT LOG: " + player.getName() + "</b></red>"));
                as.setBasePlate(true);
                ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                if (head.getItemMeta() instanceof SkullMeta skullMeta) {
                    skullMeta.setOwningPlayer(player);
                    head.setItemMeta(skullMeta);
                }
                as.getEquipment().setHelmet(head);
            });

            entry.armorStandUuid = stand.getUniqueId();
        }

        pvpCombatLogEntries.put(uuid, entry);

        int disconnectTimer = settingsManager.getInt("raidtag.disconnect_timer_sec", 60);
        final String playerName = player.getName();

        scheduler.runTaskTimer(() -> {
            PvPCombatLogEntry logEntry = pvpCombatLogEntries.get(uuid);
            if (logEntry == null || logEntry.resolved) return false;

            long elapsed = (System.currentTimeMillis() - logEntry.disconnectTime) / 1000L;
            int remaining = disconnectTimer - (int) elapsed;

            if (remaining <= 0) {
                logEntry.resolved = true;
                resolvePvPCombatLog(logEntry);
                pvpCombatLogEntries.remove(uuid);
                pvpTaggedPlayers.remove(uuid);
                return false;
            }

            if (logEntry.armorStandUuid != null && logEntry.disconnectLocation != null && logEntry.disconnectLocation.getWorld() != null) {
                Location locUpdate = logEntry.disconnectLocation;
                scheduler.runSync(locUpdate, () -> {
                    if (locUpdate.getWorld() == null) return;
                    for (Entity entity : locUpdate.getWorld().getNearbyEntities(locUpdate, 5, 5, 5)) {
                        if (entity instanceof ArmorStand as && entity.getUniqueId().equals(logEntry.armorStandUuid)) {
                            as.customName(TextUtil.format("<red><b>⚔ COMBAT LOG: " + playerName + " (" + remaining + "s)</b></red>"));
                            break;
                        }
                    }
                });
            }
            return true;
        }, 20L, 20L);

        for (Player nearby : Bukkit.getOnlinePlayers()) {
            if (nearby.getLocation().distanceSquared(loc) < 2500) {
                nearby.sendMessage(TextUtil.format(
                        "<red>⚔ " + player.getName() + " COMBAT LOGGED! Their stand will drop loot in " + disconnectTimer + "s.</red>"));
            }
        }

        DebugManager.log(DebugFlag.ANTI_LOGOUT, "PvP combat log entry created for " + player.getName());
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        PvPCombatLogEntry entry = pvpCombatLogEntries.remove(uuid);
        if (entry == null || entry.resolved) return;

        entry.resolved = true;
        killPvPCombatLogStand(entry);

        player.sendMessage(TextUtil.format("<yellow>⚔ You reconnected during a PvP combat tag. Your tag has been restored.</yellow>"));
        tagPlayer(player);
        DebugManager.log(DebugFlag.ANTI_LOGOUT, player.getName() + " reconnected during PvP combat log.");
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onEntityDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof ArmorStand stand)) return;

        for (Map.Entry<UUID, PvPCombatLogEntry> entry : pvpCombatLogEntries.entrySet()) {
            PvPCombatLogEntry logEntry = entry.getValue();
            if (logEntry.armorStandUuid != null && logEntry.armorStandUuid.equals(stand.getUniqueId())) {
                if (!logEntry.resolved) {
                    logEntry.resolved = true;

                    if (logEntry.inventorySnapshot != null) {
                        World world = stand.getLocation().getWorld();
                        if (world != null) {
                            for (ItemStack item : logEntry.inventorySnapshot) {
                                if (item != null && item.getType() != Material.AIR) {
                                    world.dropItemNaturally(stand.getLocation(), item);
                                }
                            }
                        }
                    }

                    pvpCombatLogEntries.remove(entry.getKey());
                    pvpTaggedPlayers.remove(entry.getKey());
                    DebugManager.log(DebugFlag.ANTI_LOGOUT, "PvP combat log stand killed for player " + entry.getKey() + ". Items dropped.");
                }
                break;
            }
        }
    }

    private void resolvePvPCombatLog(PvPCombatLogEntry entry) {
        if (settingsManager.getBoolean("raidtag.drop_inv_on_expire", true)) {
            if (entry.inventorySnapshot != null && entry.disconnectLocation != null && entry.disconnectLocation.getWorld() != null) {
                Location loc = entry.disconnectLocation;
                scheduler.runSync(loc, () -> {
                    World world = loc.getWorld();
                    if (world == null) return;
                    for (ItemStack item : entry.inventorySnapshot) {
                        if (item != null && item.getType() != Material.AIR) {
                            world.dropItemNaturally(loc, item);
                        }
                    }
                });

                Player offlinePlayer = Bukkit.getPlayer(entry.playerUuid);
                if (offlinePlayer != null && offlinePlayer.isOnline()) {
                    offlinePlayer.getInventory().clear();
                }
            }
        }

        killPvPCombatLogStand(entry);

        if (settingsManager.getBoolean("raidtag.award_kill_credit", true) && entry.lastDamagerUuid != null) {
            Player killer = Bukkit.getPlayer(entry.lastDamagerUuid);
            if (killer != null && killer.isOnline()) {
                killer.sendMessage(TextUtil.format("<green>⚔ " + entry.playerName + " combat logged and their items were dropped!</green>"));
            }
            DebugManager.log(DebugFlag.ANTI_LOGOUT, "PvP combat log penalty applied to " + entry.playerName +
                    " (kill credit to " + entry.lastDamagerUuid + ")");
        }
    }

    private void killPvPCombatLogStand(PvPCombatLogEntry entry) {
        if (entry.armorStandUuid == null || entry.disconnectLocation == null) return;
        Location loc = entry.disconnectLocation;
        World world = loc.getWorld();
        if (world == null) return;

        scheduler.runSync(loc, () -> {
            if (loc.getWorld() == null) return;
            for (Entity entity : loc.getWorld().getNearbyEntities(loc, 5, 5, 5)) {
                if (entity instanceof ArmorStand && entity.getUniqueId().equals(entry.armorStandUuid)) {
                    entity.remove();
                    break;
                }
            }
        });
    }

    // ═══════════════════════════════════════════════
    //  ITEM CONTROL (Ender Pearls, Shields, etc.)
    // ═══════════════════════════════════════════════

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null || item.getType().isAir()) return;

        Material type = item.getType();

        if (isGloballyDisabled(type)) {
            event.setCancelled(true);
            player.setCooldown(type, 40);
            scheduler.runSync(player, () -> player.sendActionBar(Component.text("🚫 " + type.name() + " is disabled server-wide!", NamedTextColor.RED)));
            return;
        }

        if (type == Material.SHIELD) {
            if (isGloballyDisabled(Material.SHIELD) || (isTagged(player) && isCombatDisabled(Material.SHIELD))) {
                event.setCancelled(true);
                player.setCooldown(Material.SHIELD, 100);
                scheduler.runSync(player, () -> player.sendActionBar(Component.text("🚫 Shields are disabled!", NamedTextColor.RED)));
                return;
            }
        }

        if (type == Material.TRIDENT && item.getEnchantments().containsKey(Enchantment.RIPTIDE)) {
            if (isGloballyDisabled(Material.TRIDENT) || (isTagged(player) && !settingsManager.getBoolean("combat.riptide_enabled", false))) {
                event.setCancelled(true);
                scheduler.runSync(player, () -> player.sendActionBar(Component.text("🚫 Riptide Tridents are disabled in combat!", NamedTextColor.RED)));
                return;
            }
        }

        if (type == Material.END_CRYSTAL) {
            if (isGloballyDisabled(Material.END_CRYSTAL) || (isTagged(player) && !settingsManager.getBoolean("combat.crystal_enabled", false))) {
                event.setCancelled(true);
                scheduler.runSync(player, () -> player.sendActionBar(Component.text("🚫 End Crystals are disabled in combat!", NamedTextColor.RED)));
                return;
            }
        }

        if (type == Material.RESPAWN_ANCHOR) {
            if (isGloballyDisabled(Material.RESPAWN_ANCHOR) || (isTagged(player) && !settingsManager.getBoolean("combat.anchor_enabled", false))) {
                event.setCancelled(true);
                scheduler.runSync(player, () -> player.sendActionBar(Component.text("🚫 Respawn Anchors are disabled in combat!", NamedTextColor.RED)));
                return;
            }
        }

        if (isTagged(player) && isCombatDisabled(type)) {
            event.setCancelled(true);
            DebugManager.log(DebugFlag.ITEM_DISABLE, "Blocked combat-disabled item: " + type + " for " + player.getName());
            scheduler.runSync(player, () -> player.sendActionBar(Component.text("🚫 Cannot use " + type.name() + " in combat!", NamedTextColor.RED)));
            return;
        }

        long cooldownMs = getCombatCooldownMs(type);
        if (cooldownMs > 0 && isTagged(player)) {
            Map<Material, Long> playerCooldowns = itemCooldowns.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>());
            long now = System.currentTimeMillis();
            Long expiry = playerCooldowns.get(type);

            if (expiry != null && expiry > now) {
                event.setCancelled(true);
                long remaining = (expiry - now) / 1000L;
                DebugManager.log(DebugFlag.ITEM_COOLDOWN, "Blocked " + type + " due to cooldown (" + remaining + "s) for " + player.getName());
                scheduler.runSync(player, () -> player.sendActionBar(Component.text("⏳ " + type.name() + " cooldown: " + remaining + "s", NamedTextColor.RED)));
                return;
            }

            playerCooldowns.put(type, now + cooldownMs);
            DebugManager.log(DebugFlag.ITEM_COOLDOWN, "Applied " + cooldownMs + "ms cooldown to " + type + " for " + player.getName());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMaceAndShieldAttack(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player attacker) {
            ItemStack weapon = attacker.getInventory().getItemInMainHand();
            if (weapon.getType().name().equals("MACE")) {
                if (isGloballyDisabled(weapon.getType()) || (isTagged(attacker) && isCombatDisabled(weapon.getType()))) {
                    event.setDamage(1.0);
                    attacker.setCooldown(weapon.getType(), 100);
                    scheduler.runSync(attacker, () -> attacker.sendActionBar(Component.text("🚫 Mace attacks are disabled!", NamedTextColor.RED)));
                    return;
                }

                long cooldownMs = getCombatCooldownMs(weapon.getType());
                if (cooldownMs > 0 && isTagged(attacker)) {
                    Map<Material, Long> playerCooldowns = itemCooldowns.computeIfAbsent(attacker.getUniqueId(), k -> new ConcurrentHashMap<>());
                    long now = System.currentTimeMillis();
                    Long expiry = playerCooldowns.get(weapon.getType());

                    if (expiry != null && expiry > now) {
                        event.setDamage(1.0);
                        long remaining = (expiry - now) / 1000L;
                        scheduler.runSync(attacker, () -> attacker.sendActionBar(Component.text("⏳ Mace cooldown: " + remaining + "s", NamedTextColor.RED)));
                        return;
                    }
                    playerCooldowns.put(weapon.getType(), now + cooldownMs);
                }
            }
        }

        if (event.getEntity() instanceof Player victim && victim.isBlocking()) {
            if (isGloballyDisabled(Material.SHIELD) || (isTagged(victim) && isCombatDisabled(Material.SHIELD))) {
                victim.setCooldown(Material.SHIELD, 100);
                scheduler.runSync(victim, () -> victim.sendActionBar(Component.text("🚫 Shield blocking is disabled!", NamedTextColor.RED)));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        Material type = event.getItem().getType();

        if (isGloballyDisabled(type) || (isTagged(player) && isCombatDisabled(type))) {
            event.setCancelled(true);
            scheduler.runSync(player, () -> player.sendActionBar(Component.text("🚫 Cannot consume " + type.name() + "!", NamedTextColor.RED)));
        }
    }

    // ═══════════════════════════════════════════════
    //  HELPER METHODS
    // ═══════════════════════════════════════════════

    private boolean isGloballyDisabled(Material material) {
        String key = "item.disabled_global." + material.name().toLowerCase();
        return settingsManager.getBoolean(key, false);
    }

    private boolean isCombatDisabled(Material material) {
        String key = "combat.disabled_item." + material.name().toLowerCase();
        return settingsManager.getBoolean(key, false);
    }

    private long getCombatCooldownMs(Material material) {
        if (material == Material.ENDER_PEARL) {
            return settingsManager.getInt("combat.enderpearl_cooldown", 15) * 1000L;
        }
        if (material.name().equals("WIND_CHARGE")) {
            return settingsManager.getInt("combat.windcharge_cooldown", 10) * 1000L;
        }
        if (material.name().equals("MACE")) {
            return settingsManager.getInt("combat.mace_cooldown", 12) * 1000L;
        }
        String key = "combat.cooldown_sec." + material.name().toLowerCase();
        int seconds = settingsManager.getInt(key, 0);
        return seconds * 1000L;
    }
}