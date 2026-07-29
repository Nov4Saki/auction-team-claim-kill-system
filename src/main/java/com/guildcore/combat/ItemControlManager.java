package com.guildcore.combat;

import com.guildcore.config.SettingsManager;
import com.guildcore.debug.DebugFlag;
import com.guildcore.debug.DebugManager;
import com.guildcore.scheduler.SchedulerWrapper;
import com.guildcore.util.TextUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
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

        // 1. Server-Wide Item Disable Check (Active whether in combat or not)
        if (isGloballyDisabled(type)) {
            event.setCancelled(true);
            player.setCooldown(type, 40);
            scheduler.runSync(player, () -> player.sendActionBar(Component.text("🚫 " + type.name() + " is disabled server-wide!", NamedTextColor.RED)));
            return;
        }

        // 2. Shield Usage Block
        if (type == Material.SHIELD) {
            if (isGloballyDisabled(Material.SHIELD) || (combatTagManager.isTagged(player) && isCombatDisabled(Material.SHIELD))) {
                event.setCancelled(true);
                player.setCooldown(Material.SHIELD, 100);
                scheduler.runSync(player, () -> player.sendActionBar(Component.text("🚫 Shields are disabled!", NamedTextColor.RED)));
                return;
            }
        }

        // 3. Riptide Trident check
        if (type == Material.TRIDENT && item.getEnchantments().containsKey(Enchantment.RIPTIDE)) {
            if (isGloballyDisabled(Material.TRIDENT) || (combatTagManager.isTagged(player) && !settingsManager.getBoolean("combat.riptide_enabled", false))) {
                event.setCancelled(true);
                scheduler.runSync(player, () -> player.sendActionBar(Component.text("🚫 Riptide Tridents are disabled in combat!", NamedTextColor.RED)));
                return;
            }
        }

        // 4. End Crystal check
        if (type == Material.END_CRYSTAL) {
            if (isGloballyDisabled(Material.END_CRYSTAL) || (combatTagManager.isTagged(player) && !settingsManager.getBoolean("combat.crystal_enabled", false))) {
                event.setCancelled(true);
                scheduler.runSync(player, () -> player.sendActionBar(Component.text("🚫 End Crystals are disabled in combat!", NamedTextColor.RED)));
                return;
            }
        }

        // 5. Respawn Anchor check
        if (type == Material.RESPAWN_ANCHOR) {
            if (isGloballyDisabled(Material.RESPAWN_ANCHOR) || (combatTagManager.isTagged(player) && !settingsManager.getBoolean("combat.anchor_enabled", false))) {
                event.setCancelled(true);
                scheduler.runSync(player, () -> player.sendActionBar(Component.text("🚫 Respawn Anchors are disabled in combat!", NamedTextColor.RED)));
                return;
            }
        }

        // 6. Combat Item Disable
        if (combatTagManager.isTagged(player) && isCombatDisabled(type)) {
            event.setCancelled(true);
            DebugManager.log(DebugFlag.ITEM_DISABLE, "Blocked combat-disabled item: " + type + " for " + player.getName());
            scheduler.runSync(player, () -> player.sendActionBar(Component.text("🚫 Cannot use " + type.name() + " in combat!", NamedTextColor.RED)));
            return;
        }

        // 7. Custom Combat Cooldowns
        long cooldownMs = getCombatCooldownMs(type);
        if (cooldownMs > 0 && combatTagManager.isTagged(player)) {
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
        // Mace Attack Control
        if (event.getDamager() instanceof Player attacker) {
            ItemStack weapon = attacker.getInventory().getItemInMainHand();
            if (weapon.getType().name().equals("MACE")) {
                if (isGloballyDisabled(weapon.getType()) || (combatTagManager.isTagged(attacker) && isCombatDisabled(weapon.getType()))) {
                    event.setDamage(1.0); // Reduce to basic punch damage
                    attacker.setCooldown(weapon.getType(), 100);
                    scheduler.runSync(attacker, () -> attacker.sendActionBar(Component.text("🚫 Mace attacks are disabled!", NamedTextColor.RED)));
                    return;
                }

                long cooldownMs = getCombatCooldownMs(weapon.getType());
                if (cooldownMs > 0 && combatTagManager.isTagged(attacker)) {
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

        // Shield Blocking Block
        if (event.getEntity() instanceof Player victim && victim.isBlocking()) {
            if (isGloballyDisabled(Material.SHIELD) || (combatTagManager.isTagged(victim) && isCombatDisabled(Material.SHIELD))) {
                victim.setCooldown(Material.SHIELD, 100);
                scheduler.runSync(victim, () -> victim.sendActionBar(Component.text("🚫 Shield blocking is disabled!", NamedTextColor.RED)));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        Material type = event.getItem().getType();

        if (isGloballyDisabled(type) || (combatTagManager.isTagged(player) && isCombatDisabled(type))) {
            event.setCancelled(true);
            scheduler.runSync(player, () -> player.sendActionBar(Component.text("🚫 Cannot consume " + type.name() + "!", NamedTextColor.RED)));
        }
    }

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
