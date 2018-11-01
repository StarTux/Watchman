package com.cavetale.watchman;

import com.cavetale.dirty.Dirty;
import com.winthier.generic_events.GenericEvents;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Table;
import lombok.Data;
import net.md_5.bungee.api.ChatColor;
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
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.json.simple.JSONValue;

@Data
@Table(name = "actions",
       indexes = {@Index(name="id_actor_id", columnList="actor_id"),
                  @Index(name="id_world", columnList="world"),
                  @Index(name="id_action", columnList="action"),
                  @Index(name="id_xz", columnList="x,z")})
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
    // Object old state
    @Column(nullable = false, length = 31) private String world;
    @Column(nullable = false) private Integer x, y, z;
    @Column(nullable = true, length = 63) private String oldType; // e.g. diamond_block
    @Column(nullable = true, length = MAX_TAG_LENGTH) private String oldTag; // Old NBT tag, if available
    // Object new state
    @Column(nullable = true, length = 63) private String newType; // New material type, only for blocks (or spawned entities?)
    @Column(nullable = true, length = MAX_TAG_LENGTH) private String newTag; // New NBT tag, only for blocks, if available

    enum Type {
        PLAYER_JOIN("join"),
        PLAYER_QUIT("quit"),
        BLOCK_BREAK("break"),
        BLOCK_PLACE("place"),
        BLOCK_EXPLODE("explode"),
        ENTITY_KILL("kill");
        final String human;
        Type(String human) {
            this.human = human;
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
        oldType = block.getType().name().toLowerCase();
        Map<String, Object> tag = Dirty.getBlockTag(block);
        if (tag != null) {
            this.oldTag = JSONValue.toJSONString(tag);
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
        oldType = blockState.getType().name().toLowerCase();
        Map<String, Object> tag = Dirty.getBlockTag(blockState);
        if (tag != null) {
            this.oldTag = JSONValue.toJSONString(tag);
        } else {
            this.oldTag = null;
        }
        return this;
    }

    public SQLAction setNewState(Material mat) {
        this.newType = mat.name().toLowerCase();
        this.newTag = null;
        return this;
    }

    public SQLAction setNewState(Block block) {
        this.newType = block.getType().name().toLowerCase();
        Map<String, Object> tag = Dirty.getBlockTag(block);
        if (tag != null) {
            this.newTag = JSONValue.toJSONString(tag);
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
            this.oldTag = JSONValue.toJSONString(tag);
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
        this.oldTag = JSONValue.toJSONString(map);
        return this;
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
            Material oldMaterial;
            if (this.oldType == null) {
                // legacy
                oldMaterial = Material.AIR;
            } else {
                try {
                    oldMaterial = Material.valueOf(this.oldType.toUpperCase());
                } catch (IllegalArgumentException iae) {
                    iae.printStackTrace();
                    return false;
                }
            }
            block.setType(oldMaterial, false);
            if (this.oldTag != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> json = (Map<String, Object>)JSONValue.parse(this.oldTag);
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
        boolean showOld, showNew;
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
        ComponentBuilder cb = new ComponentBuilder("[" + index + "]")
            .color(ChatColor.YELLOW)
            .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/watchman info " + index))
            .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(ChatColor.GOLD + "/watchman info " + index)));
        cb.append(" ").reset();
        long interval = System.currentTimeMillis() - this.time.getTime();
        TimeUnit unit = TimeUnit.MILLISECONDS;
        long days = unit.toDays(interval);
        long hours = unit.toHours(interval) % 24L;
        long minutes = unit.toMinutes(interval) % 60L;
        long seconds = unit.toSeconds(interval) % 60L;
        long millis = unit.toMillis(interval) % 1000L;
        HoverEvent hover = new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(new SimpleDateFormat("YY/MM/dd HH:mm:ss.SSS").format(this.time)));
        ClickEvent click;
        if (days > 0) cb.append(days + "d").color(ChatColor.DARK_RED).event(hover);
        if (hours > 0) cb.append("" + hours + "h").color(ChatColor.RED).event(hover);
        cb.append(String.format("%02d:%02d.%03d", minutes, seconds, millis)).color(ChatColor.GRAY).event(hover);
        cb.append(" ").reset();
        if (this.actorType.equals("player")) {
            String name = GenericEvents.cachedPlayerName(this.actorId);
            hover = new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText("Name: " + name + "\n" + "UUID: " + this.actorId));
            click = new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/msg " + name);
            cb.append(name).color(ChatColor.BLUE).event(hover).insertion("" + this.actorId).event(click);
        } else {
            hover = new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText("Type: " + this.actorType + "\n" + "UUID: " + this.actorId));
            click = new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/summon " + this.actorType);
            cb.append(this.actorType).color(ChatColor.DARK_GREEN).event(hover).insertion("" + this.actorId).event(click);
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
            cb.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(coords)));
        }
        boolean showOld, showNew;
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
            hover = new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText("Type: " + this.oldType + "\n" + "Tag: " + this.oldTag));
            cb.append(this.oldType).color(ChatColor.RED).event(hover).insertion(this.oldType);
        }
        if (showNew && this.newType != null) {
            cb.append(" ");
            hover = new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText("Type: " + this.newType + "\n" + "Tag: " + this.newTag));
            cb.append(this.newType).color(ChatColor.GREEN).event(hover).insertion(this.newType);
        }
        player.spigot().sendMessage(cb.create());
    }
}
