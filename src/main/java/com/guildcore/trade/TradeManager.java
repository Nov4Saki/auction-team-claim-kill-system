package com.guildcore.trade;

import com.guildcore.gui.GUIItemBuilder;
import com.guildcore.gui.holders.TradeGUIHolder;
import com.guildcore.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TradeManager {
    // Target -> Requester
    private final Map<UUID, UUID> pendingRequests = new ConcurrentHashMap<>();

    public void requestTrade(Player sender, Player target) {
        pendingRequests.put(target.getUniqueId(), sender.getUniqueId());
        sender.sendMessage(TextUtil.format("<green>Sent trade request to " + target.getName() + ".</green>"));
        target.sendMessage(TextUtil.format("<gold>🤝 <yellow>" + sender.getName() + "</yellow> requested to trade with you. Type <green>/trade accept</green> to accept.</gold>"));
    }

    public void acceptTrade(Player target) {
        UUID senderUuid = pendingRequests.remove(target.getUniqueId());
        if (senderUuid == null) {
            target.sendMessage(TextUtil.format("<red>No pending trade request.</red>"));
            return;
        }
        Player sender = Bukkit.getPlayer(senderUuid);
        if (sender == null || !sender.isOnline()) {
            target.sendMessage(TextUtil.format("<red>Sender is no longer online.</red>"));
            return;
        }

        openTradeSession(sender, target);
    }

    public void openTradeSession(Player p1, Player p2) {
        Inventory inv = Bukkit.createInventory(new TradeGUIHolder(p1.getUniqueId(), p2.getUniqueId()), 54, TextUtil.format("<gold>🤝 Trade: " + p1.getName() + " ↔ " + p2.getName() + "</gold>"));

        // Divider
        int[] dividers = {4, 13, 22, 31, 40};
        for (int d : dividers) {
            inv.setItem(d, new GUIItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name("<gray>|</gray>").build());
        }

        inv.setItem(45, new GUIItemBuilder(Material.RED_WOOL).name("<red>✖ Not Ready (" + p1.getName() + ")</red>").build());
        inv.setItem(53, new GUIItemBuilder(Material.RED_WOOL).name("<red>✖ Not Ready (" + p2.getName() + ")</red>").build());

        p1.openInventory(inv);
        p2.openInventory(inv);
    }
}
