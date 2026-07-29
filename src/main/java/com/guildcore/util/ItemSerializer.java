package com.guildcore.util;

import com.guildcore.debug.DebugFlag;
import com.guildcore.debug.DebugManager;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class ItemSerializer {

    public static String serializeItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return "";
        }
        try {
            byte[] bytes = item.serializeAsBytes();
            String encoded = Base64.getEncoder().encodeToString(bytes);
            DebugManager.log(DebugFlag.VAULT_SERIALIZATION, "Serialized item " + item.getType() + " (" + bytes.length + " bytes)");
            return encoded;
        } catch (Exception e) {
            DebugManager.log(DebugFlag.VAULT_SERIALIZATION, "Failed to serialize item: " + e.getMessage());
            return "";
        }
    }

    public static ItemStack deserializeItem(String base64) {
        if (base64 == null || base64.trim().isEmpty()) {
            return new ItemStack(Material.AIR);
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(base64);
            return ItemStack.deserializeBytes(bytes);
        } catch (Exception e) {
            DebugManager.log(DebugFlag.VAULT_SERIALIZATION, "Failed to deserialize item: " + e.getMessage());
            return new ItemStack(Material.AIR);
        }
    }

    public static String serializeInventory(ItemStack[] contents) {
        if (contents == null) contents = new ItemStack[0];
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             BukkitObjectOutputStream boos = new BukkitObjectOutputStream(baos)) {
            boos.writeInt(contents.length);
            for (ItemStack item : contents) {
                if (item == null || item.getType() == Material.AIR) {
                    boos.writeObject(null);
                } else {
                    boos.writeObject(item.serializeAsBytes());
                }
            }
            String result = Base64.getEncoder().encodeToString(baos.toByteArray());
            DebugManager.log(DebugFlag.VAULT_SERIALIZATION, "Serialized inventory size " + contents.length + " (" + result.length() + " chars)");
            return result;
        } catch (Exception e) {
            DebugManager.log(DebugFlag.VAULT_SERIALIZATION, "Failed to serialize inventory: " + e.getMessage());
            return "";
        }
    }

    public static ItemStack[] deserializeInventory(String base64) {
        if (base64 == null || base64.trim().isEmpty()) {
            return new ItemStack[0];
        }
        try {
            byte[] data = Base64.getDecoder().decode(base64);
            ByteArrayInputStream bais = new ByteArrayInputStream(data);
            try (BukkitObjectInputStream bois = new BukkitObjectInputStream(bais)) {
                int size = bois.readInt();
                ItemStack[] contents = new ItemStack[size];
                for (int i = 0; i < size; i++) {
                    byte[] itemBytes = (byte[]) bois.readObject();
                    contents[i] = itemBytes == null ? null : ItemStack.deserializeBytes(itemBytes);
                }
                return contents;
            }
        } catch (Exception e) {
            DebugManager.log(DebugFlag.VAULT_SERIALIZATION, "Failed to deserialize inventory: " + e.getMessage());
            return new ItemStack[0];
        }
    }

    public static String serializeItemList(List<ItemStack> list) {
        if (list == null) return "";
        return serializeInventory(list.toArray(new ItemStack[0]));
    }

    public static List<ItemStack> deserializeItemList(String base64) {
        ItemStack[] arr = deserializeInventory(base64);
        List<ItemStack> list = new ArrayList<>();
        for (ItemStack item : arr) {
            if (item != null && item.getType() != Material.AIR) {
                list.add(item);
            }
        }
        return list;
    }
}
