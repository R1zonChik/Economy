package xyz.moorus.economy.command;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import xyz.moorus.economy.integration.MedievalFactionsDatabase;
import xyz.moorus.economy.main.Economy;
import xyz.moorus.economy.money.WalletManager;
import xyz.moorus.economy.sql.Database;

public class CreateCurrencyCommand implements Command {

    private MedievalFactionsDatabase mfDatabase;

    public CreateCurrencyCommand() {
        this.mfDatabase = null;
    }

    @Override
    public String getName() {
        return "cc";
    }

    @Override
    public void execute(String sender, String[] args) {
        Player player = Bukkit.getPlayer(sender);
        if (player == null) return;

        if (mfDatabase == null) {
            mfDatabase = Economy.getInstance().getMedievalFactionsDatabase();
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
        if (!mfDatabase.isEnabled()) {
            boolean allowWithoutFaction = Economy.getInstance().getConfig().getBoolean("medieval_factions.allow_without_faction", false);

            if (!allowWithoutFaction) {
                player.sendMessage(colorize("&cMedieval Factions не найден!"));
                player.sendMessage(colorize("&7Для создания валют необходим плагин Medieval Factions"));
                return;
            } else {
                createCurrencyWithoutFaction(player, currencyName, maxEmission, walletManager);
                return;
            }
        }

        if (!mfDatabase.isPlayerInFaction(player)) {
            player.sendMessage(colorize("&cВы не состоите во фракции!"));
            player.sendMessage(colorize("&7Создайте фракцию: &f/faction create <название>"));
            return;
        }

        if (!mfDatabase.hasCurrencyManagePermission(player)) {
            player.sendMessage(colorize("&cУ вас нет прав на управление валютой фракции!"));
            player.sendMessage(colorize("&7Обратитесь к лидеру фракции за правами"));
            return;
        }

        String playerFactionId = mfDatabase.getPlayerFactionId(player);
        if (playerFactionId == null) {
            player.sendMessage(colorize("&cОшибка получения ID фракции!"));
            return;
        }

        // Проверяем что у фракции еще нет валюты
        Database database = Economy.getInstance().getDatabase();
        if (database.factionHasCurrency(playerFactionId)) {
            String existingCurrency = database.getFactionCurrency(playerFactionId);
            player.sendMessage(colorize("&cВаша фракция уже имеет валюту: " + existingCurrency));
            player.sendMessage(colorize("&7Одна фракция может иметь только одну валюту"));
            return;
        }

        // Создаем валюту
        createCurrencyWithFaction(player, currencyName, maxEmission, playerFactionId, walletManager);
    }

    private void createCurrencyWithoutFaction(Player player, String currencyName, long maxEmission, WalletManager walletManager) {
        String tempFactionId = "temp_" + player.getUniqueId().toString().substring(0, 8);

        if (walletManager.createCurrency(currencyName, tempFactionId, maxEmission)) {
            player.sendMessage(colorize("&a✓ Валюта " + currencyName + " успешно создана!"));
            player.sendMessage(colorize("&7Режим: &fТестовый (без фракции)"));
            player.sendMessage(colorize("&7Максимальная эмиссия: &e" + String.format("%,d", maxEmission)));
            player.sendMessage(colorize("&7Текущая эмиссия: &f0"));
            player.sendMessage(colorize("&e"));
            player.sendMessage(colorize("&7Используйте &f/emit " + currencyName + " <количество> &7для выпуска валюты"));

            Database database = Economy.getInstance().getDatabase();
            database.logTransaction("SYSTEM", player.getName(), currencyName, 0,
                    "CURRENCY_CREATE", "Currency " + currencyName + " created in test mode");
        } else {
            player.sendMessage(colorize("&cОшибка при создании валюты!"));
        }
    }

    private void createCurrencyWithFaction(Player player, String currencyName, long maxEmission,
                                           String playerFactionId, WalletManager walletManager) {

        if (walletManager.createCurrency(currencyName, playerFactionId, maxEmission)) {
            String factionName = mfDatabase.getFactionName(playerFactionId);
            if (factionName == null) factionName = "Фракция #" + playerFactionId;

            player.sendMessage(colorize("&a✓ Валюта " + currencyName + " успешно создана!"));
            player.sendMessage(colorize("&7Фракция: &f" + factionName));
            player.sendMessage(colorize("&7Максимальная эмиссия: &e" + String.format("%,d", maxEmission)));
            player.sendMessage(colorize("&7Текущая эмиссия: &f0"));
            player.sendMessage(colorize("&e"));
            player.sendMessage(colorize("&7Используйте &f/emit " + currencyName + " <количество> &7для выпуска валюты"));

            Database database = Economy.getInstance().getDatabase();
            database.logTransaction("SYSTEM", player.getName(), currencyName, 0,
                    "CURRENCY_CREATE", "Currency " + currencyName + " created by faction " + factionName);
        } else {
            player.sendMessage(colorize("&cОшибка при создании валюты!"));
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

        if (mfDatabase == null) {
            mfDatabase = Economy.getInstance().getMedievalFactionsDatabase();
        }

        player.sendMessage(colorize("&6=== СТАТУС ==="));

        if (mfDatabase.isEnabled()) {
            player.sendMessage(colorize("&a✓ Medieval Factions подключен"));

            if (mfDatabase.isPlayerInFaction(player)) {
                String playerFactionId = mfDatabase.getPlayerFactionId(player);
                String factionName = mfDatabase.getFactionName(playerFactionId);

                player.sendMessage(colorize("&a✓ Вы состоите во фракции: &f" + factionName));

                if (mfDatabase.hasCurrencyManagePermission(player)) {
                    player.sendMessage(colorize("&a✓ У вас есть права на управление валютой"));
                } else {
                    player.sendMessage(colorize("&c✗ У вас нет прав на управление валютой"));
                }
            } else {
                player.sendMessage(colorize("&c✗ Вы не состоите во фракции"));
                player.sendMessage(colorize("&7  Создайте фракцию: &f/faction create <название>"));
            }
        } else {
            boolean allowWithoutFaction = Economy.getInstance().getConfig().getBoolean("medieval_factions.allow_without_faction", false);

            player.sendMessage(colorize("&c✗ Medieval Factions недоступен"));

            if (allowWithoutFaction) {
                player.sendMessage(colorize("&a✓ Тестовый режим включен"));
            } else {
                player.sendMessage(colorize("&7  Установите Medieval Factions"));
            }
        }
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