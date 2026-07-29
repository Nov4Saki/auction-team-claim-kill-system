package com.guildcore.database;

import java.sql.Connection;
import java.sql.Statement;

public class DatabaseSetup {

    public static void initTables(Connection conn) throws Exception {
        try (Statement stmt = conn.createStatement()) {
            // Enable WAL mode & foreign keys
            stmt.execute("PRAGMA journal_mode=WAL;");
            stmt.execute("PRAGMA foreign_keys=ON;");

            // Players
            stmt.execute("CREATE TABLE IF NOT EXISTS players (" +
                    "uuid VARCHAR(36) PRIMARY KEY, " +
                    "username VARCHAR(16) NOT NULL, " +
                    "coins BIGINT DEFAULT 100, " +
                    "claim_blocks INT DEFAULT 5, " +
                    "kills INT DEFAULT 0, " +
                    "deaths INT DEFAULT 0, " +
                    "kill_streak INT DEFAULT 0, " +
                    "best_streak INT DEFAULT 0, " +
                    "team_id INT DEFAULT NULL, " +
                    "first_join TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "last_seen TIMESTAMP DEFAULT CURRENT_TIMESTAMP);");

            // Economy log
            stmt.execute("CREATE TABLE IF NOT EXISTS economy_log (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "player VARCHAR(36) NOT NULL, " +
                    "amount BIGINT NOT NULL, " +
                    "reason VARCHAR(64) NOT NULL, " +
                    "target VARCHAR(36), " +
                    "timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP);");

            // Mob kills
            stmt.execute("CREATE TABLE IF NOT EXISTS mob_kills (" +
                    "player_uuid VARCHAR(36), " +
                    "mob_type VARCHAR(32), " +
                    "count INT DEFAULT 0, " +
                    "PRIMARY KEY (player_uuid, mob_type));");

            // Bounties
            stmt.execute("CREATE TABLE IF NOT EXISTS bounties (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "target_uuid VARCHAR(36) NOT NULL, " +
                    "placer_uuid VARCHAR(36) NOT NULL, " +
                    "amount BIGINT NOT NULL, " +
                    "placed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "expires_at TIMESTAMP NOT NULL, " +
                    "collected BOOLEAN DEFAULT 0);");

            // Full-Chunk Claims
            stmt.execute("CREATE TABLE IF NOT EXISTS claims (" +
                    "world VARCHAR(64), " +
                    "chunk_x INT, " +
                    "chunk_z INT, " +
                    "owner_uuid VARCHAR(36), " +
                    "team_id INT, " +
                    "flags VARCHAR(255) DEFAULT '', " +
                    "claimed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "PRIMARY KEY (world, chunk_x, chunk_z));");

            // Claim Trust
            stmt.execute("CREATE TABLE IF NOT EXISTS claim_trust (" +
                    "world VARCHAR(64), " +
                    "chunk_x INT, " +
                    "chunk_z INT, " +
                    "player_uuid VARCHAR(36), " +
                    "trust_level VARCHAR(16) DEFAULT 'ACCESS', " +
                    "PRIMARY KEY (world, chunk_x, chunk_z, player_uuid));");

            // Teams
            stmt.execute("CREATE TABLE IF NOT EXISTS teams (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "name VARCHAR(32) UNIQUE NOT NULL, " +
                    "leader_uuid VARCHAR(36) NOT NULL, " +
                    "level INT DEFAULT 1, " +
                    "exp BIGINT DEFAULT 0, " +
                    "bank_balance BIGINT DEFAULT 0, " +
                    "max_members INT DEFAULT 3, " +
                    "max_claims INT DEFAULT 5, " +
                    "home_world VARCHAR(64), " +
                    "home_x DOUBLE, home_y DOUBLE, home_z DOUBLE, " +
                    "home_yaw FLOAT, home_pitch FLOAT, " +
                    "nexus_world VARCHAR(64), " +
                    "nexus_x INT, nexus_y INT, nexus_z INT, " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP);");

            // Team Members
            stmt.execute("CREATE TABLE IF NOT EXISTS team_members (" +
                    "team_id INT, " +
                    "player_uuid VARCHAR(36) PRIMARY KEY, " +
                    "role VARCHAR(16) DEFAULT 'RECRUIT', " +
                    "joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE);");

            // Team Permissions
            stmt.execute("CREATE TABLE IF NOT EXISTS team_permissions (" +
                    "team_id INT, " +
                    "role VARCHAR(16), " +
                    "permission_node VARCHAR(32), " +
                    "allowed BOOLEAN DEFAULT 1, " +
                    "PRIMARY KEY (team_id, role, permission_node));");

            // Team Bank Log
            stmt.execute("CREATE TABLE IF NOT EXISTS team_bank_log (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "team_id INT NOT NULL, " +
                    "player VARCHAR(36) NOT NULL, " +
                    "amount BIGINT NOT NULL, " +
                    "action VARCHAR(16) NOT NULL, " +
                    "timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP);");

            // Team Vaults
            stmt.execute("CREATE TABLE IF NOT EXISTS team_vaults (" +
                    "team_id INT, " +
                    "page INT, " +
                    "inventory_data TEXT, " +
                    "PRIMARY KEY (team_id, page));");

            // Team Vault Log
            stmt.execute("CREATE TABLE IF NOT EXISTS team_vault_log (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "team_id INT NOT NULL, " +
                    "player_uuid VARCHAR(36) NOT NULL, " +
                    "item_type VARCHAR(64) NOT NULL, " +
                    "quantity INT NOT NULL, " +
                    "action VARCHAR(8) NOT NULL, " +
                    "timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP);");

            // Auction Items (with seller_name)
            stmt.execute("CREATE TABLE IF NOT EXISTS auction_items (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "seller_uuid VARCHAR(36) NOT NULL, " +
                    "seller_name VARCHAR(36) DEFAULT 'Unknown', " +
                    "category VARCHAR(32) NOT NULL, " +
                    "price BIGINT NOT NULL, " +
                    "is_bid BOOLEAN DEFAULT 0, " +
                    "current_bid BIGINT DEFAULT 0, " +
                    "bidder_uuid VARCHAR(36), " +
                    "item_data TEXT NOT NULL, " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "expires_at TIMESTAMP NOT NULL, " +
                    "is_sold BOOLEAN DEFAULT 0, " +
                    "is_expired BOOLEAN DEFAULT 0, " +
                    "is_claimed BOOLEAN DEFAULT 0);");

            // Migration column check for seller_name if table exists
            try {
                stmt.execute("ALTER TABLE auction_items ADD COLUMN seller_name VARCHAR(36) DEFAULT 'Unknown';");
            } catch (Exception ignored) {}

            // Auction Stash (for inventory overflow / expired items)
            stmt.execute("CREATE TABLE IF NOT EXISTS auction_stash (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "player_uuid VARCHAR(36) NOT NULL, " +
                    "item_data TEXT NOT NULL, " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP);");

            // Raid Shields
            stmt.execute("CREATE TABLE IF NOT EXISTS raid_shields (" +
                    "team_id INT PRIMARY KEY, " +
                    "expires_at TIMESTAMP NOT NULL);");

            // Raid Log
            stmt.execute("CREATE TABLE IF NOT EXISTS raid_log (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "attacker_team INT NOT NULL, " +
                    "defender_team INT NOT NULL, " +
                    "result VARCHAR(16) NOT NULL, " +
                    "coins_stolen BIGINT DEFAULT 0, " +
                    "timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP);");

            // Admin Settings
            stmt.execute("CREATE TABLE IF NOT EXISTS settings (" +
                    "key VARCHAR(64) PRIMARY KEY, " +
                    "value TEXT NOT NULL);");

            // Default Settings Inserts
            stmt.execute("INSERT OR IGNORE INTO settings VALUES ('economy.starting_balance', '100');");
            stmt.execute("INSERT OR IGNORE INTO settings VALUES ('economy.pvp_kill_reward', '50');");
            stmt.execute("INSERT OR IGNORE INTO settings VALUES ('economy.sales_tax_percent', '5');");
            stmt.execute("INSERT OR IGNORE INTO settings VALUES ('combat.tag_duration', '15');");
            stmt.execute("INSERT OR IGNORE INTO settings VALUES ('combat.enderpearl_cooldown', '15');");
            stmt.execute("INSERT OR IGNORE INTO settings VALUES ('combat.windcharge_cooldown', '10');");
            stmt.execute("INSERT OR IGNORE INTO settings VALUES ('combat.riptide_enabled', 'false');");
            stmt.execute("INSERT OR IGNORE INTO settings VALUES ('combat.mace_cooldown', '12');");
            stmt.execute("INSERT OR IGNORE INTO settings VALUES ('combat.crystal_enabled', 'false');");
            stmt.execute("INSERT OR IGNORE INTO settings VALUES ('combat.anchor_enabled', 'false');");
            stmt.execute("INSERT OR IGNORE INTO settings VALUES ('claims.blocks_per_hour', '50');");
            stmt.execute("INSERT OR IGNORE INTO settings VALUES ('teams.creation_cost', '5000');");
            stmt.execute("INSERT OR IGNORE INTO settings VALUES ('teams.base_max_members', '3');");
            stmt.execute("INSERT OR IGNORE INTO settings VALUES ('raids.warmup_minutes', '5');");
            stmt.execute("INSERT OR IGNORE INTO settings VALUES ('raids.duration_minutes', '15');");
            stmt.execute("INSERT OR IGNORE INTO settings VALUES ('raids.declaration_cost', '2000');");
            stmt.execute("INSERT OR IGNORE INTO settings VALUES ('raids.nexus_max_hp', '100');");
            stmt.execute("INSERT OR IGNORE INTO settings VALUES ('auction.listing_fee', '50');");
            stmt.execute("INSERT OR IGNORE INTO settings VALUES ('auction.duration_hours', '48');");
            stmt.execute("INSERT OR IGNORE INTO settings VALUES ('auction.max_listing_price', '1000000000');");
            stmt.execute("INSERT OR IGNORE INTO settings VALUES ('auction.max_listings_default', '3');");
        }
    }
}
