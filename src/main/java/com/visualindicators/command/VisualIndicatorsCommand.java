package com.visualindicators.command;

import com.visualindicators.VisualIndicatorsPlugin;
import com.visualindicators.storage.PreferenceChannel;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class VisualIndicatorsCommand implements CommandExecutor, TabCompleter {
    private final VisualIndicatorsPlugin plugin;

    public VisualIndicatorsCommand(VisualIndicatorsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(this.plugin.messages().get("command.usage"));
            return true;
        }

        String subcommand = args[0].toLowerCase(Locale.ENGLISH);
        if (subcommand.equals("reload")) {
            if (!sender.hasPermission("visualindicators.reload")) {
                sender.sendMessage(this.plugin.messages().get("command.no-permission"));
                return true;
            }
            this.plugin.reloadPlugin();
            sender.sendMessage(this.plugin.messages().get("command.reload-success"));
            return true;
        }

        if (subcommand.equals("toggle")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(this.plugin.messages().get("command.player-only"));
                return true;
            }
            if (!sender.hasPermission("visualindicators.toggle")) {
                sender.sendMessage(this.plugin.messages().get("command.no-permission"));
                return true;
            }

            PreferenceChannel channel = parseChannel(args.length > 1 ? args[1] : "all");
            if (channel == null) {
                sender.sendMessage(this.plugin.messages().get("command.usage"));
                return true;
            }

            boolean enabled = this.plugin.preferencesStore().toggle(player.getUniqueId(), channel);
            String channelName = channel.name().toLowerCase(Locale.ENGLISH);
            sender.sendMessage(this.plugin.messages().get(enabled ? "command.toggle-enabled" : "command.toggle-disabled", Map.of("channel", channelName)));
            return true;
        }

        sender.sendMessage(this.plugin.messages().get("command.usage"));
        return true;
    }

    private PreferenceChannel parseChannel(String input) {
        return switch (input.toLowerCase(Locale.ENGLISH)) {
            case "combat" -> PreferenceChannel.COMBAT;
            case "xp" -> PreferenceChannel.XP;
            case "social" -> PreferenceChannel.SOCIAL;
            case "all" -> PreferenceChannel.ALL;
            default -> null;
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            if (sender.hasPermission("visualindicators.reload") && "reload".startsWith(args[0].toLowerCase(Locale.ENGLISH))) {
                completions.add("reload");
            }
            if (sender.hasPermission("visualindicators.toggle") && "toggle".startsWith(args[0].toLowerCase(Locale.ENGLISH))) {
                completions.add("toggle");
            }
            return completions;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("toggle") && sender.hasPermission("visualindicators.toggle")) {
            for (String option : List.of("all", "combat", "xp", "social")) {
                if (option.startsWith(args[1].toLowerCase(Locale.ENGLISH))) {
                    completions.add(option);
                }
            }
        }
        return completions;
    }
}
