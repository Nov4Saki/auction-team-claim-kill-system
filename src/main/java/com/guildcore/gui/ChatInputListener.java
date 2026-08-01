package com.guildcore.gui;

import com.guildcore.config.SettingsManager;
import com.guildcore.scheduler.SchedulerWrapper;
import com.guildcore.util.TextUtil;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class ChatInputListener implements Listener {
    private static final Map<UUID, PendingInput> pendingInputs = new ConcurrentHashMap<>();
    private final SettingsManager settingsManager;
    private final SchedulerWrapper scheduler;

    public record PendingInput(String key, boolean isString, Consumer<Player> callback) {}

    public ChatInputListener(SettingsManager settingsManager, SchedulerWrapper scheduler) {
        this.settingsManager = settingsManager;
        this.scheduler = scheduler;
    }

    public static void requestInput(Player player, String key, Consumer<Player> callback) {
        boolean isString = key.endsWith("_name") || key.endsWith("_tag") || key.endsWith("_desc") || key.endsWith("_title") || key.endsWith("_material") || key.endsWith("_mode") || key.endsWith("_format");
        requestInputInternal(player, key, isString, callback);
    }

    public static void requestStringInput(Player player, String key, Consumer<Player> callback) {
        requestInputInternal(player, key, true, callback);
    }

    public static String formatFriendlyKeyName(String key) {
        if (key == null) return "Setting";
        if (key.startsWith("teams.max_claims_level_")) {
            String lvl = key.substring("teams.max_claims_level_".length());
            return "Guild Level " + lvl + " Claim Limit";
        }
        switch (key) {
            case "claims.map.cost_xp_levels": return "Base Claim XP Cost";
            case "claims.map.cost_coins": return "Base Claim Gold Cost";
            case "claims.cost.multiplier": return "Price Scaling Multiplier";
            case "claims.map.cost_item_material": return "Required Claim Item Material";
            case "claims.map.cost_item_amount": return "Required Claim Item Quantity";
            case "teams.creation_cost": return "Guild Creation Fee";
            case "teams.base_max_members": return "Base Member Capacity";
            case "teams.max_guild_level": return "Maximum Guild Level";
            case "rtp.cooldown_sec": return "RTP Cooldown Timer (Seconds)";
            case "rtp.warmup_sec": return "RTP Warmup Standstill Timer (Seconds)";
            case "rtp.range.min_x": return "RTP Minimum X Bound";
            case "rtp.range.max_x": return "RTP Maximum X Bound";
            case "rtp.range.min_z": return "RTP Minimum Z Bound";
            case "rtp.range.max_z": return "RTP Maximum Z Bound";
            default:
                String clean = key.contains(".") ? key.substring(key.lastIndexOf('.') + 1) : key;
                clean = clean.replace('_', ' ');
                StringBuilder sb = new StringBuilder();
                for (String word : clean.split(" ")) {
                    if (!word.isEmpty()) {
                        sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(" ");
                    }
                }
                return sb.toString().trim();
        }
    }

    private static void requestInputInternal(Player player, String key, boolean isString, Consumer<Player> callback) {
        pendingInputs.put(player.getUniqueId(), new PendingInput(key, isString, callback));
        player.closeInventory();
        String friendlyName = formatFriendlyKeyName(key);
        if (isString) {
            player.sendMessage(TextUtil.format("<yellow>⌨ Enter text input for <gold>" + friendlyName + "</gold> in chat (or type <red>cancel</red>):</yellow>"));
        } else {
            player.sendMessage(TextUtil.format("<yellow>⌨ Enter a numerical value for <gold>" + friendlyName + "</gold> in chat (or type <red>cancel</red>):</yellow>"));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onPaperChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        PendingInput pending = pendingInputs.remove(player.getUniqueId());
        if (pending == null) return;

        event.setCancelled(true);
        String raw = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        processInput(player, raw, pending);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onLegacyChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        PendingInput pending = pendingInputs.remove(player.getUniqueId());
        if (pending == null) return;

        event.setCancelled(true);
        String raw = event.getMessage().trim();
        processInput(player, raw, pending);
    }

    private void processInput(Player player, String raw, PendingInput pending) {
        String friendlyName = formatFriendlyKeyName(pending.key());
        if (raw.equalsIgnoreCase("cancel")) {
            player.sendMessage(TextUtil.format("<red>Input cancelled.</red>"));
            scheduler.runSync(player, () -> pending.callback().accept(player));
            return;
        }

        if (pending.isString()) {
            settingsManager.set(pending.key(), raw);
            player.sendMessage(TextUtil.format("<green>✔ Successfully set <gold>" + friendlyName + "</gold> to '<white>" + raw + "</white>'!</green>"));
            scheduler.runSync(player, () -> pending.callback().accept(player));
            return;
        }

        try {
            if (pending.key().contains("multiplier") || pending.key().contains("scale")) {
                double doubleVal = Double.parseDouble(raw);
                settingsManager.set(pending.key(), String.valueOf(doubleVal));
                player.sendMessage(TextUtil.format("<green>✔ Successfully set <gold>" + friendlyName + "</gold> to <white>" + doubleVal + "</white>!</green>"));
                scheduler.runSync(player, () -> pending.callback().accept(player));
                return;
            }
            long val = Long.parseLong(raw);
            boolean allowNegative = pending.key().contains("min_") || pending.key().endsWith("_x") || pending.key().endsWith("_z") || pending.key().contains("bound");
            if (!allowNegative && val < 0) {
                player.sendMessage(TextUtil.format("<red>Number must be non-negative.</red>"));
                scheduler.runSync(player, () -> pending.callback().accept(player));
                return;
            }

            settingsManager.set(pending.key(), String.valueOf(val));
            player.sendMessage(TextUtil.format("<green>✔ Successfully set <gold>" + friendlyName + "</gold> to <white>" + val + "</white>!</green>"));
            scheduler.runSync(player, () -> pending.callback().accept(player));
        } catch (NumberFormatException e) {
            player.sendMessage(TextUtil.format("<red>Invalid number entered. Input cancelled.</red>"));
            scheduler.runSync(player, () -> pending.callback().accept(player));
        }
    }
}
