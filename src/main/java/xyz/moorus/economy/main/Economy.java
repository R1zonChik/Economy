package xyz.moorus.economy.main;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import xyz.moorus.economy.executor.Executor;
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

        getLogger().info("Создание конфигурационного файла...");
        saveDefaultConfig();

        getLogger().info("Economy плагин загружается асинхронно...");

        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                getLogger().info("Подключение к базе данных...");
                database = new Database(this);

                walletManager = new WalletManager(database);

                Bukkit.getScheduler().runTask(this, () -> {
                    registerCommands();
                    getLogger().info("Economy плагин полностью загружен!");
                });

            } catch (SQLException e) {
                getLogger().severe("Ошибка подключения к базе данных: " + e.getMessage());
                e.printStackTrace();
                Bukkit.getPluginManager().disablePlugin(this);
            }
        });
    }

    @Override
    public void onDisable() {
        if (database != null) {
            database.close();
        }
        getLogger().info("Economy плагин отключен!");
    }

    private void registerCommands() {
        Executor executor = new Executor(walletManager);

        getCommand("pw").setExecutor(executor);
        getCommand("pay").setExecutor(executor);
        getCommand("cc").setExecutor(executor);
        getCommand("bourse").setExecutor(executor);
        getCommand("emit").setExecutor(executor);
        getCommand("ah").setExecutor(executor);
        getCommand("sellhand").setExecutor(executor);
        getCommand("ecoadmin").setExecutor(executor);

        getLogger().info("Команды зарегистрированы!");
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