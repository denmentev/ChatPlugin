package org.Denis496.chatPlugin.managers;

import org.Denis496.chatPlugin.ChatPlugin;
import org.Denis496.chatPlugin.utils.ChatUtils;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

public class MentionManager {

    private final ChatPlugin plugin;
    private final boolean enabled;
    private final String prefix;
    private final String highlightColor;
    private final Sound mentionSound;
    private final Pattern mentionPattern;
    private final Pattern quickCheckPattern;

    // Anti-spam protection
    private final ConcurrentHashMap<String, Long> recentMentions = new ConcurrentHashMap<>();
    private static final long MENTION_COOLDOWN = 5000L; // 5 seconds

    // Pre-compiled player name patterns for faster matching
    private final ConcurrentHashMap<String, Pattern> playerPatternCache = new ConcurrentHashMap<>();

    public MentionManager(ChatPlugin plugin) {
        this.plugin = plugin;
        this.enabled = plugin.getConfig().getBoolean("mention.enabled", true);
        this.prefix = plugin.getConfig().getString("mention.prefix", "@");
        this.highlightColor = plugin.getConfig().getString("mention.highlight-color", "&e");

        String soundName = plugin.getConfig().getString("mention.sound", "BLOCK_NOTE_BLOCK_CHIME");
        Sound tempSound = null;
        try {
            tempSound = Sound.valueOf(soundName);
        } catch (IllegalArgumentException e) {
            tempSound = Sound.BLOCK_NOTE_BLOCK_CHIME;
        }
        this.mentionSound = tempSound;

        // Create optimized regex patterns
        String escapedPrefix = Pattern.quote(prefix);
        this.mentionPattern = Pattern.compile(escapedPrefix + "(\\w+)", Pattern.CASE_INSENSITIVE);
        this.quickCheckPattern = Pattern.compile(escapedPrefix + "\\w");

        // Schedule cleanup of old mentions
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin,
                this::cleanupRecentMentions, 6000L, 6000L); // Every 5 minutes
    }

    public String processMentions(Player sender, String message, Collection<? extends Player> recipients) {
        // Quick checks to avoid expensive operations
        if (!enabled || !sender.hasPermission("chat.mention")) {
            return message;
        }

        // Fast check if message might contain mentions
        if (!message.contains(prefix) || !quickCheckPattern.matcher(message).find()) {
            return message;
        }

        // Build player lookup map with lowercase keys for case-insensitive matching
        Map<String, Player> playerMap = new HashMap<>(recipients.size());
        for (Player p : recipients) {
            playerMap.put(p.getName().toLowerCase(), p);
        }

        // Process mentions
        return processMentionsInternal(sender, message, playerMap);
    }

    private String processMentionsInternal(Player sender, String message, Map<String, Player> playerMap) {
        // Use a more efficient approach with a single pass
        StringBuilder result = new StringBuilder(message.length() + 20);
        Set<Player> mentionedPlayers = new HashSet<>();

        Matcher matcher = mentionPattern.matcher(message);
        int lastEnd = 0;
        boolean foundAny = false;

        while (matcher.find()) {
            String username = matcher.group(1);
            Player mentioned = playerMap.get(username.toLowerCase());

            if (mentioned != null && !mentioned.equals(sender)) {
                // Append text before match
                result.append(message, lastEnd, matcher.start());

                // Append highlighted mention
                result.append(highlightColor)
                        .append(prefix)
                        .append(username)
                        .append("&r");

                lastEnd = matcher.end();
                foundAny = true;

                // Track mentioned player for notification
                mentionedPlayers.add(mentioned);
            }
        }

        // If no valid mentions found, return original message
        if (!foundAny) {
            return message;
        }

        // Append remaining text
        result.append(message, lastEnd, message.length());

        // Notify mentioned players asynchronously (we're already in async context)
        if (!mentionedPlayers.isEmpty()) {
            // Fire and forget notification
            CompletableFuture.runAsync(() ->
                    notifyMentionedPlayersOptimized(sender, mentionedPlayers)
            );
        }

        return ChatUtils.colorize(result.toString());
    }

    private void notifyMentionedPlayersOptimized(Player sender, Set<Player> mentionedPlayers) {
        String senderName = sender.getName();
        long currentTime = System.currentTimeMillis();

        for (Player mentioned : mentionedPlayers) {
            // Check cooldown
            String mentionKey = mentioned.getName() + ":" + senderName;
            Long lastMention = recentMentions.get(mentionKey);

            if (lastMention != null && (currentTime - lastMention) < MENTION_COOLDOWN) {
                continue; // Skip if recently mentioned
            }

            // Update last mention time
            recentMentions.put(mentionKey, currentTime);

            // Send notification (ensure we're on the main thread for sound)
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                mentioned.playSound(mentioned.getLocation(), mentionSound, 1.0f, 1.0f);
                mentioned.sendActionBar(ChatUtils.colorizeComponent("&eYou were mentioned by &6" + senderName));
            });
        }
    }

    private void cleanupRecentMentions() {
        long currentTime = System.currentTimeMillis();
        recentMentions.entrySet().removeIf(entry ->
                (currentTime - entry.getValue()) > MENTION_COOLDOWN * 2);
    }

    public void reload() {
        recentMentions.clear();
        playerPatternCache.clear();
    }
}