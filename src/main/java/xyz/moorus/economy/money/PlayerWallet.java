package xyz.moorus.economy.money;

import java.util.TreeMap;

public class PlayerWallet extends Wallet {
    private final String nickname;
    public PlayerWallet (String nickname) {this.nickname = nickname;}
    public PlayerWallet (String nickname, TreeMap<String, Integer> currencies) {
        this.nickname = nickname;
        this.currencies = currencies;
    }

    public String getNickname() {return nickname;}
    public TreeMap<String, Integer> getSlots() {return this.currencies;}

}
