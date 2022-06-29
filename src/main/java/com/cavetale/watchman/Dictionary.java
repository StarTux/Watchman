package com.cavetale.watchman;

import com.cavetale.watchman.sql.SQLDictionary;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.EntityType;
import static com.cavetale.watchman.WatchmanPlugin.database;

/**
 * Methods in this class are only to be called from within the async
 * SQL thread.
 */
@RequiredArgsConstructor
public final class Dictionary {
    private final WatchmanPlugin plugin;
    private final Map<String, Map<String, SQLDictionary>> nameMap = new HashMap<>();
    private final Map<Integer, SQLDictionary> indexMap = new TreeMap<>();

    private void cache(@NonNull SQLDictionary row) {
        nameMap.computeIfAbsent(row.getNamespace(), n -> new HashMap<>())
            .put(row.getKey(), row);
        indexMap.put(row.getId(), row);
    }

    /**
     * Fetch a dictionary entry or create if it does not exist.
     * This method is prepared to allow concurrent reading and writing
     * from different servers, thus pessimistically, we use "insert
     * ignore" followed by "select" if an entry is created.
     */

    public SQLDictionary getRow(String namespace, String key) {
        Map<String, SQLDictionary> map = nameMap.computeIfAbsent(namespace, n -> new HashMap<>());
        SQLDictionary row = map.get(key);
        if (row != null) return row;
        // Find
        row = database().find(SQLDictionary.class)
            .eq("namespace", namespace)
            .eq("key", key)
            .findUnique();
        if (row != null) {
            cache(row);
            return row;
        }
        // Create
        row = new SQLDictionary(namespace, key);
        database().insertIgnore(row);
        if (row.getId() != null) {
            cache(row);
            return row;
        }
        // Find again (super rare corner case)
        row = database().find(SQLDictionary.class)
            .eq("namespace", namespace)
            .eq("key", key)
            .findUnique();
        return row;
    }

    public SQLDictionary getRow(int index) {
        if (index == 0) return null;
        SQLDictionary row = indexMap.get(index);
        if (row != null) return row;
        // Find
        row = database().find(SQLDictionary.class)
            .idEq(index)
            .findUnique();
        if (row != null) {
            cache(row);
            return row;
        }
        return null;
    }

    public int getIndex(String namespace, String key) {
        SQLDictionary row = getRow(namespace, key);
        return row != null ? row.getId() : 0;
    }

    public int getIndex(Enum it) {
        return getIndex(it.getClass().getName(), it.name());
    }

    public int getIndex(UUID uuid) {
        return getIndex("java.util.UUID", uuid.toString());
    }

    public int getIndex(BlockData blockData) {
        return getIndex("org.bukkit.block.data.BlockData", blockData.getAsString(false));
    }

    public int getServerIndex(String server) {
        return getIndex("server", server);
    }

    public int getWorldIndex(String world) {
        return getIndex("world", world);
    }

    public String getKey(int index) {
        SQLDictionary row = getRow(index);
        return row != null ? row.getKey() : null;
    }

    public Enum getEnum(int index) {
        SQLDictionary row = getRow(index);
        return row != null ? row.getEnum() : null;
    }

    public EntityType getEntityType(int index) {
        Enum it = getEnum(index);
        return it instanceof EntityType entityType ? entityType : null;
    }

    public Material getMaterial(int index) {
        Enum it = getEnum(index);
        return it instanceof Material entityType ? entityType : null;
    }

    public UUID getUuid(int index) {
        SQLDictionary row = getRow(index);
        return row != null ? row.getUuid() : null;
    }

    public BlockData getBlockData(int index) {
        SQLDictionary row = getRow(index);
        return row != null ? Bukkit.createBlockData(row.getKey()) : null;
    }

    public String getServerName(int index) {
        SQLDictionary row = getRow(index);
        return row != null ? row.getKey() : null;
    }

    public String getWorldName(int index) {
        SQLDictionary row = getRow(index);
        return row != null ? row.getKey() : null;
    }
}
