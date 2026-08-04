package com.guildcore.claims;

import com.guildcore.scheduler.SchedulerWrapper;
import com.guildcore.util.TextUtil;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;

public class ClaimVisualizer {
    private final ClaimManager claimManager;
    private final SchedulerWrapper scheduler;

    public ClaimVisualizer(ClaimManager claimManager, SchedulerWrapper scheduler) {
        this.claimManager = claimManager;
        this.scheduler = scheduler;
    }

    public void showBorder(Player player, Chunk chunk) {
        if (player == null || chunk == null) return;
        Runnable action = () -> {
            World world = chunk.getWorld();
            int baseX = chunk.getX() * 16;
            int baseZ = chunk.getZ() * 16;
            int y = player.getLocation().getBlockY();

            Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(0, 200, 255), 1.2f);

            for (int x = 0; x < 16; x++) {
                player.spawnParticle(Particle.DUST, new Location(world, baseX + x + 0.5, y + 0.5, baseZ), 1, dust);
                player.spawnParticle(Particle.DUST, new Location(world, baseX + x + 0.5, y + 0.5, baseZ + 16), 1, dust);
            }
            for (int z = 0; z < 16; z++) {
                player.spawnParticle(Particle.DUST, new Location(world, baseX, y + 0.5, baseZ + z + 0.5), 1, dust);
                player.spawnParticle(Particle.DUST, new Location(world, baseX + 16, y + 0.5, baseZ + z + 0.5), 1, dust);
            }
        };

        if (scheduler != null) {
            scheduler.runSync(player, action);
        } else {
            action.run();
        }
    }

    public void sendAsciiMap(Player player) {
        Chunk center = player.getLocation().getChunk();
        World world = center.getWorld();
        int playerCx = center.getX();
        int playerCz = center.getZ();

        player.sendMessage(TextUtil.format("<yellow>=== Claim Map (9x9 Chunks) ===</yellow>"));
        for (int z = -4; z <= 4; z++) {
            StringBuilder line = new StringBuilder("  ");
            for (int x = -4; x <= 4; x++) {
                int cx = playerCx + x;
                int cz = playerCz + z;

                if (x == 0 && z == 0) {
                    line.append("<gold><b>P</b></gold> ");
                    continue;
                }

                ClaimInfo claim = claimManager.getClaimAt(world, cx, cz);
                if (claim == null) {
                    line.append("<gray>-</gray> ");
                } else if (!claim.isTeamClaim() && player.getUniqueId().equals(claim.getOwnerUuid())) {
                    line.append("<green>+</green> ");
                } else {
                    line.append("<red>#</red> ");
                }
            }
            player.sendMessage(TextUtil.format(line.toString()));
        }
        player.sendMessage(TextUtil.format("<gray>Legend: <gold>P</gold>=You <green>+</green>=Yours <red>#</red>=Enemy <gray>-</gray>=Wilderness</gray>"));
    }
}
