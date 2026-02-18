package org.Denis496.chatPlugin.commands;

import org.Denis496.chatPlugin.ChatPlugin;
import org.Denis496.chatPlugin.utils.ChatUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class IgnoreCommand implements CommandExecutor, TabCompleter {

    private final ChatPlugin plugin;

    public IgnoreCommand(ChatPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players!");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("chat.command.ignore")) {
            player.sendMessage(ChatUtils.colorize(plugin.getConfig().getString("messages.no-permission")));
            return true;
        }

        if (args.length == 0) {
            showIgnoreList(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("list")) {
            showIgnoreList(player);
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            player.sendMessage(ChatUtils.colorize("&cPlayer '" + args[0] + "' not found!"));
            return true;
        }

        if (target.equals(player)) {
            player.sendMessage(ChatUtils.colorize("&cYou cannot ignore yourself!"));
            return true;
        }

        if (target.hasPermission("chat.bypass.ignore")) {
            player.sendMessage(ChatUtils.colorize("&cYou cannot ignore this player!"));
            return true;
        }

        if (plugin.getIgnoreManager().isIgnoring(player, target)) {
            plugin.getIgnoreManager().removeIgnore(player, target);
            player.sendMessage(ChatUtils.colorize("&aYou are no longer ignoring &e" + target.getName() + "&a."));
        } else {
            plugin.getIgnoreManager().addIgnore(player, target);
            player.sendMessage(ChatUtils.colorize("&cYou are now ignoring &e" + target.getName() + "&c."));
            player.sendMessage(ChatUtils.colorize("&7You will not see messages from this player."));
        }

        return true;
    }

    private void showIgnoreList(Player player) {
        Set<UUID> ignored = plugin.getIgnoreManager().getIgnoredPlayers(player);

        if (ignored.isEmpty()) {
            player.sendMessage(ChatUtils.colorize("&aYou are not ignoring anyone."));
            return;
        }

        player.sendMessage(ChatUtils.colorize("&6=== Ignored Players ==="));
        for (UUID uuid : ignored) {
            Player ignoredPlayer = Bukkit.getPlayer(uuid);
            String name = ignoredPlayer != null ? ignoredPlayer.getName() :
                    Bukkit.getOfflinePlayer(uuid).getName();
            if (name != null) {
                player.sendMessage(ChatUtils.colorize("&7- &e" + name));
            }
        }
        player.sendMessage(ChatUtils.colorize("&7Use &e/ignore <player> &7to unignore."));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>();
            suggestions.add("list");

            Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                    .forEach(suggestions::add);

            return suggestions;
        }
        return null;
    }
}