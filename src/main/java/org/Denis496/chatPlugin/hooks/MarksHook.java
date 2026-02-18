package org.Denis496.chatPlugin.hooks;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.Denis496.chatPlugin.ChatPlugin;
import org.Denis496.marks.Marks;
import org.Denis496.marks.models.Mark;

import java.sql.SQLException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MarksHook {
    private final ChatPlugin plugin;
    private final Pattern markPattern = Pattern.compile(":x(\\d{4}):", Pattern.CASE_INSENSITIVE);
    private final Pattern quickCheckPattern = Pattern.compile(":x\\d");

    private Marks marksPlugin;
    private boolean isHooked = false;

    // Simple caching
    private final ConcurrentHashMap<String, CachedMark> markCache = new ConcurrentHashMap<>();
    private static final long CACHE_DURATION = 300000L; // 5 minutes cache

    private static class CachedMark {
        final Component component;
        final long expireTime;

        CachedMark(Component component) {
            this.component = component;
            this.expireTime = System.currentTimeMillis() + CACHE_DURATION;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expireTime;
        }
    }

    public MarksHook(ChatPlugin plugin) {
        this.plugin = plugin;
        tryHook();

        if (isHooked) {
            // Cache cleanup task
            Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::cleanupCache, 6000L, 6000L); // Every 5 minutes
        }
    }

    private void tryHook() {
        Plugin marksPluginRaw = Bukkit.getPluginManager().getPlugin("Marks");
        if (marksPluginRaw == null || !marksPluginRaw.isEnabled()) {
            return;
        }

        try {
            // Direct cast to Marks plugin
            if (marksPluginRaw instanceof Marks) {
                marksPlugin = (Marks) marksPluginRaw;
                isHooked = true;
                plugin.getLogger().info("Successfully hooked into Marks plugin!");
            } else {
                plugin.getLogger().warning("Marks plugin found but is not the expected type!");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to hook into Marks plugin: " + e.getMessage());
        }
    }

    private void cleanupCache() {
        markCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    public Component processMarkPlaceholders(Component message) {
        if (!isHooked) {
            return message;
        }

        // Convert to plain text for pattern matching
        String plainText = plainTextSerializer(message);

        // Quick check to avoid regex overhead
        if (!quickCheckPattern.matcher(plainText).find()) {
            return message;
        }

        // Find all marks in the message
        Matcher matcher = markPattern.matcher(plainText);
        if (!matcher.find()) {
            return message;
        }

        // Reset matcher for processing
        matcher.reset();

        // Process all marks
        Component result = message;
        while (matcher.find()) {
            String markId = matcher.group(1);
            String fullMatch = matcher.group(0);

            // Get mark component (from cache or load)
            Component markComponent = getMarkComponent(markId);

            // Replace in the message
            TextReplacementConfig config = TextReplacementConfig.builder()
                    .matchLiteral(fullMatch)
                    .replacement(markComponent)
                    .build();

            result = result.replaceText(config);
        }

        return result;
    }

    private Component getMarkComponent(String markId) {
        // Check cache first
        CachedMark cached = markCache.get(markId);

        if (cached != null && !cached.isExpired()) {
            return cached.component;
        }

        // Load mark synchronously (we're already in async context from chat)
        try {
            Mark mark = marksPlugin.getMarksManager().getMarkByMarkId(markId);
            if (mark != null) {
                Component component = createMarkComponent(mark, markId);
                markCache.put(markId, new CachedMark(component));
                return component;
            } else {
                // Mark not found
                Component notFound = Component.text("[Unknown Mark #" + markId + "]", NamedTextColor.RED);
                markCache.put(markId, new CachedMark(notFound));
                return notFound;
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to load mark " + markId + ": " + e.getMessage());
            return Component.text("[Error loading mark]", NamedTextColor.RED);
        }
    }

    private Component createMarkComponent(Mark mark, String markId) {
        // Get mark properties
        String name = mark.getName();
        String playerName = mark.getPlayerName();
        String worldName = mark.getWorldName();
        String coordinates = mark.getFormattedCoordinates();

        // Get mark type
        String typeName = mark.getMarkType().getDisplayName();

        // Get mark color based on type
        NamedTextColor markColor = switch (mark.getMarkType()) {
            case BASE -> NamedTextColor.GREEN;
            case CITY -> NamedTextColor.GOLD;
            case FARM -> NamedTextColor.YELLOW;
            case END_PORTAL -> NamedTextColor.LIGHT_PURPLE;
        };

        // Convert world name to dimension
        String dimensionName = getDimensionName(worldName);

        // Build hover text
        Component hoverText = Component.text()
                .append(Component.text("Mark Information", NamedTextColor.YELLOW))
                .append(Component.newline())
                .append(Component.newline())
                .append(Component.text("Name: ", NamedTextColor.GRAY))
                .append(Component.text(name, NamedTextColor.WHITE))
                .append(Component.newline())
                .append(Component.text("Type: ", NamedTextColor.GRAY))
                .append(Component.text(typeName, markColor))
                .append(Component.newline())
                .append(Component.text("Author: ", NamedTextColor.GRAY))
                .append(Component.text(playerName, NamedTextColor.WHITE))
                .append(Component.newline())
                .append(Component.text("World: ", NamedTextColor.GRAY))
                .append(Component.text(dimensionName, NamedTextColor.GREEN))
                .append(Component.newline())
                .append(Component.text("Coordinates: ", NamedTextColor.GRAY))
                .append(Component.text(coordinates, NamedTextColor.GOLD))
                .build();

        // Create the mark component with hover
        return Component.text("[" + name + "]", markColor)
                .hoverEvent(HoverEvent.showText(hoverText));
    }

    private String getDimensionName(String worldName) {
        if (worldName == null) return "Unknown";

        String lowerName = worldName.toLowerCase();
        if (lowerName.contains("nether") || lowerName.endsWith("_nether")) {
            return "The Nether";
        } else if (lowerName.contains("end") || lowerName.endsWith("_the_end")) {
            return "The End";
        } else {
            return "Overworld";
        }
    }

    private String plainTextSerializer(Component component) {
        StringBuilder builder = new StringBuilder();
        extractText(component, builder);
        return builder.toString();
    }

    private void extractText(Component component, StringBuilder builder) {
        if (component instanceof net.kyori.adventure.text.TextComponent textComponent) {
            builder.append(textComponent.content());
        }

        for (Component child : component.children()) {
            extractText(child, builder);
        }
    }

    public void reload() {
        isHooked = false;
        markCache.clear();
        marksPlugin = null;
        tryHook();
    }

    public void shutdown() {
        markCache.clear();
    }

    public boolean isHooked() {
        return isHooked;
    }
}