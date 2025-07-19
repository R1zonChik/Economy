package xyz.moorus.economy.market;

import java.util.TreeSet;

public class MarketPage {
    private TreeSet<Lot> lots = new TreeSet<>();
    private LotSortType sortType;

    public MarketPage(LotSortType sortType, TreeSet<Lot> lots) {
        this.sortType = sortType;
        this.lots = lots;
    }

    public TreeSet<Lot> getLots() {
        return this.lots;
    }

    public LotSortType getSortType() {
        return sortType;
    }
}