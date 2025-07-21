package xyz.moorus.economy.integration;

import org.bukkit.entity.Player;
import xyz.moorus.economy.main.Economy;

import java.sql.*;

public class MedievalFactionsDatabase {

    private final Economy plugin;
    private final boolean enabled;
    private final String databaseType;
    private final String databaseUrl;
    private final String username;
    private final String password;

    public MedievalFactionsDatabase(Economy plugin) {
        this.plugin = plugin;
        this.enabled = plugin.getConfig().getBoolean("medieval_factions.enabled", true);
        this.databaseType = plugin.getConfig().getString("medieval_factions.database.type", "h2");
        this.databaseUrl = plugin.getConfig().getString("medieval_factions.database.url",
                "jdbc:h2:./medieval_factions_db;AUTO_SERVER=true;MODE=MYSQL;DATABASE_TO_UPPER=false");
        this.username = plugin.getConfig().getString("medieval_factions.database.username", "sa");
        this.password = plugin.getConfig().getString("medieval_factions.database.password", "");

        if (enabled) {
            if (checkDatabase()) {
                plugin.getLogger().info("Medieval Factions интеграция активна!");
            } else {
                plugin.getLogger().warning("Medieval Factions база данных недоступна!");
            }
        }
    }

    public boolean isEnabled() {
        return enabled && checkDatabase();
    }

    private boolean checkDatabase() {
        try (Connection conn = getConnection()) {
            return conn != null && !conn.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }

    private Connection getConnection() throws SQLException {
        switch (databaseType.toLowerCase()) {
            case "h2":
                return DriverManager.getConnection(databaseUrl, username, password);

            case "sqlite":
                String sqlitePath = plugin.getConfig().getString("medieval_factions.database.sqlite.path",
                        "plugins/MedievalFactions/database.db");
                return DriverManager.getConnection("jdbc:sqlite:" + sqlitePath);

            case "mysql":
                String host = plugin.getConfig().getString("medieval_factions.database.mysql.host", "localhost");
                int port = plugin.getConfig().getInt("medieval_factions.database.mysql.port", 3306);
                String database = plugin.getConfig().getString("medieval_factions.database.mysql.database", "medievalfactions");
                String mysqlUsername = plugin.getConfig().getString("medieval_factions.database.mysql.username", "root");
                String mysqlPassword = plugin.getConfig().getString("medieval_factions.database.mysql.password", "");

                String mysqlUrl = "jdbc:mysql://" + host + ":" + port + "/" + database;
                return DriverManager.getConnection(mysqlUrl, mysqlUsername, mysqlPassword);

            default:
                throw new SQLException("Неподдерживаемый тип базы данных: " + databaseType);
        }
    }

    public String getPlayerFactionId(Player player) {
        if (!isEnabled()) return null;

        try (Connection conn = getConnection()) {
            // Проверяем существует ли игрок в mf_player
            String sql = "SELECT id FROM mf_player WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, player.getUniqueId().toString());

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        // Ищем фракцию в mf_faction_member
                        return getFactionIdFromMember(player.getUniqueId().toString());
                    }
                }
            }
        } catch (SQLException e) {
            // Игнорируем ошибки
        }

        return null;
    }

    private String getFactionIdFromMember(String playerId) {
        try (Connection conn = getConnection()) {
            String sql = "SELECT faction_id FROM mf_faction_member WHERE player_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, playerId);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("faction_id");
                    }
                }
            }
        } catch (SQLException e) {
            // Игнорируем ошибки
        }

        return null;
    }

    public String getFactionName(String factionId) {
        if (!isEnabled() || factionId == null) return null;

        try (Connection conn = getConnection()) {
            String sql = "SELECT name FROM mf_faction WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, factionId);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("name");
                    }
                }
            }
        } catch (SQLException e) {
            // Игнорируем ошибки
        }

        return "Unknown Faction";
    }

    public boolean hasCurrencyManagePermission(Player player) {
        if (!isEnabled()) return false;

        // Если игрок во фракции, то имеет права на создание валюты
        return getPlayerFactionId(player) != null;
    }

    public boolean isPlayerInFaction(Player player) {
        return getPlayerFactionId(player) != null;
    }
}