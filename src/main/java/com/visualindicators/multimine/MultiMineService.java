package com.visualindicators.multimine;

import com.visualindicators.VisualIndicatorsPlugin;
import com.visualindicators.config.PluginSettings;
import io.papermc.paper.event.block.BlockBreakProgressUpdateEvent;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageAbortEvent;
import org.bukkit.event.block.BlockDamageEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public final class MultiMineService implements Listener {
    private final VisualIndicatorsPlugin plugin;
    private final PluginSettings settings;
    private final Map<BlockKey, TrackedBlock> trackedBlocks = new HashMap<>();
    private final AtomicInteger sourceIds = new AtomicInteger(100_000);
    private long tick;

    public MultiMineService(VisualIndicatorsPlugin plugin, PluginSettings settings) {
        this.plugin = plugin;
        this.settings = settings;
    }

    @EventHandler
    public void onBlockDamage(BlockDamageEvent event) {
        PluginSettings.MultiMineSettings multiMine = this.settings.multiMine();
        if (!multiMine.enabled()) {
            return;
        }
        if (multiMine.ignoreInstaBreak() && event.getInstaBreak()) {
            return;
        }
        TrackedBlock trackedBlock = getOrCreate(event.getBlock());
        trackedBlock.entityProgress.putIfAbsent(event.getPlayer().getUniqueId(), 0.0F);
        trackedBlock.fading = false;
        trackedBlock.nextFadeTick = -1L;
    }

    @EventHandler
    public void onBlockBreakProgressUpdate(BlockBreakProgressUpdateEvent event) {
        PluginSettings.MultiMineSettings multiMine = this.settings.multiMine();
        if (!multiMine.enabled()) {
            return;
        }
        if (event.getProgress() <= 0.0F) {
            return;
        }

        TrackedBlock trackedBlock = getOrCreate(event.getBlock());
        UUID entityId = event.getEntity().getUniqueId();
        float previousProgress = trackedBlock.entityProgress.getOrDefault(entityId, 0.0F);
        float increment = event.getProgress() - previousProgress;
        if (increment <= 0.0F) {
            return;
        }

        trackedBlock.damage += increment;
        trackedBlock.entityProgress.put(entityId, event.getProgress());
        trackedBlock.fading = false;
        trackedBlock.nextFadeTick = -1L;

        if (trackedBlock.damage >= 1.0F) {
            breakTrackedBlock(event.getEntity(), trackedBlock);
            return;
        }
        sendProgress(trackedBlock, normalizeProgress(trackedBlock.damage));
    }

    @EventHandler
    public void onBlockDamageAbort(BlockDamageAbortEvent event) {
        if (!this.settings.multiMine().enabled()) {
            return;
        }
        TrackedBlock trackedBlock = this.trackedBlocks.get(BlockKey.from(event.getBlock()));
        if (trackedBlock == null) {
            return;
        }
        trackedBlock.entityProgress.remove(event.getPlayer().getUniqueId());
        if (trackedBlock.entityProgress.isEmpty()) {
            trackedBlock.fading = true;
            trackedBlock.nextFadeTick = this.tick + this.settings.multiMine().fadeStartDelay();
        }
        sendProgress(trackedBlock, normalizeProgress(trackedBlock.damage));
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!this.settings.multiMine().enabled()) {
            return;
        }
        if (this.settings.multiMine().resetAllOnBreak()) {
            clearAllState();
            return;
        }
        removeTracked(event.getBlock());
    }

    public void tick() {
        this.tick++;
        if (!this.settings.multiMine().enabled()) {
            return;
        }
        Iterator<TrackedBlock> iterator = this.trackedBlocks.values().iterator();
        while (iterator.hasNext()) {
            TrackedBlock trackedBlock = iterator.next();
            if (!trackedBlock.fading || trackedBlock.nextFadeTick < 0L || this.tick < trackedBlock.nextFadeTick) {
                continue;
            }
            trackedBlock.damage -= this.settings.multiMine().fadeAmount();
            if (trackedBlock.damage <= 0.0F) {
                resetVisualState(trackedBlock);
                iterator.remove();
                continue;
            }
            trackedBlock.nextFadeTick = this.tick + this.settings.multiMine().fadeInterval();
            sendProgress(trackedBlock, normalizeProgress(trackedBlock.damage));
        }
    }

    public void clearAllState() {
        for (TrackedBlock trackedBlock : this.trackedBlocks.values()) {
            resetVisualState(trackedBlock);
        }
        this.trackedBlocks.clear();
    }

    private void breakTrackedBlock(Entity entity, TrackedBlock trackedBlock) {
        Block block = trackedBlock.block;
        boolean broken = false;
        if (entity instanceof Player player) {
            broken = player.breakBlock(block);
        } else {
            broken = block.breakNaturally();
        }
        if (!broken) {
            sendProgress(trackedBlock, normalizeProgress(trackedBlock.damage));
        }
    }

    private TrackedBlock getOrCreate(Block block) {
        return this.trackedBlocks.computeIfAbsent(BlockKey.from(block), key -> new TrackedBlock(block, this.sourceIds.incrementAndGet()));
    }

    private void removeTracked(Block block) {
        TrackedBlock trackedBlock = this.trackedBlocks.remove(BlockKey.from(block));
        if (trackedBlock != null) {
            resetVisualState(trackedBlock);
        }
    }

    private void sendProgress(TrackedBlock trackedBlock, float progress) {
        Location location = trackedBlock.block.getLocation();
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        double maxDistanceSquared = 48.0D * 48.0D;
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(location) <= maxDistanceSquared) {
                player.sendBlockDamage(location, progress, trackedBlock.sourceId);
            }
        }
    }

    private void resetVisualState(TrackedBlock trackedBlock) {
        sendProgress(trackedBlock, 0.0F);
    }

    private float normalizeProgress(float damage) {
        return Math.max(0.0F, Math.min(1.0F, damage));
    }

    private static final class TrackedBlock {
        private final Block block;
        private final int sourceId;
        private final Map<UUID, Float> entityProgress = new HashMap<>();
        private float damage;
        private boolean fading;
        private long nextFadeTick = -1L;

        private TrackedBlock(Block block, int sourceId) {
            this.block = block;
            this.sourceId = sourceId;
        }
    }

    private record BlockKey(UUID worldId, int x, int y, int z) {
        private static BlockKey from(Block block) {
            return new BlockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        }
    }
}
