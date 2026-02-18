package org.Denis496.chatPlugin.listeners;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.event.EventBus;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import net.luckperms.api.event.user.UserCacheLoadEvent;
import net.luckperms.api.event.node.NodeAddEvent;
import net.luckperms.api.event.node.NodeRemoveEvent;
import net.luckperms.api.event.node.NodeClearEvent;
import net.luckperms.api.event.group.GroupDataRecalculateEvent;
import net.luckperms.api.event.track.mutate.TrackMutateEvent;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.PermissionHolder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.Denis496.chatPlugin.ChatPlugin;

import java.util.UUID;

public class LuckPermsEventListener {

    private final ChatPlugin plugin;
    private final LuckPerms luckPerms;

    public LuckPermsEventListener(ChatPlugin plugin) {
        this.plugin = plugin;
        this.luckPerms = plugin.getLuckPerms();

        if (luckPerms != null) {
            registerEvents();
        }
    }

    public void registerEvents() {
        EventBus eventBus = luckPerms.getEventBus();

        // Listen for user data recalculation (includes prefix/suffix changes)
        eventBus.subscribe(plugin, UserDataRecalculateEvent.class, event -> {
            User user = event.getUser();
            handleUserUpdate(user.getUniqueId());
        });

        // Listen for user cache load (when player joins)
        eventBus.subscribe(plugin, UserCacheLoadEvent.class, event -> {
            User user = event.getUser();
            handleUserUpdate(user.getUniqueId());
        });

        // Listen for ANY node changes (more comprehensive)
        eventBus.subscribe(plugin, NodeAddEvent.class, event -> {
            if (event.isUser()) {
                String key = event.getNode().getKey();
                // Check for prefix, suffix, or group changes
                if (key.startsWith("prefix.") || key.startsWith("suffix.") ||
                        key.startsWith("group.") || key.equals("weight")) {
                    PermissionHolder target = event.getTarget();
                    if (target instanceof User user) {
                        handleUserUpdate(user.getUniqueId());
                    }
                }
            }
        });

        eventBus.subscribe(plugin, NodeRemoveEvent.class, event -> {
            if (event.isUser()) {
                String key = event.getNode().getKey();
                if (key.startsWith("prefix.") || key.startsWith("suffix.") ||
                        key.startsWith("group.") || key.equals("weight")) {
                    PermissionHolder target = event.getTarget();
                    if (target instanceof User user) {
                        handleUserUpdate(user.getUniqueId());
                    }
                }
            }
        });

        eventBus.subscribe(plugin, NodeClearEvent.class, event -> {
            if (event.isUser()) {
                PermissionHolder target = event.getTarget();
                if (target instanceof User user) {
                    handleUserUpdate(user.getUniqueId());
                }
            }
        });

        // Listen for group changes (affects all members)
        eventBus.subscribe(plugin, GroupDataRecalculateEvent.class, event -> {
            String groupName = event.getGroup().getName();

            // Update all online players in this group
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    User user = luckPerms.getPlayerAdapter(Player.class).getUser(player);
                    if (user != null && user.getPrimaryGroup().equalsIgnoreCase(groupName)) {
                        refreshPlayerCache(player);
                    }
                }
            });
        });

        // Listen for track changes (promotions/demotions)
        eventBus.subscribe(plugin, TrackMutateEvent.class, event -> {
            // Refresh all online players as track changes might affect them
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    refreshPlayerCache(player);
                }
            });
        });
    }

    private void handleUserUpdate(UUID uuid) {
        if (uuid == null) return;

        // Update immediately on the main thread
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                refreshPlayerCache(player);
            }
        });
    }

    private void refreshPlayerCache(Player player) {
        // Clear all caches for this player
        if (plugin.getAsyncChatListener() != null) {
            plugin.getAsyncChatListener().clearPlayerCache(player);
        }

        if (plugin.getPlaceholderManager() != null) {
            plugin.getPlaceholderManager().clearPlayerCache(player);
        }

        // Log for debugging
        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info("Refreshed cache for " + player.getName() + " due to LuckPerms update");
        }
    }

    public void unregister() {
        // LuckPerms will handle cleanup when the plugin is disabled
    }
}