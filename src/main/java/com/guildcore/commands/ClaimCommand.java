// FILE: src/main/java/com/guildcore/commands/ClaimCommand.java
package com.guildcore.commands;

import com.guildcore.claims.ClaimChestManager;
import com.guildcore.claims.ClaimManager;
import com.guildcore.claims.ClaimVisualizer;
import com.guildcore.gui.GUIManager;
import com.guildcore.util.TextUtil;
import org.bukkit.Chunk;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ClaimCommand implements TabExecutor {
    private final ClaimManager claimManager;
    private final ClaimVisualizer visualizer;
    private final GUIManager guiManager;
    private final ClaimChestManager claimChestManager;

    public ClaimCommand(ClaimManager claimManager, ClaimVisualizer visualizer,
                        GUIManager guiManager, ClaimChestManager claimChestManager) {
        this.claimManager = claimManager;
        this.visualizer = visualizer;
        this.guiManager = guiManager;
        this.claimChestManager = claimChestManager;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        String cmd = alias.toLowerCase();
        if (cmd.contains("claim") && !cmd.contains("unclaim")) {
            if (args.length == 1) {
                for (String sub : Arrays.asList("map", "border", "info")) {
                    if (sub.startsWith(args[0].toLowerCase())) completions.add(sub);
                }
            }
        }
        return completions;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }

        Chunk chunk = player.getLocation().getChunk();
        String cmd = label.toLowerCase();

        // /claim border - show particles
        if (args.length >= 1 && (args[0].equalsIgnoreCase("border") ||
                args[0].equalsIgnoreCase("visual") || args[0].equalsIgnoreCase("visualize"))) {
            visualizer.showBorder(player, chunk);
            player.sendMessage(TextUtil.format("<cyan>✨ Displaying claim border particles for chunk (" +
                    chunk.getX() + ", " + chunk.getZ() + ").</cyan>"));
            return true;
        }

        // /claim map - opens territory map (view only)
        if (args.length >= 1 && args[0].equalsIgnoreCase("map")) {
            guiManager.openTeamMapGUI(player);
            return true;
        }

        // /claim info - shows claim chest cooldown info and gives chest
        if (args.length >= 1 && args[0].equalsIgnoreCase("info")) {
            int cooldownSec = com.guildcore.GuildCorePlugin.getInstance() != null ?
                    com.guildcore.GuildCorePlugin.getInstance().getSettingsManager()
                            .getInt("claims.chest_cooldown_seconds", 600) : 600;

            player.sendMessage(TextUtil.format("<gold>=== Claim Chest Info ===</gold>"));
            player.sendMessage(TextUtil.format("<gray>• The Claim Chest establishes your Guild territory.</gray>"));
            player.sendMessage(TextUtil.format("<gray>• Place it on any solid block to claim the chunk.</gray>"));
            player.sendMessage(TextUtil.format("<gray>• Right-click the placed chest to manage your Guild.</gray>"));
            player.sendMessage(TextUtil.format("<gray>• Cooldown between chest requests: " + cooldownSec + " seconds</gray>"));
            player.sendMessage(TextUtil.format("<yellow>Use /claim to receive a Claim Chest now.</yellow>"));
            return true;
        }

        // /unclaim - removed, players must use the claim chest GUI or chest management
        if (cmd.contains("unclaim")) {
            player.sendMessage(TextUtil.format("<yellow>Use /claim map to view territory, or right-click your Claim Chest to manage claims.</yellow>"));
            return true;
        }

        // Default /claim command - gives the claim chest item
        claimChestManager.giveClaimChest(player);
        return true;
    }
}