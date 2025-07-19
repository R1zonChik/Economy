package xyz.moorus.economy.market;

import java.util.TreeMap;

public class Bourse {
    private String sellCurrency;
    private String buyCurrency;
    private TreeMap<Integer, Order> orders;
    public Bourse (String sellCurrency, String buyCurrency, TreeMap<Integer, Order> orders) {
        this.sellCurrency = sellCurrency;
        this.buyCurrency = buyCurrency;
        this.orders = orders;
    }

    public int exchange(int id) {
        Order target = orders.get(id);
        int result = target.exchange(orders.get(id).getBuyAmount());
        orders.put(id, target);
        if(orders.get(id).isEmpty()) orders.remove(id);
        return result;
    };
    public void addOrder(int id, Order toAdd) {
        orders.put(id, toAdd);
    }

    public String getBuyCurrency() {
        return buyCurrency;
    }

    public String getSellCurrency() {
        return sellCurrency;
    }

    public TreeMap<Integer, Order> getOrders() {
        return orders;
    }

}
