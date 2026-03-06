package com.visualindicators.storage;

import com.visualindicators.VisualIndicatorsPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PlayerPreferencesStore {
    private final VisualIndicatorsPlugin plugin;
    private final File file;
    private final Map<UUID, PreferenceState> states = new HashMap<>();

    public PlayerPreferencesStore(VisualIndicatorsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(this.plugin.getDataFolder(), "player-preferences.yml");
        load();
    }

    public boolean isCombatEnabled(UUID playerId) {
        return this.states.getOrDefault(playerId, PreferenceState.enabled()).combatEnabled();
    }

    public boolean isXpEnabled(UUID playerId) {
        return this.states.getOrDefault(playerId, PreferenceState.enabled()).xpEnabled();
    }

    public boolean toggle(UUID playerId, PreferenceChannel channel) {
        PreferenceState current = this.states.getOrDefault(playerId, PreferenceState.enabled());
        PreferenceState updated;
        if (channel == PreferenceChannel.COMBAT) {
            updated = new PreferenceState(!current.combatEnabled(), current.xpEnabled());
        } else if (channel == PreferenceChannel.XP) {
            updated = new PreferenceState(current.combatEnabled(), !current.xpEnabled());
        } else {
            boolean enableAll = !(current.combatEnabled() && current.xpEnabled());
            updated = new PreferenceState(enableAll, enableAll);
        }
        this.states.put(playerId, updated);
        save();
        return channel == PreferenceChannel.XP ? updated.xpEnabled() : channel == PreferenceChannel.COMBAT ? updated.combatEnabled() : updated.combatEnabled() && updated.xpEnabled();
    }

    private void load() {
        this.states.clear();
        if (!this.file.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(this.file);
        ConfigurationSection players = config.getConfigurationSection("players");
        if (players == null) {
            return;
        }
        for (String key : players.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                boolean combat = players.getBoolean(key + ".combat", true);
                boolean xp = players.getBoolean(key + ".xp", true);
                this.states.put(uuid, new PreferenceState(combat, xp));
            } catch (IllegalArgumentException exception) {
                this.plugin.getLogger().warning("Invalid player UUID in preferences: " + key);
            }
        }
    }

    private void save() {
        YamlConfiguration config = new YamlConfiguration();
        for (Map.Entry<UUID, PreferenceState> entry : this.states.entrySet()) {
            String path = "players." + entry.getKey();
            config.set(path + ".combat", entry.getValue().combatEnabled());
            config.set(path + ".xp", entry.getValue().xpEnabled());
        }
        try {
            config.save(this.file);
        } catch (IOException exception) {
            this.plugin.getLogger().warning("Failed to save player preferences: " + exception.getMessage());
        }
    }

    private record PreferenceState(boolean combatEnabled, boolean xpEnabled) {
        private static PreferenceState enabled() {
            return new PreferenceState(true, true);
        }
    }
}
