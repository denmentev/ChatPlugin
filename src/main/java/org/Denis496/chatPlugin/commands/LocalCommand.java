package org.Denis496.chatPlugin.commands;

import org.Denis496.chatPlugin.ChatPlugin;
import org.Denis496.chatPlugin.managers.ChatModeManager;
import org.Denis496.chatPlugin.utils.ChatUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class LocalCommand implements CommandExecutor {

    private final ChatPlugin plugin;

    public LocalCommand(ChatPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players!");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("chat.command.local")) {
            player.sendMessage(ChatUtils.colorize(plugin.getConfig().getString("messages.no-permission")));
            return true;
        }

        plugin.getChatModeManager().setPlayerMode(player, ChatModeManager.ChatMode.LOCAL);
        player.sendMessage(ChatUtils.colorize("&aYou have switched to &eLocal &achat mode!"));
        player.sendMessage(ChatUtils.colorize("&7Your messages will now be sent to local chat by default."));
        player.sendMessage(ChatUtils.colorize("&7Use &e! &7prefix to send a message to global chat."));

        return true;
    }
}