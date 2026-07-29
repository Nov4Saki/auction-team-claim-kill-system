package com.guildcore.crates;

import org.bukkit.inventory.ItemStack;
import java.util.List;

public class Crate {
    private final String name;
    private String displayName;
    private ItemStack keyItem;
    private List<ItemStack> contents;

    public Crate(String name, String displayName, ItemStack keyItem, List<ItemStack> contents) {
        this.name = name;
        this.displayName = displayName;
        this.keyItem = keyItem;
        this.contents = contents;
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public ItemStack getKeyItem() {
        return keyItem;
    }

    public void setKeyItem(ItemStack keyItem) {
        this.keyItem = keyItem;
    }

    public List<ItemStack> getContents() {
        return contents;
    }

    public void setContents(List<ItemStack> contents) {
        this.contents = contents;
    }
}
