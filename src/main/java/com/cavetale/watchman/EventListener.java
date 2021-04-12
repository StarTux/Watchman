package com.cavetale.watchman;

import com.destroystokyo.paper.event.block.BlockDestroyEvent;
import io.papermc.paper.event.block.PlayerShearBlockEvent;
import lombok.RequiredArgsConstructor;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.EntityBlockFormEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.inventory.BlockInventoryHolder;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.metadata.FixedMetadataValue;

@RequiredArgsConstructor
public final class EventListener implements Listener {
    private final WatchmanPlugin plugin;

    public void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    void onBlockBreak(BlockBreakEvent event) {
        plugin.store(new SQLAction()
                     .setNow().setActionType(SQLAction.Type.BLOCK_BREAK)
                     .setActorPlayer(event.getPlayer())
                     .setOldState(event.getBlock())
                     .setNewState(Material.AIR));
        Block otherHalf = Blocks.getOtherHalf(event.getBlock(), event.getBlock().getBlockData());
        // BlockDestroyEvent is unreliable, will always call the top block never the bottom one
        if (otherHalf != null) {
            plugin.store(new SQLAction()
                         .setNow().setActionType(SQLAction.Type.BLOCK_BREAK)
                         .setActorPlayer(event.getPlayer())
                         .setOldState(otherHalf)
                         .setNewState(Material.AIR));

        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (event instanceof BlockMultiPlaceEvent) {
            BlockMultiPlaceEvent multiEvent = (BlockMultiPlaceEvent) event;
            for (BlockState replacedState : multiEvent.getReplacedBlockStates()) {
                plugin.store(new SQLAction()
                             .setNow().setActionType(SQLAction.Type.BLOCK_PLACE)
                             .setActorPlayer(player)
                             .setOldState(replacedState)
                             .setNewState(replacedState.getBlock()));
            }
            return;
        }
        plugin.store(new SQLAction()
                     .setNow().setActionType(SQLAction.Type.BLOCK_PLACE)
                     .setActorPlayer(event.getPlayer())
                     .setOldState(event.getBlockReplacedState())
                     .setNewState(event.getBlockPlaced()));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    void onPlayerBucketEmpty(PlayerBucketEmptyEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        SQLAction row = new SQLAction()
            .setNow().setActionType(SQLAction.Type.BUCKET_EMPTY)
            .setActorPlayer(event.getPlayer())
            .setOldState(block);
        Bukkit.getScheduler().runTask(plugin, () -> plugin.store(row.setNewState(block)));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    void onPlayerBucketFill(PlayerBucketFillEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        SQLAction row = new SQLAction()
            .setNow().setActionType(SQLAction.Type.BUCKET_FILL)
            .setActorPlayer(event.getPlayer())
            .setOldState(block);
        Bukkit.getScheduler().runTask(plugin, () -> plugin.store(row.setNewState(block)));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    void onPlayerBlockShear(PlayerShearBlockEvent event) {
        SQLAction action = new SQLAction()
            .setNow().setActionType(SQLAction.Type.BLOCK_SHEAR)
            .setActorPlayer(event.getPlayer())
            .setOldState(event.getBlock());
        Bukkit.getScheduler().runTask(plugin, () -> plugin.store(action.setNewState(event.getBlock())));
    }

    // For now, we only log player caused entity deaths.
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity().getKiller() == null) return;
        plugin.store(new SQLAction()
                     .setNow().setActionType(SQLAction.Type.ENTITY_KILL)
                     .setActorPlayer(event.getEntity().getKiller())
                     .setOldState(event.getEntity()));
    }


    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    void onPlayerJoin(PlayerJoinEvent event) {
        plugin.store(new SQLAction()
                     .setNow().setActionType(SQLAction.Type.PLAYER_JOIN)
                     .setActorPlayer(event.getPlayer())
                     .setOldState(event.getPlayer().getLocation()));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    void onPlayerQuit(PlayerQuitEvent event) {
        plugin.exit(event.getPlayer());
        plugin.store(new SQLAction()
                     .setNow().setActionType(SQLAction.Type.PLAYER_QUIT)
                     .setActorPlayer(event.getPlayer())
                     .setOldState(event.getPlayer().getLocation()));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    void onEntityExplode(EntityExplodeEvent event) {
        for (Block block: event.blockList()) {
            plugin.store(new SQLAction()
                         .setNow().setActionType(SQLAction.Type.BLOCK_EXPLODE)
                         .setActorEntity(event.getEntity())
                         .setOldState(block));
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    void onBlockExplode(BlockExplodeEvent event) {
        for (Block block: event.blockList()) {
            plugin.store(new SQLAction()
                         .setNow().setActionType(SQLAction.Type.BLOCK_EXPLODE)
                         .setActorBlock(event.getBlock())
                         .setOldState(block));
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    void onPlayerDropItem(PlayerDropItemEvent event) {
        plugin.store(new SQLAction()
                     .setNow().setActionType(SQLAction.Type.ITEM_DROP)
                     .setActorPlayer(event.getPlayer())
                     .setLocation(event.getItemDrop().getLocation())
                     .setNewState(event.getItemDrop().getItemStack()));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    void onEntityPickupItem(EntityPickupItemEvent event) {
        plugin.store(new SQLAction()
                     .setNow().setActionType(SQLAction.Type.ITEM_PICKUP)
                     .setActorEntity(event.getEntity())
                     .setLocation(event.getItem().getLocation())
                     .setOldState(event.getItem().getItemStack()));
    }

    @EventHandler(ignoreCancelled = false, priority = EventPriority.LOWEST)
    void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("watchman.privacy.command")) return;
        plugin.store(new SQLAction()
                     .setNow().setActionType(SQLAction.Type.COMMAND)
                     .setActorPlayer(player)
                     .setLocation(event.getPlayer().getLocation())
                     .setNewState(event.getMessage()));
    }

    @EventHandler(ignoreCancelled = false, priority = EventPriority.LOWEST)
    void onAsyncPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("watchman.privacy.chat")) return;
        Location location = player.getLocation();
        String message = event.getMessage();
        Bukkit.getScheduler().runTask(plugin, () -> {
                plugin.store(new SQLAction()
                             .setNow().setActionType(SQLAction.Type.CHAT)
                             .setActorPlayer(player)
                             .setLocation(location)
                             .setNewState(message));
            });
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof BlockInventoryHolder)) return;
        Block block = ((BlockInventoryHolder) holder).getBlock();
        plugin.store(new SQLAction()
                     .setNow().setActionType(SQLAction.Type.INVENTORY_OPEN)
                     .setActorPlayer(player)
                     .setOldState(block));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!event.getPlayer().hasMetadata(Meta.TOOL_KEY)) return;
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
        int x = block.getX();
        int y = block.getY();
        int z = block.getZ();
        LookupMeta meta = new LookupMeta();
        meta.world = world;
        meta.location = new LookupMeta.Vec(x, y, z);
        player.removeMetadata(Meta.LOOKUP, plugin);
        player.removeMetadata(Meta.LOOKUP_META, plugin);
        plugin.database.find(SQLAction.class)
            .eq("world", world).eq("x", x).eq("y", y).eq("z", z)
            .in("action", SQLAction.Type.inCategory(SQLAction.Type.Category.BLOCK))
            .orderByDescending("id")
            .findListAsync((actions) -> {
                    if (!player.isValid()) return;
                    if (actions.isEmpty()) {
                        player.sendMessage(ChatColor.RED + String.format("No actions to show at %d,%d,%d.", x, y, z));
                        return;
                    }
                    player.sendMessage(ChatColor.YELLOW + String.format("%d actions at %d,%d,%d.", actions.size(), x, y, z));
                    player.setMetadata(Meta.LOOKUP, new FixedMetadataValue(plugin, actions));
                    player.setMetadata(Meta.LOOKUP_META, new FixedMetadataValue(plugin, meta));
                    plugin.showActionPage(player, actions, meta, 0);
                });
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    void onEntityChangeBlock(EntityChangeBlockEvent event) {
        BlockData oldBlockData = event.getBlock().getBlockData();
        BlockData newBlockData = event.getBlockData();
        plugin.store(new SQLAction()
                     .setNow().setActionType(SQLAction.Type.BLOCK_CHANGE)
                     .setActorEntity(event.getEntity())
                     .setOldState(event.getBlock())
                     .setNewState(newBlockData));
        // Here it gets a little complicated because this could be a
        // block break or placement.
        BlockData nonAirBlockData = oldBlockData.getMaterial().isEmpty()
            ? newBlockData
            : oldBlockData;
        Block otherHalf = Blocks.getOtherHalf(event.getBlock(), nonAirBlockData);
        if (otherHalf != null) {
            plugin.store(new SQLAction()
                         .setNow().setActionType(SQLAction.Type.BLOCK_CHANGE)
                         .setActorEntity(event.getEntity())
                         .setLocation(otherHalf)
                         .setOldState(otherHalf.getBlockData())
                         .setNewState(Blocks.toOtherHalf(newBlockData)));

        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    void onBlockGrow(BlockGrowEvent event) {
        if (!plugin.eventBlockGrow) return;
        plugin.store(new SQLAction()
                     .setNow().setActionType(SQLAction.Type.BLOCK_GROW)
                     .setActorTypeName("nature")
                     .setOldState(event.getBlock())
                     .setNewState(event.getNewState()));
        Block otherHalf = Blocks.getOtherHalf(event.getBlock(), event.getNewState().getBlockData());
        if (otherHalf != null) {
            plugin.store(new SQLAction()
                         .setNow().setActionType(SQLAction.Type.BLOCK_GROW)
                         .setActorTypeName("nature")
                         .setOldState(otherHalf)
                         .setNewState(Blocks.toOtherHalf(event.getNewState().getBlockData())));

        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    void onBlockForm(BlockFormEvent event) {
        if (!plugin.eventBlockForm) return;
        plugin.store(new SQLAction()
                     .setNow().setActionType(SQLAction.Type.BLOCK_FORM)
                     .setActorTypeName("nature")
                     .setOldState(event.getBlock())
                     .setNewState(event.getNewState()));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    void onEntityBlockForm(EntityBlockFormEvent event) {
        if (!plugin.eventEntityBlockForm) return;
        plugin.store(new SQLAction()
                     .setNow().setActionType(SQLAction.Type.BLOCK_FORM)
                     .setActorEntity(event.getEntity())
                     .setOldState(event.getBlock())
                     .setNewState(event.getNewState()));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    void onBlockSpread(BlockSpreadEvent event) {
        if (!plugin.eventBlockSpread) return;
        plugin.store(new SQLAction()
                     .setNow().setActionType(SQLAction.Type.BLOCK_FORM)
                     .setActorTypeName("nature")
                     .setOldState(event.getBlock())
                     .setNewState(event.getNewState()));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    void onStructureGrow(StructureGrowEvent event) {
        Player player = event.getPlayer();
        for (BlockState state : event.getBlocks()) {
            SQLAction action = new SQLAction()
                .setNow().setActionType(SQLAction.Type.BLOCK_GROW)
                .setOldState(state.getBlock())
                .setNewState(state);
            if (player != null) {
                action.setActorPlayer(player);
            } else {
                action.setActorTypeName("nature");
            }
            plugin.store(action);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    void onBlockDestroy(BlockDestroyEvent event) {
        plugin.store(new SQLAction()
                     .setNow().setActionType(SQLAction.Type.BLOCK_DESTROY)
                     .setActorTypeName("unknown")
                     .setOldState(event.getBlock())
                     .setNewState(event.getNewState()));
        Block otherHalf = Blocks.getOtherHalf(event.getBlock(), event.getBlock().getBlockData());
        if (otherHalf != null) {
            plugin.store(new SQLAction()
                         .setNow().setActionType(SQLAction.Type.BLOCK_DESTROY)
                         .setActorTypeName("unknown")
                         .setOldState(otherHalf)
                         .setNewState(event.getBlock().getBlockData()));
        }
    }
}
