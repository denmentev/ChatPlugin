package org.Denis496.chatPlugin.listeners;

import org.Denis496.chatPlugin.ChatPlugin;
import org.Denis496.chatPlugin.utils.ChatUtils;
import org.Denis496.warns.API.WarnsAPI;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerJoinQuitListener implements Listener {

    private final ChatPlugin plugin;
    private final boolean joinSoundsEnabled;
    private final boolean leaveSoundsEnabled;
    private final Sound joinSound;
    private final Sound leaveSound;

    public PlayerJoinQuitListener(ChatPlugin plugin) {
        this.plugin = plugin;

        // Cache config values
        this.joinSoundsEnabled = plugin.getConfig().getBoolean("join.play-sound", true);
        this.leaveSoundsEnabled = plugin.getConfig().getBoolean("leave.play-sound", true);

        // Parse sounds once
        this.joinSound = parseSound(plugin.getConfig().getString("join.sound", "BLOCK_NOTE_BLOCK_BELL"));
        this.leaveSound = parseSound(plugin.getConfig().getString("leave.sound", "BLOCK_NOTE_BLOCK_BASS"));
    }

    private Sound parseSound(String soundName) {
        try {
            return Sound.valueOf(soundName);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid sound: " + soundName);
            return null;
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Play join sound if enabled
        if (joinSoundsEnabled && joinSound != null) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                online.playSound(online.getLocation(), joinSound, 1.0f, 1.0f);
            }
        }

        // Check if player is muted
        WarnsAPI warnsAPI = plugin.getWarnsAPI();
        if (warnsAPI != null && warnsAPI.isPlayerMuted(player)) {
            org.Denis496.warns.models.Mute mute = warnsAPI.getActiveMute(player);
            if (mute != null) {
                // Schedule mute notification after 2 seconds
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    player.sendMessage("");
                    player.sendMessage(ChatUtils.colorize("&c&l⚠ YOU ARE CURRENTLY MUTED ⚠"));
                    player.sendMessage(ChatUtils.colorize("&7Remaining time: &c" + mute.getRemainingTimeFormatted()));
                    player.sendMessage(ChatUtils.colorize("&7Reason: &c" + mute.getReason()));
                    player.sendMessage(ChatUtils.colorize("&7Muted by: &c" + mute.getIssuerName()));
                    player.sendMessage("");
                }, 40L); // 2 seconds delay
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Clear caches
        plugin.getMessageManager().clearLastMessenger(player);
        if (plugin.getAsyncChatListener() != null) {
            plugin.getAsyncChatListener().clearPlayerCache(player);
        }
        plugin.getPlaceholderManager().clearPlayerCache(player);
        plugin.getAntiSpamManager().clearPlayerData(player);

        // Play leave sound if enabled
        if (leaveSoundsEnabled && leaveSound != null) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!online.equals(player)) {
                    online.playSound(online.getLocation(), leaveSound, 1.0f, 1.0f);
                }
            }
        }
    }
}