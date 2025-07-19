package xyz.moorus.economy.message;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;

public class Formatter {
    public static String formatWarning(String toFormat) {
        return ChatColor.WHITE + "[" + ChatColor.DARK_RED + ChatColor.BOLD + "!" + ChatColor.WHITE + "] " + toFormat;
    }
    public static String formatMessage(String toFormat) {
        return ChatColor.WHITE + "[" + ChatColor.DARK_GREEN + ChatColor.BOLD + "i" + ChatColor.WHITE + "] " + toFormat;
    }
}
