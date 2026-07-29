package com.guildcore.config;

import com.guildcore.database.DatabaseManager;
import com.guildcore.debug.DebugFlag;
import com.guildcore.debug.DebugManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SettingsManager {
    private final DatabaseManager dbManager;
    private final Map<String, String> settingsCache = new ConcurrentHashMap<>();

    public SettingsManager(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    public void loadSettings() {
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT key, value FROM settings");
             ResultSet rs = ps.executeQuery()) {

            settingsCache.clear();
            while (rs.next()) {
                settingsCache.put(rs.getString("key"), rs.getString("value"));
            }
            DebugManager.log(DebugFlag.DATABASE_WRITES, "Loaded " + settingsCache.size() + " setting keys from database.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String getString(String key, String defaultValue) {
        return settingsCache.getOrDefault(key, defaultValue);
    }

    public int getInt(String key, int defaultValue) {
        String val = settingsCache.get(key);
        if (val == null) return defaultValue;
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public long getLong(String key, long defaultValue) {
        String val = settingsCache.get(key);
        if (val == null) return defaultValue;
        try {
            return Long.parseLong(val);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        String val = settingsCache.get(key);
        if (val == null) return defaultValue;
        return Boolean.parseBoolean(val);
    }

    public void set(String key, String value) {
        settingsCache.put(key, value);
        dbManager.executeAsync(() -> {
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement("INSERT OR REPLACE INTO settings (key, value) VALUES (?, ?)")) {
                ps.setString(1, key);
                ps.setString(2, value);
                ps.executeUpdate();
                DebugManager.log(DebugFlag.DATABASE_WRITES, "Setting updated: " + key + " = " + value);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}
