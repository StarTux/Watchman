package com.cavetale.watchman;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.data.type.Chest;

/**
 * Block utilities.
 */
public final class Blocks {
    private Blocks() { }

    /**
     * Get the other half of a double blocks.  This is supposed to
     * find blocks that are placed or removed together.
     *
     * Example: double plant, door, bed
     * Counter example: double chest, which combines but is placed or
     * removed separately.
     *
     * @param block the block location
     * @param data the BlockData
     */
    public static Block getOtherHalf(Block block, BlockData data) {
        if (data instanceof Bisected) {
            Bisected bis = (Bisected) data;
            switch (bis.getHalf()) {
            case TOP: return block.getRelative(0, -1, 0);
            case BOTTOM: return block.getRelative(0, 1, 0);
            default: return null;
            }
        } else if (data instanceof Bed) {
            Bed bed = (Bed) data;
            BlockFace facing = bed.getFacing();
            switch (bed.getPart()) {
            case FOOT: return block.getRelative(facing);
            case HEAD: return block.getRelative(facing.getOppositeFace());
            default: return null;
            }
        }
        return null;
    }

    public static BlockFace rotateLeft(BlockFace face) {
        switch (face) {
        case NORTH: return BlockFace.WEST;
        case EAST: return BlockFace.NORTH;
        case SOUTH: return BlockFace.EAST;
        case WEST: return BlockFace.SOUTH;
        default: return null;
        }
    }

    public static BlockFace rotateRight(BlockFace face) {
        switch (face) {
        case NORTH: return BlockFace.EAST;
        case EAST: return BlockFace.SOUTH;
        case SOUTH: return BlockFace.WEST;
        case WEST: return BlockFace.NORTH;
        default: return null;
        }
    }

    /**
     * Find the corresponding pair of blocks that is connected with
     * this but placed separately.
     * (Only) Example: double chest
     */
    public static Block getConnected(Block block, BlockData data) {
        if (data instanceof Chest) {
            Chest chest = (Chest) data;
            BlockFace facing = chest.getFacing();
            switch (chest.getType()) {
            case SINGLE: return null;
            case LEFT: return block.getRelative(rotateRight(facing));
            case RIGHT: return block.getRelative(rotateLeft(facing));
            default: return null;
            }
        }
        return null;
    }

    public static String toString(Block block) {
        return block.getWorld().getName() + "/" + block.getX() + "," + block.getY() + "," + block.getZ();
    }

    public static boolean equals(Block a, Block b) {
        return a.getWorld().equals(b.getWorld())
            && a.getX() == b.getX()
            && a.getY() == b.getY()
            && a.getZ() == b.getZ();
    }
}
