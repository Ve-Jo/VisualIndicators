package com.visualindicators;

import com.visualindicators.command.VisualIndicatorsCommand;
import com.visualindicators.config.PluginSettings;
import com.visualindicators.indicator.IndicatorService;
import com.visualindicators.listener.ChatHeadListener;
import com.visualindicators.listener.CombatIndicatorListener;
import com.visualindicators.multimine.MultiMineService;
import com.visualindicators.storage.PlayerPreferencesStore;
import com.visualindicators.text.MessageService;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Objects;

public final class VisualIndicatorsPlugin extends JavaPlugin {
    private PluginSettings settings;
    private MessageService messages;
    private PlayerPreferencesStore preferencesStore;
    private IndicatorService indicatorService;
    private MultiMineService multiMineService;
    private BukkitTask tickTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("lang/en.yml", false);

        this.settings = new PluginSettings(this);
        this.settings.reload();
        this.messages = new MessageService(this);
        this.messages.load(this.settings.language());
        this.preferencesStore = new PlayerPreferencesStore(this);
        this.indicatorService = new IndicatorService(this, this.settings);
        this.multiMineService = new MultiMineService(this, this.settings);
        int cleanedDisplays = this.indicatorService.cleanupStaleDisplays();
        if (cleanedDisplays > 0) {
            getLogger().info("Removed " + cleanedDisplays + " stale VisualIndicators holograms on startup.");
        }

        registerListeners();
        registerCommands();

        this.tickTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            this.indicatorService.tick();
            this.multiMineService.tick();
        }, 1L, 1L);
    }

    @Override
    public void onDisable() {
        if (this.tickTask != null) {
            this.tickTask.cancel();
            this.tickTask = null;
        }
        if (this.indicatorService != null) {
            this.indicatorService.shutdown();
        }
        if (this.multiMineService != null) {
            this.multiMineService.clearAllState();
        }
    }

    public void reloadPlugin() {
        this.settings.reload();
        this.messages.load(this.settings.language());
    }

    private void registerListeners() {
        PluginManager pluginManager = Bukkit.getPluginManager();
        pluginManager.registerEvents(new CombatIndicatorListener(this), this);
        pluginManager.registerEvents(new ChatHeadListener(this), this);
        pluginManager.registerEvents(this.multiMineService, this);
        registerAuraSkillsListener(pluginManager);
    }

    private void registerCommands() {
        PluginCommand command = Objects.requireNonNull(getCommand("visualindicators"), "visualindicators command is not defined");
        VisualIndicatorsCommand executor = new VisualIndicatorsCommand(this);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    private void registerAuraSkillsListener(PluginManager pluginManager) {
        if (pluginManager.getPlugin("AuraSkills") == null) {
            getLogger().info("AuraSkills not found; XP indicators are disabled.");
            return;
        }
        try {
            Class<?> listenerClass = Class.forName("com.visualindicators.integration.AuraSkillsXpListener", true, getClassLoader());
            Object listener = listenerClass.getConstructor(VisualIndicatorsPlugin.class).newInstance(this);
            pluginManager.registerEvents((org.bukkit.event.Listener) listener, this);
            getLogger().info("AuraSkills integration enabled.");
        } catch (Throwable throwable) {
            getLogger().warning("Failed to enable AuraSkills integration: " + throwable.getMessage());
        }
    }

    public PluginSettings settings() {
        return this.settings;
    }

    public MessageService messages() {
        return this.messages;
    }

    public PlayerPreferencesStore preferencesStore() {
        return this.preferencesStore;
    }

    public IndicatorService indicatorService() {
        return this.indicatorService;
    }

    public MultiMineService multiMineService() {
        return this.multiMineService;
    }
}
