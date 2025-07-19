package xyz.moorus.economy.money;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import xyz.moorus.economy.sql.Database;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

public class WalletManager implements Listener {

    private Database database;

    public WalletManager(Database database) {
        this.database = database;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        String nickname = e.getPlayer().getName();
        String uuid = e.getPlayer().getUniqueId().toString();

        // Асинхронно проверяем и создаем кошелек
        Bukkit.getScheduler().runTaskAsynchronously(e.getPlayer().getServer().getPluginManager().getPlugin("Economy"), () -> {
            if (!database.playerHasWallet(nickname)) {
                database.createPlayer(nickname, uuid);
                PlayerWallet wallet = new PlayerWallet(nickname);
                wallet.putCurrency("VIL", 100); // Стартовый баланс
                database.setPlayerWallet(nickname, wallet);
            }
        });
    }

    public PlayerWallet getPlayerWallet(String nickname) {
        return database.getPlayerWallet(nickname);
    }

    public PaymentResult pay(String payingPlayerNick, String recipientPlayerNick, String currency, int amount) {
        if (!database.doesCurrencyExist(currency)) return PaymentResult.WRONG_CURRENCY;
        if (!database.playerHasWallet(recipientPlayerNick) || payingPlayerNick.equals(recipientPlayerNick)) return PaymentResult.WRONG_RECIPIENT;
        if (amount <= 0) return PaymentResult.WRONG_AMOUNT;

        PlayerWallet payer = database.getPlayerWallet(payingPlayerNick);
        PlayerWallet recipient = database.getPlayerWallet(recipientPlayerNick);

        if (payer.getCurrencyAmount(currency) < amount) return PaymentResult.NOT_ENOUGH_MONEY;

        payer.getCurrency(currency, amount);
        database.setPlayerWallet(payer.getNickname(), payer);

        recipient.putCurrency(currency, amount);
        database.setPlayerWallet(recipient.getNickname(), recipient);

        // Логируем транзакцию
        database.logTransaction(payingPlayerNick, recipientPlayerNick, currency, amount, "TRANSFER", "Player transfer");

        return PaymentResult.SUCCESS;
    }

    public PaymentResult putMoney(String playerNick, String currency, int amount) {
        if (!database.doesCurrencyExist(currency)) return PaymentResult.WRONG_CURRENCY;
        if (!database.playerHasWallet(playerNick)) return PaymentResult.WRONG_RECIPIENT;
        if (amount <= 0) return PaymentResult.WRONG_AMOUNT;

        PlayerWallet recipient = database.getPlayerWallet(playerNick);
        recipient.putCurrency(currency, amount);
        database.setPlayerWallet(recipient.getNickname(), recipient);

        return PaymentResult.SUCCESS;
    }

    public PaymentResult getMoney(String payerNick, String currency, int amount) {
        if (!database.doesCurrencyExist(currency)) return PaymentResult.WRONG_CURRENCY;
        if (!database.playerHasWallet(payerNick)) return PaymentResult.WRONG_RECIPIENT;
        if (amount <= 0) return PaymentResult.WRONG_AMOUNT;

        PlayerWallet payer = database.getPlayerWallet(payerNick);

        if (payer.getCurrencyAmount(currency) < amount) return PaymentResult.NOT_ENOUGH_MONEY;

        payer.getCurrency(currency, amount);
        database.setPlayerWallet(payer.getNickname(), payer);

        return PaymentResult.SUCCESS;
    }

    public CreateResult createCurrency(String factionId, String creatorName, String currencyName, int amount) {
        if (!isCurrencyCorrect(currencyName)) return CreateResult.WRONG_NAME;
        if (amount < 10000 || amount > 1000000000) return CreateResult.WRONG_AMOUNT;
        if (database.factionHasCurrency(factionId)) return CreateResult.FACTION_ALREADY_HAS_CURRENCY;

        if (!database.doesCurrencyExist(currencyName)) {
            database.addCurrency(currencyName, factionId, (long) amount, (long) amount * 2);
            PlayerWallet wallet = database.getPlayerWallet(creatorName);
            wallet.putCurrency(currencyName, amount);
            database.setPlayerWallet(creatorName, wallet);
            return CreateResult.SUCCESS;
        } else {
            return CreateResult.ALREADY_EXISTS;
        }
    }

    public boolean emitCurrency(String emitterName, String currencyName, int amount) {
        long currentAmount = database.getCurrencyEmission(currencyName);
        long maxEmission = database.getCurrencyMaxEmission(currencyName);

        if (currentAmount + amount > maxEmission) return false;

        // Обновляем эмиссию
        if (database.updateCurrencyEmission(currencyName, currentAmount + amount)) {
            // Добавляем валюту эмитенту
            PlayerWallet wallet = database.getPlayerWallet(emitterName);
            wallet.putCurrency(currencyName, amount);
            database.setPlayerWallet(emitterName, wallet);

            // Логируем эмиссию
            database.logTransaction(null, emitterName, currencyName, amount, "EMISSION", "Currency emission");
            return true;
        }

        return false;
    }

    public boolean isCurrencyCorrect(String currency) {
        if (currency == null || currency.length() != 3) {
            return false;
        }

        // Проверяем что все символы - заглавные латинские буквы
        for (char c : currency.toCharArray()) {
            if (c < 'A' || c > 'Z') {
                return false;
            }
        }

        // Проверяем что это не зарезервированные комбинации (кроме VIL)
        String[] reserved = {"USD", "EUR", "RUB", "GBP", "JPY", "CHF", "CAD", "AUD"};
        for (String res : reserved) {
            if (currency.equals(res)) {
                return false; // ВСЕ зарезервированные валюты запрещены для создания
            }
        }

        // VIL может создавать только система
        if (currency.equals("VIL")) {
            return false;
        }

        return true;
    }

    public boolean currencyExists(String currency) {
        return database.doesCurrencyExist(currency);
    }

    public boolean canEmitCurrency(String currency, int amount) {
        long currentEmission = database.getCurrencyEmission(currency);
        long maxEmission = database.getCurrencyMaxEmission(currency);
        return (currentEmission + amount) <= maxEmission;
    }

    // Методы для PlaceholderAPI
    public double getBalance(UUID playerUuid, String currency) {
        Player player = Bukkit.getPlayer(playerUuid);
        if (player == null) return 0.0;

        PlayerWallet wallet = getPlayerWallet(player.getName());
        return wallet.getCurrencyAmount(currency);
    }

    public Map<String, Double> getWallet(UUID playerUuid) {
        Player player = Bukkit.getPlayer(playerUuid);
        if (player == null) return new HashMap<>();

        PlayerWallet wallet = getPlayerWallet(player.getName());
        TreeMap<String, Integer> slots = wallet.getSlots();

        Map<String, Double> result = new HashMap<>();
        for (Map.Entry<String, Integer> entry : slots.entrySet()) {
            result.put(entry.getKey(), entry.getValue().doubleValue());
        }

        return result;
    }

    public boolean transferMoney(UUID fromUuid, UUID toUuid, String currency, double amount) {
        Player fromPlayer = Bukkit.getPlayer(fromUuid);
        Player toPlayer = Bukkit.getPlayer(toUuid);

        if (fromPlayer == null || toPlayer == null) return false;

        PaymentResult result = pay(fromPlayer.getName(), toPlayer.getName(), currency, (int) amount);
        return result == PaymentResult.SUCCESS;
    }

    public boolean addMoney(UUID playerUuid, String currency, double amount) {
        Player player = Bukkit.getPlayer(playerUuid);
        if (player == null) return false;

        PaymentResult result = putMoney(player.getName(), currency, (int) amount);
        return result == PaymentResult.SUCCESS;
    }

    public Database getDatabase() {
        return database;
    }
}