package com.guildcore.trade;

import com.guildcore.config.SettingsManager;
import com.guildcore.gui.GUIItemBuilder;
import com.guildcore.gui.holders.TradeGUIHolder;
import com.guildcore.scheduler.SchedulerWrapper;
import com.guildcore.util.TextUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TradeManager {
    private final SettingsManager settingsManager;
    private final SchedulerWrapper scheduler;

    public static class TradeRequest {
        public final UUID requesterUuid;
        public final String requesterName;
        public final long timestamp;

        public TradeRequest(UUID requesterUuid, String requesterName) {
            this.requesterUuid = requesterUuid;
            this.requesterName = requesterName;
            this.timestamp = System.currentTimeMillis();
        }
    }

    // Target -> TradeRequest
    private final Map<UUID, TradeRequest> pendingRequests = new ConcurrentHashMap<>();
    // Player UUID -> active TradeGUIHolder
    private final Map<UUID, TradeGUIHolder> activeTradeSessions = new ConcurrentHashMap<>();

    public TradeManager(SettingsManager settingsManager, SchedulerWrapper scheduler) {
        this.settingsManager = settingsManager;
        this.scheduler = scheduler;
    }

    public void requestTrade(Player sender, Player target) {
        int expireSec = settingsManager != null ? settingsManager.getInt("requests.trade-expire-seconds", 60) : 60;
        TradeRequest existing = pendingRequests.get(target.getUniqueId());
        if (existing != null && (System.currentTimeMillis() - existing.timestamp) < (expireSec * 1000L)) {
            sender.sendMessage(TextUtil.format("<red>Player already has a pending trade request.</red>"));
            return;
        }

        pendingRequests.put(target.getUniqueId(), new TradeRequest(sender.getUniqueId(), sender.getName()));
        sender.sendMessage(TextUtil.format("<green>Sent trade request to " + target.getName() + " (expires in " + expireSec + "s).</green>"));

        Component tradeMsg = Component.text("🤝 ")
                .color(NamedTextColor.GOLD)
                .append(Component.text(sender.getName()).color(NamedTextColor.YELLOW))
                .append(Component.text(" requested to trade with you (Expires in " + expireSec + "s). ").color(NamedTextColor.GOLD))
                .append(Component.text("[ACCEPT]")
                        .color(NamedTextColor.GREEN)
                        .decorate(TextDecoration.BOLD)
                        .clickEvent(ClickEvent.runCommand("/trade accept"))
                        .hoverEvent(HoverEvent.showText(Component.text("Click to accept trade request from " + sender.getName()).color(NamedTextColor.GREEN))))
                .append(Component.text("  "))
                .append(Component.text("[DENY]")
                        .color(NamedTextColor.RED)
                        .decorate(TextDecoration.BOLD)
                        .clickEvent(ClickEvent.runCommand("/trade deny"))
                        .hoverEvent(HoverEvent.showText(Component.text("Click to deny trade request from " + sender.getName()).color(NamedTextColor.RED))));

        target.sendMessage(tradeMsg);

        scheduler.runLater(target, () -> {
            TradeRequest req = pendingRequests.get(target.getUniqueId());
            if (req != null && req.requesterUuid.equals(sender.getUniqueId())) {
                pendingRequests.remove(target.getUniqueId());
                if (sender.isOnline()) {
                    sender.sendMessage(TextUtil.format("<yellow>Your trade request to " + target.getName() + " has expired.</yellow>"));
                }
            }
        }, expireSec * 20L);
    }

    public void acceptTrade(Player target) {
        TradeRequest req = pendingRequests.remove(target.getUniqueId());
        int expireSec = settingsManager != null ? settingsManager.getInt("requests.tpa-expire-seconds", 60) : 60;
        if (req == null || (System.currentTimeMillis() - req.timestamp) > (expireSec * 1000L)) {
            target.sendMessage(TextUtil.format("<red>You have no pending or unexpired trade requests.</red>"));
            return;
        }
        Player sender = Bukkit.getPlayer(req.requesterUuid);
        if (sender == null || !sender.isOnline()) {
            target.sendMessage(TextUtil.format("<red>Sender is no longer online.</red>"));
            return;
        }

        openTradeSession(sender, target);
    }

    public void denyTrade(Player target) {
        TradeRequest req = pendingRequests.remove(target.getUniqueId());
        if (req == null) {
            target.sendMessage(TextUtil.format("<red>You have no pending trade requests.</red>"));
            return;
        }
        Player sender = Bukkit.getPlayer(req.requesterUuid);
        if (sender != null && sender.isOnline()) {
            sender.sendMessage(TextUtil.format("<red>" + target.getName() + " denied your trade request.</red>"));
        }
        target.sendMessage(TextUtil.format("<yellow>Denied trade request.</yellow>"));
    }

    public void openTradeSession(Player p1, Player p2) {
        TradeGUIHolder holder = new TradeGUIHolder(p1.getUniqueId(), p2.getUniqueId());
        Inventory inv = Bukkit.createInventory(holder, 27, TextUtil.format("<gold>🤝 Trade: " + p1.getName() + " ↔ " + p2.getName() + "</gold>"));

        updateTradeGUIControls(inv, holder, p1, p2);

        activeTradeSessions.put(p1.getUniqueId(), holder);
        activeTradeSessions.put(p2.getUniqueId(), holder);

        p1.openInventory(inv);
        p2.openInventory(inv);
    }

    public void updateTradeGUIControls(Inventory inv, TradeGUIHolder holder, Player p1, Player p2) {
        // Divider & Controls row (slots 9-17)
        inv.setItem(9, new GUIItemBuilder(Material.PLAYER_HEAD).name("<yellow><b>" + p1.getName() + "'s Offer</b></yellow>").build());
        inv.setItem(10, holder.isP1Ready()
                ? new GUIItemBuilder(Material.LIME_WOOL).name("<gradient:#00FF87:#60EFFF><b>✔ " + p1.getName() + " READY</b></gradient>").lore("<gray>Click to unready</gray>").build()
                : new GUIItemBuilder(Material.RED_WOOL).name("<gradient:#FF416C:#FF4B2B><b>✖ " + p1.getName() + " NOT READY</b></gradient>").lore("<gray>Click when ready</gray>").build());

        inv.setItem(11, new GUIItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name("<gray> </gray>").build());
        inv.setItem(12, new GUIItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name("<gray> </gray>").build());

        inv.setItem(13, new GUIItemBuilder(Material.BARRIER).name("<red><b>✖ CANCEL TRADE</b></red>").lore("<gray>Click to abort trade and reclaim items</gray>").build());

        inv.setItem(14, new GUIItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name("<gray> </gray>").build());
        inv.setItem(15, new GUIItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name("<gray> </gray>").build());

        inv.setItem(16, holder.isP2Ready()
                ? new GUIItemBuilder(Material.LIME_WOOL).name("<gradient:#00FF87:#60EFFF><b>✔ " + p2.getName() + " READY</b></gradient>").lore("<gray>Click to unready</gray>").build()
                : new GUIItemBuilder(Material.RED_WOOL).name("<gradient:#FF416C:#FF4B2B><b>✖ " + p2.getName() + " NOT READY</b></gradient>").lore("<gray>Click when ready</gray>").build());
        inv.setItem(17, new GUIItemBuilder(Material.PLAYER_HEAD).name("<yellow><b>" + p2.getName() + "'s Offer</b></yellow>").build());
    }

    public Map<UUID, TradeGUIHolder> getActiveTradeSessions() {
        return activeTradeSessions;
    }

    public SchedulerWrapper getScheduler() {
        return scheduler;
    }
}
