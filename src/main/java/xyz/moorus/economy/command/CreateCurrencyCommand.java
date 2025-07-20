package xyz.moorus.economy.command;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import xyz.moorus.economy.integration.MedievalFactionsIntegration;
import xyz.moorus.economy.main.Economy;
import xyz.moorus.economy.money.WalletManager;
import xyz.moorus.economy.sql.Database;

public class CreateCurrencyCommand implements Command {

    private MedievalFactionsIntegration mfIntegration;

    public CreateCurrencyCommand() {
        // Инициализируем интеграцию при создании команды
        this.mfIntegration = null; // Будет инициализирована в execute
    }

    @Override
    public String getName() {
        return "cc";
    }

    @Override
    public void execute(String sender, String[] args) {
        Player player = Bukkit.getPlayer(sender);
        if (player == null) return;

        // Инициализируем интеграцию если еще не сделали
        if (mfIntegration == null) {
            mfIntegration = Economy.getInstance().getMedievalFactionsIntegration();
        }

        if (args.length == 0) {
            showHelp(player);
            return;
        }

        if (args.length != 2) {
            player.sendMessage(colorize("&cИспользование: /cc <название> <максимальная_эмиссия>"));
            player.sendMessage(colorize("&7Пример: /cc ABC 1000000"));
            return;
        }

        // Вызываем метод создания валюты
        handleCreateCurrency(player, args);
    }

    private void handleCreateCurrency(Player player, String[] args) {
        if (!player.hasPermission("economy.currency.create")) {
            player.sendMessage(colorize("&cУ вас нет прав на создание валют!"));
            return;
        }

        String currencyName = args[0].toUpperCase();
        long maxEmission;

        try {
            maxEmission = Long.parseLong(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage(colorize("&cНеверная максимальная эмиссия!"));
            return;
        }

        if (maxEmission <= 0) {
            player.sendMessage(colorize("&cМаксимальная эмиссия должна быть больше 0!"));
            return;
        }

        if (maxEmission > 1000000000L) {
            player.sendMessage(colorize("&cСлишком большая эмиссия! Максимум: 1,000,000,000"));
            return;
        }

        WalletManager walletManager = Economy.getInstance().getWalletManager();

        // Проверяем корректность названия валюты
        String validationError = walletManager.getCurrencyValidationError(currencyName);
        if (validationError != null) {
            player.sendMessage(colorize(validationError));
            return;
        }

        // Проверяем что валюта не существует
        if (walletManager.currencyExists(currencyName)) {
            player.sendMessage(colorize("&cВалюта " + currencyName + " уже существует!"));
            return;
        }

        // ПРОВЕРКА MEDIEVAL FACTIONS
        if (!mfIntegration.isEnabled()) {
            player.sendMessage(colorize("&cПлагин Medieval Factions не найден!"));
            return;
        }

        // ОТЛАДКА - показываем всех игроков в MF
        player.sendMessage(colorize("&7Запуск отладки Medieval Factions..."));
        mfIntegration.debugPlayers();

        // Проверяем состоит ли игрок во фракции
        boolean inFaction = mfIntegration.isPlayerInFaction(player);
        player.sendMessage(colorize("&7Отладка: Вы во фракции = " + inFaction));

        if (!inFaction) {
            player.sendMessage(colorize("&cВы не состоите во фракции!"));
            player.sendMessage(colorize("&7Создайте фракцию: &f/faction create <название>"));
            player.sendMessage(colorize("&7Или вступите в существующую: &f/faction join <название>"));
            return;
        }

        // Получаем ID фракции
        String playerFactionId = mfIntegration.getPlayerFactionId(player);
        player.sendMessage(colorize("&7Отладка: ID фракции = " + playerFactionId));

        if (playerFactionId == null) {
            player.sendMessage(colorize("&cОшибка получения ID фракции!"));
            return;
        }

        // Получаем название фракции
        String factionName = mfIntegration.getFactionName(playerFactionId);
        player.sendMessage(colorize("&7Отладка: Название фракции = " + factionName));

        // Проверяем права на управление валютой
        boolean hasPermission = mfIntegration.hasCurrencyManagePermission(player);
        player.sendMessage(colorize("&7Отладка: Права на валюту = " + hasPermission));

        if (!hasPermission) {
            player.sendMessage(colorize("&cУ вас нет прав на управление валютой фракции!"));
            player.sendMessage(colorize("&7Обратитесь к лидеру фракции за правами"));
            player.sendMessage(colorize("&7Необходимо право: &fCURRENCY_MANAGE"));
            player.sendMessage(colorize("&7Фракция: &f" + factionName));
            return;
        }

        // Проверяем что у фракции еще нет валюты
        Database database = Economy.getInstance().getDatabase();
        boolean factionHasCurrency = database.factionHasCurrency(playerFactionId);
        player.sendMessage(colorize("&7Отладка: Фракция имеет валюту = " + factionHasCurrency));

        if (factionHasCurrency) {
            String existingCurrency = database.getFactionCurrency(playerFactionId);
            player.sendMessage(colorize("&cВаша фракция уже имеет валюту: " + existingCurrency));
            player.sendMessage(colorize("&7Одна фракция может иметь только одну валюту"));
            return;
        }

        // Создаем валюту
        player.sendMessage(colorize("&7Создание валюты..."));

        boolean success = walletManager.createCurrency(currencyName, playerFactionId, maxEmission);
        player.sendMessage(colorize("&7Отладка: Создание успешно = " + success));

        if (success) {
            if (factionName == null) factionName = "Фракция #" + playerFactionId;

            player.sendMessage(colorize("&a✓ Валюта " + currencyName + " успешно создана!"));
            player.sendMessage(colorize("&7Фракция: &f" + factionName));
            player.sendMessage(colorize("&7ID фракции: &f" + playerFactionId));
            player.sendMessage(colorize("&7Максимальная эмиссия: &e" + String.format("%,d", maxEmission)));
            player.sendMessage(colorize("&7Текущая эмиссия: &f0"));
            player.sendMessage(colorize("&e"));
            player.sendMessage(colorize("&7Используйте &f/emit " + currencyName + " <количество> &7для выпуска валюты"));

            // Логируем создание валюты
            database.logTransaction("SYSTEM", player.getName(), currencyName, 0,
                    "CURRENCY_CREATE", "Currency " + currencyName + " created by faction " + factionName);

            Economy.getInstance().getLogger().info("Игрок " + player.getName() +
                    " создал валюту " + currencyName + " для фракции " + factionName + " (ID: " + playerFactionId + ")");

        } else {
            player.sendMessage(colorize("&cОшибка при создании валюты!"));
            player.sendMessage(colorize("&7Возможно, валюта уже существует или произошла ошибка базы данных"));

            // Дополнительная отладка
            player.sendMessage(colorize("&7Отладка: Проверьте логи сервера для подробностей"));
            Economy.getInstance().getLogger().warning("Ошибка создания валюты " + currencyName +
                    " для игрока " + player.getName() + " (фракция: " + playerFactionId + ")");
        }
    }

    private void showHelp(Player player) {
        player.sendMessage(colorize("&6=== Создание валюты ==="));
        player.sendMessage(colorize("&7Команда: &f/cc <название> <максимальная_эмиссия>"));
        player.sendMessage(colorize("&7Пример: &f/cc ABC 1000000"));
        player.sendMessage(colorize("&e"));
        showRequirements(player);
        player.sendMessage(colorize("&e"));
        player.sendMessage(colorize("&7Требования:"));
        player.sendMessage(colorize("&8• Вы должны состоять во фракции"));
        player.sendMessage(colorize("&8• У вас должно быть право CURRENCY_MANAGE"));
        player.sendMessage(colorize("&8• Фракция может создать только одну валюту"));
        player.sendMessage(colorize("&e"));

        // Показываем статус интеграции
        if (mfIntegration == null) {
            mfIntegration = Economy.getInstance().getMedievalFactionsIntegration();
        }

        player.sendMessage(colorize("&6=== ДИАГНОСТИКА ==="));

        if (mfIntegration.isEnabled()) {
            player.sendMessage(colorize("&a✓ Medieval Factions подключен"));

            // Запускаем отладку
            mfIntegration.debugPlayers();

            boolean inFaction = mfIntegration.isPlayerInFaction(player);
            if (inFaction) {
                String playerFactionId = mfIntegration.getPlayerFactionId(player);
                String factionName = mfIntegration.getFactionName(playerFactionId);
                boolean hasPermission = mfIntegration.hasCurrencyManagePermission(player);

                player.sendMessage(colorize("&a✓ Вы состоите во фракции: &f" + factionName));
                player.sendMessage(colorize("&7  ID фракции: &f" + playerFactionId));

                if (hasPermission) {
                    player.sendMessage(colorize("&a✓ У вас есть права на управление валютой"));
                } else {
                    player.sendMessage(colorize("&c✗ У вас нет прав на управление валютой"));
                    player.sendMessage(colorize("&7  Попросите лидера выдать право CURRENCY_MANAGE"));
                }
            } else {
                player.sendMessage(colorize("&c✗ Вы не состоите во фракции"));
                player.sendMessage(colorize("&7  Создайте фракцию: &f/faction create <название>"));
            }
        } else {
            player.sendMessage(colorize("&c✗ Medieval Factions не найден"));
        }

        player.sendMessage(colorize("&7Проверьте консоль для подробной отладки"));
    }

    private void showRequirements(Player player) {
        String requirements = Economy.getInstance().getConfig().getString("messages.currency.name_requirements", "&7Требования: 3 заглавные латинские буквы");
        String reserved = Economy.getInstance().getConfig().getString("messages.currency.reserved_list", "&7Зарезервированные: VIL, USD, EUR, RUB, GBP, JPY, CHF, CAD, AUD");
        String examples = Economy.getInstance().getConfig().getString("messages.currency.allowed_examples", "&aПримеры: ABC, XYZ, FOO, BAR");

        player.sendMessage(colorize(requirements));
        player.sendMessage(colorize(reserved));
        player.sendMessage(colorize(examples));
    }

    private String colorize(String text) {
        return text.replace("&", "§");
    }
}