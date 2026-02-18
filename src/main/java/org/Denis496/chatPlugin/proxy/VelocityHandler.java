package org.Denis496.chatPlugin.proxy;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.Denis496.chatPlugin.ChatPlugin;
import org.Denis496.chatPlugin.utils.ChatUtils;

import java.util.Collection;

public class VelocityHandler implements PluginMessageListener {

    private final ChatPlugin plugin;
    private final String channel;
    private final boolean enabled;

    public VelocityHandler(ChatPlugin plugin) {
        this.plugin = plugin;
        this.channel = plugin.getConfig().getString("proxy.velocity.channel", "GLOBAL_CHAT");
        this.enabled = plugin.getConfig().getBoolean("proxy.velocity.enabled", false);

        if (enabled) {
            register();
        }
    }

    private void register() {
        // Register plugin messaging channel
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, "chatplugin:global", this);
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, "chatplugin:global");

        plugin.getLogger().info("Velocity proxy support enabled on channel: " + channel);
    }

    public void unregister() {
        plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin, "chatplugin:global");
        plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin, "chatplugin:global");
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!channel.equals("chatplugin:global")) {
            return;
        }

        ByteArrayDataInput in = ByteStreams.newDataInput(message);
        String subchannel = in.readUTF();

        if (subchannel.equals(this.channel)) {
            String serverName = in.readUTF();
            String playerName = in.readUTF();
            String chatMessage = in.readUTF();
            String format = in.readUTF();

            // Don't process messages from this server
            if (serverName.equals(getServerName())) {
                return;
            }

            // Format and broadcast the message
            String formattedMessage = format
                    .replace("{PLAYER}", playerName)
                    .replace("{MESSAGE}", chatMessage)
                    .replace("{SERVER}", serverName);

            Bukkit.broadcastMessage(ChatUtils.colorize(formattedMessage));
        }
    }

    public void sendGlobalMessage(Player sender, String message) {
        if (!enabled || !sender.hasPermission("chat.proxy")) {
            return;
        }

        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF(channel);
        out.writeUTF(getServerName());
        out.writeUTF(sender.getName());
        out.writeUTF(message);
        out.writeUTF(plugin.getConfig().getString("chat.global.format"));

        // Send to all players (Velocity will handle the distribution)
        Collection<? extends Player> players = Bukkit.getOnlinePlayers();
        if (!players.isEmpty()) {
            Player p = players.iterator().next();
            p.sendPluginMessage(plugin, "chatplugin:global", out.toByteArray());
        }
    }

    private String getServerName() {
        // Try to get server name from various sources
        String serverName = plugin.getConfig().getString("server-name");
        if (serverName == null || serverName.isEmpty()) {
            serverName = Bukkit.getServer().getName();
        }
        if (serverName == null || serverName.isEmpty()) {
            serverName = "Server";
        }
        return serverName;
    }

    public boolean isEnabled() {
        return enabled;
    }
}