package com.guildcore.util;

import org.bukkit.Sound;
import org.bukkit.entity.Player;

public class SoundUtil {
    public static void playClick(Player player) {
        if (player != null) player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
    }

    public static void playSuccess(Player player) {
        if (player != null) player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.2f);
    }

    public static void playError(Player player) {
        if (player != null) player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1.0f);
    }

    public static void playRaidHorn(Player player) {
        if (player != null) player.playSound(player.getLocation(), Sound.EVENT_RAID_HORN, 1.0f, 1.0f);
    }
}
