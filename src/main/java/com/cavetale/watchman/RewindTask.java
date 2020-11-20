package com.cavetale.watchman;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
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
        world = player.getWorld();
        player.sendMessage(ChatColor.YELLOW + "Rewinding " + actions.size() + " actions, bpt: " + blocksPerTick);
        hideBlocks();
        runTaskTimer(plugin, 100L, 1L);
    }

    public void hideBlocks() {
        Set<Block> blocks = new HashSet<>();
        for (SQLAction row : actions) {
            blocks.add(world.getBlockAt(row.getX(), row.getY(), row.getZ()));
        }
        for (Block block : blocks) {
            player.sendBlockChange(block.getLocation(), Material.AIR.createBlockData());
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
            player.sendBlockChange(block.getLocation(), row.getNewBlockData());
        }
    }
}
