package xyz.moorus.economy.main;

import org.bukkit.plugin.java.JavaPlugin;
import xyz.moorus.economy.command.*;
import xyz.moorus.economy.integration.MedievalFactionsDatabase;
import xyz.moorus.economy.money.WalletManager;
import xyz.moorus.economy.sql.Database;

import java.sql.SQLException;

public final class Economy extends JavaPlugin {

    private static Economy instance;
    private Database database;
    private WalletManager walletManager;
    private MedievalFactionsDatabase medievalFactionsDatabase;

    @Override
    public void onEnable() {
        instance = this;

        // Сохраняем конфиг по умолчанию
        saveDefaultConfig();

        try {
            // Инициализируем базу данных
            database = new Database(this);

            // Инициализируем менеджер кошельков
            walletManager = new WalletManager(this);

            // Инициализируем интеграцию с Medieval Factions
            medievalFactionsDatabase = new MedievalFactionsDatabase(this);

            // Регистрируем команды
            registerCommands();

            // ДОБАВЛЯЕМ РЕГИСТРАЦИЮ СОБЫТИЙ
            registerEvents();

            getLogger().info("Economy плагин успешно запущен!");

            if (medievalFactionsDatabase.isEnabled()) {
                getLogger().info("Интеграция с Medieval Factions через базу данных активна!");
            } else {
                getLogger().warning("Medieval Factions база данных не найдена!");
            }

        } catch (SQLException e) {
            getLogger().severe("Ошибка при подключении к базе данных: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (walletManager != null) {
            walletManager.saveAllWallets();
        }

        if (database != null) {
            database.close();
        }

        getLogger().info("Economy плагин отключен!");
    }

    private void registerCommands() {
        // Регистрируем команды через CommandManager
        CommandManager commandManager = new CommandManager();

        // Регистрируем команды
        commandManager.registerCommand(new PayCommand());
        commandManager.registerCommand(new WalletCommand());
        commandManager.registerCommand(new CreateCurrencyCommand());
        commandManager.registerCommand(new BourseCommand());
        commandManager.registerCommand(new EmitCommand());
        commandManager.registerCommand(new AuctionCommand());
        commandManager.registerCommand(new SellHandCommand());

        // AdminCommand с TabCompleter
        AdminCommand adminCommand = new AdminCommand();
        commandManager.registerCommand(adminCommand);

        // Регистрируем CommandManager как executor для команд
        getCommand("pay").setExecutor(commandManager);
        getCommand("pw").setExecutor(commandManager);
        getCommand("cc").setExecutor(commandManager);
        getCommand("bourse").setExecutor(commandManager);
        getCommand("emit").setExecutor(commandManager);
        getCommand("ah").setExecutor(commandManager);
        getCommand("sellhand").setExecutor(commandManager);
        getCommand("ecoadmin").setExecutor(commandManager);

        // Регистрируем TabCompleter для админских команд
        getCommand("ecoadmin").setTabCompleter(adminCommand);
    }

    // НОВЫЙ МЕТОД ДЛЯ РЕГИСТРАЦИИ СОБЫТИЙ
    private void registerEvents() {
        // BourseCommand уже регистрирует себя как Listener в конструкторе
        // но для надежности можно добавить дополнительную регистрацию
        getLogger().info("События зарегистрированы!");
    }

    // Геттеры
    public static Economy getInstance() {
        return instance;
    }

    public Database getDatabase() {
        return database;
    }

    public WalletManager getWalletManager() {
        return walletManager;
    }

    public MedievalFactionsDatabase getMedievalFactionsDatabase() {
        return medievalFactionsDatabase;
    }
}