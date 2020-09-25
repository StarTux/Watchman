package com.cavetale.watchman;

import java.util.List;
import lombok.RequiredArgsConstructor;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

@RequiredArgsConstructor
public final class RewindTask extends BukkitRunnable {
    private final WatchmanPlugin plugin;
    private final Player player;
    private final List<SQLAction> actions;
    private final long delay;
    private final int blocksPerTick;
    private int actionIndex = 0;
    private World world;

    public void start() {
        player.sendMessage(ChatColor.YELLOW + "Rewinding " + actions.size() + " actions, bpt: " + blocksPerTick);
        hideBlocks();
        runTaskTimer(plugin, 100L, 1L);
        world = player.getWorld();
    }

    public void hideBlocks() {
        for (int i = actions.size() - 1; i >= 0; i -= 1) {
            SQLAction row = actions.get(i);
            Block block = world.getBlockAt(row.getX(), row.getY(), row.getZ());
            BlockData oldData = block.getBlockData();
            player.sendBlockChange(block.getLocation(), Material.AIR.createBlockData());
            if (oldData instanceof Bisected) {
                Bisected bis = (Bisected) oldData;
                switch (bis.getHalf()) {
                case TOP:
                    player.sendBlockChange(block.getRelative(0, -1, 0).getLocation(), Material.AIR.createBlockData());
                    break;
                case BOTTOM:
                    player.sendBlockChange(block.getRelative(0, 1, 0).getLocation(), Material.AIR.createBlockData());
                    break;
                default: break;
                }
            }
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
                player.sendMessage(ChatColor.YELLOW + "Rewind done!");
                cancel();
                return;
            }
            SQLAction row = actions.get(actionIndex++);
            Block block = world.getBlockAt(row.getX(), row.getY(), row.getZ());
            if (block.isEmpty()) continue;
            BlockData data = row.getNewBlockData();
            player.sendBlockChange(block.getLocation(), data);
        }
    }
}
