package com.guildcore.auction;

import org.bukkit.Material;

public class AuctionCategoryUtil {
    public static String getCategory(Material material) {
        if (material == null) return "MISC";
        String name = material.name();

        if (name.contains("SWORD") || name.contains("BOW") || name.contains("AXE") || name.contains("TRIDENT") || name.contains("CROSSBOW") || name.contains("MACE")) {
            return "WEAPONS";
        }
        if (name.contains("HELMET") || name.contains("CHESTPLATE") || name.contains("LEGGINGS") || name.contains("BOOTS") || name.contains("SHIELD") || name.contains("ELYTRA")) {
            return "ARMOR";
        }
        if (name.contains("PICKAXE") || name.contains("SHOVEL") || name.contains("HOE") || name.contains("SHEARS") || name.contains("FISHING")) {
            return "TOOLS";
        }
        if (name.contains("SHULKER_BOX")) {
            return "SHULKERS";
        }
        if (name.contains("POTION") || name.contains("GOLDEN_APPLE") || name.contains("FOOD") || material.isEdible()) {
            return "POTIONS";
        }
        if (material.isBlock()) {
            return "BLOCKS";
        }
        return "MISC";
    }
}
