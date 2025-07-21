package xyz.moorus.economy.command;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectOutputStream;
import xyz.moorus.economy.main.Economy;
import xyz.moorus.economy.money.PaymentResult;
import xyz.moorus.economy.money.PlayerWallet;
import xyz.moorus.economy.money.WalletManager;
import xyz.moorus.economy.sql.Database;

import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.stream.Collectors;

public class AdminCommand implements Command, TabCompleter {

    @Override
    public String getName() {
        return "ecoadmin";
    }

    @Override
    public void execute(String sender, String[] args) {
        Player player = Bukkit.getPlayer(sender);
        if (player == null) return;

        if (!player.hasPermission("economy.admin")) {
            player.sendMessage(colorize("&cУ вас нет прав администратора!"));
            return;
        }

        if (args.length == 0) {
            showHelp(player);
            return;
        }

        String action = args[0].toLowerCase();

        switch (action) {
            case "givemoney":
                handleGiveMoney(player, args);
                break;
            case "takemoney":
                handleTakeMoney(player, args);
                break;
            case "givevil":
                handleGiveVil(player, args);
                break;
            case "addpremium":
                handleAddPremium(player, args);
                break;
            case "removepremium":
                handleRemovePremium(player, args);
                break;
            case "listpremium":
                handleListPremium(player);
                break;
            case "wallet":
            case "кошелек":
                handleWallet(player, args);
                break;
            case "reload":
                handleReload(player);
                break;
            case "cleanup":
                handleCleanup(player);
                break;
            case "stats":
                handleStats(player);
                break;
            default:
                showHelp(player);
                break;
        }
    }

    @Override
    public List<String> onTabComplete(org.bukkit.command.CommandSender sender, org.bukkit.command.Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) return new ArrayList<>();
        Player player = (Player) sender;

        if (!player.hasPermission("economy.admin")) {
            return new ArrayList<>();
        }

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // Первый аргумент - подкоманды
            List<String> subcommands = Arrays.asList(
                    "givemoney", "takemoney", "givevil", "wallet",
                    "addpremium", "removepremium", "listpremium",
                    "reload", "cleanup", "stats"
            );

            return subcommands.stream()
                    .filter(sub -> sub.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        String subcommand = args[0].toLowerCase();

        switch (subcommand) {
            case "givemoney":
            case "takemoney":
                if (args.length == 2) {
                    // Второй аргумент - игроки
                    return getOnlinePlayerNames(args[1]);
                } else if (args.length == 3) {
                    // Третий аргумент - валюты
                    return getAvailableCurrencies(args[2]);
                } else if (args.length == 4) {
                    // Четвертый аргумент - количество (примеры)
                    return Arrays.asList("100", "1000", "10000", "100000");
                }
                break;

            case "givevil":
                if (args.length == 2) {
                    return getOnlinePlayerNames(args[1]);
                } else if (args.length == 3) {
                    return Arrays.asList("100", "1000", "5000", "10000");
                }
                break;

            case "wallet":
                if (args.length == 2) {
                    return getAllPlayerNames(args[1]);
                }
                break;

            case "addpremium":
                if (args.length == 2) {
                    return Arrays.asList("<название>", "Меч_Героя", "Зелье_Силы", "Редкий_Блок");
                } else if (args.length == 3) {
                    return Arrays.asList("100", "500", "1000", "5000", "10000");
                } else if (args.length == 4) {
                    return Arrays.asList("<описание>", "Мощное_оружие", "Полезное_зелье", "Строительный_материал");
                } else if (args.length == 5) {
                    return Arrays.asList("1", "5", "10", "50", "-1");
                }
                break;

            case "removepremium":
                if (args.length == 2) {
                    return getPremiumItemIds(args[1]);
                }
                break;
        }

        return completions;
    }

    private List<String> getOnlinePlayerNames(String partial) {
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(partial.toLowerCase()))
                .collect(Collectors.toList());
    }

    private List<String> getAllPlayerNames(String partial) {
        List<String> names = new ArrayList<>();

        // Добавляем онлайн игроков
        names.addAll(getOnlinePlayerNames(partial));

        // Добавляем оффлайн игроков из базы данных
        Database database = Economy.getInstance().getDatabase();
        List<String> offlinePlayers = database.getAllPlayerNames();

        names.addAll(offlinePlayers.stream()
                .filter(name -> name.toLowerCase().startsWith(partial.toLowerCase()))
                .limit(10) // Ограничиваем количество для производительности
                .collect(Collectors.toList()));

        return names.stream().distinct().collect(Collectors.toList());
    }

    private List<String> getAvailableCurrencies(String partial) {
        Database database = Economy.getInstance().getDatabase();
        List<String> currencies = database.getAllCurrencies();

        return currencies.stream()
                .filter(currency -> currency.toLowerCase().startsWith(partial.toLowerCase()))
                .collect(Collectors.toList());
    }

    private List<String> getPremiumItemIds(String partial) {
        Database database = Economy.getInstance().getDatabase();
        List<Map<String, Object>> items = database.getPremiumShopItems();

        return items.stream()
                .map(item -> String.valueOf(item.get("id")))
                .filter(id -> id.startsWith(partial))
                .collect(Collectors.toList());
    }

    private void handleGiveMoney(Player player, String[] args) {
        if (!player.hasPermission("economy.admin.givemoney")) {
            player.sendMessage(colorize("&cНет прав!"));
            return;
        }

        if (args.length != 4) {
            player.sendMessage(colorize("&cИспользование: /ecoadmin givemoney <игрок> <валюта> <количество>"));
            return;
        }

        String targetPlayer = args[1];
        String currency = args[2].toUpperCase();
        int amount;

        try {
            amount = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            player.sendMessage(colorize("&cНеверное количество!"));
            return;
        }

        if (amount <= 0) {
            player.sendMessage(colorize("&cКоличество должно быть больше 0!"));
            return;
        }

        WalletManager walletManager = Economy.getInstance().getWalletManager();
        Database database = Economy.getInstance().getDatabase();

        // Улучшенная проверка игрока
        if (!playerExists(targetPlayer)) {
            player.sendMessage(colorize("&cИгрок " + targetPlayer + " не найден!"));
            player.sendMessage(colorize("&7Убедитесь что игрок заходил на сервер"));
            return;
        }

        // Создаем кошелек если его нет
        if (!database.playerHasWallet(targetPlayer)) {
            database.createPlayer(targetPlayer, getPlayerUUID(targetPlayer));
        }

        if (!walletManager.currencyExists(currency)) {
            player.sendMessage(colorize("&cВалюта " + currency + " не существует!"));
            List<String> availableCurrencies = database.getAllCurrencies();
            if (!availableCurrencies.isEmpty()) {
                player.sendMessage(colorize("&7Доступные валюты: " + String.join(", ", availableCurrencies)));
            }
            return;
        }

        PaymentResult result = walletManager.putMoney(targetPlayer, currency, amount);
        if (result == PaymentResult.SUCCESS) {
            player.sendMessage(colorize("&aВыдано " + String.format("%,d", amount) + " " + currency + " игроку " + targetPlayer));

            Player target = Bukkit.getPlayer(targetPlayer);
            if (target != null) {
                target.sendMessage(colorize("&aВы получили " + String.format("%,d", amount) + " " + currency + " от администратора"));
            }

            database.logTransaction("ADMIN:" + player.getName(), targetPlayer, currency, amount, "ADMIN_GIVE", "Admin give money");
        } else {
            player.sendMessage(colorize("&cОшибка при выдаче денег: " + result.name()));
        }
    }

    private void handleTakeMoney(Player player, String[] args) {
        if (!player.hasPermission("economy.admin.takemoney")) {
            player.sendMessage(colorize("&cНет прав!"));
            return;
        }

        if (args.length != 4) {
            player.sendMessage(colorize("&cИспользование: /ecoadmin takemoney <игрок> <валюта> <количество>"));
            return;
        }

        String targetPlayer = args[1];
        String currency = args[2].toUpperCase();
        int amount;

        try {
            amount = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            player.sendMessage(colorize("&cНеверное количество!"));
            return;
        }

        if (amount <= 0) {
            player.sendMessage(colorize("&cКоличество должно быть больше 0!"));
            return;
        }

        WalletManager walletManager = Economy.getInstance().getWalletManager();
        Database database = Economy.getInstance().getDatabase();

        if (!playerExists(targetPlayer)) {
            player.sendMessage(colorize("&cИгрок " + targetPlayer + " не найден!"));
            return;
        }

        if (!database.playerHasWallet(targetPlayer)) {
            player.sendMessage(colorize("&cУ игрока " + targetPlayer + " нет кошелька!"));
            return;
        }

        if (!walletManager.currencyExists(currency)) {
            player.sendMessage(colorize("&cВалюта " + currency + " не существует!"));
            return;
        }

        // Проверяем баланс перед изъятием
        PlayerWallet wallet = walletManager.getPlayerWallet(targetPlayer);
        int currentAmount = wallet.getSlots().getOrDefault(currency, 0);

        if (currentAmount < amount) {
            player.sendMessage(colorize("&cУ игрока недостаточно средств!"));
            player.sendMessage(colorize("&7Доступно: " + String.format("%,d", currentAmount) + " " + currency));
            return;
        }

        PaymentResult result = walletManager.getMoney(targetPlayer, currency, amount);
        if (result == PaymentResult.SUCCESS) {
            player.sendMessage(colorize("&aИзъято " + String.format("%,d", amount) + " " + currency + " у игрока " + targetPlayer));

            Player target = Bukkit.getPlayer(targetPlayer);
            if (target != null) {
                target.sendMessage(colorize("&cУ вас изъято " + String.format("%,d", amount) + " " + currency + " администратором"));
            }

            database.logTransaction(targetPlayer, "ADMIN:" + player.getName(), currency, amount, "ADMIN_TAKE", "Admin take money");
        } else {
            player.sendMessage(colorize("&cОшибка при изъятии денег: " + result.name()));
        }
    }

    private void handleGiveVil(Player player, String[] args) {
        if (!player.hasPermission("economy.admin.premium")) {
            player.sendMessage(colorize("&cНет прав!"));
            return;
        }

        if (args.length != 3) {
            player.sendMessage(colorize("&cИспользование: /ecoadmin givevil <игрок> <количество>"));
            return;
        }

        String targetPlayer = args[1];
        int amount;

        try {
            amount = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            player.sendMessage(colorize("&cНеверное количество!"));
            return;
        }

        if (amount <= 0) {
            player.sendMessage(colorize("&cКоличество должно быть больше 0!"));
            return;
        }

        WalletManager walletManager = Economy.getInstance().getWalletManager();
        Database database = Economy.getInstance().getDatabase();

        if (!playerExists(targetPlayer)) {
            player.sendMessage(colorize("&cИгрок " + targetPlayer + " не найден!"));
            return;
        }

        // Создаем кошелек если его нет
        if (!database.playerHasWallet(targetPlayer)) {
            database.createPlayer(targetPlayer, getPlayerUUID(targetPlayer));
        }

        PaymentResult result = walletManager.putMoney(targetPlayer, "VIL", amount);
        if (result == PaymentResult.SUCCESS) {
            player.sendMessage(colorize("&aВыдано " + String.format("%,d", amount) + " VIL игроку " + targetPlayer));

            Player target = Bukkit.getPlayer(targetPlayer);
            if (target != null) {
                target.sendMessage(colorize("&6Вы получили " + String.format("%,d", amount) + " VIL от администратора!"));
            }

            database.logTransaction("ADMIN:" + player.getName(), targetPlayer, "VIL", amount, "ADMIN_GIVE_VIL", "Admin give VIL");
        } else {
            player.sendMessage(colorize("&cОшибка при выдаче VIL: " + result.name()));
        }
    }

    private void handleAddPremium(Player player, String[] args) {
        if (!player.hasPermission("economy.admin.premium")) {
            player.sendMessage(colorize("&cНет прав!"));
            return;
        }

        if (args.length < 4) {
            player.sendMessage(colorize("&cИспользование: /ecoadmin addpremium <название> <цена> <описание> [количество]"));
            player.sendMessage(colorize("&7Предмет должен быть в руке!"));
            return;
        }

        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        if (itemInHand == null || itemInHand.getType() == Material.AIR) {
            player.sendMessage(colorize("&cВозьмите предмет в руку!"));
            return;
        }

        String displayName = args[1];
        long price;
        int stock = -1;

        try {
            price = Long.parseLong(args[2]);
        } catch (NumberFormatException e) {
            player.sendMessage(colorize("&cНеверная цена!"));
            return;
        }

        if (price <= 0) {
            player.sendMessage(colorize("&cЦена должна быть больше 0!"));
            return;
        }

        StringBuilder description = new StringBuilder();
        int descStart = 3;
        int descEnd = args.length;

        if (args.length > 4) {
            try {
                stock = Integer.parseInt(args[args.length - 1]);
                descEnd = args.length - 1;
            } catch (NumberFormatException e) {
                // Последний аргумент не число
            }
        }

        for (int i = descStart; i < descEnd; i++) {
            if (i > descStart) description.append(" ");
            description.append(args[i]);
        }

        String itemData = serializeItem(itemInHand);
        if (itemData == null) {
            player.sendMessage(colorize("&cОшибка при сериализации предмета!"));
            return;
        }

        Database database = Economy.getInstance().getDatabase();
        int itemId = database.addPremiumShopItem(itemData, price, "PREMIUM", displayName, description.toString(), stock);

        if (itemId > 0) {
            player.sendMessage(colorize("&aПредмет добавлен в премиум магазин! ID: " + itemId));
            player.sendMessage(colorize("&7Название: &f" + displayName));
            player.sendMessage(colorize("&7Цена: &e" + String.format("%,d", price) + " VIL"));
            player.sendMessage(colorize("&7Описание: &f" + description.toString()));
            player.sendMessage(colorize("&7Количество: &f" + (stock == -1 ? "∞" : stock)));
        } else {
            player.sendMessage(colorize("&cОшибка при добавлении предмета!"));
        }
    }

    private void handleRemovePremium(Player player, String[] args) {
        if (!player.hasPermission("economy.admin.premium")) {
            player.sendMessage(colorize("&cНет прав!"));
            return;
        }

        if (args.length != 2) {
            player.sendMessage(colorize("&cИспользование: /ecoadmin removepremium <id>"));
            return;
        }

        int id;
        try {
            id = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage(colorize("&cНеверный ID!"));
            return;
        }

        Database database = Economy.getInstance().getDatabase();
        if (database.removePremiumShopItem(id)) {
            player.sendMessage(colorize("&aПредмет #" + id + " удален из премиум магазина!"));
        } else {
            player.sendMessage(colorize("&cПредмет не найден или ошибка при удалении!"));
        }
    }

    private void handleListPremium(Player player) {
        if (!player.hasPermission("economy.admin.premium")) {
            player.sendMessage(colorize("&cНет прав!"));
            return;
        }

        Database database = Economy.getInstance().getDatabase();
        List<Map<String, Object>> items = database.getPremiumShopItems();

        if (items.isEmpty()) {
            player.sendMessage(colorize("&7Премиум магазин пуст"));
            return;
        }

        player.sendMessage(colorize("&6=== Премиум магазин ==="));
        for (Map<String, Object> item : items) {
            int id = (Integer) item.get("id");
            String name = (String) item.get("display_name");
            String description = (String) item.get("description");
            long price = (Long) item.get("price");
            int stock = (Integer) item.get("stock");

            player.sendMessage(colorize("&7#" + id + " &f" + name));
            player.sendMessage(colorize("  &7Цена: &e" + String.format("%,d", price) + " VIL"));
            player.sendMessage(colorize("  &7Описание: &f" + description));
            player.sendMessage(colorize("  &7Количество: &f" + (stock == -1 ? "∞" : stock)));
        }
    }

    private void handleWallet(Player player, String[] args) {
        if (args.length != 2) {
            player.sendMessage(colorize("&cИспользование: /ecoadmin wallet <игрок>"));
            return;
        }

        String targetPlayer = args[1];
        Database database = Economy.getInstance().getDatabase();
        WalletManager walletManager = Economy.getInstance().getWalletManager();

        if (!playerExists(targetPlayer)) {
            player.sendMessage(colorize("&cИгрок " + targetPlayer + " не найден!"));
            return;
        }

        // ИСПРАВЛЕНО: Создаем кошелек если его нет
        if (!database.playerHasWallet(targetPlayer)) {
            database.createPlayer(targetPlayer, getPlayerUUID(targetPlayer));
            player.sendMessage(colorize("&7Кошелек создан для игрока " + targetPlayer));
        }

        PlayerWallet wallet = walletManager.getPlayerWallet(targetPlayer);
        player.sendMessage(colorize("&6=== Кошелек игрока " + targetPlayer + " ==="));

        if (wallet.getSlots().isEmpty()) {
            player.sendMessage(colorize("&7Кошелек пуст"));
            return;
        }

        // Сортируем валюты: VIL первая, остальные по алфавиту
        Map<String, Integer> sortedWallet = new TreeMap<>((a, b) -> {
            if (a.equals("VIL")) return -1;
            if (b.equals("VIL")) return 1;
            return a.compareTo(b);
        });
        sortedWallet.putAll(wallet.getSlots());

        for (Map.Entry<String, Integer> entry : sortedWallet.entrySet()) {
            String currency = entry.getKey();
            int amount = entry.getValue();

            if (amount > 0) {
                String prefix = currency.equals("VIL") ? "&6💰 " : "&f💰 ";
                player.sendMessage(colorize(prefix + currency + ": &f" + String.format("%,d", amount)));
            }
        }

        player.sendMessage(colorize("&7Всего валют: &f" + sortedWallet.size()));
    }

    private void handleReload(Player player) {
        if (!player.hasPermission("economy.admin.reload")) {
            player.sendMessage(colorize("&cНет прав!"));
            return;
        }

        Economy.getInstance().reloadConfig();
        player.sendMessage(colorize("&aКонфигурация перезагружена!"));
    }

    private void handleCleanup(Player player) {
        if (!player.hasPermission("economy.admin.cleanup")) {
            player.sendMessage(colorize("&cНет прав!"));
            return;
        }

        Database database = Economy.getInstance().getDatabase();
        database.cleanupExpiredAuctions();
        player.sendMessage(colorize("&aОчистка истекших аукционов выполнена!"));

        int daysToKeep = Economy.getInstance().getConfig().getInt("cleanup.transaction_days_to_keep", 30);
        database.cleanupOldTransactions(daysToKeep);
        player.sendMessage(colorize("&aОчистка старых транзакций выполнена!"));
    }

    private void handleStats(Player player) {
        if (!player.hasPermission("economy.admin")) {
            player.sendMessage(colorize("&cНет прав!"));
            return;
        }

        Database database = Economy.getInstance().getDatabase();

        player.sendMessage(colorize("&6=== Статистика Economy ==="));
        player.sendMessage(colorize("&7Активных ордеров на бирже: &f" + database.getTotalOrdersCount()));
        player.sendMessage(colorize("&7Предметов в премиум магазине: &f" + database.getPremiumShopItems().size()));

        long expiredCleaned = database.getStatistic("expired_auctions_cleaned");
        if (expiredCleaned > 0) {
            player.sendMessage(colorize("&7Очищено истекших аукционов: &f" + expiredCleaned));
        }

        player.sendMessage(colorize("&7Системная валюта: &eVIL"));
        player.sendMessage(colorize("&7Версия плагина: &f" + Economy.getInstance().getDescription().getVersion()));
    }

    private void showHelp(Player player) {
        player.sendMessage(colorize("&6=== Админские команды Economy ==="));

        if (player.hasPermission("economy.admin.givemoney")) {
            player.sendMessage(colorize("&7/ecoadmin givemoney <игрок> <валюта> <количество> &f- выдать деньги"));
        }

        if (player.hasPermission("economy.admin.takemoney")) {
            player.sendMessage(colorize("&7/ecoadmin takemoney <игрок> <валюта> <количество> &f- изъять деньги"));
        }

        player.sendMessage(colorize("&7/ecoadmin wallet <игрок> &f- просмотр кошелька игрока"));

        if (player.hasPermission("economy.admin.premium")) {
            player.sendMessage(colorize("&7/ecoadmin givevil <игрок> <количество> &f- выдать VIL"));
            player.sendMessage(colorize("&7/ecoadmin addpremium <название> <цена> <описание> [кол-во] &f- добавить в премиум магазин"));
            player.sendMessage(colorize("&7/ecoadmin removepremium <id> &f- удалить из премиум магазина"));
            player.sendMessage(colorize("&7/ecoadmin listpremium &f- список премиум предметов"));
        }

        if (player.hasPermission("economy.admin.reload")) {
            player.sendMessage(colorize("&7/ecoadmin reload &f- перезагрузить конфиг"));
        }

        if (player.hasPermission("economy.admin.cleanup")) {
            player.sendMessage(colorize("&7/ecoadmin cleanup &f- очистить истекшие аукционы"));
        }

        player.sendMessage(colorize("&7/ecoadmin stats &f- статистика плагина"));
    }

    private boolean playerExists(String playerName) {
        // Проверяем онлайн игроков
        if (Bukkit.getPlayer(playerName) != null) {
            return true;
        }

        // Проверяем оффлайн игроков
        @SuppressWarnings("deprecation")
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerName);
        return offlinePlayer.hasPlayedBefore() || offlinePlayer.isOnline();
    }

    private String getPlayerUUID(String playerName) {
        Player onlinePlayer = Bukkit.getPlayer(playerName);
        if (onlinePlayer != null) {
            return onlinePlayer.getUniqueId().toString();
        }

        @SuppressWarnings("deprecation")
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerName);
        return offlinePlayer.getUniqueId().toString();
    }

    private String serializeItem(ItemStack item) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);
            dataOutput.writeObject(item);
            dataOutput.close();
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (Exception e) {
            Economy.getInstance().getLogger().warning("Ошибка сериализации предмета: " + e.getMessage());
            return null;
        }
    }

    private String colorize(String text) {
        return text.replace("&", "§");
    }
}