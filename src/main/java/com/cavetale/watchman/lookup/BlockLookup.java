package com.cavetale.watchman.lookup;

import com.cavetale.core.connect.Connect;
import com.cavetale.watchman.sql.SQLLog;
import com.winthier.sql.SQLTable;
import lombok.AllArgsConstructor;
import lombok.Setter;
import org.bukkit.block.Block;
import static com.cavetale.watchman.WatchmanPlugin.dictionary;

/**
 * Used by wmtool.
 */
@AllArgsConstructor
public final class BlockLookup implements PlaceLookup {
    private final String server;
    @Setter private String world;
    private final int x;
    private final int y;
    private final int z;

    public static BlockLookup of(Block block) {
        return new BlockLookup(Connect.get().getServerName(),
                               block.getWorld().getName(),
                               block.getX(), block.getY(), block.getZ());
    }

    @Override
    public SQLTable<SQLLog>.Finder accept(SQLTable<SQLLog>.Finder finder) {
        return finder.eq("server", dictionary().getServerIndex(server))
            .eq("world", dictionary().getWorldIndex(world))
            .eq("x", x)
            .eq("y", y)
            .eq("z", z)
            .orderByDescending("time");
    }

    @Override
    public String getParameters() {
        return "wmtool " + getXYZ();
    }

    public String getXYZ() {
        return x + " " + y + " " + z;
    }
}
