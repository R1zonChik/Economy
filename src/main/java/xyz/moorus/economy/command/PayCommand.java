package xyz.moorus.economy.command;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import xyz.moorus.economy.main.Economy;
import xyz.moorus.economy.money.PaymentResult;
import xyz.moorus.economy.money.WalletManager;

public class PayCommand implements Command {

    @Override
    public String getName() {
        return "pay";
    }

    @Override
    public void execute(String sender, String[] args) {
        WalletManager walletManager = Economy.getInstance().getWalletManager();

        Player player = Bukkit.getPlayer(sender);
        if (player == null) return;

        if (args.length != 3) {
            player.sendMessage(colorize("&cИспользование: /pay <игрок> <валюта> <сумма>"));
            player.sendMessage(colorize("&7Пример: /pay Steve VIL 100"));
            return;
        }

        String targetPlayer = args[0];
        String currency = args[1].toUpperCase();
        int amount;

        // Валидация суммы
        try {
            amount = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            String message = Economy.getInstance().getConfig().getString("messages.invalid_amount", "&cНеверная сумма!");
            player.sendMessage(colorize(message + " Введите число."));
            return;
        }

        // Проверка диапазона суммы
        if (amount <= 0) {
            player.sendMessage(colorize("&cСумма должна быть больше 0!"));
            return;
        }

        if (amount > 1000000000) {
            player.sendMessage(colorize("&cСлишком большая сумма! Максимум: 1,000,000,000"));
            return;
        }

        // Проверка валюты
        if (!walletManager.currencyExists(currency)) {
            String message = Economy.getInstance().getConfig().getString("messages.invalid_currency", "&cНеверная валюта!");
            player.sendMessage(colorize(message));
            return;
        }

        // Проверка получателя
        if (targetPlayer.equals(sender)) {
            player.sendMessage(colorize("&cВы не можете перевести деньги самому себе!"));
            return;
        }

        // Проверка существования получателя
        if (!walletManager.getDatabase().playerHasWallet(targetPlayer)) {
            String message = Economy.getInstance().getConfig().getString("messages.player_not_found", "&cИгрок не найден!");
            player.sendMessage(colorize(message));
            return;
        }

        // Проверка баланса
        if (walletManager.getPlayerWallet(sender).getCurrencyAmount(currency) < amount) {
            String message = Economy.getInstance().getConfig().getString("messages.not_enough_money", "&cНедостаточно средств!");
            player.sendMessage(colorize(message));
            player.sendMessage(colorize("&7У вас: " + walletManager.getPlayerWallet(sender).getCurrencyAmount(currency) + " " + currency));
            return;
        }

        // Выполняем перевод
        PaymentResult result = walletManager.pay(sender, targetPlayer, currency, amount);

        switch (result) {
            case SUCCESS:
                String successMessage = Economy.getInstance().getConfig().getString("messages.payment_success", "&aПеревод выполнен успешно!");
                player.sendMessage(colorize(successMessage));
                player.sendMessage(colorize("&7Переведено: " + amount + " " + currency + " → " + targetPlayer));

                Player target = Bukkit.getPlayer(targetPlayer);
                if (target != null) {
                    target.sendMessage(colorize("&aВы получили " + amount + " " + currency + " от " + sender));
                }
                break;

            case NOT_ENOUGH_MONEY:
                String notEnoughMessage = Economy.getInstance().getConfig().getString("messages.not_enough_money", "&cНедостаточно средств!");
                player.sendMessage(colorize(notEnoughMessage));
                break;

            case WRONG_CURRENCY:
                String wrongCurrencyMessage = Economy.getInstance().getConfig().getString("messages.invalid_currency", "&cНеверная валюта!");
                player.sendMessage(colorize(wrongCurrencyMessage));
                break;

            case WRONG_RECIPIENT:
                String wrongRecipientMessage = Economy.getInstance().getConfig().getString("messages.player_not_found", "&cИгрок не найден!");
                player.sendMessage(colorize(wrongRecipientMessage));
                break;

            case WRONG_AMOUNT:
                String wrongAmountMessage = Economy.getInstance().getConfig().getString("messages.invalid_amount", "&cНеверная сумма!");
                player.sendMessage(colorize(wrongAmountMessage));
                break;

            default:
                player.sendMessage(colorize("&cНеизвестная ошибка: " + result.name()));
                break;
        }
    }

    private String colorize(String text) {
        return text.replace("&", "§");
    }
}