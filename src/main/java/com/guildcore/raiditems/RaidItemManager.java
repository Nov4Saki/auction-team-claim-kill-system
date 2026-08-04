package com.guildcore.raiditems;

import com.guildcore.config.SettingsManager;
import com.guildcore.util.TextUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
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

    public enum RaidItemType {
        LOCK_PICK_WEAK(Material.IRON_NUGGET, "<gradient:#8B8B8B:#A9A9A9><b>Weak Lock Pick</b></gradient>", 5),
        LOCK_PICK_NORMAL(Material.IRON_INGOT, "<gradient:#C0C0C0:#E0E0E0><b>Lock Pick</b></gradient>", 10),
        LOCK_PICK_FAST(Material.GOLD_INGOT, "<gradient:#FFD700:#FFA500><b>Fast Lock Pick</b></gradient>", 1),
        LOCK_PICK_REINFORCED(Material.NETHERITE_INGOT, "<gradient:#4A0E4E:#8B008B><b>Reinforced Lock Pick</b></gradient>", 45),
        SLEDGE_HAMMER(Material.NETHERITE_AXE, "<gradient:#FF4500:#DC143C><b>⚔ Sledge Hammer</b></gradient>", 50),
        RAID_TNT(Material.TNT, "<gradient:#FF0000:#8B0000><b>💣 Raid TNT</b></gradient>", -1),
        CHARGED_CREEPER_EGG(Material.CREEPER_SPAWN_EGG, "<gradient:#00FF00:#32CD32><b>⚡ Charged Creeper Egg</b></gradient>", -1);

        public final Material material;
        public final String displayName;
        public final int defaultDurability;

        RaidItemType(Material material, String displayName, int defaultDurability) {
            this.material = material;
            this.displayName = displayName;
            this.defaultDurability = defaultDurability;
        }
    }

    public RaidItemManager(SettingsManager settingsManager) {
        this.settingsManager = settingsManager;
    }

    public ItemStack createItem(RaidItemType type, int amount) {
        ItemStack item = new ItemStack(type.material, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.displayName(TextUtil.format(type.displayName));
        meta.getPersistentDataContainer().set(RAID_ITEM_KEY, PersistentDataType.STRING, type.name());

        // Add durability for tools
        if (type.defaultDurability > 0) {
            int durability = getDurabilityForType(type);
            meta.getPersistentDataContainer().set(RAID_DURABILITY_KEY, PersistentDataType.INTEGER, durability);
        }

        // Glow effect
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

        List<Component> lore = new ArrayList<>();
        lore.add(TextUtil.format("<dark_gray>Raid Item</dark_gray>"));
        lore.add(Component.empty());

        switch (type) {
            case LOCK_PICK_WEAK -> {
                lore.add(TextUtil.format("<gray>A crude lock pick with low success rate</gray>"));
                lore.add(TextUtil.format("<gray>Success: <yellow>" + settingsManager.getInt("lockpick.weak.chance", 10) + "%</yellow></gray>"));
                lore.add(TextUtil.format("<gray>Durability: <yellow>" + getDurabilityForType(type) + " uses</yellow></gray>"));
            }
            case LOCK_PICK_NORMAL -> {
                lore.add(TextUtil.format("<gray>A standard lock pick for raiding</gray>"));
                lore.add(TextUtil.format("<gray>Success: <yellow>" + settingsManager.getInt("lockpick.normal.chance", 20) + "%</yellow></gray>"));
                lore.add(TextUtil.format("<gray>Durability: <yellow>" + getDurabilityForType(type) + " uses</yellow></gray>"));
            }
            case LOCK_PICK_FAST -> {
                lore.add(TextUtil.format("<gray>A golden lock pick with very high success</gray>"));
                lore.add(TextUtil.format("<gray>Success: <yellow>" + settingsManager.getInt("lockpick.fast.chance", 75) + "%</yellow></gray>"));
                lore.add(TextUtil.format("<gray>Durability: <red>" + getDurabilityForType(type) + " use</red></gray>"));
            }
            case LOCK_PICK_REINFORCED -> {
                lore.add(TextUtil.format("<gray>A durable lock pick with chance to save</gray>"));
                lore.add(TextUtil.format("<gray>Success: <yellow>" + settingsManager.getInt("lockpick.reinforced.chance", 20) + "%</yellow></gray>"));
                lore.add(TextUtil.format("<gray>Durability: <yellow>" + getDurabilityForType(type) + " uses</yellow></gray>"));
                lore.add(TextUtil.format("<gray>Save Chance: <light_purple>" + settingsManager.getInt("lockpick.reinforced.save_chance", 15) + "%</light_purple></gray>"));
            }
            case SLEDGE_HAMMER -> {
                lore.add(TextUtil.format("<gray>Deals damage to Guild Cores on hit</gray>"));
                lore.add(TextUtil.format("<gray>Damage: <red>" + settingsManager.getInt("core.sledgehammer_damage", 5) + " HP</red></gray>"));
                lore.add(TextUtil.format("<gray>Durability: <yellow>" + getDurabilityForType(type) + " hits</yellow></gray>"));
                lore.add(TextUtil.format("<red>⚠ Applies Raid Tag on use!</red>"));
            }
            case RAID_TNT -> {
                lore.add(TextUtil.format("<gray>Explosive that transforms blocks in claims</gray>"));
                lore.add(TextUtil.format("<gray>Core Damage: <red>" + settingsManager.getInt("core.raid_tnt_damage", 10) + " HP</red></gray>"));
                lore.add(TextUtil.format("<red>⚠ Applies Raid Tag on use!</red>"));
                lore.add(TextUtil.format("<green>✔ No player damage</green>"));
            }
            case CHARGED_CREEPER_EGG -> {
                lore.add(TextUtil.format("<gray>Spawns a charged creeper that destroys soft blocks</gray>"));
                lore.add(TextUtil.format("<gray>Hard blocks (obsidian, etc.) survive</gray>"));
                lore.add(TextUtil.format("<red>⚠ Applies Raid Tag on use!</red>"));
                lore.add(TextUtil.format("<green>✔ No player damage</green>"));
            }
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

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

    public boolean isRaidItem(ItemStack item) {
        return getRaidItemType(item) != null;
    }

    public int getDurability(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        Integer val = item.getItemMeta().getPersistentDataContainer().get(RAID_DURABILITY_KEY, PersistentDataType.INTEGER);
        return val != null ? val : 0;
    }

    public boolean consumeDurability(ItemStack item, Player player) {
        if (item == null || !item.hasItemMeta()) return false;
        RaidItemType type = getRaidItemType(item);
        if (type == null || type.defaultDurability <= 0) return true;

        // Reinforced lock pick save chance
        if (type == RaidItemType.LOCK_PICK_REINFORCED) {
            int saveChance = settingsManager.getInt("lockpick.reinforced.save_chance", 15);
            if (Math.random() * 100 < saveChance) {
                player.sendActionBar(TextUtil.format("<light_purple>✨ Lock pick durability saved!</light_purple>"));
                return true;
            }
        }

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        int current = pdc.getOrDefault(RAID_DURABILITY_KEY, PersistentDataType.INTEGER, 1);
        current--;

        if (current <= 0) {
            player.getInventory().setItemInMainHand(null);
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
            player.sendActionBar(TextUtil.format("<red>Your " + type.displayName + " <red>broke!</red>"));
            return false;
        }

        pdc.set(RAID_DURABILITY_KEY, PersistentDataType.INTEGER, current);
        item.setItemMeta(meta);
        return true;
    }

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
}
