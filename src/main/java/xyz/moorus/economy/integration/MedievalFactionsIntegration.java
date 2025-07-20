package xyz.moorus.economy.integration;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import xyz.moorus.economy.main.Economy;

public class MedievalFactionsIntegration {

    private final Economy plugin;
    private boolean enabled = false;

    public MedievalFactionsIntegration(Economy plugin) {
        this.plugin = plugin;
        this.enabled = Bukkit.getPluginManager().getPlugin("MedievalFactions") != null;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getPlayerFactionId(Player player) {
        if (!enabled) return null;

        try {
            Object medievalFactions = Bukkit.getPluginManager().getPlugin("MedievalFactions");
            Object services = medievalFactions.getClass().getMethod("getServices").invoke(medievalFactions);
            Object playerService = services.getClass().getMethod("getPlayerService").invoke(services);

            Class<?> mfPlayerIdClass = Class.forName("com.dansplugins.factionsystem.player.MfPlayerId");
            Object playerId = mfPlayerIdClass.getMethod("fromBukkitPlayer", Player.class).invoke(null, player);

            Object mfPlayer = playerService.getClass().getMethod("getPlayer", mfPlayerIdClass).invoke(playerService, playerId);

            if (mfPlayer != null) {
                Object factionId = mfPlayer.getClass().getMethod("getFactionId").invoke(mfPlayer);
                if (factionId != null) {
                    return factionId.getClass().getMethod("getValue").invoke(factionId).toString();
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
            Object medievalFactions = Bukkit.getPluginManager().getPlugin("MedievalFactions");
            Object services = medievalFactions.getClass().getMethod("getServices").invoke(medievalFactions);
            Object factionService = services.getClass().getMethod("getFactionService").invoke(services);

            Class<?> mfFactionIdClass = Class.forName("com.dansplugins.factionsystem.faction.MfFactionId");
            Object factionIdObj = mfFactionIdClass.getConstructor(String.class).newInstance(factionId);

            Object faction = factionService.getClass().getMethod("getFaction", mfFactionIdClass).invoke(factionService, factionIdObj);

            if (faction != null) {
                return (String) faction.getClass().getMethod("getName").invoke(faction);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Ошибка получения названия фракции: " + e.getMessage());
        }

        return null;
    }

    public boolean hasPermission(Player player, String permission) {
        if (!enabled) return false;

        try {
            Object medievalFactions = Bukkit.getPluginManager().getPlugin("MedievalFactions");
            Object services = medievalFactions.getClass().getMethod("getServices").invoke(medievalFactions);
            Object playerService = services.getClass().getMethod("getPlayerService").invoke(services);
            Object factionService = services.getClass().getMethod("getFactionService").invoke(services);

            Class<?> mfPlayerIdClass = Class.forName("com.dansplugins.factionsystem.player.MfPlayerId");
            Object playerId = mfPlayerIdClass.getMethod("fromBukkitPlayer", Player.class).invoke(null, player);

            Object mfPlayer = playerService.getClass().getMethod("getPlayer", mfPlayerIdClass).invoke(playerService, playerId);

            if (mfPlayer != null) {
                Object factionId = mfPlayer.getClass().getMethod("getFactionId").invoke(mfPlayer);
                if (factionId != null) {
                    Object faction = factionService.getClass().getMethod("getFaction", factionId.getClass()).invoke(factionService, factionId);
                    if (faction != null) {
                        Object permissions = medievalFactions.getClass().getMethod("getFactionPermissions").invoke(medievalFactions);
                        Object currencyManagePermission = permissions.getClass().getMethod("getCurrencyManage").invoke(permissions);

                        return (boolean) faction.getClass().getMethod("hasPermission",
                                playerId.getClass(), currencyManagePermission.getClass()).invoke(faction, playerId, currencyManagePermission);
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Ошибка проверки прав: " + e.getMessage());
        }

        return false;
    }
}