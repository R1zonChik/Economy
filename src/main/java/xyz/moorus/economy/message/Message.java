package xyz.moorus.economy.message;

public enum Message {

    YOUR_WALLET_EMPTY(new String[] {"Your wallet is empty", "Ваш кошелёк пуст"}),

    WRONG_PAY_RECIPIENT(new String[]{"Wrong recipient", "Неверный получатель"}),

    WRONG_PAY_CURRENCY(new String[]{"Wrong currency", "Неверная валюта"}),

    WRONG_PAY_AMOUNT(new String[]{"Wrong amount. Amount must be more than zero", "Неверная сумма. Сумма должна быть больше нуля"}),

    NOT_ENOUGH_MONEY(new String[]{"Not enough money", "Недостаточно средств"}),

    PAY_SUCCESS(new String[]{"Payment successful", "Успешный платёж"}),

    PAYMENT_RECEIVE(new String[]{"%s sent you %s", "%s отправил Вам %s"}),

    YOUR_NOT_PERMITTED(new String[]{"Not enough rights to do this", "Недостаточно прав"}),

    BUY(new String[]{"Buy", "Купить"}),

    FOR(new String[]{"for", "за"}),

    PRICE(new String[] {"Price", "Цена"}),

    SELL(new String[] {"Sell","Продать"}),

    BOURSE_USAGE(new String[] {"Usage: /bourse list <sell currency> <buy currency> - view and exchange currencies\n" +
            " /bourse sell <sell currency> <sell amount> <buy currency> <buy amount> ",
            "Использование: /bourse list <валюта продажи> <валюта покупки> - просмотреть и обменять валюты\n" +
                    "/bourse sell <валюта продажи> <количество продажи> <валюта покупки> <количество покупки>"}),

    CC_USAGE(new String[]{"Usage: /cc <currency name> <amount> - create currency", "/cc <валюта> <количество> - создать валюту"}),

    // Новые сообщения для аукциона
    AUCTION_ITEM_SOLD(new String[]{"Your item has been sold!", "Ваш предмет был продан!"}),

    AUCTION_ITEM_BOUGHT(new String[]{"Item purchased successfully!", "Предмет успешно куплен!"}),

    AUCTION_ITEM_EXPIRED(new String[]{"Your auction item has expired", "Срок действия вашего лота истёк"}),

    AUCTION_NOT_ENOUGH_MONEY(new String[]{"Not enough money to buy this item", "Недостаточно средств для покупки этого предмета"}),

    AUCTION_ITEM_LISTED(new String[]{"Item listed on auction successfully!", "Предмет успешно выставлен на аукцион!"}),

    AUCTION_MAX_LISTINGS_REACHED(new String[]{"You have reached the maximum number of listings", "Вы достигли максимального количества лотов"}),

    AUCTION_INVALID_PRICE(new String[]{"Invalid price! Price must be greater than 0", "Неверная цена! Цена должна быть больше 0"}),

    AUCTION_NO_ITEM_IN_HAND(new String[]{"You must hold an item in your hand!", "Вы должны держать предмет в руке!"}),

    PREMIUM_SHOP_ITEM_BOUGHT(new String[]{"Premium item purchased successfully!", "Премиум предмет успешно куплен!"}),

    PREMIUM_SHOP_NOT_ENOUGH_VIL(new String[]{"Not enough VIL to buy this item", "Недостаточно VIL для покупки этого предмета"}),

    PREMIUM_SHOP_OUT_OF_STOCK(new String[]{"This item is out of stock", "Этот предмет закончился"}),

    CURRENCY_CREATED(new String[]{"Currency created successfully!", "Валюта успешно создана!"}),

    CURRENCY_ALREADY_EXISTS(new String[]{"Currency already exists!", "Валюта уже существует!"}),

    CURRENCY_INVALID_CODE(new String[]{"Invalid currency code! Must be 3 uppercase letters", "Неверный код валюты! Должен состоять из 3 заглавных букв"}),

    CURRENCY_EMITTED(new String[]{"Currency emitted successfully!", "Валюта успешно выпущена!"}),

    CURRENCY_EMISSION_LIMIT_EXCEEDED(new String[]{"Emission limit exceeded!", "Превышен лимит эмиссии!"}),

    NO_PERMISSION_MANAGE_CURRENCY(new String[]{"You don't have permission to manage this currency!", "У вас нет прав на управление этой валютой!"}),

    PLAYER_NOT_FOUND(new String[]{"Player not found!", "Игрок не найден!"}),

    INVALID_AMOUNT(new String[]{"Invalid amount! Must be a number greater than 0", "Неверная сумма! Должна быть числом больше 0"}),

    COMMAND_USAGE_ERROR(new String[]{"Incorrect command usage!", "Неверное использование команды!"}),

    WALLET_PAGE_NOT_EXISTS(new String[]{"Page %d does not exist! Total pages: %d", "Страница %d не существует! Всего страниц: %d"}),

    WALLET_EMPTY(new String[]{"Your wallet is empty!", "Ваш кошелек пуст!"}),

    WALLET_HEADER(new String[]{"=== Your Wallet ===", "=== Ваш кошелек ==="}),

    WALLET_PAGE_INFO(new String[]{"Page %d of %d", "Страница %d из %d"}),

    WALLET_NEXT_PAGE(new String[]{"For next page: /pw %d", "Для следующей страницы: /pw %d"}),

    WALLET_PREV_PAGE(new String[]{"For previous page: /pw %d", "Для предыдущей страницы: /pw %d"});

    private String[] locales;

    Message(String[] locales) {
        this.locales = locales;
    }

    public String getByLocale(String locale) {
        // ИСПРАВЛЕНО: Java 8 совместимый switch
        switch (locale.toLowerCase()) {
            case "ru_ru":
            case "ru_ua":
            case "ru":
                return locales[1];
            default:
                return locales[0];
        }
    }

    // Дополнительный метод для форматирования сообщений с параметрами
    public String getByLocale(String locale, Object... args) {
        String message = getByLocale(locale);
        return String.format(message, args);
    }
}