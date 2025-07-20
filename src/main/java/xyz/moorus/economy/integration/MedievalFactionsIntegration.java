package xyz.moorus.economy.integration;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import xyz.moorus.economy.main.Economy;

public class MedievalFactionsIntegration {

    private final Economy plugin;
    private boolean enabled = false;
    private Object medievalFactionsPlugin;
    private Object services;

    public MedievalFactionsIntegration(Economy plugin) {
        this.plugin = plugin;
        this.medievalFactionsPlugin = Bukkit.getPluginManager().getPlugin("MedievalFactions");
        this.enabled = medievalFactionsPlugin != null;

        if (enabled) {
            try {
                this.services = medievalFactionsPlugin.getClass().getMethod("getServices").invoke(medievalFactionsPlugin);
                plugin.getLogger().info("Medieval Factions API успешно подключен!");
            } catch (Exception e) {
                plugin.getLogger().severe("Ошибка подключения к Medieval Factions API: " + e.getMessage());
                this.enabled = false;
            }
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getPlayerFactionId(Player player) {
        if (!enabled) return null;

        try {
            // Получаем playerService
            Object playerService = services.getClass().getMethod("getPlayerService").invoke(services);

            // Получаем все игроков и ищем нашего
            Object players = playerService.getClass().getMethod("getPlayers").invoke(playerService);

            // Проходим по всем игрокам
            if (players instanceof Iterable) {
                for (Object mfPlayer : (Iterable<?>) players) {
                    // Получаем ID игрока
                    Object playerId = mfPlayer.getClass().getMethod("getId").invoke(mfPlayer);
                    Object playerUUID = playerId.getClass().getMethod("getValue").invoke(playerId);

                    // Сравниваем UUID
                    if (player.getUniqueId().equals(playerUUID)) {
                        // Нашли игрока! Получаем ID фракции
                        Object factionId = mfPlayer.getClass().getMethod("getFactionId").invoke(mfPlayer);
                        if (factionId != null) {
                            return factionId.getClass().getMethod("getValue").invoke(factionId).toString();
                        }
                        break;
                    }
                }
            }

        } catch (Exception e) {
            plugin.getLogger().warning("Ошибка получения ID фракции: " + e.getMessage());
        }

        return null;
    }

    public String getFactionName(String factionId) {
        if (!enabled || factionId == null) return null;

        try {
            Object factionService = services.getClass().getMethod("getFactionService").invoke(services);

            // Создаем MfFactionId
            Class<?> mfFactionIdClass = Class.forName("com.dansplugins.factionsystem.faction.MfFactionId");
            Object factionIdObj = mfFactionIdClass.getConstructor(String.class).newInstance(factionId);

            // Получаем фракцию
            Object faction = factionService.getClass().getMethod("getFaction", mfFactionIdClass).invoke(factionService, factionIdObj);

            if (faction != null) {
                return (String) faction.getClass().getMethod("getName").invoke(faction);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Ошибка получения названия фракции: " + e.getMessage());
        }

        return "Unknown Faction";
    }

    public boolean hasCurrencyManagePermission(Player player) {
        if (!enabled) return false;

        try {
            // Получаем playerService и factionService
            Object playerService = services.getClass().getMethod("getPlayerService").invoke(services);
            Object factionService = services.getClass().getMethod("getFactionService").invoke(services);

            // Находим игрока
            Object players = playerService.getClass().getMethod("getPlayers").invoke(playerService);
            Object mfPlayer = null;
            Object playerId = null;

            if (players instanceof Iterable) {
                for (Object player_obj : (Iterable<?>) players) {
                    Object pid = player_obj.getClass().getMethod("getId").invoke(player_obj);
                    Object playerUUID = pid.getClass().getMethod("getValue").invoke(pid);

                    if (player.getUniqueId().equals(playerUUID)) {
                        mfPlayer = player_obj;
                        playerId = pid;
                        break;
                    }
                }
            }

            if (mfPlayer == null) return false;

            // Получаем фракцию игрока
            Object factionId = mfPlayer.getClass().getMethod("getFactionId").invoke(mfPlayer);
            if (factionId == null) return false;

            Object faction = factionService.getClass().getMethod("getFaction", factionId.getClass()).invoke(factionService, factionId);
            if (faction == null) return false;

            // Проверяем права на управление валютой
            Object permissions = medievalFactionsPlugin.getClass().getMethod("getFactionPermissions").invoke(medievalFactionsPlugin);
            Object currencyManagePermission = permissions.getClass().getMethod("getCurrencyManage").invoke(permissions);

            return (boolean) faction.getClass().getMethod("hasPermission",
                    playerId.getClass(), currencyManagePermission.getClass()).invoke(faction, playerId, currencyManagePermission);

        } catch (Exception e) {
            plugin.getLogger().warning("Ошибка проверки прав: " + e.getMessage());
            e.printStackTrace();
        }

        return false;
    }

    public boolean isPlayerInFaction(Player player) {
        String factionId = getPlayerFactionId(player);
        boolean inFaction = factionId != null;

        plugin.getLogger().info("Игрок " + player.getName() + " во фракции: " + inFaction + " (ID: " + factionId + ")");

        return inFaction;
    }

    // Метод для отладки - показывает всех игроков в системе MF
    public void debugPlayers() {
        if (!enabled) return;

        try {
            Object playerService = services.getClass().getMethod("getPlayerService").invoke(services);
            Object players = playerService.getClass().getMethod("getPlayers").invoke(playerService);

            plugin.getLogger().info("=== ОТЛАДКА MEDIEVAL FACTIONS ===");

            if (players instanceof Iterable) {
                int count = 0;
                for (Object mfPlayer : (Iterable<?>) players) {
                    Object playerId = mfPlayer.getClass().getMethod("getId").invoke(mfPlayer);
                    Object playerUUID = playerId.getClass().getMethod("getValue").invoke(playerId);
                    Object factionId = mfPlayer.getClass().getMethod("getFactionId").invoke(mfPlayer);

                    plugin.getLogger().info("Игрок #" + count + ": UUID=" + playerUUID + ", FactionID=" +
                            (factionId != null ? factionId.getClass().getMethod("getValue").invoke(factionId) : "null"));
                    count++;
                }
                plugin.getLogger().info("Всего игроков в MF: " + count);
            }

        } catch (Exception e) {
            plugin.getLogger().severe("Ошибка отладки: " + e.getMessage());
            e.printStackTrace();
        }
    }
}