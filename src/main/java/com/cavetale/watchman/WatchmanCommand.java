package com.cavetale.watchman;

import com.winthier.generic_events.GenericEvents;
import com.winthier.sql.SQLTable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;

@RequiredArgsConstructor
public final class WatchmanCommand implements TabExecutor {
    private final WatchmanPlugin plugin;
    private List<SQLAction> consoleSearch = null;

    public void enable() {
        plugin.getCommand("watchman").setExecutor(this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
        final Player player = sender instanceof Player ? (Player) sender : null;
        if (args.length == 0) return false;
        switch (args[0]) {
        case "tool": {
            if (player == null) {
                sender.sendMessage("Player expected");
                return true;
            }
            if (!player.hasPermission("watchman.tool")) {
                player.sendMessage(ChatColor.RED + "You don't have permission");
                return true;
            }
            if (args.length != 1) return false;
            boolean hasTool = player.hasMetadata(Meta.TOOL_KEY);
            if (hasTool) {
                player.removeMetadata(Meta.TOOL_KEY, plugin);
                sender.sendMessage(ChatColor.YELLOW + "Watchman tool disabled");
            } else {
                player.setMetadata(Meta.TOOL_KEY, new FixedMetadataValue(plugin, true));
                player.sendMessage(ChatColor.GREEN + "Watchman tool enabled");
            }
            return true;
        }
        case "lookup":
        case "l": {
            if (player != null && !player.hasPermission("watchman.lookup")) {
                player.sendMessage(ChatColor.RED + "You don't have permission");
                return true;
            }
            if (args.length == 0) return false;
            Location location = player != null
                ? player.getLocation()
                : plugin.getServer().getWorlds().get(0).getSpawnLocation();
            LookupMeta meta = new LookupMeta();
            meta.world = location.getWorld().getName();
            meta.cx = location.getBlockX();
            meta.cz = location.getBlockZ();
            meta.radius = 12;
            SQLTable<SQLAction>.Finder search = plugin.database.find(SQLAction.class);
            for (int i = 1; i < args.length; i += 1) {
                String arg = args[i];
                String[] toks = arg.split(":", 2);
                if (toks.length != 2) return false;
                switch (toks[0]) {
                case "player": case "p":
                    if (true) {
                        UUID uuid = GenericEvents.cachedPlayerUuid(toks[1]);
                        if (uuid == null) {
                            sender.sendMessage("Unknown player: " + toks[1]);
                            return true;
                        }
                        search.eq("actorId", uuid);
                        meta.player = uuid;
                    }
                    break;
                case "action": case "a":
                    try {
                        SQLAction.Type action = SQLAction.Type.valueOf(toks[1].toUpperCase());
                        search.eq("action", action.name().toLowerCase());
                        meta.action = action;
                    } catch (IllegalArgumentException iae) {
                        sender.sendMessage("Unknown action: " + toks[1]);
                        return true;
                    }
                    break;
                case "world": case "w":
                    meta.world = toks[1];
                    break;
                case "center": case "c":
                    if (true) {
                        String[] locs = toks[1].split(",", 2);
                        if (locs.length != 2) {
                            sender.sendMessage("2 comma separated coordinates expected, got: " + toks[1]);
                            return true;
                        }
                        try {
                            meta.cx = Integer.parseInt(locs[0]);
                            meta.cz = Integer.parseInt(locs[1]);
                        } catch (NumberFormatException nfe) {
                            sender.sendMessage("Invalid coordinates: " + toks[1]);
                            return true;
                        }
                    }
                    break;
                case "radius": case "r":
                    switch (toks[1]) {
                    case "global": case "g":
                        meta.global = true;
                        break;
                    case "world": case "w":
                        meta.worldwide = true;
                        break;
                    default:
                        try {
                            meta.radius = Integer.parseInt(toks[1]);
                        } catch (NumberFormatException nfe) {
                            sender.sendMessage("Invalid radius: " + toks[1]);
                            return true;
                        }
                        break;
                    }
                    break;
                case "old": case "o":
                    search.eq("oldType", toks[1]);
                    meta.oldType = toks[1];
                    break;
                case "new": case "n":
                    search.eq("newType", toks[1]);
                    meta.newType = toks[1];
                    break;
                case "time": case "t":
                    if (true) {
                        long seconds;
                        try {
                            seconds = Long.parseLong(toks[1]);
                        } catch (NumberFormatException nfe) {
                            sender.sendMessage("Seconds expected, got: " + toks[1]);
                            return true;
                        }
                        Date time = new Date(System.currentTimeMillis() - (seconds * 1000));
                        search.gt("time", time);
                        meta.after = time.getTime();
                    }
                    break;
                default:
                    sender.sendMessage("Unknown option: " + toks[0]);
                    return true;
                }
            }
            if (!meta.global) {
                search.eq("world", meta.world);
                if (!meta.worldwide) {
                    search.gt("x", meta.cx - meta.radius);
                    search.lt("x", meta.cx + meta.radius);
                    search.gt("z", meta.cz - meta.radius);
                    search.lt("z", meta.cz + meta.radius);
                }
            }
            search.orderByDescending("time");
            if (player != null) {
                player.removeMetadata(Meta.LOOKUP, plugin);
                player.removeMetadata(Meta.LOOKUP_META, plugin);
                player.sendMessage(ChatColor.YELLOW + "Searching...");
                search.findListAsync((actions) -> {
                        if (!player.isValid()) return;
                        if (actions.isEmpty()) {
                            player.sendMessage(ChatColor.RED + "Nothing found.");
                            return;
                        }
                        player.sendMessage("" + ChatColor.YELLOW + actions.size() + " actions found");
                        player.setMetadata(Meta.LOOKUP, new FixedMetadataValue(plugin, actions));
                        player.setMetadata(Meta.LOOKUP_META, new FixedMetadataValue(plugin, meta));
                        plugin.showActionPage(player, actions, meta, 0);
                    });
            } else {
                plugin.getLogger().info("Searching...");
                search.findListAsync((actions) -> {
                        consoleSearch = actions;
                        plugin.getLogger().info("Found " + actions.size() + " results. Use /wm page PAGE");
                    });
            }
            return true;
        }
        case "rollback": {
            if (player != null && !player.hasPermission("watchman.rollback")) {
                player.sendMessage(ChatColor.RED + "You don't have permission");
                return true;
            }
            if (args.length != 1 && args.length != 2) return false;
            List<SQLAction> actions;
            if (player != null) {
                if (!player.hasMetadata(Meta.LOOKUP)) {
                    player.sendMessage(ChatColor.RED + "Make a lookup first.");
                    return true;
                }
                actions = (List<SQLAction>) player.getMetadata(Meta.LOOKUP).get(0).value();
            } else {
                if (consoleSearch == null) {
                    plugin.getLogger().info("No records available");
                    return true;
                }
                actions = consoleSearch;
            }
            if (args.length == 2) {
                int index;
                try {
                    index = Integer.parseInt(args[1]);
                } catch (NumberFormatException nfe) {
                    index = -1;
                }
                if (index < 0 || index >= actions.size()) {
                    sender.sendMessage(ChatColor.RED + "Invalid lookup index " + args[1]);
                    return true;
                }
                actions = Arrays.asList(actions.get(index));
                sender.sendMessage(ChatColor.YELLOW + "Attempting to roll back action with id " + index + "...");
            } else {
                sender.sendMessage(ChatColor.YELLOW + "Attempting to roll back " + actions.size() + " actions...");
            }
            int count = 0;
            for (SQLAction action: actions) {
                if (action.rollback()) {
                    count += 1;
                }
            }
            sender.sendMessage("Successfully rolled back " + count + " actions");
            return true;
        }
        case "clear":
            if (args.length == 1 && player != null) {
                if (player.hasMetadata(Meta.LOOKUP)) {
                    player.removeMetadata(Meta.LOOKUP, plugin);
                    player.removeMetadata(Meta.LOOKUP_META, plugin);
                    player.sendMessage("Search cleared");
                } else {
                    player.sendMessage("You have no stored search");
                }
                return true;
            }
            break;
        case "page": {
            if (args.length != 2) return false;
            int num;
            try {
                num = Integer.parseInt(args[1]);
            } catch (NumberFormatException nfe) {
                num = -1;
            }
            if (num < 0) {
                sender.sendMessage(ChatColor.RED + "Invalid page: " + args[1]);
                return true;
            }
            if (player != null) {
                if (!player.hasMetadata(Meta.LOOKUP)) {
                    player.sendMessage(ChatColor.RED + "No records available");
                    return true;
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
                if (consoleSearch == null) {
                    plugin.getLogger().info("No records available");
                    return true;
                }
                int page = num - 1;
                int pageLen = 10;
                int totalPageCount = (consoleSearch.size() - 1) / pageLen + 1;
                int fromIndex = page * pageLen;
                int toIndex = fromIndex + pageLen - 1;
                plugin.getLogger().info("Page " + num + "/" + totalPageCount);
                for (int i = fromIndex; i <= toIndex; i += 1) {
                    if (i < 0 || i >= consoleSearch.size()) continue;
                    SQLAction action = consoleSearch.get(i);
                    action.showDetails(sender, i);
                }
            }
            return true;
        }
        case "info": {
            if (player == null) {
                sender.sendMessage("Player expected");
                return true;
            }
            if (args.length != 2) return false;
            int num;
            try {
                num = Integer.parseInt(args[1]);
            } catch (NumberFormatException nfe) {
                num = -1;
            }
            if (num < 0) {
                player.sendMessage(ChatColor.RED + "Invalid page: " + args[1]);
                return true;
            }
            if (!player.hasMetadata(Meta.LOOKUP)) {
                player.sendMessage(ChatColor.RED + "No records available");
                return true;
            }
            List<SQLAction> actions = (List<SQLAction>) player.getMetadata(Meta.LOOKUP).get(0).value();
            if (num >= actions.size()) {
                player.sendMessage(ChatColor.RED + "Invalid action index: " + num);
                return true;
            }
            SQLAction action = actions.get(num);
            action.showDetails(player, num);
            return true;
        }
        case "debug":
            sender.sendMessage("Storage: " + plugin.storage.size() + " rows");
            sender.sendMessage("Backlog: " + plugin.database.getBacklogSize());
            return true;
        case "rewind": return rewindCommand(player, Arrays.copyOfRange(args, 1, args.length));
        default:
            break;
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command comand, String alias, String[] args) {
        String arg = args.length == 0 ? "" : args[args.length - 1];
        if (args.length == 1) {
            return matchTab(arg, Arrays.asList("tool", "rollback", "clear", "page", "info", "lookup"));
        }
        if (args.length > 1 && (args[0].equals("lookup") || args[0].equals("l"))) {
            if (arg.startsWith("action:") || arg.startsWith("a:")) {
                return matchTab(arg, Arrays.stream(SQLAction.Type.values()).map(v -> arg.split(":", 2)[0] + ":" + v.name().toLowerCase()));
            }
            if (arg.startsWith("radius:") || arg.startsWith("r:")) {
                String l = arg.split(":", 2)[0];
                try {
                    int num = Integer.parseInt(arg.split(":", 2)[1]);
                    return matchTab(arg, Arrays.asList(l + ":" + num, l + ":" + (num * 10), l + ":global", l + ":world"));
                } catch (NumberFormatException nfe) { }
                return matchTab(arg, Arrays.asList(l + ":" + 8, l + ":global", l + ":world"));
            }
            return matchTab(arg, Arrays.asList("player:", "action:", "world:", "center:", "radius:", "old:", "new:", "time:"));
        }
        if (args.length == 2 && args[0].equals("page")) {
            List<SQLAction> actions;
            int pageLen;
            if (sender instanceof Player) {
                Player player = (Player) sender;
                if (!player.hasMetadata(Meta.LOOKUP)) return Collections.emptyList();
                actions = (List<SQLAction>) player.getMetadata(Meta.LOOKUP).get(0).value();
                pageLen = 5;
            } else {
                if (consoleSearch == null) return Collections.emptyList();
                actions = consoleSearch;
                pageLen = 10;
            }
            int totalPageCount = (actions.size() - 1) / pageLen + 1;
            List<String> ls = new ArrayList(totalPageCount);
            for (int i = 0; i < totalPageCount; i += 1) ls.add("" + (i + 1));
            return matchTab(arg, ls);
        }
        return null;
    }

    private List<String> matchTab(String arg, List<String> args) {
        return args.stream().filter(a -> a.startsWith(arg)).collect(Collectors.toList());
    }

    private List<String> matchTab(String arg, Stream<String> args) {
        return args.filter(a -> a.startsWith(arg)).collect(Collectors.toList());
    }

    boolean rewindCommand(Player player, String[] args) {
        if (args.length > 2) return false;
        Cuboid cuboid = WorldEdit.getSelection(player);
        if (cuboid == null) {
            player.sendMessage(ChatColor.RED + "No selection!");
            return true;
        }
        int speed = 1;
        if (args.length >= 1) {
            try {
                speed = Integer.parseInt(args[0]);
            } catch (NumberFormatException nfe) {
                player.sendMessage(ChatColor.RED + "Bad speed: " + args[0]);
                return true;
            }
        }
        String worldName;
        if (args.length >= 2) {
            worldName = args[1];
        } else {
            World world = player.getWorld();
            worldName = player.getWorld().getName();
        }
        SQLTable<SQLAction>.Finder search = plugin.database.find(SQLAction.class);
        search.eq("world", worldName);
        search.gte("x", cuboid.ax);
        search.gte("y", cuboid.ay);
        search.gte("z", cuboid.az);
        search.lte("x", cuboid.bx);
        search.lte("y", cuboid.by);
        search.lte("z", cuboid.bz);
        search.in("action", SQLAction.Type.inCategory(SQLAction.Type.Category.BLOCK));
        search.orderByAscending("time");
        search.orderByAscending("id");
        final int finalSpeed = speed;
        search.findListAsync(ls -> rewindCallback(player, ls, finalSpeed, cuboid));
        return true;
    }

    void rewindCallback(Player player, List<SQLAction> actions, int speed, Cuboid cuboid) {
        RewindTask task = new RewindTask(plugin, player, actions, 100L, speed, cuboid);
        task.start();
    }
}
