package com.guildcore.debug;

import com.guildcore.GuildCorePlugin;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class DebugManager {
    private static final Set<DebugFlag> activeFlags = ConcurrentHashMap.newKeySet();
    private static boolean debugAll = false;

    public static void toggle(DebugFlag flag) {
        if (activeFlags.contains(flag)) {
            activeFlags.remove(flag);
        } else {
            activeFlags.add(flag);
        }
    }

    public static boolean isEnabled(DebugFlag flag) {
        return debugAll || activeFlags.contains(flag);
    }

    public static void setDebugAll(boolean value) {
        debugAll = value;
    }

    public static boolean isDebugAll() {
        return debugAll;
    }

    public static Set<DebugFlag> getActiveFlags() {
        return activeFlags;
    }

    public static void log(DebugFlag flag, String message) {
        if (debugAll || activeFlags.contains(flag)) {
            GuildCorePlugin instance = GuildCorePlugin.getInstance();
            if (instance != null) {
                instance.getLogger().info("[DEBUG-" + flag.name() + "] " + message);
            }
        }
    }
}
