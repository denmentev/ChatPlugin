package org.Denis496.chatPlugin.managers;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.Denis496.chatPlugin.ChatPlugin;
import org.Denis496.chatPlugin.utils.ChatUtils;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.Component;

public class MessageManager {

    private final ChatPlugin plugin;
    private final ConcurrentHashMap<UUID, UUID> lastMessengers;
    private final ConcurrentHashMap<UUID, Long> lastMessageTime;
    private static final long MESSAGE_TIMEOUT = TimeUnit.MINUTES.toMillis(30);

    public MessageManager(ChatPlugin plugin) {
        this.plugin = plugin;
        this.lastMessengers = new ConcurrentHashMap<>();
        this.lastMessageTime = new ConcurrentHashMap<>();

        // Schedule cleanup task
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::cleanupOldEntries, 6000L, 6000L);
    }

    public void sendPrivateMessage(Player sender, Player recipient, String message) {
        // Check for empty message
        if (message.trim().isEmpty()) {
            sender.sendMessage(ChatUtils.colorizeComponent("&cYou cannot send empty messages!"));
            return;
        }

        // Check anti-spam for private messages
        if (!plugin.getAntiSpamManager().checkMessage(sender, message)) {
            return;
        }

        // Process message with full placeholder support (including marks)
        Component processedMessage = processMessageWithPlaceholders(sender, message);

        // Format messages
        String outgoingFormat = plugin.getConfig().getString("messages.private.outgoing",
                "&7[&6me &7-> &6{PLAYER}&7] &f");
        Component outgoingPrefix = ChatUtils.colorizeComponent(outgoingFormat.replace("{PLAYER}", recipient.getName()));
        Component outgoingMessage = outgoingPrefix.append(processedMessage);

        String incomingFormat = plugin.getConfig().getString("messages.private.incoming",
                "&7[&6{PLAYER} &7-> &6me&7] &f");
        Component incomingPrefix = ChatUtils.colorizeComponent(incomingFormat.replace("{PLAYER}", sender.getName()));
        Component incomingMessage = incomingPrefix.append(processedMessage);

        // Send messages
        sender.sendMessage(outgoingMessage);
        recipient.sendMessage(incomingMessage);

        // Update last messengers
        long currentTime = System.currentTimeMillis();
        lastMessengers.put(sender.getUniqueId(), recipient.getUniqueId());
        lastMessengers.put(recipient.getUniqueId(), sender.getUniqueId());
        lastMessageTime.put(sender.getUniqueId(), currentTime);
        lastMessageTime.put(recipient.getUniqueId(), currentTime);

        // Log if configured
        if (plugin.getConfig().getBoolean("messages.log-private", false)) {
            plugin.getLogger().info("[PM] " + sender.getName() + " -> " + recipient.getName() + ": " + message);
        }
    }

    private Component processMessageWithPlaceholders(Player player, String message) {
        // Process placeholders
        message = plugin.getPlaceholderManager().processPlaceholders(player, message);

        // Convert to component
        Component messageComponent = ChatUtils.colorizeComponent(message);

        // Process marks if available (unified processing)
        if (plugin.getMarksHook() != null && plugin.getMarksHook().isHooked()) {
            messageComponent = plugin.getMarksHook().processMarkPlaceholders(messageComponent);
        }

        return messageComponent;
    }

    public Player getLastMessenger(Player player) {
        UUID playerUUID = player.getUniqueId();
        Long lastTime = lastMessageTime.get(playerUUID);

        if (lastTime != null && (System.currentTimeMillis() - lastTime) > MESSAGE_TIMEOUT) {
            lastMessengers.remove(playerUUID);
            lastMessageTime.remove(playerUUID);
            return null;
        }

        UUID lastUUID = lastMessengers.get(playerUUID);
        return lastUUID != null ? Bukkit.getPlayer(lastUUID) : null;
    }

    public void clearLastMessenger(Player player) {
        UUID playerUUID = player.getUniqueId();
        lastMessengers.remove(playerUUID);
        lastMessageTime.remove(playerUUID);
    }

    private void cleanupOldEntries() {
        long currentTime = System.currentTimeMillis();
        lastMessageTime.entrySet().removeIf(entry -> {
            if ((currentTime - entry.getValue()) > MESSAGE_TIMEOUT) {
                lastMessengers.remove(entry.getKey());
                return true;
            }
            return false;
        });
    }

    public void shutdown() {
        lastMessengers.clear();
        lastMessageTime.clear();
    }
}