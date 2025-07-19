package xyz.moorus.economy.command;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import xyz.moorus.economy.main.Economy;
import xyz.moorus.economy.money.PlayerWallet;
import xyz.moorus.economy.money.WalletManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class WalletCommand implements Command {

    @Override
    public String getName() {
        return "pw";
    }

    @Override
    public void execute(String sender, String[] args) {
        Player player = Bukkit.getPlayer(sender);
        if (player == null) return;

        int page = 1;

        // Проверяем аргумент страницы
        if (args.length > 0) {
            try {
                page = Integer.parseInt(args[0]);
                if (page < 1) {
                    player.sendMessage(colorize("&cОшибка: номер страницы должен быть больше 0!"));
                    return;
                }
            } catch (NumberFormatException e) {
                player.sendMessage(colorize("&cОшибка: неверный номер страницы!"));
                player.sendMessage(colorize("&7Использование: /pw [страница]"));
                return;
            }
        }

        WalletManager walletManager = Economy.getInstance().getWalletManager();
        PlayerWallet wallet = walletManager.getPlayerWallet(player.getName());

        Map<String, Integer> currencies = wallet.getSlots();

        if (currencies.isEmpty()) {
            player.sendMessage(colorize("&7Ваш кошелек пуст"));
            return;
        }

        // Сортируем валюты: VIL первая, остальные по алфавиту
        List<String> sortedCurrencies = new ArrayList<>();
        if (currencies.containsKey("VIL")) {
            sortedCurrencies.add("VIL");
        }

        currencies.keySet().stream()
                .filter(currency -> !currency.equals("VIL"))
                .sorted()
                .forEach(sortedCurrencies::add);

        int currenciesPerPage = Economy.getInstance().getConfig().getInt("wallet.currencies_per_page", 10);
        int totalPages = (int) Math.ceil((double) sortedCurrencies.size() / currenciesPerPage);

        if (page > totalPages) {
            player.sendMessage(colorize("&cОшибка: страница " + page + " не существует!"));
            player.sendMessage(colorize("&7Всего страниц: " + totalPages));
            return;
        }

        int startIndex = (page - 1) * currenciesPerPage;
        int endIndex = Math.min(startIndex + currenciesPerPage, sortedCurrencies.size());

        // Заголовок
        player.sendMessage(colorize("&6=== Ваш кошелек (страница " + page + "/" + totalPages + ") ==="));

        // Отображаем валюты
        for (int i = startIndex; i < endIndex; i++) {
            String currency = sortedCurrencies.get(i);
            int amount = currencies.get(currency);

            if (currency.equals("VIL")) {
                // VIL выделяем особо
                player.sendMessage(colorize("&6⭐ VIL: &e" + String.format("%,d", amount) + " &7(Премиум валюта)"));
            } else {
                player.sendMessage(colorize("&7" + currency + ": &f" + String.format("%,d", amount)));
            }
        }

        // Навигация
        if (totalPages > 1) {
            StringBuilder navigation = new StringBuilder("&7Страницы: ");

            if (page > 1) {
                navigation.append("&a[←Пред] ");
            }

            navigation.append("&f").append(page).append("&7/&f").append(totalPages);

            if (page < totalPages) {
                navigation.append(" &a[След→]");
            }

            player.sendMessage(colorize(navigation.toString()));

            if (page < totalPages) {
                player.sendMessage(colorize("&7Используйте &f/pw " + (page + 1) + " &7для следующей страницы"));
            }
        }

        // Итого валют
        player.sendMessage(colorize("&7Всего валют: &f" + currencies.size()));
    }

    private String colorize(String text) {
        return text.replace("&", "§");
    }
}