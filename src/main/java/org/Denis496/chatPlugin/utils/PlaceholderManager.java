package org.Denis496.chatPlugin.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.Denis496.chatPlugin.ChatPlugin;
import org.plugin.teams.api.TeamsAPI;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PlaceholderManager {

    private final ChatPlugin plugin;
    private final Map<String, Function<Player, String>> placeholders;
    private final Map<String, Function<Player, Component>> componentPlaceholders;
    private final Pattern placeholderPattern;

    // Simple caching for expensive operations
    private final ConcurrentHashMap<String, CachedValue<String>> cache = new ConcurrentHashMap<>();
    private static final long CACHE_DURATION = 60000L; // 1 minute for other placeholders
    private static final long TEAM_CACHE_DURATION = 1000L; // Only 1 second for team placeholders!

    private static class CachedValue<T> {
        final T value;
        final long expireTime;

        CachedValue(T value, long duration) {
            this.value = value;
            this.expireTime = System.currentTimeMillis() + duration;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expireTime;
        }
    }

    public PlaceholderManager(ChatPlugin plugin) {
        this.plugin = plugin;
        this.placeholders = new ConcurrentHashMap<>();
        this.componentPlaceholders = new ConcurrentHashMap<>();
        this.placeholderPattern = Pattern.compile(":(\\w+):", Pattern.CASE_INSENSITIVE);

        registerDefaultPlaceholders();

        // Cleanup task
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::cleanupCache, 600L, 600L); // Every 30 seconds
    }

    private void cleanupCache() {
        cache.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    private void registerDefaultPlaceholders() {
        // Team placeholder - modified to use very short cache
        registerComponentPlaceholder("team", player -> {
            String cacheKey = player.getUniqueId() + ":team"; // Use UUID instead of name
            CachedValue<String> cached = cache.get(cacheKey);

            String teamName = null;

            // Check cache but with very short duration
            if (cached != null && !cached.isExpired()) {
                teamName = cached.value;
            } else {
                // Always fetch fresh data
                try {
                    String name = TeamsAPI.getTeamName(player);
                    teamName = name != null ? name : null;
                } catch (Exception e) {
                    teamName = null;
                }

                // Cache with short duration - don't cache "No Team" for long
                if (teamName != null) {
                    cache.put(cacheKey, new CachedValue<>(teamName, TEAM_CACHE_DURATION));
                } else {
                    // Don't cache null/no team state, or use very short cache
                    cache.remove(cacheKey);
                }
            }

            // If no team, just return [No Team] without hover
            if (teamName == null) {
                return Component.text("[No Team]", NamedTextColor.GRAY);
            }

            // Get team details if player has a team
            int memberCount = 1;
            String owner = "Unknown";

            try {
                // Get member count
                memberCount = TeamsAPI.getTeamMemberCount(teamName);

                // Get team owner
                String teamOwner = TeamsAPI.getTeamOwner(teamName);
                if (teamOwner != null && !teamOwner.isEmpty()) {
                    owner = teamOwner;
                }
            } catch (Exception ignored) {}

            // Create component with hover only for actual teams
            TextComponent.Builder hoverBuilder = Component.text();
            hoverBuilder.append(Component.text("Team Information", NamedTextColor.YELLOW));
            hoverBuilder.append(Component.newline());
            hoverBuilder.append(Component.newline());
            hoverBuilder.append(Component.text("Team: ", NamedTextColor.GRAY));
            hoverBuilder.append(Component.text(teamName, NamedTextColor.GREEN));
            hoverBuilder.append(Component.newline());
            hoverBuilder.append(Component.text("Owner: ", NamedTextColor.GRAY));
            hoverBuilder.append(Component.text(owner, NamedTextColor.WHITE));
            hoverBuilder.append(Component.newline());
            hoverBuilder.append(Component.text("Members: ", NamedTextColor.GRAY));
            hoverBuilder.append(Component.text(memberCount, NamedTextColor.WHITE));

            return Component.text("[" + teamName + "]", NamedTextColor.GREEN)
                    .hoverEvent(HoverEvent.showText(hoverBuilder.build()));
        });

        // Location placeholder with hover
        registerComponentPlaceholder("loc", player -> {
            Location loc = player.getLocation();
            String coords = String.format("[%d, %d, %d]", loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());

            // Create hover with more info
            TextComponent.Builder hoverBuilder = Component.text();
            hoverBuilder.append(Component.text("Location Details", NamedTextColor.YELLOW));
            hoverBuilder.append(Component.newline());
            hoverBuilder.append(Component.text("World: ", NamedTextColor.GRAY));
            hoverBuilder.append(Component.text(formatWorldName(loc.getWorld().getName()), NamedTextColor.GREEN));
            hoverBuilder.append(Component.newline());
            hoverBuilder.append(Component.newline());
            hoverBuilder.append(Component.text("Click to copy coordinates", NamedTextColor.GRAY, TextDecoration.ITALIC));

            return Component.text(coords, NamedTextColor.YELLOW)
                    .hoverEvent(HoverEvent.showText(hoverBuilder.build()))
                    .clickEvent(ClickEvent.copyToClipboard(coords));
        });

        // Item placeholder with hover only for enchanted items
        registerComponentPlaceholder("item", player -> {
            ItemStack item = player.getInventory().getItemInMainHand();

            if (item.getType() == Material.AIR) {
                return Component.text("Empty Hand", NamedTextColor.GRAY, TextDecoration.ITALIC);
            }

            String itemName = item.hasItemMeta() && item.getItemMeta().hasDisplayName()
                    ? item.getItemMeta().getDisplayName()
                    : formatItemName(item.getType().toString());

            // Check if it's a tool or weapon (items that can't stack)
            boolean isToolOrWeapon = item.getType().getMaxStackSize() == 1 ||
                    item.getType().toString().contains("SWORD") ||
                    item.getType().toString().contains("AXE") ||
                    item.getType().toString().contains("PICKAXE") ||
                    item.getType().toString().contains("SHOVEL") ||
                    item.getType().toString().contains("HOE") ||
                    item.getType().toString().contains("BOW") ||
                    item.getType().toString().contains("HELMET") ||
                    item.getType().toString().contains("CHESTPLATE") ||
                    item.getType().toString().contains("LEGGINGS") ||
                    item.getType().toString().contains("BOOTS") ||
                    item.getType().toString().contains("SHIELD") ||
                    item.getType().toString().contains("TRIDENT") ||
                    item.getType().toString().contains("FISHING_ROD");

            // Format name with amount if applicable
            String displayName = itemName;
            if (!isToolOrWeapon && item.getAmount() > 1) {
                displayName = itemName + " x" + item.getAmount();
            }

            // Check for enchantments
            boolean hasEnchantments = item.hasItemMeta() &&
                    item.getItemMeta().hasEnchants() &&
                    !item.getItemMeta().getEnchants().isEmpty();

            if (hasEnchantments) {
                // Create hover for enchanted items
                TextComponent.Builder hoverBuilder = Component.text();
                hoverBuilder.append(Component.text("Enchantments:", NamedTextColor.GRAY));
                hoverBuilder.append(Component.newline());

                item.getItemMeta().getEnchants().forEach((enchant, level) -> {
                    hoverBuilder.append(Component.newline());
                    hoverBuilder.append(Component.text("◇ ", NamedTextColor.DARK_PURPLE));

                    // Format enchantment name
                    String enchantName = enchant.getKey().getKey()
                            .replace('_', ' ')
                            .replace("minecraft:", "");
                    String[] words = enchantName.split(" ");
                    StringBuilder formattedName = new StringBuilder();
                    for (String word : words) {
                        formattedName.append(Character.toUpperCase(word.charAt(0)))
                                .append(word.substring(1).toLowerCase())
                                .append(" ");
                    }
                    String finalName = formattedName.toString().trim();

                    hoverBuilder.append(Component.text(finalName + " " + level, NamedTextColor.LIGHT_PURPLE));
                });

                return Component.text("[" + displayName + "]", NamedTextColor.LIGHT_PURPLE)
                        .hoverEvent(HoverEvent.showText(hoverBuilder.build()));
            } else {
                // No enchantments, no hover
                return Component.text("[" + displayName + "]", NamedTextColor.AQUA);
            }
        });

        // Register simple text versions for compatibility
        registerPlaceholder("team", player -> {
            String cacheKey = player.getUniqueId() + ":team";
            CachedValue<String> cached = cache.get(cacheKey);

            if (cached != null && !cached.isExpired()) {
                return "&a[" + cached.value + "]&r";
            }

            String teamName = null;
            try {
                String name = TeamsAPI.getTeamName(player);
                teamName = name;
            } catch (Exception ignored) {}

            if (teamName != null) {
                cache.put(cacheKey, new CachedValue<>(teamName, TEAM_CACHE_DURATION));
                return "&a[" + teamName + "]&r";
            } else {
                cache.remove(cacheKey); // Remove cache for no team
                return "&7[No Team]&r";
            }
        });

        registerPlaceholder("item", player -> {
            ItemStack item = player.getInventory().getItemInMainHand();
            if (item.getType() == Material.AIR) {
                return "&7Empty Hand&r";
            }

            String name = item.hasItemMeta() && item.getItemMeta().hasDisplayName()
                    ? item.getItemMeta().getDisplayName()
                    : formatItemName(item.getType().toString());

            // Check if it's a tool or weapon
            boolean isToolOrWeapon = item.getType().getMaxStackSize() == 1 ||
                    item.getType().toString().contains("SWORD") ||
                    item.getType().toString().contains("AXE") ||
                    item.getType().toString().contains("PICKAXE") ||
                    item.getType().toString().contains("SHOVEL") ||
                    item.getType().toString().contains("HOE") ||
                    item.getType().toString().contains("BOW") ||
                    item.getType().toString().contains("HELMET") ||
                    item.getType().toString().contains("CHESTPLATE") ||
                    item.getType().toString().contains("LEGGINGS") ||
                    item.getType().toString().contains("BOOTS") ||
                    item.getType().toString().contains("SHIELD") ||
                    item.getType().toString().contains("TRIDENT") ||
                    item.getType().toString().contains("FISHING_ROD");

            String displayName = name;
            if (!isToolOrWeapon && item.getAmount() > 1) {
                displayName = name + " x" + item.getAmount();
            }

            // Check for enchantments
            boolean hasEnchantments = item.hasItemMeta() &&
                    item.getItemMeta().hasEnchants() &&
                    !item.getItemMeta().getEnchants().isEmpty();

            if (hasEnchantments) {
                return "&d" + displayName + "&r"; // Purple for enchanted
            } else {
                return "&b" + displayName + "&r"; // Aqua for normal
            }
        });

        registerPlaceholder("loc", player -> {
            Location loc = player.getLocation();
            return "&e[" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + "]&r";
        });

        // Note: Marks placeholders (:x1234:) are handled by MarksHook, not here
    }

    private String formatItemName(String itemType) {
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;

        for (char c : itemType.toCharArray()) {
            if (c == '_') {
                result.append(' ');
                capitalizeNext = true;
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                result.append(Character.toLowerCase(c));
            }
        }

        return result.toString();
    }

    private String formatWorldName(String worldName) {
        if (worldName.endsWith("_nether")) {
            return "The Nether";
        } else if (worldName.endsWith("_the_end")) {
            return "The End";
        } else {
            return "Overworld";
        }
    }

    public String processPlaceholders(Player player, String message) {
        if (player == null || message == null || message.length() < 3 || !message.contains(":")) {
            return message;
        }

        // Process component placeholders first
        Component component = processPlaceholdersAsComponent(player, message);

        // If it contains component placeholders, we need to return the plain text version
        // This is for compatibility with systems expecting strings
        if (hasComponentPlaceholder(message)) {
            return plainTextSerializer(component);
        }

        // Otherwise process as normal string placeholders
        return processStringPlaceholders(player, message);
    }

    private String processStringPlaceholders(Player player, String message) {
        Matcher matcher = placeholderPattern.matcher(message);
        StringBuilder result = new StringBuilder(message.length());
        int lastEnd = 0;

        while (matcher.find()) {
            String placeholder = matcher.group(1);

            // Skip component placeholders
            if (componentPlaceholders.containsKey(placeholder)) {
                continue;
            }

            Function<Player, String> replacer = placeholders.get(placeholder);
            if (replacer != null) {
                result.append(message, lastEnd, matcher.start());
                try {
                    String replacement = replacer.apply(player);
                    result.append(ChatUtils.colorize(replacement));
                } catch (Exception e) {
                    result.append(matcher.group());
                }
                lastEnd = matcher.end();
            }
        }

        if (lastEnd == 0) {
            return message;
        }

        result.append(message, lastEnd, message.length());
        return result.toString();
    }

    public Component processPlaceholdersAsComponent(Player player, String message) {
        if (player == null || message == null) {
            return Component.text(message != null ? message : "");
        }

        // First process string placeholders (non-component ones)
        String processedMessage = processStringPlaceholders(player, message);

        // Now handle component placeholders
        Matcher matcher = placeholderPattern.matcher(processedMessage);

        // If no placeholders found, just return the colored message
        if (!matcher.find()) {
            return ChatUtils.colorizeComponent(processedMessage);
        }

        // Reset matcher
        matcher.reset();

        TextComponent.Builder builder = Component.text();
        int lastEnd = 0;
        boolean foundAny = false;

        while (matcher.find()) {
            String placeholder = matcher.group(1);
            Function<Player, Component> componentReplacer = componentPlaceholders.get(placeholder);

            if (componentReplacer != null) {
                foundAny = true;

                // Add text before placeholder
                if (matcher.start() > lastEnd) {
                    builder.append(ChatUtils.colorizeComponent(processedMessage.substring(lastEnd, matcher.start())));
                }

                // Add the component placeholder
                try {
                    builder.append(componentReplacer.apply(player));
                } catch (Exception e) {
                    plugin.getLogger().warning("Error processing component placeholder " + placeholder + ": " + e.getMessage());
                    builder.append(Component.text(matcher.group()));
                }

                lastEnd = matcher.end();
            }
        }

        // If we found component placeholders, complete the builder
        if (foundAny) {
            if (lastEnd < processedMessage.length()) {
                builder.append(ChatUtils.colorizeComponent(processedMessage.substring(lastEnd)));
            }
            return builder.build();
        } else {
            // No component placeholders found, just return colored message
            return ChatUtils.colorizeComponent(processedMessage);
        }
    }

    public boolean hasComponentPlaceholder(String message) {
        if (message == null || message.length() < 3 || !message.contains(":")) {
            return false;
        }

        Matcher matcher = placeholderPattern.matcher(message);
        while (matcher.find()) {
            if (componentPlaceholders.containsKey(matcher.group(1))) {
                return true;
            }
        }
        return false;
    }

    private String plainTextSerializer(Component component) {
        StringBuilder builder = new StringBuilder();
        extractText(component, builder);
        return builder.toString();
    }

    private void extractText(Component component, StringBuilder builder) {
        if (component instanceof TextComponent textComponent) {
            builder.append(textComponent.content());
        }
        for (Component child : component.children()) {
            extractText(child, builder);
        }
    }

    public void registerPlaceholder(String key, Function<Player, String> replacer) {
        if (key != null && replacer != null) {
            placeholders.put(key, replacer);
        }
    }

    public void registerComponentPlaceholder(String key, Function<Player, Component> replacer) {
        if (key != null && replacer != null) {
            componentPlaceholders.put(key, replacer);
        }
    }

    public void reloadPlaceholders() {
        cache.clear();
    }

    public void clearPlayerCache(Player player) {
        // Remove all cache entries for this player
        cache.entrySet().removeIf(entry -> entry.getKey().contains(player.getUniqueId().toString()));
    }
}