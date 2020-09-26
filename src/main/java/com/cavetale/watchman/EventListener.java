package com.cavetale.watchman;

import lombok.RequiredArgsConstructor;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.metadata.FixedMetadataValue;

@RequiredArgsConstructor
public final class EventListener implements Listener {
    private final WatchmanPlugin plugin;

    public void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockBreak(BlockBreakEvent event) {
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
    public void onBlockPlace(BlockPlaceEvent event) {
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

    // For now, we only log player caused entity deaths.
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity().getKiller() == null) return;
        plugin.store(new SQLAction()
                     .setNow().setActionType(SQLAction.Type.ENTITY_KILL)
                     .setActorPlayer(event.getEntity().getKiller())
                     .setOldState(event.getEntity()));
    }


    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        plugin.store(new SQLAction()
                     .setNow().setActionType(SQLAction.Type.PLAYER_JOIN)
                     .setActorPlayer(event.getPlayer())
                     .setOldState(event.getPlayer().getLocation()));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.exit(event.getPlayer());
        plugin.store(new SQLAction()
                     .setNow().setActionType(SQLAction.Type.PLAYER_QUIT)
                     .setActorPlayer(event.getPlayer())
                     .setOldState(event.getPlayer().getLocation()));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onEntityExplode(EntityExplodeEvent event) {
        for (Block block: event.blockList()) {
            plugin.store(new SQLAction()
                         .setNow().setActionType(SQLAction.Type.BLOCK_EXPLODE)
                         .setActorEntity(event.getEntity())
                         .setOldState(block));
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockExplode(BlockExplodeEvent event) {
        for (Block block: event.blockList()) {
            plugin.store(new SQLAction()
                         .setNow().setActionType(SQLAction.Type.BLOCK_EXPLODE)
                         .setActorBlock(event.getBlock())
                         .setOldState(block));
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
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
            .orderByDescending("time")
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
}
