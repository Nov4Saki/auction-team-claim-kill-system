package com.guildcore.commands;

import com.guildcore.auction.AuctionManager;
import com.guildcore.gui.GUIManager;
import com.guildcore.util.TextUtil;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AuctionCommand implements TabExecutor {
    private final AuctionManager auctionManager;
    private final GUIManager guiManager;

    public AuctionCommand(AuctionManager auctionManager, GUIManager guiManager) {
        this.auctionManager = auctionManager;
        this.guiManager = guiManager;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            for (String sub : Arrays.asList("sell", "expired", "stash")) {
                if (sub.startsWith(args[0].toLowerCase())) completions.add(sub);
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("sell")) {
            completions.addAll(Arrays.asList("100", "500", "1000", "5000", "10000"));
        }
        return completions;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }

        if (args.length >= 1 && (args[0].equalsIgnoreCase("expired") || args[0].equalsIgnoreCase("stash"))) {
            guiManager.openExpiredStash(player);
            return true;
        }

        if (args.length >= 2 && args[0].equalsIgnoreCase("sell")) {
            ItemStack held = player.getInventory().getItemInMainHand();
            if (held.getType() == Material.AIR) {
                player.sendMessage(TextUtil.format("<red>Hold an item in your hand to sell.</red>"));
                return true;
            }
            try {
                long price = Long.parseLong(args[1]);
                if (price <= 0) {
                    player.sendMessage(TextUtil.format("<red>Price must be positive.</red>"));
                    return true;
                }
                boolean isBid = args.length >= 3 && args[2].equalsIgnoreCase("bid");
                if (auctionManager.listItem(player, held.clone(), price, isBid)) {
                    player.getInventory().setItemInMainHand(null);
                    player.sendMessage(TextUtil.format("<green>Item listed on Auction House for $" + price + "!</green>"));
                }
            } catch (NumberFormatException e) {
                player.sendMessage(TextUtil.format("<red>Invalid price.</red>"));
            }
            return true;
        }

        guiManager.openAuctionHouse(player);
        return true;
    }
}
