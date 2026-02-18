package org.Denis496.chatPlugin.utils;

import org.bukkit.ChatColor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public class ChatUtils {

    private static final LegacyComponentSerializer SERIALIZER = LegacyComponentSerializer.legacyAmpersand();

    public static String colorize(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    public static Component colorizeComponent(String message) {
        return SERIALIZER.deserialize(message);
    }

    public static String stripColor(String message) {
        return ChatColor.stripColor(message);
    }

    public static boolean containsColor(String message) {
        return message.contains("&") || message.contains("§");
    }
}