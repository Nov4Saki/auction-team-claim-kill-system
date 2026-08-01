package com.guildcore.gui;

import com.guildcore.auction.AuctionItem;
import com.guildcore.auction.AuctionManager;
import com.guildcore.claims.ClaimInfo;
import com.guildcore.claims.ClaimManager;
import com.guildcore.config.SettingsManager;
import com.guildcore.debug.DebugFlag;
import com.guildcore.debug.DebugManager;
import com.guildcore.gui.holders.*;
import com.guildcore.items.ProhibitedItemManager;
import com.guildcore.scoreboard.ScoreboardManager;
import com.guildcore.shop.ShopManager;
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
import org.bukkit.World;
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
    private ProhibitedItemManager prohibitedManager;
    private ShopManager shopManager;

    public GUIManager(SettingsManager settingsManager, TeamManager teamManager, ClaimManager claimManager, AuctionManager auctionManager, StatsManager statsManager, TeamPermissionManager permissionManager) {
        this.settingsManager = settingsManager;
        this.teamManager = teamManager;
        this.claimManager = claimManager;
        this.auctionManager = auctionManager;
        this.statsManager = statsManager;
        this.permissionManager = permissionManager;
    }

    public void setProhibitedItemManager(ProhibitedItemManager prohibitedManager) {
        this.prohibitedManager = prohibitedManager;
    }

    public void setShopManager(ShopManager shopManager) {
        this.shopManager = shopManager;
    }

    public TeamPermissionManager getPermissionManager() {
        return permissionManager;
    }

    public ClaimManager getClaimManager() {
        return claimManager;
    }

    public SettingsManager getSettingsManager() {
        return settingsManager;
    }

    public TeamManager getTeamManager() {
        return teamManager;
    }

    public void openAdminSettings(Player player) {
        Inventory inv = Bukkit.createInventory(new SettingsGUIHolder(), 54, TextUtil.format("<gradient:#FFD700:#FFA500:#DAA520><b>👑 HIGH SOVEREIGN CONTROL PANEL</b></gradient>"));

        // Mythic Dark & Gold Frame
        for (int i = 0; i < 54; i++) {
            if (i == 0 || i == 8 || i == 45 || i == 53) {
                inv.setItem(i, new GUIItemBuilder(Material.PURPLE_STAINED_GLASS_PANE).name("<gradient:#9D50BB:#6E48AA><b>✦ Sovereign Seal</b></gradient>").build());
            } else if (i < 9 || i >= 45 || i % 9 == 0 || i % 9 == 8) {
                inv.setItem(i, new GUIItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name("<gray> </gray>").build());
            } else {
                inv.setItem(i, new GUIItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name("<gray> </gray>").build());
            }
        }

        // Row 1: Primary Module Archives (Slots 10 - 16)
        inv.setItem(10, new GUIItemBuilder(Material.GOLD_INGOT).name("<gradient:#FFD700:#FFA500><b>💰 Economy Treasury Archive</b></gradient>")
                .lore("<gray>▪ Manage kingdom starting funds, PvP bounties, and royal sales tax</gray>", "", "<yellow>▶ Click to inspect Treasury Archive</yellow>").build());

        inv.setItem(11, new GUIItemBuilder(Material.NETHERITE_SWORD).name("<gradient:#800000:#DC143C><b>⚔ Blood & Valor Kill Archive</b></gradient>")
                .lore("<gray>▪ Manage kill streak bonuses and mob slayer coin rewards</gray>", "", "<yellow>▶ Click to inspect Kill Archive</yellow>").build());

        inv.setItem(12, new GUIItemBuilder(Material.GRASS_BLOCK).name("<gradient:#11998e:#38ef7d><b>🏠 Domain & Realm Claims Archive</b></gradient>")
                .lore("<gray>▪ Manage claim block accrual rates and explosion protection</gray>", "", "<yellow>▶ Click to inspect Domain Archive</yellow>").build());

        inv.setItem(13, new GUIItemBuilder(Material.SHIELD).name("<gradient:#00c6ff:#0072ff><b>🏰 Guild Citadel Archive</b></gradient>")
                .lore("<gray>▪ Manage guild charter creation fees and roster size caps</gray>", "", "<yellow>▶ Click to inspect Guild Archive</yellow>").build());

        inv.setItem(14, new GUIItemBuilder(Material.ENCHANTED_GOLDEN_APPLE).name("<gradient:#9D50BB:#6E48AA><b>⚔ Arcane Combat & Relic Rules</b></gradient>")
                .lore("<gray>▪ Manage combat tag timers, restricted relics, and cooldowns</gray>", "", "<yellow>▶ Click to inspect Combat Archive</yellow>").build());

        inv.setItem(15, new GUIItemBuilder(Material.WRITABLE_BOOK).name("<gradient:#8E9EAB:#EEF2F3><b>📜 Scoreboard Scrolls Archive</b></gradient>")
                .lore("<gray>▪ Manage scroll refresh tick-rates and server-wide objective wipes</gray>", "", "<yellow>▶ Click to inspect Scoreboard Archive</yellow>").build());

        inv.setItem(16, new GUIItemBuilder(Material.CHEST).name("<gradient:#FFD700:#FFA500><b>📜 Grand Bazaar Settings Archive</b></gradient>")
                .lore("<gray>▪ Manage listing taxes, default expiration timers, and delays</gray>", "", "<yellow>▶ Click to inspect Bazaar Archive</yellow>").build());

        // Row 2: Secondary & Administrative Hubs (Slots 19 - 25)
        inv.setItem(19, new GUIItemBuilder(Material.COMPASS).name("<gradient:#FFD700:#FFA500><b>🎲 RTP & Teleportation Archive</b></gradient>")
                .lore("<gray>▪ Manage RTP cooldowns, standstill timers, and coordinate bounds</gray>", "", "<yellow>▶ Click to inspect RTP Archive</yellow>").build());

        inv.setItem(20, new GUIItemBuilder(Material.ANVIL).name("<gradient:#800000:#DC143C><b>🚫 Prohibited Items Archive</b></gradient>")
                .lore("<gray>▪ Manage server item bans, crafting blocks, and inventory purges</gray>", "", "<yellow>▶ Click to inspect Prohibited Archive</yellow>").build());

        inv.setItem(21, new GUIItemBuilder(Material.EMERALD).name("<gradient:#00FF87:#60EFFF><b>🛒 Server Admin Shop Hub</b></gradient>")
                .lore("<gray>▪ Manage shop categories, buy/sell items, and pricing</gray>", "", "<yellow>▶ Click to inspect Admin Shop Hub</yellow>").build());

        inv.setItem(22, new GUIItemBuilder(Material.LEVER).name("<gradient:#FF416C:#FF4B2B><b>⚡ Sovereign Debug Forge (18 Flags)</b></gradient>")
                .lore("<gray>▪ Toggle 18 surgical realm diagnostic flags in real-time</gray>", "", "<yellow>▶ Click to open Debug Forge</yellow>").build());

        inv.setItem(23, new GUIItemBuilder(Material.TRIPWIRE_HOOK).name("<gradient:#9D50BB:#6E48AA><b>🎁 Modular Choice Crates Hub</b></gradient>")
                .lore("<gray>▪ Manage key crates, reward tables, and choice menu configurations</gray>", "", "<yellow>▶ Click to inspect Crates Hub</yellow>").build());

        inv.setItem(49, new GUIItemBuilder(Material.BARRIER).name("<red><b>✖ Close Sovereign Control Panel</b></red>").build());

        player.openInventory(inv);
        DebugManager.log(DebugFlag.GUI_CLICKS, "Opened admin settings GUI for " + player.getName());
    }

    public void openAdminEconomySettings(Player player) {
        Inventory inv = Bukkit.createInventory(new AdminEconomyHolder(), 27, TextUtil.format("<gradient:#FFD700:#FFA500><b>💰 Treasury Settings</b></gradient>"));
        long startingBal = settingsManager.getInt("economy.starting_balance", 100);
        long killReward = settingsManager.getInt("economy.pvp_kill_reward", 50);
        long tax = settingsManager.getInt("economy.sales_tax_percent", 5);

        fillBorder27(inv, Material.YELLOW_STAINED_GLASS_PANE);

        inv.setItem(10, new GUIItemBuilder(Material.GOLD_INGOT).name("<yellow><b>Starting Purse: $" + String.format("%,d", startingBal) + " Gold</b></yellow>")
                .lore("<gray>▪ Initial coins granted to new realm citizens</gray>", "", "<yellow>▶ Click to edit value in chat</yellow>").build());

        inv.setItem(12, new GUIItemBuilder(Material.DIAMOND_SWORD).name("<red><b>Slayer Bounty: $" + String.format("%,d", killReward) + " Gold</b></red>")
                .lore("<gray>▪ Coins awarded for felling enemy players</gray>", "", "<yellow>▶ Click to edit value in chat</yellow>").build());

        inv.setItem(14, new GUIItemBuilder(Material.HOPPER).name("<gold><b>Bazaar Sales Tax: " + tax + "%</b></gold>")
                .lore("<gray>▪ Royal tax deducted from auction transactions</gray>", "", "<yellow>▶ Click to edit value in chat</yellow>").build());

        inv.setItem(16, new GUIItemBuilder(Material.EMERALD_BLOCK).name("<gradient:#00FF87:#60EFFF><b>➕ Royal Grant: +$1,000 Gold</b></gradient>")
                .lore("<gray>▪ Instantly deposit $1,000 gold into your purse</gray>", "", "<yellow>▶ Click to receive grant</yellow>").build());

        inv.setItem(26, new GUIItemBuilder(Material.BARRIER).name("<red><b>◀ Return to High Sovereign Panel</b></red>").build());

        player.openInventory(inv);
    }

    public void openAdminKillSettings(Player player) {
        Inventory inv = Bukkit.createInventory(new AdminKillHolder(), 27, TextUtil.format("<gradient:#800000:#DC143C><b>⚔ Blood & Valor Settings</b></gradient>"));
        fillBorder27(inv, Material.RED_STAINED_GLASS_PANE);

        inv.setItem(11, new GUIItemBuilder(Material.DIAMOND_SWORD).name("<yellow><b>PvP Kill Streak Bonus:</b> <gradient:#00FF87:#60EFFF>[✔ ENGAGED]</gradient></yellow>")
                .lore("<gray>▪ Grants extra coins for streak slayings</gray>").build());

        inv.setItem(15, new GUIItemBuilder(Material.ZOMBIE_HEAD).name("<yellow><b>Mob Slayer Bounty Rewards:</b> <gradient:#00FF87:#60EFFF>[✔ ENGAGED]</gradient></yellow>")
                .lore("<gray>▪ Grants coins for slaying hostile monsters</gray>").build());

        inv.setItem(26, new GUIItemBuilder(Material.BARRIER).name("<red><b>◀ Return to High Sovereign Panel</b></red>").build());
        player.openInventory(inv);
    }

    public void openAdminClaimSettings(Player player) {
        Inventory inv = Bukkit.createInventory(new AdminClaimHolder(), 27, TextUtil.format("<gradient:#11998e:#38ef7d><b>🏠 Domain & Realm Settings</b></gradient>"));
        int earnRate = settingsManager.getInt("claims.blocks_per_hour", 50);
        boolean disableExplosions = settingsManager.getBoolean("world.disable_explosions", false);

        fillBorder27(inv, Material.LIME_STAINED_GLASS_PANE);

        inv.setItem(10, new GUIItemBuilder(Material.GOLDEN_SHOVEL).name("<yellow><b>Claim Blocks Earn Rate: " + earnRate + "/hr</b></yellow>")
                .lore("<gray>▪ Blocks accrued by players per active hour</gray>", "", "<yellow>▶ Click to edit value in chat</yellow>").build());

        inv.setItem(12, new GUIItemBuilder(Material.GRASS_BLOCK).name("<green><b>Default Domain Immunity: STRICT 16x16</b></green>")
                .lore("<gray>▪ All claimed chunks possess 100% explosion immunity</gray>").build());

        inv.setItem(14, new GUIItemBuilder(Material.TNT).name("<red><b>Global World Explosions: " + (disableExplosions ? "<gradient:#FF416C:#FF4B2B>[✖ DISENGAGED]</gradient>" : "<gradient:#00FF87:#60EFFF>[✔ ENGAGED]</gradient>") + "</b></red>")
                .lore("<gray>▪ Controls TNT, Creeper, and End Crystal blasts server-wide</gray>", "", "<yellow>▶ Click to toggle setting</yellow>").build());

        inv.setItem(26, new GUIItemBuilder(Material.BARRIER).name("<red><b>◀ Return to High Sovereign Panel</b></red>").build());
        player.openInventory(inv);
    }

    public void openAdminTeamSettings(Player player) {
        Inventory inv = Bukkit.createInventory(new AdminTeamHolder(), 27, TextUtil.format("<gradient:#00c6ff:#0072ff><b>🏰 Guild Charter Settings</b></gradient>"));
        int cost = settingsManager.getInt("teams.creation_cost", 5000);
        int baseMembers = settingsManager.getInt("teams.base_max_members", 3);

        fillBorder27(inv, Material.BLUE_STAINED_GLASS_PANE);

        inv.setItem(11, new GUIItemBuilder(Material.GOLD_BLOCK).name("<yellow><b>Guild Charter Creation Cost: $" + String.format("%,d", cost) + " Gold</b></yellow>")
                .lore("<gray>▪ Gold required from player to register a new guild</gray>", "", "<yellow>▶ Click to edit value in chat</yellow>").build());

        inv.setItem(15, new GUIItemBuilder(Material.PLAYER_HEAD).name("<yellow><b>Base Guild Roster Limit: " + baseMembers + " Members</b></yellow>")
                .lore("<gray>▪ Initial maximum member capacity for new guilds</gray>", "", "<yellow>▶ Click to edit value in chat</yellow>").build());

        inv.setItem(26, new GUIItemBuilder(Material.BARRIER).name("<red><b>◀ Return to High Sovereign Panel</b></red>").build());
        player.openInventory(inv);
    }

    public void openAdminCombatSettings(Player player) {
        Inventory inv = Bukkit.createInventory(new AdminCombatHolder(), 27, TextUtil.format("<gradient:#9D50BB:#6E48AA><b>⚔ Arcane Combat & Relic Rules</b></gradient>"));
        int duration = settingsManager.getInt("combat.tag_duration", 15);
        int pearlCd = settingsManager.getInt("combat.enderpearl_cooldown", 15);
        int windCd = settingsManager.getInt("combat.windcharge_cooldown", 10);
        int maceCd = settingsManager.getInt("combat.mace_cooldown", 12);
        boolean disableCmds = settingsManager.getBoolean("combat.disable_commands", true);
        boolean shieldGlobal = settingsManager.getBoolean("item.disabled_global.shield", false);
        boolean riptide = settingsManager.getBoolean("combat.riptide_enabled", false);
        boolean crystal = settingsManager.getBoolean("combat.crystal_enabled", false);
        boolean anchor = settingsManager.getBoolean("combat.anchor_enabled", false);

        fillBorder27(inv, Material.PURPLE_STAINED_GLASS_PANE);

        inv.setItem(9, new GUIItemBuilder(Material.CLOCK).name("<yellow>Combat Tag Timer: " + duration + "s</yellow>").lore("<gray>▶ Click to edit in chat</gray>").build());
        inv.setItem(10, new GUIItemBuilder(Material.COMMAND_BLOCK).name("<red>Combat Commands: " + (disableCmds ? "<gradient:#FF416C:#FF4B2B>[✖ DISENGAGED]</gradient>" : "<gradient:#00FF87:#60EFFF>[✔ ENGAGED]</gradient>") + "</red>").lore("<yellow>▶ Click to toggle</yellow>").build());
        inv.setItem(11, new GUIItemBuilder(Material.ENDER_PEARL).name("<purple>Ender Pearl Cooldown: " + pearlCd + "s</purple>").lore("<gray>▶ Click to edit in chat</gray>").build());
        inv.setItem(12, new GUIItemBuilder(Material.FEATHER).name("<aqua>Wind Charge Cooldown: " + windCd + "s</aqua>").lore("<gray>▶ Click to edit in chat</gray>").build());
        inv.setItem(13, new GUIItemBuilder(Material.HEAVY_CORE).name("<gold>Mace Cooldown: " + maceCd + "s</gold>").lore("<gray>▶ Click to edit in chat</gray>").build());
        inv.setItem(14, new GUIItemBuilder(Material.SHIELD).name("<blue>Shield Use: " + (shieldGlobal ? "<gradient:#FF416C:#FF4B2B>[✖ BANNED]</gradient>" : "<gradient:#00FF87:#60EFFF>[✔ ALLOWED]</gradient>") + "</blue>").lore("<yellow>▶ Click to toggle</yellow>").build());
        inv.setItem(15, new GUIItemBuilder(Material.TRIDENT).name("<blue>Riptide Trident in Combat: " + (riptide ? "<gradient:#00FF87:#60EFFF>[✔ ENGAGED]</gradient>" : "<gradient:#FF416C:#FF4B2B>[✖ DISENGAGED]</gradient>") + "</blue>").lore("<yellow>▶ Click to toggle</yellow>").build());
        inv.setItem(16, new GUIItemBuilder(Material.END_CRYSTAL).name("<light_purple>End Crystals in Combat: " + (crystal ? "<gradient:#00FF87:#60EFFF>[✔ ENGAGED]</gradient>" : "<gradient:#FF416C:#FF4B2B>[✖ DISENGAGED]</gradient>") + "</light_purple>").lore("<yellow>▶ Click to toggle</yellow>").build());
        inv.setItem(17, new GUIItemBuilder(Material.RESPAWN_ANCHOR).name("<red>Respawn Anchors in Combat: " + (anchor ? "<gradient:#00FF87:#60EFFF>[✔ ENGAGED]</gradient>" : "<gradient:#FF416C:#FF4B2B>[✖ DISENGAGED]</gradient>") + "</red>").lore("<yellow>▶ Click to toggle</yellow>").build());
        inv.setItem(26, new GUIItemBuilder(Material.BARRIER).name("<red><b>◀ Return to High Sovereign Panel</b></red>").build());

        player.openInventory(inv);
    }

    public void openAdminScoreboardSettings(Player player) {
        Inventory inv = Bukkit.createInventory(new AdminScoreboardHolder(), 27, TextUtil.format("<gradient:#8E9EAB:#EEF2F3><b>📜 Scoreboard Scroll Settings</b></gradient>"));
        int refresh = settingsManager.getInt("scoreboard.update_ticks", 20);

        fillBorder27(inv, Material.LIGHT_GRAY_STAINED_GLASS_PANE);

        inv.setItem(11, new GUIItemBuilder(Material.NAME_TAG).name("<yellow><b>Scroll Refresh Rate: " + (refresh / 20) + "s</b></yellow>")
                .lore("<gray>▪ Scoreboard sidebar update interval</gray>", "", "<yellow>▶ Click to cycle (1s / 2s / 5s)</yellow>").build());

        inv.setItem(13, new GUIItemBuilder(Material.TNT).name("<gradient:#FF416C:#FF4B2B><b>🧹 Purge & Banish All Server Scoreboards</b></gradient>")
                .lore("<gray>▪ Immediately strips scoreboards from ALL online citizens</gray>", "", "<red>▶ Click to execute purge</red>").build());

        inv.setItem(26, new GUIItemBuilder(Material.BARRIER).name("<red><b>◀ Return to High Sovereign Panel</b></red>").build());
        player.openInventory(inv);
    }

    public void openAdminAuctionSettings(Player player) {
        Inventory inv = Bukkit.createInventory(new AdminAuctionHolder(), 27, TextUtil.format("<gradient:#FFD700:#FFA500><b>📜 Bazaar Config Archive</b></gradient>"));
        int fee = settingsManager.getInt("auction.listing_fee", 50);
        int duration = settingsManager.getInt("auction.duration_hours_default", 48);
        long maxPrice = settingsManager.getLong("auction.max_listing_price", 1000000000L);
        int listingCooldown = settingsManager.getInt("auction.listing_cooldown_sec", 30);

        fillBorder27(inv, Material.ORANGE_STAINED_GLASS_PANE);

        inv.setItem(10, new GUIItemBuilder(Material.GOLD_NUGGET).name("<yellow><b>Listing Fee: $" + fee + " Gold</b></yellow>").lore("<gray>▶ Click to edit in chat</gray>").build());
        inv.setItem(12, new GUIItemBuilder(Material.CLOCK).name("<yellow><b>Listing Duration: " + duration + " Hours</b></yellow>").lore("<gray>▶ Click to edit in chat</gray>").build());
        inv.setItem(14, new GUIItemBuilder(Material.DIAMOND_BLOCK).name("<green><b>Max Listing Price: $" + String.format("%,d", maxPrice) + " Gold</b></green>").lore("<gray>▶ Click to edit in chat</gray>").build());
        inv.setItem(16, new GUIItemBuilder(Material.REPEATER).name("<gold><b>Listing Purchase Delay: " + listingCooldown + "s</b></gold>").lore("<gray>▶ Click to edit in chat</gray>").build());
        inv.setItem(26, new GUIItemBuilder(Material.BARRIER).name("<red><b>◀ Return to High Sovereign Panel</b></red>").build());
        player.openInventory(inv);
    }

    public void openAdminRtpSettings(Player player) {
        Inventory inv = Bukkit.createInventory(new com.guildcore.gui.holders.AdminRtpHolder(), 27, TextUtil.format("<gradient:#FFD700:#FFA500><b>🎲 RTP & Teleportation Settings</b></gradient>"));
        int cooldown = settingsManager.getInt("rtp.cooldown_sec", 60);
        int warmup = settingsManager.getInt("rtp.warmup_sec", 3);
        int minX = settingsManager.getInt("rtp.range.min_x", -3000);
        int maxX = settingsManager.getInt("rtp.range.max_x", 3000);
        int minZ = settingsManager.getInt("rtp.range.min_z", -3000);
        int maxZ = settingsManager.getInt("rtp.range.max_z", 3000);

        fillBorder27(inv, Material.YELLOW_STAINED_GLASS_PANE);

        inv.setItem(10, new GUIItemBuilder(Material.CLOCK).name("<yellow><b>RTP Cooldown: " + cooldown + "s</b></yellow>")
                .lore("<gray>▪ Delay in seconds between RTP uses</gray>", "", "<yellow>▶ Click to edit value in chat</yellow>").build());

        inv.setItem(11, new GUIItemBuilder(Material.FEATHER).name("<yellow><b>Standstill Warmup: " + warmup + "s</b></yellow>")
                .lore("<gray>▪ Seconds player must stand still before teleport</gray>", "", "<yellow>▶ Click to edit value in chat</yellow>").build());

        inv.setItem(12, new GUIItemBuilder(Material.MAP).name("<yellow><b>Min X Bound: " + minX + "</b></yellow>")
                .lore("<gray>▪ Minimum X coordinate bound for random teleport</gray>", "", "<yellow>▶ Click to edit value in chat</yellow>").build());

        inv.setItem(13, new GUIItemBuilder(Material.MAP).name("<yellow><b>Max X Bound: " + maxX + "</b></yellow>")
                .lore("<gray>▪ Maximum X coordinate bound for random teleport</gray>", "", "<yellow>▶ Click to edit value in chat</yellow>").build());

        inv.setItem(14, new GUIItemBuilder(Material.MAP).name("<yellow><b>Min Z Bound: " + minZ + "</b></yellow>")
                .lore("<gray>▪ Minimum Z coordinate bound for random teleport</gray>", "", "<yellow>▶ Click to edit value in chat</yellow>").build());

        inv.setItem(15, new GUIItemBuilder(Material.MAP).name("<yellow><b>Max Z Bound: " + maxZ + "</b></yellow>")
                .lore("<gray>▪ Maximum Z coordinate bound for random teleport</gray>", "", "<yellow>▶ Click to edit value in chat</yellow>").build());

        inv.setItem(26, new GUIItemBuilder(Material.BARRIER).name("<red><b>◀ Return to High Sovereign Panel</b></red>").build());

        player.openInventory(inv);
    }

    public void openAdminDebugPanel(Player player) {
        Inventory inv = Bukkit.createInventory(new AdminDebugHolder(), 27, TextUtil.format("<gradient:#FF416C:#FF4B2B><b>⚡ Sovereign Debug Forge (18 Flags)</b></gradient>"));

        DebugFlag[] flags = DebugFlag.values();
        for (int i = 0; i < Math.min(flags.length, 18); i++) {
            DebugFlag flag = flags[i];
            boolean enabled = DebugManager.isEnabled(flag);
            Material mat = enabled ? Material.LIME_WOOL : Material.RED_WOOL;
            String status = enabled ? "<gradient:#00FF87:#60EFFF>[✔ ENGAGED]</gradient>" : "<gradient:#FF416C:#FF4B2B>[✖ DISENGAGED]</gradient>";
            inv.setItem(i, new GUIItemBuilder(mat).name("<yellow><b>" + flag.name() + "</b></yellow>").lore("<gray>Status: " + status + "</gray>", "", "<yellow>▶ Click to toggle</yellow>").build());
        }
        inv.setItem(26, new GUIItemBuilder(Material.BARRIER).name("<red><b>◀ Return to High Sovereign Panel</b></red>").build());

        player.openInventory(inv);
    }

    public void openAuctionHouse(Player player) {
        openAuctionHouse(player, 1, "ALL", "");
    }

    public void openAuctionHouse(Player player, int page, String category, String searchQuery) {
        Inventory inv = Bukkit.createInventory(new AuctionGUIHolder(page, category, searchQuery), 54, TextUtil.format("<gradient:#D4AF37:#CC7722><b>📜 GRAND BAZAAR OF THE REALM</b></gradient> <gray>(Page " + page + ")</gray>"));

        // Row 1: Category Selector Banner Buttons (Slots 0-8)
        inv.setItem(0, createCategoryButton(Material.NETHER_STAR, "⭐ ALL WARS", category.equalsIgnoreCase("ALL")));
        inv.setItem(1, createCategoryButton(Material.NETHERITE_SWORD, "🗡 WEAPONS", category.equalsIgnoreCase("WEAPONS")));
        inv.setItem(2, createCategoryButton(Material.NETHERITE_CHESTPLATE, "🛡 ARMOR", category.equalsIgnoreCase("ARMOR")));
        inv.setItem(3, createCategoryButton(Material.DIAMOND_PICKAXE, "⛏ TOOLS", category.equalsIgnoreCase("TOOLS")));
        inv.setItem(4, createCategoryButton(Material.DARK_OAK_LOG, "🧱 BLOCKS", category.equalsIgnoreCase("BLOCKS")));
        inv.setItem(5, createCategoryButton(Material.BREWING_STAND, "🧪 POTIONS", category.equalsIgnoreCase("POTIONS")));
        inv.setItem(6, createCategoryButton(Material.SHULKER_BOX, "📦 SHULKERS", category.equalsIgnoreCase("SHULKERS")));
        inv.setItem(7, new GUIItemBuilder(Material.COMPASS).name("<gradient:#D4AF37:#CC7722><b>🔍 Search Wares</b></gradient>").lore("<gray>Filter by item name or type</gray>", "", "<yellow>▶ Click to search</yellow>").build());
        inv.setItem(8, new GUIItemBuilder(Material.HOPPER).name("<gradient:#D4AF37:#CC7722><b>📊 Sort Wares</b></gradient>").lore("<gray>Cycle sorting criteria</gray>").build());

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
            ItemMeta meta = display.getItemMeta();
            if (meta != null) {
                List<Component> currentLore = meta.hasLore() && meta.lore() != null ? meta.lore() : new ArrayList<>();
                if (currentLore == null) currentLore = new ArrayList<>();

                currentLore.add(Component.text(" "));
                currentLore.add(TextUtil.format("<gray>▪ Merchant: </gray><white><b>" + item.getSellerName() + "</b></white>"));
                currentLore.add(TextUtil.format("<gray>▪ Price: </gray><#D4AF37><b>$" + String.format("%,d", item.getPrice()) + " Gold</b></#D4AF37>"));

                if (!item.isPurchasable()) {
                    currentLore.add(TextUtil.format("<#D4AF37>⏳ GRACE PERIOD - " + item.getRemainingCooldownSec() + "s left</#D4AF37>"));
                    currentLore.add(TextUtil.format("<gray>▪ Inspecting offer before public listing</gray>"));
                } else {
                    currentLore.add(TextUtil.format("<gradient:#00FF87:#60EFFF>▶ Click to Buy Relic</gradient>"));
                }

                if (display.getType().name().contains("SHULKER_BOX")) {
                    currentLore.add(TextUtil.format("<aqua>📦 Right-Click to Inspect Shulker</aqua>"));
                }

                meta.lore(currentLore);
                display.setItemMeta(meta);
            }

            inv.setItem(slot++, display);
        }

        while (slot < 45) {
            inv.setItem(slot++, new GUIItemBuilder(Material.AIR).build());
        }

        // Row 6: Controls & Stash
        inv.setItem(45, new GUIItemBuilder(Material.PLAYER_HEAD).name("<gradient:#D4AF37:#CC7722><b>👤 My Listings</b></gradient>").lore("<gray>Inspect & reclaim your listings</gray>", "", "<yellow>▶ Click to open</yellow>").build());
        if (page > 1) {
            inv.setItem(48, new GUIItemBuilder(Material.ARROW).name("<yellow><b>◀ Prev Page (" + (page - 1) + ")</b></yellow>").build());
        } else {
            inv.setItem(48, new GUIItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name("<gray> </gray>").build());
        }

        inv.setItem(49, new GUIItemBuilder(Material.ENDER_CHEST).name("<gradient:#9D50BB:#6E48AA><b>📦 Expired Stash</b></gradient>").lore("<gray>Reclaim unsold wares & gold</gray>", "", "<yellow>▶ Click to open stash</yellow>").build());

        if (endIndex < filtered.size()) {
            inv.setItem(50, new GUIItemBuilder(Material.ARROW).name("<yellow><b>Next Page (" + (page + 1) + ") ▶</b></yellow>").build());
        } else {
            inv.setItem(50, new GUIItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name("<gray> </gray>").build());
        }

        inv.setItem(53, new GUIItemBuilder(Material.EMERALD).name("<gradient:#00FF87:#60EFFF><b>➕ Sell Relic (/ah sell <price>)</b></gradient>").lore("<gray>Hold item & run /ah sell <price></gray>").build());

        player.openInventory(inv);
        DebugManager.log(DebugFlag.GUI_CLICKS, "Opened auction house GUI page " + page + " for " + player.getName());
    }

    private ItemStack createCategoryButton(Material mat, String name, boolean isActive) {
        GUIItemBuilder builder = new GUIItemBuilder(mat).name((isActive ? "<gradient:#FFD700:#FFA500><b>" : "<gray>") + name + (isActive ? "</b></gradient>" : "</gray>"));
        if (isActive) {
            builder.glow(true).lore("<gradient:#00FF87:#60EFFF><b>✔ ACTIVE CATEGORY</b></gradient>");
        }
        return builder.build();
    }

    public void openMyListings(Player player, int page) {
        Inventory inv = Bukkit.createInventory(new AuctionMyListingsHolder(page), 54, TextUtil.format("<gradient:#FFD700:#FFA500><b>📜 My Active Bazaar Offerings</b></gradient>"));

        for (int i = 0; i < 54; i++) {
            if (i >= 45 && i != 49) {
                inv.setItem(i, new GUIItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name("<gray> </gray>").build());
            }
        }

        List<AuctionItem> myListings = auctionManager.getPlayerListings(player.getUniqueId());
        int slot = 0;
        for (AuctionItem item : myListings) {
            if (slot >= 45) break;
            ItemStack display = item.getItem().clone();
            ItemMeta meta = display.getItemMeta();
            if (meta != null) {
                List<Component> lore = meta.hasLore() && meta.lore() != null ? meta.lore() : new ArrayList<>();
                if (lore == null) lore = new ArrayList<>();
                lore.add(Component.text(" "));
                lore.add(TextUtil.format("<gray>▪ Listing Price: </gray><gradient:#00FF87:#60EFFF><b>$" + String.format("%,d", item.getPrice()) + " Gold</b></gradient>"));
                if (!item.isPurchasable()) {
                    lore.add(TextUtil.format("<gradient:#FFD700:#FFA500><b>⏳ Grace Period: " + item.getRemainingCooldownSec() + "s remaining (Only seller can cancel!)</b></gradient>"));
                } else {
                    lore.add(TextUtil.format("<gradient:#00FF87:#60EFFF><b>✔ Active on Public Auction</b></gradient>"));
                }
                lore.add(TextUtil.format("<gradient:#FF416C:#FF4B2B><b>[CLICK TO CANCEL & RECLAIM RELIC]</b></gradient>"));
                meta.lore(lore);
                display.setItemMeta(meta);
            }
            inv.setItem(slot++, display);
        }

        inv.setItem(49, new GUIItemBuilder(Material.BARRIER).name("<red><b>◀ Return to Grand Bazaar</b></red>").build());
        player.openInventory(inv);
    }

    public void openConfirmPurchase(Player player, AuctionItem item) {
        Inventory inv = Bukkit.createInventory(new AuctionConfirmHolder(item), 27, TextUtil.format("<gradient:#FFD700:#FFA500><b>Confirm Relic Acquisition ($" + String.format("%,d", item.getPrice()) + ")</b></gradient>"));

        fillBorder27(inv, Material.ORANGE_STAINED_GLASS_PANE);

        inv.setItem(11, new GUIItemBuilder(Material.LIME_WOOL).name("<gradient:#00FF87:#60EFFF><b>✔ CONFIRM ACQUISITION ($" + String.format("%,d", item.getPrice()) + " Gold)</b></gradient>")
                .lore("<gray>Click to complete purchase and receive relic</gray>").build());

        inv.setItem(13, item.getItem().clone());

        inv.setItem(15, new GUIItemBuilder(Material.RED_WOOL).name("<gradient:#FF416C:#FF4B2B><b>✖ CANCEL ACQUISITION</b></gradient>")
                .lore("<gray>Return to Grand Bazaar listings</gray>").build());

        player.openInventory(inv);
    }

    public void openTeamMenu(Player player, Team team) {
        if (team == null) {
            player.sendMessage(TextUtil.format("<red>You do not belong to any guild.</red>"));
            return;
        }

        Inventory inv = Bukkit.createInventory(new TeamGUIHolder(team.getId()), 45, TextUtil.format("<gradient:#00c6ff:#0072ff><b>🏰 GUILD CITADEL: " + team.getName() + "</b></gradient>"));

        for (int i = 0; i < 45; i++) {
            if (i < 9 || i >= 36 || i % 9 == 0 || i % 9 == 8) {
                inv.setItem(i, new GUIItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name("<gray> </gray>").build());
            }
        }

        int memberCount = teamManager.getTeamMembersCount(team.getId());
        inv.setItem(10, new GUIItemBuilder(Material.BEACON).name("<gradient:#FFD700:#FFA500><b>🏰 Guild Charter: " + team.getName() + "</b></gradient>")
                .lore(
                        "<gray>▪ Guild Level: </gray><white><b>" + team.getLevel() + "</b></white>",
                        "<gray>▪ Roster Limit: </gray><white><b>" + memberCount + " / " + team.getMaxMembers() + " Members</b></white>",
                        "<gray>▪ Guild Treasury: </gray><gradient:#00FF87:#60EFFF><b>$" + String.format("%,d", team.getBankBalance()) + " Gold</b></gradient>"
                ).build());

        inv.setItem(12, new GUIItemBuilder(Material.GOLD_BLOCK).name("<gold><b>🏦 Guild Treasury Bank</b></gold>")
                .lore("<gray>View bank balance and make deposits/withdrawals</gray>", "", "<yellow>▶ Click for bank details</yellow>").build());

        inv.setItem(14, new GUIItemBuilder(Material.CHEST).name("<gold><b>📦 Shared Guild Vault</b></gold>")
                .lore("<gray>Access shared virtual storage pages</gray>", "", "<yellow>▶ Click to open vault</yellow>").build());

        inv.setItem(16, new GUIItemBuilder(Material.NETHER_STAR).name("<green><b>⬆ Guild Citadel Upgrades</b></green>")
                .lore("<gray>Expand member caps, vault pages, and claim limits</gray>", "", "<yellow>▶ Click to inspect upgrades</yellow>").build());

        inv.setItem(28, new GUIItemBuilder(Material.WRITABLE_BOOK).name("<aqua><b>📜 Rank Codex & Permissions</b></aqua>")
                .lore("<gray>Edit guild ranks and member privileges</gray>", "", "<yellow>▶ Click to open codex</yellow>").build());

        inv.setItem(30, new GUIItemBuilder(Material.MAP).name("<green><b>🗺 Guild Territory Claims</b></green>")
                .lore("<gray>View team claimed chunks and land boundaries</gray>", "", "<yellow>▶ Click to view claims</yellow>").build());

        inv.setItem(32, new GUIItemBuilder(Material.RED_BED).name("<light_purple><b>🏠 Hearthstone Teleport</b></light_purple>")
                .lore("<gray>Teleport directly to your guild home sanctuary</gray>", "", "<yellow>▶ Click to teleport</yellow>").build());

        inv.setItem(34, new GUIItemBuilder(Material.TNT).name("<red><b>⚔ War & Raid Hub</b></red>")
                .lore("<gray>Inspect rival guilds and manage raid declarations</gray>", "", "<yellow>▶ Click to enter hub</yellow>").build());

        player.openInventory(inv);
        DebugManager.log(DebugFlag.GUI_CLICKS, "Opened main Team GUI for " + player.getName());
    }

    public void openTeamUpgrades(Player player, Team team) {
        Inventory inv = Bukkit.createInventory(new TeamUpgradesHolder(team.getId()), 27, TextUtil.format("<gradient:#FFD700:#FFA500><b>⬆ Citadel Upgrades (" + team.getName() + ")</b></gradient>"));

        fillBorder27(inv, Material.CYAN_STAINED_GLASS_PANE);

        boolean memberCapAfford = team.getBankBalance() >= 5000;
        inv.setItem(11, new GUIItemBuilder(Material.PLAYER_HEAD).name("<yellow><b>👥 Member Capacity (+2 Members)</b></yellow>")
                .lore(
                        "<gray>▪ Current Cap: <white>" + team.getMaxMembers() + " Members</white></gray>",
                        "<gray>▪ Upgrade Cost: <gradient:#00FF87:#60EFFF>$5,000 Guild Treasury</gradient></gray>",
                        "",
                        memberCapAfford ? "<gradient:#00FF87:#60EFFF><b>✔ TREASURY SUFFICIENT - CLICK TO UPGRADE</b></gradient>" : "<gradient:#FF416C:#FF4B2B><b>✖ INSUFFICIENT GUILD BANK</b></gradient>"
                ).build());

        inv.setItem(13, new GUIItemBuilder(Material.CHEST).name("<gold><b>📦 Vault Capacity (+1 Page)</b></gold>")
                .lore(
                        "<gray>▪ Upgrade Cost: <gradient:#00FF87:#60EFFF>$10,000 Guild Treasury</gradient></gray>",
                        "",
                        team.getBankBalance() >= 10000 ? "<gradient:#00FF87:#60EFFF><b>✔ TREASURY SUFFICIENT - CLICK TO UPGRADE</b></gradient>" : "<gradient:#FF416C:#FF4B2B><b>✖ INSUFFICIENT GUILD BANK</b></gradient>"
                ).build());

        inv.setItem(15, new GUIItemBuilder(Material.GRASS_BLOCK).name("<green><b>🗺 Land Claims (+5 Chunks)</b></green>")
                .lore(
                        "<gray>▪ Upgrade Cost: <gradient:#00FF87:#60EFFF>$7,500 Guild Treasury</gradient></gray>",
                        "",
                        team.getBankBalance() >= 7500 ? "<gradient:#00FF87:#60EFFF><b>✔ TREASURY SUFFICIENT - CLICK TO UPGRADE</b></gradient>" : "<gradient:#FF416C:#FF4B2B><b>✖ INSUFFICIENT GUILD BANK</b></gradient>"
                ).build());

        inv.setItem(26, new GUIItemBuilder(Material.BARRIER).name("<red><b>◀ Return to Guild Citadel</b></red>").build());

        player.openInventory(inv);
    }

    public void openTeamPermissions(Player player, Team team, String selectedRole) {
        if (selectedRole == null) selectedRole = "MEMBER";
        Inventory inv = Bukkit.createInventory(new TeamPermissionsHolder(team.getId(), selectedRole), 54, TextUtil.format("<gradient:#D4AF37:#CC7722><b>📜 Rank Codex & Permissions (" + team.getName() + ")</b></gradient>"));
        fillBorder54(inv, Material.ORANGE_STAINED_GLASS_PANE);

        boolean isOfficer = selectedRole.equalsIgnoreCase("OFFICER");
        boolean isMember = selectedRole.equalsIgnoreCase("MEMBER");
        boolean isRecruit = selectedRole.equalsIgnoreCase("RECRUIT");

        inv.setItem(10, new GUIItemBuilder(isOfficer ? Material.GOLDEN_HELMET : Material.LEATHER_HELMET)
                .name(isOfficer ? "<gradient:#00FF87:#60EFFF><b>▶ OFFICER ROLE [SELECTED]</b></gradient>" : "<gray>▪ OFFICER ROLE</gray>")
                .lore("<gray>Click to view/toggle Officer privileges</gray>").build());

        inv.setItem(12, new GUIItemBuilder(isMember ? Material.IRON_HELMET : Material.LEATHER_HELMET)
                .name(isMember ? "<gradient:#00FF87:#60EFFF><b>▶ MEMBER ROLE [SELECTED]</b></gradient>" : "<gray>▪ MEMBER ROLE</gray>")
                .lore("<gray>Click to view/toggle Member privileges</gray>").build());

        inv.setItem(14, new GUIItemBuilder(isRecruit ? Material.CHAINMAIL_HELMET : Material.LEATHER_HELMET)
                .name(isRecruit ? "<gradient:#00FF87:#60EFFF><b>▶ RECRUIT ROLE [SELECTED]</b></gradient>" : "<gray>▪ RECRUIT ROLE</gray>")
                .lore("<gray>Click to view/toggle Recruit privileges</gray>").build());

        String[] nodes = {"BANK_DEPOSIT", "BANK_WITHDRAW", "VAULT_ACCESS", "CLAIM_LAND", "INVITE_MEMBERS", "KICK_MEMBERS", "BUILD", "SET_HOME"};
        String[] titles = {"Bank Deposit", "Bank Withdraw", "Vault Storage", "Claim Territory", "Invite Members", "Kick Members", "Build & Break", "Set Guild Home"};
        Material[] icons = {Material.GOLD_INGOT, Material.GOLD_NUGGET, Material.CHEST, Material.GRASS_BLOCK, Material.WRITABLE_BOOK, Material.ANVIL, Material.DIAMOND_PICKAXE, Material.RED_BED};
        int[] slots = {19, 21, 23, 25, 29, 31, 33, 35};

        for (int i = 0; i < nodes.length; i++) {
            String node = nodes[i];
            boolean allowed = permissionManager.hasPermission(team.getId(), selectedRole, node);
            inv.setItem(slots[i], new GUIItemBuilder(icons[i])
                    .name("<gradient:#D4AF37:#CC7722><b>" + titles[i] + "</b></gradient>")
                    .lore(
                            "<gray>▪ Status: " + (allowed ? "<gradient:#00FF87:#60EFFF><b>[✔ ALLOWED]</b></gradient>" : "<gradient:#FF416C:#FF4B2B><b>[✖ DENIED]</b></gradient>") + "</gray>",
                            "",
                            "<yellow>▶ Click to toggle permission for " + selectedRole + "</yellow>"
                    ).build());
        }

        inv.setItem(49, new GUIItemBuilder(Material.BARRIER).name("<red><b>◀ Return to Guild Citadel</b></red>").build());
        player.openInventory(inv);
    }

    public void openClaimFlags(Player player, Chunk chunk) {
        Inventory inv = Bukkit.createInventory(new ClaimFlagsGUIHolder(), 27, TextUtil.format("<gradient:#11998e:#38ef7d><b>🏠 Domain Protection Flags</b></gradient>"));
        fillBorder27(inv, Material.LIME_STAINED_GLASS_PANE);

        ClaimInfo claim = claimManager.getClaimAt(chunk);
        boolean mobSpawn = claim == null || claim.hasFlag("mob_spawning");
        boolean pvp = claim != null && claim.hasFlag("pvp");

        inv.setItem(10, new GUIItemBuilder(Material.ZOMBIE_HEAD).name("<yellow><b>Hostile Spawning: " + (mobSpawn ? "<gradient:#00FF87:#60EFFF>[✔ ALLOWED]</gradient>" : "<gradient:#FF416C:#FF4B2B>[✖ DISENGAGED]</gradient>") + "</b></yellow>").build());
        inv.setItem(11, new GUIItemBuilder(Material.DIAMOND_SWORD).name("<yellow><b>PvP Combat: " + (pvp ? "<gradient:#00FF87:#60EFFF>[✔ ALLOWED]</gradient>" : "<gradient:#FF416C:#FF4B2B>[✖ DISENGAGED]</gradient>") + "</b></yellow>").build());

        inv.setItem(26, new GUIItemBuilder(Material.BARRIER).name("<red><b>✖ Close Flags</b></red>").build());

        player.openInventory(inv);
    }

    private void fillBorder27(Inventory inv, Material borderPane) {
        for (int i = 0; i < 27; i++) {
            if (i < 9 || i >= 18 || i % 9 == 0 || i % 9 == 8) {
                inv.setItem(i, new GUIItemBuilder(borderPane).name("<gray> </gray>").build());
            }
        }
    }

    private void fillBorder54(Inventory inv, Material borderPane) {
        for (int i = 0; i < 54; i++) {
            if (i < 9 || i >= 45 || i % 9 == 0 || i % 9 == 8) {
                inv.setItem(i, new GUIItemBuilder(borderPane).name("<gray> </gray>").build());
            }
        }
    }

    public void openRtpWorldMenu(Player player) {
        Inventory inv = Bukkit.createInventory(new com.guildcore.gui.holders.RTPWorldGUIHolder(), 27, TextUtil.format("<gradient:#D4AF37:#CC7722><b>🎲 Realm Teleportation Codex</b></gradient>"));
        fillBorder27(inv, Material.ORANGE_STAINED_GLASS_PANE);

        inv.setItem(11, new GUIItemBuilder(Material.GRASS_BLOCK).name("<gradient:#00FF87:#60EFFF><b>🌍 Overworld Realm</b></gradient>")
                .lore("<gray>▪ Teleport to a safe surface above Y=63</gray>", "<gray>▪ Standstill Warmup & Range Protection</gray>", "", "<yellow>▶ Click to Teleport to Overworld</yellow>").build());

        inv.setItem(13, new GUIItemBuilder(Material.NETHERRACK).name("<gradient:#FF416C:#FF4B2B><b>🔥 Nether Underworld</b></gradient>")
                .lore("<gray>▪ Teleport to a safe solid block in Nether</gray>", "", "<yellow>▶ Click to Teleport to Nether</yellow>").build());

        inv.setItem(15, new GUIItemBuilder(Material.END_STONE).name("<gradient:#9D50BB:#6E48AA><b>🔮 Ender Void Realm</b></gradient>")
                .lore("<gray>▪ Teleport to safe surface in The End</gray>", "", "<yellow>▶ Click to Teleport to End</yellow>").build());

        player.openInventory(inv);
    }

    public void openAdminShopHub(Player player) {
        if (shopManager == null) return;
        Inventory inv = Bukkit.createInventory(new com.guildcore.gui.holders.AdminShopHubHolder(), 54, TextUtil.format("<gradient:#D4AF37:#CC7722><b>⚜ Merchant Guild Forge (Admin)</b></gradient>"));
        fillBorder54(inv, Material.CYAN_STAINED_GLASS_PANE);

        inv.setItem(45, new GUIItemBuilder(Material.WRITABLE_BOOK).name("<gradient:#00FF87:#60EFFF><b>➕ Create New Shop Category</b></gradient>")
                .lore("<gray>▪ Click to create a new category via chat</gray>").build());

        var categories = shopManager.getCategories().values();
        int slot = 10;
        for (var cat : categories) {
            if (slot == 17 || slot == 26 || slot == 35) slot += 2;
            if (slot >= 44) break;

            inv.setItem(slot++, new GUIItemBuilder(cat.getIcon()).name("<gradient:#D4AF37:#CC7722><b>⚜ " + cat.getName() + " (ID: " + cat.getId() + ")</b></gradient>")
                    .lore(
                            "<gray>▪ [Left-Click] Edit Category Items</gray>",
                            "<gray>▪ [Right-Click] Set Icon (Item in hand)</gray>",
                            "<gray>▪ [Shift-Right] Delete Category</gray>"
                    ).build());
        }

        inv.setItem(49, new GUIItemBuilder(Material.BARRIER).name("<red><b>◀ Return to High Sovereign Panel</b></red>").build());
        player.openInventory(inv);
    }

    public void openAdminShopCategoryEditor(Player player, int categoryId) {
        if (shopManager == null) return;
        var cat = shopManager.getCategories().get(categoryId);
        if (cat == null) return;

        Inventory inv = Bukkit.createInventory(new com.guildcore.gui.holders.AdminShopCategoryEditorHolder(categoryId), 54, TextUtil.format("<gradient:#D4AF37:#CC7722><b>⚜ Editing Category: " + cat.getName() + "</b></gradient>"));
        fillBorder54(inv, Material.CYAN_STAINED_GLASS_PANE);

        inv.setItem(45, new GUIItemBuilder(Material.HOPPER).name("<gradient:#00FF87:#60EFFF><b>➕ Add Held Item to Category</b></gradient>")
                .lore("<gray>▪ Click while holding an item to add it</gray>").build());

        var items = shopManager.getCategoryItems(categoryId);
        int[] innerSlots = {
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34,
                37, 38, 39, 40, 41, 42, 43
        };

        for (int i = 0; i < Math.min(items.size(), innerSlots.length); i++) {
            var item = items.get(i);
            ItemStack display = item.getItem().clone();
            ItemMeta meta = display.getItemMeta();
            if (meta != null) {
                List<Component> lore = meta.hasLore() && meta.lore() != null ? meta.lore() : new ArrayList<>();
                lore.add(Component.text(" "));
                lore.add(TextUtil.format("<gray>▪ Buy Price: </gray><gradient:#00FF87:#60EFFF><b>$" + String.format("%,d", item.getBuyPrice()) + " Gold</b></gradient>"));
                lore.add(TextUtil.format("<gray>▪ Sell Price: </gray><gradient:#FF416C:#FF4B2B><b>$" + String.format("%,d", item.getSellPrice()) + " Gold</b></gradient>"));
                lore.add(TextUtil.format("<gray>▪ [Left-Click] Edit Buy Price</gray>"));
                lore.add(TextUtil.format("<gray>▪ [Right-Click] Edit Sell Price</gray>"));
                lore.add(TextUtil.format("<gray>▪ [Shift-Right] Remove Item</gray>"));
                meta.lore(lore);
                display.setItemMeta(meta);
            }
            inv.setItem(innerSlots[i], display);
        }

        inv.setItem(49, new GUIItemBuilder(Material.BARRIER).name("<red><b>◀ Return to Shop Admin Hub</b></red>").build());
        player.openInventory(inv);
    }

    public void openAdminProhibitedItems(Player player) {
        openAdminProhibitedItems(player, 1);
    }

    public void openAdminProhibitedItems(Player player, int page) {
        if (prohibitedManager == null) return;
        List<Material> mats = new ArrayList<>(prohibitedManager.getProhibitedMaterials());
        int pageSize = 28;
        int totalPages = Math.max(1, (int) Math.ceil((double) mats.size() / pageSize));
        if (page < 1) page = 1;
        if (page > totalPages) page = totalPages;

        Inventory inv = Bukkit.createInventory(new com.guildcore.gui.holders.AdminProhibitedHolder(page), 54, TextUtil.format("<gradient:#800000:#DC143C><b>🚫 Prohibited Items Codex</b></gradient> <gray>(" + page + "/" + totalPages + ")</gray>"));
        fillBorder54(inv, Material.RED_STAINED_GLASS_PANE);

        inv.setItem(4, new GUIItemBuilder(Material.ANVIL).name("<gradient:#FF416C:#FF4B2B><b>🔨 Ban Item in Main Hand</b></gradient>")
                .lore("<gray>▪ Click while holding an item to add it to ban list</gray>", "", "<yellow>▶ Click to ban item in hand</yellow>").build());

        int startIndex = (page - 1) * pageSize;
        int endIndex = Math.min(startIndex + pageSize, mats.size());

        int[] itemSlots = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
        };

        for (int i = startIndex; i < endIndex; i++) {
            Material mat = mats.get(i);
            int slot = itemSlots[i - startIndex];
            inv.setItem(slot, new GUIItemBuilder(mat).name("<gradient:#FF416C:#FF4B2B><b>🚫 " + mat.name() + "</b></gradient>")
                    .lore("<gray>▪ Prohibited by Royal Decree</gray>", "", "<red>▶ Click to Unban & Allow Item</red>").build());
        }

        if (page > 1) {
            inv.setItem(45, new GUIItemBuilder(Material.ARROW).name("<yellow><b>◀ Previous Page (" + (page - 1) + ")</b></yellow>").build());
        }
        inv.setItem(48, new GUIItemBuilder(Material.BOOK).name("<gold><b>📖 Page " + page + " of " + totalPages + "</b></gold>")
                .lore("<gray>▪ Total Banned Items: " + mats.size() + "</gray>").build());
        inv.setItem(49, new GUIItemBuilder(Material.BARRIER).name("<red><b>◀ Return to High Sovereign Panel</b></red>").build());
        if (page < totalPages) {
            inv.setItem(53, new GUIItemBuilder(Material.ARROW).name("<yellow><b>Next Page (" + (page + 1) + ") ▶</b></yellow>").build());
        }

        player.openInventory(inv);
    }

    public String getClaimOwnerName(ClaimInfo claim) {
        if (claim == null) return "Wilderness";
        if (claim.isTeamClaim() && claim.getTeamId() != null) {
            Team t = teamManager.getTeam(claim.getTeamId());
            return t != null ? t.getName() : "Guild #" + claim.getTeamId();
        }
        if (claim.getOwnerUuid() != null) {
            org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(claim.getOwnerUuid());
            return op.getName() != null ? op.getName() : "Player";
        }
        return "Unknown";
    }

    public void openTeamMapGUI(Player player) {
        if (player == null) return;
        Chunk center = player.getLocation().getChunk();
        World world = center.getWorld();
        int centerCx = center.getX();
        int centerCz = center.getZ();

        Team team = teamManager.getPlayerTeam(player.getUniqueId());

        long costCoins = settingsManager.getLong("claims.map.cost_coins", 500);
        int costXpLevels = settingsManager.getInt("claims.map.cost_xp_levels", 2);
        int costXpPoints = settingsManager.getInt("claims.map.cost_xp_points", 0);
        String itemMatStr = settingsManager.getString("claims.map.cost_item_material", "DIAMOND");
        int costItemAmount = settingsManager.getInt("claims.map.cost_item_amount", 2);
        Material costItemMat = Material.matchMaterial(itemMatStr);
        if (costItemMat == null) costItemMat = Material.DIAMOND;

        Inventory inv = Bukkit.createInventory(new com.guildcore.gui.holders.TeamMapGUIHolder(centerCx, centerCz), 54, TextUtil.format("<gradient:#56ab2f:#a8e063><b>🗺 REALM TERRITORY MAP</b></gradient>"));

        fillBorder54(inv, Material.GREEN_STAINED_GLASS_PANE);

        // Header info bar (Row 0)
        inv.setItem(0, new GUIItemBuilder(Material.COMPASS).name("<gradient:#FFD700:#FFA500><b>📍 Current Position</b></gradient>")
                .lore("<gray>▪ Chunk Coords: </gray><yellow>X=" + centerCx + ", Z=" + centerCz + "</yellow>",
                      "<gray>▪ World: </gray><white>" + world.getName() + "</white>").build());

        int activeClaims = team != null ? claimManager.getTeamClaimsCount(team.getId()) : 0;
        inv.setItem(4, new GUIItemBuilder(Material.BEACON).name("<gradient:#00c6ff:#0072ff><b>🏰 " + (team != null ? team.getName() : "No Team") + " Territory</b></gradient>")
                .lore("<gray>▪ Active Claims: </gray><green>" + (team != null ? activeClaims + " / " + team.getMaxClaims() : "0") + " Chunks</green>",
                      "<gray>▪ Bank Balance: </gray><gold>$" + (team != null ? String.format("%,d", team.getBankBalance()) : "0") + " Gold</gold>").build());

        inv.setItem(8, new GUIItemBuilder(Material.GOLD_BLOCK).name("<gradient:#FFD700:#FFA500><b>📜 Claim Level Cost</b></gradient>")
                .lore("<gray>▪ Coins: </gray><gold>$" + String.format("%,d", costCoins) + "</gold>",
                      "<gray>▪ XP Levels: </gray><green>" + costXpLevels + " Levels</green>" + (costXpPoints > 0 ? " <gray>(" + costXpPoints + " pts)</gray>" : ""),
                      "<gray>▪ Item Cost: </gray><aqua>" + costItemAmount + "x " + costItemMat.name() + "</aqua>",
                      "",
                      "<yellow>▶ Click any gray pane to claim chunk!</yellow>").build());

        // Grid (Slots 10-16, 19-25, 28-34, 37-43) - 7 columns x 4 rows
        int[] mapSlots = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
        };

        int idx = 0;
        for (int dz = -1; dz <= 2; dz++) {
            for (int dx = -3; dx <= 3; dx++) {
                if (idx >= mapSlots.length) break;
                int slot = mapSlots[idx++];
                int cx = centerCx + dx;
                int cz = centerCz + dz;

                boolean isPlayerChunk = (dx == 0 && dz == 0);
                ClaimInfo claim = claimManager.getClaimAt(world, cx, cz);

                if (isPlayerChunk) {
                    inv.setItem(slot, new GUIItemBuilder(Material.YELLOW_STAINED_GLASS_PANE).name("<gradient:#FFD700:#FFA500><b>📍 YOUR CURRENT CHUNK (" + cx + ", " + cz + ")</b></gradient>")
                            .lore("<gray>▪ Status: </gray>" + (claim == null ? "<gray>Wilderness</gray>" : (team != null && claim.getTeamId() != null && team.getId() == claim.getTeamId() ? "<green>Your Team Claim</green>" : "<red>Claimed by " + getClaimOwnerName(claim) + "</red>")),
                                  "",
                                  (claim == null ? "<yellow>▶ Click to claim this chunk!</yellow>" : "<gray>Already claimed</gray>")).build());
                } else if (claim == null) {
                    inv.setItem(slot, new GUIItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name("<gray><b>Unclaimed Chunk (" + cx + ", " + cz + ")</b></gray>")
                            .lore("<gray>▪ Status: </gray><white>Wilderness</white>",
                                  "<gray>▪ Cost: </gray><gold>$" + String.format("%,d", costCoins) + "</gold> <gray>|</gray> <green>" + costXpLevels + " Lvl</green> <gray>|</gray> <aqua>" + costItemAmount + "x " + costItemMat.name() + "</aqua>",
                                  "",
                                  "<yellow>▶ Click to claim chunk for your team</yellow>").build());
                } else if (team != null && claim.getTeamId() != null && team.getId() == claim.getTeamId()) {
                    inv.setItem(slot, new GUIItemBuilder(Material.LIME_STAINED_GLASS_PANE).name("<gradient:#11998e:#38ef7d><b>🛡 Your Guild Territory (" + cx + ", " + cz + ")</b></gradient>")
                            .lore("<gray>▪ Status: </gray><green>Secured & Protected</green>",
                                  "<gray>▪ Owner: </gray><white>" + getClaimOwnerName(claim) + "</white>").build());
                } else {
                    inv.setItem(slot, new GUIItemBuilder(Material.RED_STAINED_GLASS_PANE).name("<gradient:#800000:#DC143C><b>⚔ Foreign Territory (" + cx + ", " + cz + ")</b></gradient>")
                            .lore("<gray>▪ Status: </gray><red>Occupied</red>",
                                  "<gray>▪ Owner: </gray><white>" + getClaimOwnerName(claim) + "</white>").build());
                }
            }
        }

        inv.setItem(45, new GUIItemBuilder(Material.CLOCK).name("<yellow><b>🔄 Refresh Map</b></yellow>").lore("<gray>Update claim visual status</gray>").build());
        inv.setItem(49, new GUIItemBuilder(Material.BARRIER).name("<red><b>✖ Close Territory Map</b></red>").build());

        player.openInventory(inv);
    }
}


