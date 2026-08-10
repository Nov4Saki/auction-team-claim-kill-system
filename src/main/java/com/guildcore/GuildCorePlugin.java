// FILE: src/main/java/com/guildcore/GuildCorePlugin.java
package com.guildcore;

import com.guildcore.auction.AuctionManager;
import com.guildcore.claims.*;
import com.guildcore.combat.ItemControlManager;
import com.guildcore.commands.*;
import com.guildcore.config.SettingsManager;
import com.guildcore.core.GuildCoreListener;
import com.guildcore.core.GuildCoreManager;
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
import com.guildcore.raiditems.*;
import com.guildcore.raidtag.RaidTagManager;
import com.guildcore.scheduler.SchedulerWrapper;
import com.guildcore.scoreboard.ScoreboardManager;
import com.guildcore.shield.OfflineShieldManager;
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
import org.bukkit.event.player.PlayerQuitEvent;
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
    private ItemControlManager itemControlManager;
    private ClaimManager claimManager;
    private ClaimChestManager claimChestManager;
    private ClaimVisualizer claimVisualizer;
    private TeamManager teamManager;
    private TeamBankManager teamBankManager;
    private TeamVaultManager teamVaultManager;
    private TeamUpgradeManager teamUpgradeManager;
    private TeamPermissionManager teamPermissionManager;
    private GuildCoreManager guildCoreManager;
    private OfflineShieldManager offlineShieldManager;
    private RaidTagManager raidTagManager;
    private RaidItemManager raidItemManager;
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

    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public SettingsManager getSettingsManager() { return settingsManager; }
    public TeamManager getTeamManager() { return teamManager; }
    public GuildCoreManager getGuildCoreManager() { return guildCoreManager; }
    public OfflineShieldManager getOfflineShieldManager() { return offlineShieldManager; }
    public RaidTagManager getRaidTagManager() { return raidTagManager; }
    public ClaimChestManager getClaimChestManager() { return claimChestManager; }

    @Override
    public void onEnable() {
        instance = this;
        getLogger().info("Initializing GuildCore v6 (Guild Core Raid Overhaul)...");

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
        this.itemControlManager = new ItemControlManager(raidTagManager, settingsManager, scheduler);

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
            if (this.teamVaultManager != null) this.teamVaultManager.saveAllVaultsSync();
            return true;
        }, 1200L, 1200L);
        this.teamUpgradeManager = new TeamUpgradeManager(databaseManager);
        this.teamPermissionManager = new TeamPermissionManager(databaseManager);
        this.teamPermissionManager.setSettingsManager(settingsManager);
        this.teamPermissionManager.loadPermissions();

        // 7. Guild Core, Shield, Raid Tag & Raid Items
        this.guildCoreManager = new GuildCoreManager(databaseManager, claimManager, teamManager, settingsManager, scheduler);
        this.guildCoreManager.setEconomyManager(economyManager);
        this.guildCoreManager.loadAllCores();

        this.offlineShieldManager = new OfflineShieldManager(databaseManager, teamManager, settingsManager, scheduler);
        this.offlineShieldManager.loadAllShields();
        this.offlineShieldManager.startChargeTask();
        this.offlineShieldManager.startDrainTask();

        this.raidTagManager = new RaidTagManager(teamManager, claimManager, offlineShieldManager, settingsManager, scheduler);
        this.raidTagManager.startActionBarTask();

        this.raidItemManager = new RaidItemManager(settingsManager);

        // 8. Claim Chest Manager
        this.claimChestManager = new ClaimChestManager(databaseManager, teamManager, claimManager, guildCoreManager, settingsManager);
        this.claimChestManager.setScheduler(scheduler);
        this.claimChestManager.loadClaimChests();

        // Cross-wire dependencies
        this.itemControlManager.setTeamManager(teamManager);
        this.teamManager.setClaimManager(claimManager);
        this.teamManager.setSettingsManager(settingsManager);
        this.teamManager.setEconomyManager(economyManager);
        this.teamManager.setGuildCoreManager(guildCoreManager);
        this.teamManager.setClaimChestManager(claimChestManager);
        this.teamManager.setScheduler(scheduler);

        this.claimManager.setTeamManager(teamManager);
        this.claimManager.setPermissionManager(teamPermissionManager);
        this.claimManager.setGuildCoreManager(guildCoreManager);
        this.claimManager.setSettingsManager(settingsManager);

        this.tradeManager = new TradeManager(settingsManager, scheduler);
        this.crateManager = new CrateManager(databaseManager, scheduler);
        this.crateManager.loadCrates();
        this.shopManager = new ShopManager(databaseManager, economyManager, scheduler);
        this.shopManager.loadShop();

        // 9. Auction House & Prohibited Items
        this.auctionManager = new AuctionManager(databaseManager, economyManager, settingsManager);
        this.auctionManager.loadAuctions();

        this.prohibitedItemManager = new ProhibitedItemManager(databaseManager);
        this.prohibitedItemManager.loadProhibitedItems();

        // 10. Scoreboard & GUIs
        this.scoreboardManager = new ScoreboardManager(economyManager, statsManager, bountyManager, teamManager, claimManager, raidTagManager, settingsManager, scheduler);
        this.scoreboardManager.startUpdateTask();

        this.guiManager = new GUIManager(settingsManager, teamManager, claimManager, auctionManager, statsManager, teamPermissionManager);
        this.guiManager.setProhibitedItemManager(prohibitedItemManager);
        this.guiManager.setShopManager(shopManager);
        this.guiManager.setEconomyManager(economyManager);
        this.guiManager.setTeamBankManager(teamBankManager);
        this.guiManager.setTeamVaultManager(teamVaultManager);
        this.guiManager.setScheduler(scheduler);
        this.guiManager.setClaimChestManager(claimChestManager);
        this.teamVaultManager.setGuiManager(guiManager);

        // Register Event Listeners
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(this, this);
        pm.registerEvents(new EconomyListener(economyManager, settingsManager), this);
        pm.registerEvents(itemControlManager, this);

        // Claim Protection Listener
        ClaimProtectionListener claimProtectionListener = new ClaimProtectionListener(claimManager, settingsManager, claimChestManager);
        claimProtectionListener.setTeamManager(teamManager);
        claimProtectionListener.setOfflineShieldManager(offlineShieldManager);
        claimProtectionListener.setGuildCoreManager(guildCoreManager);
        claimProtectionListener.setRaidItemManager(raidItemManager);
        pm.registerEvents(claimProtectionListener, this);

        // Claim Chest Listener
        ClaimChestListener claimChestListener = new ClaimChestListener(claimChestManager, teamManager, guiManager);
        pm.registerEvents(claimChestListener, this);

        GuildCoreListener guildCoreListener = new GuildCoreListener(guildCoreManager, claimManager, teamManager);
        guildCoreListener.setGuiManager(guiManager);
        pm.registerEvents(guildCoreListener, this);
        pm.registerEvents(raidTagManager, this);

        LockPickListener lockPickListener = new LockPickListener(raidItemManager, claimManager, offlineShieldManager, settingsManager, scheduler);
        lockPickListener.setTeamManager(teamManager);
        lockPickListener.setClaimChestManager(claimChestManager);
        pm.registerEvents(lockPickListener, this);

        SledgeHammerListener sledgeHammerListener = new SledgeHammerListener(raidItemManager, guildCoreManager, offlineShieldManager, settingsManager);
        sledgeHammerListener.setRaidTagManager(raidTagManager);
        sledgeHammerListener.setTeamManager(teamManager);
        sledgeHammerListener.setClaimChestManager(claimChestManager);
        pm.registerEvents(sledgeHammerListener, this);

        RaidTNTListener raidTNTListener = new RaidTNTListener(raidItemManager, claimManager, guildCoreManager, offlineShieldManager, settingsManager, scheduler);
        raidTNTListener.setRaidTagManager(raidTagManager);
        raidTNTListener.setTeamManager(teamManager);
        pm.registerEvents(raidTNTListener, this);

        ChargedCreeperListener chargedCreeperListener = new ChargedCreeperListener(raidItemManager, claimManager, offlineShieldManager, settingsManager, scheduler);
        chargedCreeperListener.setRaidTagManager(raidTagManager);
        chargedCreeperListener.setGuildCoreManager(guildCoreManager);
        chargedCreeperListener.setTeamManager(teamManager);
        pm.registerEvents(chargedCreeperListener, this);

        GUIClickListener guiClickListener = new GUIClickListener(guiManager, auctionManager, teamManager, teamUpgradeManager, teamVaultManager, economyManager, settingsManager, scoreboardManager, crateManager, shopManager, scheduler);
        guiClickListener.setProhibitedItemManager(prohibitedItemManager);
        guiClickListener.setTradeManager(tradeManager);
        pm.registerEvents(guiClickListener, this);

        pm.registerEvents(new ChatInputListener(settingsManager, scheduler), this);
        pm.registerEvents(new ProhibitedItemListener(prohibitedItemManager), this);

        // Register Commands & Tab Completers
        CoinsCommand coinsCmd = new CoinsCommand(economyManager);
        registerCmd("coins", coinsCmd);
        registerCmd("pay", coinsCmd);
        registerCmd("eco", coinsCmd);

        ClaimCommand claimCmd = new ClaimCommand(claimManager, claimVisualizer, guiManager, claimChestManager);
        registerCmd("claim", claimCmd);
        registerCmd("unclaim", claimCmd);

        TeamCommand teamCmd = new TeamCommand(teamManager, teamBankManager, teamVaultManager, claimManager, guildCoreManager, settingsManager, guiManager, claimChestManager);
        registerCmd("guild", teamCmd);
        registerCmd("team", teamCmd);
        registerCmd("tc", teamCmd);

        AuctionCommand ahCmd = new AuctionCommand(auctionManager, guiManager);
        registerCmd("ah", ahCmd);

        StatsCommand statsCmd = new StatsCommand(statsManager, bountyManager, guiManager);
        registerCmd("stats", statsCmd);
        registerCmd("leaderboard", statsCmd);
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
        registerCmd("warp", tpCmd);
        registerCmd("setwarp", tpCmd);
        registerCmd("delwarp", tpCmd);

        TradeCommand tradeCmd = new TradeCommand(tradeManager);
        registerCmd("trade", tradeCmd);

        CrateCommand crateCmd = new CrateCommand(crateManager);
        registerCmd("crate", crateCmd);

        ShopCommand shopCmd = new ShopCommand(shopManager, guiManager);
        registerCmd("shop", shopCmd);

        getLogger().info("GuildCore v6.5 loaded successfully with Guild Core Raid Overhaul System!");
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
        if (offlineShieldManager != null) offlineShieldManager.saveAllShields();
        if (guildCoreManager != null) guildCoreManager.saveAllCores();
        if (databaseManager != null) databaseManager.shutdown();
        getLogger().info("GuildCore disabled cleanly.");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        economyManager.loadPlayerSync(player.getUniqueId(), player.getName());
        if (offlineShieldManager != null && teamManager != null) {
            com.guildcore.teams.Team playerTeam = teamManager.getPlayerTeam(player.getUniqueId());
            if (playerTeam != null) offlineShieldManager.onPlayerJoin(player.getUniqueId(), playerTeam.getId());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (offlineShieldManager != null && teamManager != null) {
            com.guildcore.teams.Team team = teamManager.getPlayerTeam(player.getUniqueId());
            if (team != null) offlineShieldManager.onPlayerQuit(player.getUniqueId(), team.getId());
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer != null && !killer.equals(victim)) {
            if (statsManager != null) statsManager.recordKill(killer.getUniqueId(), victim.getUniqueId());
            if (bountyManager != null) {
                long bounty = bountyManager.claimBounty(killer.getUniqueId(), victim.getUniqueId());
                if (bounty > 0) {
                    killer.sendMessage("§a[Bounty] Claimed $" + bounty + " bounty on " + victim.getName() + "!");
                }
            }
        }
    }
}