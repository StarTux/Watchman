package com.cavetale.watchman;

import com.cavetale.watchman.action.Action;
import com.cavetale.watchman.action.ActionType;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.event.extent.EditSessionEvent;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.extent.AbstractDelegateExtent;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.util.eventbus.Subscribe;
import com.sk89q.worldedit.world.block.BlockStateHolder;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

@RequiredArgsConstructor
final class WorldEditListener {
    private final WatchmanPlugin plugin;

    protected void enable() {
        WorldEdit.getInstance().getEventBus().register(this);
        plugin.getLogger().info("WorldEdit Listener enabled");
    }

    protected void disable() {
        WorldEdit.getInstance().getEventBus().unregister(this);
    }

    @Subscribe
    public void onEditSession(final EditSessionEvent event) {
        if (!plugin.worldEdit) return;
        final Actor actor = event.getActor();
        if (actor == null || !actor.isPlayer()) return;
        final Player player = Bukkit.getPlayerExact(actor.getName());
        final World world = Bukkit.getWorld(event.getWorld().getName());
        event.setExtent(new MyExtent(event.getExtent(), world, player));
    }

    protected class MyExtent extends AbstractDelegateExtent {
        private final Extent extent;
        private final World world;
        private final Player player;

        MyExtent(final Extent extent, final World world, final Player player) {
            super(extent);
            this.extent = extent;
            this.world = world;
            this.player = player;
        }

        @SuppressWarnings("unchecked") @Override
        public boolean setBlock(final BlockVector3 loc, final BlockStateHolder bl) throws WorldEditException {
            if (plugin.worldEdit) {
                Block block = world.getBlockAt(loc.getX(), loc.getY(), loc.getZ());
                BlockData newBlockData = BukkitAdapter.adapt(bl);
                plugin.store(new Action()
                             .setNow().setActionType(ActionType.PLACE)
                             .setActorPlayer(player)
                             .setOldState(block)
                             .setMaterial(newBlockData.getMaterial())
                             .setNewState(newBlockData));
            }
            return extent.setBlock(loc, bl);
        }
    }
}
