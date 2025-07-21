package xyz.moorus.economy.command;

import org.bukkit.Bukkit;
import org.bukkit.Material;
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
import org.bukkit.util.io.BukkitObjectOutputStream;
import xyz.moorus.economy.main.Economy;
import xyz.moorus.economy.money.PaymentResult;
import xyz.moorus.economy.money.WalletManager;
import xyz.moorus.economy.sql.Database;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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
    private Map<String, InventoryClickEvent> lastClickEvents = new HashMap<>();

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

        if (args.length == 0) {
            openMainMenu(player);
            return;
        }

        switch (args[0].toLowerCase()) {
            case "sell":
                if (args.length != 3) {
                    player.sendMessage(colorize("&cИспользование: /ah sell <цена> <валюта>"));
                    return;
                }
                handleSellItem(player, args[1], args[2]);
                break;

            case "my":
                showMyItems(player);
                break;

            case "active":
                openActiveItems(player, 0);
                break;

            case "expired":
                openExpiredItems(player, 0);
                break;

            case "cancel":
                if (args.length != 2) {
                    player.sendMessage(colorize("&cИспользование: /ah cancel <id>"));
                    return;
                }
                handleCancelItem(player, args[1]);
                break;

            default:
                openMainMenu(player);
                break;
        }
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

        // Мои предметы - ИСПРАВЛЕНО: Добавляем поддержку ЛКМ/ПКМ
        ItemStack myItems = new ItemStack(Material.ENDER_CHEST);
        ItemMeta myMeta = myItems.getItemMeta();
        myMeta.setDisplayName(colorize("&b§lМОИ ПРЕДМЕТЫ"));
        List<String> myLore = new ArrayList<>();
        myLore.add(colorize("&aЛКМ - Активные лоты"));
        myLore.add(colorize("&cПКМ - Истекшие предметы"));
        myLore.add(colorize("&e"));
        myLore.add(colorize("&7Нажмите для выбора"));
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
        infoLore.add(colorize("&e"));
        infoLore.add(colorize("&7Команды:"));
        infoLore.add(colorize("&f/ah sell <цена> <валюта> &7- продать"));
        infoLore.add(colorize("&f/ah active &7- активные лоты"));
        infoLore.add(colorize("&f/ah expired &7- истекшие предметы"));
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

        // ИСПРАВЛЕНО: Правильные категории с иконками
        String[] categories = {"BUILDING_BLOCKS", "DECORATIONS", "REDSTONE", "TRANSPORTATION",
                "MISCELLANEOUS", "FOOD", "TOOLS", "COMBAT", "BREWING"};
        String[] categoryNames = {"&6Строительные блоки", "&5Декорации", "&cРедстоун", "&9Транспорт",
                "&7Разное", "&aЕда", "&eИнструменты", "&4Оружие и броня", "&dЗелья"};

        for (int i = 0; i < categories.length && i < 45; i++) {
            ItemStack categoryItem = getCategoryIcon(categories[i]);
            ItemMeta meta = categoryItem.getItemMeta();
            meta.setDisplayName(colorize(categoryNames[i]));

            List<String> lore = new ArrayList<>();
            lore.add(colorize("&7Категория: &f" + categories[i]));

            // ИСПРАВЛЕНО: Правильный подсчет предметов в категории
            int itemCount = getItemCountInCategory(categories[i], selectedCurrency);
            lore.add(colorize("&7Предметов: &f" + itemCount));

            if (selectedCurrency != null && !selectedCurrency.equals("ALL")) {
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
        if (selectedCurrency != null && !selectedCurrency.equals("ALL")) {
            allLore.add(colorize("&7Валюта: &f" + selectedCurrency));
        }

        // Подсчитываем общее количество предметов
        int totalItems = getItemCountInCategory("ALL", selectedCurrency);
        allLore.add(colorize("&7Всего предметов: &f" + totalItems));
        allLore.add(colorize("&e"));
        if (totalItems > 0) {
            allLore.add(colorize("&aНажмите для просмотра"));
        } else {
            allLore.add(colorize("&7Предметов нет"));
        }
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
        if (currency != null && !currency.equals("ALL")) title += " - " + currency;
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

    // НОВЫЙ МЕТОД: Активные лоты игрока
    private void openActiveItems(Player player, int page) {
        playerMenus.put(player.getName(), "active");
        playerPages.put(player.getName(), page);

        Database database = Economy.getInstance().getDatabase();
        List<Map<String, Object>> activeItems = database.getPlayerActiveAuctionItems(player.getName());

        // Пагинация
        int itemsPerPage = 45;
        int totalPages = (int) Math.ceil((double) activeItems.size() / itemsPerPage);
        int startIndex = page * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, activeItems.size());

        String title = colorize("&aАктивные лоты");
        if (totalPages > 1) {
            title += " (" + (page + 1) + "/" + totalPages + ")";
        }

        Inventory inv = Bukkit.createInventory(null, 54, title);

        // Добавляем предметы
        for (int i = startIndex; i < endIndex; i++) {
            ItemStack displayItem = createActiveItemDisplay(activeItems.get(i));
            inv.setItem(i - startIndex, displayItem);
        }

        // Навигация
        addActiveNavigation(inv, page, totalPages);

        player.openInventory(inv);

        if (activeItems.isEmpty()) {
            player.sendMessage(colorize(Economy.getInstance().getConfig().getString("messages.auction.no_active_items", "&7У вас нет активных лотов")));
        } else {
            player.sendMessage(colorize("&7Активных лотов: " + activeItems.size()));
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
            player.sendMessage(colorize(Economy.getInstance().getConfig().getString("messages.auction.no_expired_items", "&7У вас нет истекших предметов")));
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
            player.sendMessage(colorize(Economy.getInstance().getConfig().getString("messages.premium.shop_empty", "&7Премиум магазин пуст")));
        } else {
            player.sendMessage(colorize("&7Предметов в магазине: " + premiumItems.size()));
        }
    }

    // НОВЫЙ МЕТОД: Создание отображения активного предмета
    private ItemStack createActiveItemDisplay(Map<String, Object> item) {
        ItemStack displayItem = deserializeItem((String) item.get("item_data"));
        if (displayItem == null) displayItem = new ItemStack(Material.PAPER);

        ItemMeta meta = displayItem.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();

            lore.add(colorize("&8&m                    "));
            lore.add(colorize("&aАктивный лот"));
            lore.add(colorize("&7ID: &f" + item.get("id")));
            lore.add(colorize("&7Цена: &e" + String.format("%,d", (Long) item.get("price")) + " " + item.get("currency")));
            lore.add(colorize("&7Категория: &f" + item.get("category")));
            lore.add(colorize("&7Создан: &f" + item.get("created_at")));
            lore.add(colorize("&7Истекает: &f" + item.get("expires_at")));
            lore.add(colorize("&8&m                    "));
            lore.add(colorize("&cЛКМ - Отменить лот"));
            lore.add(colorize("&eПКМ - Подробная информация"));

            meta.setLore(lore);
            displayItem.setItemMeta(meta);
        }

        return displayItem;
    }

    // НОВЫЙ МЕТОД: Навигация для активных предметов
    private void addActiveNavigation(Inventory inv, int page, int totalPages) {
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

        // Обновить
        ItemStack refreshButton = new ItemStack(Material.CLOCK);
        ItemMeta refreshMeta = refreshButton.getItemMeta();
        refreshMeta.setDisplayName(colorize("&b🔄 Обновить"));
        refreshMeta.setLore(List.of(colorize("&7Обновить список активных лотов")));
        refreshButton.setItemMeta(refreshMeta);
        inv.setItem(53, refreshButton);
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

        try (Connection conn = database.getConnection()) {
            StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM auction_items WHERE is_sold = 0 AND expires_at > datetime('now')");
            List<Object> params = new ArrayList<>();

            // Фильтр по валюте
            if (currency != null && !currency.equals("ALL") && !currency.isEmpty()) {
                sql.append(" AND UPPER(currency) = UPPER(?)");
                params.add(currency);
            }

            // Фильтр по категории
            if (category != null && !category.equals("ALL") && !category.isEmpty()) {
                sql.append(" AND UPPER(category) = UPPER(?)");
                params.add(category);
            }

            try (PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
                for (int i = 0; i < params.size(); i++) {
                    stmt.setObject(i + 1, params.get(i));
                }

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            }
        } catch (SQLException e) {
            Economy.getInstance().getLogger().severe("Ошибка подсчета предметов в категории: " + e.getMessage());
        }

        return 0;
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

    private void handleSellItem(Player player, String priceStr, String currency) {
        ItemStack item = player.getInventory().getItemInMainHand();

        if (item == null || item.getType() == Material.AIR) {
            player.sendMessage(colorize("&cВозьмите предмет в руку!"));
            return;
        }

        try {
            long price = Long.parseLong(priceStr);

            if (price <= 0) {
                player.sendMessage(colorize("&cЦена должна быть больше 0!"));
                return;
            }

            // ИСПРАВЛЕНО: Проверяем существование валюты
            Database database = Economy.getInstance().getDatabase();
            if (!database.doesCurrencyExist(currency)) {
                player.sendMessage(colorize("&cВалюта " + currency + " не существует!"));
                return;
            }

            // ИСПРАВЛЕНО: Правильная проверка лимитов
            int maxItems = getMaxItemsForPlayer(player);
            int currentItems = getCurrentItemsCount(player.getName());

            if (currentItems >= maxItems) {
                player.sendMessage(colorize("&cВы достигли лимита предметов на аукционе! (" + maxItems + ")"));
                return;
            }

            String category = determineItemCategory(item);

            if (addAuctionItem(player.getName(), item, price, currency, category)) {
                player.getInventory().setItemInMainHand(null);

                // ИСПРАВЛЕНО: ТОЛЬКО ОДНО СООБЩЕНИЕ
                String message = Economy.getInstance().getConfig().getString("messages.auction.item_listed", "&aПредмет выставлен на аукцион за {price} {currency}!");
                message = message.replace("{price}", String.format("%,d", price));
                message = message.replace("{currency}", currency);
                player.sendMessage(colorize(message));

                player.sendMessage(colorize("&7Категория: &f" + category));
            } else {
                player.sendMessage(colorize("&cОшибка при выставлении предмета!"));
            }

        } catch (NumberFormatException e) {
            player.sendMessage(colorize("&cНеверная цена! Используйте только цифры."));
        }
    }

    private String replacePlaceholders(String message, long price, String currency) {
        return message
                .replace("{price}", String.format("%,d", price))
                .replace("{currency}", currency);
    }

    private boolean addAuctionItem(String sellerName, ItemStack item, long price, String currency, String category) {
        Database database = Economy.getInstance().getDatabase();

        String itemData = serializeItem(item);
        if (itemData == null) return false;

        int hoursToExpire = Economy.getInstance().getConfig().getInt("auction.expiration_hours", 72);

        // ИСПРАВЛЕНО: Используем метод из Database
        int itemId = database.addAuctionItem(
                sellerName,
                Bukkit.getPlayer(sellerName).getUniqueId().toString(),
                itemData,
                currency,
                price,
                category, // ИСПРАВЛЕНО: Передаем категорию!
                hoursToExpire
        );

        if (itemId > 0) {
            Economy.getInstance().getLogger().info("Предмет добавлен с ID: " + itemId + ", категория: " + category);
            return true;
        }

        return false;
    }

    // ИСПРАВЛЕНО: Улучшенная система лимитов
    private int getMaxItemsForPlayer(Player player) {
        // Проверяем конфигурацию
        int configLimit = Economy.getInstance().getConfig().getInt("auction.max_items_per_player", 10);

        // ИСПРАВЛЕНО: Правильная проверка прав по приоритету
        if (player.isOp()) {
            return Economy.getInstance().getConfig().getInt("auction.limits.op", 999);
        }

        if (player.hasPermission("economy.auction.max.unlimited")) {
            return Economy.getInstance().getConfig().getInt("auction.limits.admin", 999);
        }

        if (player.hasPermission("economy.auction.max.50")) {
            return Economy.getInstance().getConfig().getInt("auction.limits.vip", 50);
        }

        if (player.hasPermission("economy.auction.max.25")) {
            return Economy.getInstance().getConfig().getInt("auction.limits.premium", 25);
        }

        if (player.hasPermission("economy.auction.max.10")) {
            return Math.max(10, configLimit);
        }

        if (player.hasPermission("economy.auction.max.5")) {
            return Math.max(5, configLimit);
        }

        if (player.hasPermission("economy.auction.max.3")) {
            return Math.max(3, configLimit);
        }

        if (player.hasPermission("economy.auction.max.1")) {
            return 1;
        }

        return configLimit; // Обычный лимит
    }

    private int getCurrentItemsCount(String playerName) {
        Database database = Economy.getInstance().getDatabase();

        try (Connection conn = database.getConnection()) {
            // ИСПРАВЛЕНО: Правильный SQL запрос
            String sql = "SELECT COUNT(*) FROM auction_items WHERE seller_name = ? AND is_sold = 0 AND expires_at > datetime('now')";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, playerName);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        int count = rs.getInt(1);
                        Economy.getInstance().getLogger().info("Текущих предметов у " + playerName + ": " + count);
                        return count;
                    }
                }
            }
        } catch (SQLException e) {
            Economy.getInstance().getLogger().severe("Ошибка подсчета предметов: " + e.getMessage());
        }

        return 0;
    }

    private String determineItemCategory(ItemStack item) {
        Material material = item.getType();
        String materialName = material.name().toLowerCase();

        // ИСПРАВЛЕНО: Правильное определение категорий

        // COMBAT - Оружие и броня
        if (materialName.contains("sword") || materialName.contains("bow") ||
                materialName.contains("crossbow") || materialName.contains("trident") ||
                materialName.contains("helmet") || materialName.contains("chestplate") ||
                materialName.contains("leggings") || materialName.contains("boots") ||
                materialName.contains("shield") || material == Material.ARROW ||
                material == Material.SPECTRAL_ARROW || material == Material.TIPPED_ARROW) {
            return "COMBAT";
        }

        // TOOLS - Инструменты
        if (materialName.contains("pickaxe") || materialName.contains("axe") ||
                materialName.contains("shovel") || materialName.contains("hoe") ||
                materialName.contains("shears") || material == Material.FISHING_ROD ||
                material == Material.FLINT_AND_STEEL || material == Material.COMPASS ||
                material == Material.CLOCK || material == Material.SPYGLASS) {
            return "TOOLS";
        }

        // FOOD - Еда
        if (material.isEdible() ||
                materialName.contains("bread") || materialName.contains("cake") ||
                materialName.contains("pie") || materialName.contains("stew") ||
                materialName.contains("soup") || material == Material.MILK_BUCKET ||
                material == Material.HONEY_BOTTLE || material == Material.SUSPICIOUS_STEW) {
            return "FOOD";
        }

        // BUILDING_BLOCKS - Строительные блоки
        if (material.isBlock() && (
                materialName.contains("stone") || materialName.contains("brick") ||
                        materialName.contains("wood") || materialName.contains("plank") ||
                        materialName.contains("log") || materialName.contains("cobblestone") ||
                        materialName.contains("concrete") || materialName.contains("terracotta") ||
                        materialName.contains("wool") || materialName.contains("glass") ||
                        material == Material.DIRT || material == Material.SAND ||
                        material == Material.GRAVEL || material == Material.CLAY ||
                        materialName.contains("stairs") || materialName.contains("slab"))) {
            return "BUILDING_BLOCKS";
        }

        // REDSTONE - Редстоун механизмы
        if (materialName.contains("redstone") || materialName.contains("piston") ||
                materialName.contains("repeater") || materialName.contains("comparator") ||
                materialName.contains("lever") || materialName.contains("button") ||
                materialName.contains("pressure_plate") || materialName.contains("tripwire") ||
                material == Material.OBSERVER || material == Material.HOPPER ||
                material == Material.DROPPER || material == Material.DISPENSER) {
            return "REDSTONE";
        }

        // TRANSPORTATION - Транспорт
        if (materialName.contains("rail") || materialName.contains("cart") ||
                materialName.contains("boat") || material == Material.SADDLE ||
                material == Material.DIAMOND_HORSE_ARMOR || materialName.contains("horse_armor") ||
                material == Material.LEAD || material == Material.NAME_TAG) {
            return "TRANSPORTATION";
        }

        // BREWING - Зелья и алхимия
        if (materialName.contains("potion") || material == Material.BREWING_STAND ||
                material == Material.CAULDRON || material == Material.BLAZE_POWDER ||
                material == Material.NETHER_WART || material == Material.FERMENTED_SPIDER_EYE ||
                material == Material.GLISTERING_MELON_SLICE || material == Material.GOLDEN_CARROT ||
                materialName.contains("spider_eye") || materialName.contains("ghast_tear")) {
            return "BREWING";
        }

        // DECORATIONS - Декорации
        if (materialName.contains("painting") || materialName.contains("frame") ||
                materialName.contains("flower") || materialName.contains("banner") ||
                materialName.contains("carpet") || materialName.contains("candle") ||
                material == Material.FLOWER_POT || material == Material.BEACON ||
                materialName.contains("head") || materialName.contains("skull") ||
                material == Material.ARMOR_STAND || materialName.contains("sign")) {
            return "DECORATIONS";
        }

        // Все остальное
        return "MISCELLANEOUS";
    }

    private void showMyItems(Player player) {
        Database database = Economy.getInstance().getDatabase();

        // ИСПРАВЛЕНО: Используем правильный метод
        List<String> items = getPlayerAuctionItemsList(player.getName());

        if (items.isEmpty()) {
            player.sendMessage(colorize("&7У вас нет предметов на аукционе"));
            return;
        }

        player.sendMessage(colorize("&6=== Ваши предметы на аукционе ==="));
        for (String item : items) {
            player.sendMessage(colorize("&7" + item));
        }
        player.sendMessage(colorize("&7Используйте &f/ah cancel <ID> &7для отмены"));
        player.sendMessage(colorize("&7Или &f/ah active &7для GUI"));
    }

    // ИСПРАВЛЕНО: Добавляем метод для получения предметов игрока
    private List<String> getPlayerAuctionItemsList(String playerName) {
        List<String> items = new ArrayList<>();
        Database database = Economy.getInstance().getDatabase();

        try (Connection conn = database.getConnection()) {
            String sql = "SELECT id, price, currency, created_at FROM auction_items WHERE seller_name = ? AND is_sold = 0 AND expires_at > datetime('now')";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, playerName);

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        int id = rs.getInt("id");
                        long price = rs.getLong("price");
                        String currency = rs.getString("currency");
                        String createdAt = rs.getString("created_at");

                        items.add("ID: " + id + " | Цена: " + String.format("%,d", price) + " " + currency + " | Создан: " + createdAt);
                    }
                }
            }
        } catch (SQLException e) {
            Economy.getInstance().getLogger().severe("Ошибка получения предметов игрока: " + e.getMessage());
        }

        return items;
    }

    private void handleCancelItem(Player player, String itemIdStr) {
        try {
            int itemId = Integer.parseInt(itemIdStr);

            // ИСПРАВЛЕНО: Используем правильный метод
            if (cancelPlayerAuctionItem(itemId, player.getName())) {
                player.sendMessage(colorize(Economy.getInstance().getConfig().getString("messages.auction.item_cancelled", "&aПредмет снят с аукциона!")));
            } else {
                player.sendMessage(colorize("&cПредмет не найден или не принадлежит вам!"));
            }

        } catch (NumberFormatException e) {
            player.sendMessage(colorize("&cНеверный ID предмета!"));
        }
    }

    // ИСПРАВЛЕНО: Добавляем метод для отмены предмета
    private boolean cancelPlayerAuctionItem(int itemId, String playerName) {
        Database database = Economy.getInstance().getDatabase();

        try (Connection conn = database.getConnection()) {
            // Сначала получаем предмет и проверяем владельца
            String selectSql = "SELECT seller_name, item_data FROM auction_items WHERE id = ? AND is_sold = 0 AND expires_at > datetime('now')";
            try (PreparedStatement selectStmt = conn.prepareStatement(selectSql)) {
                selectStmt.setInt(1, itemId);

                try (ResultSet rs = selectStmt.executeQuery()) {
                    if (rs.next()) {
                        String seller = rs.getString("seller_name");
                        String itemData = rs.getString("item_data");

                        if (!seller.equals(playerName)) {
                            return false; // Не владелец
                        }

                        // Возвращаем предмет игроку
                        Player player = Bukkit.getPlayer(playerName);
                        if (player != null) {
                            ItemStack item = deserializeItem(itemData);
                            if (item != null) {
                                player.getInventory().addItem(item);
                            }
                        }

                        // Помечаем как проданный (отмененный)
                        String deleteSql = "UPDATE auction_items SET is_sold = 1 WHERE id = ?";
                        try (PreparedStatement deleteStmt = conn.prepareStatement(deleteSql)) {
                            deleteStmt.setInt(1, itemId);
                            return deleteStmt.executeUpdate() > 0;
                        }
                    }
                }
            }
        } catch (SQLException e) {
            Economy.getInstance().getLogger().severe("Ошибка отмены предмета: " + e.getMessage());
        }

        return false;
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

        // Сохраняем последний клик для определения ЛКМ/ПКМ
        lastClickEvents.put(player.getName(), event);

        int slot = event.getSlot();
        String menuType = playerMenus.get(player.getName());

        switch (menuType) {
            case "main":
                handleMainMenuClick(player, slot, event.isLeftClick());
                break;
            case "currency":
                handleCurrencyMenuClick(player, slot);
                break;
            case "items":
                handleItemsClick(player, slot, event.isLeftClick());
                break;
            case "active": // НОВЫЙ ОБРАБОТЧИК
                handleActiveClick(player, slot);
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
                        cleanupPlayerData(player);
                    }
                } else {
                    // Инвентарь закрыт - очищаем данные
                    cleanupPlayerData(player);
                }
            }, 2L);
        }
    }

    private void cleanupPlayerData(Player player) {
        playerMenus.remove(player.getName());
        playerPages.remove(player.getName());
        playerCurrency.remove(player.getName());
        playerCategory.remove(player.getName());
        lastClickEvents.remove(player.getName());
    }

    // ИСПРАВЛЕНО: Обновленный обработчик главного меню с поддержкой ЛКМ/ПКМ
    private void handleMainMenuClick(Player player, int slot, boolean leftClick) {
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
            case 20: // Мои предметы - ИСПРАВЛЕНО: Поддержка ЛКМ/ПКМ
                if (leftClick) {
                    openActiveItems(player, 0); // Активные лоты
                } else {
                    openExpiredItems(player, 0); // Истекшие предметы
                }
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

    // НОВЫЙ ОБРАБОТЧИК: Активные предметы
    private void handleActiveClick(Player player, int slot) {
        if (slot == 49) { // Назад
            openMainMenu(player);
            return;
        }

        if (slot == 48) { // Предыдущая страница
            int currentPage = playerPages.getOrDefault(player.getName(), 0);
            if (currentPage > 0) {
                openActiveItems(player, currentPage - 1);
            }
            return;
        }

        if (slot == 50) { // Следующая страница
            int currentPage = playerPages.getOrDefault(player.getName(), 0);
            openActiveItems(player, currentPage + 1);
            return;
        }

        if (slot == 53) { // Обновить
            int currentPage = playerPages.getOrDefault(player.getName(), 0);
            openActiveItems(player, currentPage);
            return;
        }

        if (slot < 45) { // Клик по предмету - отмена лота
            Database database = Economy.getInstance().getDatabase();
            List<Map<String, Object>> activeItems = database.getPlayerActiveAuctionItems(player.getName());

            int currentPage = playerPages.getOrDefault(player.getName(), 0);
            int itemIndex = currentPage * 45 + slot;

            if (itemIndex < activeItems.size()) {
                Map<String, Object> activeItem = activeItems.get(itemIndex);
                int itemId = (Integer) activeItem.get("id");

                if (cancelPlayerAuctionItem(itemId, player.getName())) {
                    player.sendMessage(colorize(Economy.getInstance().getConfig().getString("messages.auction.item_cancelled", "&aЛот отменен и предмет возвращен!")));
                    openActiveItems(player, currentPage); // Обновляем меню
                } else {
                    player.sendMessage(colorize("&cОшибка отмены лота!"));
                }
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

                    player.sendMessage(colorize(Economy.getInstance().getConfig().getString("messages.auction.item_returned", "&aПредмет возвращен в ваш инвентарь!")));
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
                    String message = Economy.getInstance().getConfig().getString("messages.premium.insufficient_vil", "&cНедостаточно VIL! Нужно: {price}");
                    player.sendMessage(colorize(message.replace("{price}", String.format("%,d", price))));
                    return;
                }

                PaymentResult result = walletManager.getMoney(player.getName(), "VIL", (int) price);
                if (result == PaymentResult.SUCCESS) {
                    ItemStack purchasedItem = deserializeItem((String) premiumItem.get("item_data"));
                    if (purchasedItem != null) {
                        player.getInventory().addItem(purchasedItem);
                        database.decreasePremiumShopStock(itemId);

                        String message = Economy.getInstance().getConfig().getString("messages.premium.item_purchased", "&a⭐ Предмет куплен за {price} VIL! ⭐");
                        player.sendMessage(colorize(message.replace("{price}", String.format("%,d", price))));
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
            player.sendMessage(colorize(Economy.getInstance().getConfig().getString("messages.auction.cannot_buy_own_item", "&cВы не можете купить свой предмет!")));
            return;
        }

        if (walletManager.getPlayerWallet(player.getName()).getSlots().getOrDefault(itemCurrency, 0) < price) {
            player.sendMessage(colorize("&cНедостаточно средств! Нужно: " + String.format("%,d", price) + " " + itemCurrency));
            return;
        }

        Map<String, Object> currentItem = database.getAuctionItem(itemId);
        if (currentItem == null) {
            player.sendMessage(colorize(Economy.getInstance().getConfig().getString("messages.auction.item_already_sold", "&cПредмет уже продан или снят с продажи!")));
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

            String buyMessage = Economy.getInstance().getConfig().getString("messages.auction.item_bought", "&aВы купили предмет за {price} {currency}!");
            player.sendMessage(colorize(buyMessage.replace("{price}", String.format("%,d", price)).replace("{currency}", itemCurrency)));

            Player seller = Bukkit.getPlayer(sellerName);
            if (seller != null) {
                String sellMessage = Economy.getInstance().getConfig().getString("messages.auction.item_sold", "&aВаш предмет продан за {price} {currency}!");
                seller.sendMessage(colorize(sellMessage.replace("{price}", String.format("%,d", price)).replace("{currency}", itemCurrency)));
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

    private InventoryClickEvent getLastClickEvent(Player player) {
        return lastClickEvents.get(player.getName());
    }

    private boolean isOurInventory(String title) {
        String cleanTitle = title.replace("§", "&").toLowerCase();
        return cleanTitle.contains("аукцион") ||
                cleanTitle.contains("auction") ||
                cleanTitle.contains("предметы") ||
                cleanTitle.contains("items") ||
                cleanTitle.contains("истекшие") ||
                cleanTitle.contains("expired") ||
                cleanTitle.contains("активные") ||
                cleanTitle.contains("active") ||
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

    private String serializeItem(ItemStack item) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);
            dataOutput.writeObject(item);
            dataOutput.close();
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (Exception e) {
            Economy.getInstance().getLogger().warning("Ошибка сериализации предмета: " + e.getMessage());
            return null;
        }
    }

    private String colorize(String text) {
        return text.replace("&", "§");
    }
}