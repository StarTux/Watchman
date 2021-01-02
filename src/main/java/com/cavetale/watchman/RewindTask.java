package com.cavetale.watchman;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.scheduler.BukkitRunnable;

@RequiredArgsConstructor
public final class RewindTask extends BukkitRunnable {
    private final WatchmanPlugin plugin;
    private final Player player;
    private final List<SQLAction> actions;
    private final long delay;
    private final int blocksPerTick;
    private final Cuboid cuboid;
    private final Set<Flag> flags;
    private final Location moveFrom;
    private final Location moveTo;
    private int actionIndex = 0;
    private World world;

    public enum Flag {
        NO_SNOW,
        NO_HEADS,
        NO_TNT,
        REVERSE,
        MOVE;
    }

    public void start() {
        world = player.getWorld();
        player.sendMessage(ChatColor.YELLOW + "Rewinding " + actions.size() + " actions, bpt: " + blocksPerTick
                           + ", flags: " + flags);
        if (!flags.contains(Flag.REVERSE)) {
            hideEntities();
            hideBlocks();
        } else {
            actionIndex = actions.size() - 1;
        }
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_XYLOPHONE, SoundCategory.MASTER, 1.0f, 2.0f);
        runTaskTimer(plugin, 100L, 1L);
        if (flags.contains(Flag.MOVE) && moveFrom != null) {
            player.teleport(moveFrom, TeleportCause.PLUGIN);
        }
    }

    public void hideEntities() {
        for (Entity e : world.getEntities()) {
            if (e instanceof Player) continue;
            if (!cuboid.contains(e.getLocation().getBlock())) continue;
            plugin.getEntityHider().hideEntity(player, e);
        }
    }

    public void showEntities() {
        for (Entity e : world.getEntities()) {
            if (e instanceof Player) continue;
            if (!cuboid.contains(e.getLocation().getBlock())) continue;
            plugin.getEntityHider().showEntity(player, e);
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
            if (flags.contains(Flag.NO_SNOW) && blockData.getMaterial() == Material.SNOW) {
                blockData = null;
            }
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
        boolean res = flags.contains(Flag.REVERSE)
            ? runReverse()
            : runForward();
        if (!res) {
            if (!flags.contains(Flag.REVERSE)) {
                showEntities();
            }
            player.sendMessage(ChatColor.YELLOW + "Rewind done!");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_XYLOPHONE, SoundCategory.MASTER, 1.0f, 1.0f);
            cancel();
        }
    }

    private Location getMoveLocation() {
        double progress = (double) actionIndex / (double) actions.size();
        progress = Math.max(0, Math.min(1, progress));
        if (flags.contains(Flag.REVERSE)) progress = 1.0 - progress;
        double ssergorp = 1.0 - progress;
        double x = moveFrom.getX() * ssergorp + moveTo.getX() * progress;
        double y = moveFrom.getY() * ssergorp + moveTo.getY() * progress;
        double z = moveFrom.getZ() * ssergorp + moveTo.getZ() * progress;
        float pitch = moveFrom.getPitch() * (float) ssergorp + moveTo.getPitch() * (float) progress;
        float yaw1 = moveFrom.getYaw();
        float yaw2 = moveTo.getYaw();
        while (yaw1 < 0) yaw1 += 360.0f;
        while (yaw2 < 0) yaw2 += 360.0f;
        if (Math.abs(yaw2 - yaw1) > Math.abs((yaw2 - 360.0f) - yaw1)) {
            yaw2 -= 360.0f;
        } else if (Math.abs(yaw2 - yaw1) > Math.abs((yaw2 + 360.0f) - yaw1)) {
            yaw2 += 360.0f;
        }
        float yaw = yaw1 * (float) ssergorp + yaw2 * (float) progress;
        Location location = new Location(player.getWorld(), x, y, z, yaw, pitch);
        return location;
    }

    private boolean runForward() {
        if (flags.contains(Flag.MOVE)) {
            player.teleport(getMoveLocation(), TeleportCause.PLUGIN);
        }
        for (int i = 0; i < blocksPerTick; i += 1) {
            if (actionIndex >= actions.size()) {
                return false;
            }
            SQLAction row = actions.get(actionIndex++);
            Block block = world.getBlockAt(row.getX(), row.getY(), row.getZ());
            BlockData blockData = row.getNewBlockData();
            if (block.getType() == blockData.getMaterial()) {
                blockData = block.getBlockData();
            }
            if (flags.contains(Flag.NO_HEADS)) {
                Material material = blockData.getMaterial();
                if (material == Material.PLAYER_HEAD || material == Material.PLAYER_WALL_HEAD) {
                    continue;
                }
            }
            if (flags.contains(Flag.NO_TNT) && blockData.getMaterial() == Material.TNT) {
                continue;
            }
            player.sendBlockChange(block.getLocation(), blockData);
        }
        return true;
    }

    private boolean runReverse() {
        if (flags.contains(Flag.MOVE)) {
            player.teleport(getMoveLocation(), TeleportCause.PLUGIN);
        }
        if (actionIndex == actions.size() - 1) {
            hideEntities();
        }
        for (int i = 0; i < blocksPerTick; i += 1) {
            if (actionIndex < 0) {
                return false;
            }
            SQLAction row = actions.get(actionIndex--);
            Block block = world.getBlockAt(row.getX(), row.getY(), row.getZ());
            BlockData blockData = row.getOldBlockData();
            if (blockData == null) blockData = Material.AIR.createBlockData();
            if (flags.contains(Flag.NO_SNOW) && blockData.getMaterial() == Material.SNOW) {
                blockData = Material.AIR.createBlockData();
            }
            player.sendBlockChange(block.getLocation(), blockData);
        }
        return true;
    }
}
