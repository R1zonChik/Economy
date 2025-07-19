package xyz.moorus.economy.market;

public class Order implements Comparable<Order> {
    private String nickname;
    private String sellCurrency;
    private String buyCurrency;
    private int sell;
    private int buy;
    private double price;
    private boolean empty = false;
    private int id;

    public Order(int id, String nickname, String sellCurrency, String buyCurrency, int sellAmount, int buyAmount) {
        this.sellCurrency = sellCurrency;
        this.buyCurrency = buyCurrency;
        this.sell = sellAmount;
        this.buy = buyAmount;
        this.price = ((double)buy) / ((double)sell);
        this.id = id;
        this.nickname = nickname;
    }

    public int exchange(int putCurrencyAmount) {
        if (putCurrencyAmount <= 0) throw new IllegalArgumentException("Put currency must be more then zero");
        if (putCurrencyAmount >= buy) {
            sell = 0;
            buy = 0;
            empty = true;
            return sell;
        } else {
            buy -= putCurrencyAmount;
            sell -= (int) Math.floor(putCurrencyAmount / price);
            if (sell == 0) empty = true;
            return (int) Math.floor(putCurrencyAmount / price);

        }
    }
    public double getPrice() {return price;}
    public boolean isEmpty() {return empty;}
    public int getBuyAmount() {return buy;}
    public int getSellAmount() {return sell;}
    public String getSellCurrency() {return sellCurrency;}
    public String getBuyCurrency() {return buyCurrency;}
    public String getNickname() {return nickname;}
    public int getId(){return id;}

    @Override
    public int compareTo(Order o) {
        return (int)(1000 * (o.getPrice() - this.getPrice()));
    }
}
