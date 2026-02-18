package org.Denis496.chatPlugin;

import org.Denis496.chatPlugin.integration.PlayTimeIntegration;
import org.Denis496.chatPlugin.utils.PlaceholderManager;
import org.Denis496.chatPlugin.hooks.MarksHook;
import org.Denis496.warns.API.WarnsAPI;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.Denis496.chatPlugin.commands.*;
import org.Denis496.chatPlugin.listeners.AsyncChatListener;
import org.Denis496.chatPlugin.listeners.PlayerJoinQuitListener;
import org.Denis496.chatPlugin.listeners.LuckPermsEventListener;
import org.Denis496.chatPlugin.managers.*;
import org.Denis496.chatPlugin.proxy.VelocityHandler;
import org.Denis496.chatPlugin.utils.ConfigManager;
import org.Denis496.chatPlugin.utils.ChatUtils;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.logging.Level;

public class ChatPlugin extends JavaPlugin {

    private static ChatPlugin instance;
    private ConfigManager configManager;
    private ChatManager chatManager;
    private MentionManager mentionManager;
    private MessageManager messageManager;
    private PlaceholderManager placeholderManager;
    private VelocityHandler velocityHandler;
    private MarksHook marksHook;
    private LuckPerms luckPerms;
    private AsyncChatListener asyncChatListener;
    private LuckPermsEventListener luckPermsListener;
    private AntiSpamManager antiSpamManager;
    private WarnsAPI warnsAPI;
    private ChatModeManager chatModeManager;
    private DMManager dmManager;
    private IgnoreManager ignoreManager;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        configManager = new ConfigManager(this);

        if (!initializeLuckPerms()) {
            getLogger().warning("LuckPerms not available. Prefix features will be disabled.");
        }

        if (!checkDependencies()) {
            getLogger().severe("Required dependencies not found. Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Initialize PlayTimeTracker integration
        initializePlayTimeIntegration();

        initializeWarnsAPI();
        initializeManagers();
        initializeHooks();

        if (getConfig().getBoolean("proxy.velocity.enabled", false)) {
            velocityHandler = new VelocityHandler(this);
        }

        registerListeners();
        registerCommands();

        getLogger().info("ChatPlugin v" + getDescription().getVersion() + " enabled!");
    }

    @Override
    public void onDisable() {
        if (luckPermsListener != null) {
            luckPermsListener.unregister();
        }

        if (asyncChatListener != null) {
            asyncChatListener.shutdown();
            asyncChatListener.clearAllCaches();
        }

        if (marksHook != null) {
            marksHook.shutdown();
        }

        if (velocityHandler != null) {
            velocityHandler.unregister();
        }

        if (messageManager != null) {
            messageManager.shutdown();
        }

        if (chatModeManager != null) {
            chatModeManager.shutdown();
        }

        if (dmManager != null) {
            dmManager.shutdown();
        }

        if (ignoreManager != null) {
            ignoreManager.shutdown();
        }

        Bukkit.getScheduler().cancelTasks(this);

        getLogger().info("ChatPlugin disabled!");
    }

    private void initializePlayTimeIntegration() {
        // This will automatically try to hook into PlayTimeTracker if available
        PlayTimeIntegration integration = PlayTimeIntegration.getInstance();

        // Check if integration was successful
        if (integration.isAvailable()) {
            getLogger().info("PlayTimeTracker integration enabled - playtime features available!");
        } else {
            getLogger().info("PlayTimeTracker not found or not ready - playtime features disabled");
        }
    }

    private boolean initializeLuckPerms() {
        try {
            Plugin luckPermsPlugin = getServer().getPluginManager().getPlugin("LuckPerms");
            if (luckPermsPlugin == null || !luckPermsPlugin.isEnabled()) {
                return false;
            }

            try {
                luckPerms = LuckPermsProvider.get();
                getLogger().info("Successfully hooked into LuckPerms using LuckPermsProvider!");
                return true;
            } catch (IllegalStateException e) {
                luckPerms = getServer().getServicesManager().load(LuckPerms.class);
                if (luckPerms != null) {
                    getLogger().info("Successfully hooked into LuckPerms using ServicesManager!");
                    return true;
                }
            }
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Failed to hook into LuckPerms", e);
        }
        return false;
    }

    private void initializeWarnsAPI() {
        Plugin warnsPlugin = getServer().getPluginManager().getPlugin("WarnsPlugin");
        if (warnsPlugin != null && warnsPlugin.isEnabled()) {
            try {
                warnsAPI = org.Denis496.warns.WarnsPlugin.getAPI();
                if (warnsAPI != null) {
                    getLogger().info("Successfully hooked into WarnsPlugin API!");

                    warnsAPI.registerMuteListener(new WarnsAPI.MuteListener() {
                        @Override
                        public void onPlayerMuted(UUID playerUUID, org.Denis496.warns.models.Mute mute) {
                            Player player = Bukkit.getPlayer(playerUUID);
                            if (player != null && player.isOnline()) {
                                getLogger().info("Player " + player.getName() + " was muted");
                            }
                        }

                        @Override
                        public void onPlayerUnmuted(UUID playerUUID, Player remover) {
                            Player player = Bukkit.getPlayer(playerUUID);
                            if (player != null && player.isOnline()) {
                                getLogger().info("Player " + player.getName() + " was unmuted");
                            }
                        }

                        @Override
                        public void onMuteExpired(UUID playerUUID, org.Denis496.warns.models.Mute mute) {
                            Player player = Bukkit.getPlayer(playerUUID);
                            if (player != null && player.isOnline()) {
                                player.sendMessage(ChatUtils.colorize("&aYour mute has expired! You can chat again."));
                            }
                        }
                    });
                } else {
                    getLogger().warning("WarnsPlugin found but API is not available!");
                }
            } catch (Exception e) {
                getLogger().warning("Failed to hook into WarnsPlugin API: " + e.getMessage());
            }
        } else {
            getLogger().info("WarnsPlugin not found. Mute features will be disabled.");
        }
    }

    private boolean checkDependencies() {
        Plugin teamsPlugin = getServer().getPluginManager().getPlugin("Teams");
        if (teamsPlugin == null || !teamsPlugin.isEnabled()) {
            getLogger().warning("Teams plugin not found. Team features will be limited.");
        }

        return true;
    }

    private void initializeManagers() {
        try {
            chatManager = new ChatManager(this);
            mentionManager = new MentionManager(this);
            messageManager = new MessageManager(this);
            placeholderManager = new PlaceholderManager(this);
            antiSpamManager = new AntiSpamManager(this);
            chatModeManager = new ChatModeManager(this);
            dmManager = new DMManager(this);
            ignoreManager = new IgnoreManager(this);
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to initialize managers", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private void initializeHooks() {
        Plugin marksPlugin = getServer().getPluginManager().getPlugin("Marks");
        if (marksPlugin != null && marksPlugin.isEnabled()) {
            try {
                marksHook = new MarksHook(this);
                if (marksHook.isHooked()) {
                    getLogger().info("Successfully hooked into Marks plugin!");
                }
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "Failed to hook into Marks plugin", e);
            }
        }
    }

    private void registerListeners() {
        if (asyncChatListener != null) {
            HandlerList.unregisterAll(asyncChatListener);
        }

        asyncChatListener = new AsyncChatListener(this);
        getServer().getPluginManager().registerEvents(asyncChatListener, this);
        getServer().getPluginManager().registerEvents(new PlayerJoinQuitListener(this), this);

        if (luckPerms != null) {
            luckPermsListener = new LuckPermsEventListener(this);
            getLogger().info("Registered LuckPerms event listener for instant prefix updates");
        }
    }

    private void registerCommands() {
        registerCommand("message", new MessageCommand(this));
        registerCommand("reply", new ReplyCommand(this));
        registerCommand("roll", new RollCommand(this));
        registerCommand("coin", new CoinCommand(this));
        registerCommand("chatplugin", new ChatPluginCommand(this));
        registerCommand("chatformat", new ChatFormatCommand(this));
        registerCommand("muteinfo", new MuteInfoCommand(this));
        registerCommand("local", new LocalCommand(this));
        registerCommand("global", new GlobalCommand(this));
        registerCommand("dm", new DMCommand(this));
        registerCommand("ignore", new IgnoreCommand(this));
    }

    private void registerCommand(String name, Object executor) {
        var command = getCommand(name);
        if (command != null) {
            if (executor instanceof org.bukkit.command.CommandExecutor) {
                command.setExecutor((org.bukkit.command.CommandExecutor) executor);
            }
            if (executor instanceof org.bukkit.command.TabCompleter) {
                command.setTabCompleter((org.bukkit.command.TabCompleter) executor);
            }
        } else {
            getLogger().warning("Command '" + name + "' not found in plugin.yml!");
        }
    }

    public void reloadPlugin() {
        try {
            if (asyncChatListener != null) {
                HandlerList.unregisterAll(asyncChatListener);
            }

            reloadConfig();
            configManager.reload();

            chatManager = new ChatManager(this);
            mentionManager = new MentionManager(this);
            placeholderManager.reloadPlaceholders();
            antiSpamManager = new AntiSpamManager(this);
            chatModeManager.reload();
            dmManager.reload();
            ignoreManager.reload();

            if (asyncChatListener != null) {
                asyncChatListener.clearAllCaches();
            }

            if (luckPermsListener != null) {
                luckPermsListener.unregister();
            }

            if (initializeLuckPerms()) {
                luckPermsListener = new LuckPermsEventListener(this);
            }

            // Reinitialize PlayTimeTracker integration
            PlayTimeIntegration.getInstance().reinitialize();

            initializeWarnsAPI();

            if (marksHook != null) {
                marksHook.reload();
            }

            boolean velocityEnabled = getConfig().getBoolean("proxy.velocity.enabled", false);
            if (velocityHandler != null && !velocityEnabled) {
                velocityHandler.unregister();
                velocityHandler = null;
            } else if (velocityHandler == null && velocityEnabled) {
                velocityHandler = new VelocityHandler(this);
            }

            registerListeners();

            for (Player player : Bukkit.getOnlinePlayers()) {
                refreshPlayerCache(player);
            }

            getLogger().info("Configuration reloaded and all player caches refreshed!");

        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error during reload", e);
        }
    }

    public void refreshPlayerCache(Player player) {
        if (player == null || !player.isOnline()) return;

        if (asyncChatListener != null) {
            asyncChatListener.clearPlayerCache(player);
        }

        if (placeholderManager != null) {
            placeholderManager.clearPlayerCache(player);
        }

        if (getConfig().getBoolean("debug", false)) {
            getLogger().info("Refreshed cache for player: " + player.getName());
        }
    }

    public static ChatPlugin getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public ChatManager getChatManager() {
        return chatManager;
    }

    public MentionManager getMentionManager() {
        return mentionManager;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public PlaceholderManager getPlaceholderManager() {
        return placeholderManager;
    }

    public VelocityHandler getVelocityHandler() {
        return velocityHandler;
    }

    public MarksHook getMarksHook() {
        return marksHook;
    }

    public LuckPerms getLuckPerms() {
        return luckPerms;
    }

    public AntiSpamManager getAntiSpamManager() {
        return antiSpamManager;
    }

    public AsyncChatListener getAsyncChatListener() {
        return asyncChatListener;
    }

    public WarnsAPI getWarnsAPI() {
        return warnsAPI;
    }

    public ChatModeManager getChatModeManager() {
        return chatModeManager;
    }

    public DMManager getDMManager() {
        return dmManager;
    }

    public IgnoreManager getIgnoreManager() {
        return ignoreManager;
    }
}