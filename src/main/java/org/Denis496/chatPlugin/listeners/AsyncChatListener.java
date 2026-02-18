package org.Denis496.chatPlugin.listeners;

import org.Denis496.chatPlugin.ChatPlugin;
import org.Denis496.chatPlugin.integration.PlayTimeIntegration;
import org.Denis496.chatPlugin.managers.ChatModeManager;
import org.Denis496.chatPlugin.utils.ChatUtils;
import org.Denis496.warns.API.WarnsAPI;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.*;
import java.util.concurrent.*;

public class AsyncChatListener implements Listener {

    private final ChatPlugin plugin;
    private final LuckPerms luckPerms;
    private final PlainTextComponentSerializer plainSerializer = PlainTextComponentSerializer.plainText();
    private final PlayTimeIntegration playTimeIntegration;

    private final ExecutorService asyncExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "ChatPlugin-Async");
        t.setDaemon(true);
        return t;
    });

    private final ConcurrentHashMap<UUID, CachedData<String>> prefixCache = new ConcurrentHashMap<>();

    private static final long PREFIX_CACHE_DURATION = 30000L;

    private static class CachedData<T> {
        final T value;
        final long expireTime;

        CachedData(T value, long ttl) {
            this.value = value;
            this.expireTime = System.currentTimeMillis() + ttl;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expireTime;
        }
    }

    public AsyncChatListener(ChatPlugin plugin) {
        this.plugin = plugin;
        this.luckPerms = plugin.getLuckPerms();
        this.playTimeIntegration = PlayTimeIntegration.getInstance();

        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::cleanup, 600L, 600L);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAsyncChat(AsyncChatEvent event) {
        event.setCancelled(true);
        event.viewers().clear();

        Player player = event.getPlayer();
        String message = plainSerializer.serialize(event.message());

        if (message.trim().isEmpty()) {
            return;
        }

        WarnsAPI warnsAPI = plugin.getWarnsAPI();
        if (warnsAPI != null && warnsAPI.isPlayerMuted(player)) {
            org.Denis496.warns.models.Mute mute = warnsAPI.getActiveMute(player);
            if (mute != null) {
                player.sendMessage(Component.text()
                        .append(Component.text("You are muted! ", NamedTextColor.RED))
                        .append(Component.text("Time remaining: ", NamedTextColor.GRAY))
                        .append(Component.text(mute.getRemainingTimeFormatted(), NamedTextColor.YELLOW))
                        .build());
                player.sendMessage(Component.text()
                        .append(Component.text("Reason: ", NamedTextColor.GRAY))
                        .append(Component.text(mute.getReason(), NamedTextColor.WHITE))
                        .build());
            } else {
                player.sendMessage(Component.text("You are muted and cannot chat!", NamedTextColor.RED));
            }
            return;
        }

        if (!plugin.getAntiSpamManager().checkMessage(player, message)) {
            return;
        }

        String globalPrefixStr = plugin.getConfig().getString("chat.global.prefix", "!");
        boolean isGlobalPrefix = message.startsWith(globalPrefixStr);
        boolean isInGlobalMode = plugin.getChatModeManager().isInGlobalMode(player);

        boolean sendToGlobal = false;

        if (isGlobalPrefix) {
            message = message.substring(globalPrefixStr.length()).trim();
            sendToGlobal = true;
        } else if (isInGlobalMode) {
            sendToGlobal = true;
        }

        if (sendToGlobal) {
            if (!player.hasPermission("chat.global")) {
                player.sendMessage(Component.text("You don't have permission to use global chat!", NamedTextColor.RED));
                return;
            }

            handleGlobalChat(player, message);
        } else {
            if (!player.hasPermission("chat.local")) {
                player.sendMessage(Component.text("You don't have permission to use local chat!", NamedTextColor.RED));
                return;
            }

            handleLocalChat(player, message);
        }
    }

    private void handleGlobalChat(Player player, String message) {
        try {
            String prefix = getCachedPrefix(player);
            Component formattedMessage = buildMessage(player, message, prefix, true);

            for (Player recipient : Bukkit.getOnlinePlayers()) {
                if (!plugin.getIgnoreManager().isIgnoring(recipient, player)) {
                    recipient.sendMessage(formattedMessage);
                }
            }

            Bukkit.getConsoleSender().sendMessage(formattedMessage);

            if (plugin.getVelocityHandler() != null && plugin.getVelocityHandler().isEnabled()) {
                plugin.getVelocityHandler().sendGlobalMessage(player, message);
            }

            if (plugin.getChatModeManager().isInGlobalMode(player)) {
                notifyDiscordAuthForGlobalMode(player, message);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error in global chat: " + e.getMessage());
        }
    }

    private void handleLocalChat(Player player, String message) {
        try {
            int radius = plugin.getConfig().getInt("chat.local.radius", 50);
            double radiusSquared = radius * radius;

            String prefix = getCachedPrefix(player);
            Component formattedMessage = buildMessage(player, message, prefix, false);

            Set<Player> recipients = new HashSet<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getWorld().equals(player.getWorld()) &&
                        p.getLocation().distanceSquared(player.getLocation()) <= radiusSquared &&
                        !plugin.getIgnoreManager().isIgnoring(p, player)) {
                    recipients.add(p);
                    p.sendMessage(formattedMessage);
                }
            }

            if (recipients.size() <= 1) {
                player.sendMessage(Component.text()
                        .append(Component.text("No one heard you. ", NamedTextColor.GRAY))
                        .append(Component.text("Use ", NamedTextColor.GRAY))
                        .append(Component.text("!", NamedTextColor.YELLOW))
                        .append(Component.text(" or ", NamedTextColor.GRAY))
                        .append(Component.text("/global", NamedTextColor.YELLOW))
                        .append(Component.text(" to switch to global chat.", NamedTextColor.GRAY))
                        .build());
            }

            Bukkit.getConsoleSender().sendMessage(formattedMessage);
        } catch (Exception e) {
            plugin.getLogger().warning("Error in local chat: " + e.getMessage());
        }
    }

    private void notifyDiscordAuthForGlobalMode(Player player, String message) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String playerName = player.getName();
                plugin.getLogger().info("[ChatPlugin] Player " + playerName + " in global mode sent: " + message);
            } catch (Exception e) {
                plugin.getLogger().warning("Error notifying DiscordAuth: " + e.getMessage());
            }
        });
    }

    private Component buildMessage(Player player, String message, String prefix, boolean isGlobal) {
        String playerName = player.getName();

        TextComponent.Builder builder = Component.text();

        builder.append(Component.text(isGlobal ? "G " : "L ", isGlobal ? NamedTextColor.RED : NamedTextColor.YELLOW));
        builder.append(Component.text("| ", NamedTextColor.DARK_GRAY));

        if (!prefix.isEmpty()) {
            builder.append(ChatUtils.colorizeComponent(prefix + " "));
        }

        Component playerComponent = createSimplePlayerComponent(player);
        builder.append(playerComponent);

        builder.append(Component.text(" › ", NamedTextColor.DARK_GRAY));

        Component processedMessage = processMessage(player, message);
        builder.append(processedMessage);

        return builder.build();
    }

    private Component createSimplePlayerComponent(Player player) {
        TextComponent.Builder hoverBuilder = Component.text();

        hoverBuilder.append(Component.text("Player Information", NamedTextColor.YELLOW));
        hoverBuilder.append(Component.newline());

        // Use safe integration for playtime - only show if available
        if (playTimeIntegration.isAvailable()) {
            double totalHours = playTimeIntegration.getTotalHours(player);
            if (totalHours > 0) {
                hoverBuilder.append(Component.text("Hours Played: ", NamedTextColor.GRAY));
                hoverBuilder.append(Component.text(playTimeIntegration.formatHours(totalHours), NamedTextColor.GREEN));
                hoverBuilder.append(Component.newline());
                hoverBuilder.append(Component.newline());
            }
        }

        hoverBuilder.append(Component.text("Click to message", NamedTextColor.GRAY, TextDecoration.ITALIC));

        Component hover = hoverBuilder.build();

        return Component.text(player.getName(), NamedTextColor.WHITE)
                .hoverEvent(HoverEvent.showText(hover))
                .clickEvent(ClickEvent.suggestCommand("/m " + player.getName() + " "));
    }

    private Component processMessage(Player player, String message) {
        if (plugin.getConfig().getBoolean("mention.enabled", true) && message.contains("@")) {
            message = plugin.getMentionManager().processMentions(player, message, Bukkit.getOnlinePlayers());
        }

        Component messageComponent = plugin.getPlaceholderManager().processPlaceholdersAsComponent(player, message);

        if (plugin.getMarksHook() != null && plugin.getMarksHook().isHooked()) {
            messageComponent = plugin.getMarksHook().processMarkPlaceholders(messageComponent);
        }

        return messageComponent;
    }

    private String getCachedPrefix(Player player) {
        UUID uuid = player.getUniqueId();
        CachedData<String> cached = prefixCache.get(uuid);

        if (cached != null && !cached.isExpired()) {
            return cached.value;
        }

        return loadPrefix(player);
    }

    private String loadPrefix(Player player) {
        String prefix = "";

        if (luckPerms != null && plugin.getConfig().getBoolean("chat.use-luckperms-prefix", true)) {
            try {
                User user = luckPerms.getPlayerAdapter(Player.class).getUser(player);
                if (user != null) {
                    CachedMetaData metaData = user.getCachedData().getMetaData();
                    String lpPrefix = metaData.getPrefix();
                    prefix = lpPrefix != null ? lpPrefix : "";

                    String suffix = metaData.getSuffix();
                    if (suffix != null && !suffix.isEmpty()) {
                        prefix = prefix + " " + suffix;
                    }
                }
            } catch (Exception e) {
                if (plugin.getConfig().getBoolean("debug", false)) {
                    plugin.getLogger().warning("Failed to load prefix for " + player.getName() + ": " + e.getMessage());
                }
            }
        }

        prefixCache.put(player.getUniqueId(), new CachedData<>(prefix, PREFIX_CACHE_DURATION));
        return prefix;
    }

    public void clearPlayerCache(Player player) {
        prefixCache.remove(player.getUniqueId());
    }

    public void clearAllCaches() {
        prefixCache.clear();
    }

    private void cleanup() {
        prefixCache.entrySet().removeIf(e -> e.getValue().isExpired());

        Set<UUID> onlineUUIDs = new HashSet<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            onlineUUIDs.add(p.getUniqueId());
        }

        prefixCache.keySet().retainAll(onlineUUIDs);
    }

    public void shutdown() {
        asyncExecutor.shutdown();
        try {
            if (!asyncExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                asyncExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            asyncExecutor.shutdownNow();
        }
    }
}