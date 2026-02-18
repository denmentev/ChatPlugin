package org.Denis496.chatPlugin.commands;

import org.Denis496.chatPlugin.ChatPlugin;
import org.Denis496.chatPlugin.utils.ChatUtils;
import org.Denis496.chatPlugin.managers.MessageManager;
import org.Denis496.warns.API.WarnsAPI;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ReplyCommand implements CommandExecutor {

    private final ChatPlugin plugin;

    public ReplyCommand(ChatPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players!");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("chat.command.reply")) {
            player.sendMessage(ChatUtils.colorize(plugin.getConfig().getString("messages.no-permission")));
            return true;
        }

        if (!plugin.getDMManager().isDMEnabled(player)) {
            player.sendMessage(ChatUtils.colorize("&cYou have direct messages disabled! Use &e/dm on &cto enable them."));
            return true;
        }

        WarnsAPI warnsAPI = plugin.getWarnsAPI();
        if (warnsAPI != null && warnsAPI.isPlayerMuted(player)) {
            org.Denis496.warns.models.Mute mute = warnsAPI.getActiveMute(player);
            if (mute != null) {
                player.sendMessage(ChatUtils.colorize("&cYou cannot send messages while muted!"));
                player.sendMessage(ChatUtils.colorize("&7Time remaining: &e" + mute.getRemainingTimeFormatted()));
                player.sendMessage(ChatUtils.colorize("&7Reason: &f" + mute.getReason()));
            } else {
                player.sendMessage(ChatUtils.colorize("&cYou are muted and cannot send messages!"));
            }
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(ChatUtils.colorize("&cUsage: /" + label + " <message>"));
            return true;
        }

        Player target = plugin.getMessageManager().getLastMessenger(player);
        if (target == null) {
            player.sendMessage(ChatUtils.colorize(plugin.getConfig().getString("messages.no-reply")));
            return true;
        }

        if (!plugin.getDMManager().isDMEnabled(target)) {
            player.sendMessage(ChatUtils.colorize("&cSorry, this player is not allowing other people to send them DMs."));
            return true;
        }

        if (plugin.getIgnoreManager().isIgnoring(target, player)) {
            player.sendMessage(ChatUtils.colorize("&cYou cannot reply to this player."));
            return true;
        }

        String message = String.join(" ", args);
        plugin.getMessageManager().sendPrivateMessage(player, target, message);
        return true;
    }
}