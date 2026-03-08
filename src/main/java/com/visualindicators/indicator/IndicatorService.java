package com.visualindicators.indicator;

import com.visualindicators.VisualIndicatorsPlugin;
import com.visualindicators.config.PluginSettings;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataHolder;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;
import java.util.regex.Pattern;

public final class IndicatorService {
    private static final String INDICATOR_TAG = "visualindicators";
    private static final String COMBAT_TAG = "visualindicators:combat";
    private static final String SOCIAL_TAG = "visualindicators:social";
    private static final String CHAT_TAG = "visualindicators:chat";
    private static final int DISPLAY_INTERPOLATION_DURATION = 2;
    private static final int DISPLAY_TELEPORT_DURATION = 1;
    private static final float CHAT_ANCHOR_WIDTH = 0.0F;
    private final VisualIndicatorsPlugin plugin;
    private final PluginSettings settings;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final PlainTextComponentSerializer plainTextSerializer = PlainTextComponentSerializer.plainText();
    private final DecimalFormat decimalFormat = new DecimalFormat("0.#");
    private final Random random = new Random();
    private final List<ActiveIndicator> activeIndicators = new ArrayList<>();
    private static final Color BACKGROUND_COLOR = Color.fromARGB(160, 0, 0, 0);
    private static final Pattern MINI_MESSAGE_TAGS = Pattern.compile("<[^>]+>");
    private static final double CHAT_UPWARD_SPEED = 0.015D;
    private final NamespacedKey indicatorTypeKey;
    private long tick;

    public IndicatorService(VisualIndicatorsPlugin plugin, PluginSettings settings) {
        this.plugin = plugin;
        this.settings = settings;
        this.indicatorTypeKey = new NamespacedKey(plugin, "indicator_type");
    }

    public void spawnCombatIndicator(Player attacker, LivingEntity victim, double amount) {
        PluginSettings.CombatSettings combat = this.settings.combat();
        if (!combat.enabled()) {
            return;
        }
        Location location = baseLocation(victim.getLocation(), victim.getHeight() + combat.verticalOffset(), combat.randomOffsetEnabled(), combat.randomOffsetX(), combat.randomOffsetY(), combat.randomOffsetZ());
        String mergeKey = attacker.getUniqueId() + ":combat";
        upsert(IndicatorType.COMBAT, mergeKey, location, amount, combat.mergeRadius(), combat.mergeWindowTicks(), combat.displayDuration(), combat.upwardSpeed(), combat.scale(), null);
    }

    public void spawnXpIndicator(Player player, Location origin, double amount, String skillName, String skillKey) {
        PluginSettings.XpSettings xp = this.settings.xp();
        if (!xp.enabled()) {
            return;
        }
        Location safeOrigin = origin == null ? player.getLocation() : origin;
        Location location = baseLocation(safeOrigin, xp.verticalOffset(), xp.randomOffsetEnabled(), xp.randomOffsetX(), xp.randomOffsetY(), xp.randomOffsetZ());
        String mergeKey = player.getUniqueId() + ":xp:" + skillKey;
        upsert(IndicatorType.XP, mergeKey, location, amount, xp.mergeRadius(), xp.mergeWindowTicks(), xp.displayDuration(), xp.upwardSpeed(), xp.scale(), skillName);
    }

    public void spawnSocialIndicator(Player actor, Player target, String actionKey) {
        PluginSettings.SocialSettings social = this.settings.social();
        if (!social.enabled()) {
            return;
        }
        if (actor == null || target == null || !actor.isOnline() || !target.isOnline()) {
            return;
        }
        if (!this.plugin.preferencesStore().isSocialEnabled(actor.getUniqueId())) {
            return;
        }
        if (actor.getWorld() == null || target.getWorld() == null || !actor.getWorld().equals(target.getWorld())) {
            return;
        }
        if (social.worldDisabled(actor.getWorld().getName())) {
            return;
        }
        if (social.maxDistance() > 0.0D && actor.getLocation().distanceSquared(target.getLocation()) > social.maxDistance() * social.maxDistance()) {
            return;
        }

        Location midpoint = midpoint(actor, target);
        if (midpoint == null) {
            return;
        }
        double height = Math.max(actor.getHeight(), target.getHeight());
        Location location = baseLocation(midpoint, height + social.verticalOffset(), social.randomOffsetEnabled(), social.randomOffsetX(), social.randomOffsetY(), social.randomOffsetZ());
        String normalizedAction = normalizeSocialAction(actionKey);
        String mergeKey = orderedPairKey(actor.getUniqueId(), target.getUniqueId()) + ":social:" + normalizedAction;
        upsert(IndicatorType.SOCIAL, mergeKey, location, 0.0D, social.mergeRadius(), social.mergeWindowTicks(), social.displayDuration(), social.upwardSpeed(), social.scale(), social.resolveAction(normalizedAction));
    }

    public void spawnChatIndicator(Player player, String rawMessage) {
        PluginSettings.ChatSettings chat = this.settings.chat();
        if (!chat.enabled()) {
            return;
        }
        String formattedMessage = formatChatMessage(rawMessage, chat.symbolsPerLine(), chat.symbolsLimit());
        if (formattedMessage.isBlank()) {
            return;
        }

        int visibleLength = formattedMessage.replace("\n", " ").trim().length();
        double durationSeconds = chat.timeToExist();
        if (chat.scalingEnabled()) {
            durationSeconds += visibleLength * chat.scalingCoefficient();
        }
        int displayDuration = Math.max(20, (int) Math.round(durationSeconds * 20.0D));
        Location location = player.getLocation().add(0.0D, player.getHeight() + chat.gapAboveHead(), 0.0D);

        World world = location.getWorld();
        if (world == null) {
            return;
        }

        Interaction anchor = world.spawn(location, Interaction.class, entity -> {
            entity.setInteractionWidth(CHAT_ANCHOR_WIDTH);
            entity.setInteractionHeight((float) chat.gapAboveHead());
            entity.setInvulnerable(true);
            entity.setGravity(false);
            markEntity(entity, IndicatorType.CHAT);
        });

        TextDisplay display = world.spawn(location, TextDisplay.class, entity -> {
            entity.setBillboard(chat.pivotAxis());
            entity.setAlignment(TextDisplay.TextAlignment.CENTER);
            entity.setSeeThrough(true);
            entity.setShadowed(chat.shadowed());
            entity.setInterpolationDelay(0);
            entity.setInterpolationDuration(DISPLAY_INTERPOLATION_DURATION);
            entity.setTeleportDuration(DISPLAY_TELEPORT_DURATION);
            entity.setDefaultBackground(false);
            entity.setBackgroundColor(chatBackgroundColor());
            entity.text(renderText(IndicatorType.CHAT, 0.0D, 1, formattedMessage));
            markEntity(entity, IndicatorType.CHAT);
            Transformation transformation = entity.getTransformation();
            float initialScale = calculateScale(IndicatorType.CHAT, renderPlainText(IndicatorType.CHAT, 0.0D, 1, formattedMessage), 1.0F);
            transformation.getScale().set(new Vector3f(initialScale, initialScale, initialScale));
            entity.setTransformation(transformation);
            entity.setTextOpacity((byte) 255);
        });

        anchor.addPassenger(display);

        if (!chat.visibleToSender()) {
            player.hideEntity(this.plugin, anchor);
            player.hideEntity(this.plugin, display);
        }

        ActiveIndicator created = new ActiveIndicator(
                IndicatorType.CHAT,
                player.getUniqueId() + ":chat:" + this.tick + ":" + this.activeIndicators.size(),
                display,
                anchor,
                location,
                0.0D,
                1,
                formattedMessage,
                CHAT_UPWARD_SPEED,
                1.0F,
                this.tick,
                this.tick + displayDuration,
                this.tick,
                player.getUniqueId(),
                0
        );
        applyVisualState(created);
        this.activeIndicators.add(created);
        rebuildChatPassengerChains();
    }

    private Location baseLocation(Location location, double yOffset, boolean randomOffsetEnabled, double randomOffsetX, double randomOffsetY, double randomOffsetZ) {
        Location clone = location.clone().add(0.0D, yOffset, 0.0D);
        if (randomOffsetEnabled) {
            clone.add(
                    (this.random.nextDouble() - 0.5D) * randomOffsetX,
                    (this.random.nextDouble() - 0.5D) * randomOffsetY,
                    (this.random.nextDouble() - 0.5D) * randomOffsetZ
            );
        }
        return clone;
    }

    private void upsert(IndicatorType type, String mergeKey, Location location, double amount, double mergeRadius, int mergeWindowTicks, int displayDuration, double upwardSpeed, float scale, String label) {
        ActiveIndicator indicator = findMergeTarget(type, mergeKey, location, mergeRadius, mergeWindowTicks);
        if (indicator != null) {
            indicator.totalAmount += amount;
            indicator.count++;
            indicator.label = label == null ? indicator.label : label;
            indicator.createdAtTick = this.tick;
            indicator.expiresAtTick = this.tick + displayDuration;
            indicator.lastMergeTick = this.tick;
            indicator.location = averageLocation(indicator.location, location);
            indicator.display.teleport(indicator.location);
            indicator.display.text(renderText(indicator));
            applyVisualState(indicator);
            return;
        }

        World world = location.getWorld();
        if (world == null) {
            return;
        }

        TextDisplay display = world.spawn(location, TextDisplay.class, entity -> {
            entity.setBillboard(Display.Billboard.CENTER);
            entity.setAlignment(TextDisplay.TextAlignment.CENTER);
            entity.setSeeThrough(true);
            entity.setInterpolationDelay(0);
            entity.setInterpolationDuration(DISPLAY_INTERPOLATION_DURATION);
            entity.setTeleportDuration(DISPLAY_TELEPORT_DURATION);
            entity.setDefaultBackground(false);
            entity.setBackgroundColor(BACKGROUND_COLOR);
            entity.text(renderText(type, amount, 1, label));
            markDisplay(entity, type);
            Transformation transformation = entity.getTransformation();
            float initialScale = calculateScale(type, renderPlainText(type, amount, 1, label), scale);
            transformation.getScale().set(new Vector3f(initialScale, initialScale, initialScale));
            entity.setTransformation(transformation);
            entity.setTextOpacity((byte) 255);
        });

        ActiveIndicator created = new ActiveIndicator(type, mergeKey, display, null, location, amount, 1, label, upwardSpeed, scale, this.tick, this.tick + displayDuration, this.tick, null, 0);
        applyVisualState(created);
        this.activeIndicators.add(created);
    }

    private ActiveIndicator findMergeTarget(IndicatorType type, String mergeKey, Location location, double mergeRadius, int mergeWindowTicks) {
        double maxDistanceSquared = mergeRadius * mergeRadius;
        for (ActiveIndicator activeIndicator : this.activeIndicators) {
            if (activeIndicator.type != type) {
                continue;
            }
            if (!activeIndicator.mergeKey.equals(mergeKey)) {
                continue;
            }
            if (!activeIndicator.display.isValid()) {
                continue;
            }
            if (activeIndicator.location.getWorld() == null || location.getWorld() == null) {
                continue;
            }
            if (!activeIndicator.location.getWorld().getUID().equals(location.getWorld().getUID())) {
                continue;
            }
            if (this.tick - activeIndicator.lastMergeTick > mergeWindowTicks) {
                continue;
            }
            if (activeIndicator.location.distanceSquared(location) > maxDistanceSquared) {
                continue;
            }
            return activeIndicator;
        }
        return null;
    }

    private Location averageLocation(Location first, Location second) {
        if (first.getWorld() == null || second.getWorld() == null || !first.getWorld().getUID().equals(second.getWorld().getUID())) {
            return second.clone();
        }
        return new Location(
                first.getWorld(),
                (first.getX() + second.getX()) / 2.0D,
                (first.getY() + second.getY()) / 2.0D,
                (first.getZ() + second.getZ()) / 2.0D,
                first.getYaw(),
                first.getPitch()
        );
    }

    private Location midpoint(Player first, Player second) {
        if (first == null || second == null) {
            return null;
        }
        if (first.getWorld() == null || second.getWorld() == null || !first.getWorld().equals(second.getWorld())) {
            return null;
        }
        Location firstLocation = first.getLocation();
        Location secondLocation = second.getLocation();
        return new Location(
                first.getWorld(),
                (firstLocation.getX() + secondLocation.getX()) / 2.0D,
                (firstLocation.getY() + secondLocation.getY()) / 2.0D,
                (firstLocation.getZ() + secondLocation.getZ()) / 2.0D,
                firstLocation.getYaw(),
                firstLocation.getPitch()
        );
    }

    private String normalizeSocialAction(String actionKey) {
        if (actionKey == null || actionKey.isBlank()) {
            return "default";
        }
        return actionKey.toLowerCase(Locale.ENGLISH).replace(' ', '_');
    }

    private String orderedPairKey(UUID first, UUID second) {
        if (first.compareTo(second) <= 0) {
            return first + ":" + second;
        }
        return second + ":" + first;
    }

    private Component renderText(ActiveIndicator indicator) {
        return renderText(indicator.type, indicator.totalAmount, indicator.count, indicator.label);
    }

    private String renderPlainText(ActiveIndicator indicator) {
        return renderPlainText(indicator.type, indicator.totalAmount, indicator.count, indicator.label);
    }

    private Component renderText(IndicatorType type, double amount, int count, String label) {
        if (type == IndicatorType.CHAT) {
            TextColor color = parseTextColor(this.settings.chat().textColor(), "#FFFFFF");
            return Component.text(label == null ? "" : label).color(color).decoration(TextDecoration.ITALIC, false);
        }
        return this.miniMessage.deserialize(renderTemplate(type, amount, count, label));
    }

    private String renderPlainText(IndicatorType type, double amount, int count, String label) {
        if (type == IndicatorType.CHAT) {
            return label == null ? "" : label;
        }
        return MINI_MESSAGE_TAGS.matcher(renderTemplate(type, amount, count, label)).replaceAll("");
    }

    private String renderTemplate(IndicatorType type, double amount, int count, String label) {
        String template;
        if (type == IndicatorType.COMBAT) {
            template = count > 1 ? this.settings.combat().stackedFormat() : this.settings.combat().format();
        } else if (type == IndicatorType.SOCIAL) {
            template = count > 1 ? this.settings.social().stackedFormat() : this.settings.social().format();
        } else {
            template = count > 1 ? this.settings.xp().stackedFormat() : this.settings.xp().format();
        }
        String text = template
                .replace("{amount}", this.decimalFormat.format(amount))
                .replace("{count}", Integer.toString(count))
                .replace("{skill}", label == null ? "" : label)
                .replace("{action}", label == null ? "" : label)
                .replace("{source}", "");
        return text;
    }

    public void tick() {
        this.tick++;
        updateChatStacks();
        Iterator<ActiveIndicator> iterator = this.activeIndicators.iterator();
        while (iterator.hasNext()) {
            ActiveIndicator indicator = iterator.next();
            if (!indicator.display.isValid() || (indicator.anchor != null && !indicator.anchor.isValid())) {
                iterator.remove();
                continue;
            }
            if (this.tick >= indicator.expiresAtTick) {
                removeIndicatorEntities(indicator);
                iterator.remove();
                if (indicator.type == IndicatorType.CHAT) {
                    rebuildChatPassengerChains();
                }
                continue;
            }
            if (indicator.type == IndicatorType.CHAT) {
                applyVisualState(indicator);
                continue;
            }
            Location nextLocation = nextLocation(indicator);
            indicator.location = nextLocation;
            indicator.display.teleport(nextLocation);
            applyVisualState(indicator);
        }
    }

    private void updateChatStacks() {
        List<ActiveIndicator> chatIndicators = new ArrayList<>();
        for (ActiveIndicator indicator : this.activeIndicators) {
            if (indicator.type == IndicatorType.CHAT && indicator.ownerId != null && indicator.display.isValid() && indicator.anchor != null && indicator.anchor.isValid()) {
                chatIndicators.add(indicator);
            }
        }

        List<UUID> processedOwners = new ArrayList<>();
        for (ActiveIndicator indicator : chatIndicators) {
            UUID ownerId = indicator.ownerId;
            if (processedOwners.contains(ownerId)) {
                continue;
            }
            processedOwners.add(ownerId);
            List<ActiveIndicator> ownerIndicators = chatIndicators.stream()
                    .filter(active -> ownerId.equals(active.ownerId))
                    .sorted(Comparator.comparingLong((ActiveIndicator active) -> active.createdAtTick).reversed())
                    .toList();
            for (int index = 0; index < ownerIndicators.size(); index++) {
                ownerIndicators.get(index).stackIndex = index;
            }
        }
        rebuildChatPassengerChains();
    }

    private void rebuildChatPassengerChains() {
        PluginSettings.ChatSettings chat = this.settings.chat();
        List<UUID> processedOwners = new ArrayList<>();
        for (ActiveIndicator indicator : this.activeIndicators) {
            if (indicator.type != IndicatorType.CHAT || indicator.ownerId == null || indicator.anchor == null || !indicator.anchor.isValid() || !indicator.display.isValid()) {
                continue;
            }
            if (processedOwners.contains(indicator.ownerId)) {
                continue;
            }
            processedOwners.add(indicator.ownerId);
            Player owner = Bukkit.getPlayer(indicator.ownerId);
            if (owner == null || !owner.isOnline()) {
                continue;
            }
            List<ActiveIndicator> ownerIndicators = this.activeIndicators.stream()
                    .filter(active -> active.type == IndicatorType.CHAT)
                    .filter(active -> indicator.ownerId.equals(active.ownerId))
                    .filter(active -> active.anchor != null && active.anchor.isValid() && active.display.isValid())
                    .sorted(Comparator.comparingInt((ActiveIndicator active) -> active.stackIndex))
                    .toList();
            for (ActiveIndicator active : ownerIndicators) {
                Entity vehicle = active.anchor.getVehicle();
                if (vehicle != null) {
                    vehicle.removePassenger(active.anchor);
                }
                Entity passengerVehicle = active.display.getVehicle();
                if (passengerVehicle != null) {
                    passengerVehicle.removePassenger(active.display);
                }
                active.anchor.setInteractionWidth(CHAT_ANCHOR_WIDTH);
                active.anchor.setInteractionHeight(active.stackIndex == 0 ? (float) chat.gapAboveHead() : (float) chat.gapBetweenMessages());
                active.anchor.setGravity(false);
                active.anchor.setInvulnerable(true);
                active.anchor.addPassenger(active.display);
            }
            Entity seat = owner;
            for (ActiveIndicator active : ownerIndicators) {
                seat.addPassenger(active.anchor);
                seat = active.display;
            }
        }
    }

    private Location nextLocation(ActiveIndicator indicator) {
        if (indicator.type != IndicatorType.CHAT || indicator.ownerId == null) {
            return indicator.display.getLocation().add(0.0D, indicator.upwardSpeed, 0.0D);
        }
        return indicator.display.getLocation();
    }

    private void applyVisualState(ActiveIndicator indicator) {
        long totalLifetime = Math.max(1L, indicator.expiresAtTick - indicator.createdAtTick);
        long remainingTicks = Math.max(0L, indicator.expiresAtTick - this.tick);
        double progress = Math.max(0.0D, Math.min(1.0D, (double) remainingTicks / (double) totalLifetime));
        int opacity = (int) Math.round(progress * 255.0D);
        indicator.display.setTextOpacity((byte) Math.max(0, Math.min(255, opacity)));
        indicator.display.setBackgroundColor(backgroundColor(indicator));
        updateScale(indicator);
    }

    private Color backgroundColor(ActiveIndicator indicator) {
        if (indicator.type == IndicatorType.CHAT) {
            return chatBackgroundColor();
        }
        return BACKGROUND_COLOR;
    }

    private Color chatBackgroundColor() {
        PluginSettings.ChatSettings chat = this.settings.chat();
        if (!chat.backgroundEnabled()) {
            return Color.fromARGB(0, 0, 0, 0);
        }
        Color base = parseBukkitColor(chat.backgroundColor(), "#000000");
        int alpha = (int) Math.round(255.0D * (100.0D - Math.max(0, Math.min(100, chat.backgroundTransparencyPercentage()))) / 100.0D);
        return Color.fromARGB(alpha, base.getRed(), base.getGreen(), base.getBlue());
    }

    private void updateScale(ActiveIndicator indicator) {
        float adjustedScale = calculateScale(indicator.type, renderPlainText(indicator), indicator.scale);
        Transformation transformation = indicator.display.getTransformation();
        transformation.getScale().set(new Vector3f(adjustedScale, adjustedScale, adjustedScale));
        indicator.display.setTransformation(transformation);
    }

    private float calculateScale(IndicatorType type, String plainText, float baseScale) {
        String compact = plainText == null ? "" : plainText.replaceAll("\\s+", " ").trim();
        int length = compact.length();
        if (length <= 0) {
            return baseScale;
        }

        if (type == IndicatorType.COMBAT) {
            if (length <= 12) {
                return baseScale;
            }
            float factor = Math.max(0.78F, 1.0F - (length - 12) * 0.022F);
            return baseScale * factor;
        }

        if (type == IndicatorType.CHAT) {
            if (length <= 20) {
                return baseScale;
            }
            float factor = Math.max(0.45F, 1.0F - (length - 20) * 0.015F);
            return baseScale * factor;
        }

        if (type == IndicatorType.SOCIAL) {
            if (length <= 24) {
                return baseScale;
            }
            float factor = Math.max(0.6F, 1.0F - (length - 24) * 0.016F);
            return baseScale * factor;
        }

        if (length <= 18) {
            return baseScale;
        }
        float factor = Math.max(0.58F, 1.0F - (length - 18) * 0.018F);
        return baseScale * factor;
    }

    private String formatChatMessage(String message, int symbolsPerLine, int symbolsLimit) {
        String normalized = message == null ? "" : message.replaceAll("\\s+", " ").trim();
        if (normalized.isEmpty()) {
            return "";
        }
        if (symbolsLimit >= 0 && normalized.length() > symbolsLimit) {
            int limit = Math.max(0, symbolsLimit);
            normalized = limit <= 3 ? normalized.substring(0, limit) : normalized.substring(0, limit - 3) + "...";
        }
        if (symbolsPerLine <= 0 || normalized.length() <= symbolsPerLine) {
            return normalized;
        }

        StringBuilder builder = new StringBuilder();
        int lineLength = 0;
        for (String word : normalized.split(" ")) {
            if (word.isEmpty()) {
                continue;
            }
            int additional = lineLength == 0 ? word.length() : word.length() + 1;
            if (lineLength > 0 && lineLength + additional > symbolsPerLine) {
                builder.append('\n');
                builder.append(word);
                lineLength = word.length();
                continue;
            }
            if (lineLength > 0) {
                builder.append(' ');
            }
            builder.append(word);
            lineLength += additional;
        }
        return builder.toString();
    }

    public int cleanupStaleDisplays() {
        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntitiesByClass(TextDisplay.class)) {
                TextDisplay display = (TextDisplay) entity;
                if (!shouldCleanup(display)) {
                    continue;
                }
                display.remove();
                removed++;
            }
            for (Entity entity : world.getEntitiesByClass(Interaction.class)) {
                Interaction interaction = (Interaction) entity;
                if (!shouldCleanup(interaction)) {
                    continue;
                }
                interaction.remove();
                removed++;
            }
        }
        return removed;
    }

    private void markDisplay(TextDisplay display, IndicatorType type) {
        display.addScoreboardTag(INDICATOR_TAG);
        if (type == IndicatorType.CHAT) {
            display.addScoreboardTag(CHAT_TAG);
        } else if (type == IndicatorType.COMBAT) {
            display.addScoreboardTag(COMBAT_TAG);
        } else if (type == IndicatorType.SOCIAL) {
            display.addScoreboardTag(SOCIAL_TAG);
        }
        display.getPersistentDataContainer().set(this.indicatorTypeKey, PersistentDataType.STRING, type.name());
    }

    private void markEntity(PersistentDataHolder entity, IndicatorType type) {
        if (entity instanceof Entity bukkitEntity) {
            bukkitEntity.addScoreboardTag(INDICATOR_TAG);
            if (type == IndicatorType.CHAT) {
                bukkitEntity.addScoreboardTag(CHAT_TAG);
            } else if (type == IndicatorType.COMBAT) {
                bukkitEntity.addScoreboardTag(COMBAT_TAG);
            } else if (type == IndicatorType.SOCIAL) {
                bukkitEntity.addScoreboardTag(SOCIAL_TAG);
            }
        }
        entity.getPersistentDataContainer().set(this.indicatorTypeKey, PersistentDataType.STRING, type.name());
    }

    private boolean shouldCleanup(Entity entity) {
        if (!(entity instanceof PersistentDataHolder holder)) {
            return false;
        }
        String taggedType = holder.getPersistentDataContainer().get(this.indicatorTypeKey, PersistentDataType.STRING);
        if (taggedType != null) {
            return taggedType.equals(IndicatorType.CHAT.name()) || taggedType.equals(IndicatorType.COMBAT.name()) || taggedType.equals(IndicatorType.SOCIAL.name());
        }
        if (entity.getScoreboardTags().contains(CHAT_TAG) || entity.getScoreboardTags().contains(COMBAT_TAG) || entity.getScoreboardTags().contains(SOCIAL_TAG)) {
            return true;
        }
        return entity instanceof TextDisplay display && (isLegacyCombatDisplay(display) || isLegacyChatDisplay(display));
    }

    private void removeIndicatorEntities(ActiveIndicator indicator) {
        if (indicator.display.isValid()) {
            indicator.display.remove();
        }
        if (indicator.anchor != null && indicator.anchor.isValid()) {
            indicator.anchor.remove();
        }
    }

    private boolean isLegacyCombatDisplay(TextDisplay display) {
        if (display.getBillboard() != Display.Billboard.CENTER) {
            return false;
        }
        if (!display.isSeeThrough() || display.isDefaultBackground()) {
            return false;
        }
        if (!BACKGROUND_COLOR.equals(display.getBackgroundColor())) {
            return false;
        }
        String plainText = plainText(display);
        return plainText.contains("♥");
    }

    private boolean isLegacyChatDisplay(TextDisplay display) {
        PluginSettings.ChatSettings chat = this.settings.chat();
        if (display.getBillboard() != chat.pivotAxis()) {
            return false;
        }
        if (!display.isSeeThrough() || display.isDefaultBackground()) {
            return false;
        }
        if (display.isShadowed() != chat.shadowed()) {
            return false;
        }
        if (!chatBackgroundColor().equals(display.getBackgroundColor())) {
            return false;
        }
        String plainText = plainText(display);
        return !plainText.isBlank() && !plainText.contains("♥") && !plainText.contains("XP");
    }

    private String plainText(TextDisplay display) {
        Component component = display.text();
        if (component == null) {
            return "";
        }
        return this.plainTextSerializer.serialize(component).replace('\n', ' ').trim();
    }

    private TextColor parseTextColor(String input, String fallback) {
        TextColor parsed = TextColor.fromHexString(input);
        if (parsed != null) {
            return parsed;
        }
        TextColor fallbackColor = TextColor.fromHexString(fallback);
        return fallbackColor == null ? TextColor.color(255, 255, 255) : fallbackColor;
    }

    private Color parseBukkitColor(String input, String fallback) {
        String hex = input != null ? input : fallback;
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }
        try {
            int rgb = Integer.parseInt(hex, 16);
            return Color.fromRGB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
        } catch (IllegalArgumentException exception) {
            return Color.fromRGB(0, 0, 0);
        }
    }

    public void shutdown() {
        for (ActiveIndicator indicator : this.activeIndicators) {
            removeIndicatorEntities(indicator);
        }
        this.activeIndicators.clear();
    }

    private static final class ActiveIndicator {
        private final IndicatorType type;
        private final String mergeKey;
        private final TextDisplay display;
        private final Interaction anchor;
        private Location location;
        private double totalAmount;
        private int count;
        private String label;
        private final double upwardSpeed;
        private final float scale;
        private long createdAtTick;
        private long expiresAtTick;
        private long lastMergeTick;
        private final UUID ownerId;
        private int stackIndex;

        private ActiveIndicator(IndicatorType type, String mergeKey, TextDisplay display, Interaction anchor, Location location, double totalAmount,
                                int count, String label, double upwardSpeed, float scale, long createdAtTick, long expiresAtTick,
                                long lastMergeTick, UUID ownerId, int stackIndex) {
            this.type = type;
            this.mergeKey = mergeKey;
            this.display = display;
            this.anchor = anchor;
            this.location = location;
            this.totalAmount = totalAmount;
            this.count = count;
            this.label = label;
            this.upwardSpeed = upwardSpeed;
            this.scale = scale;
            this.createdAtTick = createdAtTick;
            this.expiresAtTick = expiresAtTick;
            this.lastMergeTick = lastMergeTick;
            this.ownerId = ownerId;
            this.stackIndex = stackIndex;
        }
    }
}
