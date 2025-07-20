package xyz.moorus.economy.money;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import xyz.moorus.economy.main.Economy;
import xyz.moorus.economy.sql.Database;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class WalletManager {

    private final Economy plugin;
    private final Database database;
    private final Map<String, PlayerWallet> wallets;

    public WalletManager(Economy plugin) {
        this.plugin = plugin;
        this.database = plugin.getDatabase();
        this.wallets = new ConcurrentHashMap<>();
    }

    public PlayerWallet getPlayerWallet(String playerName) {
        return wallets.computeIfAbsent(playerName, name -> {
            PlayerWallet wallet = database.getPlayerWallet(name);
            if (wallet == null || wallet.getSlots().isEmpty()) {
                wallet = new PlayerWallet(name);
                // Добавляем стартовый баланс VIL
                int startingVil = plugin.getConfig().getInt("currencies.starting_vil_balance", 100);
                wallet.getSlots().put("VIL", startingVil);
                database.setPlayerWallet(name, wallet);
            }
            return wallet;
        });
    }

    public PaymentResult putMoney(String playerName, String currency, int amount) {
        if (!currencyExists(currency)) {
            return PaymentResult.WRONG_CURRENCY;
        }

        if (amount <= 0) {
            return PaymentResult.WRONG_AMOUNT;
        }

        if (amount > 1000000000) {
            return PaymentResult.WRONG_AMOUNT;
        }

        PlayerWallet wallet = getPlayerWallet(playerName);
        int currentAmount = wallet.getSlots().getOrDefault(currency, 0);

        // Проверяем переполнение
        if (currentAmount > Integer.MAX_VALUE - amount) {
            return PaymentResult.WRONG_AMOUNT;
        }

        wallet.getSlots().put(currency, currentAmount + amount);
        database.setPlayerWallet(playerName, wallet);

        return PaymentResult.SUCCESS;
    }

    public PaymentResult getMoney(String playerName, String currency, int amount) {
        if (!currencyExists(currency)) {
            return PaymentResult.WRONG_CURRENCY;
        }

        if (amount <= 0) {
            return PaymentResult.WRONG_AMOUNT;
        }

        PlayerWallet wallet = getPlayerWallet(playerName);
        int currentAmount = wallet.getSlots().getOrDefault(currency, 0);

        if (currentAmount < amount) {
            return PaymentResult.NOT_ENOUGH_MONEY;
        }

        wallet.getSlots().put(currency, currentAmount - amount);
        database.setPlayerWallet(playerName, wallet);

        return PaymentResult.SUCCESS;
    }

    public PaymentResult pay(String fromPlayer, String toPlayer, String currency, int amount) {
        if (!currencyExists(currency)) {
            return PaymentResult.WRONG_CURRENCY;
        }

        if (amount <= 0) {
            return PaymentResult.WRONG_AMOUNT;
        }

        if (amount > 1000000000) {
            return PaymentResult.WRONG_AMOUNT;
        }

        if (!database.playerHasWallet(toPlayer)) {
            return PaymentResult.WRONG_RECIPIENT;
        }

        PlayerWallet fromWallet = getPlayerWallet(fromPlayer);
        if (fromWallet.getSlots().getOrDefault(currency, 0) < amount) {
            return PaymentResult.NOT_ENOUGH_MONEY;
        }

        // Выполняем перевод
        PaymentResult takeResult = getMoney(fromPlayer, currency, amount);
        if (takeResult != PaymentResult.SUCCESS) {
            return takeResult;
        }

        PaymentResult giveResult = putMoney(toPlayer, currency, amount);
        if (giveResult != PaymentResult.SUCCESS) {
            // Возвращаем деньги если не удалось перевести
            putMoney(fromPlayer, currency, amount);
            return giveResult;
        }

        // Логируем транзакцию
        database.logTransaction(fromPlayer, toPlayer, currency, amount,
                "PLAYER_TRANSFER", "Transfer from " + fromPlayer + " to " + toPlayer);

        return PaymentResult.SUCCESS;
    }

    public boolean currencyExists(String currency) {
        return database.doesCurrencyExist(currency);
    }

    public boolean createCurrency(String currency, String factionId, long maxEmission) {
        if (!isCurrencyCorrect(currency)) {
            return false;
        }

        if (currencyExists(currency)) {
            return false;
        }

        database.addCurrency(currency, factionId, 0, maxEmission);
        return true;
    }

    public boolean isCurrencyCorrect(String currency) {
        if (currency == null || currency.isEmpty()) {
            return false;
        }

        // Проверяем что это от 3 до 4 заглавных латинских букв
        if (!currency.matches("^[A-Z]{3,4}$")) {
            return false;
        }

        // Проверяем что не зарезервированная валюта
        List<String> reserved = plugin.getConfig().getStringList("currencies.reserved");
        if (reserved.contains(currency)) {
            return false;
        }

        return true;
    }

    public String getCurrencyValidationError(String currency) {
        if (currency == null || currency.isEmpty()) {
            return plugin.getConfig().getString("messages.currency.invalid_name", "&cНеверное название валюты!");
        }

        if (currency.length() < 3 || currency.length() > 4) {
            return plugin.getConfig().getString("messages.currency.wrong_length", "&cНазвание валюты должно содержать 3-4 символа!");
        }

        if (!currency.matches("^[A-Z]{3,4}$")) {
            return plugin.getConfig().getString("messages.currency.only_latin_uppercase", "&cИспользуйте только заглавные латинские буквы!");
        }

        List<String> reserved = plugin.getConfig().getStringList("currencies.reserved");
        if (reserved.contains(currency)) {
            return plugin.getConfig().getString("messages.currency.reserved_currency", "&cЭта валюта зарезервирована!");
        }

        return null; // Валюта корректна
    }

    public boolean canEmitCurrency(String currency, int amount) {
        long currentEmission = database.getCurrencyEmission(currency);
        long maxEmission = database.getCurrencyMaxEmission(currency);

        return currentEmission + amount <= maxEmission;
    }

    public long getCurrencyEmission(String currency) {
        return database.getCurrencyEmission(currency);
    }

    public long getMaxCurrencyEmission(String currency) {
        return database.getCurrencyMaxEmission(currency);
    }

    // Методы для совместимости с существующим кодом
    public int getBalance(java.util.UUID uuid, String currency) {
        String playerName = org.bukkit.Bukkit.getOfflinePlayer(uuid).getName();
        if (playerName == null) return 0;

        PlayerWallet wallet = getPlayerWallet(playerName);
        return wallet.getSlots().getOrDefault(currency, 0);
    }

    public PlayerWallet getWallet(java.util.UUID uuid) {
        String playerName = org.bukkit.Bukkit.getOfflinePlayer(uuid).getName();
        if (playerName == null) return new PlayerWallet("Unknown");

        return getPlayerWallet(playerName);
    }

    public List<String> getAllCurrencies() {
        return database.getAllCurrencies();
    }

    public Database getDatabase() {
        return database;
    }

    public void saveAllWallets() {
        for (PlayerWallet wallet : wallets.values()) {
            database.setPlayerWallet(wallet.getPlayerName(), wallet);
        }
    }

    public void loadPlayerWallet(String playerName) {
        if (!wallets.containsKey(playerName)) {
            getPlayerWallet(playerName); // Это загрузит кошелек
        }
    }

    public void unloadPlayerWallet(String playerName) {
        PlayerWallet wallet = wallets.remove(playerName);
        if (wallet != null) {
            database.setPlayerWallet(playerName, wallet);
        }
    }
}