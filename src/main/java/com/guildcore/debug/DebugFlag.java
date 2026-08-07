package com.guildcore.debug;

public enum DebugFlag {
    ECONOMY_TRANSACTIONS,  // 1. Log all deposits, withdrawals, and tax deductions
    CLAIM_PROTECTION,      // 2. Log blocked block breaks/places in protected chunks
    PISTON_EVENTS,         // 3. Log piston extend/retract cancellations
    HOPPER_EVENTS,         // 4. Log cross-chunk hopper item movement cancellations
    COMBAT_TAGGING,        // 5. Log application and expiration of combat tags
    ITEM_DISABLE,          // 6. Log blocked usage of globally or combat-disabled items
    ITEM_COOLDOWN,         // 7. Log custom item cooldown triggers and remaining time
    DATABASE_WRITES,       // 8. Log async DB task queuing, execution time, and retries
    SCHEDULER_ROUTING,     // 9. Log Folia/Bukkit thread routing decisions
    AUCTION_PURCHASES,     // 10. Log race condition checks, successes, and refunds
    VAULT_SERIALIZATION,   // 11. Log BLOB serialize/deserialize sizes
    SCOREBOARD_UPDATES,    // 12. Log scoreboard line rebuilds and objective creations
    TEAM_UPGRADES,         // 13. Log team cap increases, bank upgrades, and EXP gains
    BOUNTY_COLLECTION,     // 14. Log bounty payouts, stack collections, and expiry refunds
    MOB_SPAWN_GATING,      // 15. Log why a mob kill was denied a reward
    ANTI_LOGOUT,           // 16. Log combat logout attempts, cancellations, and punishments
    GUI_CLICKS,            // 17. Log all inventory click events for troubleshooting
    GUILD_CORE,            // 18. Log core placement, damage, destruction, and tier upgrades
    OFFLINE_SHIELD,        // 19. Log shield charge accumulation, activation, drain, and depletion
    RAID_TAG,              // 20. Log raid tag triggers, exit timers, and combat log penalties
    RAID_ITEMS,            // 21. Log lock pick attempts, TNT placement, creeper spawns, sledge hits
    LOCK_PICK              // 22. Log detailed lock pick success/fail rolls and durability changes
}