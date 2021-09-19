package com.cavetale.watchman;

import java.util.List;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;

@RequiredArgsConstructor
public final class WatchmanToolCommand implements TabExecutor {
    protected  final WatchmanPlugin plugin;

    public void enable() {
        plugin.getCommand("wmtool").setExecutor(this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 0) return false;
        if (!(sender instanceof Player)) {
            sender.sendMessage("[watchman:wmtool] player expected");
            return true;
        }
        Player player = (Player) sender;
        boolean hasTool = player.hasMetadata(Meta.TOOL_KEY);
        if (hasTool) {
            player.removeMetadata(Meta.TOOL_KEY, plugin);
            sender.sendMessage(Component.text("Watchman tool disabled", NamedTextColor.RED));
        } else {
            player.setMetadata(Meta.TOOL_KEY, new FixedMetadataValue(plugin, true));
            player.sendMessage(Component.text("Watchman tool enabled", NamedTextColor.GREEN));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command comand, String alias, String[] args) {
        return List.of();
    }
}
