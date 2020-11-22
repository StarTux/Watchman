package com.cavetale.watchman;

import com.cavetale.core.util.Json;
import com.cavetale.dirty.Dirty;
import com.winthier.generic_events.GenericEvents;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import org.bukkit.entity.Player;

@Data
@Table(name = "actions",
       indexes = {@Index(name = "id_actor_id", columnList = "actor_id"),
                  @Index(name = "id_time", columnList = "time"),
                  @Index(name = "id_world", columnList = "world"),
                  @Index(name = "id_action", columnList = "action"),
                  @Index(name = "id_xz", columnList = "x,z")})
public final class SQLAction {
    private static final int MAX_TAG_LENGTH = 8192;
    @Id private Integer id;
    // Action
    @Column(nullable = false) private Date time;
    @Column(nullable = false, length = 31) private String action; // enum Type, e.g. block_break
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
        BLOCK_BREAK("break", Category.BLOCK),
        BLOCK_PLACE("place", Category.BLOCK),
        BLOCK_EXPLODE("explode", Category.BLOCK),
        ENTITY_KILL("kill", Category.ENTITY);

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
            ENTITY;
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
    }

    public Type getType() {
        if (action == null) return null;
        try {
            return Type.valueOf(this.action.toUpperCase());
        } catch (IllegalArgumentException iae) {
            WatchmanPlugin.getInstance().getLogger().warning("Unknown action type: " + this.action);
            return null;
        }
    }

    public SQLAction setActionType(Type type) {
        this.action = type.name().toLowerCase();
        return this;
    }

    public SQLAction setNow() {
        this.time = new Date();
        return this;
    }

    public SQLAction setActorPlayer(Player player) {
        this.actorId = player.getUniqueId();
        this.actorType = player.getType().name().toLowerCase();
        this.actorName = player.getName();
        return this;
    }

    public SQLAction setActorEntity(Entity entity) {
        this.actorId = entity.getUniqueId();
        this.actorType = entity.getType().name().toLowerCase();
        this.actorName = entity.getCustomName();
        return this;
    }

    public SQLAction setActorBlock(Block block) {
        this.actorType = "block";
        this.setActorName(block.getType().name().toLowerCase());
        return this;
    }

    public SQLAction setOldState(Block block) {
        this.world = block.getWorld().getName();
        this.x = block.getX();
        this.y = block.getY();
        this.z = block.getZ();
        oldType = block.getBlockData().getAsString(false);
        Map<String, Object> tag = Dirty.getBlockTag(block);
        if (tag != null) {
            this.oldTag = Json.serialize(tag);
        } else {
            this.oldTag = null;
        }
        return this;
    }

    public SQLAction setOldState(BlockState blockState) {
        this.world = blockState.getWorld().getName();
        this.x = blockState.getX();
        this.y = blockState.getY();
        this.z = blockState.getZ();
        oldType = blockState.getBlockData().getAsString(false);
        Map<String, Object> tag = Dirty.getBlockTag(blockState);
        if (tag != null) {
            this.oldTag = Json.serialize(tag);
        } else {
            this.oldTag = null;
        }
        return this;
    }

    public SQLAction setNewState(Material mat) {
        this.newType = mat.createBlockData().getAsString(false);
        this.newTag = null;
        return this;
    }

    public SQLAction setNewState(Block block) {
        this.newType = block.getBlockData().getAsString(false);
        Map<String, Object> tag = Dirty.getBlockTag(block);
        if (tag != null) {
            this.newTag = Json.serialize(tag);
        } else {
            this.newTag = null;
        }
        return this;
    }

    public SQLAction setNewState(BlockState blockState) {
        this.world = blockState.getWorld().getName();
        this.x = blockState.getX();
        this.y = blockState.getY();
        this.z = blockState.getZ();
        newType = blockState.getBlockData().getAsString(false);
        Map<String, Object> tag = Dirty.getBlockTag(blockState);
        if (tag != null) {
            this.newTag = Json.serialize(tag);
        } else {
            this.newTag = null;
        }
        return this;
    }

    public SQLAction setOldState(Entity entity) {
        Location location = entity.getLocation();
        this.world = location.getWorld().getName();
        this.x = location.getBlockX();
        this.y = location.getBlockY();
        this.z = location.getBlockZ();
        this.oldType = entity.getType().name().toLowerCase();
        Map<String, Object> tag = Dirty.getEntityTag(entity);
        if (tag != null) {
            this.oldTag = Json.serialize(tag);
        } else {
            this.oldTag = null;
        }
        return this;
    }

    public SQLAction setOldState(Location location) {
        this.world = location.getWorld().getName();
        this.x = location.getBlockX();
        this.y = location.getBlockY();
        this.z = location.getBlockZ();
        this.oldType = "location";
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("x", location.getX());
        map.put("y", location.getY());
        map.put("z", location.getZ());
        map.put("pitch", location.getPitch());
        map.put("yaw", location.getYaw());
        this.oldTag = Json.serialize(map);
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
        if (this.newType == null) {
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
        Type type = getType();
        switch (type) {
        case BLOCK_BREAK:
        case BLOCK_EXPLODE:
        case BLOCK_PLACE:
            World bworld = Bukkit.getWorld(this.world);
            if (bworld == null) return false;
            Block block = bworld.getBlockAt(this.x, this.y, this.z);
            BlockData oldData;
            if (this.oldType == null) {
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
            if (this.oldTag != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> json = (Map<String, Object>) Json.deserialize(this.oldTag, Map.class);
                if (json != null) {
                    Dirty.setBlockTag(block, json);
                }
            }
            return true;
        default:
            WatchmanPlugin.getInstance().getLogger().warning("Unable to rollback type " + type);
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
        calThen.setTime(this.time);
        if (calNow.get(Calendar.YEAR) != calThen.get(Calendar.YEAR)) {
            sb.append(new SimpleDateFormat("YY/MMM/dd HH:mm").format(this.time));
        } else if (calNow.get(Calendar.MONTH) != calThen.get(Calendar.MONTH)) {
            sb.append(new SimpleDateFormat("MMM/dd HH:mm").format(this.time));
        } else if (calNow.get(Calendar.DAY_OF_MONTH) != calThen.get(Calendar.DAY_OF_MONTH)) {
            sb.append(new SimpleDateFormat("EEE/dd HH:mm").format(this.time));
        } else {
            sb.append(new SimpleDateFormat("HH:mm:ss").format(this.time));
        }
        sb.append(" ");
        if (this.actorType.equals("player")) {
            sb.append(GenericEvents.cachedPlayerName(this.actorId));
        } else {
            sb.append(this.actorType);
        }
        sb.append(" ");
        sb.append(this.action);
        sb.append(" ");
        sb.append(this.world);
        sb.append(":");
        sb.append(this.x);
        sb.append(",");
        sb.append(this.y);
        sb.append(",");
        sb.append(this.z);
        Type type = this.getType();
        boolean showOld;
        boolean showNew;
        if (type == null) {
            showOld = true;
            showNew = true;
        } else {
            switch (type) {
            case BLOCK_BREAK: showOld = true; showNew = false; break;
            case BLOCK_PLACE: showOld = false; showNew = true; break;
            case PLAYER_JOIN: showOld = false; showNew = false; break;
            case PLAYER_QUIT: showOld = false; showNew = false; break;
            default: showOld = true; showNew = true; break;
            }
        }
        if (showOld && this.oldType != null) {
            sb.append(" ");
            sb.append(this.oldType);
            if (this.oldTag != null) {
                sb.append(this.oldTag);
            }
        }
        if (showNew && this.newType != null) {
            sb.append(" ");
            sb.append(this.newType);
            if (this.newTag != null) {
                sb.append(this.newTag);
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
        long interval = System.currentTimeMillis() - this.time.getTime();
        TimeUnit unit = TimeUnit.MILLISECONDS;
        long days = unit.toDays(interval);
        long hours = unit.toHours(interval) % 24L;
        long minutes = unit.toMinutes(interval) % 60L;
        long seconds = unit.toSeconds(interval) % 60L;
        long millis = unit.toMillis(interval) % 1000L;
        lore = TextComponent
            .fromLegacyText(new SimpleDateFormat("YY/MM/dd HH:mm:ss.SSS").format(this.time));
        // Build time format
        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append("" + ChatColor.DARK_RED + days + "d");
        if (hours > 0) sb.append("" + ChatColor.RED + hours + "h");
        sb.append(String.format(ChatColor.GRAY + "%02d:%02d.%03d", minutes, seconds, millis));
        //
        cb.append(sb.toString()).color(ChatColor.GRAY);
        cb.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, lore));
        cb.append(" ").reset();
        if (this.actorType.equals("player")) {
            String name = GenericEvents.cachedPlayerName(this.actorId);
            lore = TextComponent
                .fromLegacyText("Name: " + name + "\n" + "UUID: " + this.actorId);
            cb.append(name).color(ChatColor.BLUE);
            cb.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, lore));
            cb.event(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/msg " + name));
            cb.insertion("" + this.actorId);
        } else {
            lore = TextComponent
                .fromLegacyText("Type: " + this.actorType + "\n" + "UUID: " + this.actorId);
            cb.append(this.actorType).color(ChatColor.DARK_GREEN);
            cb.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, lore));
            cb.event(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
                                    "/summon " + this.actorType));
            cb.insertion("" + this.actorId);
        }
        cb.append(" ");
        Type type = this.getType();
        if (type == null) {
            cb.append(this.action).color(ChatColor.DARK_RED);
        } else {
            cb.append(type.human).color(ChatColor.YELLOW);
        }
        if (meta.location == null) {
            cb.append(" ");
            cb.append(this.world).color(ChatColor.DARK_GRAY).insertion(this.world);
            cb.append(":");
            String coords = this.x + "," + this.y + "," + this.z;
            String coords2 = this.x + " " + this.y + " " + this.z;
            cb.append(coords).color(ChatColor.DARK_GRAY).insertion(coords2);
            cb.event(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/tp " + coords2));
            cb.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                    TextComponent.fromLegacyText(coords)));
        }
        boolean showOld;
        boolean showNew;
        if (type == null) {
            showOld = false;
            showNew = false;
        } else {
            switch (type) {
            case BLOCK_BREAK:
            case BLOCK_EXPLODE:
                showOld = true; showNew = false;
                break;
            case BLOCK_PLACE:
                showOld = false; showNew = true;
                break;
            default: showOld = false; showNew = false; break;
            }
        }
        if (showOld && this.oldType != null) {
            cb.append(" ");
            lore = TextComponent
                .fromLegacyText("Type: " + this.oldType + "\n" + "Tag: " + this.oldTag);
            cb.append(this.oldType).color(ChatColor.RED);
            cb.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, lore));
            cb.insertion(this.oldType);
        }
        if (showNew && this.newType != null) {
            cb.append(" ");
            lore = TextComponent
                .fromLegacyText("Type: " + this.newType + "\n" + "Tag: " + this.newTag);
            cb.append(this.newType).color(ChatColor.GREEN);
            cb.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, lore));
            cb.insertion(this.newType);
        }
        player.spigot().sendMessage(cb.create());
    }

    public Vec3i getVector() {
        return new Vec3i(x, y, z);
    }
}
