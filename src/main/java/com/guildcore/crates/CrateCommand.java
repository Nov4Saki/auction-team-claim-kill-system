package com.guildcore.crates;

import com.guildcore.gui.GUIItemBuilder;
import com.guildcore.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CrateCommand implements TabExecutor {
    private final CrateManager crateManager;

    public CrateCommand(CrateManager crateManager) {
        this.crateManager = crateManager;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            for (String sub : Arrays.asList("open", "givekey", "create", "edit", "list")) {
                if (sub.startsWith(args[0].toLowerCase())) completions.add(sub);
            }
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("open") || sub.equals("edit") || sub.equals("givekey")) {
                for (Crate c : crateManager.getAllCrates()) {
                    if (c.getName().startsWith(args[1].toLowerCase())) completions.add(c.getName());
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

        if (args.length == 0) {
            // Open Crates Overview GUI
            Inventory inv = Bukkit.createInventory(null, 27, TextUtil.format("<gold>🎁 Server Choice Crates</gold>"));
            int slot = 10;
            for (Crate crate : crateManager.getAllCrates()) {
                inv.setItem(slot++, new GUIItemBuilder(Material.CHEST).name("<gold>" + crate.getDisplayName() + "</gold>")
                        .lore(List.of("<gray>Required Key: " + crate.getKeyItem().getType() + "</gray>", "<yellow>Type /crate open " + crate.getName() + " to inspect & open!</yellow>")).build());
            }
            player.openInventory(inv);
            return true;
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("open")) {
            if (args.length < 2) {
                player.sendMessage(TextUtil.format("<red>Usage: /crate open <crate_name></red>"));
                return true;
            }
            Crate crate = crateManager.getCrate(args[1]);
            if (crate == null) {
                player.sendMessage(TextUtil.format("<red>Crate '" + args[1] + "' not found.</red>"));
                return true;
            }
            if (!crateManager.hasKey(player, crate)) {
                player.sendMessage(TextUtil.format("<red>You need a matching key item in your inventory to open this crate!</red>"));
                return true;
            }
            // Open crate choice menu FIRST; key is consumed only when item is chosen!
            crateManager.openCrateChoiceMenu(player, crate);
            return true;
        }

        if (sub.equals("create")) {
            if (!player.hasPermission("guildcore.admin")) {
                player.sendMessage(TextUtil.format("<red>No permission.</red>"));
                return true;
            }
            if (args.length < 2) {
                player.sendMessage(TextUtil.format("<red>Usage: /crate create <name></red>"));
                return true;
            }
            ItemStack inHand = player.getInventory().getItemInMainHand();
            if (inHand == null || inHand.getType().isAir()) {
                player.sendMessage(TextUtil.format("<red>Hold the item you want to use as the Key in your main hand!</red>"));
                return true;
            }
            ItemStack keyItem = inHand.clone();
            keyItem.setAmount(1);
            crateManager.createCrate(args[1], args[1], keyItem);
            player.sendMessage(TextUtil.format("<green>Created crate '" + args[1] + "' using item in hand as the key!</green>"));
            return true;
        }

        if (sub.equals("edit")) {
            if (!player.hasPermission("guildcore.admin")) {
                player.sendMessage(TextUtil.format("<red>No permission.</red>"));
                return true;
            }
            if (args.length < 2) {
                player.sendMessage(TextUtil.format("<red>Usage: /crate edit <crate_name></red>"));
                return true;
            }
            Crate crate = crateManager.getCrate(args[1]);
            if (crate == null) {
                player.sendMessage(TextUtil.format("<red>Crate '" + args[1] + "' not found.</red>"));
                return true;
            }
            crateManager.openCrateAdminEditor(player, crate);
            return true;
        }

        if (sub.equals("givekey")) {
            if (!player.hasPermission("guildcore.admin")) {
                player.sendMessage(TextUtil.format("<red>No permission.</red>"));
                return true;
            }
            if (args.length < 3) {
                player.sendMessage(TextUtil.format("<red>Usage: /crate givekey <player> <crate_name> [amount]</red>"));
                return true;
            }
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null || !target.isOnline()) {
                player.sendMessage(TextUtil.format("<red>Player not found.</red>"));
                return true;
            }
            int amount = args.length >= 4 ? Integer.parseInt(args[3]) : 1;
            crateManager.giveKey(target, args[2], amount);
            player.sendMessage(TextUtil.format("<green>Gave " + amount + "x key for crate '" + args[2] + "' to " + target.getName() + "!</green>"));
            return true;
        }

        return true;
    }
}
