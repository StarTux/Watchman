package com.cavetale.watchman;

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

    void enable() {
        WorldEdit.getInstance().getEventBus().register(this);
    }

    void disable() {
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

    class MyExtent extends AbstractDelegateExtent {
        private final Extent extent;
        private final World world;
        private final Player player;

        MyExtent(final Extent extent, final World world, final Player player) {
            super(extent);
            this.extent = extent;
            this.world = world;
            this.player = player;
        }

        @Override
        public boolean setBlock(final BlockVector3 loc, final BlockStateHolder bl) throws WorldEditException {
            if (!plugin.worldEdit) return extent.setBlock(loc, bl);
            Block block = world.getBlockAt(loc.getX(), loc.getY(), loc.getZ());
            BlockData newBlockData = BukkitAdapter.adapt(bl);
            plugin.store(new SQLAction()
                         .setNow().setActionType(SQLAction.Type.BLOCK_WORLDEDIT)
                         .setActorPlayer(player)
                         .setOldState(block)
                         .setMaterial(newBlockData.getMaterial())
                         .setNewState(newBlockData));
            return extent.setBlock(loc, bl);
        }
    }
}
