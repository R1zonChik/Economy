package xyz.moorus.economy.command;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import xyz.moorus.economy.main.Economy;
import xyz.moorus.economy.money.WalletManager;
import xyz.moorus.economy.sql.Database;

public class CreateCurrencyCommand implements Command {

    @Override
    public String getName() {
        return "cc";
    }

    @Override
    public void execute(String sender, String[] args) {
        Player player = Bukkit.getPlayer(sender);
        if (player == null) return;

        if (args.length == 0) {
            showHelp(player);
            return;
        }

        if (args.length != 1) {
            player.sendMessage(colorize("&cИспользование: /cc <название_валюты>"));
            player.sendMessage(colorize("&7Пример: /cc USD"));
            showRequirements(player);
            return;
        }

        String currency = args[0].toUpperCase();
        WalletManager walletManager = Economy.getInstance().getWalletManager();
        Database database = Economy.getInstance().getDatabase();

        // Проверяем корректность валюты
        String validationError = walletManager.getCurrencyValidationError(currency);
        if (validationError != null) {
            player.sendMessage(colorize(validationError));
            showRequirements(player);
            return;
        }

        // Проверяем что валюта не существует
        if (walletManager.currencyExists(currency)) {
            String message = Economy.getInstance().getConfig().getString("messages.currency_exists", "&cТакая валюта уже существует!");
            player.sendMessage(colorize(message));
            return;
        }

        // Получаем фракцию игрока
        String playerFactionId = database.getPlayerFactionId(player.getName());
        if (playerFactionId == null) {
            player.sendMessage(colorize("&cВы должны состоять во фракции для создания валюты!"));
            return;
        }

        // Проверяем права в фракции
        if (!database.hasPermissionInFaction(player.getName(), playerFactionId, "CURRENCY_MANAGE")) {
            player.sendMessage(colorize("&cУ вас нет прав на создание валюты во фракции!"));
            return;
        }

        // Проверяем что фракция еще не создала валюту
        if (database.getFactionCurrency(playerFactionId) != null) {
            player.sendMessage(colorize("&cВаша фракция уже создала валюту!"));
            return;
        }

        // Создаем валюту
        long defaultMaxEmission = Economy.getInstance().getConfig().getLong("emission.default_max_emission", 1000000000L);

        if (walletManager.createCurrency(currency, playerFactionId, defaultMaxEmission)) {
            String successMessage = Economy.getInstance().getConfig().getString("messages.currency_created", "&aВалюта {currency} создана успешно!")
                    .replace("{currency}", currency);
            player.sendMessage(colorize(successMessage));

            player.sendMessage(colorize("&7Фракция: &f" + database.getFactionName(playerFactionId)));
            player.sendMessage(colorize("&7Максимальная эмиссия: &f" + String.format("%,d", defaultMaxEmission) + " " + currency));
            player.sendMessage(colorize("&7Используйте &f/emit " + currency + " <количество> &7для выпуска валюты"));

            // Логируем создание валюты
            database.logTransaction(player.getName(), null, currency, 0,
                    "CURRENCY_CREATE", "Currency " + currency + " created by faction " + playerFactionId);
        } else {
            player.sendMessage(colorize("&cОшибка при создании валюты!"));
        }
    }

    private void showHelp(Player player) {
        player.sendMessage(colorize("&6=== Создание валюты ==="));
        player.sendMessage(colorize("&7Команда: &f/cc <название>"));
        player.sendMessage(colorize("&7Пример: &f/cc USD"));
        player.sendMessage(colorize("&e"));
        showRequirements(player);
        player.sendMessage(colorize("&e"));
        player.sendMessage(colorize("&7Примечания:"));
        player.sendMessage(colorize("&8• Вы должны состоять во фракции"));
        player.sendMessage(colorize("&8• У вас должно быть право CURRENCY_MANAGE"));
        player.sendMessage(colorize("&8• Фракция может создать только одну валюту"));
    }

    private void showRequirements(Player player) {
        String requirements = Economy.getInstance().getConfig().getString("messages.currency.name_requirements", "&7Требования: 3 заглавные латинские буквы");
        String reserved = Economy.getInstance().getConfig().getString("messages.currency.reserved_list", "&7Зарезервированные: VIL, USD, EUR, RUB, GBP, JPY, CHF, CAD, AUD");
        String examples = Economy.getInstance().getConfig().getString("messages.currency.allowed_examples", "&aПримеры: ABC, XYZ, FOO, BAR, TEST");

        player.sendMessage(colorize(requirements));
        player.sendMessage(colorize(reserved));
        player.sendMessage(colorize(examples));
    }

    private String colorize(String text) {
        return text.replace("&", "§");
    }
}