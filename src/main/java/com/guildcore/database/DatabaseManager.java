package com.guildcore.database;

import com.guildcore.GuildCorePlugin;
import com.guildcore.debug.DebugFlag;
import com.guildcore.debug.DebugManager;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class DatabaseManager {
    private final GuildCorePlugin plugin;
    private HikariDataSource dataSource;
    private final ExecutorService asyncDbExecutor;

    public DatabaseManager(GuildCorePlugin plugin) {
        this.plugin = plugin;
        this.asyncDbExecutor = Executors.newSingleThreadExecutor(r -> new Thread(r, "GuildCore-DB-Writer"));
    }

    public void initialize() throws Exception {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        File dbFile = new File(dataFolder, "data.db");
        String jdbcUrl = "jdbc:sqlite:" + dbFile.getAbsolutePath();

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setDriverClassName("org.sqlite.JDBC");
        config.setMaximumPoolSize(5);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);

        this.dataSource = new HikariDataSource(config);

        try (Connection conn = getConnection()) {
            DatabaseSetup.initTables(conn);
        }

        DebugManager.log(DebugFlag.DATABASE_WRITES, "Database initialized successfully at " + dbFile.getAbsolutePath());
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public void executeAsync(Runnable task) {
        asyncDbExecutor.submit(() -> {
            try {
                long start = System.currentTimeMillis();
                task.run();
                long duration = System.currentTimeMillis() - start;
                DebugManager.log(DebugFlag.DATABASE_WRITES, "Async DB write task executed in " + duration + "ms");
            } catch (Exception e) {
                plugin.getLogger().severe("Error executing async DB task: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    public <T> Future<T> writeSync(Supplier<T> supplier) {
        return asyncDbExecutor.submit(() -> {
            try {
                return supplier.get();
            } catch (Exception e) {
                plugin.getLogger().severe("Error executing sync DB task: " + e.getMessage());
                e.printStackTrace();
                return null;
            }
        });
    }

    public void shutdown() {
        if (asyncDbExecutor != null && !asyncDbExecutor.isShutdown()) {
            asyncDbExecutor.shutdown();
        }
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
        DebugManager.log(DebugFlag.DATABASE_WRITES, "Database pool shut down.");
    }
}
