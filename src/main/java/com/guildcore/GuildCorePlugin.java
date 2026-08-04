package com.guildcore;

import com.guildcore.auction.AuctionManager;
import com.guildcore.claims.ClaimManager;
import com.guildcore.claims.ClaimProtectionListener;
import com.guildcore.claims.ClaimVisualizer;
import com.guildcore.combat.CombatTagManager;
import com.guildcore.combat.ItemControlManager;
import com.guildcore.commands.*;
import com.guildcore.config.SettingsManager;
import com.guildcore.crates.CrateCommand;
import com.guildcore.crates.CrateManager;
import com.guildcore.database.DatabaseManager;
import com.guildcore.economy.EconomyListener;
import com.guildcore.economy.EconomyManager;
import com.guildcore.gui.ChatInputListener;
import com.guildcore.gui.GUIClickListener;
import com.guildcore.gui.GUIManager;
import com.guildcore.items.ProhibitedItemListener;
import com.guildcore.items.ProhibitedItemManager;
import com.guildcore.raids.RaidManager;
import com.guildcore.raids.RaidNexusListener;
import com.guildcore.raids.RaidRollbackEngine;
import com.guildcore.scheduler.SchedulerWrapper;
import com.guildcore.scoreboard.ScoreboardManager;
import com.guildcore.shop.ShopCommand;
import com.guildcore.shop.ShopManager;
import com.guildcore.stats.BountyManager;
import com.guildcore.stats.StatsManager;
import com.guildcore.teams.*;
import com.guildcore.trade.TradeManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public class GuildCorePlugin extends JavaPlugin implements Listener {

    private static GuildCorePlugin instance;

    private SchedulerWrapper scheduler;
    private DatabaseManager databaseManager;
    private SettingsManager settingsManager;
    private EconomyManager economyManager;
    private StatsManager statsManager;
    private BountyManager bountyManager;
    private CombatTagManager combatTagManager;
    private ItemControlManager itemControlManager;
    private ClaimManager claimManager;
    private ClaimVisualizer claimVisualizer;
    private TeamManager teamManager;
    private TeamBankManager teamBankManager;
    private TeamVaultManager teamVaultManager;
    private TeamUpgradeManager teamUpgradeManager;
    private TeamPermissionManager teamPermissionManager;
    private RaidRollbackEngine raidRollbackEngine;
    private RaidManager raidManager;
    private AuctionManager auctionManager;
    private ProhibitedItemManager prohibitedItemManager;
    private ScoreboardManager scoreboardManager;
    private GUIManager guiManager;
    private TradeManager tradeManager;
    private CrateManager crateManager;
    private ShopManager shopManager;

    public static GuildCorePlugin getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        getLogger().info("Initializing GuildCore v5 (Modular Crates & Server Shop Engine)...");

        // 1. Scheduler Wrapper
        this.scheduler = new SchedulerWrapper(this);

        // 2. Database & Config
        this.databaseManager = new DatabaseManager(this);
        try {
            this.databaseManager.initialize();
        } catch (Exception e) {
            getLogger().severe("Failed to initialize database: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.settingsManager = new SettingsManager(databaseManager);
        this.settingsManager.loadSettings();

        // 3. Economy & Stats
        this.economyManager = new EconomyManager(databaseManager);
        this.statsManager = new StatsManager(databaseManager);
        this.bountyManager = new BountyManager(databaseManager, economyManager);
        this.bountyManager.loadBounties();

        // 4. Combat & Items
        this.combatTagManager = new CombatTagManager(settingsManager, scheduler);
        this.itemControlManager = new ItemControlManager(combatTagManager, settingsManager, scheduler);

        // 5. Full-Chunk Claims
        this.claimManager = new ClaimManager(databaseManager);
        this.claimManager.loadClaims();
        this.claimVisualizer = new ClaimVisualizer(claimManager, scheduler);

        // 6. Teams
        this.teamManager = new TeamManager(databaseManager);
        this.teamManager.loadTeams();
        this.teamBankManager = new TeamBankManager(databaseManager, economyManager);
        this.teamVaultManager = new TeamVaultManager(databaseManager);
        this.scheduler.runTaskTimer(() -> {
            if (!this.isEnabled()) return false;
            if (this.teamVaultManager != null) {
                this.teamVaultManager.saveAllVaultsSync();
            }
            return true;
        }, 1200L, 1200L);
        this.teamUpgradeManager = new TeamUpgradeManager(databaseManager);
        this.teamPermissionManager = new TeamPermissionManager(databaseManager);
        this.teamPermissionManager.loadPermissions();

        // 7. Raids, Trade, Crates & Shop
        this.raidRollbackEngine = new RaidRollbackEngine(scheduler);
        this.raidManager = new RaidManager(teamManager, teamBankManager, claimManager, settingsManager, scheduler, raidRollbackEngine);
        this.teamManager.setClaimManager(claimManager);
        this.teamManager.setSettingsManager(settingsManager);
        this.claimManager.setTeamManager(teamManager);
        this.claimManager.setPermissionManager(teamPermissionManager);
        this.combatTagManager.setTeamManager(teamManager);
        this.tradeManager = new TradeManager();
        this.crateManager = new CrateManager(databaseManager, scheduler);
        this.crateManager.loadCrates();
        this.shopManager = new ShopManager(databaseManager, economyManager, scheduler);
        this.shopManager.loadShop();

        // 8. Auction House & Prohibited Items
        this.auctionManager = new AuctionManager(databaseManager, economyManager, settingsManager);
        this.auctionManager.loadAuctions();

        this.prohibitedItemManager = new ProhibitedItemManager(databaseManager);
        this.prohibitedItemManager.loadProhibitedItems();

        // 9. Scoreboard & GUIs
        this.scoreboardManager = new ScoreboardManager(economyManager, statsManager, bountyManager, teamManager, claimManager, combatTagManager, raidManager, settingsManager, scheduler);
        this.scoreboardManager.startUpdateTask();

        this.guiManager = new GUIManager(settingsManager, teamManager, claimManager, auctionManager, statsManager, teamPermissionManager);
        this.guiManager.setProhibitedItemManager(prohibitedItemManager);
        this.guiManager.setShopManager(shopManager);
        this.guiManager.setEconomyManager(economyManager);
        this.guiManager.setTeamBankManager(teamBankManager);
        this.guiManager.setTeamVaultManager(teamVaultManager);
        this.guiManager.setScheduler(scheduler);

        // Register Event Listeners
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(this, this);
        pm.registerEvents(new EconomyListener(economyManager, settingsManager), this);
        pm.registerEvents(combatTagManager, this);
        pm.registerEvents(itemControlManager, this);
        ClaimProtectionListener claimProtectionListener = new ClaimProtectionListener(claimManager, settingsManager);
        claimProtectionListener.setTeamManager(teamManager);
        pm.registerEvents(claimProtectionListener, this);
        pm.registerEvents(new RaidNexusListener(raidManager, claimManager), this);
        GUIClickListener guiClickListener = new GUIClickListener(guiManager, auctionManager, teamManager, teamUpgradeManager, teamVaultManager, economyManager, settingsManager, scoreboardManager, crateManager, shopManager, scheduler);
        guiClickListener.setProhibitedItemManager(prohibitedItemManager);
        pm.registerEvents(guiClickListener, this);
        pm.registerEvents(new ChatInputListener(settingsManager, scheduler), this);
        pm.registerEvents(new ProhibitedItemListener(prohibitedItemManager), this);

        // Register Commands & Tab Completers
        CoinsCommand coinsCmd = new CoinsCommand(economyManager);
        registerCmd("coins", coinsCmd);
        registerCmd("pay", coinsCmd);
        registerCmd("eco", coinsCmd);

        ClaimCommand claimCmd = new ClaimCommand(claimManager, claimVisualizer, guiManager);
        registerCmd("claim", claimCmd);
        registerCmd("unclaim", claimCmd);
        registerCmd("trust", claimCmd);
        registerCmd("untrust", claimCmd);

        TeamCommand teamCmd = new TeamCommand(teamManager, teamBankManager, teamVaultManager, claimManager, raidManager, settingsManager, guiManager);
        registerCmd("team", teamCmd);
        registerCmd("tc", teamCmd);

        AuctionCommand ahCmd = new AuctionCommand(auctionManager, guiManager);
        registerCmd("ah", ahCmd);

        StatsCommand statsCmd = new StatsCommand(statsManager, bountyManager);
        registerCmd("stats", statsCmd);
        registerCmd("bounty", statsCmd);

        DebugCommand debugCmd = new DebugCommand();
        registerCmd("guildcore", debugCmd);

        SettingsCommand settingsCmd = new SettingsCommand(guiManager);
        registerCmd("settings", settingsCmd);

        TeleportCommand tpCmd = new TeleportCommand(databaseManager, settingsManager, scheduler, guiManager);
        registerCmd("tpa", tpCmd);
        registerCmd("tpaccept", tpCmd);
        registerCmd("tpdeny", tpCmd);
        registerCmd("rtp", tpCmd);
        registerCmd("spawn", tpCmd);
        registerCmd("setspawn", tpCmd);
        registerCmd("sethome", tpCmd);
        registerCmd("home", tpCmd);
        registerCmd("delhome", tpCmd);
        registerCmd("warp", tpCmd);
        registerCmd("setwarp", tpCmd);
        registerCmd("delwarp", tpCmd);

        TradeCommand tradeCmd = new TradeCommand(tradeManager);
        registerCmd("trade", tradeCmd);

        CrateCommand crateCmd = new CrateCommand(crateManager);
        registerCmd("crate", crateCmd);

        ShopCommand shopCmd = new ShopCommand(shopManager, guiManager);
        registerCmd("shop", shopCmd);

        getLogger().info("GuildCore v5 loaded successfully with Prohibited Items Engine, RTP World Selector, and Shop Admin Forge!");
    }

    private void registerCmd(String name, org.bukkit.command.TabExecutor executor) {
        var cmd = getCommand(name);
        if (cmd != null) {
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        }
    }

    @Override
    public void onDisable() {
        if (teamVaultManager != null) {
            for (Player player : getServer().getOnlinePlayers()) {
                if (player.getOpenInventory() != null && player.getOpenInventory().getTopInventory() != null) {
                    var top = player.getOpenInventory().getTopInventory();
                    if (top.getHolder() instanceof com.guildcore.gui.holders.VaultGUIHolder vaultHolder) {
                        org.bukkit.inventory.ItemStack[] contents = top.getContents();
                        if (contents.length > 53 && contents[53] != null && contents[53].getType() == org.bukkit.Material.BARRIER) {
                            contents[53] = null;
                        }
                        teamVaultManager.saveVaultPageSync(vaultHolder.getTeamId(), vaultHolder.getPage(), contents);
                    }
                }
            }
            teamVaultManager.saveAllVaultsSync();
        }
        if (databaseManager != null) {
            databaseManager.shutdown();
        }
        getLogger().info("GuildCore disabled cleanly.");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        economyManager.loadPlayer(player.getUniqueId(), player.getName());
        scheduler.runSync(player, () -> scoreboardManager.updateBoard(player));
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        if (killer != null && !killer.equals(victim)) {
            if (statsManager != null) {
                statsManager.recordKill(killer.getUniqueId(), victim.getUniqueId());
            }
            if (bountyManager != null) {
                long bounty = bountyManager.claimBounty(killer.getUniqueId(), victim.getUniqueId());
                if (bounty > 0) {
                    killer.sendMessage("§a[Bounty] Claimed $" + bounty + " bounty on " + victim.getName() + "!");
                }
            }
        }
    }
}
