package com.guildcore.teams;

import com.guildcore.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TeamPermissionManager {
    private final DatabaseManager dbManager;
    private com.guildcore.config.SettingsManager settingsManager;
    // Key format: "teamId:role:permissionNode" -> boolean allowed
    private final Map<String, Boolean> permissionsCache = new ConcurrentHashMap<>();

    public TeamPermissionManager(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    public void setSettingsManager(com.guildcore.config.SettingsManager settingsManager) {
        this.settingsManager = settingsManager;
    }

    public void loadPermissions() {
        dbManager.executeAsync(() -> {
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT team_id, role, permission_node, allowed FROM team_permissions");
                 ResultSet rs = ps.executeQuery()) {

                permissionsCache.clear();
                while (rs.next()) {
                    String key = makeKey(rs.getInt("team_id"), rs.getString("role"), rs.getString("permission_node"));
                    permissionsCache.put(key, rs.getBoolean("allowed"));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private String makeKey(int teamId, String role, String node) {
        return teamId + ":" + role.toUpperCase() + ":" + node.toUpperCase();
    }

    public boolean hasPermission(int teamId, String role, String node) {
        if ("LEADER".equalsIgnoreCase(role)) return true;
        String key = makeKey(teamId, role, node);
        Boolean val = permissionsCache.get(key);
        if (val != null) return val;

        // Dynamic Configurable Defaults via SettingsManager
        if (settingsManager != null) {
            String configKey = "teams.perms." + role.toLowerCase() + "." + node.toLowerCase();
            if (settingsManager.getString(configKey, null) != null) {
                return settingsManager.getBoolean(configKey, false);
            }
        }

        // Fallback default permission matrix
        if ("OFFICER".equalsIgnoreCase(role)) return true;
        if ("MEMBER".equalsIgnoreCase(role)) {
            return node.equalsIgnoreCase("BANK_DEPOSIT") || node.equalsIgnoreCase("VAULT_ACCESS") || node.equalsIgnoreCase("BUILD");
        }
        if ("RECRUIT".equalsIgnoreCase(role)) {
            return node.equalsIgnoreCase("BANK_DEPOSIT");
        }
        return false;
    }

    public void togglePermission(int teamId, String role, String node) {
        String key = makeKey(teamId, role, node);
        boolean current = hasPermission(teamId, role, node);
        boolean next = !current;
        permissionsCache.put(key, next);

        dbManager.executeAsync(() -> {
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT OR REPLACE INTO team_permissions (team_id, role, permission_node, allowed) VALUES (?, ?, ?, ?)")) {
                ps.setInt(1, teamId);
                ps.setString(2, role.toUpperCase());
                ps.setString(3, node.toUpperCase());
                ps.setBoolean(4, next);
                ps.executeUpdate();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}
