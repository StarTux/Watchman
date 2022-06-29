package com.cavetale.watchman.lookup;

import com.cavetale.watchman.action.ActorType;
import com.cavetale.watchman.sql.SQLLog;
import com.winthier.playercache.PlayerCache;
import com.winthier.sql.SQLTable;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import static com.cavetale.watchman.WatchmanPlugin.dictionary;

@RequiredArgsConstructor
public final class PlayerLookup implements Lookup {
    private final UUID uuid;

    @Override
    public SQLTable<SQLLog>.Finder accept(SQLTable<SQLLog>.Finder finder) {
        return finder.eq("actorType", ActorType.PLAYER.index)
            .eq("actorUuid", dictionary().getIndex(uuid));
    }

    @Override
    public String getParameters() {
        return "p:" + PlayerCache.nameForUuid(uuid);
    }
}
