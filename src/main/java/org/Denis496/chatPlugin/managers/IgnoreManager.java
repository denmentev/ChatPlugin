package org.Denis496.chatPlugin.managers;

import org.Denis496.chatPlugin.ChatPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class IgnoreManager {

    private final ChatPlugin plugin;
    private final ConcurrentHashMap<UUID, Set<UUID>> ignoreList;
    private File dataFile;
    private FileConfiguration dataConfig;

    public IgnoreManager(ChatPlugin plugin) {
        this.plugin = plugin;
        this.ignoreList = new ConcurrentHashMap<>();
        loadData();
    }

    private void loadData() {
        dataFile = new File(plugin.getDataFolder(), "ignorelist.yml");
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to create ignorelist.yml: " + e.getMessage());
            }
        }

        dataConfig = YamlConfiguration.loadConfiguration(dataFile);

        for (String key : dataConfig.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                List<String> ignoredList = dataConfig.getStringList(key);
                Set<UUID> ignoredSet = Collections.newSetFromMap(new ConcurrentHashMap<>());

                for (String ignoredStr : ignoredList) {
                    try {
                        ignoredSet.add(UUID.fromString(ignoredStr));
                    } catch (Exception ignored) {}
                }

                if (!ignoredSet.isEmpty()) {
                    ignoreList.put(uuid, ignoredSet);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Invalid ignore list entry: " + key);
            }
        }
    }

    public void saveData() {
        for (UUID uuid : ignoreList.keySet()) {
            List<String> ignoredList = new ArrayList<>();
            for (UUID ignored : ignoreList.get(uuid)) {
                ignoredList.add(ignored.toString());
            }
            dataConfig.set(uuid.toString(), ignoredList);
        }

        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save ignorelist.yml: " + e.getMessage());
        }
    }

    public boolean isIgnoring(Player player, Player target) {
        Set<UUID> ignored = ignoreList.get(player.getUniqueId());
        if (ignored == null) {
            return false;
        }
        return ignored.contains(target.getUniqueId());
    }

    public void addIgnore(Player player, Player target) {
        ignoreList.computeIfAbsent(player.getUniqueId(),
                        k -> Collections.newSetFromMap(new ConcurrentHashMap<>()))
                .add(target.getUniqueId());
        saveData();
    }

    public void removeIgnore(Player player, Player target) {
        Set<UUID> ignored = ignoreList.get(player.getUniqueId());
        if (ignored != null) {
            ignored.remove(target.getUniqueId());
            if (ignored.isEmpty()) {
                ignoreList.remove(player.getUniqueId());
            }
            saveData();
        }
    }

    public Set<UUID> getIgnoredPlayers(Player player) {
        return ignoreList.getOrDefault(player.getUniqueId(), Collections.emptySet());
    }

    public void clearPlayerData(UUID uuid) {
        ignoreList.remove(uuid);
    }

    public void reload() {
        saveData();
        ignoreList.clear();
        loadData();
    }

    public void shutdown() {
        saveData();
    }
}