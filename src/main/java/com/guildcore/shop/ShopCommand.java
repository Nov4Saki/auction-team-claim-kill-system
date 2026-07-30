package com.guildcore.shop;

import com.guildcore.gui.GUIManager;
import com.guildcore.util.TextUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ShopCommand implements TabExecutor {
    private final ShopManager shopManager;
    private final GUIManager guiManager;

    public ShopCommand(ShopManager shopManager, GUIManager guiManager) {
        this.shopManager = shopManager;
        this.guiManager = guiManager;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            for (String sub : Arrays.asList("admin", "additem")) {
                if (sub.startsWith(args[0].toLowerCase())) completions.add(sub);
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
            shopManager.openShopMainMenu(player);
            return true;
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("admin")) {
            if (!player.hasPermission("guildcore.admin")) {
                player.sendMessage(TextUtil.format("<red>No permission.</red>"));
                return true;
            }
            guiManager.openAdminShopHub(player);
            return true;
        }

        if (sub.equals("additem")) {
            if (!player.hasPermission("guildcore.admin")) {
                player.sendMessage(TextUtil.format("<red>No permission.</red>"));
                return true;
            }
            if (args.length < 4) {
                player.sendMessage(TextUtil.format("<red>Usage: /shop additem <category_id> <buy_price> <sell_price></red>"));
                return true;
            }

            ItemStack inHand = player.getInventory().getItemInMainHand();
            if (inHand == null || inHand.getType().isAir()) {
                player.sendMessage(TextUtil.format("<red>Hold an item in hand to add to shop!</red>"));
                return true;
            }

            try {
                int catId = Integer.parseInt(args[1]);
                long buy = Long.parseLong(args[2]);
                long sell = Long.parseLong(args[3]);

                ItemStack item = inHand.clone();
                item.setAmount(1);

                int slot = shopManager.getCategoryItems(catId).size();
                shopManager.addShopItem(catId, item, buy, sell, slot);
                player.sendMessage(TextUtil.format("<green>Added " + item.getType() + " to Shop Category " + catId + " (Buy: $" + buy + " | Sell: $" + sell + ")!</green>"));
            } catch (NumberFormatException e) {
                player.sendMessage(TextUtil.format("<red>Invalid category ID or prices.</red>"));
            }
            return true;
        }

        return true;
    }
}

