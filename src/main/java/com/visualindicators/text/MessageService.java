package com.visualindicators.text;

import com.visualindicators.VisualIndicatorsPlugin;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public final class MessageService {
    private final VisualIndicatorsPlugin plugin;
    private final Map<String, String> messages = new HashMap<>();

    public MessageService(VisualIndicatorsPlugin plugin) {
        this.plugin = plugin;
    }

    public void load(String language) {
        this.messages.clear();
        File langFolder = new File(this.plugin.getDataFolder(), "lang");
        if (!langFolder.exists()) {
            langFolder.mkdirs();
        }

        File languageFile = new File(langFolder, language + ".yml");
        if (!languageFile.exists() && this.plugin.getResource("lang/" + language + ".yml") != null) {
            this.plugin.saveResource("lang/" + language + ".yml", false);
        }

        YamlConfiguration config;
        if (languageFile.exists()) {
            config = YamlConfiguration.loadConfiguration(languageFile);
        } else {
            InputStream stream = this.plugin.getResource("lang/en.yml");
            if (stream == null) {
                return;
            }
            config = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }

        loadSection(config, "");
    }

    private void loadSection(ConfigurationSection section, String path) {
        for (String key : section.getKeys(false)) {
            String fullPath = path.isEmpty() ? key : path + "." + key;
            if (section.isConfigurationSection(key)) {
                loadSection(section.getConfigurationSection(key), fullPath);
                continue;
            }
            this.messages.put(fullPath, section.getString(key, fullPath));
        }
    }

    public String get(String key) {
        return colorize(this.messages.getOrDefault(key, key));
    }

    public String get(String key, Map<String, String> placeholders) {
        String message = this.messages.getOrDefault(key, key);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            message = message.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return colorize(message);
    }

    private String colorize(String input) {
        return ChatColor.translateAlternateColorCodes('&', input);
    }
}
