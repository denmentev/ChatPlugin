package org.Denis496.chatPlugin.managers;

import org.Denis496.chatPlugin.ChatPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DMManager {

    private final ChatPlugin plugin;
    private final ConcurrentHashMap<UUID, Boolean> dmEnabled;
    private File dataFile;
    private FileConfiguration dataConfig;

    public DMManager(ChatPlugin plugin) {
        this.plugin = plugin;
        this.dmEnabled = new ConcurrentHashMap<>();
        loadData();
    }

    private void loadData() {
        dataFile = new File(plugin.getDataFolder(), "dmsettings.yml");
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to create dmsettings.yml: " + e.getMessage());
            }
        }

        dataConfig = YamlConfiguration.loadConfiguration(dataFile);

        for (String key : dataConfig.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                boolean enabled = dataConfig.getBoolean(key, true);
                dmEnabled.put(uuid, enabled);
            } catch (Exception e) {
                plugin.getLogger().warning("Invalid DM settings entry: " + key);
            }
        }
    }

    public void saveData() {
        for (UUID uuid : dmEnabled.keySet()) {
            dataConfig.set(uuid.toString(), dmEnabled.get(uuid));
        }

        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save dmsettings.yml: " + e.getMessage());
        }
    }

    public boolean isDMEnabled(Player player) {
        return dmEnabled.getOrDefault(player.getUniqueId(), true);
    }

    public void setDMEnabled(Player player, boolean enabled) {
        dmEnabled.put(player.getUniqueId(), enabled);
        saveData();
    }

    public boolean canSendDM(Player sender, Player recipient) {
        if (!isDMEnabled(sender)) {
            return false;
        }
        return isDMEnabled(recipient);
    }

    public void clearPlayerData(UUID uuid) {
        dmEnabled.remove(uuid);
    }

    public void reload() {
        saveData();
        dmEnabled.clear();
        loadData();
    }

    public void shutdown() {
        saveData();
    }
}