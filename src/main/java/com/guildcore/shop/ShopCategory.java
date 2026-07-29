package com.guildcore.shop;

import org.bukkit.Material;

public class ShopCategory {
    private final int id;
    private String name;
    private Material icon;
    private int slot;

    public ShopCategory(int id, String name, Material icon, int slot) {
        this.id = id;
        this.name = name;
        this.icon = icon;
        this.slot = slot;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Material getIcon() { return icon; }
    public void setIcon(Material icon) { this.icon = icon; }
    public int getSlot() { return slot; }
    public void setSlot(int slot) { this.slot = slot; }
}
