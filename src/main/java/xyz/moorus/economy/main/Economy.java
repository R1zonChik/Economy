package xyz.moorus.economy.main;

import org.bukkit.plugin.java.JavaPlugin;
import xyz.moorus.economy.command.*;
import xyz.moorus.economy.money.WalletManager;
import xyz.moorus.economy.sql.Database;

import java.sql.SQLException;

public final class Economy extends JavaPlugin {

    private static Economy instance;
    private Database database;
    private WalletManager walletManager;

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

            // Регистрируем команды
            registerCommands();

            getLogger().info("Economy плагин успешно запущен!");

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
        // Регистрируем команды через твою систему Command Manager
        CommandManager commandManager = new CommandManager();

        // Регистрируем твои команды
        commandManager.registerCommand(new PayCommand());
        commandManager.registerCommand(new WalletCommand());
        commandManager.registerCommand(new CreateCommand());
        commandManager.registerCommand(new BourseCommand());
        commandManager.registerCommand(new EmitCommand());
        commandManager.registerCommand(new AuctionCommand());
        commandManager.registerCommand(new SellHandCommand());
        commandManager.registerCommand(new AdminCommand());

        AdminCommand adminCommand = new AdminCommand();
        commandManager.registerCommand(adminCommand);
        // Регистрируем CommandManager как executor для всех команд
        getCommand("pay").setExecutor(commandManager);
        getCommand("pw").setExecutor(commandManager);
        getCommand("cc").setExecutor(commandManager);
        getCommand("ecoadmin").setTabCompleter(adminCommand);
        getCommand("bourse").setExecutor(commandManager);
        getCommand("emit").setExecutor(commandManager);
        getCommand("ah").setExecutor(commandManager);
        getCommand("sellhand").setExecutor(commandManager);
        getCommand("ecoadmin").setExecutor(commandManager);
    }

    public static Economy getInstance() {
        return instance;
    }

    public Database getDatabase() {
        return database;
    }

    public WalletManager getWalletManager() {
        return walletManager;
    }
}