package com.guildcore.raids;

import com.guildcore.claims.ClaimInfo;
import com.guildcore.claims.ClaimManager;
import com.guildcore.config.SettingsManager;
import com.guildcore.debug.DebugFlag;
import com.guildcore.debug.DebugManager;
import com.guildcore.scheduler.SchedulerWrapper;
import com.guildcore.teams.Team;
import com.guildcore.teams.TeamBankManager;
import com.guildcore.teams.TeamManager;
import com.guildcore.util.SoundUtil;
import com.guildcore.util.TextUtil;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class RaidManager {
    private final TeamManager teamManager;
    private final TeamBankManager bankManager;
    private final ClaimManager claimManager;
    private final SettingsManager settingsManager;
    private final SchedulerWrapper scheduler;
    private final RaidRollbackEngine rollbackEngine;

    public enum RaidState {
        WARMUP,
        ACTIVE,
        FINISHED
    }

    public static class ActiveRaidSession {
        public int attackerTeamId;
        public int defenderTeamId;
        public RaidState state;
        public long endTimeMs;
        public int nexusHp;
        public BossBar bossBar;
        public List<RaidRollbackEngine.RollbackEntry> rollbackQueue = new CopyOnWriteArrayList<>();

        public ActiveRaidSession(int attackerTeamId, int defenderTeamId, long durationMs, int nexusHp) {
            this.attackerTeamId = attackerTeamId;
            this.defenderTeamId = defenderTeamId;
            this.state = RaidState.WARMUP;
            this.endTimeMs = System.currentTimeMillis() + durationMs;
            this.nexusHp = nexusHp;
        }
    }

    // Key: defenderTeamId -> ActiveRaidSession
    private final Map<Integer, ActiveRaidSession> activeRaids = new ConcurrentHashMap<>();

    public RaidManager(TeamManager teamManager, TeamBankManager bankManager, ClaimManager claimManager, SettingsManager settingsManager, SchedulerWrapper scheduler, RaidRollbackEngine rollbackEngine) {
        this.teamManager = teamManager;
        this.bankManager = bankManager;
        this.claimManager = claimManager;
        this.settingsManager = settingsManager;
        this.scheduler = scheduler;
        this.rollbackEngine = rollbackEngine;
    }

    public boolean declareRaid(Player attackerPlayer, Team defenderTeam) {
        Team attackerTeam = teamManager.getPlayerTeam(attackerPlayer.getUniqueId());
        if (attackerTeam == null || defenderTeam == null) return false;
        if (attackerTeam.getId() == defenderTeam.getId()) return false;
        if (activeRaids.containsKey(defenderTeam.getId())) return false;

        long cost = settingsManager.getLong("raids.declaration_cost", 2000);
        if (!bankManager.withdraw(attackerTeam, attackerPlayer.getUniqueId(), cost)) {
            return false;
        }

        int warmupMin = settingsManager.getInt("raids.warmup_minutes", 5);
        int nexusMaxHp = settingsManager.getInt("raids.nexus_max_hp", 100);

        ActiveRaidSession session = new ActiveRaidSession(attackerTeam.getId(), defenderTeam.getId(), warmupMin * 60 * 1000L, nexusMaxHp);
        activeRaids.put(defenderTeam.getId(), session);

        BossBar bar = BossBar.bossBar(
            TextUtil.format("<red>⚔ WARMUP: " + attackerTeam.getName() + " raiding " + defenderTeam.getName() + "</red>"),
            1.0f,
            BossBar.Color.RED,
            BossBar.Overlay.PROGRESS
        );
        session.bossBar = bar;

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showBossBar(bar);
            SoundUtil.playRaidHorn(p);
        }

        DebugManager.log(DebugFlag.RAID_DAMAGE, "Raid declared by " + attackerTeam.getName() + " against " + defenderTeam.getName());
        return true;
    }

    public boolean isActiveRaidAt(Location location) {
        if (location == null) return false;
        ClaimInfo claim = claimManager.getClaimAt(location.getChunk());
        if (claim == null || !claim.isTeamClaim()) return false;

        ActiveRaidSession session = activeRaids.get(claim.getTeamId());
        return session != null && session.state == RaidState.ACTIVE;
    }

    public ActiveRaidSession getSessionForDefender(int defenderTeamId) {
        return activeRaids.get(defenderTeamId);
    }

    public void damageNexus(ActiveRaidSession session, int damage) {
        if (session == null || session.state != RaidState.ACTIVE) return;
        session.nexusHp = Math.max(0, session.nexusHp - damage);
        DebugManager.log(DebugFlag.RAID_DAMAGE, "Nexus hit! HP remaining: " + session.nexusHp);

        if (session.nexusHp <= 0) {
            finishRaid(session, true);
        }
    }

    public void finishRaid(ActiveRaidSession session, boolean attackerWon) {
        session.state = RaidState.FINISHED;
        if (session.bossBar != null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.hideBossBar(session.bossBar);
            }
        }

        Team attacker = teamManager.getTeam(session.attackerTeamId);
        Team defender = teamManager.getTeam(session.defenderTeamId);

        if (attackerWon && attacker != null && defender != null) {
            long stolen = (long) (defender.getBankBalance() * 0.25);
            defender.setBankBalance(defender.getBankBalance() - stolen);
            attacker.setBankBalance(attacker.getBankBalance() + stolen);

            Bukkit.broadcast(TextUtil.format("<gold>🏆 RAID VICTORY! <red>" + attacker.getName() + "</red> raided <yellow>" + defender.getName() + "</yellow> and stole $" + stolen + "!</gold>"));
        } else if (defender != null) {
            Bukkit.broadcast(TextUtil.format("<green>🛡 RAID DEFENDED! <yellow>" + defender.getName() + "</yellow> successfully repelled the raid!</green>"));
        }

        rollbackEngine.rollbackBlocks(session.rollbackQueue);
        activeRaids.remove(session.defenderTeamId);
    }
}
