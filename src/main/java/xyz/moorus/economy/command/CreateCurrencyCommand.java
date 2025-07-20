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

        // Создаем новый массив для handleCreateCurrency
        String[] newArgs = new String[args.length + 1];
        newArgs[0] = "create"; // Добавляем команду create
        System.arraycopy(args, 0, newArgs, 1, args.length);

        // Вызываем метод создания валюты
        handleCreateCurrency(player, newArgs);
    }

    // ВОТ СЮДА ВСТАВЛЯЕШЬ ВЕСЬ МЕТОД handleCreateCurrency
    private void handleCreateCurrency(Player player, String[] args) {
        if (!player.hasPermission("economy.currency.create")) {
            player.sendMessage(colorize("&cУ вас нет прав на создание валют!"));
            return;
        }

        if (args.length != 3) {
            player.sendMessage(colorize("&cИспользование: /cc <название> <максимальная_эмиссия>"));
            player.sendMessage(colorize("&7Пример: /cc ABC 1000000"));
            return;
        }

        String currencyName = args[1].toUpperCase();
        long maxEmission;

        try {
            maxEmission = Long.parseLong(args[2]);
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

        // ИСПРАВЛЕННАЯ ПРОВЕРКА ФРАКЦИИ
        String playerFactionId = null;
        boolean hasPermission = false;

        // Проверяем через Medieval Factions API
        if (Bukkit.getPluginManager().getPlugin("MedievalFactions") != null) {
            try {
                // Используем рефлексию для безопасного вызова
                Object medievalFactions = Bukkit.getPluginManager().getPlugin("MedievalFactions");
                Object services = medievalFactions.getClass().getMethod("getServices").invoke(medievalFactions);
                Object playerService = services.getClass().getMethod("getPlayerService").invoke(services);
                Object factionService = services.getClass().getMethod("getFactionService").invoke(services);

                // Получаем MfPlayerId
                Class<?> mfPlayerIdClass = Class.forName("com.dansplugins.factionsystem.player.MfPlayerId");
                Object playerId = mfPlayerIdClass.getMethod("fromBukkitPlayer", org.bukkit.entity.Player.class).invoke(null, player);

                // Получаем игрока
                Object mfPlayer = playerService.getClass().getMethod("getPlayer", mfPlayerIdClass).invoke(playerService, playerId);

                if (mfPlayer != null) {
                    // Получаем ID фракции
                    Object factionId = mfPlayer.getClass().getMethod("getFactionId").invoke(mfPlayer);
                    if (factionId != null) {
                        // Получаем фракцию
                        Object faction = factionService.getClass().getMethod("getFaction", factionId.getClass()).invoke(factionService, factionId);
                        if (faction != null) {
                            playerFactionId = factionId.getClass().getMethod("getValue").invoke(factionId).toString();

                            // Проверяем права на управление валютой
                            Object permissions = medievalFactions.getClass().getMethod("getFactionPermissions").invoke(medievalFactions);
                            Object currencyManagePermission = permissions.getClass().getMethod("getCurrencyManage").invoke(permissions);

                            // Проверяем есть ли право
                            hasPermission = (boolean) faction.getClass().getMethod("hasPermission",
                                    playerId.getClass(), currencyManagePermission.getClass()).invoke(faction, playerId, currencyManagePermission);

                            if (!hasPermission) {
                                player.sendMessage(colorize("&cУ вас нет прав на управление валютой фракции!"));
                                player.sendMessage(colorize("&7Обратитесь к лидеру фракции за правами"));
                                return;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Economy.getInstance().getLogger().warning("Ошибка при работе с Medieval Factions: " + e.getMessage());
                // Продолжаем с временной системой
            }
        }

        // Если фракции нет или нет Medieval Factions, используем временную систему
        if (playerFactionId == null) {
            Database database = Economy.getInstance().getDatabase();
            playerFactionId = database.getPlayerFactionId(player.getName());

            if (playerFactionId == null) {
                // Создаем временную фракцию
                if (database.createTempFaction(player.getName())) {
                    playerFactionId = database.getPlayerFactionId(player.getName());
                    player.sendMessage(colorize("&7Создана временная фракция для управления валютой"));
                    hasPermission = true; // Владелец временной фракции имеет все права
                } else {
                    player.sendMessage(colorize("&cОшибка при создании временной фракции!"));
                    return;
                }
            } else {
                // Проверяем права в временной фракции
                hasPermission = database.hasPermissionInFaction(player.getName(), playerFactionId, "CURRENCY_MANAGE");
                if (!hasPermission) {
                    player.sendMessage(colorize("&cУ вас нет прав на управление валютой фракции!"));
                    return;
                }
            }
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
        if (walletManager.createCurrency(currencyName, playerFactionId, maxEmission)) {
            String factionName = database.getFactionName(playerFactionId);
            if (factionName == null) factionName = "Неизвестная фракция";

            player.sendMessage(colorize("&aВалюта " + currencyName + " успешно создана!"));
            player.sendMessage(colorize("&7Фракция: &f" + factionName));
            player.sendMessage(colorize("&7Максимальная эмиссия: &e" + String.format("%,d", maxEmission)));
            player.sendMessage(colorize("&7Текущая эмиссия: &f0"));
            player.sendMessage(colorize("&e"));
            player.sendMessage(colorize("&7Используйте &f/emit " + currencyName + " <количество> &7для выпуска валюты"));

            // Логируем создание валюты
            database.logTransaction("SYSTEM", player.getName(), currencyName, 0,
                    "CURRENCY_CREATE", "Currency " + currencyName + " created by faction " + factionName);

            Economy.getInstance().getLogger().info("Игрок " + player.getName() +
                    " создал валюту " + currencyName + " для фракции " + factionName);

        } else {
            player.sendMessage(colorize("&cОшибка при создании валюты!"));
            player.sendMessage(colorize("&7Возможно, валюта уже существует или произошла ошибка базы данных"));
        }
    }

    private void showHelp(Player player) {
        player.sendMessage(colorize("&6=== Создание валюты ==="));
        player.sendMessage(colorize("&7Команда: &f/cc <название> <максимальная_эмиссия>"));
        player.sendMessage(colorize("&7Пример: &f/cc ABC 1000000"));
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
        String examples = Economy.getInstance().getConfig().getString("messages.currency.allowed_examples", "&aПримеры: ABC, XYZ, FOO, BAR");

        player.sendMessage(colorize(requirements));
        player.sendMessage(colorize(reserved));
        player.sendMessage(colorize(examples));
    }

    private String colorize(String text) {
        return text.replace("&", "§");
    }
}