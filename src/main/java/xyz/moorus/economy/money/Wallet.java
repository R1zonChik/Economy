package xyz.moorus.economy.money;

import java.util.TreeMap;

public abstract class Wallet {
    protected TreeMap<String, Integer> currencies = new TreeMap<String, Integer>();

    public int getCurrencyAmount(String currencyName) {
        if(currencies.get(currencyName) == null) return 0;
        return currencies.get(currencyName);
    }
    public void putCurrency(String currencyName, int amount) {
        if (currencies.get(currencyName) == null) currencies.put(currencyName, 0);
        currencies.put(currencyName, currencies.get(currencyName) + amount);
    }
    public void getCurrency(String currencyName, int amount) {
        if (!currencies.containsKey(currencyName)) throw new IllegalArgumentException("The wallet doesn't contain that currency");
        else if (currencies.get(currencyName) < amount ) throw new IllegalArgumentException("Not enough money");
        else {currencies.put(currencyName, currencies.get(currencyName) - amount);}
    }
}
