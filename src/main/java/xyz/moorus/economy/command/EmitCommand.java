package xyz.moorus.economy.command;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import xyz.moorus.economy.main.Economy;
import xyz.moorus.economy.money.WalletManager;
import xyz.moorus.economy.sql.Database;

public class EmitCommand implements Command {

    @Override
    public String getName() {
        return "emit";
    }

    @Override
    public void execute(String sender, String[] args) {
        WalletManager walletManager = Economy.getInstance().getWalletManager();
        Database database = Economy.getInstance().getDatabase();

        Player player = Bukkit.getPlayer(sender);
        if (player == null) return;

        if (args.length != 2) {
            player.sendMessage("§cИспользование: /emit <валюта> <количество>");
            return;
        }

        String currency = args[0].toUpperCase();
        int amount;

        try {
            amount = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage("§cНеверное количество!");
            return;
        }

        if (!walletManager.currencyExists(currency)) {
            player.sendMessage("§cТакой валюты не существует!");
            return;
        }

        if (!walletManager.canEmitCurrency(currency, amount)) {
            player.sendMessage("§cПревышен лимит эмиссии!");
            return;
        }

        // РЕАЛИЗОВАНО: Проверка прав на эмиссию валюты
        String currencyFactionId = database.getCurrencyFaction(currency);
        if (currencyFactionId == null || currencyFactionId.equals("System")) {
            // Системная валюта - только админы
            if (!player.hasPermission("economy.admin")) {
                player.sendMessage("§cТолько администраторы могут выпускать системную валюту!");
                return;
            }
        } else {
            // Фракционная валюта - проверяем права в фракции
            if (!database.hasPermissionInFaction(sender, currencyFactionId, "CURRENCY_MANAGE")) {
                player.sendMessage("§cУ вас нет прав на выпуск этой валюты!");
                return;
            }
        }

        if (walletManager.emitCurrency(sender, currency, amount)) {
            player.sendMessage("§aВыпущено " + amount + " " + currency);
            player.sendMessage("§7Валюта добавлена в ваш кошелек");
        } else {
            player.sendMessage("§cОшибка при выпуске валюты!");
        }
    }
}