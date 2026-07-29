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
        boolean isString = key.endsWith("_name") || key.endsWith("_tag") || key.endsWith("_desc") || key.endsWith("_title");
        requestInputInternal(player, key, isString, callback);
    }

    public static void requestStringInput(Player player, String key, Consumer<Player> callback) {
        requestInputInternal(player, key, true, callback);
    }

    private static void requestInputInternal(Player player, String key, boolean isString, Consumer<Player> callback) {
        pendingInputs.put(player.getUniqueId(), new PendingInput(key, isString, callback));
        player.closeInventory();
        if (isString) {
            player.sendMessage(TextUtil.format("<yellow>⌨ Type text input in chat for <gold>" + key + "</gold> (or type <red>cancel</red>):</yellow>"));
        } else {
            player.sendMessage(TextUtil.format("<yellow>⌨ Type a numerical value in chat for <gold>" + key + "</gold> (or type <red>cancel</red>):</yellow>"));
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
        if (raw.equalsIgnoreCase("cancel")) {
            player.sendMessage(TextUtil.format("<red>Input cancelled.</red>"));
            scheduler.runSync(player, () -> pending.callback().accept(player));
            return;
        }

        if (pending.isString()) {
            settingsManager.set(pending.key(), raw);
            player.sendMessage(TextUtil.format("<green>✔ Successfully set <gold>" + pending.key() + "</gold> to '<white>" + raw + "</white>'!</green>"));
            scheduler.runSync(player, () -> pending.callback().accept(player));
            return;
        }

        try {
            long val = Long.parseLong(raw);
            if (val < 0) {
                player.sendMessage(TextUtil.format("<red>Number must be non-negative.</red>"));
                scheduler.runSync(player, () -> pending.callback().accept(player));
                return;
            }

            settingsManager.set(pending.key(), String.valueOf(val));
            player.sendMessage(TextUtil.format("<green>✔ Successfully set <gold>" + pending.key() + "</gold> to <white>" + val + "</white>!</green>"));
            scheduler.runSync(player, () -> pending.callback().accept(player));
        } catch (NumberFormatException e) {
            player.sendMessage(TextUtil.format("<red>Invalid number entered. Input cancelled.</red>"));
            scheduler.runSync(player, () -> pending.callback().accept(player));
        }
    }
}
