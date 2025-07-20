package xyz.moorus.economy.command;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.io.BukkitObjectInputStream;
import xyz.moorus.economy.main.Economy;
import xyz.moorus.economy.money.PaymentResult;
import xyz.moorus.economy.money.WalletManager;
import xyz.moorus.economy.sql.Database;

import java.io.ByteArrayInputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

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

        // Всегда открываем главное меню
        openMainMenu(player);
    }

    private void openMainMenu(Player player) {
        playerMenus.put(player.getName(), "main");
        playerPages.put(player.getName(), 0);
        playerCurrency.remove(player.getName());
        playerCategory.remove(player.getName());

        Inventory inv = Bukkit.createInventory(null, 54, colorize("&6§lАУКЦИОН"));

        // === ВЫБОР ВАЛЮТЫ (верхний ряд) ===

        // VIL - Премиум валюта
        ItemStack vilItem = new ItemStack(Material.EMERALD);
        ItemMeta vilMeta = vilItem.getItemMeta();
        vilMeta.setDisplayName(colorize("&a§l⭐ VIL АУКЦИОН ⭐"));
        List<String> vilLore = new ArrayList<>();
        vilLore.add(colorize("&7Премиум валюта сервера"));
        vilLore.add(colorize("&7Эксклюзивные предметы"));
        vilLore.add(colorize("&e"));
        vilLore.add(colorize("&aНажмите для просмотра"));
        vilMeta.setLore(vilLore);
        vilMeta.addEnchant(Enchantment.UNBREAKING, 1, true);
        vilMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        vilItem.setItemMeta(vilMeta);
        inv.setItem(1, vilItem);

        // Получаем другие валюты
        Set<String> currencies = new HashSet<>();
        Database database = Economy.getInstance().getDatabase();
        try (Connection connection = database.getConnection();
             PreparedStatement stmt = connection.prepareStatement("SELECT DISTINCT currency_name FROM currencies WHERE currency_name != 'VIL'")) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                currencies.add(rs.getString("currency_name"));
            }
        } catch (SQLException e) {
            Economy.getInstance().getLogger().severe("Ошибка получения валют: " + e.getMessage());
        }

        // Добавляем другие валюты
        int currencySlot = 3;
        for (String currency : currencies) {
            if (currencySlot >= 7) break;

            ItemStack currencyItem = new ItemStack(Material.GOLD_INGOT);
            ItemMeta currencyMeta = currencyItem.getItemMeta();
            currencyMeta.setDisplayName(colorize("&6" + currency + " АУКЦИОН"));
            List<String> currencyLore = new ArrayList<>();
            currencyLore.add(colorize("&7Фракционная валюта"));
            currencyLore.add(colorize("&7Предметы за " + currency));
            currencyLore.add(colorize("&e"));
            currencyLore.add(colorize("&aНажмите для просмотра"));
            currencyMeta.setLore(currencyLore);
            currencyItem.setItemMeta(currencyMeta);
            inv.setItem(currencySlot, currencyItem);
            currencySlot += 2;
        }

        // Все валюты
        ItemStack allItem = new ItemStack(Material.CHEST);
        ItemMeta allMeta = allItem.getItemMeta();
        allMeta.setDisplayName(colorize("&b§lВСЕ ВАЛЮТЫ"));
        List<String> allLore = new ArrayList<>();
        allLore.add(colorize("&7Показать предметы за любую валюту"));
        allLore.add(colorize("&e"));
        allLore.add(colorize("&aНажмите для просмотра"));
        allMeta.setLore(allLore);
        allItem.setItemMeta(allMeta);
        inv.setItem(7, allItem);

        // === РАЗДЕЛИТЕЛЬ ===
        ItemStack separator = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta sepMeta = separator.getItemMeta();
        sepMeta.setDisplayName(" ");
        separator.setItemMeta(sepMeta);
        for (int i = 9; i < 18; i++) {
            inv.setItem(i, separator);
        }

        // === СПЕЦИАЛЬНЫЕ РАЗДЕЛЫ ===

        // Мои предметы
        ItemStack myItems = new ItemStack(Material.ENDER_CHEST);
        ItemMeta myMeta = myItems.getItemMeta();
        myMeta.setDisplayName(colorize("&b§lМОИ ПРЕДМЕТЫ"));
        List<String> myLore = new ArrayList<>();
        myLore.add(colorize("&7Истекшие лоты"));
        myLore.add(colorize("&7Снятые с продажи предметы"));
        myLore.add(colorize("&e"));
        myLore.add(colorize("&aНажмите для просмотра"));
        myMeta.setLore(myLore);
        myItems.setItemMeta(myMeta);
        inv.setItem(20, myItems);

        // Премиум магазин
        ItemStack premiumShop = new ItemStack(Material.NETHER_STAR);
        ItemMeta premiumMeta = premiumShop.getItemMeta();
        premiumMeta.setDisplayName(colorize("&6§l⭐ ПРЕМИУМ МАГАЗИН ⭐"));
        List<String> premiumLore = new ArrayList<>();
        premiumLore.add(colorize("&7Эксклюзивные предметы"));
        premiumLore.add(colorize("&7Только за VIL"));
        premiumLore.add(colorize("&e"));
        premiumLore.add(colorize("&aНажмите для просмотра"));
        premiumMeta.setLore(premiumLore);
        premiumMeta.addEnchant(Enchantment.UNBREAKING, 1, true);
        premiumMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        premiumShop.setItemMeta(premiumMeta);
        inv.setItem(24, premiumShop);

        // === ИНФОРМАЦИЯ ===
        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName(colorize("&e§lИНФОРМАЦИЯ"));
        List<String> infoLore = new ArrayList<>();
        infoLore.add(colorize("&7Как пользоваться аукционом:"));
        infoLore.add(colorize("&71. Выберите валюту"));
        infoLore.add(colorize("&72. Выберите категорию"));
        infoLore.add(colorize("&73. Просматривайте предметы"));
        infoLore.add(colorize("&e"));
        infoLore.add(colorize("&7ЛКМ - купить предмет"));
        infoLore.add(colorize("&7ПКМ - подробная информация"));
        infoMeta.setLore(infoLore);
        info.setItemMeta(infoMeta);
        inv.setItem(40, info);

        player.openInventory(inv);
    }

    private void openCurrencyMenu(Player player, String selectedCurrency) {
        playerMenus.put(player.getName(), "currency");
        playerCurrency.put(player.getName(), selectedCurrency);
        playerPages.put(player.getName(), 0);

        String title = selectedCurrency != null ?
                colorize("&6Аукцион - " + selectedCurrency) :
                colorize("&6Аукцион - Все валюты");

        Inventory inv = Bukkit.createInventory(null, 54, title);

        // === КАТЕГОРИИ ===
        String[] categories = {"BUILDING_BLOCKS", "DECORATIONS", "REDSTONE", "TRANSPORTATION",
                "MISCELLANEOUS", "FOOD", "TOOLS", "COMBAT", "BREWING"};
        String[] categoryNames = {"§6Блоки", "§5Декорации", "§cРедстоун", "§9Транспорт",
                "§7Разное", "§aЕда", "§eИнструменты", "§4Оружие", "§dЗелья"};

        for (int i = 0; i < categories.length && i < 45; i++) {
            ItemStack categoryItem = getCategoryIcon(categories[i]);
            ItemMeta meta = categoryItem.getItemMeta();
            meta.setDisplayName(colorize(categoryNames[i]));

            List<String> lore = new ArrayList<>();
            lore.add(colorize("&7Категория: " + categories[i]));

            // Подсчитываем предметы в категории
            int itemCount = getItemCountInCategory(categories[i], selectedCurrency);
            lore.add(colorize("&7Предметов: &f" + itemCount));

            if (selectedCurrency != null) {
                lore.add(colorize("&7Валюта: &f" + selectedCurrency));
                if ("VIL".equals(selectedCurrency)) {
                    lore.add(colorize("&a§l★ ПРЕМИУМ КАТЕГОРИЯ ★"));
                }
            } else {
                lore.add(colorize("&7Валюта: &fЛюбая"));
            }

            lore.add(colorize("&e"));
            if (itemCount > 0) {
                lore.add(colorize("&aНажмите для просмотра"));
            } else {
                lore.add(colorize("&7Предметов нет"));
            }
            meta.setLore(lore);
            categoryItem.setItemMeta(meta);

            inv.setItem(i, categoryItem);
        }

        // Кнопка "Все категории"
        ItemStack allCategories = new ItemStack(Material.ENDER_CHEST);
        ItemMeta allMeta = allCategories.getItemMeta();
        allMeta.setDisplayName(colorize("&b§lВСЕ КАТЕГОРИИ"));
        List<String> allLore = new ArrayList<>();
        allLore.add(colorize("&7Показать все предметы"));
        if (selectedCurrency != null) {
            allLore.add(colorize("&7Валюта: &f" + selectedCurrency));
        }
        allLore.add(colorize("&e"));
        allLore.add(colorize("&aНажмите для просмотра"));
        allMeta.setLore(allLore);
        allCategories.setItemMeta(allMeta);
        inv.setItem(49, allCategories);

        // Кнопка "Назад"
        ItemStack backButton = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backButton.getItemMeta();
        backMeta.setDisplayName(colorize("&c← Назад к выбору валют"));
        backMeta.setLore(List.of(colorize("&7Вернуться к главному меню")));
        backButton.setItemMeta(backMeta);
        inv.setItem(45, backButton);

        player.openInventory(inv);
    }

    private void openAuctionItems(Player player, String currency, String category, int page) {
        playerMenus.put(player.getName(), "items");
        playerCurrency.put(player.getName(), currency);
        playerCategory.put(player.getName(), category);
        playerPages.put(player.getName(), page);

        Database database = Economy.getInstance().getDatabase();
        int itemsPerPage = 45;
        List<Map<String, Object>> items = database.getAuctionItems(category, currency, page, itemsPerPage);

        String title = colorize("&6Предметы");
        if (currency != null) title += " - " + currency;
        if (category != null && !category.equals("ALL")) title += " - " + category;
        if (items.size() >= itemsPerPage) title += " (" + (page + 1) + ")";

        Inventory inv = Bukkit.createInventory(null, 54, title);

        // Добавляем предметы
        for (int i = 0; i < items.size() && i < 45; i++) {
            ItemStack displayItem = createAuctionItemDisplay(items.get(i));
            inv.setItem(i, displayItem);
        }

        // Навигация
        addItemsNavigation(inv, page, items.size() >= itemsPerPage);

        player.openInventory(inv);

        if (items.isEmpty()) {
            player.sendMessage(colorize("&7В данной категории нет предметов"));
        } else {
            player.sendMessage(colorize("&7Найдено предметов: " + items.size()));
        }
    }

    private void openExpiredItems(Player player, int page) {
        playerMenus.put(player.getName(), "expired");
        playerPages.put(player.getName(), page);

        Database database = Economy.getInstance().getDatabase();
        List<Map<String, Object>> expiredItems = database.getExpiredAuctionItems(player.getName());

        // Пагинация
        int itemsPerPage = 45;
        int totalPages = (int) Math.ceil((double) expiredItems.size() / itemsPerPage);
        int startIndex = page * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, expiredItems.size());

        String title = colorize("&cИстекшие предметы");
        if (totalPages > 1) {
            title += " (" + (page + 1) + "/" + totalPages + ")";
        }

        Inventory inv = Bukkit.createInventory(null, 54, title);

        // Добавляем предметы
        for (int i = startIndex; i < endIndex; i++) {
            ItemStack displayItem = createExpiredItemDisplay(expiredItems.get(i));
            inv.setItem(i - startIndex, displayItem);
        }

        // Навигация
        addExpiredNavigation(inv, page, totalPages);

        player.openInventory(inv);

        if (expiredItems.isEmpty()) {
            player.sendMessage(colorize("&7У вас нет истекших предметов"));
        } else {
            player.sendMessage(colorize("&7Истекших предметов: " + expiredItems.size()));
        }
    }

    private void openPremiumShop(Player player, int page) {
        playerMenus.put(player.getName(), "premium");
        playerPages.put(player.getName(), page);

        Database database = Economy.getInstance().getDatabase();
        List<Map<String, Object>> premiumItems = database.getPremiumShopItems();

        // Пагинация
        int itemsPerPage = 45;
        int totalPages = (int) Math.ceil((double) premiumItems.size() / itemsPerPage);
        int startIndex = page * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, premiumItems.size());

        String title = colorize("&6⭐ ПРЕМИУМ МАГАЗИН ⭐");
        if (totalPages > 1) {
            title += " (" + (page + 1) + "/" + totalPages + ")";
        }

        Inventory inv = Bukkit.createInventory(null, 54, title);

        // Добавляем предметы
        for (int i = startIndex; i < endIndex; i++) {
            ItemStack displayItem = createPremiumItemDisplay(premiumItems.get(i));
            inv.setItem(i - startIndex, displayItem);
        }

        // Навигация
        addPremiumNavigation(inv, page, totalPages);

        player.openInventory(inv);

        if (premiumItems.isEmpty()) {
            player.sendMessage(colorize("&7Премиум магазин пуст"));
        } else {
            player.sendMessage(colorize("&7Предметов в магазине: " + premiumItems.size()));
        }
    }

    private void addItemsNavigation(Inventory inv, int page, boolean hasNextPage) {
        // Назад к категориям
        ItemStack backButton = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backButton.getItemMeta();
        backMeta.setDisplayName(colorize("&c← Назад к категориям"));
        backMeta.setLore(List.of(colorize("&7Выбрать другую категорию")));
        backButton.setItemMeta(backMeta);
        inv.setItem(45, backButton);

        // Предыдущая страница
        if (page > 0) {
            ItemStack prevButton = new ItemStack(Material.ARROW);
            ItemMeta prevMeta = prevButton.getItemMeta();
            prevMeta.setDisplayName(colorize("&e← Предыдущая страница"));
            prevMeta.setLore(List.of(colorize("&7Страница " + page)));
            prevButton.setItemMeta(prevMeta);
            inv.setItem(48, prevButton);
        }

        // Следующая страница
        if (hasNextPage) {
            ItemStack nextButton = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = nextButton.getItemMeta();
            nextMeta.setDisplayName(colorize("&eСледующая страница →"));
            nextMeta.setLore(List.of(colorize("&7Страница " + (page + 2))));
            nextButton.setItemMeta(nextMeta);
            inv.setItem(50, nextButton);
        }

        // Главное меню
        ItemStack homeButton = new ItemStack(Material.COMPASS);
        ItemMeta homeMeta = homeButton.getItemMeta();
        homeMeta.setDisplayName(colorize("&6🏠 Главное меню"));
        homeMeta.setLore(List.of(colorize("&7Вернуться к выбору валют")));
        homeButton.setItemMeta(homeMeta);
        inv.setItem(49, homeButton);

        // Обновить
        ItemStack refreshButton = new ItemStack(Material.CLOCK);
        ItemMeta refreshMeta = refreshButton.getItemMeta();
        refreshMeta.setDisplayName(colorize("&b🔄 Обновить"));
        refreshMeta.setLore(List.of(colorize("&7Обновить список предметов")));
        refreshButton.setItemMeta(refreshMeta);
        inv.setItem(53, refreshButton);
    }

    private void addExpiredNavigation(Inventory inv, int page, int totalPages) {
        // Назад
        ItemStack backButton = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backButton.getItemMeta();
        backMeta.setDisplayName(colorize("&c← Назад к главному меню"));
        backButton.setItemMeta(backMeta);
        inv.setItem(49, backButton);

        // Предыдущая страница
        if (page > 0) {
            ItemStack prevButton = new ItemStack(Material.ARROW);
            ItemMeta prevMeta = prevButton.getItemMeta();
            prevMeta.setDisplayName(colorize("&e← Предыдущая страница"));
            prevButton.setItemMeta(prevMeta);
            inv.setItem(48, prevButton);
        }

        // Следующая страница
        if (page < totalPages - 1) {
            ItemStack nextButton = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = nextButton.getItemMeta();
            nextMeta.setDisplayName(colorize("&eСледующая страница →"));
            nextButton.setItemMeta(nextMeta);
            inv.setItem(50, nextButton);
        }
    }

    private void addPremiumNavigation(Inventory inv, int page, int totalPages) {
        // Назад
        ItemStack backButton = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backButton.getItemMeta();
        backMeta.setDisplayName(colorize("&c← Назад к главному меню"));
        backButton.setItemMeta(backMeta);
        inv.setItem(49, backButton);

        // Предыдущая страница
        if (page > 0) {
            ItemStack prevButton = new ItemStack(Material.ARROW);
            ItemMeta prevMeta = prevButton.getItemMeta();
            prevMeta.setDisplayName(colorize("&e← Предыдущая страница"));
            prevButton.setItemMeta(prevMeta);
            inv.setItem(48, prevButton);
        }

        // Следующая страница
        if (page < totalPages - 1) {
            ItemStack nextButton = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = nextButton.getItemMeta();
            nextMeta.setDisplayName(colorize("&eСледующая страница →"));
            nextButton.setItemMeta(nextMeta);
            inv.setItem(50, nextButton);
        }
    }

    private ItemStack getCategoryIcon(String category) {
        switch (category) {
            case "BUILDING_BLOCKS": return new ItemStack(Material.BRICKS);
            case "DECORATIONS": return new ItemStack(Material.PAINTING);
            case "REDSTONE": return new ItemStack(Material.REDSTONE);
            case "TRANSPORTATION": return new ItemStack(Material.MINECART);
            case "MISCELLANEOUS": return new ItemStack(Material.LAVA_BUCKET);
            case "FOOD": return new ItemStack(Material.APPLE);
            case "TOOLS": return new ItemStack(Material.DIAMOND_PICKAXE);
            case "COMBAT": return new ItemStack(Material.DIAMOND_SWORD);
            case "BREWING": return new ItemStack(Material.BREWING_STAND);
            default: return new ItemStack(Material.CHEST);
        }
    }

    private int getItemCountInCategory(String category, String currency) {
        Database database = Economy.getInstance().getDatabase();
        List<Map<String, Object>> items = database.getAuctionItems(category, currency, 0, 1000);
        return items.size();
    }

    private ItemStack createAuctionItemDisplay(Map<String, Object> item) {
        ItemStack displayItem = deserializeItem((String) item.get("item_data"));
        if (displayItem == null) displayItem = new ItemStack(Material.PAPER);

        ItemMeta meta = displayItem.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();

            lore.add(colorize("&8&m                    "));
            lore.add(colorize("&7Продавец: &f" + item.get("seller_name")));
            lore.add(colorize("&7Цена: &e" + String.format("%,d", (Long) item.get("price")) + " " + item.get("currency")));
            lore.add(colorize("&7Категория: &f" + item.get("category")));
            lore.add(colorize("&8&m                    "));
            lore.add(colorize("&aЛКМ - Купить предмет"));
            lore.add(colorize("&eПКМ - Подробная информация"));

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

            lore.add(colorize("&8&m                    "));
            lore.add(colorize("&cИстекший лот"));
            lore.add(colorize("&7Цена была: &e" + String.format("%,d", (Long) item.get("price")) + " " + item.get("currency")));
            lore.add(colorize("&8&m                    "));
            lore.add(colorize("&aНажмите чтобы забрать"));

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
            meta.setDisplayName(colorize("&6⭐ " + item.get("display_name") + " ⭐"));

            List<String> lore = new ArrayList<>();
            lore.add(colorize("&8&m                    "));
            lore.add(colorize("&7" + item.get("description")));
            lore.add(colorize("&7Цена: &e" + String.format("%,d", (Long) item.get("price")) + " VIL"));

            int stock = (Integer) item.get("stock");
            if (stock == -1) {
                lore.add(colorize("&7Количество: &a∞"));
            } else {
                lore.add(colorize("&7Количество: &f" + stock));
            }

            lore.add(colorize("&8&m                    "));
            lore.add(colorize("&aНажмите для покупки"));

            meta.setLore(lore);
            displayItem.setItemMeta(meta);
        }

        return displayItem;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        String title = event.getView().getTitle();
        if (!isOurInventory(title)) return;

        event.setCancelled(true);

        if (event.getClickedInventory() != event.getView().getTopInventory()) return;

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        int slot = event.getSlot();
        String menuType = playerMenus.get(player.getName());

        switch (menuType) {
            case "main":
                handleMainMenuClick(player, slot);
                break;
            case "currency":
                handleCurrencyMenuClick(player, slot);
                break;
            case "items":
                handleItemsClick(player, slot, event.isLeftClick());
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
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        String title = event.getView().getTitle();
        if (isOurInventory(title)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();

        String title = event.getView().getTitle();
        if (isOurInventory(title)) {
            // Задержка для корректного перехода между меню
            Bukkit.getScheduler().runTaskLater(Economy.getInstance(), () -> {
                // Проверяем что игрок не в другом нашем меню
                if (player.getOpenInventory() != null) {
                    String currentTitle = player.getOpenInventory().getTitle();
                    if (!isOurInventory(currentTitle)) {
                        // Очищаем данные только если игрок не в наших меню
                        playerMenus.remove(player.getName());
                        playerPages.remove(player.getName());
                        playerCurrency.remove(player.getName());
                        playerCategory.remove(player.getName());
                    }
                } else {
                    // Инвентарь закрыт - очищаем данные
                    playerMenus.remove(player.getName());
                    playerPages.remove(player.getName());
                    playerCurrency.remove(player.getName());
                    playerCategory.remove(player.getName());
                }
            }, 2L); // Увеличиваем задержку
        }
    }

    private void handleMainMenuClick(Player player, int slot) {
        switch (slot) {
            case 1: // VIL
                openCurrencyMenu(player, "VIL");
                break;
            case 3: case 5: // Другие валюты
                ItemStack clicked = player.getOpenInventory().getTopInventory().getItem(slot);
                if (clicked != null && clicked.hasItemMeta()) {
                    String displayName = clicked.getItemMeta().getDisplayName();
                    String currency = displayName.replace("§6", "").replace(" АУКЦИОН", "");
                    openCurrencyMenu(player, currency);
                }
                break;
            case 7: // Все валюты
                openCurrencyMenu(player, null);
                break;
            case 20: // Мои предметы
                openExpiredItems(player, 0);
                break;
            case 24: // Премиум магазин
                openPremiumShop(player, 0);
                break;
        }
    }

    private void handleCurrencyMenuClick(Player player, int slot) {
        if (slot == 45) { // Назад
            openMainMenu(player);
            return;
        }

        if (slot == 49) { // Все категории
            String currency = playerCurrency.get(player.getName());
            openAuctionItems(player, currency, "ALL", 0);
            return;
        }

        if (slot < 45) { // Категория
            String[] categories = {"BUILDING_BLOCKS", "DECORATIONS", "REDSTONE", "TRANSPORTATION",
                    "MISCELLANEOUS", "FOOD", "TOOLS", "COMBAT", "BREWING"};
            if (slot < categories.length) {
                String currency = playerCurrency.get(player.getName());
                openAuctionItems(player, currency, categories[slot], 0);
            }
        }
    }

    private void handleItemsClick(Player player, int slot, boolean leftClick) {
        if (slot == 45) { // Назад к категориям
            String currency = playerCurrency.get(player.getName());
            openCurrencyMenu(player, currency);
            return;
        }

        if (slot == 48) { // Предыдущая страница
            int currentPage = playerPages.getOrDefault(player.getName(), 0);
            if (currentPage > 0) {
                String currency = playerCurrency.get(player.getName());
                String category = playerCategory.get(player.getName());
                openAuctionItems(player, currency, category, currentPage - 1);
            }
            return;
        }

        if (slot == 49) { // Главное меню
            openMainMenu(player);
            return;
        }

        if (slot == 50) { // Следующая страница
            int currentPage = playerPages.getOrDefault(player.getName(), 0);
            String currency = playerCurrency.get(player.getName());
            String category = playerCategory.get(player.getName());
            openAuctionItems(player, currency, category, currentPage + 1);
            return;
        }

        if (slot == 53) { // Обновить
            int currentPage = playerPages.getOrDefault(player.getName(), 0);
            String currency = playerCurrency.get(player.getName());
            String category = playerCategory.get(player.getName());
            openAuctionItems(player, currency, category, currentPage);
            return;
        }

        if (slot < 45) { // Клик по предмету
            if (leftClick) {
                buyAuctionItem(player, slot);
            } else {
                showItemDetails(player, slot);
            }
        }
    }

    private void handleExpiredClick(Player player, int slot) {
        if (slot == 49) { // Назад
            openMainMenu(player);
            return;
        }

        if (slot == 48) { // Предыдущая страница
            int currentPage = playerPages.getOrDefault(player.getName(), 0);
            if (currentPage > 0) {
                openExpiredItems(player, currentPage - 1);
            }
            return;
        }

        if (slot == 50) { // Следующая страница
            int currentPage = playerPages.getOrDefault(player.getName(), 0);
            openExpiredItems(player, currentPage + 1);
            return;
        }

        if (slot < 45) { // Забрать предмет
            Database database = Economy.getInstance().getDatabase();
            List<Map<String, Object>> expiredItems = database.getExpiredAuctionItems(player.getName());

            int currentPage = playerPages.getOrDefault(player.getName(), 0);
            int itemIndex = currentPage * 45 + slot;

            if (itemIndex < expiredItems.size()) {
                Map<String, Object> expiredItem = expiredItems.get(itemIndex);
                int itemId = (Integer) expiredItem.get("id");
                ItemStack originalItem = deserializeItem((String) expiredItem.get("item_data"));

                if (originalItem != null) {
                    player.getInventory().addItem(originalItem);
                    database.removeExpiredAuctionItem(itemId);

                    player.sendMessage(colorize("&aПредмет возвращен в ваш инвентарь!"));
                    openExpiredItems(player, currentPage); // Обновляем меню
                }
            }
        }
    }

    private void handlePremiumClick(Player player, int slot) {
        if (slot == 49) { // Назад
            openMainMenu(player);
            return;
        }

        if (slot == 48) { // Предыдущая страница
            int currentPage = playerPages.getOrDefault(player.getName(), 0);
            if (currentPage > 0) {
                openPremiumShop(player, currentPage - 1);
            }
            return;
        }

        if (slot == 50) { // Следующая страница
            int currentPage = playerPages.getOrDefault(player.getName(), 0);
            openPremiumShop(player, currentPage + 1);
            return;
        }

        if (slot < 45) { // Купить предмет
            Database database = Economy.getInstance().getDatabase();
            WalletManager walletManager = Economy.getInstance().getWalletManager();
            List<Map<String, Object>> premiumItems = database.getPremiumShopItems();

            int currentPage = playerPages.getOrDefault(player.getName(), 0);
            int itemIndex = currentPage * 45 + slot;

            if (itemIndex < premiumItems.size()) {
                Map<String, Object> premiumItem = premiumItems.get(itemIndex);
                int itemId = (Integer) premiumItem.get("id");
                long price = (Long) premiumItem.get("price");

                if (walletManager.getPlayerWallet(player.getName()).getSlots().getOrDefault("VIL", 0) < price) {
                    player.sendMessage(colorize("&cНедостаточно VIL! Нужно: " + String.format("%,d", price)));
                    return;
                }

                PaymentResult result = walletManager.getMoney(player.getName(), "VIL", (int) price);
                if (result == PaymentResult.SUCCESS) {
                    ItemStack purchasedItem = deserializeItem((String) premiumItem.get("item_data"));
                    if (purchasedItem != null) {
                        player.getInventory().addItem(purchasedItem);
                        database.decreasePremiumShopStock(itemId);

                        player.sendMessage(colorize("&a⭐ Предмет куплен за " + String.format("%,d", price) + " VIL! ⭐"));
                        openPremiumShop(player, currentPage); // Обновляем меню
                    }
                }
            }
        }
    }

    private void buyAuctionItem(Player player, int slot) {
        Database database = Economy.getInstance().getDatabase();
        WalletManager walletManager = Economy.getInstance().getWalletManager();

        String currency = playerCurrency.get(player.getName());
        String category = playerCategory.get(player.getName());
        int page = playerPages.getOrDefault(player.getName(), 0);

        List<Map<String, Object>> items = database.getAuctionItems(category, currency, page, 45);

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

        if (walletManager.getPlayerWallet(player.getName()).getSlots().getOrDefault(itemCurrency, 0) < price) {
            player.sendMessage(colorize("&cНедостаточно средств! Нужно: " + String.format("%,d", price) + " " + itemCurrency));
            return;
        }

        Map<String, Object> currentItem = database.getAuctionItem(itemId);
        if (currentItem == null) {
            player.sendMessage(colorize("&cПредмет уже продан или снят с продажи!"));
            openAuctionItems(player, currency, category, page);
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

            player.sendMessage(colorize("&aВы купили предмет за " + String.format("%,d", price) + " " + itemCurrency + "!"));

            Player seller = Bukkit.getPlayer(sellerName);
            if (seller != null) {
                seller.sendMessage(colorize("&aВаш предмет продан за " + String.format("%,d", price) + " " + itemCurrency + "!"));
            }

            openAuctionItems(player, currency, category, page);
        } else {
            player.sendMessage(colorize("&cОшибка при покупке: " + result.name()));
        }
    }

    private void showItemDetails(Player player, int slot) {
        Database database = Economy.getInstance().getDatabase();

        String currency = playerCurrency.get(player.getName());
        String category = playerCategory.get(player.getName());
        int page = playerPages.getOrDefault(player.getName(), 0);

        List<Map<String, Object>> items = database.getAuctionItems(category, currency, page, 45);

        if (slot >= items.size()) return;

        Map<String, Object> item = items.get(slot);

        player.sendMessage(colorize("&6=== Информация о предмете ==="));
        player.sendMessage(colorize("&7ID: &f" + item.get("id")));
        player.sendMessage(colorize("&7Продавец: &f" + item.get("seller_name")));
        player.sendMessage(colorize("&7Цена: &e" + String.format("%,d", (Long) item.get("price")) + " " + item.get("currency")));
        player.sendMessage(colorize("&7Категория: &f" + item.get("category")));
        player.sendMessage(colorize("&7Выставлен: &f" + item.get("created_at")));
        player.sendMessage(colorize("&7Истекает: &f" + item.get("expires_at")));
    }

    private boolean isOurInventory(String title) {
        String cleanTitle = title.replace("§", "&").toLowerCase();
        return cleanTitle.contains("аукцион") ||
                cleanTitle.contains("auction") ||
                cleanTitle.contains("предметы") ||
                cleanTitle.contains("items") ||
                cleanTitle.contains("истекшие") ||
                cleanTitle.contains("expired") ||
                cleanTitle.contains("премиум") ||
                cleanTitle.contains("premium");
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

    private String colorize(String text) {
        return text.replace("&", "§");
    }
}