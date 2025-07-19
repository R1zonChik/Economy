package xyz.moorus.economy.main;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import xyz.moorus.economy.money.WalletManager;

public class MoneyExpansion extends PlaceholderExpansion {

    private WalletManager walletManager;

    public MoneyExpansion(WalletManager walletManager) {
        this.walletManager = walletManager;
    }

    @Override
    public String getIdentifier() {
        return "economy";
    }

    @Override
    public String getAuthor() {
        return "Moorus";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (player == null) {
            return "";
        }

        // %economy_balance_VIL%
        if (params.startsWith("balance_")) {
            String currency = params.substring(8);
            return String.valueOf((int) walletManager.getBalance(player.getUniqueId(), currency));
        }

        // %economy_wallet%
        if (params.equals("wallet")) {
            return walletManager.getWallet(player.getUniqueId()).toString();
        }

        return null;
    }
}