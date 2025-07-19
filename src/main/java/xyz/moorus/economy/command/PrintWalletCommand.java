package xyz.moorus.economy.command;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import xyz.moorus.economy.main.Economy;
import xyz.moorus.economy.money.PlayerWallet;
import xyz.moorus.economy.money.WalletManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PrintWalletCommand implements Command {

    @Override
    public String getName() {
        return "pw";
    }

    @Override
    public void execute(String sender, String[] args) {
        WalletManager walletManager = Economy.getInstance().getWalletManager();

        Player player = Bukkit.getPlayer(sender);
        if (player == null) return;

        int page = 1;
        if (args.length > 0) {
            try {
                page = Integer.parseInt(args[0]);
                if (page < 1) page = 1;
            } catch (NumberFormatException e) {
                player.sendMessage("§cНеверный номер страницы! Используйте: /pw [страница]");
                return;
            }
        }

        PlayerWallet wallet = walletManager.getPlayerWallet(sender);

        if (wallet.getSlots().isEmpty()) {
            player.sendMessage("§7Ваш кошелек пуст");
            return;
        }

        List<Map.Entry<String, Integer>> currencies = new ArrayList<>(wallet.getSlots().entrySet());

        // VIL валюта всегда первая
        currencies.sort((a, b) -> {
            if (a.getKey().equals("VIL")) return -1;
            if (b.getKey().equals("VIL")) return 1;
            return a.getKey().compareTo(b.getKey());
        });

        int itemsPerPage = 10;
        int totalPages = (int) Math.ceil((double) currencies.size() / itemsPerPage);

        if (page > totalPages) {
            player.sendMessage("§cСтраница не существует! Максимум: " + totalPages);
            return;
        }

        player.sendMessage("§6=== Ваш кошелек (стр. " + page + "/" + totalPages + ") ===");

        int startIndex = (page - 1) * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, currencies.size());

        for (int i = startIndex; i < endIndex; i++) {
            Map.Entry<String, Integer> entry = currencies.get(i);
            if (entry.getKey().equals("VIL")) {
                player.sendMessage("§e§l" + entry.getKey() + ": §f§l" + entry.getValue());
            } else {
                player.sendMessage("§7" + entry.getKey() + ": §f" + entry.getValue());
            }
        }

        if (totalPages > 1) {
            player.sendMessage("§7Используйте §f/pw " + (page + 1) + " §7для следующей страницы");
        }
    }
}