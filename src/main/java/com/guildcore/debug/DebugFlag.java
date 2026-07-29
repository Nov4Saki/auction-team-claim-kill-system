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
    RAID_DAMAGE,           // 12. Log nexus hits, TNT damage, and rollback queue additions
    SCOREBOARD_UPDATES,    // 13. Log scoreboard line rebuilds and objective creations
    TEAM_UPGRADES,         // 14. Log team cap increases, bank upgrades, and EXP gains
    BOUNTY_COLLECTION,     // 15. Log bounty payouts, stack collections, and expiry refunds
    MOB_SPAWN_GATING,      // 16. Log why a mob kill was denied a reward
    ANTI_LOGOUT,           // 17. Log combat logout attempts, cancellations, and punishments
    GUI_CLICKS             // 18. Log all inventory click events for troubleshooting
}
