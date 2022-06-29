package com.cavetale.watchman;

import com.cavetale.core.command.AbstractCommand;
import com.cavetale.core.command.CommandArgCompleter;
import org.bukkit.entity.Player;

public final class WatchmanPageCommand extends AbstractCommand<WatchmanPlugin> {
    protected WatchmanPageCommand(final WatchmanPlugin plugin) {
        super(plugin, "wmpage");
    }

    @Override
    protected void onEnable() {
        rootNode.arguments("<page>")
            .description("View Page")
            .completers(CommandArgCompleter.integer(i -> i >= 0))
            .playerCaller(this::page);
    }

    private boolean page(Player player, String[] args) {
        if (args.length != 1) return false;
        return page(player, args[0]);
    }

    protected boolean page(Player player, String arg) {
        plugin.sessions.showPage(player, CommandArgCompleter.requireInt(arg, i -> i > 0) - 1);
        return true;
    }
}
