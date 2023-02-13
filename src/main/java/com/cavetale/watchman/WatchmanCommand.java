package com.cavetale.watchman;

import com.cavetale.core.command.CommandArgCompleter;
import com.cavetale.core.command.CommandWarn;
import com.cavetale.core.command.RemotePlayer;
import com.cavetale.core.connect.Connect;
import com.cavetale.watchman.action.Action;
import com.cavetale.watchman.action.ActionType;
import com.cavetale.watchman.action.ActorType;
import com.cavetale.watchman.lookup.ActionTypeLookup;
import com.cavetale.watchman.lookup.EntityTypeLookup;
import com.cavetale.watchman.lookup.LookupContainer;
import com.cavetale.watchman.lookup.MaterialLookup;
import com.cavetale.watchman.lookup.MaterialTagLookup;
import com.cavetale.watchman.lookup.PlayerLookup;
import com.cavetale.watchman.lookup.RadiusLookup;
import com.cavetale.watchman.lookup.SelectionLookup;
import com.cavetale.watchman.lookup.TimeLookup;
import com.cavetale.watchman.lookup.WorldLookup;
import com.cavetale.watchman.session.LookupSession;
import com.cavetale.watchman.sql.SQLLog;
import com.winthier.playercache.Cache;
import com.winthier.playercache.PlayerCache;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import static net.kyori.adventure.text.Component.join;
import static net.kyori.adventure.text.Component.space;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.JoinConfiguration.noSeparators;
import static net.kyori.adventure.text.JoinConfiguration.separator;
import static net.kyori.adventure.text.format.NamedTextColor.*;

@RequiredArgsConstructor
public final class WatchmanCommand implements TabExecutor {
    private final WatchmanPlugin plugin;

    public void enable() {
        plugin.getCommand("watchman").setExecutor(this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
        try {
            return watchman(sender, args);
        } catch (CommandWarn warn) {
            sender.sendMessage(warn.getMessage());
            return true;
        }
    }

    private boolean watchman(CommandSender sender, String[] args) {
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
                sender.sendMessage("[watchman:wm] player expected");
                return true;
            }
            if (!player.hasPermission("watchman.tool")) {
                player.sendMessage(text("You don't have permission", RED));
                return true;
            }
            if (args.length != 1) return false;
            plugin.watchmanToolCommand.toggle(player);
            return true;
        }
        case "lookup":
        case "l": {
            if (!sender.hasPermission("watchman.lookup")) {
                player.sendMessage(text("You don't have permission", RED));
                return true;
            }
            if (args.length < 2) return false;
            LookupContainer lookup = new LookupContainer();
            int limit = 10000;
            for (int i = 1; i < args.length; i += 1) {
                String arg = args[i];
                String[] toks = arg.split(":", 2);
                String key = toks[0];
                String value = toks[1];
                if (toks.length != 2) return false;
                switch (key) {
                case "player": case "p": {
                    PlayerCache target = PlayerCache.require(value);
                    lookup.setWho(new PlayerLookup(target.uuid));
                    break;
                }
                case "action": case "a":
                    try {
                        ActionType actionType = ActionType.valueOf(value.toUpperCase());
                        lookup.setActionType(new ActionTypeLookup(actionType));
                    } catch (IllegalArgumentException iae) {
                        throw new CommandWarn("Invalid action: " + value);
                    }
                    break;
                case "world": case "w": {
                    if (lookup.getWhere() != null) {
                        lookup.getWhere().setWorld(value);
                    } else {
                        lookup.setWhere(WorldLookup.of(value));
                    }
                    break;
                }
                case "radius": case "r":
                    switch (value) {
                    case "world": case "w":
                        if (player == null) throw new CommandWarn("r:global Player expected");
                        lookup.setWhere(WorldLookup.of(player.getWorld()));
                        break;
                    case "worldedit": case "we": {
                        if (player == null) throw new CommandWarn("r:worldedit Player expected");
                        Cuboid selection = WorldEdit.getSelection(player);
                        if (selection == null) throw new CommandWarn("r:worldedit Selection required");
                        lookup.setWhere(SelectionLookup.of(player.getWorld(), selection));
                        break;
                    }
                    default:
                        lookup.setWhere(RadiusLookup.of(player.getLocation(),
                                                        CommandArgCompleter.requireInt(value, intValue -> intValue > 0)));
                        break;
                    }
                    break;
                case "block": case "b":
                case "item": case "i": {
                    if (value.startsWith("#")) {
                        Tag<Material> tag;
                        String registry = key.startsWith("b") ? Tag.REGISTRY_BLOCKS : Tag.REGISTRY_ITEMS;
                        tag = Bukkit.getTag(registry, NamespacedKey.minecraft(value.substring(1).toLowerCase()), Material.class);
                        if (tag == null) throw new CommandWarn("Invalid material tag: " + value);
                        lookup.setChangedType(new MaterialTagLookup(key.charAt(0), tag));
                    } else {
                        Material material;
                        try {
                            material = Material.valueOf(value.toUpperCase());
                        } catch (IllegalArgumentException iae) {
                            throw new CommandWarn("Invalid material: " + value);
                        }
                        lookup.setChangedType(new MaterialLookup(key.charAt(0), material));
                    }
                    break;
                }
                case "entity": case "e": {
                    EntityType entityType;
                    try {
                        entityType = EntityType.valueOf(value.toUpperCase());
                    } catch (IllegalArgumentException iae) {
                        throw new CommandWarn("Invalid entity type: " + value);
                    }
                    if (entityType == EntityType.UNKNOWN) {
                        throw new CommandWarn("Invalid entity type: " + value);
                    }
                    lookup.setChangedType(new EntityTypeLookup(entityType));
                    break;
                }
                case "time": case "t":
                    lookup.setTime(TimeLookup.parse(value));
                    break;
                case "limit": case "l":
                    limit = CommandArgCompleter.requireInt(value, j -> j > 0 && j <= 100_000_000);
                    break;
                default:
                    throw new CommandWarn("Unknown option: " + key);
                }
            }
            if (lookup.isEmpty()) {
                throw new CommandWarn("Please provide some parameters!");
            }
            lookup.accept(plugin.database.find(SQLLog.class))
                .orderByDescending("time")
                .limit(limit)
                .findListAsync(logs -> {
                        if (player == null) {
                            plugin.database.scheduleAsyncTask(() -> {
                                    List<Action> actions = new ArrayList<>(logs.size());
                                    for (SQLLog log : logs) {
                                        actions.add(new Action().fetch(log));
                                    }
                                    Bukkit.getScheduler().runTask(plugin, () -> {
                                            for (Action action : actions) {
                                                sender.sendMessage(action.getMessage());
                                            }
                                        });
                                });
                        } else {
                            plugin.sessions.set(player.getUniqueId(), lookup.getParameters(), logs, session -> {
                                    plugin.sessions.showPage(player, 0);
                                });
                        }
                    });
            return true;
        }
        case "rollback": {
            if (!sender.hasPermission("watchman.rollback")) {
                throw new CommandWarn("You don't have permission");
            }
            if (args.length == 2) {
                final long id;
                try {
                    id = Long.parseLong(args[1]);
                } catch (IllegalArgumentException iae) {
                    throw new CommandWarn("Invalid id: " + args[1]);
                }
                requireLog(sender, id, action -> {
                        if (!action.rollback()) {
                            sender.sendMessage(join(noSeparators(), text("Rollback failed: ", RED), action.getMessage()));
                        } else {
                            sender.sendMessage(join(noSeparators(), text("Rollback succeeded: ", AQUA), action.getMessage()));
                        }
                    });
            } else if (args.length == 1) {
                if (player == null) throw new CommandWarn("[wm:rollback] Player expected");
                requireSession(player, session -> {
                        int count = 0;
                        for (Action action : session.getActions()) {
                            if (action.rollback()) count += 1;
                        }
                        sender.sendMessage(text("Rolled back " + count + "/" + session.getActions().size()
                                                + " actions", (count == 0 ? RED : AQUA)));
                    });
            }
            return true;
        }
        case "clear":
            if (plugin == null) throw new CommandWarn("[wm:clear] Player expected");
            plugin.sessions.clear(player.getUniqueId());
            player.sendMessage(text("Lookup cleared", AQUA));
            return true;
        case "page": {
            if (player == null || args.length != 2) return false;
            return plugin.watchmanPageCommand.page(player, args[1]);
        }
        case "info": {
            if (args.length != 2) return false;
            final long id;
            try {
                id = Long.parseLong(args[1]);
            } catch (IllegalArgumentException iae) {
                throw new CommandWarn("Invalid id: " + args[1]);
            }
            requireLog(sender, id, action -> {
                    sender.sendMessage(action.getMessage());
                });
            return true;
        }
        case "debug":
            sender.sendMessage("Queued: " + plugin.actionQueue.size() + " actions");
            sender.sendMessage("Backlog: " + plugin.database.getBacklogSize());
            sender.sendMessage("Draining: " + plugin.draining);
            sender.sendMessage("Expiring: " + plugin.expiring);
            return true;
        case "tp": {
            if (args.length != 2) return false;
            final long id;
            try {
                id = Long.parseLong(args[1]);
            } catch (IllegalArgumentException iae) {
                throw new CommandWarn("Invalid id: " + args[1]);
            }
            requireLog(sender, id, action -> {
                    if (!action.isOnThisServer()) {
                        if (player != null) {
                            Connect.get().dispatchRemoteCommand(player, "wm tp " + id, action.getServer());
                        }
                        return;
                    }
                    Location location = action.getLocation();
                    if (location == null) {
                        sender.sendMessage(text("Location not found: #" + id));
                        return;
                    }
                    if (player != null) {
                        player.sendMessage(text("Teleporting to log #" + id, AQUA));
                        player.teleport(location, TeleportCause.COMMAND);
                    } else if (sender instanceof RemotePlayer remote) {
                        remote.bring(plugin, location, p -> {
                                p.sendMessage(text("Teleported to log #" + id, AQUA));
                            });
                    }
                });
            return true;
        }
        case "open": {
            if (player == null) throw new CommandWarn("[watchman:open] player expected");
            if (args.length != 2) return false;
            final long id;
            try {
                id = Long.parseLong(args[1]);
            } catch (IllegalArgumentException iae) {
                throw new CommandWarn("Invalid id: " + args[1]);
            }
            requireLog(player, id, action -> {
                    if (!action.open(player)) {
                        player.sendMessage(text("Opening failed: " + id, RED));
                    }
                });
            return true;
        }
        case "rank": return rank(sender, Arrays.copyOfRange(args, 1, args.length));
        case "ranksel": return rankSel(sender, Arrays.copyOfRange(args, 1, args.length));
        default:
            return false;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command comand, String alias, String[] args) {
        String arg = args.length == 0 ? "" : args[args.length - 1];
        if (args.length == 1) {
            return matchTab(arg, List.of("tool", "rollback", "clear", "page", "info", "lookup", "tab", "tp", "open", "rank", "ranksel"));
        }
        if (args.length > 1 && (args[0].equals("lookup") || args[0].equals("l"))) {
            if (arg.startsWith("action:") || arg.startsWith("a:")) {
                return matchTab(arg, Arrays.stream(ActionType.values()).map(v -> arg.split(":", 2)[0] + ":" + v.name().toLowerCase()));
            }
            if (arg.startsWith("radius:") || arg.startsWith("r:")) {
                String l = arg.split(":", 2)[0];
                try {
                    int num = Integer.parseInt(arg.split(":", 2)[1]);
                    return matchTab(arg, List.of(l + ":" + num, l + ":" + (num * 10), l + ":world"));
                } catch (NumberFormatException nfe) { }
                return matchTab(arg, List.of(l + ":" + 16, l + ":world", l + ":we", l + ":worldedit"));
            }
            if (arg.startsWith("time:") || arg.startsWith("t:")) {
                String[] toks = arg.split(":", 2);
                try {
                    TimeLookup timeLookup = TimeLookup.parse(toks[1]);
                    return List.of(toks[0] + ":" + timeLookup.format());
                } catch (CommandWarn warn) {
                    return List.of(toks[0] + ":");
                }
            }
            if (arg.startsWith("player:") || arg.startsWith("p:")) {
                String[] toks = arg.split(":", 2);
                String pref = toks[0] + ":";
                String name = toks[1];
                String lower = name.toLowerCase();
                return Cache.names().stream()
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
                    .filter(e -> e != EntityType.UNKNOWN)
                    .map(EntityType::getKey)
                    .map(NamespacedKey::getKey)
                    .filter(s -> s.startsWith(lower))
                    .map(s -> pref + s)
                    .limit(128)
                    .collect(Collectors.toList());
            }
            if (arg.startsWith("limit:") || arg.startsWith("l:")) {
                String[] toks = arg.split(":", 2);
                try {
                    int value = Integer.parseInt(toks[1]);
                    return List.of(toks[0] + ":" + value,
                                   toks[0] + ":" + (value * 10));
                } catch (IllegalArgumentException iae) {
                    return List.of(toks[0] + ":");
                }
            }
            return matchTab(arg, List.of("player:", "action:", "world:",
                                         "radius:", "item:", "block:",
                                         "entity:", "time:", "limit:"));
        }
        if (args.length == 2 && args[0].equals("page")) {
            return List.of();
        }
        if (args[0].equals("rank")) {
            if (args.length == 2) {
                List<String> worldNames = new ArrayList<>();
                for (var world : Bukkit.getWorlds()) {
                    worldNames.add(world.getName());
                }
                return matchTab(arg, worldNames);
            } else {
                return List.of();
            }
        }
        return null;
    }


    private boolean rank(CommandSender sender, String[] args) {
        if (args.length != 1) return false;
        final String worldName = args[0];
        plugin.database.scheduleAsyncTask(() -> {
                final String query = "SELECT actor_uuid, count(*) score FROM "
                    + plugin.database.getTable(SQLLog.class).getTableName()
                    + " WHERE server = " + plugin.dictionary.getServerIndex(Connect.get().getServerName())
                    + " AND world = " + plugin.dictionary.getWorldIndex(worldName)
                    + " AND action_type = " + ActionType.PLACE.index
                    + " AND actor_type = " + ActorType.PLAYER.index
                    + " GROUP BY actor_uuid";
                plugin.getLogger().info("[rank] [" + worldName + "] " + query);
                final Map<UUID, Integer> scores = new HashMap<>();
                try (var resultSet = plugin.database.executeQuery(query)) {
                    while (resultSet.next()) {
                        final int actorId = resultSet.getInt("actor_uuid");
                        final int score = resultSet.getInt("score");
                        final UUID uuid = plugin.dictionary.getUuid(actorId);
                        scores.put(uuid, score);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                List<UUID> ranking = new ArrayList<>(scores.keySet());
                ranking.sort((b, a) -> Integer.compare(scores.get(a), scores.get(b)));
                Bukkit.getScheduler().runTask(plugin, () -> {
                        sender.sendMessage(text("Total " + scores.size() + " players", AQUA));
                        for (UUID uuid : ranking) {
                            sender.sendMessage(join(separator(space()),
                                                    text(scores.get(uuid), YELLOW),
                                                    text(PlayerCache.nameForUuid(uuid), WHITE),
                                                    text(uuid.toString(), GRAY)));
                        }
                    });
            });
        sender.sendMessage(text("Counting, please wait...", YELLOW));
        return true;
    }

    private boolean rankSel(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            throw new CommandWarn("[wm ranksel] Player required)");
        }
        if (args.length != 0) return false;
        final String worldName = player.getWorld().getName();
        final Cuboid cuboid = WorldEdit.getSelection(player);
        if (cuboid == null) throw new CommandWarn("Selection required!");
        plugin.database.scheduleAsyncTask(() -> {
                final String query = "SELECT actor_uuid, count(*) score FROM "
                    + plugin.database.getTable(SQLLog.class).getTableName()
                    + " WHERE server = " + plugin.dictionary.getServerIndex(Connect.get().getServerName())
                    + " AND world = " + plugin.dictionary.getWorldIndex(worldName)
                    + " AND x BETWEEN " + cuboid.ax + " AND " + cuboid.bx
                    + " AND z BETWEEN " + cuboid.az + " AND " + cuboid.bz
                    + " AND action_type = " + ActionType.PLACE.index
                    + " AND actor_type = " + ActorType.PLAYER.index
                    + " GROUP BY actor_uuid";
                plugin.getLogger().info("[rank] [" + worldName + "] " + cuboid + " " + query);
                final Map<UUID, Integer> scores = new HashMap<>();
                try (var resultSet = plugin.database.executeQuery(query)) {
                    while (resultSet.next()) {
                        final int actorId = resultSet.getInt("actor_uuid");
                        final int score = resultSet.getInt("score");
                        final UUID uuid = plugin.dictionary.getUuid(actorId);
                        scores.put(uuid, score);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                List<UUID> ranking = new ArrayList<>(scores.keySet());
                ranking.sort((b, a) -> Integer.compare(scores.get(a), scores.get(b)));
                Bukkit.getScheduler().runTask(plugin, () -> {
                        sender.sendMessage(text("Total " + scores.size() + " players in " + cuboid, AQUA));
                        for (UUID uuid : ranking) {
                            sender.sendMessage(join(separator(space()),
                                                    text(scores.get(uuid), YELLOW),
                                                    text(PlayerCache.nameForUuid(uuid), WHITE),
                                                    text(uuid.toString(), GRAY)));
                        }
                    });
            });
        sender.sendMessage(text("Counting, please wait...", YELLOW));
        return true;
    }

    private List<String> matchTab(String arg, List<String> args) {
        return args.stream().filter(a -> a.startsWith(arg)).collect(Collectors.toList());
    }

    private List<String> matchTab(String arg, Stream<String> args) {
        return args.filter(a -> a.startsWith(arg)).collect(Collectors.toList());
    }

    private void requireLog(CommandSender sender, long id, Consumer<Action> callback) {
        plugin.database.scheduleAsyncTask(() -> {
                SQLLog log = plugin.database.find(SQLLog.class)
                    .eq("id", id)
                    .findUnique();
                if (log == null) {
                    Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(text("Log not found: " + id, RED)));
                    return;
                }
                Action action = new Action().fetch(log);
                Bukkit.getScheduler().runTask(plugin, () -> callback.accept(action));
            });
    }

    private void requireSession(Player player, Consumer<LookupSession> callback) {
        plugin.sessions.get(player.getUniqueId(), session -> {
                if (session == null || session.getActions().isEmpty()) {
                    player.sendMessage(text("You do not have a lookup stored", RED));
                    return;
                }
                callback.accept(session);
            });
    }
}
