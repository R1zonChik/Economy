package xyz.moorus.economy.command;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectOutputStream;
import xyz.moorus.economy.main.Economy;
import xyz.moorus.economy.money.PaymentResult;
import xyz.moorus.economy.money.WalletManager;
import xyz.moorus.economy.sql.Database;

import java.io.ByteArrayOutputStream;
import java.util.Base64;

public class AdminCommand implements Command {

    @Override
    public String getName() {
        return "ecoadmin";
    }

    @Override
    public void execute(String sender, String[] args) {
        Player player = Bukkit.getPlayer(sender);
        if (player == null) return;

        if (!player.hasPermission("economy.admin")) {
            player.sendMessage(colorize("&cУ вас нет прав администратора!"));
            return;
        }

        if (args.length == 0) {
            showHelp(player);
            return;
        }

        String action = args[0].toLowerCase();

        switch (action) {
            case "addpremium":
                handleAddPremium(player, args);
                break;
            case "removepremium":
                handleRemovePremium(player, args);
                break;
            case "givevil":
                handleGiveVil(player, args);
                break;
            case "givemoney":
                handleGiveMoney(player, args);
                break;
            case "takemoney":
                handleTakeMoney(player, args);
                break;
            case "reload":
                handleReload(player);
                break;
            case "cleanup":
                handleCleanup(player);
                break;
            case "stats":
                handleStats(player);
                break;
            case "help":
            default:
                showHelp(player);
                break;
        }
    }

    private void showHelp(Player player) {
        player.sendMessage(colorize("&6=== Economy Admin ==="));
        player.sendMessage(colorize("&7/ecoadmin addpremium <цена> <название> <описание> [количество]"));
        player.sendMessage(colorize("&7/ecoadmin removepremium <id>"));
        player.sendMessage(colorize("&7/ecoadmin givevil <игрок> <количество>"));
        player.sendMessage(colorize("&7/ecoadmin givemoney <игрок> <валюта> <количество>"));
        player.sendMessage(colorize("&7/ecoadmin takemoney <игрок> <валюта> <количество>"));
        player.sendMessage(colorize("&7/ecoadmin reload"));
        player.sendMessage(colorize("&7/ecoadmin cleanup"));
        player.sendMessage(colorize("&7/ecoadmin stats"));
        player.sendMessage(colorize("&e"));
        player.sendMessage(colorize("&7Для addpremium возьмите предмет в руку"));
    }

    private void handleAddPremium(Player player, String[] args) {
        if (args.length < 4) {
            player.sendMessage(colorize("&cИспользование: /ecoadmin addpremium <цена> <название> <описание> [количество]"));
            return;
        }

        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        if (itemInHand == null || itemInHand.getType().isAir()) {
            player.sendMessage(colorize("&cВозьмите предмет в руку!"));
            return;
        }

        try {
            long price = Long.parseLong(args[1]);
            String displayName = args[2].replace("_", " ");
            String description = args[3].replace("_", " ");
            int stock = args.length > 4 ? Integer.parseInt(args[4]) : -1;

            if (price <= 0) {
                player.sendMessage(colorize("&cЦена должна быть больше 0!"));
                return;
            }

            Database database = Economy.getInstance().getDatabase();
            String itemData = serializeItem(itemInHand);
            String category = getItemCategory(itemInHand.getType());

            int itemId = database.addPremiumShopItem(itemData, price, category, displayName, description, stock);

            if (itemId > 0) {
                player.sendMessage(colorize("&aПредмет добавлен в премиум магазин!"));
                player.sendMessage(colorize("&7ID: " + itemId));
                player.sendMessage(colorize("&7Название: " + displayName));
                player.sendMessage(colorize("&7Цена: " + price + " VIL"));
                player.sendMessage(colorize("&7Количество: " + (stock == -1 ? "∞" : stock)));
            } else {
                player.sendMessage(colorize("&cОшибка при добавлении предмета!"));
            }

        } catch (NumberFormatException e) {
            player.sendMessage(colorize("&cНеверная цена или количество!"));
        }
    }

    private void handleRemovePremium(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(colorize("&cИспользование: /ecoadmin removepremium <id>"));
            return;
        }

        try {
            int itemId = Integer.parseInt(args[1]);
            Database database = Economy.getInstance().getDatabase();

            if (database.removePremiumShopItem(itemId)) {
                player.sendMessage(colorize("&aПредмет удален из премиум магазина!"));
            } else {
                player.sendMessage(colorize("&cПредмет не найден!"));
            }

        } catch (NumberFormatException e) {
            player.sendMessage(colorize("&cНеверный ID предмета!"));
        }
    }

    private void handleGiveVil(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(colorize("&cИспользование: /ecoadmin givevil <игрок> <количество>"));
            return;
        }

        String targetName = args[1];

        try {
            int amount = Integer.parseInt(args[2]);

            if (amount <= 0) {
                player.sendMessage(colorize("&cКоличество должно быть больше 0!"));
                return;
            }

            WalletManager walletManager = Economy.getInstance().getWalletManager();
            PaymentResult result = walletManager.putMoney(targetName, "VIL", amount);

            if (result == PaymentResult.SUCCESS) {
                player.sendMessage(colorize("&aВыдано " + String.format("%,d", amount) + " VIL игроку " + targetName));
                Player target = Bukkit.getPlayer(targetName);
                if (target != null) {
                    target.sendMessage(colorize("&aВы получили " + String.format("%,d", amount) + " VIL от администратора!"));
                }

                // Логируем транзакцию
                Database database = Economy.getInstance().getDatabase();
                database.logTransaction("SYSTEM", targetName, "VIL", amount, "ADMIN_GIVE", "Admin gave VIL");

            } else {
                player.sendMessage(colorize("&cОшибка при выдаче валюты: " + result.name()));
            }

        } catch (NumberFormatException e) {
            player.sendMessage(colorize("&cНеверное количество!"));
        }
    }

    private void handleGiveMoney(Player player, String[] args) {
        if (args.length < 4) {
            player.sendMessage(colorize("&cИспользование: /ecoadmin givemoney <игрок> <валюта> <количество>"));
            return;
        }

        String targetName = args[1];
        String currency = args[2].toUpperCase();

        try {
            int amount = Integer.parseInt(args[3]);

            if (amount <= 0) {
                player.sendMessage(colorize("&cКоличество должно быть больше 0!"));
                return;
            }

            WalletManager walletManager = Economy.getInstance().getWalletManager();

            if (!walletManager.currencyExists(currency)) {
                player.sendMessage(colorize("&cВалюта " + currency + " не существует!"));
                return;
            }

            PaymentResult result = walletManager.putMoney(targetName, currency, amount);

            if (result == PaymentResult.SUCCESS) {
                player.sendMessage(colorize("&aВыдано " + String.format("%,d", amount) + " " + currency + " игроку " + targetName));
                Player target = Bukkit.getPlayer(targetName);
                if (target != null) {
                    target.sendMessage(colorize("&aВы получили " + String.format("%,d", amount) + " " + currency + " от администратора!"));
                }

                // Логируем транзакцию
                Database database = Economy.getInstance().getDatabase();
                database.logTransaction("SYSTEM", targetName, currency, amount, "ADMIN_GIVE", "Admin gave " + currency);

            } else {
                player.sendMessage(colorize("&cОшибка при выдаче валюты: " + result.name()));
            }

        } catch (NumberFormatException e) {
            player.sendMessage(colorize("&cНеверное количество!"));
        }
    }

    private void handleTakeMoney(Player player, String[] args) {
        if (args.length < 4) {
            player.sendMessage(colorize("&cИспользование: /ecoadmin takemoney <игрок> <валюта> <количество>"));
            return;
        }

        String targetName = args[1];
        String currency = args[2].toUpperCase();

        try {
            int amount = Integer.parseInt(args[3]);

            if (amount <= 0) {
                player.sendMessage(colorize("&cКоличество должно быть больше 0!"));
                return;
            }

            WalletManager walletManager = Economy.getInstance().getWalletManager();

            if (!walletManager.currencyExists(currency)) {
                player.sendMessage(colorize("&cВалюта " + currency + " не существует!"));
                return;
            }

            PaymentResult result = walletManager.getMoney(targetName, currency, amount);

            if (result == PaymentResult.SUCCESS) {
                player.sendMessage(colorize("&aЗабрано " + String.format("%,d", amount) + " " + currency + " у игрока " + targetName));
                Player target = Bukkit.getPlayer(targetName);
                if (target != null) {
                    target.sendMessage(colorize("&cУ вас забрали " + String.format("%,d", amount) + " " + currency + " администратором!"));
                }

                // Логируем транзакцию
                Database database = Economy.getInstance().getDatabase();
                database.logTransaction(targetName, "SYSTEM", currency, amount, "ADMIN_TAKE", "Admin took " + currency);

            } else {
                player.sendMessage(colorize("&cОшибка при изъятии валюты: " + result.name()));
            }

        } catch (NumberFormatException e) {
            player.sendMessage(colorize("&cНеверное количество!"));
        }
    }

    private void handleReload(Player player) {
        try {
            Economy.getInstance().reloadConfig();
            player.sendMessage(colorize("&aКонфигурация перезагружена!"));
        } catch (Exception e) {
            player.sendMessage(colorize("&cОшибка при перезагрузке: " + e.getMessage()));
        }
    }

    private void handleCleanup(Player player) {
        try {
            Database database = Economy.getInstance().getDatabase();
            database.cleanupExpiredAuctions();
            player.sendMessage(colorize("&aОчистка истекших аукционов выполнена!"));
        } catch (Exception e) {
            player.sendMessage(colorize("&cОшибка при очистке: " + e.getMessage()));
        }
    }

    private void handleStats(Player player) {
        try {
            Database database = Economy.getInstance().getDatabase();

            player.sendMessage(colorize("&6=== Статистика Economy ==="));
            player.sendMessage(colorize("&7Активных ордеров на бирже: &f" + database.getTotalOrdersCount()));
            player.sendMessage(colorize("&7Предметов в премиум магазине: &f" + database.getPremiumShopItems().size()));

            // Подсчитываем активные аукционы
            int activeAuctions = database.getAuctionItems("ALL", null, 0, Integer.MAX_VALUE).size();
            player.sendMessage(colorize("&7Активных лотов на аукционе: &f" + activeAuctions));

        } catch (Exception e) {
            player.sendMessage(colorize("&cОшибка при получении статистики: " + e.getMessage()));
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