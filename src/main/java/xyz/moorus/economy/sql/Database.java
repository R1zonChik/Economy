package xyz.moorus.economy.sql;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.file.FileConfiguration;
import xyz.moorus.economy.main.Economy;
import xyz.moorus.economy.market.Lot;
import xyz.moorus.economy.market.Order;
import xyz.moorus.economy.money.PlayerWallet;

import java.io.File;
import java.sql.*;
import java.util.*;

public class Database {

    private HikariDataSource dataSource;
    private Economy plugin;

    public Database(Economy plugin) throws SQLException {
        this.plugin = plugin;

        FileConfiguration config = plugin.getConfig();

        File dataFolder = new File(plugin.getDataFolder(), "database");
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        HikariConfig hikariConfig = new HikariConfig();

        String dbType = config.getString("database.type", "sqlite");

        if (dbType.equalsIgnoreCase("sqlite")) {
            String dbPath = new File(dataFolder, "economy.db").getAbsolutePath();
            hikariConfig.setJdbcUrl("jdbc:sqlite:" + dbPath);
            hikariConfig.setMaximumPoolSize(config.getInt("performance.database_pool_size", 5));
            hikariConfig.setMinimumIdle(1);
            hikariConfig.setConnectionTimeout(10000);
            hikariConfig.setIdleTimeout(300000);
            hikariConfig.setMaxLifetime(600000);
            plugin.getLogger().info("Используется SQLite база данных: " + dbPath);

        } else if (dbType.equalsIgnoreCase("mysql")) {
            String host = config.getString("database.host", "localhost");
            int port = config.getInt("database.port", 3306);
            String database = config.getString("database.database", "economy");
            String username = config.getString("database.username", "root");
            String password = config.getString("database.password", "");

            hikariConfig.setJdbcUrl(String.format("jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                    host, port, database));
            hikariConfig.setUsername(username);
            hikariConfig.setPassword(password);
            hikariConfig.setMaximumPoolSize(config.getInt("performance.database_pool_size", 10));
            hikariConfig.setConnectionTimeout(10000);
            hikariConfig.setIdleTimeout(300000);
            hikariConfig.setMaxLifetime(600000);
            plugin.getLogger().info("Используется MySQL база данных: " + host + ":" + port + "/" + database);
        }

        dataSource = new HikariDataSource(hikariConfig);
        createTablesIfNotExist();
        plugin.getLogger().info("База данных успешно подключена!");
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    private void createTablesIfNotExist() throws SQLException {
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement()) {

            plugin.getLogger().info("Создание таблиц базы данных...");

            // Таблица кошельков игроков
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS player_wallets (" +
                    "player_name VARCHAR(16), " +
                    "currency VARCHAR(3), " +
                    "amount BIGINT, " +
                    "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "PRIMARY KEY (player_name, currency)" +
                    ")");

            // Таблица валют
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS currencies (" +
                    "currency_name VARCHAR(3) PRIMARY KEY," +
                    "faction_id VARCHAR(36)," +
                    "emission BIGINT," +
                    "max_emission BIGINT," +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ")");

            // Таблица игроков
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS players (" +
                    "player_name VARCHAR(16) PRIMARY KEY," +
                    "uuid VARCHAR(36) UNIQUE," +
                    "first_join TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "last_seen TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ")");

            // Таблица ордеров биржи
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS orders (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "nickname VARCHAR(16) NOT NULL," +
                    "buy_currency VARCHAR(3)," +
                    "sell_currency VARCHAR(3)," +
                    "buy_amount BIGINT," +
                    "sell_amount BIGINT," +
                    "status VARCHAR(20) DEFAULT 'ACTIVE'," +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ")");

            // Таблица аукциона
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS auction_items (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "seller_name VARCHAR(16) NOT NULL," +
                    "seller_uuid VARCHAR(36) NOT NULL," +
                    "item_data TEXT NOT NULL," +
                    "currency VARCHAR(3) NOT NULL," +
                    "price BIGINT NOT NULL," +
                    "category VARCHAR(50) NOT NULL," +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "expires_at TIMESTAMP NOT NULL," +
                    "is_sold BOOLEAN DEFAULT FALSE" +
                    ")");

            // Таблица премиум магазина
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS premium_shop (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "item_data TEXT NOT NULL," +
                    "price BIGINT NOT NULL," +
                    "stock INT DEFAULT -1," +
                    "category VARCHAR(50) NOT NULL," +
                    "display_name VARCHAR(100)," +
                    "description TEXT," +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ")");

            // Таблица истории транзакций
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS transactions (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "from_player VARCHAR(16)," +
                    "to_player VARCHAR(16)," +
                    "currency VARCHAR(3)," +
                    "amount BIGINT," +
                    "transaction_type VARCHAR(20)," +
                    "description TEXT," +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ")");

            // Таблица фракций (временная система)
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS temp_factions (" +
                    "faction_id VARCHAR(36) PRIMARY KEY," +
                    "faction_name VARCHAR(50)," +
                    "owner VARCHAR(16)," +
                    "members TEXT," +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ")");

            // Таблица статистики
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS statistics (" +
                    "stat_key VARCHAR(50) PRIMARY KEY," +
                    "stat_value BIGINT," +
                    "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ")");

            // Создаем системную валюту VIL если её нет
            if (!doesCurrencyExist("VIL")) {
                addCurrency("VIL", "System", 1000000000L, 2000000000L);
                plugin.getLogger().info("Создана системная валюта VIL");
            }

            // Обновляем структуру таблиц если нужно
            updateTableStructure();

            plugin.getLogger().info("Таблицы базы данных созданы успешно!");

        } catch (SQLException e) {
            plugin.getLogger().severe("Ошибка при создании таблиц: " + e.getMessage());
            throw e;
        }
    }

    private void updateTableStructure() {
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement()) {

            // Добавляем колонки если их нет
            try {
                statement.executeUpdate("ALTER TABLE orders ADD COLUMN status VARCHAR(20) DEFAULT 'ACTIVE'");
            } catch (SQLException ignored) {}

            try {
                statement.executeUpdate("ALTER TABLE orders ADD COLUMN updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP");
            } catch (SQLException ignored) {}

            try {
                statement.executeUpdate("ALTER TABLE player_wallets ADD COLUMN updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP");
            } catch (SQLException ignored) {}

        } catch (SQLException e) {
            plugin.getLogger().warning("Ошибка при обновлении структуры таблиц: " + e.getMessage());
        }
    }

    // ==================== МЕТОДЫ ДЛЯ ФРАКЦИЙ (ВРЕМЕННАЯ СИСТЕМА) ====================

    public String getPlayerFactionId(String playerName) {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT faction_id FROM temp_factions WHERE owner = ? OR members LIKE ?")) {
            statement.setString(1, playerName);
            statement.setString(2, "%" + playerName + "%");
            ResultSet rs = statement.executeQuery();
            return rs.next() ? rs.getString("faction_id") : null;
        } catch (SQLException e) {
            plugin.getLogger().severe("Ошибка при получении фракции игрока: " + e.getMessage());
            return null;
        }
    }

    public boolean hasPermissionInFaction(String playerName, String factionId, String permission) {
        // В временной системе владелец фракции имеет все права
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT owner FROM temp_factions WHERE faction_id = ?")) {
            statement.setString(1, factionId);
            ResultSet rs = statement.executeQuery();
            if (rs.next()) {
                return rs.getString("owner").equals(playerName);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Ошибка при проверке прав фракции: " + e.getMessage());
        }
        return false;
    }

    public boolean createTempFaction(String playerName) {
        String factionId = "temp_faction_" + playerName + "_" + System.currentTimeMillis();
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO temp_factions (faction_id, faction_name, owner, members) VALUES (?, ?, ?, ?)")) {
            statement.setString(1, factionId);
            statement.setString(2, playerName + "'s Faction");
            statement.setString(3, playerName);
            statement.setString(4, playerName);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().severe("Ошибка при создании временной фракции: " + e.getMessage());
            return false;
        }
    }

    public String getFactionName(String factionId) {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT faction_name FROM temp_factions WHERE faction_id = ?")) {
            statement.setString(1, factionId);
            ResultSet rs = statement.executeQuery();
            return rs.next() ? rs.getString("faction_name") : null;
        } catch (SQLException e) {
            plugin.getLogger().severe("Ошибка при получении названия фракции: " + e.getMessage());
            return null;
        }
    }

    public String getFactionCurrency(String factionId) {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT currency_name FROM currencies WHERE faction_id = ? LIMIT 1")) {
            statement.setString(1, factionId);
            ResultSet rs = statement.executeQuery();
            if (rs.next()) {
                String currency = rs.getString("currency_name");
                return currency;
            }
            return null;
        } catch (SQLException e) {
            plugin.getLogger().severe("Ошибка при получении валюты фракции: " + e.getMessage());
            return null;
        }
    }

    // ==================== МЕТОДЫ ДЛЯ ИГРОКОВ ====================

    public boolean playerHasWallet(String nickname) {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM players WHERE player_name = ?")) {
            statement.setString(1, nickname);
            ResultSet rs = statement.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        } catch (SQLException e) {
            plugin.getLogger().severe("Ошибка при проверке кошелька: " + e.getMessage());
            return false;
        }
    }

    public void createPlayer(String nickname, String uuid) {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT OR IGNORE INTO players (player_name, uuid) VALUES (?, ?)")) {
            statement.setString(1, nickname);
            statement.setString(2, uuid);
            statement.executeUpdate();

            // Выдаем стартовый баланс VIL
            int startingBalance = plugin.getConfig().getInt("currencies.starting_vil_balance", 100);
            if (startingBalance > 0) {
                try (PreparedStatement walletStatement = connection.prepareStatement(
                        "INSERT OR IGNORE INTO player_wallets (player_name, currency, amount) VALUES (?, 'VIL', ?)")) {
                    walletStatement.setString(1, nickname);
                    walletStatement.setLong(2, startingBalance);
                    walletStatement.executeUpdate();
                }
            }

        } catch (SQLException e) {
            plugin.getLogger().severe("Ошибка при создании игрока: " + e.getMessage());
        }
    }

    public void setPlayerWallet(String nickname, PlayerWallet wallet) {
        try (Connection connection = getConnection()) {
            // Удаляем старые записи кошелька
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM player_wallets WHERE player_name = ?")) {
                statement.setString(1, nickname);
                statement.executeUpdate();
            }

            // Добавляем новые записи
            for (Map.Entry<String, Integer> entry : wallet.getSlots().entrySet()) {
                if (entry.getValue() > 0) {
                    try (PreparedStatement statement = connection.prepareStatement(
                            "INSERT INTO player_wallets (player_name, currency, amount) VALUES (?, ?, ?)")) {
                        statement.setString(1, nickname);
                        statement.setString(2, entry.getKey());
                        statement.setLong(3, entry.getValue());
                        statement.executeUpdate();
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Ошибка при сохранении кошелька: " + e.getMessage());
        }
    }

    public PlayerWallet getPlayerWallet(String nickname) {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT * FROM player_wallets WHERE player_name = ?")) {
            statement.setString(1, nickname);
            ResultSet resultSet = statement.executeQuery();

            TreeMap<String, Integer> currencies = new TreeMap<>();
            while (resultSet.next()) {
                currencies.put(resultSet.getString("currency"),
                        (int) resultSet.getLong("amount"));
            }

            return new PlayerWallet(nickname, currencies);
        } catch (SQLException e) {
            plugin.getLogger().severe("Ошибка при получении кошелька: " + e.getMessage());
            return new PlayerWallet(nickname);
        }
    }

    // ==================== МЕТОДЫ ДЛЯ СОВМЕСТИМОСТИ С НОВЫМ WALLETMANAGER ====================

    public PlayerWallet loadPlayerWallet(String playerName) {
        return getPlayerWallet(playerName);
    }

    public void savePlayerWallet(PlayerWallet wallet) {
        setPlayerWallet(wallet.getPlayerName(), wallet);
    }

    public boolean currencyExists(String currency) {
        return doesCurrencyExist(currency);
    }

    public boolean createCurrency(String currency, String factionId, long maxEmission) {
        addCurrency(currency, factionId, 0, maxEmission);
        return true;
    }

    // ==================== МЕТОДЫ ДЛЯ ВАЛЮТ ====================

    public boolean doesCurrencyExist(String currency) {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM currencies WHERE currency_name = ?")) {
            statement.setString(1, currency);
            ResultSet rs = statement.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        } catch (SQLException e) {
            plugin.getLogger().severe("Ошибка при проверке валюты: " + e.getMessage());
            return false;
        }
    }

    public void addCurrency(String currencyName, String factionId, long emission, long maxEmission) {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT OR REPLACE INTO currencies (currency_name, faction_id, emission, max_emission) VALUES (?, ?, ?, ?)")) {
            statement.setString(1, currencyName);
            statement.setString(2, factionId);
            statement.setLong(3, emission);
            statement.setLong(4, maxEmission);
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Ошибка при добавлении валюты: " + e.getMessage());
        }
    }

    public boolean factionHasCurrency(String factionId) {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM currencies WHERE faction_id = ?")) {
            statement.setString(1, factionId);
            ResultSet rs = statement.executeQuery();
            if (rs.next()) {
                int count = rs.getInt(1);
                return count > 0;
            }
            return false;
        } catch (SQLException e) {
            plugin.getLogger().severe("Ошибка при проверке валюты фракции: " + e.getMessage());
            return false;
        }
    }

    public long getCurrencyEmission(String currency) {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT emission FROM currencies WHERE currency_name = ?")) {
            statement.setString(1, currency);
            ResultSet rs = statement.executeQuery();
            return rs.next() ? rs.getLong("emission") : 0;
        } catch (SQLException e) {
            plugin.getLogger().severe("Ошибка при получении эмиссии: " + e.getMessage());
            return 0;
        }
    }

    public long getCurrencyMaxEmission(String currency) {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT max_emission FROM currencies WHERE currency_name = ?")) {
            statement.setString(1, currency);
            ResultSet rs = statement.executeQuery();
            return rs.next() ? rs.getLong("max_emission") : 0;
        } catch (SQLException e) {
            plugin.getLogger().severe("Ошибка при получении максимальной эмиссии: " + e.getMessage());
            return 0;
        }
    }

    public boolean updateCurrencyEmission(String currency, long newEmission) {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE currencies SET emission = ? WHERE currency_name = ?")) {
            statement.setLong(1, newEmission);
            statement.setString(2, currency);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().severe("Ошибка при обновлении эмиссии: " + e.getMessage());
            return false;
        }
    }

    public String getCurrencyFaction(String currency) {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT faction_id FROM currencies WHERE currency_name = ?")) {
            statement.setString(1, currency);
            ResultSet rs = statement.executeQuery();
            return rs.next() ? rs.getString("faction_id") : null;
        } catch (SQLException e) {
            plugin.getLogger().severe("Ошибка при получении фракции валюты: " + e.getMessage());
            return null;
        }
    }

    // ==================== МЕТОДЫ ДЛЯ БИРЖИ ====================

    public boolean addOrder(Order order) {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO orders (nickname, buy_currency, sell_currency, buy_amount, sell_amount, status) VALUES (?, ?, ?, ?, ?, 'ACTIVE')")) {
            statement.setString(1, order.getNickname());
            statement.setString(2, order.getBuyCurrency());
            statement.setString(3, order.getSellCurrency());
            statement.setLong(4, order.getBuyAmount());
            statement.setLong(5, order.getSellAmount());
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().severe("Ошибка при добавлении ордера: " + e.getMessage());
            return false;
        }
    }

    public List<Order> getOrders(String sellCurrency, String buyCurrency) {
        List<Order> orders = new ArrayList<>();
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT * FROM orders WHERE sell_currency = ? AND buy_currency = ? AND status = 'ACTIVE' ORDER BY created_at ASC")) {
            statement.setString(1, sellCurrency);
            statement.setString(2, buyCurrency);
            ResultSet rs = statement.executeQuery();

            while (rs.next()) {
                orders.add(new Order(
                        rs.getInt("id"),
                        rs.getString("nickname"),
                        rs.getString("sell_currency"),
                        rs.getString("buy_currency"),
                        (int) rs.getLong("sell_amount"),
                        (int) rs.getLong("buy_amount")
                ));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Ошибка при получении ордеров: " + e.getMessage());
        }
        return orders;
    }

    public Order getOrderById(int orderId) {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT * FROM orders WHERE id = ? AND status = 'ACTIVE'")) {
            statement.setInt(1, orderId);
            ResultSet rs = statement.executeQuery();

            if (rs.next()) {
                return new Order(
                        rs.getInt("id"),
                        rs.getString("nickname"),
                        rs.getString("sell_currency"),
                        rs.getString("buy_currency"),
                        (int) rs.getLong("sell_amount"),
                        (int) rs.getLong("buy_amount")
                );
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Ошибка при получении ордера: " + e.getMessage());
        }
        return null;
    }

    public boolean removeOrder(int orderId) {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE orders SET status = 'CANCELLED', updated_at = CURRENT_TIMESTAMP WHERE id = ?")) {
            statement.setInt(1, orderId);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().severe("Ошибка при удалении ордера: " + e.getMessage());
            return false;
        }
    }

    public List<Order> getPlayerOrders(String playerName) {
        List<Order> orders = new ArrayList<>();
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT * FROM orders WHERE nickname = ? AND status = 'ACTIVE' ORDER BY created_at DESC")) {
            statement.setString(1, playerName);
            ResultSet rs = statement.executeQuery();

            while (rs.next()) {
                orders.add(new Order(
                        rs.getInt("id"),
                        rs.getString("nickname"),
                        rs.getString("sell_currency"),
                        rs.getString("buy_currency"),
                        (int) rs.getLong("sell_amount"),
                        (int) rs.getLong("buy_amount")
                ));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Ошибка при получении ордеров игрока: " + e.getMessage());
        }
        return orders;
    }

    public List<Order> getPlayerOrdersPaginated(String playerName, int page, int itemsPerPage) {
        List<Order> orders = new ArrayList<>();
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT * FROM orders WHERE nickname = ? AND status = 'ACTIVE' ORDER BY created_at DESC LIMIT ? OFFSET ?")) {
            statement.setString(1, playerName);
            statement.setInt(2, itemsPerPage);
            statement.setInt(3, page * itemsPerPage);
            ResultSet rs = statement.executeQuery();

            while (rs.next()) {
                orders.add(new Order(
                        rs.getInt("id"),
                        rs.getString("nickname"),
                        rs.getString("sell_currency"),
                        rs.getString("buy_currency"),
                        (int) rs.getLong("sell_amount"),
                        (int) rs.getLong("buy_amount")
                ));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Ошибка при получении ордеров игрока: " + e.getMessage());
        }
        return orders;
    }

    public int getPlayerOrderCount(String playerName) {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM orders WHERE nickname = ? AND status = 'ACTIVE'")) {
            statement.setString(1, playerName);
            ResultSet rs = statement.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            plugin.getLogger().severe("Ошибка при подсчете ордеров игрока: " + e.getMessage());
            return 0;
        }
    }

    public boolean cancelPlayerOrder(int orderId, String playerName) {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE orders SET status = 'CANCELLED', updated_at = CURRENT_TIMESTAMP WHERE id = ? AND nickname = ? AND status = 'ACTIVE'")) {
            statement.setInt(1, orderId);
            statement.setString(2, playerName);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().severe("Ошибка при отмене ордера: " + e.getMessage());
            return false;
        }
    }

    public Order getPlayerOrder(int orderId, String playerName) {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT * FROM orders WHERE id = ? AND nickname = ? AND status = 'ACTIVE'")) {
            statement.setInt(1, orderId);
            statement.setString(2, playerName);
            ResultSet rs = statement.executeQuery();

            if (rs.next()) {
                return new Order(
                        rs.getInt("id"),
                        rs.getString("nickname"),
                        rs.getString("sell_currency"),
                        rs.getString("buy_currency"),
                        (int) rs.getLong("sell_amount"),
                        (int) rs.getLong("buy_amount")
                );
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Ошибка при получении ордера игрока: " + e.getMessage());
        }
        return null;
    }

    public int getTotalOrdersCount() {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM orders WHERE status = 'ACTIVE'")) {
            ResultSet rs = statement.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            plugin.getLogger().severe("Ошибка при подсчете ордеров: " + e.getMessage());
            return 0;
        }
    }

    // ==================== МЕТОДЫ ДЛЯ АУКЦИОНА ====================

    public int addAuctionItem(String sellerName, String sellerUuid, String itemData,
                              String currency, long price, String category, int hoursToExpire) {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO auction_items (seller_name, seller_uuid, item_data, currency, price, category, expires_at) " +
                             "VALUES (?, ?, ?, ?, ?, ?, datetime('now', '+' || ? || ' hours'))",
                     Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, sellerName);
            statement.setString(2, sellerUuid);
            statement.setString(3, itemData);
            statement.setString(4, currency);
            statement.setLong(5, price);
            statement.setString(6, category);
            statement.setInt(7, hoursToExpire);

            int affectedRows = statement.executeUpdate();
            if (affectedRows > 0) {
                ResultSet generatedKeys = statement.getGeneratedKeys();
                if (generatedKeys.next()) {
                    return generatedKeys.getInt(1);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Ошибка при добавлении предмета на аукцион: " + e.getMessage());
        }
        return -1;
    }

    public List<Map<String, Object>> getAuctionItems(String category, String currency, int page, int itemsPerPage) {
        List<Map<String, Object>> items = new ArrayList<>();
        StringBuilder query = new StringBuilder("SELECT * FROM auction_items WHERE is_sold = 0 AND expires_at > datetime('now')");

        if (category != null && !category.equals("ALL")) {
            query.append(" AND category = ?");
        }
        if (currency != null) {
            query.append(" AND currency = ?");
        }

        query.append(" ORDER BY created_at DESC LIMIT ? OFFSET ?");

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(query.toString())) {

            int paramIndex = 1;
            if (category != null && !category.equals("ALL")) {
                statement.setString(paramIndex++, category);
            }
            if (currency != null) {
                statement.setString(paramIndex++, currency);
            }
            statement.setInt(paramIndex++, itemsPerPage);
            statement.setInt(paramIndex, page * itemsPerPage);

            ResultSet rs = statement.executeQuery();
            while (rs.next()) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", rs.getInt("id"));
                item.put("seller_name", rs.getString("seller_name"));
                item.put("item_data", rs.getString("item_data"));
                item.put("currency", rs.getString("currency"));
                item.put("price", rs.getLong("price"));
                item.put("category", rs.getString("category"));
                item.put("created_at", rs.getString("created_at"));
                item.put("expires_at", rs.getString("expires_at"));
                items.add(item);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Ошибка при получении предметов аукциона: " + e.getMessage());
        }
        return items;
    }

    public Map<String, Object> getAuctionItem(int itemId) {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT * FROM auction_items WHERE id = ? AND is_sold = 0 AND expires_at > datetime('now')")) {
            statement.setInt(1, itemId);
            ResultSet rs = statement.executeQuery();

            if (rs.next()) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", rs.getInt("id"));
                item.put("seller_name", rs.getString("seller_name"));
                item.put("seller_uuid", rs.getString("seller_uuid"));
                item.put("item_data", rs.getString("item_data"));
                item.put("currency", rs.getString("currency"));
                item.put("price", rs.getLong("price"));
                item.put("category", rs.getString("category"));
                return item;
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Ошибка при получении предмета аукциона: " + e.getMessage());
        }
        return null;
    }

    public boolean markAuctionItemSold(int itemId) {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE auction_items SET is_sold = 1 WHERE id = ?")) {
            statement.setInt(1, itemId);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().severe("Ошибка при отметке предмета как проданного: " + e.getMessage());
            return false;
        }
    }

    public int getPlayerAuctionItemCount(String playerName) {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM auction_items WHERE seller_name = ? AND is_sold = 0 AND expires_at > datetime('now')")) {
            statement.setString(1, playerName);
            ResultSet rs = statement.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            plugin.getLogger().severe("Ошибка при подсчете предметов игрока на аукционе: " + e.getMessage());
            return 0;
        }
    }

    public List<Map<String, Object>> getExpiredAuctionItems(String playerName) {
        List<Map<String, Object>> items = new ArrayList<>();
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT * FROM auction_items WHERE seller_name = ? AND (is_sold = 0 AND expires_at <= datetime('now'))")) {
            statement.setString(1, playerName);
            ResultSet rs = statement.executeQuery();

            while (rs.next()) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", rs.getInt("id"));
                item.put("item_data", rs.getString("item_data"));
                item.put("currency", rs.getString("currency"));
                item.put("price", rs.getLong("price"));
                items.add(item);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Ошибка при получении истекших предметов: " + e.getMessage());
        }
        return items;
    }

    public boolean removeExpiredAuctionItem(int itemId) {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM auction_items WHERE id = ?")) {
            statement.setInt(1, itemId);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().severe("Ошибка при удалении истекшего предмета: " + e.getMessage());
            return false;
        }
    }

    // ==================== МЕТОДЫ ДЛЯ ПРЕМИУМ МАГАЗИНА ====================

    public int addPremiumShopItem(String itemData, long price, String category, String displayName, String description, int stock) {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO premium_shop (item_data, price, category, display_name, description, stock) VALUES (?, ?, ?, ?, ?, ?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, itemData);
            statement.setLong(2, price);
            statement.setString(3, category);
            statement.setString(4, displayName);
            statement.setString(5, description);
            statement.setInt(6, stock);

            int affectedRows = statement.executeUpdate();
            if (affectedRows > 0) {
                ResultSet generatedKeys = statement.getGeneratedKeys();
                if (generatedKeys.next()) {
                    return generatedKeys.getInt(1);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Ошибка при добавлении предмета в премиум магазин: " + e.getMessage());
        }
        return -1;
    }

    public List<Map<String, Object>> getPremiumShopItems() {
        List<Map<String, Object>> items = new ArrayList<>();
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT * FROM premium_shop WHERE stock != 0 ORDER BY category, id")) {
            ResultSet rs = statement.executeQuery();

            while (rs.next()) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", rs.getInt("id"));
                item.put("item_data", rs.getString("item_data"));
                item.put("price", rs.getLong("price"));
                item.put("category", rs.getString("category"));
                item.put("display_name", rs.getString("display_name"));
                item.put("description", rs.getString("description"));
                item.put("stock", rs.getInt("stock"));
                items.add(item);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Ошибка при получении предметов премиум магазина: " + e.getMessage());
        }
        return items;
    }

    public boolean decreasePremiumShopStock(int itemId) {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE premium_shop SET stock = stock - 1, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND stock > 0")) {
            statement.setInt(1, itemId);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().severe("Ошибка при уменьшении запаса премиум магазина: " + e.getMessage());
            return false;
        }
    }

    public boolean removePremiumShopItem(int itemId) {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM premium_shop WHERE id = ?")) {
            statement.setInt(1, itemId);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().severe("Ошибка при удалении предмета из премиум магазина: " + e.getMessage());
            return false;
        }
    }

    // ==================== МЕТОДЫ ДЛЯ СОВМЕСТИМОСТИ ====================

    public Lot getLotById(int lotId) {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT * FROM auction_items WHERE id = ? AND is_sold = 0 AND expires_at > datetime('now')")) {
            statement.setInt(1, lotId);
            ResultSet rs = statement.executeQuery();

            if (rs.next()) {
                return new Lot(
                        rs.getInt("id"),
                        rs.getString("seller_name"),
                        rs.getString("currency"),
                        rs.getLong("price"),
                        rs.getString("item_data")
                );
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Ошибка при получении лота: " + e.getMessage());
        }
        return null;
    }

    public Order getOrderByID(int orderId) {
        return getOrderById(orderId);
    }

    public TreeMap<Integer, Order> getOrdersByBourse(String sellCurrency, String buyCurrency) {
        TreeMap<Integer, Order> orders = new TreeMap<>();
        List<Order> orderList = getOrders(sellCurrency, buyCurrency);
        for (Order order : orderList) {
            orders.put(order.getId(), order);
        }
        return orders;
    }

    public boolean deleteOrder(int orderId) {
        return removeOrder(orderId);
    }

    public boolean updateOrder(Order order) {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE orders SET buy_amount = ?, sell_amount = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND status = 'ACTIVE'")) {
            statement.setLong(1, order.getBuyAmount());
            statement.setLong(2, order.getSellAmount());
            statement.setInt(3, order.getId());
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().severe("Ошибка при обновлении ордера: " + e.getMessage());
            return false;
        }
    }

    // ==================== МЕТОДЫ ДЛЯ ТРАНЗАКЦИЙ ====================

    public void logTransaction(String fromPlayer, String toPlayer, String currency, long amount,
                               String transactionType, String description) {
        if (!plugin.getConfig().getBoolean("debug.log_transactions", true)) {
            return;
        }

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO transactions (from_player, to_player, currency, amount, transaction_type, description) " +
                             "VALUES (?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, fromPlayer);
            statement.setString(2, toPlayer);
            statement.setString(3, currency);
            statement.setLong(4, amount);
            statement.setString(5, transactionType);
            statement.setString(6, description);
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Ошибка при логировании транзакции: " + e.getMessage());
        }
    }

    // ==================== МЕТОДЫ ДЛЯ СТАТИСТИКИ ====================

    public void updateStatistic(String key, long value) {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT OR REPLACE INTO statistics (stat_key, stat_value, updated_at) VALUES (?, ?, CURRENT_TIMESTAMP)")) {
            statement.setString(1, key);
            statement.setLong(2, value);
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Ошибка при обновлении статистики: " + e.getMessage());
        }
    }

    public long getStatistic(String key) {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT stat_value FROM statistics WHERE stat_key = ?")) {
            statement.setString(1, key);
            ResultSet rs = statement.executeQuery();
            return rs.next() ? rs.getLong("stat_value") : 0;
        } catch (SQLException e) {
            plugin.getLogger().severe("Ошибка при получении статистики: " + e.getMessage());
            return 0;
        }
    }

    // ==================== УТИЛИТЫ ====================

    public void cleanupExpiredAuctions() {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM auction_items WHERE expires_at <= datetime('now') AND is_sold = 0")) {
            int deleted = statement.executeUpdate();
            if (deleted > 0) {
                plugin.getLogger().info("Очищено " + deleted + " истекших лотов аукциона");
                updateStatistic("expired_auctions_cleaned", getStatistic("expired_auctions_cleaned") + deleted);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Ошибка при очистке аукциона: " + e.getMessage());
        }
    }

    public void cleanupOldTransactions(int daysToKeep) {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM transactions WHERE created_at < datetime('now', '-' || ? || ' days')")) {
            statement.setInt(1, daysToKeep);
            int deleted = statement.executeUpdate();
            if (deleted > 0) {
                plugin.getLogger().info("Очищено " + deleted + " старых транзакций");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Ошибка при очистке транзакций: " + e.getMessage());
        }
    }

    public List<String> getAllPlayerNames() {
        List<String> names = new ArrayList<>();
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT DISTINCT player_name FROM players ORDER BY player_name LIMIT 50")) {
            ResultSet rs = statement.executeQuery();
            while (rs.next()) {
                names.add(rs.getString("player_name"));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Ошибка при получении имен игроков: " + e.getMessage());
        }
        return names;
    }

    public List<String> getAllCurrencies() {
        List<String> currencies = new ArrayList<>();
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT DISTINCT currency_name FROM currencies ORDER BY currency_name")) {
            ResultSet rs = statement.executeQuery();
            while (rs.next()) {
                currencies.add(rs.getString("currency_name"));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Ошибка при получении валют: " + e.getMessage());
        }
        return currencies;
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("Соединение с базой данных закрыто");
        }
    }
}