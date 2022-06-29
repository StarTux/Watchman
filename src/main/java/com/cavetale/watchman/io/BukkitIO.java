package com.cavetale.watchman.io;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.bukkit.Bukkit;
import org.bukkit.block.BlockState;
import org.bukkit.structure.Structure;
import org.bukkit.util.BlockVector;

public final class BukkitIO {
    private static final BlockVector BLOCK_VECTOR_ONE = new BlockVector(1, 1, 1);

    public static byte[] serializeBlockState(BlockState blockState) {
        Structure structure = Bukkit.getStructureManager().createStructure();
        structure.fill(blockState.getLocation(), BLOCK_VECTOR_ONE, false);
        return serializeStructure(structure);
    }

    public static byte[] serializeStructure(Structure structure) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Bukkit.getStructureManager().saveStructure(baos, structure);
            return baos.toByteArray();
        } catch (IOException ioe) {
            throw new IllegalStateException(ioe);
        }
    }

    public static Structure deserializeStructure(byte[] bytes) {
        try {
            return Bukkit.getStructureManager().loadStructure(new ByteArrayInputStream(bytes));
        } catch (IOException ioe) {
            throw new IllegalStateException(ioe);
        }
    }

    private BukkitIO() { }
}
