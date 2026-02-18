package org.Denis496.chatPlugin.commands;

import org.Denis496.chatPlugin.ChatPlugin;
import org.Denis496.chatPlugin.utils.ChatUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ChatPluginCommand implements CommandExecutor, TabCompleter {

    private final ChatPlugin plugin;

    public ChatPluginCommand(ChatPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("chat.admin")) {
            sender.sendMessage(ChatUtils.colorize(plugin.getConfig().getString("messages.no-permission")));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(ChatUtils.colorize("&6ChatPlugin &7v1.0.0 by YourName"));
            sender.sendMessage(ChatUtils.colorize("&7Usage: /" + label + " reload"));
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            plugin.reloadPlugin();
            sender.sendMessage(ChatUtils.colorize("&aConfiguration reloaded successfully!"));
            return true;
        }

        sender.sendMessage(ChatUtils.colorize("&cUnknown subcommand. Use /" + label + " for help."));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("reload").stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return null;
    }
}