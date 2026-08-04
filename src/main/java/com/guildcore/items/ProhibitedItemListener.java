package com.guildcore.items;

import com.guildcore.util.TextUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.SmithItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.ItemStack;

public class ProhibitedItemListener implements Listener {
    private final ProhibitedItemManager prohibitedManager;

    public ProhibitedItemListener(ProhibitedItemManager prohibitedManager) {
        this.prohibitedManager = prohibitedManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        if (prohibitedManager == null) return;
        if (event.getRecipe() != null && event.getRecipe().getResult() != null) {
            if (prohibitedManager.isProhibited(event.getRecipe().getResult())) {
                event.getInventory().setResult(null);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {
        if (prohibitedManager == null) return;
        if (event.getRecipe() != null && prohibitedManager.isProhibited(event.getRecipe().getResult())) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
                player.sendMessage(TextUtil.format("<red>✖ Crafting this item is prohibited by Royal Decree!</red>"));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        if (prohibitedManager == null) return;
        if (event.getResult() != null && prohibitedManager.isProhibited(event.getResult())) {
            event.setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSmithItem(SmithItemEvent event) {
        if (prohibitedManager == null) return;
        if (event.getCurrentItem() != null && prohibitedManager.isProhibited(event.getCurrentItem())) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
                player.sendMessage(TextUtil.format("<red>✖ Smithing this item is prohibited by Royal Decree!</red>"));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (prohibitedManager == null) return;
        if (event.getEntity() instanceof Player player) {
            ItemStack item = event.getItem().getItemStack();
            if (prohibitedManager.isProhibited(item)) {
                event.setCancelled(true);
                event.getItem().remove();
                player.sendMessage(TextUtil.format("<red>⚠ Prohibited item (" + item.getType().name() + ") destroyed on pickup!</red>"));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (prohibitedManager == null) return;
        ItemStack item = event.getItemDrop().getItemStack();
        if (prohibitedManager.isProhibited(item)) {
            event.getItemDrop().remove();
            event.getPlayer().sendMessage(TextUtil.format("<red>⚠ Prohibited item (" + item.getType().name() + ") destroyed on drop!</red>"));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemHeld(PlayerItemHeldEvent event) {
        if (prohibitedManager == null) return;
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItem(event.getNewSlot());
        if (item != null && prohibitedManager.isProhibited(item)) {
            player.getInventory().setItem(event.getNewSlot(), null);
            player.sendMessage(TextUtil.format("<red>⚠ Prohibited item (" + item.getType().name() + ") destroyed on slot change!</red>"));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (prohibitedManager == null) return;
        ItemStack item = event.getItem();
        if (item != null && prohibitedManager.isProhibited(item)) {
            event.setCancelled(true);
            Player player = event.getPlayer();
            player.getInventory().removeItem(item);
            player.sendMessage(TextUtil.format("<red>⚠ Prohibited item (" + item.getType().name() + ") destroyed on interact!</red>"));
        }
    }
}
