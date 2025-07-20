package xyz.moorus.economy.main;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

public class CommandManager implements CommandExecutor {

    private final Map<String, xyz.moorus.economy.command.Command> commands = new HashMap<>();

    public void registerCommand(xyz.moorus.economy.command.Command command) {
        commands.put(command.getName().toLowerCase(), command);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cЭта команда доступна только игрокам!");
            return true;
        }

        Player player = (Player) sender;
        String commandName = command.getName().toLowerCase();

        xyz.moorus.economy.command.Command economyCommand = commands.get(commandName);
        if (economyCommand != null) {
            try {
                economyCommand.execute(player.getName(), args);
            } catch (Exception e) {
                player.sendMessage("§cОшибка при выполнении команды: " + e.getMessage());
                Economy.getInstance().getLogger().severe("Ошибка в команде " + commandName + ": " + e.getMessage());
                e.printStackTrace();
            }
            return true;
        }

        return false;
    }
}