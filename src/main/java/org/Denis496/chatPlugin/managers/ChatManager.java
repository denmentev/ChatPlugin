package org.Denis496.chatPlugin.managers;

import org.Denis496.chatPlugin.ChatPlugin;

public class ChatManager {

    private final ChatPlugin plugin;
    private final boolean localEnabled;
    private final boolean globalEnabled;
    private final int localRadius;
    private final String globalPrefix;

    public ChatManager(ChatPlugin plugin) {
        this.plugin = plugin;
        this.localEnabled = plugin.getConfig().getBoolean("chat.local.enabled", true);
        this.globalEnabled = plugin.getConfig().getBoolean("chat.global.enabled", true);
        this.localRadius = plugin.getConfig().getInt("chat.local.radius", 50);
        this.globalPrefix = plugin.getConfig().getString("chat.global.prefix", "!");
    }

    public boolean isGlobalPrefix(String message) {
        return globalEnabled && message.startsWith(globalPrefix);
    }

    public boolean isLocalEnabled() {
        return localEnabled;
    }

    public boolean isGlobalEnabled() {
        return globalEnabled;
    }

    public int getLocalRadius() {
        return localRadius;
    }

    public String getGlobalPrefix() {
        return globalPrefix;
    }
}