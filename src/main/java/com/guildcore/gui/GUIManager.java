package com.guildcore.gui;

import com.guildcore.auction.AuctionItem;
import com.guildcore.auction.AuctionManager;
import com.guildcore.claims.ClaimInfo;
import com.guildcore.claims.ClaimManager;
import com.guildcore.config.SettingsManager;
import com.guildcore.debug.DebugFlag;
import com.guildcore.debug.DebugManager;
import com.guildcore.gui.holders.*;
import com.guildcore.scoreboard.ScoreboardManager;
import com.guildcore.stats.StatsManager;
import com.guildcore.teams.Team;
import com.guildcore.teams.TeamManager;
import com.guildcore.teams.TeamPermissionManager;
import com.guildcore.util.TextUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

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
        inv.setItem(12, new GUIItemBuilder(Material.GRASS_BLOCK).name("<green>🏠 Claim & World Settings</green>").lore(List.of("<gray>Click to edit claim blocks & world explosion rules</gray>")).build());
        inv.setItem(13, new GUIItemBuilder(Material.SHIELD).name("<blue>🏰 Team Settings</blue>").lore(List.of("<gray>Click to edit team creation cost and base caps</gray>")).build());
        inv.setItem(14, new GUIItemBuilder(Material.GOLDEN_APPLE).name("<dark_purple>⚔ Combat & Item Controls</dark_purple>").lore(List.of("<gray>Click to edit combat tags, command blocks, & item rules</gray>")).build());
        inv.setItem(15, new GUIItemBuilder(Material.NAME_TAG).name("<aqua>📊 Scoreboard Settings</aqua>").lore(List.of("<gray>Click to edit scoreboard refresh rate, titles, & wipe</gray>")).build());
        inv.setItem(16, new GUIItemBuilder(Material.CHEST).name("<gold>🏪 Auction Settings</gold>").lore(List.of("<gray>Click to edit listing fees, max listings, and cooldowns</gray>")).build());
        inv.setItem(22, new GUIItemBuilder(Material.LEVER).name("<red>🐞 Debug Panel (18 Flags)</red>").lore(List.of("<gray>Click to toggle 18 surgical debug flags</gray>")).build());

        player.openInventory(inv);
        DebugManager.log(DebugFlag.GUI_CLICKS, "Opened admin settings GUI for " + player.getName());
    }

    public void openAdminEconomySettings(Player player) {
        Inventory inv = Bukkit.createInventory(new AdminEconomyHolder(), 27, TextUtil.format("<gold>💰 Economy Config</gold>"));
        long startingBal = settingsManager.getInt("economy.starting_balance", 100);
        long killReward = settingsManager.getInt("economy.pvp_kill_reward", 50);
        long tax = settingsManager.getInt("economy.sales_tax_percent", 5);

        inv.setItem(10, new GUIItemBuilder(Material.GOLD_INGOT).name("<yellow>Starting Balance: $" + startingBal + "</yellow>").lore(List.of("<gray>Click to enter custom value in chat or cycle</gray>")).build());
        inv.setItem(12, new GUIItemBuilder(Material.DIAMOND_SWORD).name("<red>PvP Kill Reward: $" + killReward + "</red>").lore(List.of("<gray>Click to enter custom value in chat or cycle</gray>")).build());
        inv.setItem(14, new GUIItemBuilder(Material.HOPPER).name("<gold>Sales Tax: " + tax + "%</gold>").lore(List.of("<gray>Click to enter custom value in chat or cycle</gray>")).build());
        inv.setItem(16, new GUIItemBuilder(Material.EMERALD_BLOCK).name("<green>➕ Admin: Give Self +$1,000 Coins</green>").lore(List.of("<gray>Click to instantly receive $1,000 coins</gray>")).build());
        inv.setItem(26, new GUIItemBuilder(Material.BARRIER).name("<red>◀ Back to Admin Panel</red>").build());

        player.openInventory(inv);
    }

    public void openAdminKillSettings(Player player) {
        Inventory inv = Bukkit.createInventory(new AdminKillHolder(), 27, TextUtil.format("<red>⚔ Kill Counter Config</red>"));
        inv.setItem(11, new GUIItemBuilder(Material.DIAMOND_SWORD).name("<yellow>PvP Kill Streak Bonus: ON</yellow>").build());
        inv.setItem(15, new GUIItemBuilder(Material.ZOMBIE_HEAD).name("<yellow>Mob Kill Coin Rewards: ON</yellow>").build());
        inv.setItem(26, new GUIItemBuilder(Material.BARRIER).name("<red>◀ Back to Admin Panel</red>").build());
        player.openInventory(inv);
    }

    public void openAdminClaimSettings(Player player) {
        Inventory inv = Bukkit.createInventory(new AdminClaimHolder(), 27, TextUtil.format("<green>🏠 Claim & World Config</green>"));
        int earnRate = settingsManager.getInt("claims.blocks_per_hour", 50);
        boolean disableExplosions = settingsManager.getBoolean("world.disable_explosions", false);

        inv.setItem(10, new GUIItemBuilder(Material.GOLDEN_SHOVEL).name("<yellow>Claim Blocks Per Hour: " + earnRate + "</yellow>").lore(List.of("<gray>Click to enter custom value in chat or cycle</gray>")).build());
        inv.setItem(12, new GUIItemBuilder(Material.GRASS_BLOCK).name("<green>Default Chunk Protection: STRICT 16x16</green>").lore(List.of("<gray>All claims are 100% immune to explosion damage</gray>")).build());
        inv.setItem(14, new GUIItemBuilder(Material.TNT).name("<red>Global World Explosions: " + (disableExplosions ? "<red>DISABLED</red>" : "<green>ENABLED</green>") + "</red>").lore(List.of("<yellow>Click to toggle all TNT/Creeper/Crystal explosions server-wide</yellow>")).build());
        inv.setItem(26, new GUIItemBuilder(Material.BARRIER).name("<red>◀ Back to Admin Panel</red>").build());
        player.openInventory(inv);
    }

    public void openAdminTeamSettings(Player player) {
        Inventory inv = Bukkit.createInventory(new AdminTeamHolder(), 27, TextUtil.format("<blue>🏰 Team Config</blue>"));
        int cost = settingsManager.getInt("teams.creation_cost", 5000);
        int baseMembers = settingsManager.getInt("teams.base_max_members", 3);
        inv.setItem(11, new GUIItemBuilder(Material.GOLD_BLOCK).name("<yellow>Team Creation Cost: $" + cost + "</yellow>").lore(List.of("<gray>Click to enter custom value in chat or cycle</gray>")).build());
        inv.setItem(15, new GUIItemBuilder(Material.PLAYER_HEAD).name("<yellow>Base Max Members: " + baseMembers + "</yellow>").lore(List.of("<gray>Click to enter custom value in chat or cycle</gray>")).build());
        inv.setItem(26, new GUIItemBuilder(Material.BARRIER).name("<red>◀ Back to Admin Panel</red>").build());
        player.openInventory(inv);
    }

    public void openAdminCombatSettings(Player player) {
        Inventory inv = Bukkit.createInventory(new AdminCombatHolder(), 27, TextUtil.format("<dark_purple>⚔ Combat & Item Controls</dark_purple>"));
        int duration = settingsManager.getInt("combat.tag_duration", 15);
        int pearlCd = settingsManager.getInt("combat.enderpearl_cooldown", 15);
        int windCd = settingsManager.getInt("combat.windcharge_cooldown", 10);
        int maceCd = settingsManager.getInt("combat.mace_cooldown", 12);
        boolean disableCmds = settingsManager.getBoolean("combat.disable_commands", true);
        boolean shieldGlobal = settingsManager.getBoolean("item.disabled_global.shield", false);
        boolean riptide = settingsManager.getBoolean("combat.riptide_enabled", false);
        boolean crystal = settingsManager.getBoolean("combat.crystal_enabled", false);
        boolean anchor = settingsManager.getBoolean("combat.anchor_enabled", false);

        inv.setItem(9, new GUIItemBuilder(Material.CLOCK).name("<yellow>Combat Tag Duration: " + duration + "s</yellow>").lore(List.of("<gray>Click to enter custom value in chat or cycle</gray>")).build());
        inv.setItem(10, new GUIItemBuilder(Material.COMMAND_BLOCK).name("<red>Commands in Combat: " + (disableCmds ? "<red>DISABLED</red>" : "<green>ENABLED</green>") + "</red>").lore(List.of("<yellow>Click to toggle</yellow>")).build());
        inv.setItem(11, new GUIItemBuilder(Material.ENDER_PEARL).name("<purple>Ender Pearl Cooldown: " + pearlCd + "s</purple>").lore(List.of("<gray>Click to enter custom value in chat or cycle</gray>")).build());
        inv.setItem(12, new GUIItemBuilder(Material.FEATHER).name("<aqua>Wind Charge Cooldown: " + windCd + "s</aqua>").lore(List.of("<gray>Click to enter custom value in chat or cycle</gray>")).build());
        inv.setItem(13, new GUIItemBuilder(Material.HEAVY_CORE).name("<gold>Mace Cooldown: " + maceCd + "s</gold>").lore(List.of("<gray>Click to enter custom value in chat or cycle</gray>")).build());
        inv.setItem(14, new GUIItemBuilder(Material.SHIELD).name("<blue>Shields Server-Wide: " + (shieldGlobal ? "<red>DISABLED</red>" : "<green>ENABLED</green>") + "</blue>").lore(List.of("<yellow>Click to toggle server-wide shield use</yellow>")).build());
        inv.setItem(15, new GUIItemBuilder(Material.TRIDENT).name("<blue>Riptide Trident in Combat: " + (riptide ? "<green>ENABLED</green>" : "<red>DISABLED</red>") + "</blue>").lore(List.of("<yellow>Click to toggle</yellow>")).build());
        inv.setItem(16, new GUIItemBuilder(Material.END_CRYSTAL).name("<light_purple>End Crystals: " + (crystal ? "<green>ENABLED</green>" : "<red>DISABLED</red>") + "</light_purple>").lore(List.of("<yellow>Click to toggle</yellow>")).build());
        inv.setItem(17, new GUIItemBuilder(Material.RESPAWN_ANCHOR).name("<red>Respawn Anchors: " + (anchor ? "<green>ENABLED</green>" : "<red>DISABLED</red>") + "</red>").lore(List.of("<yellow>Click to toggle</yellow>")).build());
        inv.setItem(26, new GUIItemBuilder(Material.BARRIER).name("<red>◀ Back to Admin Panel</red>").build());

        player.openInventory(inv);
    }

    public void openAdminScoreboardSettings(Player player) {
        Inventory inv = Bukkit.createInventory(new AdminScoreboardHolder(), 27, TextUtil.format("<aqua>📊 Scoreboard Config</aqua>"));
        int refresh = settingsManager.getInt("scoreboard.update_ticks", 20);
        inv.setItem(11, new GUIItemBuilder(Material.NAME_TAG).name("<yellow>Scoreboard Refresh: " + (refresh / 20) + "s</yellow>").lore(List.of("<gray>Click to cycle 1s / 2s / 5s</gray>")).build());
        inv.setItem(13, new GUIItemBuilder(Material.TNT).name("<red>🧹 Wipe & Disable All Server Scoreboards</red>").lore(List.of("<gray>Click to remove scoreboards & objectives from ALL players</gray>")).build());
        inv.setItem(26, new GUIItemBuilder(Material.BARRIER).name("<red>◀ Back to Admin Panel</red>").build());
        player.openInventory(inv);
    }

    public void openAdminAuctionSettings(Player player) {
        Inventory inv = Bukkit.createInventory(new AdminAuctionHolder(), 27, TextUtil.format("<gold>🏪 Auction Config</gold>"));
        int fee = settingsManager.getInt("auction.listing_fee", 50);
        int duration = settingsManager.getInt("auction.duration_hours_default", 48);
        long maxPrice = settingsManager.getLong("auction.max_listing_price", 1000000000L);
        int listingCooldown = settingsManager.getInt("auction.listing_cooldown_sec", 0);

        inv.setItem(10, new GUIItemBuilder(Material.GOLD_NUGGET).name("<yellow>Listing Fee: $" + fee + "</yellow>").lore(List.of("<gray>Click to enter custom value in chat or cycle</gray>")).build());
        inv.setItem(12, new GUIItemBuilder(Material.CLOCK).name("<yellow>Default Listing Duration: " + duration + "h</yellow>").lore(List.of("<gray>Click to enter custom value in chat or cycle</gray>")).build());
        inv.setItem(14, new GUIItemBuilder(Material.DIAMOND_BLOCK).name("<green>Max Listing Price: $" + maxPrice + "</green>").lore(List.of("<gray>Click to enter custom value in chat</gray>")).build());
        inv.setItem(16, new GUIItemBuilder(Material.REPEATER).name("<gold>Listing Purchase Delay: " + listingCooldown + "s</gold>").lore(List.of("<gray>Click to enter custom value in chat or cycle</gray>")).build());
        inv.setItem(26, new GUIItemBuilder(Material.BARRIER).name("<red>◀ Back to Admin Panel</red>").build());
        player.openInventory(inv);
    }

    public void openAdminDebugPanel(Player player) {
        Inventory inv = Bukkit.createInventory(new AdminDebugHolder(), 27, TextUtil.format("<red>🐞 Debug Flags Panel (18 Flags)</red>"));

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

            // PRESERVE 100% COMPLETE NBT & CUSTOM MODEL DATA COMPONENTS FROM ORIGINAL ITEM!
            ItemStack display = item.getItem().clone();
            ItemMeta meta = display.getItemMeta();
            if (meta != null) {
                List<Component> currentLore = meta.hasLore() ? meta.lore() : new ArrayList<>();
                if (currentLore == null) currentLore = new ArrayList<>();

                currentLore.add(Component.text("Seller: " + item.getSellerName(), NamedTextColor.WHITE));
                currentLore.add(Component.text("Price: $" + item.getPrice(), NamedTextColor.GREEN));

                if (!item.isPurchasable()) {
                    currentLore.add(Component.text("⏳ Purchasable in " + item.getRemainingCooldownSec() + "s", NamedTextColor.GOLD));
                } else {
                    currentLore.add(Component.text("Click to Purchase", NamedTextColor.YELLOW));
                }

                if (display.getType().name().contains("SHULKER_BOX")) {
                    currentLore.add(Component.text("Right-Click to Preview Contents", NamedTextColor.AQUA));
                }

                meta.lore(currentLore);
                display.setItemMeta(meta);
            }

            inv.setItem(slot++, display);
        }

        // Row 6: Control & Stash Buttons (Slots 45-53)
        inv.setItem(45, new GUIItemBuilder(Material.PLAYER_HEAD).name("<yellow>👤 My Listings</yellow>").lore(List.of("<gray>Click to view & cancel active listings</gray>")).build());
        if (page > 1) {
            inv.setItem(48, new GUIItemBuilder(Material.ARROW).name("<yellow>◀ Previous Page (" + (page - 1) + ")</yellow>").build());
        }
        inv.setItem(49, new GUIItemBuilder(Material.ENDER_CHEST).name("<gold>📦 Expired / Purchased Stash</gold>").lore(List.of("<gray>View & reclaim items from inventory overflow</gray>")).build());
        if (endIndex < filtered.size()) {
            inv.setItem(50, new GUIItemBuilder(Material.ARROW).name("<yellow>Next Page (" + (page + 1) + ") ▶</yellow>").build());
        }
        inv.setItem(53, new GUIItemBuilder(Material.EMERALD).name("<green>➕ Sell Item</green>").lore(List.of("<gray>Hold an item & type /ah sell <price></gray>")).build());

        player.openInventory(inv);
        DebugManager.log(DebugFlag.GUI_CLICKS, "Opened auction house GUI page " + page + " for " + player.getName());
    }

    public void openMyListings(Player player, int page) {
        Inventory inv = Bukkit.createInventory(new AuctionMyListingsHolder(page), 54, TextUtil.format("<gold>👤 My Active Listings (Click to Cancel)</gold>"));

        List<AuctionItem> myListings = auctionManager.getPlayerListings(player.getUniqueId());
        int slot = 0;
        for (AuctionItem item : myListings) {
            ItemStack display = item.getItem().clone();
            ItemMeta meta = display.getItemMeta();
            if (meta != null) {
                List<Component> lore = meta.hasLore() ? meta.lore() : new ArrayList<>();
                if (lore == null) lore = new ArrayList<>();
                lore.add(Component.text("Price: $" + item.getPrice(), NamedTextColor.GREEN));
                lore.add(Component.text("Click to CANCEL & Reclaim Item", NamedTextColor.RED));
                meta.lore(lore);
                display.setItemMeta(meta);
            }
            inv.setItem(slot++, display);
        }

        inv.setItem(49, new GUIItemBuilder(Material.BARRIER).name("<red>◀ Back to Auction House</red>").build());
        player.openInventory(inv);
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

        inv.setItem(26, new GUIItemBuilder(Material.BARRIER).name("<red>◀ Back to Team Menu</red>").build());

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
