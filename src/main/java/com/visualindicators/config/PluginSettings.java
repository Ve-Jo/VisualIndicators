package com.visualindicators.config;

import com.visualindicators.VisualIndicatorsPlugin;
import org.bukkit.entity.Display;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class PluginSettings {
    private final VisualIndicatorsPlugin plugin;
    private String language;
    private CombatSettings combat;
    private XpSettings xp;
    private ChatSettings chat;
    private MultiMineSettings multiMine;

    public PluginSettings(VisualIndicatorsPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        this.plugin.reloadConfig();
        FileConfiguration config = this.plugin.getConfig();
        this.language = config.getString("language", "en");

        this.combat = new CombatSettings(
                config.getBoolean("combat.enabled", true),
                config.getString("combat.format", "<#ffffff>-{amount}<#ff5555>♥"),
                config.getString("combat.stacked-format", "<#ffffff>-{amount}<#ff5555>♥ <gray>x{count}"),
                config.getInt("combat.display-duration", 30),
                config.getDouble("combat.upward-speed", 0.03D),
                config.getDouble("combat.vertical-offset", 0.6D),
                config.getBoolean("combat.random-offset.enabled", true),
                config.getDouble("combat.random-offset.x", 0.5D),
                config.getDouble("combat.random-offset.y", 0.25D),
                config.getDouble("combat.random-offset.z", 0.5D),
                config.getInt("combat.stack.merge-window-ticks", 12),
                config.getDouble("combat.stack.radius", 2.75D),
                (float) config.getDouble("combat.scale", 1.35D),
                new HashSet<>(config.getStringList("combat.disabled-worlds")),
                config.getBoolean("combat.show-on-players", false),
                config.getBoolean("combat.entity-filter.whitelist-mode", false),
                parseEntityTypes(config.getStringList("combat.entity-filter.entities"))
        );

        String xpLocaleTag = config.getString("xp.locale", "ru-RU");
        this.xp = new XpSettings(
                config.getBoolean("xp.enabled", true),
                config.getString("xp.format", "<#55ff55>+{amount} XP <#ffff55>{skill}"),
                config.getString("xp.stacked-format", "<#55ff55>+{amount} XP <#ffff55>{skill} <gray>x{count}"),
                config.getInt("xp.display-duration", 24),
                config.getDouble("xp.upward-speed", 0.025D),
                config.getDouble("xp.vertical-offset", 1.15D),
                config.getBoolean("xp.random-offset.enabled", false),
                config.getDouble("xp.random-offset.x", 0.25D),
                config.getDouble("xp.random-offset.y", 0.15D),
                config.getDouble("xp.random-offset.z", 0.25D),
                config.getInt("xp.stack.merge-window-ticks", 16),
                config.getDouble("xp.stack.radius", 2.5D),
                (float) config.getDouble("xp.scale", 1.25D),
                Locale.forLanguageTag(xpLocaleTag.replace('_', '-'))
        );

        this.chat = new ChatSettings(
                config.getBoolean("chat.enabled", true),
                config.getInt("chat.symbols-per-line", 30),
                config.getInt("chat.symbols-limit", -1),
                config.getDouble("chat.time-to-exist", 10.0D),
                config.getBoolean("chat.visible-to-sender", true),
                config.getBoolean("chat.scaling-enabled", true),
                config.getDouble("chat.scaling-coefficient", 0.05D),
                config.getDouble("chat.gap-between-messages", 0.3D),
                config.getDouble("chat.gap-above-head", 0.8D),
                config.getString("chat.text-color", "#FFFFFF"),
                config.getBoolean("chat.background-enabled", true),
                config.getString("chat.background-color", "#000000"),
                config.getInt("chat.background-transparency-percentage", 25),
                config.getBoolean("chat.shadowed", true),
                parseBillboard(config.getString("chat.pivot-axis", "VERTICAL")),
                config.getBoolean("chat.placeholderapi-integration", false),
                config.getString("chat.color-placeholder", "%ezcolors_color%"),
                config.getString("chat.line-format", "&[defaultColor]&[colorPlaceholder][message]")
        );

        this.multiMine = new MultiMineSettings(
                config.getBoolean("multimine.enabled", true),
                config.getBoolean("multimine.reset-all-on-break", true),
                config.getBoolean("multimine.ignore-insta-break", true),
                config.getInt("multimine.fade.start-delay", 40),
                config.getInt("multimine.fade.interval", 20),
                (float) config.getDouble("multimine.fade.amount", 0.1D)
        );
    }

    private Set<EntityType> parseEntityTypes(List<String> values) {
        Set<EntityType> types = new HashSet<>();
        for (String value : values) {
            try {
                types.add(EntityType.valueOf(value.toUpperCase(Locale.ENGLISH)));
            } catch (IllegalArgumentException exception) {
                this.plugin.getLogger().warning("Unknown entity type in config: " + value);
            }
        }
        return types;
    }

    private Display.Billboard parseBillboard(String value) {
        try {
            return Display.Billboard.valueOf(value.toUpperCase(Locale.ENGLISH));
        } catch (IllegalArgumentException exception) {
            this.plugin.getLogger().warning("Unknown chat pivot-axis in config: " + value + ", defaulting to VERTICAL");
            return Display.Billboard.VERTICAL;
        }
    }

    public String language() {
        return this.language;
    }

    public CombatSettings combat() {
        return this.combat;
    }

    public XpSettings xp() {
        return this.xp;
    }

    public ChatSettings chat() {
        return this.chat;
    }

    public MultiMineSettings multiMine() {
        return this.multiMine;
    }

    public record CombatSettings(boolean enabled, String format, String stackedFormat, int displayDuration,
                                 double upwardSpeed, double verticalOffset, boolean randomOffsetEnabled,
                                 double randomOffsetX, double randomOffsetY, double randomOffsetZ,
                                 int mergeWindowTicks, double mergeRadius, float scale, Set<String> disabledWorlds,
                                 boolean showOnPlayers, boolean entityFilterWhitelist, Set<EntityType> entityFilter) {
        public boolean worldDisabled(String worldName) {
            return this.disabledWorlds.contains(worldName);
        }

        public boolean shouldShow(EntityType type) {
            if (this.entityFilter.isEmpty()) {
                return true;
            }
            if (this.entityFilterWhitelist) {
                return this.entityFilter.contains(type);
            }
            return !this.entityFilter.contains(type);
        }
    }

    public record XpSettings(boolean enabled, String format, String stackedFormat, int displayDuration,
                             double upwardSpeed, double verticalOffset, boolean randomOffsetEnabled,
                             double randomOffsetX, double randomOffsetY, double randomOffsetZ,
                             int mergeWindowTicks, double mergeRadius, float scale, Locale locale) {
    }

    public record ChatSettings(boolean enabled, int symbolsPerLine, int symbolsLimit, double timeToExist,
                               boolean visibleToSender, boolean scalingEnabled, double scalingCoefficient,
                               double gapBetweenMessages, double gapAboveHead, String textColor,
                               boolean backgroundEnabled, String backgroundColor, int backgroundTransparencyPercentage,
                               boolean shadowed, Display.Billboard pivotAxis, boolean placeholderApiIntegration,
                               String colorPlaceholder, String lineFormat) {
    }

    public record MultiMineSettings(boolean enabled, boolean resetAllOnBreak, boolean ignoreInstaBreak,
                                    int fadeStartDelay, int fadeInterval, float fadeAmount) {
    }
}
