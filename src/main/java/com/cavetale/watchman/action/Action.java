package com.cavetale.watchman.action;

import com.cavetale.core.connect.Connect;
import com.cavetale.core.font.Unicode;
import com.cavetale.core.item.ItemKinds;
import com.cavetale.watchman.Vec3i;
import com.cavetale.watchman.WatchmanPlugin;
import com.cavetale.watchman.sql.SQLExtra;
import com.cavetale.watchman.sql.SQLLog;
import com.winthier.playercache.PlayerCache;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import lombok.Data;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.structure.Structure;
import static com.cavetale.core.font.Unicode.tiny;
import static com.cavetale.core.util.CamelCase.toCamelCase;
import static com.cavetale.watchman.WatchmanPlugin.database;
import static com.cavetale.watchman.WatchmanPlugin.dictionary;
import static com.cavetale.watchman.WatchmanPlugin.logExpiry;
import static com.cavetale.watchman.io.BukkitIO.deserializeStructure;
import static com.cavetale.watchman.io.BukkitIO.serializeBlockState;
import static java.nio.charset.StandardCharsets.UTF_8;
import static net.kyori.adventure.text.Component.empty;
import static net.kyori.adventure.text.Component.join;
import static net.kyori.adventure.text.Component.newline;
import static net.kyori.adventure.text.Component.space;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.JoinConfiguration.noSeparators;
import static net.kyori.adventure.text.JoinConfiguration.separator;
import static net.kyori.adventure.text.event.ClickEvent.suggestCommand;
import static net.kyori.adventure.text.event.HoverEvent.showText;
import static net.kyori.adventure.text.format.NamedTextColor.*;
import static net.kyori.adventure.text.format.TextDecoration.*;

@Data
public final class Action {
    private ActionType actionType;

    private Material changedMaterial;
    private EntityType changedEntityType;

    private ActorType actorType;
    private EntityType actorEntityType;
    private Material actorMaterial;
    private UUID actorUuid;

    private Instant time;
    private String server;
    private String world;
    private int x;
    private int y;
    private int z;

    private BlockData oldBlockData;
    private BlockData newBlockData;

    private String eventName;

    private Map<ExtraType, byte[]> extras;

    private SQLLog log;

    public Action() { }

    public Action actionType(ActionType value) {
        this.actionType = value;
        return this;
    }

    public Action block(Block block) {
        this.server = Connect.get().getServerName();
        this.world = block.getWorld().getName();
        this.x = block.getX();
        this.y = block.getY();
        this.z = block.getZ();
        return this;
    }

    public Action location(Location location) {
        this.server = Connect.get().getServerName();
        this.world = location.getWorld().getName();
        this.x = location.getBlockX();
        this.y = location.getBlockY();
        this.z = location.getBlockZ();
        return this;
    }

    public Action setNow() {
        this.time = Instant.now();
        return this;
    }

    public Action time(long value) {
        this.time = Instant.ofEpochMilli(value);
        return this;
    }

    public Action setActionType(ActionType value) {
        this.actionType = value;
        return this;
    }

    public Block getBlock() {
        World w = Bukkit.getWorld(world);
        return w != null
            ? w.getBlockAt(x, y, z)
            : null;
    }

    public Location getLocation() {
        Block block = getBlock();
        if (block == null) return null;
        return block.getLocation().add(0.5, 0.0, 0.5);
    }

    public Vec3i getVector() {
        return new Vec3i(x, y, z);
    }

    public Action setMaterial(Material value) {
        this.changedMaterial = value;
        return this;
    }

    public Action setEntityType(EntityType value) {
        this.changedEntityType = value;
        return this;
    }

    public Action setActorUnknown() {
        this.actorType = ActorType.UNKNOWN;
        return this;
    }

    public Action setActorNature() {
        this.actorType = ActorType.NATURE;
        return this;
    }

    public Action setActorFake() {
        this.actorType = ActorType.FAKE;
        return this;
    }

    public Action setActorPlayer(Player player) {
        this.actorType = ActorType.PLAYER;
        this.actorEntityType = EntityType.PLAYER;
        this.actorUuid = player.getUniqueId();
        return this;
    }

    public Action setActorEntity(Entity entity) {
        if (entity instanceof Player player) return setActorPlayer(player);
        this.actorType = ActorType.ENTITY;
        this.actorEntityType = entity.getType();
        return this;
    }

    public Action setActorBlock(Block block) {
        this.actorType = ActorType.BLOCK;
        this.actorMaterial = block.getType();
        return this;
    }

    public Action setOldState(Block block) {
        setOldState(block.getState());
        return this;
    }

    public Action setOldState(BlockData blockData) {
        this.oldBlockData = blockData;
        return this;
    }

    public Action setOldState(BlockState blockState) {
        block(blockState.getBlock());
        oldBlockData = blockState.getBlockData();
        if (blockState instanceof TileState) {
            putExtra(ExtraType.OLD_BLOCK_STRUCTURE, serializeBlockState(blockState));
        }
        return this;
    }

    public Action setEntity(Entity entity) {
        setEntityType(entity.getType());
        @SuppressWarnings("deprecation") final byte[] bytes = Bukkit.getUnsafe().serializeEntity(entity);
        putExtra(ExtraType.ENTITY, bytes);
        return this;
    }

    public Action setChatMessage(String value) {
        putExtra(ExtraType.CHAT_MESSAGE, value.getBytes(UTF_8));
        return this;
    }

    public Action setItemStack(ItemStack value) {
        setMaterial(value.getType());
        @SuppressWarnings("deprecation") final byte[] bytes = Bukkit.getUnsafe().serializeItem(value);
        putExtra(ExtraType.ITEM, bytes);
        return this;
    }

    public Action setNewState(Block block) {
        setNewState(block.getState());
        return this;
    }

    public Action setNewState(BlockData blockData) {
        newBlockData = blockData;
        return this;
    }

    public Action setNewState(BlockState blockState) {
        block(blockState.getBlock());
        newBlockData = blockState.getBlockData();
        if (blockState instanceof TileState) {
            putExtra(ExtraType.NEW_BLOCK_STRUCTURE, serializeBlockState(blockState));
        }
        return this;
    }

    public Action setNewState(Material mat) {
        newBlockData = mat.createBlockData();
        return this;
    }

    public Action setEvent(Event event) {
        eventName = event.getClass().getName();
        return this;
    }

    public String getChatMessage() {
        return extras != null && extras.containsKey(ExtraType.CHAT_MESSAGE)
            ? new String(extras.get(ExtraType.CHAT_MESSAGE))
            : "";
    }

    /**
     * Store all information in the log.
     * Called in an SQL thread.
     */
    public SQLLog store() {
        if (server == null || world == null) throw new IllegalStateException(toString());
        log = new SQLLog();
        // Base
        log.setTime(time.toEpochMilli());
        log.setExpiry(time.plus(logExpiry()).toEpochMilli());
        log.setActionType(actionType.index);
        if (changedMaterial != null) {
            log.setChangedEnum(dictionary().getIndex(changedMaterial));
        } else if (changedEntityType != null) {
            log.setChangedEnum(dictionary().getIndex(changedEntityType));
        }
        // Actor
        log.setActorType(actorType.index);
        if (actorEntityType != null) {
            log.setActorEnum(dictionary().getIndex(actorEntityType));
        } else if (actorMaterial != null) {
            log.setActorEnum(dictionary().getIndex(actorMaterial));
        }
        if (actorUuid != null) {
            log.setActorUuid(dictionary().getIndex(actorUuid));
        }
        // Location
        log.setServer(dictionary().getServerIndex(server));
        log.setWorld(dictionary().getWorldIndex(world));
        log.setX(x);
        log.setY(y);
        log.setZ(z);
        // BlockData
        if (oldBlockData != null) {
            log.setOldBlockData(dictionary().getIndex(oldBlockData));
        }
        if (newBlockData != null) {
            log.setNewBlockData(dictionary().getIndex(newBlockData));
        }
        if (eventName != null) {
            log.setEventName(dictionary().getClassNameIndex(eventName));
        }
        if (extras != null && !extras.isEmpty()) {
            log.setExtra(extras.size());
        }
        return log;
    }

    /**
     * Requires a saved log.
     */
    public void makeExtras(List<SQLExtra> extraList) {
        if (extras == null || extras.isEmpty()) return;
        for (Map.Entry<ExtraType, byte[]> it : extras.entrySet()) {
            SQLExtra extra = new SQLExtra(log, it.getKey(), it.getValue());
            extraList.add(extra);
        }
    }

    /**
     * Fetch data from the log.
     * Called in an SQL thread.
     */
    public Action fetch(SQLLog theLog) {
        log = theLog;
        // Base
        this.time = Instant.ofEpochMilli(log.getTime());
        this.actionType = ActionType.ofIndex(log.getActionType());
        Enum changedEnum = dictionary().getEnum(log.getChangedEnum());
        if (changedEnum instanceof Material material) {
            changedMaterial = material;
        } else if (changedEnum instanceof EntityType entityType) {
            changedEntityType = entityType;
        }
        // Actor
        this.actorType = ActorType.ofIndex(log.getActorType());
        Enum actorEnum = dictionary().getEntityType(log.getActorEnum());
        if (actorEnum instanceof EntityType entityType) {
            this.actorEntityType = entityType;
        } else if (actorEnum instanceof Material material) {
            this.actorMaterial = material;
        }
        this.actorUuid = dictionary().getUuid(log.getActorUuid());
        // Location
        this.server = dictionary().getKey(log.getServer());
        this.world = dictionary().getKey(log.getWorld());
        this.x = log.getX();
        this.y = log.getY();
        this.z = log.getZ();
        this.oldBlockData = dictionary().getBlockData(log.getOldBlockData());
        this.newBlockData = dictionary().getBlockData(log.getNewBlockData());
        this.eventName = dictionary().getClassName(log.getEventName());
        if (log.getExtra() != 0) {
            for (SQLExtra extra : database().find(SQLExtra.class)
                     .eq("logId", log.getId())
                     .findList()) {
                putExtra(ExtraType.ofIndex(extra.getType()), extra.getDataBytes());
            }
        }
        return this;
    }

    private void putExtra(ExtraType key, byte[] value) {
        if (extras == null) extras = new EnumMap<>(ExtraType.class);
        extras.put(key, value);
    }

    private Component getIdTag() {
        String str = "" + log.getId();
        if (str.length() > 2) str = "." + str.substring(str.length() - 2);
        return text(str, GRAY)
            .hoverEvent(showText(text("" + log.getId(), GRAY)))
            .clickEvent(suggestCommand("/wm info " + log.getId()))
            .insertion("" + log.getId());
    }

    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("yyyy MMM dd HH:mm:ss");

    private Component getTimeTagHelper(boolean detailed) {
        Duration duration = Duration.between(time, Instant.now());
        final long seconds = duration.toSeconds();
        final long minutes = duration.toMinutes();
        final long hours = duration.toHours();
        final long days = duration.toDays();
        ArrayList<Component> list = new ArrayList<>(10);
        if (days > 0) {
            list.add(text(days));
            list.add(text(Unicode.SMALLD.character, GRAY));
        }
        if (hours > 0) {
            list.add(text(hours % 24));
            list.add(text(Unicode.SMALLH.character, GRAY));
        }
        if (minutes > 0) {
            list.add(text(minutes % 60));
            list.add(text(Unicode.SMALLM.character, GRAY));
        }
        list.add(text(Math.max(0, seconds % 60)));
        if (detailed) {
            list.add(text(".", GRAY));
            list.add(text(duration.toMillis() % 1000L));
        }
        list.add(text(Unicode.SMALLS.character, GRAY));
        return join(noSeparators(), list).color(WHITE);
    }

    public Component getTimeTag() {
        return getTimeTagHelper(false)
            .hoverEvent(showText(join(separator(newline()),
                                      getTimeTagHelper(true),
                                      text(TIME_FORMAT.format(Date.from(time)), WHITE))));
    }

    public Component getActorTag() {
        return switch (actorType) {
        case PLAYER -> {
            PlayerCache player = PlayerCache.forUuid(actorUuid);
            yield text(player.name, GREEN)
                .insertion(player.name)
                .hoverEvent(showText(join(separator(newline()),
                                          text(player.name, WHITE),
                                          text("Player", DARK_GRAY, ITALIC),
                                          text(player.uuid.toString(), WHITE))))
                .clickEvent(suggestCommand("/status " + player.name));
        }
        case ENTITY -> text(toCamelCase(" ", actorEntityType), RED);
        case BLOCK -> actorMaterial != null ? text(toCamelCase(" ", actorMaterial), GOLD) : text("N/A", DARK_RED);
        default -> text(toCamelCase(" ", actorType), LIGHT_PURPLE, ITALIC);
        };
    }

    public Component getChangedTag() {
        Component tag;
        if (changedMaterial != null) {
            tag = changedMaterial.isItem()
                ? ItemKinds.icon(new ItemStack(changedMaterial))
                : text(toCamelCase(" ", changedMaterial), GOLD);
            if (tag.equals(empty())) tag = text(toCamelCase(" ", changedMaterial), GOLD);
        } else if (changedEntityType != null) {
            tag = text(toCamelCase(" ", changedEntityType), RED);
        } else {
            tag = text("N/A", DARK_GRAY);
        }
        if (extras != null && extras.containsKey(ExtraType.ITEM)) {
            @SuppressWarnings("deprecation") ItemStack item = Bukkit
                .getUnsafe().deserializeItem(extras.get(ExtraType.ITEM));
            item.editMeta(meta -> {
                    meta.lore(List.of());
                    meta.displayName(null);
                });
            ItemStack bundle = new ItemStack(Material.BUNDLE);
            bundle.editMeta(m -> {
                    m.addItemFlags(ItemFlag.values());
                    if (!(m instanceof BundleMeta meta)) return;
                    meta.setItems(List.of(item));
                });
            tag = tag.hoverEvent(bundle.asHoverEvent());
            tag = tag.clickEvent(suggestCommand("/wm open " + log.getId()));
        } else if (extras != null && extras.containsKey(ExtraType.OLD_BLOCK_STRUCTURE)) {
            Structure structure = deserializeStructure(extras.get(ExtraType.OLD_BLOCK_STRUCTURE));
            BlockState state = structure.getPalettes().get(0).getBlocks().get(0);
            if (state instanceof Container container) {
                List<ItemStack> items = new ArrayList<>();
                for (ItemStack item : container.getInventory()) {
                    if (item == null || item.getType().isAir()) continue;
                    item.editMeta(meta -> {
                            meta.lore(List.of());
                            meta.displayName(null);
                        });
                    items.add(item);
                }
                ItemStack bundle = new ItemStack(Material.BUNDLE);
                bundle.editMeta(m -> {
                        m.addItemFlags(ItemFlag.values());
                        if (!(m instanceof BundleMeta meta)) return;
                        meta.setItems(items);
                    });
                tag = tag.hoverEvent(bundle.asHoverEvent());
                tag = tag.clickEvent(suggestCommand("/wm open " + log.getId()));
            }
        }
        return tag;
    }

    public boolean open(Player player) {
        if (extras != null && extras.containsKey(ExtraType.ITEM)) {
            @SuppressWarnings("deprecation") ItemStack item = Bukkit
                .getUnsafe().deserializeItem(extras.get(ExtraType.ITEM));
            Inventory inventory = Bukkit.createInventory(null, 9, text("Item #" + log.getId() + " (copy)", DARK_GRAY));
            inventory.setItem(4, item);
            player.openInventory(inventory);
            return true;
        } else if (extras != null && extras.containsKey(ExtraType.OLD_BLOCK_STRUCTURE)) {
            Structure structure = deserializeStructure(extras.get(ExtraType.OLD_BLOCK_STRUCTURE));
            BlockState state = structure.getPalettes().get(0).getBlocks().get(0);
            if (state instanceof Container container) {
                player.openInventory(container.getInventory());
                return true;
            }
        }
        return false;
    }

    public Component getLocationTag() {
        String coords = x + " " + y + " " + z;
        return text(coords)
            .hoverEvent(showText(join(separator(newline()),
                                      join(noSeparators(), text(tiny("server "), GRAY), text(server, WHITE)),
                                      join(noSeparators(), text(tiny("world "), GRAY), text(world, WHITE)),
                                      join(noSeparators(), text(tiny("at "), GRAY), text(coords, WHITE)),
                                      join(noSeparators(), text(tiny("event "), GRAY), text("" + eventName, WHITE)))))
            .clickEvent(suggestCommand("/wm tp " + log.getId()))
            .insertion(coords);
    }

    public Component getMessage() {
        switch (actionType) {
        case CHAT:
            String msg = getChatMessage();
            return join(noSeparators(),
                        getIdTag(),
                        space(),
                        getTimeTag(),
                        getLocationTag(),
                        space(),
                        getActorTag(),
                        space(),
                        text(getChatMessage())
                        .hoverEvent(showText(text(msg, WHITE)))
                        .insertion(msg));
        default:
            return join(noSeparators(),
                        getIdTag(),
                        space(),
                        getTimeTag(),
                        space(),
                        getActorTag(),
                        space(),
                        text(actionType.human),
                        space(),
                        getChangedTag(),
                        text(" at ", DARK_GRAY),
                        getLocationTag());
        }
    }

    @SuppressWarnings("deprecation")
    public boolean rollback() {
        switch (actionType) {
        case BREAK:
        case PLACE: {
            Block block = getBlock();
            if (block == null) return false;
            if (extras != null && extras.containsKey(ExtraType.OLD_BLOCK_STRUCTURE)) {
                Structure structure = deserializeStructure(extras.get(ExtraType.OLD_BLOCK_STRUCTURE));
                structure.place(block.getLocation(), false, StructureRotation.NONE, Mirror.NONE, 0, 1.0f, ThreadLocalRandom.current());
                return true;
            } else if (oldBlockData != null) {
                block.setBlockData(oldBlockData, false);
                return true;
            } else {
                return false;
            }
        }
        case KILL: {
            Block block = getBlock();
            if (block == null) return false;
            if (extras == null || !extras.containsKey(ExtraType.ENTITY)) return false;
            final Entity entity;
            try {
                entity = Bukkit.getUnsafe().deserializeEntity(extras.get(ExtraType.ENTITY), block.getWorld(), false);
            } catch (IllegalArgumentException iae) {
                WatchmanPlugin.getInstance().getLogger().log(Level.SEVERE, "rollback actionType=KILL " + log, iae);
                return false;
            }
            return entity.spawnAt(block.getLocation().add(0.5, 0.0, 0.5));
        }
        default: return false;
        }
    }

    public boolean redo() {
        return false;
    }

    public boolean isOnThisServer() {
        return server.equals(Connect.get().getServerName());
    }
}
