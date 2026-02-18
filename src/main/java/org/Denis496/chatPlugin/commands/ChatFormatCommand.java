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

public class ChatFormatCommand implements CommandExecutor, TabCompleter {

    private final ChatPlugin plugin;

    public ChatFormatCommand(ChatPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("chat.admin.format")) {
            sender.sendMessage(ChatUtils.colorize(plugin.getConfig().getString("messages.no-permission")));
            return true;
        }

        if (args.length == 0) {
            showHelp(sender, label);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "preview":
                handlePreview(sender, args);
                break;
            case "set":
                handleSet(sender, args);
                break;
            case "reset":
                handleReset(sender, args);
                break;
            case "info":
                handleInfo(sender);
                break;
            default:
                showHelp(sender, label);
                break;
        }

        return true;
    }

    private void showHelp(CommandSender sender, String label) {
        sender.sendMessage(ChatUtils.colorize("&6=== Chat Format Management ==="));
        sender.sendMessage(ChatUtils.colorize("&e/" + label + " preview <local|global> <format> &7- Preview a format"));
        sender.sendMessage(ChatUtils.colorize("&e/" + label + " set <local|global> <format> &7- Set a format"));
        sender.sendMessage(ChatUtils.colorize("&e/" + label + " reset <local|global> &7- Reset to default"));
        sender.sendMessage(ChatUtils.colorize("&e/" + label + " info &7- Show current formats"));
        sender.sendMessage("");
        sender.sendMessage(ChatUtils.colorize("&7Available placeholders:"));
        sender.sendMessage(ChatUtils.colorize("&7- &e{PLAYER} &7- Player name (with hover info)"));
        sender.sendMessage(ChatUtils.colorize("&7- &e{PREFIX} &7- LuckPerms prefix"));
        sender.sendMessage(ChatUtils.colorize("&7- &e{MESSAGE} &7- Chat message"));
        sender.sendMessage(ChatUtils.colorize("&7- &e{WORLD} &7- Player's world"));
        sender.sendMessage(ChatUtils.colorize("&7- &e{X}, {Y}, {Z} &7- Coordinates"));
    }

    private void handlePreview(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatUtils.colorize("&cUsage: /chatformat preview <local|global> <format>"));
            return;
        }

        String type = args[1].toLowerCase();
        if (!type.equals("local") && !type.equals("global")) {
            sender.sendMessage(ChatUtils.colorize("&cInvalid type! Use 'local' or 'global'"));
            return;
        }

        // Join the rest of the arguments as the format
        String format = String.join(" ", Arrays.copyOfRange(args, 2, args.length));

        // Preview with dummy data
        Player player = sender instanceof Player ? (Player) sender : null;
        String playerName = player != null ? player.getName() : "Steve";
        String world = player != null ? player.getWorld().getName() : "world";
        String prefix = "&7[Member] &r"; // Example prefix

        String preview = format.replace("{PLAYER}", playerName)
                .replace("{PREFIX}", prefix)
                .replace("{MESSAGE}", "This is a test message!")
                .replace("{WORLD}", world)
                .replace("{X}", "100")
                .replace("{Y}", "64")
                .replace("{Z}", "-200");

        sender.sendMessage(ChatUtils.colorize("&6Preview of " + type + " format:"));
        sender.sendMessage(ChatUtils.colorize(preview));
    }

    private void handleSet(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatUtils.colorize("&cUsage: /chatformat set <local|global> <format>"));
            return;
        }

        String type = args[1].toLowerCase();
        if (!type.equals("local") && !type.equals("global")) {
            sender.sendMessage(ChatUtils.colorize("&cInvalid type! Use 'local' or 'global'"));
            return;
        }

        // Join the rest of the arguments as the format
        String format = String.join(" ", Arrays.copyOfRange(args, 2, args.length));

        // Validate format contains required placeholders
        if (!format.contains("{PLAYER}")) {
            sender.sendMessage(ChatUtils.colorize("&cFormat must contain {PLAYER} placeholder!"));
            return;
        }
        if (!format.contains("{MESSAGE}")) {
            sender.sendMessage(ChatUtils.colorize("&cFormat must contain {MESSAGE} placeholder!"));
            return;
        }

        // Set the format in config
        String configPath = "chat." + type + ".format";
        plugin.getConfig().set(configPath, format);
        plugin.saveConfig();

        sender.sendMessage(ChatUtils.colorize("&aSuccessfully set " + type + " chat format!"));
        sender.sendMessage(ChatUtils.colorize("&7Format: &f" + format));
        sender.sendMessage(ChatUtils.colorize("&7Use &e/chatplugin reload &7to apply changes."));
    }

    private void handleReset(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatUtils.colorize("&cUsage: /chatformat reset <local|global>"));
            return;
        }

        String type = args[1].toLowerCase();
        if (!type.equals("local") && !type.equals("global")) {
            sender.sendMessage(ChatUtils.colorize("&cInvalid type! Use 'local' or 'global'"));
            return;
        }

        // Default formats
        String defaultFormat;
        if (type.equals("local")) {
            defaultFormat = "&eL &8| {PREFIX} &f{PLAYER} &8› &f{MESSAGE}";
        } else {
            defaultFormat = "&cG &8| {PREFIX} &f{PLAYER} &8› &f{MESSAGE}";
        }

        plugin.getConfig().set("chat." + type + ".format", defaultFormat);
        plugin.saveConfig();

        sender.sendMessage(ChatUtils.colorize("&aReset " + type + " chat format to default!"));
        sender.sendMessage(ChatUtils.colorize("&7Use &e/chatplugin reload &7to apply changes."));
    }

    private void handleInfo(CommandSender sender) {
        String localFormat = plugin.getConfig().getString("chat.local.format", "&eL &8| {PREFIX} &f{PLAYER} &8› &f{MESSAGE}");
        String globalFormat = plugin.getConfig().getString("chat.global.format", "&cG &8| {PREFIX} &f{PLAYER} &8› &f{MESSAGE}");

        sender.sendMessage(ChatUtils.colorize("&6=== Current Chat Formats ==="));
        sender.sendMessage("");
        sender.sendMessage(ChatUtils.colorize("&eLocal Chat:"));
        sender.sendMessage(ChatUtils.colorize("&7Format: &f" + localFormat));
        sender.sendMessage("");
        sender.sendMessage(ChatUtils.colorize("&eGlobal Chat:"));
        sender.sendMessage(ChatUtils.colorize("&7Format: &f" + globalFormat));
        sender.sendMessage("");
        sender.sendMessage(ChatUtils.colorize("&7Note: Player names always have hover info with click to message"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("preview", "set", "reset", "info").stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            if (subCommand.equals("preview") || subCommand.equals("set") || subCommand.equals("reset")) {
                return Arrays.asList("local", "global").stream()
                        .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        if (args.length == 3) {
            String subCommand = args[0].toLowerCase();
            if (subCommand.equals("preview") || subCommand.equals("set")) {
                // Suggest example formats
                return Arrays.asList(
                        "&7[L] {PREFIX}{PLAYER}&7: &f{MESSAGE}",
                        "&e[{WORLD}] {PREFIX}{PLAYER}: {MESSAGE}",
                        "{PREFIX}&7{PLAYER} &8» &f{MESSAGE}"
                );
            }
        }

        return null;
    }
}