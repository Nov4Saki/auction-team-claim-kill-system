package com.guildcore.scheduler;

import com.guildcore.GuildCorePlugin;
import com.guildcore.debug.DebugFlag;
import com.guildcore.debug.DebugManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

import java.util.function.BooleanSupplier;

public class SchedulerWrapper {
    private final GuildCorePlugin plugin;
    private final boolean isFolia;

    public SchedulerWrapper(GuildCorePlugin plugin) {
        this.plugin = plugin;
        boolean foliaCheck = false;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            foliaCheck = true;
        } catch (ClassNotFoundException ignored) {
        }
        this.isFolia = foliaCheck;
        DebugManager.log(DebugFlag.SCHEDULER_ROUTING, "SchedulerWrapper initialized. Environment: " + (isFolia ? "Folia" : "Paper/Spigot"));
    }

    public boolean isFolia() {
        return isFolia;
    }

    public void runSync(Runnable runnable) {
        if (!plugin.isEnabled()) return;
        if (isFolia) {
            Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
                if (plugin.isEnabled()) runnable.run();
            });
        } else {
            Bukkit.getScheduler().runTask(plugin, runnable);
        }
    }

    public void runSync(Entity entity, Runnable runnable) {
        if (!plugin.isEnabled()) return;
        if (isFolia && entity != null) {
            if (!entity.isValid()) return;
            entity.getScheduler().run(plugin, task -> {
                if (plugin.isEnabled()) runnable.run();
            }, null);
        } else {
            Bukkit.getScheduler().runTask(plugin, runnable);
        }
    }

    public void runSync(Location location, Runnable runnable) {
        if (!plugin.isEnabled()) return;
        if (isFolia && location != null) {
            Bukkit.getRegionScheduler().run(plugin, location, task -> {
                if (plugin.isEnabled()) runnable.run();
            });
        } else {
            Bukkit.getScheduler().runTask(plugin, runnable);
        }
    }

    public void runAsync(Runnable runnable) {
        if (!plugin.isEnabled()) return;
        if (isFolia) {
            Bukkit.getAsyncScheduler().runNow(plugin, task -> {
                if (plugin.isEnabled()) runnable.run();
            });
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable);
        }
    }

    public void runTaskTimer(BooleanSupplier repeatingTask, long delayTicks, long periodTicks) {
        if (!plugin.isEnabled()) return;
        if (isFolia) {
            long delayMs = delayTicks * 50L;
            long periodMs = periodTicks * 50L;
            if (delayMs < 1) delayMs = 1;
            if (periodMs < 1) periodMs = 1;
            Bukkit.getAsyncScheduler().runAtFixedRate(plugin, task -> {
                if (!plugin.isEnabled()) {
                    task.cancel();
                    return;
                }
                boolean continueTask = repeatingTask.getAsBoolean();
                if (!continueTask || !plugin.isEnabled()) {
                    task.cancel();
                }
            }, delayMs, periodMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        } else {
            new org.bukkit.scheduler.BukkitRunnable() {
                @Override
                public void run() {
                    if (!plugin.isEnabled()) {
                        cancel();
                        return;
                    }
                    boolean continueTask = repeatingTask.getAsBoolean();
                    if (!continueTask || !plugin.isEnabled()) {
                        cancel();
                    }
                }
            }.runTaskTimer(plugin, delayTicks, periodTicks);
        }
    }

    public void runLater(Entity entity, Runnable runnable, long delayTicks) {
        if (!plugin.isEnabled()) return;
        if (isFolia && entity != null) {
            if (!entity.isValid()) return;
            entity.getScheduler().runDelayed(plugin, task -> {
                if (plugin.isEnabled()) runnable.run();
            }, null, Math.max(1L, delayTicks));
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, runnable, Math.max(1L, delayTicks));
        }
    }

    public void runLater(Location location, Runnable runnable, long delayTicks) {
        if (!plugin.isEnabled()) return;
        if (isFolia && location != null) {
            Bukkit.getRegionScheduler().runDelayed(plugin, location, task -> {
                if (plugin.isEnabled()) runnable.run();
            }, Math.max(1L, delayTicks));
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, runnable, Math.max(1L, delayTicks));
        }
    }
}
