package xyz.moorus.economy.market;

import xyz.moorus.economy.main.Economy;
import xyz.moorus.economy.money.PaymentResult;
import xyz.moorus.economy.money.WalletManager;
import xyz.moorus.economy.sql.Database;

import java.util.TreeMap;

public class BourseManager {

    private static BourseManager manager;

    public static synchronized BourseManager getInstance() {
        if (manager == null) {
            manager = new BourseManager();
        }
        return manager;
    }

    public ExchangeResult exchange(int orderId, String buyerName) {
        Database db = Economy.getInstance().getDatabase();
        WalletManager wm = Economy.getInstance().getWalletManager();

        Order order = db.getOrderByID(orderId);
        if (order == null) return ExchangeResult.ORDER_NOT_FOUND;

        TreeMap<Integer, Order> orders = db.getOrdersByBourse(order.getSellCurrency(), order.getBuyCurrency());

        if (!orders.containsKey(orderId)) return ExchangeResult.ORDER_NOT_FOUND;

        Order targetOrder = orders.get(orderId);
        PaymentResult result = wm.getMoney(buyerName, targetOrder.getBuyCurrency(), targetOrder.getBuyAmount());

        if (result == PaymentResult.SUCCESS) {
            TreeMap<Integer, Order> updatedOrders = db.getOrdersByBourse(targetOrder.getSellCurrency(), targetOrder.getBuyCurrency());

            for (Order orderEntry : updatedOrders.values()) {
                if (orderEntry.getId() == orderId) {
                    db.deleteOrder(orderId);
                    break;
                }
                db.updateOrder(orderEntry);
            }

            wm.putMoney(targetOrder.getNickname(), targetOrder.getSellCurrency(), targetOrder.getSellAmount());
            wm.putMoney(buyerName, targetOrder.getSellCurrency(), targetOrder.getSellAmount());

            return ExchangeResult.SUCCESS;
        }

        return ExchangeResult.FAILED;
    }

    public boolean addOrder(Order order) {
        Database db = Economy.getInstance().getDatabase();
        WalletManager wm = Economy.getInstance().getWalletManager();

        PaymentResult result = wm.getMoney(order.getNickname(), order.getSellCurrency(), order.getSellAmount());
        if (result == PaymentResult.SUCCESS) {
            TreeMap<Integer, Order> orders = db.getOrdersByBourse(order.getSellCurrency(), order.getBuyCurrency());
            return db.addOrder(order);
        }
        return false;
    }

    public Bourse getBourse(String sellCurrency, String buyCurrency) {
        Database db = Economy.getInstance().getDatabase();
        TreeMap<Integer, Order> orders = db.getOrdersByBourse(sellCurrency, buyCurrency);
        return new Bourse(sellCurrency, buyCurrency, orders);
    }
}