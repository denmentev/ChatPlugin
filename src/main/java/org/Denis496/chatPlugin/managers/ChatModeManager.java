package org.Denis496.chatPlugin.managers;

import org.Denis496.chatPlugin.ChatPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ChatModeManager {

    private final ChatPlugin plugin;
    private final ConcurrentHashMap<UUID, ChatMode> playerModes;
    private File dataFile;
    private FileConfiguration dataConfig;

    public enum ChatMode {
        LOCAL,
        GLOBAL
    }

    public ChatModeManager(ChatPlugin plugin) {
        this.plugin = plugin;
        this.playerModes = new ConcurrentHashMap<>();
        loadData();
    }

    private void loadData() {
        dataFile = new File(plugin.getDataFolder(), "chatmodes.yml");
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to create chatmodes.yml: " + e.getMessage());
            }
        }

        dataConfig = YamlConfiguration.loadConfiguration(dataFile);

        for (String key : dataConfig.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                String modeStr = dataConfig.getString(key);
                ChatMode mode = ChatMode.valueOf(modeStr);
                playerModes.put(uuid, mode);

                org.bukkit.entity.Player player = org.bukkit.Bukkit.getPlayer(uuid);
                if (player != null && player.isOnline()) {
                    player.setMetadata("chatMode", new org.bukkit.metadata.FixedMetadataValue(plugin, mode.name()));
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Invalid chat mode entry: " + key);
            }
        }
    }

    public void saveData() {
        for (UUID uuid : playerModes.keySet()) {
            dataConfig.set(uuid.toString(), playerModes.get(uuid).name());
        }

        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save chatmodes.yml: " + e.getMessage());
        }
    }

    public ChatMode getPlayerMode(Player player) {
        ChatMode mode = playerModes.getOrDefault(player.getUniqueId(), ChatMode.LOCAL);
        player.setMetadata("chatMode", new org.bukkit.metadata.FixedMetadataValue(plugin, mode.name()));
        return mode;
    }

    public void setPlayerMode(Player player, ChatMode mode) {
        playerModes.put(player.getUniqueId(), mode);
        player.setMetadata("chatMode", new org.bukkit.metadata.FixedMetadataValue(plugin, mode.name()));
        saveData();
    }

    public boolean isInGlobalMode(Player player) {
        return getPlayerMode(player) == ChatMode.GLOBAL;
    }

    public void clearPlayerData(UUID uuid) {
        playerModes.remove(uuid);
    }

    public void reload() {
        saveData();
        playerModes.clear();
        loadData();
    }

    public void shutdown() {
        saveData();
    }
}