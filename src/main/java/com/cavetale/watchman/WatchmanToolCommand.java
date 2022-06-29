package com.cavetale.watchman;

import java.util.List;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

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
        toggle(player);
        return true;
    }

    protected void toggle(Player player) {
        boolean hasTool = plugin.sessions.wmtool(player);
        if (hasTool) {
            plugin.sessions.wmtool(player, false);
            player.sendMessage(Component.text("Watchman tool disabled", NamedTextColor.RED));
        } else {
            plugin.sessions.wmtool(player, true);
            player.sendMessage(Component.text("Watchman tool enabled", NamedTextColor.AQUA));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command comand, String alias, String[] args) {
        return List.of();
    }
}
