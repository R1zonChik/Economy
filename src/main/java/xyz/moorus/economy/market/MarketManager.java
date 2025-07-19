package xyz.moorus.economy.market;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import xyz.moorus.economy.main.Economy;
import xyz.moorus.economy.money.PaymentResult;
import xyz.moorus.economy.money.WalletManager;
import xyz.moorus.economy.sql.Database;

public class MarketManager {

    private static MarketManager manager;

    public static synchronized MarketManager getManager() {
        if (manager == null) {
            manager = new MarketManager();
        }
        return manager;
    }

    public PaymentResult buyLot(int lotId, String buyerName) {
        Database db = Economy.getInstance().getDatabase();
        WalletManager wm = Economy.getInstance().getWalletManager();

        Lot lot = db.getLotById(lotId);
        if (lot == null) return PaymentResult.WRONG_AMOUNT;

        PaymentResult result = wm.getMoney(buyerName, lot.getCurrency(), lot.getPriceInt());
        wm.putMoney(lot.getSellerNick(), lot.getCurrency(), lot.getPriceInt());

        return result;
    }

    public boolean sellItem(String sellerName, ItemStack item, String currency, int price) {
        // TODO: Реализовать продажу предмета
        return false;
    }

    public boolean removeLot(int lotId) {
        // TODO: Реализовать удаление лота
        return false;
    }
}