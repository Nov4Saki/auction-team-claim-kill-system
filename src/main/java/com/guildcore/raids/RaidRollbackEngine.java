package com.guildcore.raids;

import com.guildcore.debug.DebugFlag;
import com.guildcore.debug.DebugManager;
import com.guildcore.scheduler.SchedulerWrapper;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class RaidRollbackEngine {
    private final SchedulerWrapper scheduler;

    public record RollbackEntry(String world, int x, int y, int z, Material material, String blockDataString) {}

    public RaidRollbackEngine(SchedulerWrapper scheduler) {
        this.scheduler = scheduler;
    }

    public void rollbackBlocks(List<RollbackEntry> queue) {
        if (queue == null || queue.isEmpty()) return;

        AtomicInteger index = new AtomicInteger(0);
        scheduler.runTaskTimer(() -> {
            for (int i = 0; i < 10 && index.get() < queue.size(); i++) {
                RollbackEntry entry = queue.get(index.getAndIncrement());
                World world = Bukkit.getWorld(entry.world());
                if (world != null) {
                    Block block = world.getBlockAt(entry.x(), entry.y(), entry.z());
                    block.setType(entry.material());
                    block.setBlockData(Bukkit.createBlockData(entry.blockDataString()));
                    world.spawnParticle(Particle.HAPPY_VILLAGER, block.getLocation().add(0.5, 0.5, 0.5), 5);
                }
            }
            boolean continueTask = index.get() < queue.size();
            if (!continueTask) {
                DebugManager.log(DebugFlag.RAID_DAMAGE, "Completed TNT rollback of " + queue.size() + " blocks.");
            }
            return continueTask;
        }, 0L, 1L);
    }
}
