package com.visualindicators.integration;

import com.visualindicators.VisualIndicatorsPlugin;
import dev.aurelium.auraskills.api.event.skill.DamageXpGainEvent;
import dev.aurelium.auraskills.api.event.skill.EntityXpGainEvent;
import dev.aurelium.auraskills.api.event.skill.XpGainEvent;
import dev.aurelium.auraskills.api.skill.Skill;
import dev.aurelium.auraskills.api.source.XpSource;
import dev.aurelium.auraskills.api.source.type.BlockXpSource;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AuraSkillsXpListener implements Listener {
    private final VisualIndicatorsPlugin plugin;
    private final Map<UUID, TimedLocation> recentBlockBreaks = new ConcurrentHashMap<>();
    private static final Map<String, String> RUSSIAN_SKILL_NAMES = Map.ofEntries(
            Map.entry("archery", "Стрельба"),
            Map.entry("fighting", "Бой"),
            Map.entry("defense", "Защита"),
            Map.entry("endurance", "Выносливость"),
            Map.entry("farming", "Фермерство"),
            Map.entry("foraging", "Лесорубство"),
            Map.entry("mining", "Горное дело"),
            Map.entry("fishing", "Рыбалка"),
            Map.entry("excavation", "Раскопки"),
            Map.entry("agility", "Ловкость"),
            Map.entry("alchemy", "Алхимия"),
            Map.entry("enchanting", "Зачарование"),
            Map.entry("sorcery", "Колдовство"),
            Map.entry("healing", "Исцеление"),
            Map.entry("forging", "Кузнечное дело")
    );

    public AuraSkillsXpListener(VisualIndicatorsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        this.recentBlockBreaks.put(
                event.getPlayer().getUniqueId(),
                new TimedLocation(event.getBlock().getLocation().add(0.5D, 0.5D, 0.5D), System.currentTimeMillis())
        );
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onXpGain(XpGainEvent event) {
        if (!this.plugin.settings().xp().enabled()) {
            return;
        }
        Player player = event.getPlayer();
        if (!this.plugin.preferencesStore().isXpEnabled(player.getUniqueId())) {
            return;
        }
        if (event.getAmount() <= 0.0D) {
            return;
        }

        Locale locale = this.plugin.settings().xp().locale();
        String skillName = resolveSkillName(event.getSkill(), locale);
        XpSource source = event.getSource();
        String sourceName = resolveSourceName(source, locale);
        String skillKey = event.getSkill().name().toLowerCase(Locale.ENGLISH);
        Location location = resolveLocation(event, player);

        this.plugin.indicatorService().spawnXpIndicator(player, location, event.getAmount(), skillName, sourceName, skillKey);
    }

    private String resolveSkillName(Skill skill, Locale locale) {
        String translated = sanitizeLabel(skill.getDisplayName(locale));
        if (!translated.isBlank() && !looksLikeRawEnglishSkillName(skill.name(), translated, locale)) {
            return translated;
        }
        if (isRussianLocale(locale)) {
            String fallback = RUSSIAN_SKILL_NAMES.get(skill.name().toLowerCase(Locale.ENGLISH));
            if (fallback != null) {
                return fallback;
            }
        }
        return toReadableName(skill.name());
    }

    private String resolveSourceName(XpSource source, Locale locale) {
        if (source == null) {
            return "";
        }
        String translated = sanitizeLabel(source.getDisplayName(locale));
        if (!translated.isBlank()) {
            return translated;
        }
        return toReadableName(source.name());
    }

    private String sanitizeLabel(String input) {
        if (input == null) {
            return "";
        }
        return input
                .replace("!!REMOVE!!", "")
                .replace("&", "")
                .replaceAll("<[^>]+>", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private boolean looksLikeRawEnglishSkillName(String skillKey, String displayName, Locale locale) {
        if (!isRussianLocale(locale)) {
            return false;
        }
        return toReadableName(skillKey).equalsIgnoreCase(displayName.trim());
    }

    private boolean isRussianLocale(Locale locale) {
        return "ru".equalsIgnoreCase(locale.getLanguage());
    }

    private String toReadableName(String key) {
        String[] parts = key.toLowerCase(Locale.ENGLISH).split("[_\\-]");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.toString();
    }

    private Location resolveLocation(XpGainEvent event, Player player) {
        if (event instanceof EntityXpGainEvent entityXpGainEvent) {
            return entityXpGainEvent.getAttacked().getLocation();
        }
        if (event instanceof DamageXpGainEvent damageXpGainEvent && damageXpGainEvent.getDamager() != null) {
            return damageXpGainEvent.getDamager().getLocation();
        }
        if (event.getSource() instanceof BlockXpSource) {
            TimedLocation timedLocation = this.recentBlockBreaks.get(player.getUniqueId());
            if (timedLocation != null && System.currentTimeMillis() - timedLocation.timestamp <= 3000L) {
                return timedLocation.location.clone();
            }
        }
        return player.getLocation();
    }

    private record TimedLocation(Location location, long timestamp) {
    }
}
