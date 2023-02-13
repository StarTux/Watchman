package com.cavetale.watchman.lookup;

import com.cavetale.core.connect.Connect;
import com.cavetale.watchman.sql.SQLLog;
import com.winthier.sql.SQLTable;
import lombok.AllArgsConstructor;
import lombok.Setter;
import org.bukkit.Location;
import static com.cavetale.watchman.WatchmanPlugin.dictionary;

@AllArgsConstructor
public final class RadiusLookup implements PlaceLookup {
    private final String server;
    @Setter private String world;
    private final int x;
    private final int y;
    private final int z;
    private final int radius;

    public static RadiusLookup of(Location location, int radius) {
        return new RadiusLookup(Connect.get().getServerName(),
                                location.getWorld().getName(),
                                location.getBlockX(), location.getBlockY(), location.getBlockZ(),
                                radius);
    }

    @Override
    public SQLTable<SQLLog>.Finder accept(SQLTable<SQLLog>.Finder finder) {
        return finder.eq("server", dictionary().getServerIndex(server))
            .eq("world", dictionary().getWorldIndex(world))
            .between("z", z - radius, z + radius)
            .between("x", x - radius, x + radius)
            .between("y", y - radius, y + radius);
    }

    @Override
    public String getParameters() {
        return "r:" + radius;
    }
}
