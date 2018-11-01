package com.cavetale.watchman;

import com.winthier.generic_events.GenericEvents;
import com.winthier.sql.SQLDatabase;
import com.winthier.sql.SQLTable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Getter;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;

public final class WatchmanPlugin extends JavaPlugin implements Listener {
    // Constants
    public static final String TOOL_KEY = "watchman.Tool";
    static final String META_LOOKUP = "watchman.lookup";
    static final String META_LOOKUP_META = "watchman.lookup.meta";
    // Members
    @Getter private static WatchmanPlugin instance;
    private SQLDatabase database;
    private List<SQLAction> consoleSearch = null;

    @Override
    public void onEnable() {
        instance = this;
        database = new SQLDatabase(this);
        database.registerTables(SQLAction.class);
        database.createAllTables();
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        for (Player player: getServer().getOnlinePlayers()) {
            player.removeMetadata(TOOL_KEY, this);
            player.removeMetadata(META_LOOKUP, this);
            player.removeMetadata(META_LOOKUP_META, this);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
        final Player player = sender instanceof Player ? (Player)sender : null;
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
            boolean hasTool = player.hasMetadata(TOOL_KEY);
            if (hasTool) {
                player.removeMetadata(TOOL_KEY, this);
                sender.sendMessage(ChatColor.YELLOW + "Watchman tool disabled");
            } else {
                player.setMetadata(TOOL_KEY, new FixedMetadataValue(this, true));
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
            Location location = player != null ? player.getLocation() : getServer().getWorlds().get(0).getSpawnLocation();
            LookupMeta meta = new LookupMeta();
            meta.world = location.getWorld().getName();
            meta.cx = location.getBlockX();
            meta.cz = location.getBlockZ();
            meta.radius = 12;
            SQLTable<SQLAction>.Finder search = database.find(SQLAction.class);
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
                player.removeMetadata(META_LOOKUP, this);
                player.removeMetadata(META_LOOKUP_META, this);
                player.sendMessage(ChatColor.YELLOW + "Searching...");
                search.findListAsync((actions) -> {
                        if (!player.isValid()) return;
                        if (actions.isEmpty()) {
                            player.sendMessage(ChatColor.RED + "Nothing found.");
                            return;
                        }
                        player.sendMessage("" + ChatColor.YELLOW + actions.size() + " actions found");
                        player.setMetadata(META_LOOKUP, new FixedMetadataValue(this, actions));
                        player.setMetadata(META_LOOKUP_META, new FixedMetadataValue(this, meta));
                        showActionPage(player, actions, meta, 0);
                    });
            } else {
                getLogger().info("Searching...");
                search.findListAsync((actions) -> {
                        this.consoleSearch = actions;
                        getLogger().info("Found " + actions.size() + " results. Use /wm page PAGE");
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
                if (!player.hasMetadata(META_LOOKUP)) {
                    player.sendMessage(ChatColor.RED + "Make a lookup first.");
                    return true;
                }
                actions = (List<SQLAction>)player.getMetadata(META_LOOKUP).get(0).value();
            } else {
                if (consoleSearch == null) {
                    getLogger().info("No records available");
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
                if (player.hasMetadata(META_LOOKUP)) {
                    player.removeMetadata(META_LOOKUP, this);
                    player.removeMetadata(META_LOOKUP_META, this);
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
                if (!player.hasMetadata(META_LOOKUP)) {
                    player.sendMessage(ChatColor.RED + "No records available");
                    return true;
                }
                List<SQLAction> actions = (List<SQLAction>)player.getMetadata(META_LOOKUP).get(0).value();
                LookupMeta meta;
                if (!player.hasMetadata(META_LOOKUP)) {
                    meta = new LookupMeta();
                } else {
                    meta = (LookupMeta)player.getMetadata(META_LOOKUP_META).get(0).value();
                }
                showActionPage(player, actions, meta, num - 1);
            } else {
                if (consoleSearch == null) {
                    getLogger().info("No records available");
                    return true;
                }
                int page = num - 1;
                int pageLen = 10;
                int totalPageCount = (consoleSearch.size() - 1) / pageLen + 1;
                int fromIndex = page * pageLen;
                int toIndex = fromIndex + pageLen - 1;
                getLogger().info("Page " + num + "/" + totalPageCount);
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
            if (!player.hasMetadata(META_LOOKUP)) {
                player.sendMessage(ChatColor.RED + "No records available");
                return true;
            }
            List<SQLAction> actions = (List<SQLAction>)player.getMetadata(META_LOOKUP).get(0).value();
            if (num >= actions.size()) {
                player.sendMessage(ChatColor.RED + "Invalid action index: " + num);
                return true;
            }
            SQLAction action = actions.get(num);
            action.showDetails(player, num);
            return true;
        }
        case "debug":
            return true;
        default:
            break;
        }
        return false;
    }

    private List<String> matchTab(String arg, List<String> args) {
        return args.stream().filter(a -> a.startsWith(arg)).collect(Collectors.toList());
    }

    private List<String> matchTab(String arg, Stream<String> args) {
        return args.filter(a -> a.startsWith(arg)).collect(Collectors.toList());
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
                Player player = (Player)sender;
                if (!player.hasMetadata(META_LOOKUP)) return Collections.emptyList();
                actions = (List<SQLAction>)player.getMetadata(META_LOOKUP).get(0).value();
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

    void store(SQLAction action) {
        database.insertAsync(action, null);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockBreak(BlockBreakEvent event) {
        store(new SQLAction()
              .setNow().setActionType(SQLAction.Type.BLOCK_BREAK)
              .setActorPlayer(event.getPlayer())
              .setOldState(event.getBlock())
              .setNewState(Material.AIR));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockPlace(BlockPlaceEvent event) {
        store(new SQLAction()
              .setNow().setActionType(SQLAction.Type.BLOCK_PLACE)
              .setActorPlayer(event.getPlayer())
              .setOldState(event.getBlockReplacedState())
              .setNewState(event.getBlockPlaced()));
    }

    // For now, we only log player caused entity deaths.
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity().getKiller() == null) return;
        store(new SQLAction()
              .setNow().setActionType(SQLAction.Type.ENTITY_KILL)
              .setActorPlayer(event.getEntity().getKiller())
              .setOldState(event.getEntity()));
    }


    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        store(new SQLAction()
              .setNow().setActionType(SQLAction.Type.PLAYER_JOIN)
              .setActorPlayer(event.getPlayer())
              .setOldState(event.getPlayer().getLocation()));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        store(new SQLAction()
              .setNow().setActionType(SQLAction.Type.PLAYER_QUIT)
              .setActorPlayer(event.getPlayer())
              .setOldState(event.getPlayer().getLocation()));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onEntityExplode(EntityExplodeEvent event) {
        for (Block block: event.blockList()) {
            store(new SQLAction()
                  .setNow().setActionType(SQLAction.Type.BLOCK_EXPLODE)
                  .setActorEntity(event.getEntity())
                  .setOldState(block));
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockExplode(BlockExplodeEvent event) {
        for (Block block: event.blockList()) {
            store(new SQLAction()
                  .setNow().setActionType(SQLAction.Type.BLOCK_EXPLODE)
                  .setActorBlock(event.getBlock())
                  .setOldState(block));
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!event.getPlayer().hasMetadata(TOOL_KEY)) return;
        if (!event.getPlayer().hasPermission("watchman.watchman")) return;
        event.setCancelled(true);
        Block block;
        switch (event.getAction()) {
        case LEFT_CLICK_BLOCK:
            block = event.getClickedBlock();
            break;
        case RIGHT_CLICK_BLOCK:
            block = event.getClickedBlock().getRelative(event.getBlockFace());
            break;
        default:
            return;
        }
        Player player = event.getPlayer();
        String world = block.getWorld().getName();
        int x = block.getX(), y = block.getY(), z = block.getZ();
        LookupMeta meta = new LookupMeta();
        meta.world = world;
        meta.location = new LookupMeta.Vec(x, y, z);
        player.removeMetadata(META_LOOKUP, this);
        player.removeMetadata(META_LOOKUP_META, this);
        database.find(SQLAction.class)
            .eq("world", world).eq("x", x).eq("y", y).eq("z", z)
            .orderByDescending("time")
            .findListAsync((actions) -> {
                    if (!player.isValid()) return;
                    if (actions.isEmpty()) {
                        player.sendMessage(ChatColor.RED + String.format("No actions to show at %d,%d,%d.", x, y, z));
                        return;
                    }
                    player.sendMessage(ChatColor.YELLOW + String.format("%d actions at %d,%d,%d.", actions.size(), x, y, z));
                    player.setMetadata(META_LOOKUP, new FixedMetadataValue(this, actions));
                    player.setMetadata(META_LOOKUP_META, new FixedMetadataValue(this, meta));
                    showActionPage(player, actions, meta, 0);
                });
    }

    // --- Chat UI

    void showActionPage(Player player, List<SQLAction> actions, LookupMeta meta, int page) {
        int pageLen = 5;
        int totalPageCount = (actions.size() - 1) / pageLen + 1;
        player.sendMessage(ChatColor.YELLOW + "Watchman log page " + (page + 1) + "/" + totalPageCount + ChatColor.GRAY + ChatColor.ITALIC + " (" + actions.size() + " logs)");
        StringBuilder sb = new StringBuilder();
        if (meta.location != null) {
            sb.append("location=").append(meta.world)
                .append(":").append(meta.location.x)
                .append(",").append(meta.location.y)
                .append(",").append(meta.location.y);
        } else if (meta.global) {
            sb.append("global");
        } else if (meta.worldwide) {
            sb.append("worldwide");
            sb.append(" world=").append(meta.world);
        } else {
            sb.append("radius=").append(meta.radius);
            sb.append(" center=").append(meta.world)
                .append(":").append(meta.cx)
                .append(",").append(meta.cz);
        }
        if (meta.action != null) sb.append(" action=").append(meta.action.human);
        if (meta.oldType != null) sb.append(" old=").append(meta.oldType);
        if (meta.newType != null) sb.append(" new=").append(meta.newType);
        if (meta.after != null) sb.append(" after=").append(new Date(meta.after));
        player.sendMessage("Param: " + ChatColor.GRAY + sb.toString());
        int fromIndex = page * pageLen;
        int toIndex = fromIndex + pageLen - 1;
        for (int i = fromIndex; i <= toIndex; i += 1) {
            if (i >= actions.size()) continue;
            SQLAction action = actions.get(i);
            action.showShortInfo(player, meta, i);
        }
        // Prev and Next
        ComponentBuilder cb = new ComponentBuilder("");
        cb.append("[Prev]");
        if (page > 0) {
            cb.color(ChatColor.GOLD)
                .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/watchman page " + page))
                .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(ChatColor.GOLD + "/wm page " + page)));
        } else {
            cb.color(ChatColor.DARK_GRAY);
        }
        cb.append("  ").reset();
        cb.append("[Next]");
        if (page < totalPageCount - 1) {
            cb.color(ChatColor.GOLD)
                .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/watchman page " + (page + 2)))
                .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(ChatColor.GOLD + "/wm page " + (page + 2))));
        } else {
            cb.color(ChatColor.DARK_GRAY);
        }
        player.spigot().sendMessage(cb.create());
    }
}
