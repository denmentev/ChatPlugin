package org.Denis496.chatPlugin.utils;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.Denis496.chatPlugin.ChatPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ConfigManager {

    private final ChatPlugin plugin;
    private final Map<String, FileConfiguration> configs;

    public ConfigManager(ChatPlugin plugin) {
        this.plugin = plugin;
        this.configs = new HashMap<>();

        loadConfigs();
    }

    private void loadConfigs() {
        // Load main config
        plugin.saveDefaultConfig();
        configs.put("config", plugin.getConfig());

        // Load messages config if exists (optional)
        loadOptionalConfig("messages.yml");
    }

    private void loadConfig(String fileName) {
        File file = new File(plugin.getDataFolder(), fileName);

        if (!file.exists()) {
            plugin.saveResource(fileName, false);
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        configs.put(fileName.replace(".yml", ""), config);
    }

    private void loadOptionalConfig(String fileName) {
        File file = new File(plugin.getDataFolder(), fileName);

        // Check if resource exists in JAR
        if (plugin.getResource(fileName) != null && !file.exists()) {
            plugin.saveResource(fileName, false);
        }

        // If file exists (either from JAR or already created), load it
        if (file.exists()) {
            FileConfiguration config = YamlConfiguration.loadConfiguration(file);
            configs.put(fileName.replace(".yml", ""), config);
        }
    }

    public FileConfiguration getConfig(String name) {
        return configs.get(name);
    }

    public void saveConfig(String name) {
        FileConfiguration config = configs.get(name);
        if (config != null) {
            try {
                config.save(new File(plugin.getDataFolder(), name + ".yml"));
            } catch (IOException e) {
                plugin.getLogger().severe("Could not save " + name + ".yml!");
                e.printStackTrace();
            }
        }
    }

    public void reload() {
        configs.clear();
        loadConfigs();
    }

    public String getString(String path, String defaultValue) {
        return plugin.getConfig().getString(path, defaultValue);
    }

    public int getInt(String path, int defaultValue) {
        return plugin.getConfig().getInt(path, defaultValue);
    }

    public boolean getBoolean(String path, boolean defaultValue) {
        return plugin.getConfig().getBoolean(path, defaultValue);
    }
}