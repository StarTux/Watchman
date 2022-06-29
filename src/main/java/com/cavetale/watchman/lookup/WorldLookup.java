package com.cavetale.watchman.lookup;

import com.cavetale.core.connect.Connect;
import com.cavetale.watchman.sql.SQLLog;
import com.winthier.sql.SQLTable;
import lombok.RequiredArgsConstructor;
import org.bukkit.World;
import static com.cavetale.watchman.WatchmanPlugin.dictionary;

@RequiredArgsConstructor
public final class WorldLookup implements PlaceLookup {
    private final String server;
    private final String world;

    public static WorldLookup of(World w) {
        return new WorldLookup(Connect.get().getServerName(), w.getName());
    }

    public static WorldLookup of(String w) {
        return new WorldLookup(Connect.get().getServerName(), w);
    }

    @Override
    public SQLTable<SQLLog>.Finder accept(SQLTable<SQLLog>.Finder finder) {
        return finder.eq("server", dictionary().getServerIndex(server))
            .eq("world", dictionary().getWorldIndex(world));
    }

    @Override
    public String getParameters() {
        return "w:" + world;
    }
}
