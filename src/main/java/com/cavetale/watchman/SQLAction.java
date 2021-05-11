package com.cavetale.watchman;

import com.cavetale.core.util.Json;
import com.cavetale.dirty.Dirty;
import com.winthier.generic_events.GenericEvents;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.EnumSet;
import java.util.GregorianCalendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Table;
import lombok.Data;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

@Data
@Table(name = "actions",
       indexes = {@Index(name = "id_actor_id", columnList = "actor_id"),
                  @Index(name = "id_time", columnList = "time"),
                  @Index(name = "id_world", columnList = "world"),
                  @Index(name = "id_action", columnList = "action"),
                  @Index(name = "id_type", columnList = "type"),
                  @Index(name = "id_xz", columnList = "x,z")})
public final class SQLAction {
    private static final int MAX_TAG_LENGTH = 8192;
    @Id private Integer id;
    // Action
    @Column(nullable = false) private Date time;
    @Column(nullable = false, length = 31) private String action; // enum Type, e.g. block_break
    @Column(nullable = true, length = 255) private String type; // Material or EntityType
    // Actor
    @Column(nullable = true) private UUID actorId; // Player unique id
    @Column(nullable = false, length = 63) private String actorType; // Entity type
    @Column(nullable = true, length = 255) private String actorName; // Entity name
    // Object location
    @Column(nullable = false, length = 31) private String world;
    @Column(nullable = false) private int x;
    @Column(nullable = false) private int y;
    @Column(nullable = false) private int z;
    // Object old state
    @Column(nullable = true, length = 255)
    private String oldType; // e.g. diamond_block
    @Column(nullable = true, length = MAX_TAG_LENGTH)
    private String oldTag; // Old NBT tag, if available
    // Object new state
    @Column(nullable = true, length = 255)
    private String newType; // New material type, only for blocks (or spawned entities?)
    @Column(nullable = true, length = MAX_TAG_LENGTH)
    private String newTag; // New NBT tag, only for blocks, if available

    public enum Type {
        PLAYER_JOIN("join", Category.PLAYER),
        PLAYER_QUIT("quit", Category.PLAYER),
        PLAYER_DEATH("death", Category.PLAYER),
        BLOCK_BREAK("break", Category.BLOCK),
        BLOCK_PLACE("place", Category.BLOCK),
        BLOCK_CHANGE("change", Category.BLOCK),
        BLOCK_GROW("grow", Category.BLOCK),
        BLOCK_FORM("form", Category.BLOCK),
        BLOCK_SHEAR("shear", Category.BLOCK),
        BLOCK_DESTROY("destroy", Category.BLOCK),
        BLOCK_FAKE("fake", Category.BLOCK),
        BUCKET_EMPTY("bucket", Category.BLOCK),
        BUCKET_FILL("debucket", Category.BLOCK),
        BLOCK_EXPLODE("explode", Category.BLOCK),
        ENTITY_KILL("kill", Category.ENTITY),
        ITEM_DROP("drop", Category.INVENTORY),
        ITEM_PICKUP("pickup", Category.INVENTORY),
        INVENTORY_OPEN("open", Category.INVENTORY),
        COMMAND("command", Category.CHAT),
        CHAT("chat", Category.CHAT);

        public enum Category {
            /**
             * Block changing from oldState to newState.
             */
            BLOCK,

            /**
             * Player related.
             */
            PLAYER,

            /**
             * Entity related.
             */
            ENTITY,

            INVENTORY,
            CHAT;
        }

        public final String key;
        public final String human;
        public final Category category;

        Type(final String human, final Category category) {
            this.key = name().toLowerCase();
            this.human = human;
            this.category = category;
        }

        public static List<String> inCategory(Category category) {
            return Stream.of(Type.values())
                .filter(t -> t.category == category)
                .map(t -> t.key)
                .collect(Collectors.toList());
        }

        public static List<String> inCategories(Category first, Category... others) {
            Set<Category> set = EnumSet.of(first, others);
            return Stream.of(Type.values())
                .filter(t -> set.contains(t.category))
                .map(t -> t.key)
                .collect(Collectors.toList());
        }
    }

    public Type getActionType() {
        if (action == null) return null;
        try {
            return Type.valueOf(action.toUpperCase());
        } catch (IllegalArgumentException iae) {
            WatchmanPlugin.getInstance().getLogger().warning("Unknown action type: " + action);
            return null;
        }
    }

    public SQLAction setActionType(Type actionType) {
        action = actionType.name().toLowerCase();
        return this;
    }

    public SQLAction setNow() {
        time = new Date();
        return this;
    }

    public SQLAction time(long now) {
        time = new Date(now);
        return this;
    }

    public SQLAction setActorPlayer(Player player) {
        if (player == null) {
            actorType = "unknown";
            return this;
        }
        actorId = player.getUniqueId();
        actorType = player.getType().getKey().getKey();
        actorName = player.getName();
        return this;
    }

    public SQLAction setActorEntity(Entity entity) {
        if (entity == null) {
            actorType = "unknown";
            return this;
        }
        if (entity instanceof Player) return setActorPlayer((Player) entity);
        actorId = entity.getUniqueId();
        actorType = entity.getType().getKey().getKey();
        actorName = entity.getCustomName();
        return this;
    }

    public SQLAction setActorBlock(Block block) {
        actorType = "block";
        setActorName(block != null ? block.getType().getKey().getKey() : "air");
        return this;
    }

    public SQLAction setActorTypeName(String name) {
        actorType = name;
        return this;
    }

    public SQLAction setOldState(Block block) {
        world = block.getWorld().getName();
        x = block.getX();
        y = block.getY();
        z = block.getZ();
        oldType = block.getBlockData().getAsString(false);
        Map<String, Object> tag = Dirty.getBlockTag(block);
        if (tag != null) {
            oldTag = Json.serialize(tag);
        } else {
            oldTag = null;
        }
        return this;
    }

    public SQLAction setOldState(BlockState blockState) {
        world = blockState.getWorld().getName();
        x = blockState.getX();
        y = blockState.getY();
        z = blockState.getZ();
        oldType = blockState.getBlockData().getAsString(false);
        Map<String, Object> tag = Dirty.getBlockTag(blockState);
        if (tag != null) {
            oldTag = Json.serialize(tag);
        } else {
            oldTag = null;
        }
        return this;
    }

    public SQLAction setOldState(BlockData blockData) {
        oldType = blockData.getAsString(false);
        return this;
    }

    public SQLAction setNewState(Material mat) {
        newType = mat.createBlockData().getAsString(false);
        newTag = null;
        return this;
    }

    public SQLAction setNewState(Block block) {
        newType = block.getBlockData().getAsString(false);
        Map<String, Object> tag = Dirty.getBlockTag(block);
        if (tag != null) {
            newTag = Json.serialize(tag);
        } else {
            newTag = null;
        }
        return this;
    }

    public SQLAction setNewState(BlockState blockState) {
        newType = blockState.getBlockData().getAsString(false);
        Map<String, Object> tag = Dirty.getBlockTag(blockState);
        if (tag != null) {
            newTag = Json.serialize(tag);
        } else {
            newTag = null;
        }
        return this;
    }

    public SQLAction setNewState(BlockData blockData) {
        newType = blockData.getAsString(false);
        return this;
    }

    public SQLAction setNewState(Entity entity) {
        Location location = entity.getLocation();
        world = location.getWorld().getName();
        x = location.getBlockX();
        y = location.getBlockY();
        z = location.getBlockZ();
        newType = entity.getType().getKey().getKey();
        Map<String, Object> tag = Dirty.getEntityTag(entity);
        if (tag != null) {
            newTag = Json.serialize(tag);
        } else {
            newTag = null;
        }
        return this;
    }

    public SQLAction setOldState(Entity entity) {
        Location location = entity.getLocation();
        world = location.getWorld().getName();
        x = location.getBlockX();
        y = location.getBlockY();
        z = location.getBlockZ();
        oldType = entity.getType().getKey().getKey();
        Map<String, Object> tag = Dirty.getEntityTag(entity);
        if (tag != null) {
            oldTag = Json.serialize(tag);
        } else {
            oldTag = null;
        }
        return this;
    }

    public SQLAction setOldState(Location location) {
        world = location.getWorld().getName();
        x = location.getBlockX();
        y = location.getBlockY();
        z = location.getBlockZ();
        oldType = "location";
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("x", location.getX());
        map.put("y", location.getY());
        map.put("z", location.getZ());
        map.put("pitch", location.getPitch());
        map.put("yaw", location.getYaw());
        oldTag = Json.serialize(map);
        return this;
    }

    public SQLAction setOldState(ItemStack item) {
        oldType = item.getType().getKey().getKey();
        Map<String, Object> tag = Dirty.getItemTag(item);
        if (tag != null) {
            oldTag = Json.serialize(tag);
        } else {
            oldTag = null;
        }
        return this;
    }

    public SQLAction setNewState(ItemStack item) {
        newType = item.getType().getKey().getKey();
        Map<String, Object> tag = Dirty.getItemTag(item);
        if (tag != null) {
            newTag = Json.serialize(tag);
        } else {
            newTag = null;
        }
        return this;
    }

    public SQLAction setNewState(String string) {
        newType = "text";
        newTag = string;
        return this;
    }

    public SQLAction setLocation(Location location) {
        world = location.getWorld().getName();
        x = location.getBlockX();
        y = location.getBlockY();
        z = location.getBlockZ();
        return this;
    }

    public SQLAction setLocation(Block block) {
        world = block.getWorld().getName();
        x = block.getX();
        y = block.getY();
        z = block.getZ();
        return this;
    }

    public SQLAction setMaterial(Material material) {
        this.type = material.getKey().getKey();
        return this;
    }

    public SQLAction setEntityType(EntityType entityType) {
        this.type = entityType.getKey().getKey();
        return this;
    }

    public BlockData getOldBlockData() {
        if (oldType == null) {
            return Material.AIR.createBlockData();
        }
        try {
            return Bukkit.createBlockData(oldType);
        } catch (IllegalArgumentException iae) {
            System.err.println("Bukkit.createBlockData(" + oldType + ")");
            iae.printStackTrace();
        }
        return Material.AIR.createBlockData();
    }

    public BlockData getNewBlockData() {
        if (newType == null) {
            return Material.AIR.createBlockData();
        }
        try {
            return Bukkit.createBlockData(newType);
        } catch (IllegalArgumentException iae) {
            iae.printStackTrace();
        }
        return Material.AIR.createBlockData();
    }

    boolean rollback() {
        Type actionType = getActionType();
        switch (actionType.category) {
        case BLOCK:
            World bworld = Bukkit.getWorld(world);
            if (bworld == null) return false;
            Block block = bworld.getBlockAt(x, y, z);
            BlockData oldData;
            if (oldType == null) {
                // legacy
                oldData = Material.AIR.createBlockData();
            } else {
                try {
                    oldData = Bukkit.createBlockData(oldType);
                } catch (IllegalArgumentException iae) {
                    iae.printStackTrace();
                    return false;
                }
            }
            block.setBlockData(oldData, false);
            if (oldTag != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> json = (Map<String, Object>) Json.deserialize(oldTag, Map.class);
                if (json != null) {
                    Dirty.setBlockTag(block, json);
                }
            }
            return true;
        case CHAT: case INVENTORY: case PLAYER:
            return false;
        case ENTITY:
        default:
            WatchmanPlugin.getInstance().getLogger().warning("Unable to rollback type " + actionType);
            return false;
        }
    }

    public void showDetails(CommandSender sender, int index) {
        StringBuilder sb = new StringBuilder();
        sb.append(index);
        sb.append(" ");
        GregorianCalendar calNow = new GregorianCalendar();
        calNow.setTime(new Date());
        GregorianCalendar calThen = new GregorianCalendar();
        calThen.setTime(time);
        if (calNow.get(Calendar.YEAR) != calThen.get(Calendar.YEAR)) {
            sb.append(new SimpleDateFormat("YY/MMM/dd HH:mm").format(time));
        } else if (calNow.get(Calendar.MONTH) != calThen.get(Calendar.MONTH)) {
            sb.append(new SimpleDateFormat("MMM/dd HH:mm").format(time));
        } else if (calNow.get(Calendar.DAY_OF_MONTH) != calThen.get(Calendar.DAY_OF_MONTH)) {
            sb.append(new SimpleDateFormat("EEE/dd HH:mm").format(time));
        } else {
            sb.append(new SimpleDateFormat("HH:mm:ss").format(time));
        }
        sb.append(" ");
        if (actorType.equals("player")) {
            sb.append(GenericEvents.cachedPlayerName(actorId));
        } else {
            sb.append(actorType);
        }
        sb.append(" ");
        sb.append(action);
        sb.append(" ");
        sb.append(world);
        sb.append(":");
        sb.append(x);
        sb.append(",");
        sb.append(y);
        sb.append(",");
        sb.append(z);
        Type actionType = getActionType();
        final boolean showOld = oldType != null || oldTag != null;
        final boolean showNew = newType != null || newTag != null;
        if (showOld) {
            sb.append(" ");
            sb.append(oldType != null ? oldType : "unknown");
            if (oldTag != null) {
                sb.append(oldTag);
            }
        }
        if (showNew) {
            sb.append(" ");
            sb.append(newType != null ? newType : "unknown");
            if (newTag != null) {
                sb.append(newTag);
            }
        }
        sender.sendMessage(sb.toString());
    }

    public void showShortInfo(Player player, LookupMeta meta, int index) {
        ComponentBuilder cb = new ComponentBuilder("[" + index + "]").color(ChatColor.YELLOW);
        cb.event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/watchman info " + index));
        BaseComponent[] lore = TextComponent
            .fromLegacyText(ChatColor.GOLD + "/watchman info " + index);
        cb.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, lore));
        cb.append(" ").reset();
        long interval = System.currentTimeMillis() - time.getTime();
        TimeUnit unit = TimeUnit.MILLISECONDS;
        long days = unit.toDays(interval);
        long hours = unit.toHours(interval) % 24L;
        long minutes = unit.toMinutes(interval) % 60L;
        long seconds = unit.toSeconds(interval) % 60L;
        long millis = unit.toMillis(interval) % 1000L;
        lore = TextComponent
            .fromLegacyText(new SimpleDateFormat("YY/MM/dd HH:mm:ss.SSS").format(time));
        // Build time format
        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append("" + ChatColor.DARK_RED + days + "d");
        if (hours > 0) sb.append("" + ChatColor.RED + hours + "h");
        sb.append(String.format(ChatColor.GRAY + "%02d:%02d.%03d", minutes, seconds, millis));
        //
        cb.append(sb.toString()).color(ChatColor.GRAY);
        cb.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, lore));
        cb.append(" ").reset();
        if (actorType == null) {
            cb.append("null").color(ChatColor.RED);
        } else if (actorType.equals("player")) {
            String name = GenericEvents.cachedPlayerName(actorId);
            lore = TextComponent
                .fromLegacyText("Name: " + name + "\n" + "UUID: " + actorId);
            cb.append(name).color(ChatColor.BLUE);
            cb.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, lore));
            cb.event(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/msg " + name));
            cb.insertion("" + actorId);
        } else if (actorType.equals("nature")) {
            cb.append("nature").color(ChatColor.GREEN);
        } else if (actorType.equals("unknown")) {
            cb.append("unknown").color(ChatColor.RED);
        } else {
            lore = TextComponent
                .fromLegacyText("Type: " + actorType + "\n" + "UUID: " + actorId);
            cb.append(actorType).color(ChatColor.DARK_GREEN);
            cb.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, lore));
            cb.event(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
                                    "/summon " + actorType));
            cb.insertion("" + actorId);
        }
        cb.append(" ");
        Type actionType = getActionType();
        if (actionType == null) {
            cb.append(action).color(ChatColor.DARK_RED);
        } else {
            cb.append(actionType.human).color(ChatColor.YELLOW);
        }
        if (type != null) {
            cb.append(" ").append(type).color(ChatColor.YELLOW);
        }
        if (meta.location == null) {
            cb.append(" ");
            cb.append(world).color(ChatColor.DARK_GRAY).insertion(world);
            cb.append(":");
            String coords = x + "," + y + "," + z;
            String coords2 = x + " " + y + " " + z;
            cb.append(coords).color(ChatColor.DARK_GRAY).insertion(coords2);
            cb.event(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/tp " + coords2));
            cb.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                    TextComponent.fromLegacyText(coords)));
        }
        final boolean showOld = oldType != null || oldTag != null;
        final boolean showNew = newType != null || newTag != null;
        if (showOld) {
            cb.append(" ");
            List<String> lines = new ArrayList<>(2);
            if (oldType != null) lines.add(ChatColor.GRAY + "Type " + ChatColor.WHITE + oldType);
            if (oldTag != null) lines.add(ChatColor.GRAY + "Tag " + ChatColor.WHITE + oldTag);
            lore = TextComponent.fromLegacyText(String.join("\n", lines));
            String text = oldType != null ? oldType : "Unknown";
            cb.append(text).color(ChatColor.RED);
            cb.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, lore));
            cb.insertion(text);
        }
        if (showNew) {
            if (showOld) cb.append(" =>").color(ChatColor.DARK_GRAY);
            cb.append(" ");
            List<String> lines = new ArrayList<>(2);
            if (newType != null) lines.add(ChatColor.GRAY + "Type " + ChatColor.WHITE + newType);
            if (newTag != null) lines.add(ChatColor.GRAY + "Tag " + ChatColor.WHITE + newTag);
            lore = TextComponent.fromLegacyText(String.join("\n", lines));
            String text = newType != null ? newType : "Unknown";
            cb.append(text).color(ChatColor.GREEN);
            cb.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, lore));
            cb.insertion(text);
        }
        player.spigot().sendMessage(cb.create());
    }

    public Vec3i getVector() {
        return new Vec3i(x, y, z);
    }

    public Block getBlock() {
        World bw = Bukkit.getWorld(world);
        if (bw == null) return null;
        return bw.getBlockAt(x, y, z);
    }
}
