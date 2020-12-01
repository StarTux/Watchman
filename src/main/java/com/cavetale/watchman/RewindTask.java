package com.cavetale.watchman;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

@RequiredArgsConstructor
public final class RewindTask extends BukkitRunnable {
    private final WatchmanPlugin plugin;
    private final Player player;
    private final List<SQLAction> actions;
    private final long delay;
    private final int blocksPerTick;
    private final Cuboid cuboid;
    private int actionIndex = 0;
    private World world;

    public void start() {
        world = player.getWorld();
        player.sendMessage(ChatColor.YELLOW + "Rewinding " + actions.size() + " actions, bpt: " + blocksPerTick);
        hideEntities();
        hideBlocks();
        runTaskTimer(plugin, 100L, 1L);
    }

    public void hideEntities() {
        for (Entity e : world.getEntities()) {
            if (e instanceof Player) continue;
            if (!cuboid.contains(e.getLocation().getBlock())) continue;
            plugin.entityHider.hideEntity(player, e);
        }
    }

    public void showEntities() {
        for (Entity e : world.getEntities()) {
            if (e instanceof Player) continue;
            if (!cuboid.contains(e.getLocation().getBlock())) continue;
            plugin.entityHider.showEntity(player, e);
        }
    }

    public void hideBlocks() {
        Set<Vec3i> done = new HashSet<>();
        for (SQLAction row : actions) {
            Vec3i vector = row.getVector();
            if (done.contains(vector)) continue;
            done.add(vector);
            Block block = vector.toBlock(world);
            BlockData blockData = row.getOldBlockData();
            if (blockData == null) blockData = Material.AIR.createBlockData();
            player.sendBlockChange(block.getLocation(), blockData);
        }
    }

    @Override
    public void run() {
        if (!player.isValid() || !world.equals(player.getWorld())) {
            cancel();
            return;
        }
        for (int i = 0; i < blocksPerTick; i += 1) {
            if (actionIndex >= actions.size()) {
                showEntities();
                player.sendMessage(ChatColor.YELLOW + "Rewind done!");
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_XYLOPHONE, SoundCategory.MASTER, 1.0f, 1.0f);
                cancel();
                return;
            }
            SQLAction row = actions.get(actionIndex++);
            Block block = world.getBlockAt(row.getX(), row.getY(), row.getZ());
            BlockData blockData = row.getNewBlockData();
            if (block.getType() == blockData.getMaterial()) {
                blockData = block.getBlockData();
            }
            player.sendBlockChange(block.getLocation(), blockData);
        }
    }
}
