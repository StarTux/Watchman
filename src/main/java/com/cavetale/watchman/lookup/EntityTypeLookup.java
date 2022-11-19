package com.cavetale.watchman.lookup;

import com.cavetale.watchman.sql.SQLLog;
import com.winthier.sql.SQLTable;
import org.bukkit.entity.EntityType;
import static com.cavetale.watchman.WatchmanPlugin.dictionary;

public final class EntityTypeLookup extends ChangedTypeLookup {
    private final EntityType entityType;

    public EntityTypeLookup(final EntityType entityType) {
        super('e');
        this.entityType = entityType;
    }

    @Override
    public SQLTable<SQLLog>.Finder accept(SQLTable<SQLLog>.Finder finder) {
        return finder.eq("changedEnum", dictionary().getIndex(entityType));
    }

    @Override
    public String getParameters() {
        return "" + prefix + ":" + entityType.getKey().getKey();
    }
}
