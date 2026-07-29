package com.guildcore.scheduler;

import com.guildcore.GuildCorePlugin;
import com.guildcore.debug.DebugFlag;
import com.guildcore.debug.DebugManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;
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
        if (isFolia) {
            Bukkit.getGlobalRegionScheduler().run(plugin, task -> runnable.run());
        } else {
            Bukkit.getScheduler().runTask(plugin, runnable);
        }
    }

    public void runSync(Entity entity, Runnable runnable) {
        if (isFolia && entity != null) {
            entity.getScheduler().run(plugin, task -> runnable.run(), null);
        } else {
            Bukkit.getScheduler().runTask(plugin, runnable);
        }
    }

    public void runSync(Location location, Runnable runnable) {
        if (isFolia && location != null) {
            Bukkit.getRegionScheduler().run(plugin, location, task -> runnable.run());
        } else {
            Bukkit.getScheduler().runTask(plugin, runnable);
        }
    }

    public void runAsync(Runnable runnable) {
        if (isFolia) {
            Bukkit.getAsyncScheduler().runNow(plugin, task -> runnable.run());
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable);
        }
    }

    public void runTaskTimer(BooleanSupplier repeatingTask, long delayTicks, long periodTicks) {
        if (isFolia) {
            long delayMs = delayTicks * 50L;
            long periodMs = periodTicks * 50L;
            if (delayMs < 1) delayMs = 1;
            if (periodMs < 1) periodMs = 1;
            Bukkit.getAsyncScheduler().runAtFixedRate(plugin, task -> {
                boolean continueTask = repeatingTask.getAsBoolean();
                if (!continueTask) {
                    task.cancel();
                }
            }, delayMs, periodMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        } else {
            Bukkit.getScheduler().runTaskTimer(plugin, task -> {
                boolean continueTask = repeatingTask.getAsBoolean();
                if (!continueTask) {
                    task.cancel();
                }
            }, delayTicks, periodTicks);
        }
    }
}
