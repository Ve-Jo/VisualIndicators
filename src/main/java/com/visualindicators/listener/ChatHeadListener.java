package com.visualindicators.listener;

import com.visualindicators.VisualIndicatorsPlugin;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public final class ChatHeadListener implements Listener {
    private final VisualIndicatorsPlugin plugin;
    private final PlainTextComponentSerializer plainText = PlainTextComponentSerializer.plainText();

    public ChatHeadListener(VisualIndicatorsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (!this.plugin.settings().chat().enabled()) {
            return;
        }
        Player player = event.getPlayer();
        String message = this.plainText.serialize(event.message()).trim();
        if (message.isEmpty()) {
            return;
        }
        Bukkit.getScheduler().runTask(this.plugin, () -> this.plugin.indicatorService().spawnChatIndicator(player, message));
    }
}
