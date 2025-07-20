package xyz.moorus.economy.command;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import xyz.moorus.economy.main.Economy;
import xyz.moorus.economy.money.WalletManager;
import xyz.moorus.economy.money.PaymentResult;
import xyz.moorus.economy.sql.Database;

public class CreateCommand implements Command {

    @Override
    public String getName() {
        return "cc";
    }

    @Override
    public void execute(String sender, String[] args) {
        WalletManager walletManager = Economy.getInstance().getWalletManager();
        Database database = Economy.getInstance().getDatabase();

        Player player = Bukkit.getPlayer(sender);
        if (player == null) return;

        if (args.length == 0) {
            player.sendMessage(colorize("&6=== Создание валюты ==="));
            player.sendMessage(colorize("&7Использование: /cc <код_валюты> <лимит_эмиссии>"));
            player.sendMessage(colorize("&7Пример: /cc ABC 1000000"));
            player.sendMessage(colorize("&e"));
            player.sendMessage(colorize("&7Требования к коду валюты:"));
            player.sendMessage(colorize("&7• Ровно 3 символа"));
            player.sendMessage(colorize("&7• Только заглавные латинские буквы (A-Z)"));
            player.sendMessage(colorize("&7• Не должен быть зарезервированным"));
            player.sendMessage(colorize("&e"));
            player.sendMessage(colorize("&cЗарезервированные валюты:"));
            player.sendMessage(colorize("&7VIL, USD, EUR, RUB, GBP, JPY, CHF, CAD, AUD"));
            player.sendMessage(colorize("&e"));
            player.sendMessage(colorize("&7Лимит эмиссии: от 10,000 до 1,000,000,000"));
            return;
        }

        if (args.length != 2) {
            player.sendMessage(colorize("&cИспользование: /cc <код_валюты> <лимит_эмиссии>"));
            return;
        }

        String currencyCode = args[0].toUpperCase();
        int emission;

        // Валидация кода валюты
        if (!walletManager.isCurrencyCorrect(currencyCode)) {
            player.sendMessage(colorize("&cНеверное название валюты!"));
            player.sendMessage(colorize("&7Требования:"));
            player.sendMessage(colorize("&7• Ровно 3 заглавные латинские буквы"));
            player.sendMessage(colorize("&7• Не должно быть зарезервированным"));
            player.sendMessage(colorize("&e"));
            player.sendMessage(colorize("&cЗарезервированные валюты:"));
            player.sendMessage(colorize("&7VIL, USD, EUR, RUB, GBP, JPY, CHF, CAD, AUD"));
            player.sendMessage(colorize("&e"));
            player.sendMessage(colorize("&aПримеры разрешенных валют:"));
            player.sendMessage(colorize("&7ABC, XYZ, FOO, BAR, TEST"));
            return;
        }

        // Валидация лимита эмиссии
        try {
            emission = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage(colorize("&cНеверный лимит эмиссии! Введите число."));
            return;
        }

        if (emission < 10000) {
            player.sendMessage(colorize("&cСлишком маленький лимит эмиссии! Минимум: 10,000"));
            return;
        }

        if (emission > 1000000000) {
            player.sendMessage(colorize("&cСлишком большой лимит эмиссии! Максимум: 1,000,000,000"));
            return;
        }

        // Проверка существования валюты
        if (walletManager.currencyExists(currencyCode)) {
            player.sendMessage(colorize("&cВалюта " + currencyCode + " уже существует!"));
            return;
        }

        // Получаем или создаем фракцию игрока
        String factionId = database.getPlayerFactionId(sender);
        if (factionId == null) {
            if (database.createTempFaction(sender)) {
                factionId = database.getPlayerFactionId(sender);
                player.sendMessage(colorize("&aСоздана временная фракция для вас!"));
            } else {
                player.sendMessage(colorize("&cОшибка при создании фракции!"));
                return;
            }
        }

        // Проверяем права на управление валютой в фракции
        if (!database.hasPermissionInFaction(sender, factionId, "CURRENCY_MANAGE")) {
            player.sendMessage(colorize("&cУ вас нет прав на создание валюты в этой фракции!"));
            return;
        }

        // Проверяем что фракция еще не имеет валюты
        if (database.factionHasCurrency(factionId)) {
            player.sendMessage(colorize("&cВаша фракция уже имеет валюту!"));
            return;
        }

        // Создаем валюту
        boolean success = walletManager.createCurrency(currencyCode, factionId, (long)emission);

        if (success) {
            // Выдаем валюту создателю
            PaymentResult paymentResult = walletManager.putMoney(player.getName(), currencyCode, emission);

            if (paymentResult == PaymentResult.SUCCESS) {
                // Обновляем эмиссию в базе данных
                database.updateCurrencyEmission(currencyCode, emission);

                // Логируем создание валюты
                database.logTransaction(null, player.getName(), currencyCode, emission,
                        "CURRENCY_CREATE", "Currency " + currencyCode + " created by " + player.getName());

                String successMessage = Economy.getInstance().getConfig().getString("messages.currency_created", "&aВалюта создана успешно!");
                player.sendMessage(colorize(successMessage.replace("{currency}", currencyCode)));
                player.sendMessage(colorize("&7Код валюты: &f" + currencyCode));
                player.sendMessage(colorize("&7Лимит эмиссии: &f" + String.format("%,d", emission)));
                player.sendMessage(colorize("&7Вы получили " + String.format("%,d", emission) + " " + currencyCode + " в свой кошелек"));

                // Показываем информацию о фракции
                String factionName = database.getFactionName(factionId);
                if (factionName != null) {
                    player.sendMessage(colorize("&7Фракция: &f" + factionName));
                }

                player.sendMessage(colorize("&7Используйте &f/emit " + currencyCode + " <количество> &7для дополнительного выпуска валюты"));

            } else {
                player.sendMessage(colorize("&cОшибка при выдаче валюты: " + paymentResult.name()));
            }

        } else {
            // Определяем причину ошибки
            if (walletManager.currencyExists(currencyCode)) {
                String existsMessage = Economy.getInstance().getConfig().getString("messages.currency_exists", "&cТакая валюта уже существует!");
                player.sendMessage(colorize(existsMessage));
            } else if (!walletManager.isCurrencyCorrect(currencyCode)) {
                player.sendMessage(colorize("&cНеверное название валюты!"));
                player.sendMessage(colorize("&7Используйте 3 заглавные латинские буквы"));
                player.sendMessage(colorize("&7Избегайте зарезервированных валют"));
            } else if (database.factionHasCurrency(factionId)) {
                player.sendMessage(colorize("&cВаша фракция уже имеет валюту!"));
            } else {
                player.sendMessage(colorize("&cОшибка при создании валюты!"));
                player.sendMessage(colorize("&7Проверьте правильность введенных данных"));
            }
        }
    }

    private String colorize(String text) {
        return text.replace("&", "§");
    }
}