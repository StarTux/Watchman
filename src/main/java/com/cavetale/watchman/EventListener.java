package com.cavetale.watchman;

import com.cavetale.core.event.block.PlayerBreakBlockEvent;
import com.cavetale.core.event.block.PlayerChangeBlockEvent;
import com.cavetale.core.event.hud.PlayerHudEvent;
import com.cavetale.core.event.hud.PlayerHudPriority;
import com.cavetale.watchman.action.Action;
import com.cavetale.watchman.action.ActionType;
import com.cavetale.watchman.lookup.BlockLookup;
import com.cavetale.watchman.sql.SQLLog;
import com.destroystokyo.paper.event.block.BlockDestroyEvent;
import io.papermc.paper.event.block.PlayerShearBlockEvent;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.EntityBlockFormEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.inventory.BlockInventoryHolder;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import static net.kyori.adventure.text.format.NamedTextColor.*;

@RequiredArgsConstructor
public final class EventListener implements Listener {
    private final WatchmanPlugin plugin;

    public void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    private void onBlockBreak(BlockBreakEvent event) {
        long now = System.currentTimeMillis();
        plugin.store(new Action()
                     .time(now).setActionType(ActionType.BREAK)
                     .setMaterial(event.getBlock().getType())
                     .setActorPlayer(event.getPlayer())
                     .setOldState(event.getBlock())
                     .setNewState(Material.AIR));
        Block otherHalf = Blocks.getOtherHalf(event.getBlock(), event.getBlock().getBlockData());
        // BlockDestroyEvent is unreliable, will always call the top block never the bottom one
        if (otherHalf != null) {
            plugin.store(new Action()
                         .time(now).setActionType(ActionType.BREAK)
                         .setMaterial(otherHalf.getType())
                         .setActorPlayer(event.getPlayer())
                         .setOldState(otherHalf)
                         .setNewState(Material.AIR));
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    private void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        long now = System.currentTimeMillis();
        if (event instanceof BlockMultiPlaceEvent) {
            BlockMultiPlaceEvent multiEvent = (BlockMultiPlaceEvent) event;
            for (BlockState replacedState : multiEvent.getReplacedBlockStates()) {
                plugin.store(new Action()
                             .time(now).setActionType(ActionType.PLACE)
                             .setMaterial(replacedState.getType())
                             .setActorPlayer(player)
                             .setOldState(replacedState)
                             .setNewState(replacedState.getBlock()));
            }
            return;
        }
        plugin.store(new Action()
                     .time(now).setActionType(ActionType.PLACE)
                     .setMaterial(event.getBlockPlaced().getType())
                     .setActorPlayer(event.getPlayer())
                     .setOldState(event.getBlockReplacedState())
                     .setNewState(event.getBlockPlaced()));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    private void onPlayerBucketEmpty(PlayerBucketEmptyEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        Action action = new Action()
            .setNow().setActionType(ActionType.PLACE)
            .setActorPlayer(event.getPlayer())
            .setOldState(block);
        Bukkit.getScheduler().runTask(plugin, () -> plugin.store(action.setMaterial(block.getType()).setNewState(block)));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    private void onPlayerBucketFill(PlayerBucketFillEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        Action action = new Action()
            .setNow().setActionType(ActionType.BREAK)
            .setMaterial(block.getType())
            .setActorPlayer(event.getPlayer())
            .setOldState(block);
        Bukkit.getScheduler().runTask(plugin, () -> plugin.store(action.setNewState(block)));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    private void onPlayerBlockShear(PlayerShearBlockEvent event) {
        Action action = new Action()
            .setNow().setActionType(ActionType.PLACE)
            .setActorPlayer(event.getPlayer())
            .setOldState(event.getBlock());
        Bukkit.getScheduler().runTask(plugin, () -> plugin.store(action.setNewState(event.getBlock())));
    }

    // For now, we only log player caused entity deaths.
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    private void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.getKiller() != null) {
            plugin.store(new Action()
                         .setNow().setActionType(ActionType.KILL)
                         .setActorPlayer(entity.getKiller())
                         .location(entity.getLocation())
                         .setEntity(entity));
            return;
        }
        EntityDamageEvent lastDamageCause = entity.getLastDamageCause();
        if (lastDamageCause instanceof EntityDamageByEntityEvent) {
            EntityDamageByEntityEvent edbee = (EntityDamageByEntityEvent) lastDamageCause;
            plugin.store(new Action()
                         .setNow().setActionType(ActionType.KILL)
                         .setActorEntity(edbee.getDamager())
                         .location(entity.getLocation())
                         .setEntity(entity));
            return;
        } else if (lastDamageCause instanceof EntityDamageByBlockEvent) {
            EntityDamageByBlockEvent edbbe = (EntityDamageByBlockEvent) lastDamageCause;
            plugin.store(new Action()
                         .setNow().setActionType(ActionType.KILL)
                         .setActorBlock(edbbe.getDamager())
                         .location(entity.getLocation())
                         .setEntity(entity));
            return;
        } else if (lastDamageCause != null) {
            Action action = new Action()
                .setNow().setActionType(ActionType.KILL)
                .setActorUnknown()
                .location(entity.getLocation())
                .setEntity(entity);
            plugin.store(action);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    private void onPlayerDeath(PlayerDeathEvent event) {
        plugin.store(new Action()
                     .setNow().setActionType(ActionType.DEATH)
                     .setActorPlayer(event.getEntity())
                     .location(event.getEntity().getLocation()));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    private void onPlayerJoin(PlayerJoinEvent event) {
        plugin.store(new Action()
                     .setNow().setActionType(ActionType.JOIN)
                     .setActorPlayer(event.getPlayer())
                     .location(event.getPlayer().getLocation()));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    private void onPlayerQuit(PlayerQuitEvent event) {
        plugin.store(new Action()
                     .setNow().setActionType(ActionType.QUIT)
                     .setActorPlayer(event.getPlayer())
                     .location(event.getPlayer().getLocation()));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    private void onEntityExplode(EntityExplodeEvent event) {
        for (Block block: event.blockList()) {
            plugin.store(new Action()
                         .setNow().setActionType(ActionType.BREAK)
                         .setActorEntity(event.getEntity())
                         .setOldState(block));
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    private void onBlockExplode(BlockExplodeEvent event) {
        for (Block block: event.blockList()) {
            plugin.store(new Action()
                         .setNow().setActionType(ActionType.BREAK)
                         .setMaterial(block.getType())
                         .setActorBlock(event.getBlock())
                         .setOldState(block));
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    private void onPlayerDropItem(PlayerDropItemEvent event) {
        plugin.store(new Action()
                     .setNow().setActionType(ActionType.DROP)
                     .setMaterial(event.getItemDrop().getItemStack().getType())
                     .setActorPlayer(event.getPlayer())
                     .location(event.getItemDrop().getLocation())
                     .setItemStack(event.getItemDrop().getItemStack()));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    private void onEntityPickupItem(EntityPickupItemEvent event) {
        if (event.getItem().getItemStack().getType().isAir()) return;
        plugin.store(new Action()
                     .setNow().setActionType(ActionType.PICKUP)
                     .setMaterial(event.getItem().getItemStack().getType())
                     .setActorEntity(event.getEntity())
                     .location(event.getItem().getLocation())
                     .setItemStack(event.getItem().getItemStack()));
    }

    @EventHandler(ignoreCancelled = false, priority = EventPriority.LOWEST)
    private void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("watchman.privacy.command")) return;
        plugin.store(new Action()
                     .setNow().setActionType(ActionType.CHAT)
                     .setActorPlayer(player)
                     .location(event.getPlayer().getLocation())
                     .setChatMessage(event.getMessage()));
    }

    @EventHandler(ignoreCancelled = false, priority = EventPriority.LOWEST)
    private void onAsyncPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("watchman.privacy.chat")) return;
        Location location = player.getLocation();
        String message = event.getMessage();
        Bukkit.getScheduler().runTask(plugin, () -> {
                plugin.store(new Action()
                             .setNow().setActionType(ActionType.CHAT)
                             .setActorPlayer(player)
                             .location(location)
                             .setChatMessage(message));
            });
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    private void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof BlockInventoryHolder) {
            Block block = ((BlockInventoryHolder) holder).getBlock();
            plugin.store(new Action()
                         .setNow().setActionType(ActionType.OPEN)
                         .setActorPlayer(player)
                         .setOldState(block)
                         .setMaterial(block.getType()));
        } else if (holder instanceof DoubleChest) {
            DoubleChest doubleChest = (DoubleChest) holder;
            long now = System.currentTimeMillis();
            InventoryHolder left = doubleChest.getLeftSide();
            if (left instanceof BlockInventoryHolder) {
                Block block = ((BlockInventoryHolder) left).getBlock();
                plugin.store(new Action()
                             .time(now).setActionType(ActionType.OPEN)
                             .setActorPlayer(player)
                             .setOldState(block)
                             .setMaterial(block.getType()));
            }
            InventoryHolder right = doubleChest.getRightSide();
            if (right instanceof BlockInventoryHolder) {
                Block block = ((BlockInventoryHolder) right).getBlock();
                plugin.store(new Action()
                             .time(now).setActionType(ActionType.OPEN)
                             .setActorPlayer(player)
                             .setOldState(block)
                             .setMaterial(block.getType()));
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    private void onPlayerInteract(PlayerInteractEvent event) {
        if (!plugin.sessions.wmtool(event.getPlayer())) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        final Player player = event.getPlayer();
        if (!player.hasPermission("watchman.tool")) return;
        final Block block = switch (event.getAction()) {
        case LEFT_CLICK_BLOCK -> event.getClickedBlock();
        case RIGHT_CLICK_BLOCK -> event.getClickedBlock().getRelative(event.getBlockFace());
        default -> null;
        };
        if (block == null) return;
        event.setCancelled(true);
        final boolean detective = player.hasPermission("watchman.tool.detective");
        BlockLookup lookup = BlockLookup.of(block);
        lookup.accept(plugin.database.find(SQLLog.class))
            .limit(1000)
            .findListAsync(logs -> {
                    if (!detective) logs.removeIf(l -> ActionType.ofIndex(l.getActionType()).category != ActionType.Category.BLOCK);
                    plugin.sessions.set(player.getUniqueId(), lookup.getParameters(), logs, session -> {
                            plugin.sessions.showPage(player, 0);
                        });
                });
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    private void onEntityChangeBlock(EntityChangeBlockEvent event) {
        BlockData oldBlockData = event.getBlock().getBlockData();
        BlockData newBlockData = event.getBlockData();
        // Here it gets a little complicated because this could be a
        // block break or placement.
        BlockData nonAirBlockData = oldBlockData.getMaterial().isEmpty()
            ? newBlockData
            : oldBlockData;
        long now = System.currentTimeMillis();
        plugin.store(new Action()
                     .time(now).setActionType(ActionType.PLACE)
                     .setMaterial(nonAirBlockData.getMaterial())
                     .setActorEntity(event.getEntity())
                     .setOldState(event.getBlock())
                     .setNewState(newBlockData));
        Block otherHalf = Blocks.getOtherHalf(event.getBlock(), nonAirBlockData);
        if (otherHalf != null) {
            plugin.store(new Action()
                         .time(now).setActionType(ActionType.PLACE)
                         .setMaterial(nonAirBlockData.getMaterial())
                         .setActorEntity(event.getEntity())
                         .block(otherHalf)
                         .setOldState(otherHalf)
                         .setNewState(Blocks.toOtherHalf(newBlockData)));
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    private void onBlockGrow(BlockGrowEvent event) {
        if (!plugin.eventBlockGrow) return;
        long now = System.currentTimeMillis();
        plugin.store(new Action()
                     .time(now).setActionType(ActionType.PLACE)
                     .setMaterial(event.getNewState().getType())
                     .setActorNature()
                     .setOldState(event.getBlock())
                     .setNewState(event.getNewState()));
        Block otherHalf = Blocks.getOtherHalf(event.getBlock(), event.getNewState().getBlockData());
        if (otherHalf != null) {
            plugin.store(new Action()
                         .time(now).setActionType(ActionType.PLACE)
                         .setMaterial(event.getNewState().getType())
                         .setActorNature()
                         .setOldState(otherHalf)
                         .setNewState(Blocks.toOtherHalf(event.getNewState().getBlockData())));

        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    private void onBlockForm(BlockFormEvent event) {
        if (!plugin.eventBlockForm) return;
        plugin.store(new Action()
                     .setNow().setActionType(ActionType.PLACE)
                     .setMaterial(event.getNewState().getType())
                     .setActorNature()
                     .setOldState(event.getBlock())
                     .setNewState(event.getNewState()));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    private void onEntityBlockForm(EntityBlockFormEvent event) {
        if (!plugin.eventEntityBlockForm) return;
        plugin.store(new Action()
                     .setNow().setActionType(ActionType.PLACE)
                     .setMaterial(event.getNewState().getType())
                     .setActorEntity(event.getEntity())
                     .setOldState(event.getBlock())
                     .setNewState(event.getNewState()));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    private void onBlockSpread(BlockSpreadEvent event) {
        if (!plugin.eventBlockSpread) return;
        plugin.store(new Action()
                     .setNow().setActionType(ActionType.PLACE)
                     .setMaterial(event.getNewState().getType())
                     .setActorNature()
                     .setOldState(event.getBlock())
                     .setNewState(event.getNewState()));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    private void onStructureGrow(StructureGrowEvent event) {
        Player player = event.getPlayer();
        for (BlockState state : event.getBlocks()) {
            Action action = new Action()
                .setNow().setActionType(ActionType.PLACE)
                .setMaterial(state.getType())
                .setOldState(state.getBlock())
                .setNewState(state);
            if (player != null) {
                action.setActorPlayer(player);
            } else {
                action.setActorNature();
            }
            plugin.store(action);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    private void onBlockDestroy(BlockDestroyEvent event) {
        long now = System.currentTimeMillis();
        plugin.store(new Action()
                     .time(now).setActionType(ActionType.BREAK)
                     .setMaterial(event.getBlock().getType())
                     .setActorUnknown()
                     .setOldState(event.getBlock())
                     .setNewState(event.getNewState()));
        Block otherHalf = Blocks.getOtherHalf(event.getBlock(), event.getBlock().getBlockData());
        if (otherHalf != null) {
            plugin.store(new Action()
                         .time(now).setActionType(ActionType.BREAK)
                         .setMaterial(event.getBlock().getType())
                         .setActorUnknown()
                         .setOldState(otherHalf)
                         .setNewState(event.getBlock().getBlockData()));
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    private void onPlayerBreakBlock(PlayerBreakBlockEvent event) {
        plugin.store(new Action().setNow()
                     .setActionType(ActionType.BREAK)
                     .setMaterial(event.getBlock().getType())
                     .setActorPlayer(event.getPlayer())
                     .setOldState(event.getBlock())
                     .setNewState(Material.AIR));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    private void onPlayerChangeBlock(PlayerChangeBlockEvent event) {
        plugin.store(new Action().setNow()
                     .setActionType(ActionType.PLACE)
                     .setMaterial(event.getNewBlockData().getMaterial())
                     .setActorPlayer(event.getPlayer())
                     .setOldState(event.getOldBlockState())
                     .setNewState(event.getNewBlockState()));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    private void onBlockFromTo(BlockFromToEvent event) {
        Block block = event.getToBlock();
        Action row = new Action()
            .setNow().setActionType(ActionType.PLACE)
            .setActorNature()
            .setOldState(block);
        Bukkit.getScheduler().runTask(plugin, () -> {
                row.setMaterial(block.getType())
                    .setNewState(block);
                plugin.store(row);
            });
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    private void onEntityPlace(EntityPlaceEvent event) {
        plugin.store(new Action().setNow()
                     .setActionType(ActionType.SPAWN)
                     .setActorPlayer(event.getPlayer())
                     .location(event.getEntity().getLocation())
                     .setEntity(event.getEntity()));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    private void onVehicleDestroy(VehicleDestroyEvent event) {
        plugin.store(new Action().setNow()
                     .setActionType(ActionType.KILL)
                     .setActorEntity(event.getAttacker())
                     .location(event.getVehicle().getLocation())
                     .setEntity(event.getVehicle()));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    private void onHangingPlace(HangingPlaceEvent event) {
        plugin.store(new Action().setNow()
                     .setActionType(ActionType.SPAWN)
                     .setActorPlayer(event.getPlayer())
                     .location(event.getEntity().getLocation())
                     .setEntity(event.getEntity()));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    private void onHangingBreak(HangingBreakEvent event) {
        if (event instanceof HangingBreakByEntityEvent) return;
        plugin.store(new Action().setNow()
                     .setActionType(ActionType.KILL)
                     .setActorUnknown()
                     .location(event.getEntity().getLocation())
                     .setEntity(event.getEntity()));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    private void onHangingBreakByEntity(HangingBreakByEntityEvent event) {
        plugin.store(new Action().setNow()
                     .setActionType(ActionType.KILL)
                     .setActorEntity(event.getRemover())
                     .location(event.getEntity().getLocation())
                     .setEntity(event.getEntity()));
    }

    // Listen for ItemFrame edits
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    private void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof ItemFrame) {
            ItemFrame itemFrame = (ItemFrame) event.getEntity();
            if (itemFrame.isFixed()) return;
            ItemStack item = itemFrame.getItem();
            if (item == null || item.getType().isAir()) return;
            plugin.store(new Action().setNow()
                         .setActionType(ActionType.ACCESS)
                         .setActorEntity(event.getDamager())
                         .location(itemFrame.getLocation())
                         .setItemStack(item));
        }
    }

    // Listen for ItemFrame edits
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    private void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getRightClicked() instanceof ItemFrame) {
            ItemFrame itemFrame = (ItemFrame) event.getRightClicked();
            if (itemFrame.isFixed()) return;
            ItemStack item = itemFrame.getItem();
            if (item != null && item.getType() != Material.AIR) return;
            Player player = event.getPlayer();
            item = player.getInventory().getItemInMainHand();
            if (item == null || item.getType().isAir()) {
                item = player.getInventory().getItemInOffHand();
                if (item == null || item.getType().isAir()) return;
            }
            plugin.store(new Action().setNow()
                         .setActionType(ActionType.ACCESS)
                         .setActorPlayer(player)
                         .location(itemFrame.getLocation())
                         .setItemStack(item));
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    private void onPlayerArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        ItemStack playerItem = event.getPlayerItem();
        ItemStack armorStandItem = event.getArmorStandItem();
        if (playerItem != null && playerItem.getType().isAir()) playerItem = null;
        if (armorStandItem != null && armorStandItem.getType().isAir()) armorStandItem = null;
        if (playerItem == null && armorStandItem == null) return;
        Player player = event.getPlayer();
        ArmorStand armorStand = event.getRightClicked();
        Action row = new Action().setNow()
            .setActorPlayer(player)
            .location(armorStand.getLocation());
        if (playerItem != null && armorStandItem != null) { // sawp
            row.setActionType(ActionType.ACCESS)
                .setItemStack(armorStandItem);
            //.setItemStack(playerItem);
        } else if (playerItem != null) { // insert
            row.setActionType(ActionType.ACCESS)
                .setItemStack(playerItem);
        } else if (armorStandItem != null) { // remove
            row.setActionType(ActionType.ACCESS)
                .setItemStack(armorStandItem);
        }
        plugin.store(row);
    }

    private static final Component WMTOOL_NOTIFICATION = Component.text("/wmtool", YELLOW)
        .append(Component.text(" enabled!", GRAY));

    @EventHandler
    private void onPlayerHud(PlayerHudEvent event) {
        if (plugin.sessions.wmtool(event.getPlayer())) {
            event.bossbar(PlayerHudPriority.HIGHEST,
                          WMTOOL_NOTIFICATION,
                          BossBar.Color.YELLOW,
                          BossBar.Overlay.PROGRESS,
                          1.0f);
        }
    }
}
