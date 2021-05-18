package com.cavetale.watchman;

import com.winthier.generic_events.GenericEvents;
import com.winthier.playercache.PlayerCache;
import com.winthier.sql.SQLTable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;

@RequiredArgsConstructor
public final class WatchmanCommand implements TabExecutor {
    private final WatchmanPlugin plugin;
    private List<SQLAction> consoleSearch = null;

    private Location center;
    private Location pos1;
    private Location pos2;

    public void enable() {
        plugin.getCommand("watchman").setExecutor(this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
        final Player player = sender instanceof Player ? (Player) sender : null;
        if (args.length == 0) return false;
        switch (args[0]) {
        case "reload": {
            plugin.loadConf();
            sender.sendMessage("Configuration reloaded");
            return true;
        }
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
                    meta.worldwide = true;
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
                    case "worldedit": case "we":
                        if (player == null) {
                            sender.sendMessage("[watchman:wm] Player expected!");
                            return true;
                        }
                        meta.worldedit = true;
                        meta.selection = WorldEdit.getSelection(player);
                        if (meta.selection == null) {
                            player.sendMessage(ChatColor.RED + "WorldEdit selection required!");
                            return true;
                        }
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
                case "block": case "b":
                case "item": case "i": {
                    Set<Material> mats;
                    String string = toks[1];
                    if (string.startsWith("#")) {
                        Tag<Material> tag;
                        String registry = toks[0].startsWith("b") ? Tag.REGISTRY_BLOCKS : Tag.REGISTRY_ITEMS;
                        tag = Bukkit.getTag(registry, NamespacedKey.minecraft(string.substring(1).toLowerCase()), Material.class);
                        if (tag == null) {
                            sender.sendMessage("Invalid material tag: " + string);
                            return true;
                        }
                        meta.type = tag;
                        mats = tag.getValues();
                    } else {
                        Material material;
                        try {
                            material = Material.valueOf(string.toUpperCase());
                        } catch (IllegalArgumentException iae) {
                            sender.sendMessage("Invalid material: " + string);
                            return true;
                        }
                        meta.type = material;
                        mats = EnumSet.of(material);
                    }
                    search.in("type", mats.stream().map(Material::getKey).map(NamespacedKey::getKey).collect(Collectors.toSet()));
                    break;
                }
                case "entity": case "e": {
                    Set<EntityType> types;
                    String string = toks[1];
                    EntityType entityType;
                    try {
                        entityType = EntityType.valueOf(string.toUpperCase());
                    } catch (IllegalArgumentException iae) {
                        sender.sendMessage("Invalid entity type: " + string);
                        return true;
                    }
                    meta.type = entityType;
                    types = EnumSet.of(entityType);
                    search.in("type", types.stream().map(EntityType::getKey).map(NamespacedKey::getKey).collect(Collectors.toSet()));
                    break;
                }
                case "time": case "t":
                    if (true) {
                        long seconds;
                        try {
                            seconds = Time.parseSeconds(toks[1]);
                        } catch (IllegalArgumentException nfe) {
                            sender.sendMessage("Time expected, got: " + toks[1]);
                            return true;
                        }
                        meta.seconds = seconds;
                        Date time = new Date(System.currentTimeMillis() - (seconds * 1000));
                        search.gt("time", time);
                    }
                    break;
                default:
                    sender.sendMessage("Unknown option: " + toks[0]);
                    return true;
                }
            }
            if (meta.worldedit) {
                search.eq("world", meta.world);
                search.between("x", meta.selection.ax, meta.selection.bx);
                search.between("z", meta.selection.az, meta.selection.bz);
                search.between("y", meta.selection.ay, meta.selection.by);
            } else if (!meta.global) {
                search.eq("world", meta.world);
                if (!meta.worldwide) {
                    search.gt("x", meta.cx - meta.radius);
                    search.lt("x", meta.cx + meta.radius);
                    search.gt("z", meta.cz - meta.radius);
                    search.lt("z", meta.cz + meta.radius);
                }
            }
            search.orderByDescending("id");
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
        case "delete": {
            if (player != null && !player.hasPermission("watchman.delete")) {
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
                sender.sendMessage(ChatColor.YELLOW + "Attempting to delete action with id " + index + "...");
            } else {
                sender.sendMessage(ChatColor.YELLOW + "Attempting to delete " + actions.size() + " actions...");
            }
            int count = plugin.database.delete(actions);
            sender.sendMessage("Successfully deleted " + count + " actions");
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
        case "rank": return rankCommand(player, Arrays.copyOfRange(args, 1, args.length));
        case "debug":
            sender.sendMessage("Storage: " + plugin.storage.size() + " rows");
            sender.sendMessage("Backlog: " + plugin.database.getBacklogSize());
            return true;
        case "pos1":
            pos1 = player.getLocation();
            player.sendMessage("pos1 saved");
            return true;
        case "pos2":
            pos2 = player.getLocation();
            player.sendMessage("pos2 saved");
            return true;
        case "center":
            center = player.getLocation();
            player.sendMessage("center saved");
            return true;
        case "nopos":
            pos1 = null;
            pos2 = null;
            center = null;
            player.sendMessage("positions reset");
            return true;
        case "rewind":
            if (player == null) {
                sender.sendMessage("[watchman:rewind] player expected");
                return true;
            }
            if (!player.hasPermission("watchman.rewind")) {
                player.sendMessage(ChatColor.RED + "You don't have permission!");
                return true;
            }
            return rewindCommand(player, Arrays.copyOfRange(args, 1, args.length));
        case "fake": return fakeCommand(player, Arrays.copyOfRange(args, 1, args.length));
        case "expire": {
            plugin.deleteExpiredLogs();
            sender.sendMessage("Deleting expired logs. See console");
            return true;
        }
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
                return matchTab(arg, Arrays.asList(l + ":" + 8, l + ":global", l + ":world", l + ":we", l + ":worldedit"));
            }
            if (arg.startsWith("time:") || arg.startsWith("t:")) {
                try {
                    long seconds = Time.parseSeconds(arg.split(":", 2)[1]);
                    return Arrays.asList(arg.split(":", 2)[0] + ":" + Time.formatSeconds(seconds));
                } catch (IllegalArgumentException nfe) {
                    return Collections.emptyList();
                }
            }
            if (arg.startsWith("player:") || arg.startsWith("p:")) {
                String[] toks = arg.split(":", 2);
                String pref = toks[0] + ":";
                String name = toks[1];
                String lower = name.toLowerCase();
                return PlayerCache.allCached().stream()
                    .map(PlayerCache::getName)
                    .filter(s -> s.toLowerCase().startsWith(lower))
                    .map(s -> pref + s)
                    .limit(128)
                    .collect(Collectors.toList());
            }
            if (arg.startsWith("block:") || arg.startsWith("b:")) {
                String[] toks = arg.split(":", 2);
                String pref = toks[0] + ":";
                String name = toks[1];
                String lower = name.toLowerCase();
                return Stream.concat((StreamSupport.stream(Bukkit.getTags(Tag.REGISTRY_BLOCKS, Material.class).spliterator(), false)
                                      .map(Tag::getKey).map(NamespacedKey::getKey).map(s -> "#" + s)),
                                     (Stream.of(Material.values())
                                      .map(Material::getKey).map(NamespacedKey::getKey)))
                    .filter(s -> s.startsWith(lower))
                    .map(s -> pref + s)
                    .limit(128)
                    .collect(Collectors.toList());
            }
            if (arg.startsWith("item:") || arg.startsWith("i:")) {
                String[] toks = arg.split(":", 2);
                String pref = toks[0] + ":";
                String name = toks[1];
                String lower = name.toLowerCase();
                return Stream.concat((StreamSupport.stream(Bukkit.getTags(Tag.REGISTRY_ITEMS, Material.class).spliterator(), false)
                                      .map(Tag::getKey).map(NamespacedKey::getKey).map(s -> "#" + s)),
                                     (Stream.of(Material.values())
                                      .map(Material::getKey).map(NamespacedKey::getKey)))
                    .filter(s -> s.startsWith(lower))
                    .map(s -> pref + s)
                    .limit(128)
                    .collect(Collectors.toList());
            }
            if (arg.startsWith("entity:") || arg.startsWith("e:")) {
                String[] toks = arg.split(":", 2);
                String pref = toks[0] + ":";
                String name = toks[1];
                String lower = name.toLowerCase();
                return Stream.of(EntityType.values())
                    .map(EntityType::getKey).map(NamespacedKey::getKey)
                    .filter(s -> s.startsWith(lower))
                    .map(s -> pref + s)
                    .limit(128)
                    .collect(Collectors.toList());
            }
            return matchTab(arg, Arrays.asList("player:", "action:", "world:", "center:",
                                               "radius:", "item:", "block:", "entity:", "time:"));
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
        if (args.length >= 3 && args[0].equals("rewind")) {
            return Stream.of(RewindTask.Flag.values())
                .map(Enum::name).map(String::toLowerCase).map(s -> s.replace("_", "-"))
                .filter(s -> s.startsWith(arg))
                .collect(Collectors.toList());
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
        final int duration;
        if (args.length >= 1) {
            try {
                duration = Integer.parseInt(args[0]);
            } catch (NumberFormatException nfe) {
                player.sendMessage(ChatColor.RED + "Bad duration (seconds): " + args[0]);
                return true;
            }
        } else {
            duration = 10;
        }
        String worldName = player.getWorld().getName();
        Set<RewindTask.Flag> flags = EnumSet.noneOf(RewindTask.Flag.class);
        for (int i = 1; i < args.length; i += 1) {
            String arg = args[i];
            RewindTask.Flag flag;
            try {
                flag = RewindTask.Flag.valueOf(arg.toUpperCase().replace("-", "_"));
            } catch (IllegalArgumentException iae) {
                player.sendMessage(ChatColor.RED + "Invalid flag: " + arg);
                return true;
            }
            flags.add(flag);
        }
        Cuboid cuboid;
        if (flags.contains(RewindTask.Flag.LOOKUP)) {
            if (!player.hasMetadata(Meta.LOOKUP) || !player.hasMetadata(Meta.LOOKUP_META)) {
                player.sendMessage(ChatColor.RED + "Make a lookup first.");
                return true;
            }
            LookupMeta meta = (LookupMeta) player.getMetadata(Meta.LOOKUP_META).get(0).value();
            List<SQLAction> actions = (List<SQLAction>) player.getMetadata(Meta.LOOKUP).get(0).value();
            cuboid = meta.selection != null ? meta.selection : Cuboid.ZERO;
            rewindCallback(player, actions, duration, cuboid, flags);
        } else {
            cuboid = WorldEdit.getSelection(player);
            if (cuboid == null) {
                player.sendMessage(ChatColor.RED + "No selection!");
                return true;
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
            search.orderByAscending("id");
            search.findListAsync(ls -> rewindCallback(player, ls, duration, cuboid, flags));
        }
        player.sendMessage("Preparing rewind of " + cuboid + " within " + duration + "s...");
        return true;
    }

    void rewindCallback(Player player, List<SQLAction> actions, int duration, Cuboid cuboid, Set<RewindTask.Flag> flags) {
        Location loc1 = pos1 != null && pos1.getWorld().equals(player.getWorld()) ? pos1 : null;
        Location loc2 = pos2 != null && pos2.getWorld().equals(player.getWorld()) ? pos2 : null;
        Location loc3 = center != null && center.getWorld().equals(player.getWorld()) ? center : null;
        int durationInTicks = duration * 20;
        int blocksPerTick = actions.size() / durationInTicks;
        RewindTask task = new RewindTask(plugin, player, actions, 100L, blocksPerTick, cuboid, flags, loc1, loc2, loc3);
        task.start();
    }

    boolean rankCommand(Player player, String[] args) {
        if (args.length != 0) return false;
        Cuboid cuboid = WorldEdit.getSelection(player);
        if (cuboid == null) {
            player.sendMessage(ChatColor.RED + "No selection!");
            return true;
        }
        String sql = "SELECT `actor_name`, `action`, count(*) c FROM `" + plugin.database.getTable(SQLAction.class).getTableName() + "`"
            + " WHERE `actor_name` IS NOT NULL"
            + " AND `world` = '" + player.getWorld().getName() + "'"
            + " AND `x` BETWEEN " + cuboid.ax + " AND " + cuboid.bx
            + " AND `y` BETWEEN " + cuboid.ay + " AND " + cuboid.by
            + " AND `z` BETWEEN " + cuboid.az + " AND " + cuboid.bz
            + " AND `action` IN ("
            + SQLAction.Type.inCategory(SQLAction.Type.Category.BLOCK).stream().map(s -> "'" + s + "'").collect(Collectors.joining(", "))
            + ")"
            + " GROUP BY `actor_name`, `action`"
            + " ORDER BY `c` ASC";
        plugin.database.executeQueryAsync(sql, resultSet -> {
                int total = 0;
                try {
                    while (resultSet.next()) {
                        String name = resultSet.getString("actor_name");
                        String action = resultSet.getString("action");
                        int count = resultSet.getInt("c");
                        player.sendMessage(" " + ChatColor.YELLOW + count
                                           + " " + ChatColor.RED + action
                                           + " " + ChatColor.WHITE + name);
                        total += 1;
                    }
                    resultSet.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                player.sendMessage("Total " + total);
            });
        return true;
    }

    boolean fakeCommand(Player player, String[] args) {
        if (args.length != 0) return false;
        Cuboid cuboid = WorldEdit.getSelection(player);
        if (cuboid == null) {
            player.sendMessage(ChatColor.RED + "No selection!");
            return true;
        }
        World world = player.getWorld();
        Set<Vec3i> ignore = new HashSet<>();
        plugin.database.find(SQLAction.class)
            .eq("world", world.getName())
            .between("x", cuboid.ax, cuboid.bx)
            .between("y", cuboid.ay, cuboid.by)
            .between("z", cuboid.az, cuboid.bz)
            .in("action", SQLAction.Type.inCategory(SQLAction.Type.Category.BLOCK))
            .findListAsync(list -> {
                    if (!player.isOnline() || !player.getWorld().equals(world)) return;
                    for (SQLAction row : list) {
                        if (ignore.contains(row.getVector())) continue;
                        if (row.getBlock().getType() == row.getNewBlockData().getMaterial()) {
                            ignore.add(row.getVector());
                        }
                    }
                    int count = 0;
                    for (Vec3i vec : cuboid.enumerate()) {
                        if (ignore.contains(vec)) continue;
                        Block block = vec.toBlock(world);
                        if (block.isEmpty()) continue;
                        plugin.store(new SQLAction()
                                     .setNow().setActionType(SQLAction.Type.BLOCK_FAKE)
                                     .setLocation(block)
                                     .setOldState(Material.AIR.createBlockData())
                                     .setNewState(block)
                                     .setActorTypeName("fake"));
                        count += 1;
                    }
                    player.sendMessage(count + " fake blocks stored!");
                });
        player.sendMessage("Searching...");
        return true;
    }
}
