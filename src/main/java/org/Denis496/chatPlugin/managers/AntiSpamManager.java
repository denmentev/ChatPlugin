package org.Denis496.chatPlugin.managers;

import org.Denis496.chatPlugin.ChatPlugin;
import org.Denis496.chatPlugin.utils.ChatUtils;
import org.bukkit.entity.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;

public class AntiSpamManager {

    private final ChatPlugin plugin;
    private final boolean enabled;
    private final int messageCooldown;
    private final int duplicateMessageTime;
    private final int maxMessagesPerMinute;
    private final int maxCapsPercent;
    private final int minMessageLength;
    private final boolean blockDuplicates;
    private final boolean blockExcessiveCaps;
    private final boolean blockSpamChars;
    private final int maxRepeatingChars;

    // Tracking maps
    private final ConcurrentHashMap<UUID, Long> lastMessageTime = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, String> lastMessages = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, List<Long>> messageHistory = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Integer> warnings = new ConcurrentHashMap<>();

    public AntiSpamManager(ChatPlugin plugin) {
        this.plugin = plugin;

        // Load config values
        this.enabled = plugin.getConfig().getBoolean("anti-spam.enabled", true);
        this.messageCooldown = plugin.getConfig().getInt("anti-spam.message-cooldown", 3);
        this.duplicateMessageTime = plugin.getConfig().getInt("anti-spam.duplicate-message-time", 30);
        this.maxMessagesPerMinute = plugin.getConfig().getInt("anti-spam.max-messages-per-minute", 10);
        this.maxCapsPercent = plugin.getConfig().getInt("anti-spam.max-caps-percent", 50);
        this.minMessageLength = plugin.getConfig().getInt("anti-spam.min-message-length", 3);
        this.blockDuplicates = plugin.getConfig().getBoolean("anti-spam.block-duplicates", true);
        this.blockExcessiveCaps = plugin.getConfig().getBoolean("anti-spam.block-excessive-caps", true);
        this.blockSpamChars = plugin.getConfig().getBoolean("anti-spam.block-spam-chars", true);
        this.maxRepeatingChars = plugin.getConfig().getInt("anti-spam.max-repeating-chars", 5);

        // Cleanup task
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::cleanup, 6000L, 6000L); // Every 5 minutes
    }

    public boolean checkMessage(Player player, String message) {
        if (!enabled || player.hasPermission("chat.bypass.antispam")) {
            return true;
        }

        UUID uuid = player.getUniqueId();
        long currentTime = System.currentTimeMillis();

        // Check cooldown
        if (!checkCooldown(player, currentTime)) {
            return false;
        }

        // Check messages per minute
        if (!checkMessageRate(player, currentTime)) {
            return false;
        }

        // Check duplicate messages
        if (blockDuplicates && !checkDuplicate(player, message)) {
            return false;
        }

        // Check excessive caps
        if (blockExcessiveCaps && !checkCaps(player, message)) {
            return false;
        }

        // Check spam characters
        if (blockSpamChars && !checkSpamChars(player, message)) {
            return false;
        }

        // Update tracking
        lastMessageTime.put(uuid, currentTime);
        lastMessages.put(uuid, message.toLowerCase());

        // Add to message history
        messageHistory.compute(uuid, (k, v) -> {
            if (v == null) v = new ArrayList<>();
            v.add(currentTime);
            return v;
        });

        return true;
    }

    private boolean checkCooldown(Player player, long currentTime) {
        UUID uuid = player.getUniqueId();
        Long lastTime = lastMessageTime.get(uuid);

        if (lastTime != null) {
            long timeDiff = currentTime - lastTime;
            if (timeDiff < messageCooldown * 1000L) {
                long remaining = (messageCooldown * 1000L - timeDiff) / 1000L;
                player.sendMessage(Component.text("Please wait " + remaining + " more second(s) before sending another message!", NamedTextColor.RED));
                incrementWarning(player);
                return false;
            }
        }

        return true;
    }

    private boolean checkMessageRate(Player player, long currentTime) {
        UUID uuid = player.getUniqueId();
        List<Long> history = messageHistory.get(uuid);

        if (history != null) {
            // Remove old entries
            history.removeIf(time -> currentTime - time > 60000L);

            if (history.size() >= maxMessagesPerMinute) {
                player.sendMessage(Component.text("You are sending messages too quickly! Maximum " + maxMessagesPerMinute + " messages per minute.", NamedTextColor.RED));
                incrementWarning(player);
                return false;
            }
        }

        return true;
    }

    private boolean checkDuplicate(Player player, String message) {
        UUID uuid = player.getUniqueId();
        String lastMessage = lastMessages.get(uuid);

        if (lastMessage != null && lastMessage.equalsIgnoreCase(message)) {
            Long lastTime = lastMessageTime.get(uuid);
            if (lastTime != null && System.currentTimeMillis() - lastTime < duplicateMessageTime * 1000L) {
                player.sendMessage(Component.text("Please don't repeat the same message!", NamedTextColor.RED));
                incrementWarning(player);
                return false;
            }
        }

        return true;
    }

    private boolean checkCaps(Player player, String message) {
        if (message.length() < minMessageLength) {
            return true;
        }

        int upperCount = 0;
        int letterCount = 0;

        for (char c : message.toCharArray()) {
            if (Character.isLetter(c)) {
                letterCount++;
                if (Character.isUpperCase(c)) {
                    upperCount++;
                }
            }
        }

        if (letterCount > 0) {
            int capsPercent = (upperCount * 100) / letterCount;
            if (capsPercent > maxCapsPercent) {
                player.sendMessage(Component.text("Please don't use excessive capital letters!", NamedTextColor.RED));
                incrementWarning(player);
                return false;
            }
        }

        return true;
    }

    private boolean checkSpamChars(Player player, String message) {
        // Check for repeating characters
        int repeatCount = 1;
        char lastChar = '\0';

        for (char c : message.toCharArray()) {
            if (c == lastChar && c != ' ') {
                repeatCount++;
                if (repeatCount > maxRepeatingChars) {
                    player.sendMessage(Component.text("Please don't spam repeating characters!", NamedTextColor.RED));
                    incrementWarning(player);
                    return false;
                }
            } else {
                repeatCount = 1;
                lastChar = c;
            }
        }

        // Check for excessive special characters
        int specialCount = 0;
        for (char c : message.toCharArray()) {
            if (!Character.isLetterOrDigit(c) && c != ' ') {
                specialCount++;
            }
        }

        if (message.length() > 0 && specialCount > message.length() / 2) {
            player.sendMessage(Component.text("Please don't use excessive special characters!", NamedTextColor.RED));
            incrementWarning(player);
            return false;
        }

        return true;
    }

    private void incrementWarning(Player player) {
        UUID uuid = player.getUniqueId();
        int count = warnings.compute(uuid, (k, v) -> v == null ? 1 : v + 1);

        int kickThreshold = plugin.getConfig().getInt("anti-spam.kick-after-warnings", 5);
        if (kickThreshold > 0 && count >= kickThreshold) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                player.kick(Component.text("Kicked for spamming!", NamedTextColor.RED));
            });
            warnings.remove(uuid);
            clearPlayerData(uuid);
        }
    }

    private void cleanup() {
        long currentTime = System.currentTimeMillis();

        // Clean old message times
        lastMessageTime.entrySet().removeIf(entry ->
                currentTime - entry.getValue() > TimeUnit.MINUTES.toMillis(5));

        // Clean message history
        messageHistory.values().forEach(list ->
                list.removeIf(time -> currentTime - time > TimeUnit.MINUTES.toMillis(1)));

        // Remove empty histories
        messageHistory.entrySet().removeIf(entry -> entry.getValue().isEmpty());

        // Clean old warnings
        warnings.entrySet().removeIf(entry -> {
            Player player = plugin.getServer().getPlayer(entry.getKey());
            return player == null || !player.isOnline();
        });
    }

    public void clearPlayerData(UUID uuid) {
        lastMessageTime.remove(uuid);
        lastMessages.remove(uuid);
        messageHistory.remove(uuid);
        warnings.remove(uuid);
    }

    public void clearPlayerData(Player player) {
        clearPlayerData(player.getUniqueId());
    }

    public void reload() {
        // Data is cleared on reload, config values are reloaded in constructor
        lastMessageTime.clear();
        lastMessages.clear();
        messageHistory.clear();
        warnings.clear();
    }
}