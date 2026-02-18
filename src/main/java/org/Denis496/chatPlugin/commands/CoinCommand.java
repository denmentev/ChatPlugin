package org.Denis496.chatPlugin.commands;

import org.Denis496.chatPlugin.ChatPlugin;
import org.Denis496.chatPlugin.utils.ChatUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Random;

public class CoinCommand implements CommandExecutor {

    private final ChatPlugin plugin;
    private final Random random;

    public CoinCommand(ChatPlugin plugin) {
        this.plugin = plugin;
        this.random = new Random();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players!");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("chat.command.coin")) {
            player.sendMessage(ChatUtils.colorize(plugin.getConfig().getString("messages.no-permission")));
            return true;
        }

        String result = random.nextBoolean() ? "Heads" : "Tails";

        String format = plugin.getConfig().getString("commands.coin.format", "&eCoin toss result: &6{RESULT}");
        String message = format.replace("{RESULT}", result);

        Bukkit.broadcastMessage(ChatUtils.colorize(message));
        return true;
    }
}