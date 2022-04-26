package com.cavetale.watchman;

import com.cavetale.core.command.AbstractCommand;
import com.cavetale.core.command.CommandArgCompleter;
import com.cavetale.core.command.CommandWarn;
import java.util.List;
import org.bukkit.command.CommandSender;
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
            .senderCaller(this::page);
    }

    private boolean page(CommandSender sender, String[] args) {
        if (args.length != 1) return false;
        return page(sender, args[0]);
    }

    protected boolean page(CommandSender sender, String arg) {
        final int num;
        try {
            num = Integer.parseInt(arg);
        } catch (NumberFormatException nfe) {
            throw new CommandWarn("Invalid page: " + arg);
        }
        page(sender, num);
        return true;
    }

    private void page(CommandSender sender, int num) {
        if (num < 0) {
            throw new CommandWarn("Invalid page: " + num);
        }
        if (sender instanceof Player player) {
            if (!player.hasMetadata(Meta.LOOKUP)) {
                throw new CommandWarn("No records available");
            }
            List<SQLAction> actions = (List<SQLAction>) player.getMetadata(Meta.LOOKUP).get(0).value();
            LookupMeta meta;
            if (!player.hasMetadata(Meta.LOOKUP)) {
                meta = new LookupMeta();
            } else {
                meta = (LookupMeta) player.getMetadata(Meta.LOOKUP_META).get(0).value();
            }
            plugin.showActionPage(player, actions, meta, num - 1);
        } else {
            List<SQLAction> search = plugin.watchmanCommand.consoleSearch;
            if (search == null) {
                throw new CommandWarn("No records available");
            }
            int page = num - 1;
            int pageLen = 10;
            int totalPageCount = (search.size() - 1) / pageLen + 1;
            int fromIndex = page * pageLen;
            int toIndex = fromIndex + pageLen - 1;
            plugin.getLogger().info("Page " + num + "/" + totalPageCount);
            for (int i = fromIndex; i <= toIndex; i += 1) {
                if (i < 0 || i >= search.size()) continue;
                SQLAction action = search.get(i);
                action.showDetails(sender, "" + i);
            }
        }
    }
}
