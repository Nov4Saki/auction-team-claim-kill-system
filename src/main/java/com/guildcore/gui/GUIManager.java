package com.guildcore.gui;

import com.guildcore.auction.AuctionItem;
import com.guildcore.auction.AuctionManager;
import com.guildcore.claims.ClaimInfo;
import com.guildcore.claims.ClaimManager;
import com.guildcore.config.SettingsManager;
import com.guildcore.debug.DebugFlag;
import com.guildcore.debug.DebugManager;
import com.guildcore.gui.holders.*;
import com.guildcore.stats.StatsManager;
import com.guildcore.teams.Team;
import com.guildcore.teams.TeamManager;
import com.guildcore.teams.TeamPermissionManager;
import com.guildcore.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class GUIManager {
    private final SettingsManager settingsManager;
    private final TeamManager teamManager;
    private final ClaimManager claimManager;
    private final AuctionManager auctionManager;
    private final StatsManager statsManager;
    private final TeamPermissionManager permissionManager;

    public GUIManager(SettingsManager settingsManager, TeamManager teamManager, ClaimManager claimManager, AuctionManager auctionManager, StatsManager statsManager, TeamPermissionManager permissionManager) {
        this.settingsManager = settingsManager;
        this.teamManager = teamManager;
        this.claimManager = claimManager;
        this.auctionManager = auctionManager;
        this.statsManager = statsManager;
        this.permissionManager = permissionManager;
    }

    public void openAdminSettings(Player player) {
        Inventory inv = Bukkit.createInventory(new SettingsGUIHolder(), 54, TextUtil.format("<gold>⚙ GuildCore Admin Panel</gold>"));

        inv.setItem(10, new GUIItemBuilder(Material.GOLD_INGOT).name("<yellow>💰 Economy Settings</yellow>").lore(List.of("<gray>Click to edit starting balance, kill rewards, tax%</gray>")).build());
        inv.setItem(11, new GUIItemBuilder(Material.DIAMOND_SWORD).name("<red>⚔ Kill Counter Settings</red>").lore(List.of("<gray>Click to edit mob and PvP kill rewards</gray>")).build());
        inv.setItem(12, new GUIItemBuilder(Material.GRASS_BLOCK).name("<green>🏠 Claim Settings</green>").lore(List.of("<gray>Click to edit claim block earn rate and flags</gray>")).build());
        inv.setItem(13, new GUIItemBuilder(Material.SHIELD).name("<blue>🏰 Team Settings</blue>").lore(List.of("<gray>Click to edit team creation cost and base caps</gray>")).build());
        inv.setItem(14, new GUIItemBuilder(Material.GOLDEN_APPLE).name("<dark_purple>⚔ Combat & Items</dark_purple>").lore(List.of("<gray>Click to edit combat tag duration and item rules</gray>")).build());
        inv.setItem(15, new GUIItemBuilder(Material.NAME_TAG).name("<aqua>📊 Scoreboard Settings</aqua>").lore(List.of("<gray>Click to edit scoreboard refresh rate and titles</gray>")).build());
        inv.setItem(16, new GUIItemBuilder(Material.CHEST).name("<gold>🏪 Auction Settings</gold>").lore(List.of("<gray>Click to edit listing fees and max listings</gray>")).build());
        inv.setItem(22, new GUIItemBuilder(Material.LEVER).name("<red>🐞 Debug Panel (18 Flags)</red>").lore(List.of("<gray>Click to toggle 18 surgical debug flags</gray>")).build());

        player.openInventory(inv);
        DebugManager.log(DebugFlag.GUI_CLICKS, "Opened admin settings GUI for " + player.getName());
    }

    public void openAdminEconomySettings(Player player) {
        Inventory inv = Bukkit.createInventory(new SettingsGUIHolder(), 27, TextUtil.format("<gold>💰 Economy Config</gold>"));
        long startingBal = settingsManager.getInt("economy.starting_balance", 100);
        long killReward = settingsManager.getInt("economy.pvp_kill_reward", 50);
        long tax = settingsManager.getInt("economy.sales_tax_percent", 5);

        inv.setItem(11, new GUIItemBuilder(Material.GOLD_INGOT).name("<yellow>Starting Balance: $" + startingBal + "</yellow>").lore(List.of("<gray>Click to cycle $100 / $500 / $1000</gray>")).build());
        inv.setItem(13, new GUIItemBuilder(Material.DIAMOND_SWORD).name("<red>PvP Kill Reward: $" + killReward + "</red>").lore(List.of("<gray>Click to cycle $10 / $50 / $100</gray>")).build());
        inv.setItem(15, new GUIItemBuilder(Material.HOPPER).name("<gold>Sales Tax: " + tax + "%</gold>").lore(List.of("<gray>Click to cycle 0% / 5% / 10%</gray>")).build());
        inv.setItem(26, new GUIItemBuilder(Material.BARRIER).name("<red>◀ Back to Admin Panel</red>").build());

        player.openInventory(inv);
    }

    public void openAdminCombatSettings(Player player) {
        Inventory inv = Bukkit.createInventory(new SettingsGUIHolder(), 27, TextUtil.format("<dark_purple>⚔ Combat Config</dark_purple>"));
        int duration = settingsManager.getInt("combat.tag_duration", 15);

        inv.setItem(11, new GUIItemBuilder(Material.CLOCK).name("<yellow>Combat Tag Duration: " + duration + "s</yellow>").lore(List.of("<gray>Click to cycle 10s / 15s / 30s</gray>")).build());
        inv.setItem(13, new GUIItemBuilder(Material.ENDER_PEARL).name("<purple>Ender Pearl Combat Rule: DISABLED IN COMBAT</purple>").build());
        inv.setItem(15, new GUIItemBuilder(Material.ELYTRA).name("<aqua>Elytra Combat Rule: DISABLED IN COMBAT</aqua>").build());
        inv.setItem(26, new GUIItemBuilder(Material.BARRIER).name("<red>◀ Back to Admin Panel</red>").build());

        player.openInventory(inv);
    }

    public void openAdminDebugPanel(Player player) {
        Inventory inv = Bukkit.createInventory(new SettingsGUIHolder(), 27, TextUtil.format("<red>🐞 Debug Flags Panel (18 Flags)</red>"));

        DebugFlag[] flags = DebugFlag.values();
        for (int i = 0; i < Math.min(flags.length, 18); i++) {
            DebugFlag flag = flags[i];
            boolean enabled = DebugManager.isEnabled(flag);
            Material mat = enabled ? Material.LIME_WOOL : Material.RED_WOOL;
            String status = enabled ? "<green>ENABLED</green>" : "<red>DISABLED</red>";
            inv.setItem(i, new GUIItemBuilder(mat).name("<yellow>" + flag.name() + "</yellow>").lore(List.of("<gray>Status: " + status + "</gray>", "<yellow>Click to toggle</yellow>")).build());
        }
        inv.setItem(26, new GUIItemBuilder(Material.BARRIER).name("<red>◀ Back to Admin Panel</red>").build());

        player.openInventory(inv);
    }

    public void openAuctionHouse(Player player) {
        openAuctionHouse(player, 1, "ALL", "");
    }

    public void openAuctionHouse(Player player, int page, String category, String searchQuery) {
        Inventory inv = Bukkit.createInventory(new AuctionGUIHolder(page, category, searchQuery), 54, TextUtil.format("<gold>🏪 Auction House (Page " + page + ")</gold>"));

        // Row 1: Category Filters & Buttons (Slots 0-8)
        inv.setItem(0, new GUIItemBuilder(Material.NETHER_STAR).name("<yellow>⭐ ALL</yellow>").build());
        inv.setItem(1, new GUIItemBuilder(Material.DIAMOND_SWORD).name("<red>🗡 Weapons</red>").build());
        inv.setItem(2, new GUIItemBuilder(Material.DIAMOND_CHESTPLATE).name("<blue>🛡 Armor</blue>").build());
        inv.setItem(3, new GUIItemBuilder(Material.DIAMOND_PICKAXE).name("<green>⛏ Tools</green>").build());
        inv.setItem(4, new GUIItemBuilder(Material.BRICKS).name("<gold>🧱 Blocks</gold>").build());
        inv.setItem(5, new GUIItemBuilder(Material.POTION).name("<purple>🧪 Potions</purple>").build());
        inv.setItem(6, new GUIItemBuilder(Material.SHULKER_BOX).name("<aqua>📦 Shulkers</aqua>").build());
        inv.setItem(7, new GUIItemBuilder(Material.COMPASS).name("<yellow>🔍 Search Filter</yellow>").lore(List.of("<gray>Click to filter by search query</gray>")).build());
        inv.setItem(8, new GUIItemBuilder(Material.HOPPER).name("<gold>📊 Sort Order</gold>").lore(List.of("<gray>Click to cycle sorting mode</gray>")).build());

        List<AuctionItem> active = auctionManager.getActiveListings();
        List<AuctionItem> filtered = new ArrayList<>();
        for (AuctionItem item : active) {
            if (!category.equalsIgnoreCase("ALL") && !item.getCategory().equalsIgnoreCase(category)) continue;
            if (!searchQuery.isEmpty()) {
                String name = item.getItem().getType().name().toLowerCase();
                if (!name.contains(searchQuery.toLowerCase())) continue;
            }
            filtered.add(item);
        }

        int pageSize = 36;
        int startIndex = (page - 1) * pageSize;
        int endIndex = Math.min(startIndex + pageSize, filtered.size());

        int slot = 9;
        for (int i = startIndex; i < endIndex; i++) {
            AuctionItem item = filtered.get(i);
            ItemStack display = item.getItem().clone();

            List<String> lore = new ArrayList<>();
            lore.add("<gray>Seller: <white>" + item.getSellerUuid().toString().substring(0, 8) + "...</white></gray>");
            lore.add("<gray>Price: <green>$" + item.getPrice() + "</green></gray>");
            lore.add("<yellow>Click to Purchase</yellow>");
            if (display.getType().name().contains("SHULKER_BOX")) {
                lore.add("<aqua>Right-Click to Preview Contents</aqua>");
            }

            display = new GUIItemBuilder(display.getType()).lore(lore).build();
            inv.setItem(slot++, display);
        }

        // Row 6: Control & Stash Buttons (Slots 45-53)
        inv.setItem(45, new GUIItemBuilder(Material.PLAYER_HEAD).name("<yellow>👤 My Listings</yellow>").lore(List.of("<gray>View your active sales</gray>")).build());
        if (page > 1) {
            inv.setItem(48, new GUIItemBuilder(Material.ARROW).name("<yellow>◀ Previous Page (" + (page - 1) + ")</yellow>").build());
        }
        inv.setItem(49, new GUIItemBuilder(Material.ENDER_CHEST).name("<gold>📦 Expired / Purchased Stash</gold>").lore(List.of("<gray>View & reclaim items from inventory overflow</gray>")).build());
        if (endIndex < filtered.size()) {
            inv.setItem(50, new GUIItemBuilder(Material.ARROW).name("<yellow>Next Page (" + (page + 1) + ") ▶</yellow>").build());
        }
        inv.setItem(53, new GUIItemBuilder(Material.EMERALD).name("<green>➕ Sell Item</green>").lore(List.of("<gray>Hold an item & type /gcah sell <price></gray>")).build());

        player.openInventory(inv);
        DebugManager.log(DebugFlag.GUI_CLICKS, "Opened auction house GUI page " + page + " for " + player.getName());
    }

    public void openConfirmPurchase(Player player, AuctionItem item) {
        Inventory inv = Bukkit.createInventory(new AuctionConfirmHolder(item), 27, TextUtil.format("<gold>Confirm Purchase ($" + item.getPrice() + ")</gold>"));

        inv.setItem(11, new GUIItemBuilder(Material.LIME_WOOL).name("<green>✔ CONFIRM PURCHASE ($" + item.getPrice() + ")</green>").build());
        inv.setItem(13, item.getItem().clone());
        inv.setItem(15, new GUIItemBuilder(Material.RED_WOOL).name("<red>✖ CANCEL</red>").build());

        player.openInventory(inv);
    }

    public void openTeamMenu(Player player, Team team) {
        if (team == null) {
            player.sendMessage(TextUtil.format("<red>You are not in a team.</red>"));
            return;
        }

        Inventory inv = Bukkit.createInventory(new TeamGUIHolder(team.getId()), 45, TextUtil.format("<gold>🏰 Team: " + team.getName() + "</gold>"));

        inv.setItem(10, new GUIItemBuilder(Material.BEACON).name("<gold>🏰 " + team.getName() + "</gold>")
                .lore(List.of(
                        "<yellow>Level: <white>" + team.getLevel() + "</white></yellow>",
                        "<yellow>Max Members: <white>" + team.getMaxMembers() + "</white></yellow>",
                        "<yellow>Bank Balance: <green>$" + team.getBankBalance() + "</green></yellow>"
                )).build());

        inv.setItem(12, new GUIItemBuilder(Material.GOLD_BLOCK).name("<yellow>🏦 Team Bank</yellow>").lore(List.of("<gray>Click to view bank & deposit/withdraw</gray>")).build());
        inv.setItem(14, new GUIItemBuilder(Material.CHEST).name("<gold>📦 Team Vault (Virtual Storage)</gold>").lore(List.of("<gray>Click to open shared virtual vault</gray>")).build());
        inv.setItem(16, new GUIItemBuilder(Material.NETHER_STAR).name("<green>⬆ Team Upgrades</green>").lore(List.of("<gray>Click to upgrade member caps and vault pages</gray>")).build());
        inv.setItem(28, new GUIItemBuilder(Material.WRITABLE_BOOK).name("<aqua>📜 Rank Permissions Editor</aqua>").lore(List.of("<gray>Click to edit rank permissions</gray>")).build());
        inv.setItem(30, new GUIItemBuilder(Material.MAP).name("<green>🗺 Team Land Claims</green>").lore(List.of("<gray>Click to view team claimed territory</gray>")).build());
        inv.setItem(32, new GUIItemBuilder(Material.RED_BED).name("<light_purple>🏠 Teleport to Team Home</light_purple>").build());
        inv.setItem(34, new GUIItemBuilder(Material.TNT).name("<red>⚔ Team Raid Hub</red>").lore(List.of("<gray>Click to declare or inspect raids</gray>")).build());

        player.openInventory(inv);
        DebugManager.log(DebugFlag.GUI_CLICKS, "Opened main Team GUI for " + player.getName());
    }

    public void openTeamUpgrades(Player player, Team team) {
        Inventory inv = Bukkit.createInventory(new TeamUpgradesHolder(team.getId()), 27, TextUtil.format("<gold>⬆ Team Upgrades (" + team.getName() + ")</gold>"));

        inv.setItem(11, new GUIItemBuilder(Material.PLAYER_HEAD).name("<yellow>👥 Member Cap (+2 Members)</yellow>")
                .lore(List.of(
                        "<gray>Current Cap: <white>" + team.getMaxMembers() + "</white></gray>",
                        "<gray>Upgrade Cost: <green>$5,000 Team Bank</green></gray>",
                        "<yellow>Click to Upgrade</yellow>"
                )).build());

        inv.setItem(13, new GUIItemBuilder(Material.CHEST).name("<gold>📦 Vault Capacity (+1 Page)</gold>")
                .lore(List.of(
                        "<gray>Upgrade Cost: <green>$10,000 Team Bank</green></gray>",
                        "<yellow>Click to Upgrade</yellow>"
                )).build());

        inv.setItem(15, new GUIItemBuilder(Material.GRASS_BLOCK).name("<green>🗺 Max Claims (+5 Chunks)</green>")
                .lore(List.of(
                        "<gray>Upgrade Cost: <green>$7,500 Team Bank</green></gray>",
                        "<yellow>Click to Upgrade</yellow>"
                )).build());

        player.openInventory(inv);
    }

    public void openClaimFlags(Player player, Chunk chunk) {
        Inventory inv = Bukkit.createInventory(new ClaimFlagsGUIHolder(), 27, TextUtil.format("<green>🏠 Claim Flags</green>"));

        ClaimInfo claim = claimManager.getClaimAt(chunk);
        boolean mobSpawn = claim == null || claim.hasFlag("mob_spawning");
        boolean pvp = claim != null && claim.hasFlag("pvp");

        inv.setItem(10, new GUIItemBuilder(Material.ZOMBIE_HEAD).name("<yellow>Mob Spawning: " + (mobSpawn ? "<green>ON</green>" : "<red>OFF</red>") + "</yellow>").build());
        inv.setItem(11, new GUIItemBuilder(Material.DIAMOND_SWORD).name("<yellow>PvP: " + (pvp ? "<green>ON</green>" : "<red>OFF</red>") + "</yellow>").build());

        player.openInventory(inv);
    }
}
