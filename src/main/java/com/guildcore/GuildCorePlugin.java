package com.guildcore;

import com.guildcore.auction.AuctionManager;
import com.guildcore.claims.ClaimManager;
import com.guildcore.claims.ClaimProtectionListener;
import com.guildcore.claims.ClaimVisualizer;
import com.guildcore.combat.CombatTagManager;
import com.guildcore.combat.ItemControlManager;
import com.guildcore.commands.*;
import com.guildcore.config.SettingsManager;
import com.guildcore.database.DatabaseManager;
import com.guildcore.debug.DebugFlag;
import com.guildcore.debug.DebugManager;
import com.guildcore.economy.EconomyListener;
import com.guildcore.economy.EconomyManager;
import com.guildcore.gui.GUIClickListener;
import com.guildcore.gui.GUIManager;
import com.guildcore.raids.RaidManager;
import com.guildcore.raids.RaidNexusListener;
import com.guildcore.raids.RaidRollbackEngine;
import com.guildcore.scheduler.SchedulerWrapper;
import com.guildcore.scoreboard.ScoreboardManager;
import com.guildcore.stats.BountyManager;
import com.guildcore.stats.StatsManager;
import com.guildcore.teams.*;
import org.bukkit.Bukkit;
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
    private ScoreboardManager scoreboardManager;
    private GUIManager guiManager;

    public static GuildCorePlugin getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        getLogger().info("Initializing GuildCore v5 (Folia Scoreboard & Custom Item Preservation Engine)...");

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
        this.claimVisualizer = new ClaimVisualizer(claimManager);

        // 6. Teams
        this.teamManager = new TeamManager(databaseManager);
        this.teamManager.loadTeams();
        this.teamBankManager = new TeamBankManager(databaseManager);
        this.teamVaultManager = new TeamVaultManager(databaseManager);
        this.teamUpgradeManager = new TeamUpgradeManager(databaseManager);
        this.teamPermissionManager = new TeamPermissionManager(databaseManager);
        this.teamPermissionManager.loadPermissions();

        // 7. Raids
        this.raidRollbackEngine = new RaidRollbackEngine(scheduler);
        this.raidManager = new RaidManager(teamManager, teamBankManager, claimManager, settingsManager, scheduler, raidRollbackEngine);

        // 8. Auction House
        this.auctionManager = new AuctionManager(databaseManager, economyManager, settingsManager);
        this.auctionManager.loadAuctions();

        // 9. Scoreboard & GUI
        this.scoreboardManager = new ScoreboardManager(economyManager, statsManager, bountyManager, teamManager, claimManager, combatTagManager, raidManager, settingsManager, scheduler);
        this.scoreboardManager.startUpdateTask();

        this.guiManager = new GUIManager(settingsManager, teamManager, claimManager, auctionManager, statsManager, teamPermissionManager);

        // Register Event Listeners
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(this, this);
        pm.registerEvents(new EconomyListener(economyManager, settingsManager), this);
        pm.registerEvents(combatTagManager, this);
        pm.registerEvents(itemControlManager, this);
        pm.registerEvents(new ClaimProtectionListener(claimManager), this);
        pm.registerEvents(new RaidNexusListener(raidManager, claimManager), this);
        pm.registerEvents(new GUIClickListener(guiManager, auctionManager, teamManager, teamUpgradeManager, teamVaultManager, scheduler), this);

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

        getLogger().info("GuildCore v5 enabled successfully!");
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
            statsManager.recordKill(killer.getUniqueId(), victim.getUniqueId());
            long bounty = bountyManager.claimBounty(killer.getUniqueId(), victim.getUniqueId());
            if (bounty > 0) {
                killer.sendMessage("§a[Bounty] Claimed $" + bounty + " bounty on " + victim.getName() + "!");
            }
        }
    }
}
