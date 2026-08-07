// FILE: src/main/java/com/guildcore/raiditems/RaidItemManager.java
package com.guildcore.raiditems;

import com.guildcore.config.SettingsManager;
import com.guildcore.util.TextUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class RaidItemManager {
    private final SettingsManager settingsManager;

    public static final NamespacedKey RAID_ITEM_KEY = new NamespacedKey("guildcore", "raid_item_type");
    public static final NamespacedKey RAID_DURABILITY_KEY = new NamespacedKey("guildcore", "raid_durability");
    public static final NamespacedKey RAID_MAX_DURABILITY_KEY = new NamespacedKey("guildcore", "raid_max_durability");

    public enum RaidItemType {
        LOCK_PICK_WEAK,
        LOCK_PICK_NORMAL,
        LOCK_PICK_FAST,
        LOCK_PICK_REINFORCED,
        SLEDGE_HAMMER,
        RAID_TNT,
        CHARGED_CREEPER_EGG
    }

    public RaidItemManager(SettingsManager settingsManager) {
        this.settingsManager = settingsManager;
    }

    /**
     * Gets the configurable material for a raid item type.
     */
    public Material getMaterialForType(RaidItemType type) {
        String key = switch (type) {
            case LOCK_PICK_WEAK -> "items.lockpick_weak.material";
            case LOCK_PICK_NORMAL -> "items.lockpick_normal.material";
            case LOCK_PICK_FAST -> "items.lockpick_fast.material";
            case LOCK_PICK_REINFORCED -> "items.lockpick_reinforced.material";
            case SLEDGE_HAMMER -> "items.sledge_hammer.material";
            case RAID_TNT -> "items.raid_tnt.material";
            case CHARGED_CREEPER_EGG -> "items.charged_creeper.material";
        };

        Material defaultMat = getDefaultMaterial(type);
        String configMat = settingsManager.getString(key, defaultMat.name());
        Material mat = Material.matchMaterial(configMat);
        return mat != null ? mat : defaultMat;
    }

    private Material getDefaultMaterial(RaidItemType type) {
        return switch (type) {
            case LOCK_PICK_WEAK -> Material.IRON_NUGGET;
            case LOCK_PICK_NORMAL -> Material.IRON_INGOT;
            case LOCK_PICK_FAST -> Material.GOLD_INGOT;
            case LOCK_PICK_REINFORCED -> Material.NETHERITE_INGOT;
            case SLEDGE_HAMMER -> Material.NETHERITE_AXE;
            case RAID_TNT -> Material.TNT;
            case CHARGED_CREEPER_EGG -> Material.CREEPER_SPAWN_EGG;
        };
    }

    public String getDisplayName(RaidItemType type) {
        return switch (type) {
            case LOCK_PICK_WEAK -> "<gradient:#8B8B8B:#A9A9A9><b>Weak Lock Pick</b></gradient>";
            case LOCK_PICK_NORMAL -> "<gradient:#C0C0C0:#E0E0E0><b>Lock Pick</b></gradient>";
            case LOCK_PICK_FAST -> "<gradient:#FFD700:#FFA500><b>Fast Lock Pick</b></gradient>";
            case LOCK_PICK_REINFORCED -> "<gradient:#4A0E4E:#8B008B><b>Reinforced Lock Pick</b></gradient>";
            case SLEDGE_HAMMER -> "<gradient:#FF4500:#DC143C><b>⚔ Sledge Hammer</b></gradient>";
            case RAID_TNT -> "<gradient:#FF0000:#8B0000><b>💣 Raid TNT</b></gradient>";
            case CHARGED_CREEPER_EGG -> "<gradient:#00FF00:#32CD32><b>⚡ Charged Creeper Egg</b></gradient>";
        };
    }

    public String getPlainName(RaidItemType type) {
        return switch (type) {
            case LOCK_PICK_WEAK -> "Weak Lock Pick";
            case LOCK_PICK_NORMAL -> "Lock Pick";
            case LOCK_PICK_FAST -> "Fast Lock Pick";
            case LOCK_PICK_REINFORCED -> "Reinforced Lock Pick";
            case SLEDGE_HAMMER -> "Sledge Hammer";
            case RAID_TNT -> "Raid TNT";
            case CHARGED_CREEPER_EGG -> "Charged Creeper Egg";
        };
    }

    /**
     * Gets the configurable durability for a raid item type.
     */
    private int getDurabilityForType(RaidItemType type) {
        return switch (type) {
            case LOCK_PICK_WEAK -> settingsManager.getInt("lockpick.weak.durability", 5);
            case LOCK_PICK_NORMAL -> settingsManager.getInt("lockpick.normal.durability", 10);
            case LOCK_PICK_FAST -> settingsManager.getInt("lockpick.fast.durability", 1);
            case LOCK_PICK_REINFORCED -> settingsManager.getInt("lockpick.reinforced.durability", 45);
            case SLEDGE_HAMMER -> settingsManager.getInt("core.sledgehammer_durability", 50);
            default -> -1;
        };
    }

    /**
     * Creates a new raid item with the given type and amount.
     */
    public ItemStack createItem(RaidItemType type, int amount) {
        int durability = getDurabilityForType(type);
        return createItem(type, amount, durability);
    }

    /**
     * Creates a raid item with specific durability.
     */
    public ItemStack createItem(RaidItemType type, int amount, int durability) {
        Material material = getMaterialForType(type);
        ItemStack item = new ItemStack(material, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        // Set display name
        meta.displayName(TextUtil.format(getDisplayName(type)));

        // Store type and durability in persistent data
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(RAID_ITEM_KEY, PersistentDataType.STRING, type.name());

        if (durability > 0) {
            pdc.set(RAID_DURABILITY_KEY, PersistentDataType.INTEGER, durability);
            pdc.set(RAID_MAX_DURABILITY_KEY, PersistentDataType.INTEGER, durability);
        }

        // Hide ALL vanilla attributes
        hideAllAttributes(meta);

        // Add glow effect
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        meta.addItemFlags(ItemFlag.HIDE_DYE);

        // Build dynamic lore
        updateLore(meta, type, durability);

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Updates the lore of a raid item to reflect current durability and stats.
     */
    public void updateLore(ItemMeta meta, RaidItemType type, int currentDurability) {
        if (meta == null) return;

        List<Component> lore = new ArrayList<>();
        lore.add(TextUtil.format("<dark_gray>Raid Item</dark_gray>"));
        lore.add(Component.empty());

        int maxDurability = getDurabilityForType(type);

        switch (type) {
            case LOCK_PICK_WEAK -> {
                lore.add(TextUtil.format("<gray>A crude lock pick with low success rate</gray>"));
                lore.add(TextUtil.format("<gray>Success: <yellow>" + settingsManager.getInt("lockpick.weak.chance", 10) + "%</yellow></gray>"));
                lore.add(TextUtil.format("<gray>Durability: <yellow>" + currentDurability + "/" + maxDurability + " uses</yellow></gray>"));
                lore.add(TextUtil.format("<gray>Breaks on fail</gray>"));
                lore.add(TextUtil.format("<green>Stealth: No raid tag applied</green>"));
            }
            case LOCK_PICK_NORMAL -> {
                lore.add(TextUtil.format("<gray>A standard lock pick for raiding</gray>"));
                lore.add(TextUtil.format("<gray>Success: <yellow>" + settingsManager.getInt("lockpick.normal.chance", 20) + "%</yellow></gray>"));
                lore.add(TextUtil.format("<gray>Durability: <yellow>" + currentDurability + "/" + maxDurability + " uses</yellow></gray>"));
                lore.add(TextUtil.format("<green>Stealth: No raid tag applied</green>"));
            }
            case LOCK_PICK_FAST -> {
                lore.add(TextUtil.format("<gray>A golden lock pick with very high success</gray>"));
                lore.add(TextUtil.format("<gray>Success: <yellow>" + settingsManager.getInt("lockpick.fast.chance", 75) + "%</yellow></gray>"));
                lore.add(TextUtil.format("<gray>Durability: <red>" + currentDurability + "/" + maxDurability + " use</red></gray>"));
                lore.add(TextUtil.format("<green>Stealth: No raid tag applied</green>"));
            }
            case LOCK_PICK_REINFORCED -> {
                lore.add(TextUtil.format("<gray>A durable lock pick with chance to save</gray>"));
                lore.add(TextUtil.format("<gray>Success: <yellow>" + settingsManager.getInt("lockpick.reinforced.chance", 20) + "%</yellow></gray>"));
                lore.add(TextUtil.format("<gray>Durability: <yellow>" + currentDurability + "/" + maxDurability + " uses</yellow></gray>"));
                lore.add(TextUtil.format("<gray>Save Chance: <light_purple>" + settingsManager.getInt("lockpick.reinforced.save_chance", 15) + "%</light_purple></gray>"));
                lore.add(TextUtil.format("<green>Stealth: No raid tag applied</green>"));
            }
            case SLEDGE_HAMMER -> {
                lore.add(TextUtil.format("<gray>Deals damage to Guild Cores on hit</gray>"));
                lore.add(TextUtil.format("<gray>Damage: <red>" + settingsManager.getInt("core.sledgehammer_damage", 5) + " HP</red></gray>"));
                lore.add(TextUtil.format("<gray>Durability: <yellow>" + currentDurability + "/" + maxDurability + " hits</yellow></gray>"));
                lore.add(TextUtil.format("<red>⚠ Applies Raid Tag on use!</red>"));
            }
            case RAID_TNT -> {
                lore.add(TextUtil.format("<gray>Explosive that transforms blocks in claims</gray>"));
                lore.add(TextUtil.format("<gray>Chain: Reinforced Deepslate → Obsidian → Crying Obsidian → Cobblestone</gray>"));
                lore.add(TextUtil.format("<gray>Core Damage: <red>" + settingsManager.getInt("core.raid_tnt_damage", 10) + " HP</red></gray>"));
                lore.add(TextUtil.format("<red>⚠ Applies Raid Tag in enemy claims!</red>"));
                lore.add(TextUtil.format("<green>✔ No player damage</green>"));
                lore.add(TextUtil.format("<green>✔ Works in wilderness</green>"));
            }
            case CHARGED_CREEPER_EGG -> {
                lore.add(TextUtil.format("<gray>Spawns a charged creeper that destroys soft blocks</gray>"));
                lore.add(TextUtil.format("<gray>Hard blocks (obsidian, etc.) survive</gray>"));
                lore.add(TextUtil.format("<gray>Fuse: <yellow>" + String.format("%.1f", settingsManager.getDouble("creeper.fuse_seconds", 3.0)) + "s</yellow></gray>"));
                lore.add(TextUtil.format("<red>⚠ Applies Raid Tag in enemy claims!</red>"));
                lore.add(TextUtil.format("<green>✔ No player damage</green>"));
                lore.add(TextUtil.format("<green>✔ Works in wilderness</green>"));
            }
        }

        meta.lore(lore);
    }

    /**
     * Updates the lore on an existing item to reflect current durability.
     */
    public void updateItemLore(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        RaidItemType type = getRaidItemType(item);
        if (type == null) return;

        int currentDurability = getDurability(item);
        updateLore(meta, type, currentDurability);
        item.setItemMeta(meta);
    }

    /**
     * Hides all vanilla attributes from the item.
     */
    private void hideAllAttributes(ItemMeta meta) {
        // Remove all attribute modifiers
        if (meta.getAttributeModifiers() != null) {
            for (Attribute attribute : Attribute.values()) {
                meta.removeAttributeModifier(attribute);
            }
        }

        // For tools/weapons, explicitly zero out attack stats
        meta.addAttributeModifier(Attribute.ATTACK_DAMAGE,
                new AttributeModifier(
                        new NamespacedKey("guildcore", "hidden_damage"),
                        0,
                        AttributeModifier.Operation.ADD_NUMBER,
                        EquipmentSlotGroup.MAINHAND
                ));

        meta.addAttributeModifier(Attribute.ATTACK_SPEED,
                new AttributeModifier(
                        new NamespacedKey("guildcore", "hidden_speed"),
                        0,
                        AttributeModifier.Operation.ADD_NUMBER,
                        EquipmentSlotGroup.MAINHAND
                ));
    }

    /**
     * Gets the RaidItemType from an item's persistent data.
     */
    public RaidItemType getRaidItemType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        String typeStr = pdc.get(RAID_ITEM_KEY, PersistentDataType.STRING);
        if (typeStr == null) return null;
        try {
            return RaidItemType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Quick check if an item is any raid item.
     */
    public boolean isRaidItem(ItemStack item) {
        return getRaidItemType(item) != null;
    }

    /**
     * Gets the current durability from an item's persistent data.
     */
    public int getDurability(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        Integer val = item.getItemMeta().getPersistentDataContainer()
                .get(RAID_DURABILITY_KEY, PersistentDataType.INTEGER);
        return val != null ? val : 0;
    }

    /**
     * Gets the max durability from an item's persistent data.
     */
    public int getMaxDurability(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        Integer val = item.getItemMeta().getPersistentDataContainer()
                .get(RAID_MAX_DURABILITY_KEY, PersistentDataType.INTEGER);
        return val != null ? val : getDurabilityForType(getRaidItemType(item));
    }

    /**
     * Consumes one durability from the item. Returns true if item still exists, false if destroyed.
     */
    public boolean consumeDurability(ItemStack item, Player player) {
        if (item == null || !item.hasItemMeta()) return false;
        RaidItemType type = getRaidItemType(item);
        if (type == null || getDurabilityForType(type) <= 0) return true;

        // Reinforced lock pick save chance
        if (type == RaidItemType.LOCK_PICK_REINFORCED) {
            int saveChance = settingsManager.getInt("lockpick.reinforced.save_chance", 15);
            if (Math.random() * 100 < saveChance) {
                player.sendActionBar(TextUtil.format("<light_purple>✨ Lock pick durability saved!</light_purple>"));
                return true;
            }
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        int current = pdc.getOrDefault(RAID_DURABILITY_KEY, PersistentDataType.INTEGER, 1);
        current--;

        if (current <= 0) {
            player.getInventory().setItemInMainHand(null);
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
            player.sendActionBar(TextUtil.format("<red>Your " + getPlainName(type) + " broke!</red>"));
            return false;
        }

        pdc.set(RAID_DURABILITY_KEY, PersistentDataType.INTEGER, current);

        // Update lore to show new durability
        updateLore(meta, type, current);
        item.setItemMeta(meta);

        return true;
    }
}