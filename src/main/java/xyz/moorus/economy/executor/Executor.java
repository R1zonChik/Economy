package xyz.moorus.economy.executor;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import xyz.moorus.economy.command.*;
import xyz.moorus.economy.money.WalletManager;

import java.util.HashMap;
import java.util.Map;

public class Executor implements CommandExecutor {

    private final Map<String, xyz.moorus.economy.command.Command> commands;

    public Executor(WalletManager walletManager) {
        commands = new HashMap<>();

        // Регистрируем все команды
        registerCommand(new PrintWalletCommand());
        registerCommand(new PayCommand());
        registerCommand(new CreateCommand());
        registerCommand(new BourseCommand());
        registerCommand(new EmitCommand());
        registerCommand(new AuctionCommand());
        registerCommand(new SellHandCommand());
        registerCommand(new AdminCommand());
    }

    private void registerCommand(xyz.moorus.economy.command.Command command) {
        commands.put(command.getName(), command);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cТолько игроки могут использовать эту команду!");
            return true;
        }

        Player player = (Player) sender;
        xyz.moorus.economy.command.Command economyCommand = commands.get(command.getName());

        if (economyCommand != null) {
            try {
                economyCommand.execute(player.getName(), args);
            } catch (Exception e) {
                player.sendMessage("§cОшибка при выполнении команды: " + e.getMessage());
                e.printStackTrace();
            }
            return true;
        }

        return false;
    }
}