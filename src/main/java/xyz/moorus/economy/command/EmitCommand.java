package xyz.moorus.economy.command;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import xyz.moorus.economy.main.Economy;
import xyz.moorus.economy.money.PaymentResult;
import xyz.moorus.economy.money.WalletManager;
import xyz.moorus.economy.sql.Database;

public class EmitCommand implements Command {

    @Override
    public String getName() {
        return "emit";
    }

    @Override
    public void execute(String sender, String[] args) {
        Player player = Bukkit.getPlayer(sender);
        if (player == null) return;

        if (args.length != 2) {
            player.sendMessage(colorize("&cИспользование: /emit <валюта> <количество>"));
            player.sendMessage(colorize("&7Пример: /emit USD 1000"));
            return;
        }

        String currency = args[0].toUpperCase();
        int amount;

        // Проверяем что количество - число
        try {
            amount = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage(colorize("&cОшибка: количество должно быть числом!"));
            player.sendMessage(colorize("&7Пример: /emit USD 1000"));
            return;
        }

        if (amount <= 0) {
            player.sendMessage(colorize("&cОшибка: количество должно быть больше 0!"));
            return;
        }

        WalletManager walletManager = Economy.getInstance().getWalletManager();
        Database database = Economy.getInstance().getDatabase();

        // Проверяем что валюта существует
        if (!walletManager.currencyExists(currency)) {
            player.sendMessage(colorize("&cОшибка: валюта " + currency + " не существует!"));
            return;
        }

        // Проверяем права на управление валютой
        if (!hasPermissionToManageCurrency(player, currency)) {
            player.sendMessage(colorize("&cУ вас нет прав на управление валютой " + currency + "!"));
            return;
        }

        // Проверяем лимиты эмиссии
        long currentEmission = database.getCurrencyEmission(currency);
        long maxEmission = database.getCurrencyMaxEmission(currency);

        if (currentEmission + amount > maxEmission) {
            long available = maxEmission - currentEmission;
            player.sendMessage(colorize("&cОшибка: превышен лимит эмиссии!"));
            player.sendMessage(colorize("&7Текущая эмиссия: &f" + String.format("%,d", currentEmission) + " " + currency));
            player.sendMessage(colorize("&7Максимальная эмиссия: &f" + String.format("%,d", maxEmission) + " " + currency));
            player.sendMessage(colorize("&7Доступно для выпуска: &f" + String.format("%,d", available) + " " + currency));
            return;
        }

        // Выпускаем валюту
        PaymentResult result = walletManager.putMoney(player.getName(), currency, amount);
        if (result == PaymentResult.SUCCESS) {
            // Обновляем эмиссию в базе данных
            database.updateCurrencyEmission(currency, currentEmission + amount);

            // Логируем транзакцию
            database.logTransaction("EMISSION", player.getName(), currency, amount,
                    "CURRENCY_EMISSION", "Currency emission by " + player.getName());

            player.sendMessage(colorize("&aУспешно выпущено " + String.format("%,d", amount) + " " + currency + "!"));
            player.sendMessage(colorize("&7Новая эмиссия: &f" + String.format("%,d", currentEmission + amount) + " " + currency));

            long remaining = maxEmission - (currentEmission + amount);
            player.sendMessage(colorize("&7Осталось до лимита: &f" + String.format("%,d", remaining) + " " + currency));
        } else {
            player.sendMessage(colorize("&cОшибка при выпуске валюты: " + result.name()));
        }
    }

    private boolean hasPermissionToManageCurrency(Player player, String currency) {
        Database database = Economy.getInstance().getDatabase();

        // Получаем фракцию владельца валюты
        String currencyFaction = database.getCurrencyFaction(currency);
        if (currencyFaction == null) return false;

        // Системная валюта VIL - только админы
        if (currency.equals("VIL")) {
            return player.hasPermission("economy.admin.emit");
        }

        // Получаем фракцию игрока
        String playerFaction = database.getPlayerFactionId(player.getName());
        if (playerFaction == null || !playerFaction.equals(currencyFaction)) {
            return false;
        }

        // Проверяем права в фракции (CURRENCY_MANAGE)
        return database.hasPermissionInFaction(player.getName(), playerFaction, "CURRENCY_MANAGE");
    }

    private String colorize(String text) {
        return text.replace("&", "§");
    }
}