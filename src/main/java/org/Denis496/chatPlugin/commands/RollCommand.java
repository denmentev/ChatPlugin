package org.Denis496.chatPlugin.commands;

import org.Denis496.chatPlugin.ChatPlugin;
import org.Denis496.chatPlugin.utils.ChatUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Random;

public class RollCommand implements CommandExecutor {

    private final ChatPlugin plugin;
    private final Random random;

    public RollCommand(ChatPlugin plugin) {
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

        if (!player.hasPermission("chat.command.roll")) {
            player.sendMessage(ChatUtils.colorize(plugin.getConfig().getString("messages.no-permission")));
            return true;
        }

        int max = 6; // Default dice

        if (args.length > 0) {
            try {
                max = Integer.parseInt(args[0]);
                if (max < 1 || max > 1000) {
                    player.sendMessage(ChatUtils.colorize(plugin.getConfig().getString("commands.roll.invalid-number")));
                    return true;
                }
            } catch (NumberFormatException e) {
                player.sendMessage(ChatUtils.colorize(plugin.getConfig().getString("commands.roll.invalid-number")));
                return true;
            }
        }

        int result = random.nextInt(max) + 1;

        String format = plugin.getConfig().getString("commands.roll.format", "&6{PLAYER} &erolled a dice and got &6{RESULT}");
        String message = format
                .replace("{PLAYER}", player.getName())
                .replace("{RESULT}", String.valueOf(result));

        Bukkit.broadcastMessage(ChatUtils.colorize(message));
        return true;
    }
}