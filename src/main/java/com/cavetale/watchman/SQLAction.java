package com.cavetale.watchman;

import com.cavetale.dirty.Dirty;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Table;
import lombok.Data;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.json.simple.JSONValue;

@Data @Table(name = "actions")
public final class SQLAction {
    private static final int MAX_TAG_LENGTH = 4096;
    @Id private Integer id;
    // Action
    @Column(nullable = false) private Date time;
    @Column(nullable = false) private String action; // enum Type, e.g. block_break
    // Actor
    @Column(nullable = true) private UUID actorId; // Player unique id
    @Column(nullable = false) private String actorType; // Entity type
    @Column(nullable = true) private String actorName; // Entity name
    // Object old state
    @Column(nullable = false) private String world;
    @Column(nullable = false) private Integer x, y, z;
    @Column(nullable = true) private String oldType; // e.g. diamond_block
    @Column(nullable = true, length = MAX_TAG_LENGTH) private String oldTag; // Old NBT tag, if available
    // Object new state
    @Column(nullable = true) private String newType; // New material type, only for blocks (or spawned entities?)
    @Column(nullable = true, length = MAX_TAG_LENGTH) private String newTag; // New NBT tag, only for blocks, if available

    enum Type {
        PLAYER_JOIN,
        PLAYER_QUIT,
        BLOCK_BREAK,
        BLOCK_PLACE,
        BLOCK_EXPLODE,
        ENTITY_KILL;
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
        if (blockState.getType() == Material.AIR) {
            oldType = null;
            oldTag = null;
        } else {
            oldType = blockState.getType().name().toLowerCase();
            Map<String, Object> tag = Dirty.getBlockTag(blockState);
            if (tag != null) {
                this.oldTag = JSONValue.toJSONString(tag);
            } else {
                this.oldTag = null;
            }
        }
        return this;
    }

    public SQLAction setNewState(Material mat) {
        if (mat == Material.AIR) {
            this.newType = null;
            this.newTag = null;
        } else {
            this.newType = mat.name().toLowerCase();
            this.newTag = null;
        }
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

    void truncate() {
        if (oldTag != null && oldTag.length() > MAX_TAG_LENGTH) {
            System.err.println("Warning: old_tag too long, truncating: " + oldTag);
            oldTag = oldTag.substring(0, MAX_TAG_LENGTH);
        }
        if (newTag != null && newTag.length() > MAX_TAG_LENGTH) {
            System.err.println("Warning: new_tag too long, truncating: " + newTag);
            newTag = newTag.substring(0, MAX_TAG_LENGTH);
        }
    }
}
