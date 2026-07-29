package com.guildcore.gui;

import com.guildcore.util.TextUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class GUIItemBuilder {
    private final ItemStack item;

    public GUIItemBuilder(Material material) {
        this.item = new ItemStack(material);
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
        if (meta != null) {
            List<Component> loreComponents = new ArrayList<>();
            for (String line : loreLines) {
                loreComponents.add(TextUtil.format(line));
            }
            meta.lore(loreComponents);
            item.setItemMeta(meta);
        }
        return this;
    }

    public ItemStack build() {
        return item;
    }
}
