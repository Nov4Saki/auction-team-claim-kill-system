package com.guildcore;

import org.bukkit.plugin.java.JavaPlugin;
import java.util.logging.Logger;

public final class GuildCore extends JavaPlugin {

    private static GuildCore instance;

    @Override
    public void onEnable() {
        instance = this;
        Logger logger = getLogger();
        logger.info("Initializing GuildCore Hyper-Plugin (Paper 1.21.11)...");

        // Save default config if not present
        saveDefaultConfig();

        logger.info("GuildCore successfully enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("Disabling GuildCore...");
        instance = null;
    }

    public static GuildCore getInstance() {
        return instance;
    }
}
