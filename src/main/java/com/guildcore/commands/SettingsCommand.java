package com.guildcore.commands;

import com.guildcore.gui.GUIManager;
import com.guildcore.util.TextUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SettingsCommand implements TabExecutor {
    private final GUIManager guiManager;

    public SettingsCommand(GUIManager guiManager) {
        this.guiManager = guiManager;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            for (String sub : Arrays.asList("economy", "kills", "claims", "teams", "combat", "scoreboard", "auction", "debug")) {
                if (sub.startsWith(args[0].toLowerCase())) completions.add(sub);
            }
        }
        return completions;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("guildcore.admin")) {
            sender.sendMessage(TextUtil.format("<red>No permission.</red>"));
            return true;
        }

        if (sender instanceof Player player) {
            guiManager.openAdminSettings(player);
        } else {
            sender.sendMessage("In-game players only.");
        }
        return true;
    }
}
