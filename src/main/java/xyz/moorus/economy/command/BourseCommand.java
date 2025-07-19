package xyz.moorus.economy.command;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import xyz.moorus.economy.main.Economy;
import xyz.moorus.economy.market.BourseManager;
import xyz.moorus.economy.market.ExchangeResult;
import xyz.moorus.economy.market.Order;
import xyz.moorus.economy.money.WalletManager;
import xyz.moorus.economy.sql.Database;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BourseCommand implements Command, Listener {

    private Map<String, String> playerMenus = new HashMap<>();
    private Map<String, String[]> playerTradingPair = new HashMap<>();
    private Map<String, Integer> playerPages = new HashMap<>();

    public BourseCommand() {
        Bukkit.getPluginManager().registerEvents(this, Economy.getInstance());
    }

    @Override
    public String getName() {
        return "bourse";
    }

    @Override
    public void execute(String sender, String[] args) {
        Player player = Bukkit.getPlayer(sender);
        if (player == null) return;

        if (args.length == 0) {
            openBourseMenu(player);
            return;
        }

        String action = args[0].toLowerCase();

        switch (action) {
            case "list":
            case "menu":
                openBourseMenu(player);
                break;

            case "add":
            case "create":
                if (args.length != 5) {
                    player.sendMessage(colorize("&cИспользование: /bourse add <продаю_валюта> <количество> <покупаю_валюта> <количество>"));
                    player.sendMessage(colorize("&7Пример: /bourse add VIL 100 USD 50"));
                } else {
                    handleAddOrder(player, args);
                }
                break;

            case "buy":
                if (args.length != 2) {
                    player.sendMessage(colorize("&cИспользование: /bourse buy <id_ордера>"));
                } else {
                    handleBuyOrder(player, args[1]);
                }
                break;

            case "cancel":
                if (args.length != 2) {
                    player.sendMessage(colorize("&cИспользование: /bourse cancel <id_ордера>"));
                } else {
                    handleCancelOrder(player, args[1]);
                }
                break;

            case "my":
            case "myorders":
                openMyOrdersMenu(player, 0);
                break;

            case "pair":
                if (args.length == 3) {
                    openTradingPairOrders(player, args[1].toUpperCase(), args[2].toUpperCase());
                } else {
                    player.sendMessage(colorize("&cИспользование: /bourse pair <валюта1> <валюта2>"));
                }
                break;

            case "help":
            default:
                showHelp(player);
                break;
        }
    }

    private int getMaxOrders(Player player) {
        if (player.hasPermission("economy.bourse.max.unlimited")) return -1; // Неограниченно
        if (player.hasPermission("economy.bourse.max.20")) return 20;
        if (player.hasPermission("economy.bourse.max.15")) return 15;
        if (player.hasPermission("economy.bourse.max.10")) return 10;
        if (player.hasPermission("economy.bourse.max.5")) return 5;
        if (player.hasPermission("economy.bourse.max.3")) return 3;
        return Economy.getInstance().getConfig().getInt("bourse.default_max_orders", 3);
    }

    private void openBourseMenu(Player player) {
        playerMenus.put(player.getName(), "bourse");

        String title = Economy.getInstance().getConfig().getString("bourse.gui.titles.main", "&6Биржа валют");
        Inventory bourseGui = Bukkit.createInventory(null, 54, colorize(title));

        // Популярные пары валют
        addCurrencyPair(bourseGui, 10, "VIL", "USD");
        addCurrencyPair(bourseGui, 11, "VIL", "EUR");
        addCurrencyPair(bourseGui, 12, "USD", "EUR");
        addCurrencyPair(bourseGui, 13, "VIL", "RUB");
        addCurrencyPair(bourseGui, 14, "USD", "RUB");
        addCurrencyPair(bourseGui, 15, "EUR", "RUB");
        addCurrencyPair(bourseGui, 16, "VIL", "GBP");
        addCurrencyPair(bourseGui, 19, "USD", "GBP");
        addCurrencyPair(bourseGui, 20, "EUR", "GBP");

        // Статистика
        ItemStack statsButton = new ItemStack(Material.BOOK);
        ItemMeta statsMeta = statsButton.getItemMeta();
        if (statsMeta != null) {
            statsMeta.setDisplayName(colorize("&bСтатистика биржи"));
            statsMeta.setLore(Arrays.asList(
                    colorize("&7Общая статистика"),
                    colorize("&7торгов на бирже"),
                    colorize("&e"),
                    colorize("&7Нажмите для просмотра")
            ));
            statsButton.setItemMeta(statsMeta);
        }
        bourseGui.setItem(22, statsButton);

        // Кнопки управления
        ItemStack addOrderButton = new ItemStack(Material.EMERALD);
        ItemMeta addMeta = addOrderButton.getItemMeta();
        if (addMeta != null) {
            addMeta.setDisplayName(colorize("&aСоздать ордер"));
            addMeta.setLore(Arrays.asList(
                    colorize("&7Нажмите для создания"),
                    colorize("&7нового ордера на бирже"),
                    colorize("&e"),
                    colorize("&7Или используйте команду:"),
                    colorize("&f/bourse add <валюта> <кол-во> <валюта> <кол-во>")
            ));
            addOrderButton.setItemMeta(addMeta);
        }
        bourseGui.setItem(49, addOrderButton);

        // Мои ордера с информацией
        Database database = Economy.getInstance().getDatabase();
        int myOrdersCount = database.getPlayerOrderCount(player.getName());
        int maxOrders = getMaxOrders(player);

        ItemStack myOrdersButton = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta myMeta = myOrdersButton.getItemMeta();
        if (myMeta != null) {
            myMeta.setDisplayName(colorize("&eMои ордера"));
            myMeta.setLore(Arrays.asList(
                    colorize("&7Активных ордеров: &f" + myOrdersCount),
                    colorize("&7Лимит: &f" + (maxOrders == -1 ? "∞" : maxOrders)),
                    colorize("&7Нажмите для просмотра")
            ));
            myOrdersButton.setItemMeta(myMeta);
        }
        bourseGui.setItem(53, myOrdersButton);

        // Кнопка помощи
        ItemStack helpButton = new ItemStack(Material.KNOWLEDGE_BOOK);
        ItemMeta helpMeta = helpButton.getItemMeta();
        if (helpMeta != null) {
            helpMeta.setDisplayName(colorize("&6Помощь"));
            helpMeta.setLore(Arrays.asList(
                    colorize("&7Как пользоваться биржей"),
                    colorize("&7и создавать ордера")
            ));
            helpButton.setItemMeta(helpMeta);
        }
        bourseGui.setItem(45, helpButton);

        player.openInventory(bourseGui);
    }

    private void openTradingPairOrders(Player player, String currency1, String currency2) {
        playerMenus.put(player.getName(), "trading_pair");
        playerTradingPair.put(player.getName(), new String[]{currency1, currency2});
        playerPages.put(player.getName(), 0);

        Database database = Economy.getInstance().getDatabase();
        List<Order> orders = database.getOrders(currency1, currency2);

        String title = Economy.getInstance().getConfig().getString("bourse.gui.titles.trading_pair", "&6{currency1} → {currency2}")
                .replace("{currency1}", currency1)
                .replace("{currency2}", currency2);

        Inventory ordersGui = Bukkit.createInventory(null, 54, colorize(title));

        int slot = 0;
        for (Order order : orders) {
            if (slot >= 45) break; // Оставляем место для кнопок

            ItemStack orderItem = createOrderDisplay(order);
            ordersGui.setItem(slot++, orderItem);
        }

        // Кнопка назад
        ItemStack backButton = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backButton.getItemMeta();
        if (backMeta != null) {
            backMeta.setDisplayName(colorize("&cНазад к бирже"));
            backMeta.setLore(Arrays.asList(colorize("&7Вернуться к главному меню")));
            backButton.setItemMeta(backMeta);
        }
        ordersGui.setItem(45, backButton);

        // Кнопка создать ордер
        ItemStack createButton = new ItemStack(Material.EMERALD);
        ItemMeta createMeta = createButton.getItemMeta();
        if (createMeta != null) {
            createMeta.setDisplayName(colorize("&aСоздать ордер"));
            createMeta.setLore(Arrays.asList(
                    colorize("&7Создать ордер для"),
                    colorize("&7пары " + currency1 + "/" + currency2)
            ));
            createButton.setItemMeta(createMeta);
        }
        ordersGui.setItem(49, createButton);

        // Кнопка обновить
        ItemStack refreshButton = new ItemStack(Material.CLOCK);
        ItemMeta refreshMeta = refreshButton.getItemMeta();
        if (refreshMeta != null) {
            refreshMeta.setDisplayName(colorize("&bОбновить"));
            refreshMeta.setLore(Arrays.asList(colorize("&7Обновить список ордеров")));
            refreshButton.setItemMeta(refreshMeta);
        }
        ordersGui.setItem(53, refreshButton);

        player.openInventory(ordersGui);

        if (orders.isEmpty()) {
            player.sendMessage(colorize("&7Нет активных ордеров для пары " + currency1 + "/" + currency2));
        } else {
            player.sendMessage(colorize("&7Найдено ордеров: " + orders.size()));
        }
    }

    private void openMyOrdersMenu(Player player, int page) {
        playerMenus.put(player.getName(), "my_orders");
        playerPages.put(player.getName(), page);

        Database database = Economy.getInstance().getDatabase();
        int itemsPerPage = 45;
        List<Order> myOrders = database.getPlayerOrdersPaginated(player.getName(), page, itemsPerPage);
        int totalOrders = database.getPlayerOrderCount(player.getName());
        int totalPages = (int) Math.ceil((double) totalOrders / itemsPerPage);

        String title = Economy.getInstance().getConfig().getString("bourse.gui.titles.my_orders", "&6Мои ордера");
        if (totalPages > 1) {
            title += " (" + (page + 1) + "/" + totalPages + ")";
        }

        Inventory myOrdersGui = Bukkit.createInventory(null, 54, colorize(title));

        int slot = 0;
        for (Order order : myOrders) {
            if (slot >= 45) break;

            ItemStack orderItem = createMyOrderDisplay(order);
            myOrdersGui.setItem(slot++, orderItem);
        }

        // Навигационные кнопки
        if (page > 0) {
            ItemStack prevButton = new ItemStack(Material.ARROW);
            ItemMeta prevMeta = prevButton.getItemMeta();
            if (prevMeta != null) {
                prevMeta.setDisplayName(colorize("&cПредыдущая страница"));
                prevMeta.setLore(Arrays.asList(colorize("&7Страница " + page)));
                prevButton.setItemMeta(prevMeta);
            }
            myOrdersGui.setItem(48, prevButton);
        }

        if (page < totalPages - 1) {
            ItemStack nextButton = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = nextButton.getItemMeta();
            if (nextMeta != null) {
                nextMeta.setDisplayName(colorize("&aСледующая страница"));
                nextMeta.setLore(Arrays.asList(colorize("&7Страница " + (page + 2))));
                nextButton.setItemMeta(nextMeta);
            }
            myOrdersGui.setItem(50, nextButton);
        }

        // Кнопка назад
        ItemStack backButton = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = backButton.getItemMeta();
        if (backMeta != null) {
            backMeta.setDisplayName(colorize("&cНазад к бирже"));
            backMeta.setLore(Arrays.asList(colorize("&7Вернуться к главному меню")));
            backButton.setItemMeta(backMeta);
        }
        myOrdersGui.setItem(49, backButton);

        // Кнопка обновить
        ItemStack refreshButton = new ItemStack(Material.CLOCK);
        ItemMeta refreshMeta = refreshButton.getItemMeta();
        if (refreshMeta != null) {
            refreshMeta.setDisplayName(colorize("&bОбновить"));
            refreshMeta.setLore(Arrays.asList(colorize("&7Обновить список ордеров")));
            refreshButton.setItemMeta(refreshMeta);
        }
        myOrdersGui.setItem(53, refreshButton);

        // Кнопка отменить все
        if (!myOrders.isEmpty()) {
            ItemStack cancelAllButton = new ItemStack(Material.TNT);
            ItemMeta cancelAllMeta = cancelAllButton.getItemMeta();
            if (cancelAllMeta != null) {
                cancelAllMeta.setDisplayName(colorize("&cОтменить все ордера"));
                cancelAllMeta.setLore(Arrays.asList(
                        colorize("&7Отменить все ваши"),
                        colorize("&7активные ордера"),
                        colorize("&e"),
                        colorize("&cОСТОРОЖНО!")
                ));
                cancelAllButton.setItemMeta(cancelAllMeta);
            }
            myOrdersGui.setItem(45, cancelAllButton);
        }

        player.openInventory(myOrdersGui);

        if (myOrders.isEmpty()) {
            player.sendMessage(colorize(Economy.getInstance().getConfig().getString("messages.bourse.no_orders", "&7У вас нет активных ордеров")));
        } else {
            player.sendMessage(colorize("&7Ваших ордеров: " + totalOrders + " (стр. " + (page + 1) + "/" + totalPages + ")"));
        }
    }

    private ItemStack createOrderDisplay(Order order) {
        ItemStack orderItem = new ItemStack(Material.PAPER);
        ItemMeta meta = orderItem.getItemMeta();
        if (meta != null) {
            double rate = (double) order.getBuyAmount() / order.getSellAmount();
            meta.setDisplayName(colorize("&6Ордер #" + order.getId()));

            List<String> lore = Economy.getInstance().getConfig().getStringList("bourse.order_display.order_lore");
            if (lore.isEmpty()) {
                lore = Arrays.asList(
                        "&7Продавец: &f{seller}",
                        "&7Продает: &e{sell_amount} {sell_currency}",
                        "&7Покупает: &e{buy_amount} {buy_currency}",
                        "&7Курс: &f{rate}",
                        "&7За 1 {sell_currency} = {rate} {buy_currency}",
                        "",
                        "&aЛКМ - Купить",
                        "&cПКМ - Подробнее"
                );
            }

            for (int i = 0; i < lore.size(); i++) {
                String line = lore.get(i);
                line = line.replace("{seller}", order.getNickname())
                        .replace("{sell_amount}", String.format("%,d", order.getSellAmount()))
                        .replace("{sell_currency}", order.getSellCurrency())
                        .replace("{buy_amount}", String.format("%,d", order.getBuyAmount()))
                        .replace("{buy_currency}", order.getBuyCurrency())
                        .replace("{rate}", String.format("%.6f", rate));
                lore.set(i, colorize(line));
            }

            meta.setLore(lore);
            orderItem.setItemMeta(meta);
        }
        return orderItem;
    }

    private ItemStack createMyOrderDisplay(Order order) {
        ItemStack orderItem = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta meta = orderItem.getItemMeta();
        if (meta != null) {
            double rate = (double) order.getBuyAmount() / order.getSellAmount();
            meta.setDisplayName(colorize("&6Мой ордер #" + order.getId()));

            List<String> lore = Economy.getInstance().getConfig().getStringList("bourse.order_display.my_order_lore");
            if (lore.isEmpty()) {
                lore = Arrays.asList(
                        "&7Продаю: &e{sell_amount} {sell_currency}",
                        "&7Покупаю: &e{buy_amount} {buy_currency}",
                        "&7Курс: &f{rate}",
                        "&7Статус: &aАктивен",
                        "",
                        "&cЛКМ - Отменить ордер",
                        "&eПКМ - Подробная информация",
                        "&7Shift+ЛКМ - Подтвердить отмену"
                );
            }

            for (int i = 0; i < lore.size(); i++) {
                String line = lore.get(i);
                line = line.replace("{sell_amount}", String.format("%,d", order.getSellAmount()))
                        .replace("{sell_currency}", order.getSellCurrency())
                        .replace("{buy_amount}", String.format("%,d", order.getBuyAmount()))
                        .replace("{buy_currency}", order.getBuyCurrency())
                        .replace("{rate}", String.format("%.6f", rate));
                lore.set(i, colorize(line));
            }

            meta.setLore(lore);
            orderItem.setItemMeta(meta);
        }
        return orderItem;
    }

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

        // БЛОКИРУЕМ ВСЕ КЛИКИ В НАШИХ МЕНЮ
        event.setCancelled(true);

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        int slot = event.getSlot();

        // Проверяем что клик в верхнем инвентаре (наше меню)
        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            return; // Клик в инвентаре игрока - не обрабатываем
        }

        switch (menuType) {
            case "bourse":
                handleBourseMenuClick(player, slot);
                break;
            case "trading_pair":
                handleTradingPairClick(player, slot, event.isLeftClick());
                break;
            case "my_orders":
                handleMyOrdersClick(player, slot, event.isLeftClick(), event.isRightClick(), event.isShiftClick());
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
            playerTradingPair.remove(player.getName());
        }
    }

    private boolean isOurInventory(String title) {
        // Проверяем что это один из наших инвентарей
        String cleanTitle = title.replace("§", "&");

        return cleanTitle.contains("Биржа валют") ||
                cleanTitle.contains("Мои ордера") ||
                cleanTitle.contains("→") ||
                cleanTitle.contains("Bourse") ||
                cleanTitle.contains("Orders");
    }

    private void handleBourseMenuClick(Player player, int slot) {
        if (slot == 49) { // Создать ордер
            player.closeInventory();
            player.sendMessage(colorize("&7Используйте: /bourse add <продаю_валюта> <кол-во> <покупаю_валюта> <кол-во>"));
        } else if (slot == 53) { // Мои ордера
            openMyOrdersMenu(player, 0);
        } else if (slot == 22) { // Статистика
            player.closeInventory();
            showBourseStats(player);
        } else if (slot == 45) { // Помощь
            player.closeInventory();
            showHelp(player);
        } else if (slot >= 10 && slot <= 20) { // Торговые пары
            String[] pair = getCurrencyPairBySlot(slot);
            if (pair != null) {
                openTradingPairOrders(player, pair[0], pair[1]);
            }
        }
    }

    private void handleTradingPairClick(Player player, int slot, boolean leftClick) {
        if (slot == 45) { // Назад
            openBourseMenu(player);
        } else if (slot == 49) { // Создать ордер
            String[] pair = playerTradingPair.get(player.getName());
            if (pair != null) {
                player.closeInventory();
                player.sendMessage(colorize("&7Создание ордера для пары " + pair[0] + "/" + pair[1] + ":"));
                player.sendMessage(colorize("&f/bourse add " + pair[0] + " <количество> " + pair[1] + " <количество>"));
            }
        } else if (slot == 53) { // Обновить
            String[] pair = playerTradingPair.get(player.getName());
            if (pair != null) {
                openTradingPairOrders(player, pair[0], pair[1]);
            }
        } else if (slot < 45) { // Клик по ордеру
            Database database = Economy.getInstance().getDatabase();
            String[] pair = playerTradingPair.get(player.getName());

            if (pair != null) {
                List<Order> orders = database.getOrders(pair[0], pair[1]);
                if (slot < orders.size()) {
                    Order order = orders.get(slot);
                    if (leftClick) {
                        // Покупка ордера
                        player.closeInventory();
                        handleBuyOrder(player, String.valueOf(order.getId()));
                    } else {
                        // Показать детали
                        player.closeInventory();
                        showOrderDetails(player, order);
                    }
                }
            }
        }
    }

    private void handleMyOrdersClick(Player player, int slot, boolean leftClick, boolean rightClick, boolean shiftClick) {
        if (slot == 49) { // Назад
            openBourseMenu(player);
        } else if (slot == 53) { // Обновить
            int currentPage = playerPages.getOrDefault(player.getName(), 0);
            openMyOrdersMenu(player, currentPage);
        } else if (slot == 48) { // Предыдущая страница
            int currentPage = playerPages.getOrDefault(player.getName(), 0);
            if (currentPage > 0) {
                openMyOrdersMenu(player, currentPage - 1);
            }
        } else if (slot == 50) { // Следующая страница
            int currentPage = playerPages.getOrDefault(player.getName(), 0);
            openMyOrdersMenu(player, currentPage + 1);
        } else if (slot == 45) { // Отменить все
            if (shiftClick) {
                cancelAllOrders(player);
                openMyOrdersMenu(player, 0);
            } else {
                player.sendMessage(colorize("&eВы уверены что хотите отменить ВСЕ ордера?"));
                player.sendMessage(colorize("&7Нажмите Shift+ЛКМ на кнопку для подтверждения"));
            }
        } else if (slot < 45) { // Клик по ордеру
            Database database = Economy.getInstance().getDatabase();
            int currentPage = playerPages.getOrDefault(player.getName(), 0);
            List<Order> myOrders = database.getPlayerOrdersPaginated(player.getName(), currentPage, 45);

            if (slot < myOrders.size()) {
                Order order = myOrders.get(slot);

                if (leftClick && shiftClick) {
                    // Подтверждение отмены
                    cancelOrder(player, order);
                    openMyOrdersMenu(player, currentPage); // Обновляем меню
                } else if (leftClick) {
                    // Предупреждение об отмене
                    player.sendMessage(colorize("&eВы уверены что хотите отменить ордер #" + order.getId() + "?"));
                    player.sendMessage(colorize("&7Нажмите Shift+ЛКМ для подтверждения"));
                    player.sendMessage(colorize("&7Будет возвращено: " + String.format("%,d", order.getSellAmount()) + " " + order.getSellCurrency()));
                } else if (rightClick) {
                    // Показать детали
                    player.closeInventory();
                    showOrderDetails(player, order);
                }
            }
        }
    }

    private String[] getCurrencyPairBySlot(int slot) {
        switch (slot) {
            case 10: return new String[]{"VIL", "USD"};
            case 11: return new String[]{"VIL", "EUR"};
            case 12: return new String[]{"USD", "EUR"};
            case 13: return new String[]{"VIL", "RUB"};
            case 14: return new String[]{"USD", "RUB"};
            case 15: return new String[]{"EUR", "RUB"};
            case 16: return new String[]{"VIL", "GBP"};
            case 19: return new String[]{"USD", "GBP"};
            case 20: return new String[]{"EUR", "GBP"};
            default: return null;
        }
    }

    private void handleAddOrder(Player player, String[] args) {
        WalletManager walletManager = Economy.getInstance().getWalletManager();
        Database database = Economy.getInstance().getDatabase();

        // Проверяем лимит ордеров
        int currentOrders = database.getPlayerOrderCount(player.getName());
        int maxOrders = getMaxOrders(player);

        if (maxOrders != -1 && currentOrders >= maxOrders) {
            String message = Economy.getInstance().getConfig().getString("messages.bourse.max_orders_reached", "&cВы достигли лимита ордеров! ({max})")
                    .replace("{max}", String.valueOf(maxOrders));
            player.sendMessage(colorize(message));
            player.sendMessage(colorize("&7Отмените существующие ордера или получите больше прав"));
            return;
        }

        String sellCurrency = args[1].toUpperCase();
        String buyCurrency = args[3].toUpperCase();
        int sellAmount, buyAmount;

        try {
            sellAmount = Integer.parseInt(args[2]);
            buyAmount = Integer.parseInt(args[4]);
        } catch (NumberFormatException e) {
            player.sendMessage(colorize("&cНеверные суммы! Введите числа."));
            return;
        }

        if (sellAmount <= 0 || buyAmount <= 0) {
            player.sendMessage(colorize("&cСуммы должны быть больше 0!"));
            return;
        }

        if (sellAmount > 1000000000 || buyAmount > 1000000000) {
            player.sendMessage(colorize("&cСлишком большие суммы! Максимум: 1,000,000,000"));
            return;
        }

        if (!walletManager.currencyExists(sellCurrency) || !walletManager.currencyExists(buyCurrency)) {
            player.sendMessage(colorize("&cОдна из валют не существует!"));
            return;
        }

        if (sellCurrency.equals(buyCurrency)) {
            String message = Economy.getInstance().getConfig().getString("messages.bourse.same_currency", "&cНельзя обменивать валюту на саму себя!");
            player.sendMessage(colorize(message));
            return;
        }

        // Проверяем баланс
        if (walletManager.getPlayerWallet(player.getName()).getCurrencyAmount(sellCurrency) < sellAmount) {
            String message = Economy.getInstance().getConfig().getString("messages.bourse.insufficient_funds", "&cНедостаточно средств!")
                    .replace("{currency}", sellCurrency)
                    .replace("{amount}", String.format("%,d", sellAmount));
            player.sendMessage(colorize(message));
            player.sendMessage(colorize("&7У вас: " + String.format("%,d", walletManager.getPlayerWallet(player.getName()).getCurrencyAmount(sellCurrency))));
            return;
        }

        // Создаем ордер
        Order order = new Order(0, player.getName(), sellCurrency, buyCurrency, sellAmount, buyAmount);
        BourseManager bourseManager = BourseManager.getInstance();

        if (bourseManager.addOrder(order)) {
            double rate = (double) buyAmount / sellAmount;
            String message = Economy.getInstance().getConfig().getString("messages.bourse.order_created", "&aОрдер создан успешно!");
            player.sendMessage(colorize(message));
            player.sendMessage(colorize("&7Продаю: " + String.format("%,d", sellAmount) + " " + sellCurrency));
            player.sendMessage(colorize("&7Покупаю: " + String.format("%,d", buyAmount) + " " + buyCurrency));
            player.sendMessage(colorize("&7Курс: " + String.format("%.6f", rate)));
            player.sendMessage(colorize("&7Ордеров: " + (currentOrders + 1) + "/" + (maxOrders == -1 ? "∞" : maxOrders)));
            player.sendMessage(colorize("&7Ваши " + String.format("%,d", sellAmount) + " " + sellCurrency + " заблокированы до исполнения ордера"));

            // Логируем создание ордера
            database.logTransaction(player.getName(), null, sellCurrency, sellAmount, "ORDER_CREATE", "Order created on bourse");

        } else {
            player.sendMessage(colorize("&cОшибка при создании ордера!"));
        }
    }

    private void handleBuyOrder(Player player, String orderIdStr) {
        try {
            int orderId = Integer.parseInt(orderIdStr);
            BourseManager bourseManager = BourseManager.getInstance();
            Database database = Economy.getInstance().getDatabase();
            WalletManager walletManager = Economy.getInstance().getWalletManager();

            // Получаем ордер
            Order order = database.getOrderById(orderId);
            if (order == null) {
                String message = Economy.getInstance().getConfig().getString("messages.bourse.order_not_found", "&cОрдер не найден!");
                player.sendMessage(colorize(message.replace("{id}", String.valueOf(orderId))));
                return;
            }

            // Проверяем что игрок не покупает свой ордер
            if (order.getNickname().equals(player.getName())) {
                String message = Economy.getInstance().getConfig().getString("messages.bourse.cannot_buy_own_order", "&cВы не можете купить свой собственный ордер!");
                player.sendMessage(colorize(message));
                return;
            }

            // Проверяем баланс покупателя
            String buyCurrency = order.getBuyCurrency();
            int buyAmount = order.getBuyAmount();

            if (walletManager.getPlayerWallet(player.getName()).getCurrencyAmount(buyCurrency) < buyAmount) {
                String message = Economy.getInstance().getConfig().getString("messages.bourse.insufficient_funds", "&cНедостаточно средств!")
                        .replace("{currency}", buyCurrency)
                        .replace("{amount}", String.format("%,d", buyAmount));
                player.sendMessage(colorize(message));
                player.sendMessage(colorize("&7У вас: " + String.format("%,d", walletManager.getPlayerWallet(player.getName()).getCurrencyAmount(buyCurrency))));
                return;
            }

            // Выполняем обмен
            ExchangeResult result = bourseManager.exchange(orderId, player.getName());

            switch (result) {
                case SUCCESS:
                    String successMessage = Economy.getInstance().getConfig().getString("messages.bourse.order_executed", "&aОрдер #{id} исполнен!")
                            .replace("{id}", String.valueOf(orderId));
                    player.sendMessage(colorize(successMessage));
                    player.sendMessage(colorize("&7Вы отдали: " + String.format("%,d", buyAmount) + " " + buyCurrency));
                    player.sendMessage(colorize("&7Вы получили: " + String.format("%,d", order.getSellAmount()) + " " + order.getSellCurrency()));

                    // Уведомляем продавца
                    Player seller = Bukkit.getPlayer(order.getNickname());
                    if (seller != null) {
                        seller.sendMessage(colorize(successMessage));
                        seller.sendMessage(colorize("&7Вы получили: " + String.format("%,d", buyAmount) + " " + buyCurrency));
                        seller.sendMessage(colorize("&7От игрока: " + player.getName()));
                    }

                    // Логируем обмен
                    database.logTransaction(player.getName(), order.getNickname(), buyCurrency, buyAmount, "ORDER_EXCHANGE", "Order #" + orderId + " exchanged");
                    database.logTransaction(order.getNickname(), player.getName(), order.getSellCurrency(), order.getSellAmount(), "ORDER_EXCHANGE", "Order #" + orderId + " exchanged");

                    break;

                case ORDER_NOT_FOUND:
                    String notFoundMessage = Economy.getInstance().getConfig().getString("messages.bourse.order_not_found", "&cОрдер не найден!");
                    player.sendMessage(colorize(notFoundMessage));
                    break;

                case FAILED:
                    player.sendMessage(colorize("&cОшибка при выполнении обмена!"));
                    break;

                default:
                    player.sendMessage(colorize("&cНеизвестная ошибка: " + result.name()));
                    break;
            }

        } catch (NumberFormatException e) {
            String message = Economy.getInstance().getConfig().getString("messages.bourse.invalid_order_id", "&cНеверный ID ордера!");
            player.sendMessage(colorize(message));
        }
    }

    private void handleCancelOrder(Player player, String orderIdStr) {
        try {
            int orderId = Integer.parseInt(orderIdStr);
            Database database = Economy.getInstance().getDatabase();

            Order order = database.getPlayerOrder(orderId, player.getName());
            if (order == null) {
                String message = Economy.getInstance().getConfig().getString("messages.bourse.order_not_found", "&cОрдер #{id} не найден или не принадлежит вам!")
                        .replace("{id}", String.valueOf(orderId));
                player.sendMessage(colorize(message));
                return;
            }

            cancelOrder(player, order);

        } catch (NumberFormatException e) {
            String message = Economy.getInstance().getConfig().getString("messages.bourse.invalid_order_id", "&cНеверный ID ордера!");
            player.sendMessage(colorize(message));
        }
    }

    private void cancelOrder(Player player, Order order) {
        Database database = Economy.getInstance().getDatabase();
        WalletManager walletManager = Economy.getInstance().getWalletManager();

        // Возвращаем валюту игроку
        walletManager.putMoney(player.getName(), order.getSellCurrency(), order.getSellAmount());

        // Удаляем ордер
        if (database.cancelPlayerOrder(order.getId(), player.getName())) {
            String message = Economy.getInstance().getConfig().getString("messages.bourse.order_cancelled", "&aОрдер #{id} отменен!")
                    .replace("{id}", String.valueOf(order.getId()));
            player.sendMessage(colorize(message));
            player.sendMessage(colorize("&7Возвращено: " + String.format("%,d", order.getSellAmount()) + " " + order.getSellCurrency()));

            // Логируем отмену
            database.logTransaction(player.getName(), null, order.getSellCurrency(), order.getSellAmount(),
                    "ORDER_CANCEL", "Order #" + order.getId() + " cancelled by player");

        } else {
            player.sendMessage(colorize("&cОшибка при отмене ордера!"));
        }
    }

    private void cancelAllOrders(Player player) {
        Database database = Economy.getInstance().getDatabase();
        WalletManager walletManager = Economy.getInstance().getWalletManager();
        List<Order> playerOrders = database.getPlayerOrders(player.getName());

        if (playerOrders.isEmpty()) {
            player.sendMessage(colorize("&7У вас нет активных ордеров для отмены"));
            return;
        }

        int cancelledCount = 0;

        for (Order order : playerOrders) {
            // Возвращаем валюту
            walletManager.putMoney(player.getName(), order.getSellCurrency(), order.getSellAmount());

            // Отменяем ордер
            if (database.cancelPlayerOrder(order.getId(), player.getName())) {
                cancelledCount++;

                // Логируем отмену
                database.logTransaction(player.getName(), null, order.getSellCurrency(), order.getSellAmount(),
                        "ORDER_CANCEL_ALL", "Order #" + order.getId() + " cancelled (cancel all)");
            }
        }

        player.sendMessage(colorize("&aОтменено ордеров: " + cancelledCount));
        if (cancelledCount > 0) {
            player.sendMessage(colorize("&7Возвращено валют в кошелек"));
        }
    }

    private void showOrderDetails(Player player, Order order) {
        double rate = (double) order.getBuyAmount() / order.getSellAmount();
        player.sendMessage(colorize("&6=== Детали ордера #" + order.getId() + " ==="));
        if (order.getNickname().equals(player.getName())) {
            player.sendMessage(colorize("&7Ваш ордер"));
        } else {
            player.sendMessage(colorize("&7Продавец: &f" + order.getNickname()));
        }
        player.sendMessage(colorize("&7Продает: &e" + String.format("%,d", order.getSellAmount()) + " " + order.getSellCurrency()));
        player.sendMessage(colorize("&7Покупает: &e" + String.format("%,d", order.getBuyAmount()) + " " + order.getBuyCurrency()));
        player.sendMessage(colorize("&7Курс: &f" + String.format("%.6f", rate)));
        player.sendMessage(colorize("&7За 1 " + order.getSellCurrency() + " получите " + String.format("%.6f", rate) + " " + order.getBuyCurrency()));

        if (order.getNickname().equals(player.getName())) {
            player.sendMessage(colorize("&7Для отмены: &c/bourse cancel " + order.getId()));
        } else {
            player.sendMessage(colorize("&7Для покупки: &a/bourse buy " + order.getId()));
        }
    }

    private void showBourseStats(Player player) {
        Database database = Economy.getInstance().getDatabase();

        player.sendMessage(colorize("&6=== Статистика биржи ==="));

        int totalOrders = database.getTotalOrdersCount();
        int myOrders = database.getPlayerOrderCount(player.getName());
        int maxOrders = getMaxOrders(player);

        player.sendMessage(colorize("&7Всего активных ордеров: &f" + totalOrders));
        player.sendMessage(colorize("&7Ваших ордеров: &f" + myOrders + "/" + (maxOrders == -1 ? "∞" : maxOrders)));

        // Топ торговые пары
        player.sendMessage(colorize("&7Популярные торговые пары:"));
        String[] pairs = {"VIL/USD", "VIL/EUR", "USD/EUR", "VIL/RUB", "USD/RUB", "EUR/RUB"};
        for (String pair : pairs) {
            String[] currencies = pair.split("/");
            int count = database.getOrders(currencies[0], currencies[1]).size();
            if (count > 0) {
                player.sendMessage(colorize("&8• &f" + pair + ": &e" + count + " ордеров"));
            }
        }

        player.sendMessage(colorize("&e"));
        player.sendMessage(colorize("&7Для просмотра конкретной пары:"));
        player.sendMessage(colorize("&f/bourse pair <валюта1> <валюта2>"));
    }

    private void showHelp(Player player) {
        player.sendMessage(colorize("&6=== Биржа валют ==="));
        player.sendMessage(colorize("&7/bourse &f- открыть меню биржи"));
        player.sendMessage(colorize("&7/bourse add <продаю_валюта> <кол-во> <покупаю_валюта> <кол-во> &f- создать ордер"));
        player.sendMessage(colorize("&7/bourse buy <id> &f- купить по ордеру"));
        player.sendMessage(colorize("&7/bourse cancel <id> &f- отменить свой ордер"));
        player.sendMessage(colorize("&7/bourse my &f- мои ордера"));
        player.sendMessage(colorize("&7/bourse pair <валюта1> <валюта2> &f- просмотр торговой пары"));
        player.sendMessage(colorize("&e"));
        player.sendMessage(colorize("&7Пример создания ордера:"));
        player.sendMessage(colorize("&f/bourse add VIL 100 USD 50"));
        player.sendMessage(colorize("&7(продаю 100 VIL, покупаю 50 USD)"));
        player.sendMessage(colorize("&e"));
        player.sendMessage(colorize("&7Лимит ордеров: " + (getMaxOrders(player) == -1 ? "∞" : getMaxOrders(player))));
        player.sendMessage(colorize("&e"));
        player.sendMessage(colorize("&7Поддерживаемые валюты:"));
        player.sendMessage(colorize("&fVIL, USD, EUR, RUB, GBP &7и другие созданные валюты"));
    }

    private void addCurrencyPair(Inventory gui, int slot, String currency1, String currency2) {
        Database database = Economy.getInstance().getDatabase();
        List<Order> orders = database.getOrders(currency1, currency2);

        ItemStack pairItem = new ItemStack(Material.GOLD_INGOT);
        ItemMeta meta = pairItem.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(colorize("&6" + currency1 + " ↔ " + currency2));
            meta.setLore(Arrays.asList(
                    colorize("&7Торговая пара"),
                    colorize("&7Активных ордеров: &f" + orders.size()),
                    colorize("&7Нажмите для просмотра ордеров")
            ));
            pairItem.setItemMeta(meta);
        }
        gui.setItem(slot, pairItem);
    }

    private String colorize(String text) {
        return text.replace("&", "§");
    }
}