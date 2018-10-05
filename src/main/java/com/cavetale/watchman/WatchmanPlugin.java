package com.cavetale.watchman;

import com.cavetale.dirty.Dirty;
import com.winthier.playercache.PlayerCache;
import com.winthier.sql.SQLDatabase;
import com.winthier.sql.SQLTable;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import javax.persistence.PersistenceException;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
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
import org.json.simple.JSONValue;

public final class WatchmanPlugin extends JavaPlugin implements Listener {
    private SQLDatabase database, asyncDatabase;
    public static final String TOOL_KEY = "Watchman.Tool";
    static final String META_LOOKUP = "watchman.lookup";
    private final LinkedBlockingQueue<SQLAction> saveQueue = new LinkedBlockingQueue();
    private int saveCount = 0;

    @Override
    public void onEnable() {
        database = new SQLDatabase(this);
        database.registerTables(SQLAction.class);
        database.createAllTables();
        asyncDatabase = database.async();
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getScheduler().runTaskAsynchronously(this, this::saveTask);
    }

    @Override
    public void onDisable() {
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
        Player player = sender instanceof Player ? (Player)sender : null;
        if (args.length == 0) return false;
        switch (args[0]) {
        case "tool":
            if (args.length == 1) {
                if (player == null) {
                    sender.sendMessage("Player expected");
                    return true;
                }
                boolean hasTool = player.hasMetadata(TOOL_KEY);
                if (hasTool) {
                    player.removeMetadata(TOOL_KEY, this);
                    sender.sendMessage("Watchman tool disabled");
                } else {
                    player.setMetadata(TOOL_KEY, new FixedMetadataValue(this, true));
                    player.sendMessage("Watchman tool enabled");
                }
                return true;
            }
            break;
        case "search":
        case "s":
        case "lookup":
        case "l":
            if (args.length > 0) {
                Location location = player != null ? player.getLocation() : getServer().getWorlds().get(0).getSpawnLocation();
                String world = location.getWorld().getName();
                int x = location.getBlockX();
                int z = location.getBlockZ();
                int radius = 128;
                boolean globalSearch = false;
                // TODO: Make a map first, search later.
                // TODO: Move to own thread. Will require new SQLDatabase for thread safety
                SQLTable<SQLAction>.Finder search = database.find(SQLAction.class);
                for (int i = 1; i < args.length; i += 1) {
                    String arg = args[i];
                    String[] toks = arg.split(":", 2);
                    if (toks.length != 2) return false;
                    switch (toks[0]) {
                    case "player": case "p":
                        if (true) {
                            UUID uuid = PlayerCache.uuidForName(toks[1]);
                            if (uuid == null) {
                                sender.sendMessage("Unknown player: " + toks[1]);
                                return true;
                            }
                            search.eq("actorId", uuid);
                        }
                        break;
                    case "action": case "a":
                        try {
                            SQLAction.Type action = SQLAction.Type.valueOf(toks[1].toUpperCase());
                            search.eq("action", action.name().toLowerCase());
                        } catch (IllegalArgumentException iae) {
                            sender.sendMessage("Unknown action: " + toks[1]);
                            return true;
                        }
                        break;
                    case "world": case "w":
                        world = toks[1];
                        break;
                    case "location": case "l":
                        if (true) {
                            String[] locs = toks[1].split(",", 2);
                            if (locs.length != 2) {
                                sender.sendMessage("2 comma separated coordinates expected, got: " + toks[1]);
                                return true;
                            }
                            try {
                                x = Integer.parseInt(locs[0]);
                                z = Integer.parseInt(locs[1]);
                            } catch (NumberFormatException nfe) {
                                sender.sendMessage("Invalid coordinates: " + toks[1]);
                                return true;
                            }
                        }
                        break;
                    case "radius": case "r":
                        switch (toks[1]) {
                        case "global": case "g":
                            globalSearch = true;
                            break;
                        default:
                            try {
                                radius = Integer.parseInt(toks[1]);
                            } catch (NumberFormatException nfe) {
                                sender.sendMessage("Invalid radius: " + toks[1]);
                                return true;
                            }
                            break;
                        }
                        break;
                    case "old": case "o":
                        search.eq("oldType", toks[1]);
                        break;
                    case "new": case "n":
                        search.eq("newType", toks[1]);
                        break;
                    case "time": case "t":
                        if (true) {
                            long seconds;
                            try {
                                seconds = Long.parseLong(toks[1]);
                            } catch (NumberFormatException nfe) {
                                sender.sendMessage("Secons expected, got: " + toks[1]);
                                return true;
                            }
                            Date time = new Date(System.currentTimeMillis() - (seconds * 1000));
                            search.gt("time", time);
                        }
                        break;
                    default:
                        sender.sendMessage("Unknown option: " + toks[0]);
                        return true;
                    }
                }
                if (!globalSearch) {
                    search.eq("world", world);
                    search.gt("x", x - radius);
                    search.lt("x", x + radius);
                    search.gt("z", z - radius);
                    search.lt("z", z + radius);
                }
                search.orderByDescending("time");
                long time = System.nanoTime();
                List<SQLAction> actions = search.findList();
                time = System.nanoTime() - time;
                double dtime = (double)time / 1000000000.0;
                sender.sendMessage(String.format("Time elapsed: %.04f", dtime));
                int actionIndex = 0;
                for (SQLAction action: actions) {
                    actionIndex += 1;
                    sendActionInfo(sender, action, actionIndex);
                }
                player.setMetadata(META_LOOKUP, new FixedMetadataValue(this, actions));
                player.sendMessage(actions.size() + " actions stored");
                return true;
            }
            break;
        case "rollback":
            if (args.length == 1 && player != null && player.hasMetadata(META_LOOKUP)) {
                @SuppressWarnings("unchecked")
                List<SQLAction> actions = (List<SQLAction>)player.getMetadata(META_LOOKUP).get(0).value();
                sender.sendMessage("Attempting to roll back " + actions.size() + " actions...");
                int count = 0;
                for (SQLAction action: actions) {
                    SQLAction.Type type;
                    try {
                        type = SQLAction.Type.valueOf(action.getAction().toUpperCase());
                    } catch (IllegalArgumentException iae) {
                        iae.printStackTrace();
                        continue;
                    }
                    switch (type) {
                    case BLOCK_BREAK:
                    case BLOCK_EXPLODE:
                        World world = Bukkit.getWorld(action.getWorld());
                        if (world == null) continue;
                        Block block = world.getBlockAt(action.getX(), action.getY(), action.getZ());
                        Material oldMaterial;
                        try {
                            oldMaterial = Material.valueOf(action.getOldType().toUpperCase());
                        } catch (IllegalArgumentException iae) {
                            iae.printStackTrace();
                            continue;
                        }
                        block.setType(oldMaterial, false);
                        String tag = action.getOldTag();
                        if (tag != null) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> json = (Map<String, Object>)JSONValue.parse(tag);
                            if (json != null) {
                                Dirty.setBlockTag(block, json);
                            }
                        }
                        count += 1;
                        break;
                    default: break;
                    }
                }
                sender.sendMessage("Successfully rolled back " + count + " actions");
                return true;
            }
            break;
        case "clear":
            if (args.length == 1 && player != null) {
                if (player.hasMetadata(META_LOOKUP)) {
                    player.removeMetadata(META_LOOKUP, this);
                    player.sendMessage("Search cleared");
                } else {
                    player.sendMessage("You have no stored search");
                }
                return true;
            }
            break;
        case "debug":
            if (args.length == 1) {
                sender.sendMessage("Actions saved: " + saveCount);
            }
            break;
        default:
            break;
        }
        return false;
    }

    private void saveTask() {
        while (isEnabled()) {
            try {
                SQLAction action = saveQueue.poll(1, TimeUnit.SECONDS);
                if (action != null) {
                    asyncDatabase.save(action);
                    saveCount += 1;
                } else {
                    Thread.sleep(1000L);
                }
            } catch (InterruptedException ie) {
            }
        }
    }

    void store(SQLAction action) {
        action.truncate();
        saveQueue.offer(action);
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
        List<SQLAction> actions = database.find(SQLAction.class)
            .eq("world", world)
            .eq("x", x)
            .eq("y", y)
            .eq("z", z)
            .orderByDescending("time")
            .findList();
        if (actions.isEmpty()) {
            player.sendMessage(String.format("No actions to show at %s:%d,%d,%d.", world, x, y, z));
            return;
        }
        player.sendMessage(String.format("%d actions at %s:%d,%d,%d:", actions.size(), world, x, y, z));
        int actionIndex = 0;
        for (SQLAction action: actions) {
            actionIndex += 1;
            sendActionInfo(player, action, actionIndex);
        }
    }

    public void sendActionInfo(CommandSender sender, SQLAction action, int actionIndex) {
        StringBuilder sb = new StringBuilder();
        sb.append(actionIndex);
        sb.append(" ");
        GregorianCalendar calNow = new GregorianCalendar();
        calNow.setTime(new Date());
        GregorianCalendar calThen = new GregorianCalendar();
        calThen.setTime(action.getTime());
        if (calNow.get(Calendar.YEAR) != calThen.get(Calendar.YEAR)) {
            sb.append(new SimpleDateFormat("YY/MMM/dd HH:mm").format(action.getTime()));
        } else if (calNow.get(Calendar.MONTH) != calThen.get(Calendar.MONTH)) {
            sb.append(new SimpleDateFormat("MMM/dd HH:mm").format(action.getTime()));
        } else if (calNow.get(Calendar.DAY_OF_MONTH) != calThen.get(Calendar.DAY_OF_MONTH)) {
            sb.append(new SimpleDateFormat("EEE/dd HH:mm").format(action.getTime()));
        } else {
            sb.append(new SimpleDateFormat("HH:mm:ss").format(action.getTime()));
        }
        sb.append(" ");
        if (action.getActorType().equals("player")) {
            sb.append(PlayerCache.nameForUuid(action.getActorId()));
        } else {
            sb.append(action.getActorType());
        }
        sb.append(" ");
        sb.append(action.getAction());
        sb.append(" ");
        sb.append(action.getWorld());
        sb.append(":");
        sb.append(action.getX());
        sb.append(",");
        sb.append(action.getY());
        sb.append(",");
        sb.append(action.getZ());
        SQLAction.Type type = SQLAction.Type.valueOf(action.getAction().toUpperCase());
        boolean showOld, showNew;
        switch (type) {
        case BLOCK_BREAK: showOld = true; showNew = false; break;
        case BLOCK_PLACE: showOld = false; showNew = true; break;
        case PLAYER_JOIN: showOld = false; showNew = false; break;
        case PLAYER_QUIT: showOld = false; showNew = false; break;
        default: showOld = true; showNew = true; break;
        }
        if (showOld && action.getOldType() != null) {
            sb.append(" ");
            sb.append(action.getOldType());
            if (action.getOldTag() != null) {
                sb.append(action.getOldTag());
            }
        }
        if (showNew && action.getNewType() != null) {
            sb.append(" ");
            sb.append(action.getNewType());
            if (action.getNewTag() != null) {
                sb.append(action.getNewTag());
            }
        }
        sender.sendMessage(sb.toString());
    }
}
