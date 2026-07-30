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
        Inventory inv = Bukkit.createInventory(new SettingsGUIHolder(), 54, TextUtil.format("<gradient:#FFD700:#FFA500:#DAA520><b>👑 HIGH SOVEREIGN CONTROL PANEL</b></gradient>"));

        // Mythic Dark & Gold Frame
        for (int i = 0; i < 54; i++) {
            if (i == 0 || i == 8 || i == 45 || i == 53) {
                inv.setItem(i, new GUIItemBuilder(Material.PURPLE_STAINED_GLASS_PANE).name("<gradient:#9D50BB:#6E48AA><b>✦ Sovereign Seal</b></gradient>").build());
            } else if (i < 9 || i >= 45 || i % 9 == 0 || i % 9 == 8) {
                inv.setItem(i, new GUIItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name("<gray> </gray>").build());
            } else if (i >= 18 && i <= 26) {
                inv.setItem(i, new GUIItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name("<gray> </gray>").build());
            }
        }

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

        inv.setItem(22, new GUIItemBuilder(Material.LEVER).name("<gradient:#FF416C:#FF4B2B><b>⚡ Sovereign Debug Forge (18 Flags)</b></gradient>")
                .lore("<gray>▪ Toggle 18 surgical realm diagnostic flags in real-time</gray>", "", "<yellow>▶ Click to open Debug Forge</yellow>").build());

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
        int listingCooldown = settingsManager.getInt("auction.listing_cooldown_sec", 0);

        fillBorder27(inv, Material.ORANGE_STAINED_GLASS_PANE);

        inv.setItem(10, new GUIItemBuilder(Material.GOLD_NUGGET).name("<yellow><b>Listing Fee: $" + fee + " Gold</b></yellow>").lore("<gray>▶ Click to edit in chat</gray>").build());
        inv.setItem(12, new GUIItemBuilder(Material.CLOCK).name("<yellow><b>Listing Duration: " + duration + " Hours</b></yellow>").lore("<gray>▶ Click to edit in chat</gray>").build());
        inv.setItem(14, new GUIItemBuilder(Material.DIAMOND_BLOCK).name("<green><b>Max Listing Price: $" + String.format("%,d", maxPrice) + " Gold</b></green>").lore("<gray>▶ Click to edit in chat</gray>").build());
        inv.setItem(16, new GUIItemBuilder(Material.REPEATER).name("<gold><b>Listing Purchase Delay: " + listingCooldown + "s</b></gold>").lore("<gray>▶ Click to edit in chat</gray>").build());
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
        Inventory inv = Bukkit.createInventory(new AuctionGUIHolder(page, category, searchQuery), 54, TextUtil.format("<gradient:#FFD700:#FFA500><b>📜 GRAND BAZAAR OF THE REALM</b></gradient> <gray>(Page " + page + ")</gray>"));

        // Row 1: Mythic Category Banner Buttons (Slots 0-8)
        inv.setItem(0, createCategoryButton(Material.NETHER_STAR, "⭐ ALL RELICS", category.equalsIgnoreCase("ALL")));
        inv.setItem(1, createCategoryButton(Material.NETHERITE_SWORD, "🗡 WAR WEAPONS", category.equalsIgnoreCase("WEAPONS")));
        inv.setItem(2, createCategoryButton(Material.NETHERITE_CHESTPLATE, "🛡 ROYAL ARMOR", category.equalsIgnoreCase("ARMOR")));
        inv.setItem(3, createCategoryButton(Material.DIAMOND_PICKAXE, "⛏ ANCIENT TOOLS", category.equalsIgnoreCase("TOOLS")));
        inv.setItem(4, createCategoryButton(Material.DARK_OAK_LOG, "🧱 CASTLE BLOCKS", category.equalsIgnoreCase("BLOCKS")));
        inv.setItem(5, createCategoryButton(Material.BREWING_STAND, "🧪 ALCHEMY ELIXIRS", category.equalsIgnoreCase("POTIONS")));
        inv.setItem(6, createCategoryButton(Material.SHULKER_BOX, "📦 VAULT SHULKERS", category.equalsIgnoreCase("SHULKERS")));
        inv.setItem(7, new GUIItemBuilder(Material.COMPASS).name("<yellow><b>🔍 Search Chronicle</b></yellow>").lore("<gray>Filter by name or material type</gray>", "", "<yellow>▶ Click to set filter query</yellow>").build());
        inv.setItem(8, new GUIItemBuilder(Material.HOPPER).name("<gold><b>📊 Treasury Sort Order</b></gold>").lore("<gray>Cycle sorting criteria</gray>").build());

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
                currentLore.add(TextUtil.format("<gray>▪ Price: </gray><gradient:#00FF87:#60EFFF><b>$" + String.format("%,d", item.getPrice()) + " Gold</b></gradient>"));

                if (!item.isPurchasable()) {
                    currentLore.add(TextUtil.format("<gold>⏳ Cooldown: " + item.getRemainingCooldownSec() + "s remaining</gold>"));
                } else {
                    currentLore.add(TextUtil.format("<yellow>▶ Click to Purchase Relic</yellow>"));
                }

                if (display.getType().name().contains("SHULKER_BOX")) {
                    currentLore.add(TextUtil.format("<aqua>✦ Right-Click to Inspect Shulker Contents</aqua>"));
                }

                meta.lore(currentLore);
                display.setItemMeta(meta);
            }

            inv.setItem(slot++, display);
        }

        // Fill empty active listing slots with subtle dark pane structure if empty
        while (slot < 45) {
            inv.setItem(slot++, new GUIItemBuilder(Material.AIR).build());
        }

        // Row 6: Control & Stash Buttons (Slots 45-53)
        inv.setItem(45, new GUIItemBuilder(Material.PLAYER_HEAD).name("<gradient:#FFD700:#FFA500><b>👤 Personal Offerings</b></gradient>").lore("<gray>View & reclaim your active listings</gray>", "", "<yellow>▶ Click to open</yellow>").build());
        if (page > 1) {
            inv.setItem(48, new GUIItemBuilder(Material.ARROW).name("<yellow><b>◀ Previous Page (" + (page - 1) + ")</b></yellow>").build());
        } else {
            inv.setItem(48, new GUIItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name("<gray> </gray>").build());
        }

        inv.setItem(49, new GUIItemBuilder(Material.ENDER_CHEST).name("<gradient:#9D50BB:#6E48AA><b>📦 Expired Stash & Overflow</b></gradient>").lore("<gray>Reclaim unsold items and gold refunds</gray>", "", "<yellow>▶ Click to open stash</yellow>").build());

        if (endIndex < filtered.size()) {
            inv.setItem(50, new GUIItemBuilder(Material.ARROW).name("<yellow><b>Next Page (" + (page + 1) + ") ▶</b></yellow>").build());
        } else {
            inv.setItem(50, new GUIItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name("<gray> </gray>").build());
        }

        inv.setItem(53, new GUIItemBuilder(Material.EMERALD).name("<gradient:#00FF87:#60EFFF><b>➕ Offer Relic to Bazaar</b></gradient>").lore("<gray>Hold an item in hand and type:</gray>", "<white>/ah sell <price></white>").build());

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
}

