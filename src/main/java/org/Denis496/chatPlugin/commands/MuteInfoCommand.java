package org.Denis496.chatPlugin.commands;

import org.Denis496.chatPlugin.ChatPlugin;
import org.Denis496.chatPlugin.utils.ChatUtils;
import org.Denis496.warns.API.WarnsAPI;
import org.Denis496.warns.models.Mute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class MuteInfoCommand implements CommandExecutor {
    private final ChatPlugin plugin;

    public MuteInfoCommand(ChatPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players!");
            return true;
        }

        Player player = (Player) sender;
        WarnsAPI warnsAPI = plugin.getWarnsAPI();

        if (warnsAPI == null) {
            player.sendMessage(ChatUtils.colorize("&cMute system is not available!"));
            return true;
        }

        if (warnsAPI.isPlayerMuted(player)) {
            Mute mute = warnsAPI.getActiveMute(player);
            if (mute != null) {
                player.sendMessage(ChatUtils.colorize("&c&lYou are currently muted!"));
                player.sendMessage(ChatUtils.colorize("&7Time remaining: &e" + mute.getRemainingTimeFormatted()));
                player.sendMessage(ChatUtils.colorize("&7Reason: &f" + mute.getReason()));
                player.sendMessage(ChatUtils.colorize("&7Muted by: &f" + mute.getIssuerName()));
            }
        } else {
            player.sendMessage(ChatUtils.colorize("&aYou are not muted!"));
        }

        return true;
    }
}