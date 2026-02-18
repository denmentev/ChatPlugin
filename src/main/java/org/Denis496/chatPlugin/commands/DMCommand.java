package org.Denis496.chatPlugin.commands;

import org.Denis496.chatPlugin.ChatPlugin;
import org.Denis496.chatPlugin.utils.ChatUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class DMCommand implements CommandExecutor, TabCompleter {

    private final ChatPlugin plugin;

    public DMCommand(ChatPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players!");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("chat.command.dm")) {
            player.sendMessage(ChatUtils.colorize(plugin.getConfig().getString("messages.no-permission")));
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(ChatUtils.colorize("&cUsage: /" + label + " <on|off>"));
            return true;
        }

        String subCommand = args[0].toLowerCase();

        if (subCommand.equals("on")) {
            plugin.getDMManager().setDMEnabled(player, true);
            player.sendMessage(ChatUtils.colorize("&aDirect messages have been &2enabled&a!"));
            player.sendMessage(ChatUtils.colorize("&7Other players can now send you private messages."));
        } else if (subCommand.equals("off")) {
            plugin.getDMManager().setDMEnabled(player, false);
            player.sendMessage(ChatUtils.colorize("&cDirect messages have been &4disabled&c!"));
            player.sendMessage(ChatUtils.colorize("&7Other players cannot send you private messages."));
            player.sendMessage(ChatUtils.colorize("&7Note: You also cannot send messages to others while DMs are off."));
        } else {
            player.sendMessage(ChatUtils.colorize("&cUsage: /" + label + " <on|off>"));
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("on", "off").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return null;
    }
}