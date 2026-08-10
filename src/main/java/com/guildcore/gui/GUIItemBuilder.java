package com.guildcore.gui;

import com.guildcore.util.TextUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GUIItemBuilder {
    private final ItemStack item;

    public GUIItemBuilder(Material material) {
        this.item = new ItemStack(material);
    }

    public GUIItemBuilder(ItemStack existing) {
        this.item = existing != null ? existing.clone() : new ItemStack(Material.STONE);
    }

    public GUIItemBuilder name(String name) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(TextUtil.format(name));
            item.setItemMeta(meta);
        }
        return this;
    }

    public GUIItemBuilder lore(List<String> loreLines) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null && loreLines != null) {
            List<Component> loreComponents = new ArrayList<>();
            for (String line : loreLines) {
                loreComponents.add(TextUtil.format(line));
            }
            meta.lore(loreComponents);
            item.setItemMeta(meta);
        }
        return this;
    }

    public GUIItemBuilder lore(String... loreLines) {
        return lore(Arrays.asList(loreLines));
    }

    public GUIItemBuilder amount(int amount) {
        item.setAmount(Math.max(1, amount));
        return this;
    }

    public GUIItemBuilder glow(boolean glowing) {
        if (!glowing) return this;
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        return this;
    }

    public GUIItemBuilder hideFlags() {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.addItemFlags(ItemFlag.values());
            item.setItemMeta(meta);
        }
        return this;
    }

    public GUIItemBuilder skullOwner(org.bukkit.OfflinePlayer owner) {
        if (owner != null && item.getItemMeta() instanceof org.bukkit.inventory.meta.SkullMeta skullMeta) {
            skullMeta.setOwningPlayer(owner);
            item.setItemMeta(skullMeta);
        }
        return this;
    }

    public ItemStack build() {
        return item;
    }
}

