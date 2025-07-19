package xyz.moorus.economy.command;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.io.BukkitObjectInputStream;
import xyz.moorus.economy.main.Economy;
import xyz.moorus.economy.money.PaymentResult;
import xyz.moorus.economy.money.WalletManager;
import xyz.moorus.economy.sql.Database;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AuctionCommand implements Command, Listener {

    private Map<String, String> playerMenus = new HashMap<>();
    private Map<String, Integer> playerPages = new HashMap<>();
    private Map<String, String> playerCurrency = new HashMap<>();
    private Map<String, String> playerCategory = new HashMap<>();

    public AuctionCommand() {
        Bukkit.getPluginManager().registerEvents(this, Economy.getInstance());
    }

    @Override
    public String getName() {
        return "ah";
    }

    @Override
    public void execute(String sender, String[] args) {
        Player player = Bukkit.getPlayer(sender);
        if (player == null) return;

        String currency = args.length > 0 ? args[0].toUpperCase() : null;
        String category = args.length > 1 ? args[1].toUpperCase() : "ALL";

        if (args.length > 0 && args[0].equalsIgnoreCase("categories")) {
            openCategoriesMenu(player, currency);
            return;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("expired")) {
            openExpiredItemsMenu(player);
            return;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("premium")) {
            openPremiumShop(player);
            return;
        }

        openAuctionMenu(player, currency, category, 0);
    }

    private void openAuctionMenu(Player player, String currency, String category, int page) {
        Database database = Economy.getInstance().getDatabase();

        playerMenus.put(player.getName(), "auction");
        playerPages.put(player.getName(), page);
        playerCurrency.put(player.getName(), currency);
        playerCategory.put(player.getName(), category);

        String title = getConfigString("auction.gui.titles.main", "&6Аукцион");
        if (currency != null) title += " (" + currency + ")";
        if (!category.equals("ALL")) title += " - " + category;

        int size = Economy.getInstance().getConfig().getInt("auction.gui.size", 54);
        Inventory auctionGui = Bukkit.createInventory(null, size, colorize(title));

        int itemsPerPage = size - 9;
        List<Map<String, Object>> items = database.getAuctionItems(category, currency, page, itemsPerPage);

        int slot = 0;
        for (Map<String, Object> item : items) {
            if (slot >= itemsPerPage) break;
            ItemStack displayItem = createAuctionItemDisplay(item);
            auctionGui.setItem(slot++, displayItem);
        }

        addNavigationButtons(auctionGui, page, items.size() >= itemsPerPage);

        player.openInventory(auctionGui);

        String message = items.isEmpty() ?
                getConfigString("messages.auction.no_items", "&7На аукционе пока нет предметов") :
                getConfigString("messages.auction.items_found", "&7Найдено предметов: {count}").replace("{count}", String.valueOf(items.size()));

        player.sendMessage(colorize(message));
    }

    private void openCategoriesMenu(Player player, String currency) {
        playerMenus.put(player.getName(), "categories");
        playerCurrency.put(player.getName(), currency);

        String title = getConfigString("auction.gui.titles.categories", "&6Категории аукциона");
        Inventory categoriesGui = Bukkit.createInventory(null, 27, colorize(title));

        ConfigurationSection categoriesConfig = Economy.getInstance().getConfig().getConfigurationSection("auction.categories");

        if (categoriesConfig != null) {
            int slot = 10;
            for (String categoryKey : categoriesConfig.getKeys(false)) {
                ConfigurationSection categoryConfig = categoriesConfig.getConfigurationSection(categoryKey);
                if (categoryConfig != null) {
                    ItemStack categoryItem = createCategoryItem(categoryKey, categoryConfig);
                    categoriesGui.setItem(slot++, categoryItem);
                }
            }
        }

        ItemStack allItem = new ItemStack(Material.ENDER_CHEST);
        ItemMeta allMeta = allItem.getItemMeta();
        if (allMeta != null) {
            allMeta.setDisplayName(colorize("&aВсе категории"));
            allMeta.setLore(List.of(colorize("&7Показать все предметы")));
            allItem.setItemMeta(allMeta);
        }
        categoriesGui.setItem(22, allItem);

        player.openInventory(categoriesGui);
    }

    private void openExpiredItemsMenu(Player player) {
        playerMenus.put(player.getName(), "expired");
        Database database = Economy.getInstance().getDatabase();

        List<Map<String, Object>> expiredItems = database.getExpiredAuctionItems(player.getName());

        String title = getConfigString("auction.gui.titles.expired", "&cИстекшие предметы");
        Inventory expiredGui = Bukkit.createInventory(null, 54, colorize(title));

        int slot = 0;
        for (Map<String, Object> item : expiredItems) {
            if (slot >= 54) break;
            ItemStack displayItem = createExpiredItemDisplay(item);
            expiredGui.setItem(slot++, displayItem);
        }

        player.openInventory(expiredGui);

        if (expiredItems.isEmpty()) {
            player.sendMessage(colorize("&7У вас нет истекших предметов"));
        }
    }

    private void openPremiumShop(Player player) {
        playerMenus.put(player.getName(), "premium");
        Database database = Economy.getInstance().getDatabase();

        List<Map<String, Object>> premiumItems = database.getPremiumShopItems();

        String title = getConfigString("auction.gui.titles.premium", "&6Премиум магазин");
        Inventory premiumGui = Bukkit.createInventory(null, 54, colorize(title));

        int slot = 0;
        for (Map<String, Object> item : premiumItems) {
            if (slot >= 54) break;
            ItemStack displayItem = createPremiumItemDisplay(item);
            premiumGui.setItem(slot++, displayItem);
        }

        player.openInventory(premiumGui);

        if (premiumItems.isEmpty()) {
            player.sendMessage(colorize("&7Премиум магазин пуст"));
        }
    }

    private ItemStack createAuctionItemDisplay(Map<String, Object> item) {
        ItemStack displayItem = deserializeItem((String) item.get("item_data"));
        if (displayItem == null) displayItem = new ItemStack(Material.PAPER);

        ItemMeta meta = displayItem.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();

            List<String> auctionLore = Economy.getInstance().getConfig().getStringList("auction.item_display.auction_lore");
            for (String line : auctionLore) {
                line = line.replace("{seller}", (String) item.get("seller_name"))
                        .replace("{price}", String.valueOf(item.get("price")))
                        .replace("{currency}", (String) item.get("currency"))
                        .replace("{category}", (String) item.get("category"))
                        .replace("{expires}", (String) item.get("expires_at"));
                lore.add(colorize(line));
            }

            meta.setLore(lore);
            displayItem.setItemMeta(meta);
        }

        return displayItem;
    }

    private ItemStack createExpiredItemDisplay(Map<String, Object> item) {
        ItemStack displayItem = deserializeItem((String) item.get("item_data"));
        if (displayItem == null) displayItem = new ItemStack(Material.PAPER);

        ItemMeta meta = displayItem.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();

            List<String> expiredLore = Economy.getInstance().getConfig().getStringList("auction.item_display.expired_lore");
            for (String line : expiredLore) {
                line = line.replace("{price}", String.valueOf(item.get("price")))
                        .replace("{currency}", (String) item.get("currency"));
                lore.add(colorize(line));
            }

            meta.setLore(lore);
            displayItem.setItemMeta(meta);
        }

        return displayItem;
    }

    private ItemStack createPremiumItemDisplay(Map<String, Object> item) {
        ItemStack displayItem = deserializeItem((String) item.get("item_data"));
        if (displayItem == null) displayItem = new ItemStack(Material.PAPER);

        ItemMeta meta = displayItem.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(colorize("&6" + item.get("display_name")));

            List<String> lore = new ArrayList<>();
            List<String> premiumLore = Economy.getInstance().getConfig().getStringList("auction.item_display.premium_lore");
            for (String line : premiumLore) {
                line = line.replace("{description}", (String) item.get("description"))
                        .replace("{price}", String.valueOf(item.get("price")))
                        .replace("{stock}", ((Integer) item.get("stock")) == -1 ? "∞" : String.valueOf(item.get("stock")));
                lore.add(colorize(line));
            }

            meta.setLore(lore);
            displayItem.setItemMeta(meta);
        }

        return displayItem;
    }

    private ItemStack createCategoryItem(String categoryKey, ConfigurationSection config) {
        String materialName = config.getString("material", "CHEST");
        Material material;
        try {
            material = Material.valueOf(materialName);
        } catch (IllegalArgumentException e) {
            material = Material.CHEST;
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(colorize(config.getString("name", categoryKey)));

            List<String> lore = new ArrayList<>();
            for (String line : config.getStringList("lore")) {
                lore.add(colorize(line));
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    private void addNavigationButtons(Inventory gui, int page, boolean hasNextPage) {
        int categoriesSlot = Economy.getInstance().getConfig().getInt("auction.gui.buttons.categories", 49);
        gui.setItem(categoriesSlot, createNavigationButton("categories"));

        int expiredSlot = Economy.getInstance().getConfig().getInt("auction.gui.buttons.expired", 53);
        gui.setItem(expiredSlot, createNavigationButton("expired"));

        int premiumSlot = Economy.getInstance().getConfig().getInt("auction.gui.buttons.premium", 45);
        gui.setItem(premiumSlot, createNavigationButton("premium"));

        if (page > 0) {
            int prevSlot = Economy.getInstance().getConfig().getInt("auction.gui.buttons.prev_page", 48);
            gui.setItem(prevSlot, createNavigationButton("prev_page"));
        }

        if (hasNextPage) {
            int nextSlot = Economy.getInstance().getConfig().getInt("auction.gui.buttons.next_page", 50);
            gui.setItem(nextSlot, createNavigationButton("next_page"));
        }
    }

    private ItemStack createNavigationButton(String buttonType) {
        ConfigurationSection buttonConfig = Economy.getInstance().getConfig().getConfigurationSection("auction.gui.items." + buttonType);
        if (buttonConfig == null) return new ItemStack(Material.BARRIER);

        String materialName = buttonConfig.getString("material", "BARRIER");
        Material material;
        try {
            material = Material.valueOf(materialName);
        } catch (IllegalArgumentException e) {
            material = Material.BARRIER;
        }

        ItemStack button = new ItemStack(material);
        ItemMeta meta = button.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(colorize(buttonConfig.getString("name", "Button")));

            List<String> lore = new ArrayList<>();
            for (String line : buttonConfig.getStringList("lore")) {
                lore.add(colorize(line));
            }
            meta.setLore(lore);
            button.setItemMeta(meta);
        }

        return button;
    }

    // ==================== ОБРАБОТЧИКИ СОБЫТИЙ ====================

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        // ВАЖНО: Проверяем что это НАШЕ меню!
        String menuType = playerMenus.get(player.getName());
        if (menuType == null) return; // Не наше меню - не трогаем!

        // Проверяем что заголовок инвентаря соответствует нашим меню
        String title = event.getView().getTitle();
        if (!isOurInventory(title)) {
            return; // Не наш инвентарь - не трогаем!
        }

        // ИСПРАВЛЕНИЕ БАГА: ОТМЕНЯЕМ ВСЕ КЛИКИ В НАШИХ МЕНЮ
        event.setCancelled(true);

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        int slot = event.getSlot();

        // Проверяем что клик в верхнем инвентаре (наше меню)
        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            return; // Клик в инвентаре игрока - не обрабатываем
        }

        switch (menuType) {
            case "auction":
                handleAuctionClick(player, slot, event.isLeftClick());
                break;
            case "categories":
                handleCategoryClick(player, slot);
                break;
            case "expired":
                handleExpiredClick(player, slot);
                break;
            case "premium":
                handlePremiumClick(player, slot);
                break;
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();

        // Очищаем данные игрока при закрытии инвентаря
        String title = event.getView().getTitle();
        if (isOurInventory(title)) {
            playerMenus.remove(player.getName());
            playerPages.remove(player.getName());
            playerCurrency.remove(player.getName());
            playerCategory.remove(player.getName());
        }
    }

    private boolean isOurInventory(String title) {
        // Проверяем что это один из наших инвентарей
        String cleanTitle = title.replace("§", "&");
        return cleanTitle.contains("Аукцион") ||
                cleanTitle.contains("Категории аукциона") ||
                cleanTitle.contains("Истекшие предметы") ||
                cleanTitle.contains("Премиум магазин") ||
                cleanTitle.contains("Auction") ||
                cleanTitle.contains("Categories") ||
                cleanTitle.contains("Expired") ||
                cleanTitle.contains("Premium");
    }

    private void handleAuctionClick(Player player, int slot, boolean leftClick) {
        int categoriesSlot = Economy.getInstance().getConfig().getInt("auction.gui.buttons.categories", 49);
        int expiredSlot = Economy.getInstance().getConfig().getInt("auction.gui.buttons.expired", 53);
        int premiumSlot = Economy.getInstance().getConfig().getInt("auction.gui.buttons.premium", 45);
        int nextSlot = Economy.getInstance().getConfig().getInt("auction.gui.buttons.next_page", 50);
        int prevSlot = Economy.getInstance().getConfig().getInt("auction.gui.buttons.prev_page", 48);

        if (slot == categoriesSlot) {
            openCategoriesMenu(player, playerCurrency.get(player.getName()));
        } else if (slot == expiredSlot) {
            openExpiredItemsMenu(player);
        } else if (slot == premiumSlot) {
            openPremiumShop(player);
        } else if (slot == nextSlot) {
            int currentPage = playerPages.getOrDefault(player.getName(), 0);
            openAuctionMenu(player, playerCurrency.get(player.getName()),
                    playerCategory.get(player.getName()), currentPage + 1);
        } else if (slot == prevSlot) {
            int currentPage = playerPages.getOrDefault(player.getName(), 0);
            if (currentPage > 0) {
                openAuctionMenu(player, playerCurrency.get(player.getName()),
                        playerCategory.get(player.getName()), currentPage - 1);
            }
        } else {
            if (leftClick) {
                buyAuctionItem(player, slot);
            } else {
                showItemDetails(player, slot);
            }
        }
    }

    private void handleCategoryClick(Player player, int slot) {
        if (slot == 22) { // Все категории
            openAuctionMenu(player, playerCurrency.get(player.getName()), "ALL", 0);
            return;
        }

        ConfigurationSection categoriesConfig = Economy.getInstance().getConfig().getConfigurationSection("auction.categories");
        if (categoriesConfig == null) return;

        // ИСПРАВЛЕНО: Правильный маппинг слотов к категориям
        int categoryIndex = slot - 10;
        List<String> categories = new ArrayList<>(categoriesConfig.getKeys(false));

        if (categoryIndex >= 0 && categoryIndex < categories.size()) {
            String category = categories.get(categoryIndex);
            openAuctionMenu(player, playerCurrency.get(player.getName()), category, 0);
        }
    }

    private void handleExpiredClick(Player player, int slot) {
        Database database = Economy.getInstance().getDatabase();
        List<Map<String, Object>> expiredItems = database.getExpiredAuctionItems(player.getName());

        if (slot >= expiredItems.size()) return;

        Map<String, Object> expiredItem = expiredItems.get(slot);
        int itemId = (Integer) expiredItem.get("id");
        ItemStack originalItem = deserializeItem((String) expiredItem.get("item_data"));

        if (originalItem != null) {
            player.getInventory().addItem(originalItem);
            database.removeExpiredAuctionItem(itemId);

            String message = getConfigString("messages.auction.item_returned", "&aПредмет возвращен в ваш инвентарь!");
            player.sendMessage(colorize(message));
            openExpiredItemsMenu(player);
        }
    }

    private void handlePremiumClick(Player player, int slot) {
        Database database = Economy.getInstance().getDatabase();
        WalletManager walletManager = Economy.getInstance().getWalletManager();

        List<Map<String, Object>> premiumItems = database.getPremiumShopItems();

        if (slot >= premiumItems.size()) return;

        Map<String, Object> premiumItem = premiumItems.get(slot);
        int itemId = (Integer) premiumItem.get("id");
        long price = (Long) premiumItem.get("price");

        if (walletManager.getPlayerWallet(player.getName()).getCurrencyAmount("VIL") < price) {
            player.sendMessage(colorize("&cНедостаточно VIL! Нужно: " + price));
            return;
        }

        PaymentResult result = walletManager.getMoney(player.getName(), "VIL", (int) price);
        if (result == PaymentResult.SUCCESS) {
            ItemStack purchasedItem = deserializeItem((String) premiumItem.get("item_data"));
            if (purchasedItem != null) {
                player.getInventory().addItem(purchasedItem);
                database.decreasePremiumShopStock(itemId);

                String message = getConfigString("messages.auction.item_bought", "&aПредмет куплен за {price} {currency}!")
                        .replace("{price}", String.valueOf(price))
                        .replace("{currency}", "VIL");
                player.sendMessage(colorize(message));
                openPremiumShop(player);
            }
        }
    }

    private void buyAuctionItem(Player player, int slot) {
        Database database = Economy.getInstance().getDatabase();
        WalletManager walletManager = Economy.getInstance().getWalletManager();

        String currency = playerCurrency.get(player.getName());
        String category = playerCategory.get(player.getName());
        int page = playerPages.getOrDefault(player.getName(), 0);
        int itemsPerPage = Economy.getInstance().getConfig().getInt("auction.gui.size", 54) - 9;

        List<Map<String, Object>> items = database.getAuctionItems(category, currency, page, itemsPerPage);

        if (slot >= items.size()) {
            player.sendMessage(colorize("&cПредмет не найден!"));
            return;
        }

        Map<String, Object> item = items.get(slot);
        int itemId = (Integer) item.get("id");
        String sellerName = (String) item.get("seller_name");
        String itemCurrency = (String) item.get("currency");
        long price = (Long) item.get("price");

        if (sellerName.equals(player.getName())) {
            player.sendMessage(colorize("&cВы не можете купить свой предмет!"));
            return;
        }

        if (walletManager.getPlayerWallet(player.getName()).getCurrencyAmount(itemCurrency) < price) {
            player.sendMessage(colorize("&cНедостаточно средств! Нужно: " + price + " " + itemCurrency));
            return;
        }

        Map<String, Object> currentItem = database.getAuctionItem(itemId);
        if (currentItem == null) {
            player.sendMessage(colorize("&cПредмет уже продан или снят с продажи!"));
            openAuctionMenu(player, currency, category, page);
            return;
        }

        PaymentResult result = walletManager.getMoney(player.getName(), itemCurrency, (int) price);
        if (result == PaymentResult.SUCCESS) {
            walletManager.putMoney(sellerName, itemCurrency, (int) price);

            ItemStack purchasedItem = deserializeItem((String) item.get("item_data"));
            if (purchasedItem != null) {
                player.getInventory().addItem(purchasedItem);
            }

            database.markAuctionItemSold(itemId);
            database.logTransaction(player.getName(), sellerName, itemCurrency, price,
                    "AUCTION_BUY", "Auction purchase: item #" + itemId);

            String buyMessage = getConfigString("messages.auction.item_bought", "&aВы купили предмет за {price} {currency}!")
                    .replace("{price}", String.valueOf(price))
                    .replace("{currency}", itemCurrency);
            player.sendMessage(colorize(buyMessage));

            Player seller = Bukkit.getPlayer(sellerName);
            if (seller != null) {
                String sellMessage = getConfigString("messages.auction.item_sold", "&aВаш предмет продан за {price} {currency}!")
                        .replace("{price}", String.valueOf(price))
                        .replace("{currency}", itemCurrency);
                seller.sendMessage(colorize(sellMessage));
            }

            openAuctionMenu(player, currency, category, page);
        } else {
            player.sendMessage(colorize("&cОшибка при покупке: " + result.name()));
        }
    }

    private void showItemDetails(Player player, int slot) {
        Database database = Economy.getInstance().getDatabase();

        String currency = playerCurrency.get(player.getName());
        String category = playerCategory.get(player.getName());
        int page = playerPages.getOrDefault(player.getName(), 0);
        int itemsPerPage = Economy.getInstance().getConfig().getInt("auction.gui.size", 54) - 9;

        List<Map<String, Object>> items = database.getAuctionItems(category, currency, page, itemsPerPage);

        if (slot >= items.size()) return;

        Map<String, Object> item = items.get(slot);

        player.sendMessage(colorize("&6=== Информация о предмете ==="));
        player.sendMessage(colorize("&7ID: &f" + item.get("id")));
        player.sendMessage(colorize("&7Продавец: &f" + item.get("seller_name")));
        player.sendMessage(colorize("&7Цена: &e" + item.get("price") + " " + item.get("currency")));
        player.sendMessage(colorize("&7Категория: &f" + item.get("category")));
        player.sendMessage(colorize("&7Выставлен: &f" + item.get("created_at")));
        player.sendMessage(colorize("&7Истекает: &f" + item.get("expires_at")));
    }

    private ItemStack deserializeItem(String itemData) {
        try {
            if (itemData.length() > 50 && !itemData.contains(":")) {
                byte[] data = Base64.getDecoder().decode(itemData);
                ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
                BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
                ItemStack item = (ItemStack) dataInput.readObject();
                dataInput.close();
                return item;
            } else {
                String[] parts = itemData.split(":");
                Material material = Material.valueOf(parts[0]);
                int amount = Integer.parseInt(parts[1]);
                return new ItemStack(material, amount);
            }
        } catch (Exception e) {
            Economy.getInstance().getLogger().warning("Ошибка десериализации предмета: " + e.getMessage());
            return new ItemStack(Material.PAPER);
        }
    }

    private String getConfigString(String path, String defaultValue) {
        return Economy.getInstance().getConfig().getString(path, defaultValue);
    }

    private String colorize(String text) {
        return text.replace("&", "§");
    }
}