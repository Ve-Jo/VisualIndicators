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

    public boolean isSocialEnabled(UUID playerId) {
        return this.states.getOrDefault(playerId, PreferenceState.enabled()).socialEnabled();
    }

    public boolean toggle(UUID playerId, PreferenceChannel channel) {
        PreferenceState current = this.states.getOrDefault(playerId, PreferenceState.enabled());
        PreferenceState updated;
        if (channel == PreferenceChannel.COMBAT) {
            updated = new PreferenceState(!current.combatEnabled(), current.xpEnabled(), current.socialEnabled());
        } else if (channel == PreferenceChannel.XP) {
            updated = new PreferenceState(current.combatEnabled(), !current.xpEnabled(), current.socialEnabled());
        } else if (channel == PreferenceChannel.SOCIAL) {
            updated = new PreferenceState(current.combatEnabled(), current.xpEnabled(), !current.socialEnabled());
        } else {
            boolean enableAll = !(current.combatEnabled() && current.xpEnabled() && current.socialEnabled());
            updated = new PreferenceState(enableAll, enableAll, enableAll);
        }
        this.states.put(playerId, updated);
        save();
        if (channel == PreferenceChannel.XP) {
            return updated.xpEnabled();
        }
        if (channel == PreferenceChannel.COMBAT) {
            return updated.combatEnabled();
        }
        if (channel == PreferenceChannel.SOCIAL) {
            return updated.socialEnabled();
        }
        return updated.combatEnabled() && updated.xpEnabled() && updated.socialEnabled();
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
                boolean social = players.getBoolean(key + ".social", true);
                this.states.put(uuid, new PreferenceState(combat, xp, social));
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
            config.set(path + ".social", entry.getValue().socialEnabled());
        }
        try {
            config.save(this.file);
        } catch (IOException exception) {
            this.plugin.getLogger().warning("Failed to save player preferences: " + exception.getMessage());
        }
    }

    private record PreferenceState(boolean combatEnabled, boolean xpEnabled, boolean socialEnabled) {
        private static PreferenceState enabled() {
            return new PreferenceState(true, true, true);
        }
    }
}
