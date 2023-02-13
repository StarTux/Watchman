package com.cavetale.watchman.lookup;

import com.cavetale.core.connect.Connect;
import com.cavetale.watchman.Cuboid;
import com.cavetale.watchman.sql.SQLLog;
import com.winthier.sql.SQLTable;
import lombok.AllArgsConstructor;
import lombok.Setter;
import org.bukkit.World;
import static com.cavetale.watchman.WatchmanPlugin.dictionary;

@AllArgsConstructor
public final class SelectionLookup implements PlaceLookup {
    private final String server;
    @Setter private String world;
    private final Cuboid cuboid;

    public static SelectionLookup of(World w, Cuboid cuboid) {
        return new SelectionLookup(Connect.get().getServerName(), w.getName(), cuboid);
    }

    @Override
    public SQLTable<SQLLog>.Finder accept(SQLTable<SQLLog>.Finder finder) {
        return finder.eq("server", dictionary().getServerIndex(server))
            .eq("world", dictionary().getWorldIndex(world))
            .between("z", cuboid.az, cuboid.bz)
            .between("x", cuboid.ax, cuboid.bx)
            .between("y", cuboid.ay, cuboid.by);
    }

    @Override
    public String getParameters() {
        return "r:we";
    }
}
