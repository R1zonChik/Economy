package xyz.moorus.economy.command;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.io.BukkitObjectOutputStream;
import xyz.moorus.economy.main.Economy;
import xyz.moorus.economy.money.WalletManager;
import xyz.moorus.economy.sql.Database;

import java.io.ByteArrayOutputStream;
import java.util.Base64;

public class SellHandCommand implements Command {

    @Override
    public String getName() {
        return "sellhand";
    }

    @Override
    public void execute(String sender, String[] args) {
        WalletManager walletManager = Economy.getInstance().getWalletManager();
        Database database = Economy.getInstance().getDatabase();

        Player player = Bukkit.getPlayer(sender);
        if (player == null) return;

        if (args.length != 2) {
            player.sendMessage(colorize("&cИспользование: /sellhand <валюта> <цена>"));
            player.sendMessage(colorize("&7Пример: /sellhand VIL 100"));
            return;
        }

        String currency = args[0].toUpperCase();
        long price;

        try {
            price = Long.parseLong(args[1]);
        } catch (NumberFormatException e) {
            String message = Economy.getInstance().getConfig().getString("messages.invalid_amount", "&cНеверная сумма!");
            player.sendMessage(colorize(message));
            return;
        }

        if (price <= 0) {
            String message = Economy.getInstance().getConfig().getString("messages.auction.invalid_price", "&cЦена должна быть больше 0!");
            player.sendMessage(colorize(message));
            return;
        }

        if (!walletManager.currencyExists(currency)) {
            String message = Economy.getInstance().getConfig().getString("messages.invalid_currency", "&cНеверная валюта!");
            player.sendMessage(colorize(message));
            return;
        }

        // Проверяем предмет в руке
        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        if (itemInHand == null || itemInHand.getType() == Material.AIR) {
            String message = Economy.getInstance().getConfig().getString("messages.auction.take_item_in_hand", "&cВозьмите предмет в руку!");
            player.sendMessage(colorize(message));
            return;
        }

        // Проверяем лимит предметов на аукционе
        int currentItems = database.getPlayerAuctionItemCount(sender);
        int maxItems = getMaxAuctionItems(player);

        if (currentItems >= maxItems) {
            String message = Economy.getInstance().getConfig().getString("messages.auction.max_items_reached", "&cВы достигли лимита предметов на аукционе! ({max})")
                    .replace("{max}", String.valueOf(maxItems));
            player.sendMessage(colorize(message));
            return;
        }

        // Сериализуем предмет
        String itemData = serializeItem(itemInHand);
        String category = getItemCategory(itemInHand.getType());

        // Добавляем на аукцион
        int hoursToExpire = Economy.getInstance().getConfig().getInt("auction.expiration_hours", 72);
        int itemId = database.addAuctionItem(sender, player.getUniqueId().toString(),
                itemData, currency, price, category, hoursToExpire);

        if (itemId > 0) {
            // Убираем предмет из инвентаря
            player.getInventory().setItemInMainHand(null);

            // Логируем транзакцию
            database.logTransaction(sender, null, currency, price,
                    "AUCTION_SELL", "Item listed on auction: #" + itemId);

            // ИСПРАВЛЕНО: Используем сообщение из конфига с заменой плейсхолдеров
            String message = Economy.getInstance().getConfig().getString("messages.auction.item_listed", "&aПредмет выставлен на аукцион за {price} {currency}!");
            player.sendMessage(colorize(replacePlaceholders(message, price, currency)));
            message = message.replace("{currency}", currency);
            player.sendMessage(colorize(message));

            player.sendMessage(colorize("&7ID: " + itemId));
            player.sendMessage(colorize("&7Истекает через " + hoursToExpire + " часов"));

        } else {
            player.sendMessage(colorize("&cОшибка при выставлении предмета на аукцион!"));
        }
    }

    private String replacePlaceholders(String message, long price, String currency) {
        return message
                .replace("{price}", String.format("%,d", price))
                .replace("{currency}", currency);
    }

    // ИСПРАВЛЕНО: Операторы получают 9999 предметов
    private int getMaxAuctionItems(Player player) {
        // ИСПРАВЛЕНО: Проверяем OP статус в первую очередь
        if (player.isOp()) {
            return 9999; // Операторы могут выставлять 9999 предметов
        }

        // Проверяем права по убыванию
        if (player.hasPermission("economy.auction.max.unlimited")) return 9999;
        if (player.hasPermission("economy.auction.max.50")) return 50;
        if (player.hasPermission("economy.auction.max.25")) return 25;
        if (player.hasPermission("economy.auction.max.10")) return 10;
        if (player.hasPermission("economy.auction.max.5")) return 5;
        if (player.hasPermission("economy.auction.max.3")) return 3;

        // Базовый лимит из конфига
        return Economy.getInstance().getConfig().getInt("auction.default_max_items", 1);
    }

    private String serializeItem(ItemStack item) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);
            dataOutput.writeObject(item);
            dataOutput.close();
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (Exception e) {
            // Fallback к простой сериализации
            return item.getType().name() + ":" + item.getAmount() +
                    (item.hasItemMeta() ? ":" + item.getItemMeta().toString() : "");
        }
    }

    private String getItemCategory(Material material) {
        String name = material.name();

        if (name.contains("SWORD") || name.contains("AXE") || name.contains("BOW") ||
                name.contains("TRIDENT") || name.contains("CROSSBOW") || name.contains("MACE")) {
            return "COMBAT";
        } else if (name.contains("PICKAXE") || name.contains("SHOVEL") || name.contains("HOE") ||
                name.contains("SHEARS") || name.contains("FISHING_ROD")) {
            return "TOOLS";
        } else if (name.contains("HELMET") || name.contains("CHESTPLATE") ||
                name.contains("LEGGINGS") || name.contains("BOOTS") || name.contains("SHIELD")) {
            return "ARMOR";
        } else if (material.isBlock()) {
            return "BLOCKS";
        } else if (material.isEdible()) {
            return "FOOD";
        } else if (name.contains("POTION") || name.contains("SPLASH") || name.contains("LINGERING")) {
            return "POTIONS";
        } else if (name.contains("ENCHANTED_BOOK") || name.contains("BOOK")) {
            return "BOOKS";
        } else {
            return "MISC";
        }
    }

    private String colorize(String text) {
        return text.replace("&", "§");
    }
}