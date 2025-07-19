package xyz.moorus.economy.market;

public class Lot {
    private int id;
    private String sellerName;
    private String currency;
    private long price;
    private String itemData;

    public Lot(int id, String sellerName, String currency, long price, String itemData) {
        this.id = id;
        this.sellerName = sellerName;
        this.currency = currency;
        this.price = price;
        this.itemData = itemData;
    }

    // Геттеры
    public int getId() { return id; }
    public String getSellerName() { return sellerName; }
    public String getSellerNick() { return sellerName; } // ДОБАВЛЕНО для совместимости
    public String getCurrency() { return currency; }
    public long getPrice() { return price; }
    public int getPriceInt() { return (int) price; } // ДОБАВЛЕНО для совместимости
    public String getItemData() { return itemData; }

    // Сеттеры
    public void setId(int id) { this.id = id; }
    public void setSellerName(String sellerName) { this.sellerName = sellerName; }
    public void setCurrency(String currency) { this.currency = currency; }
    public void setPrice(long price) { this.price = price; }
    public void setItemData(String itemData) { this.itemData = itemData; }
}