package com.cavetale.watchman.lookup;

import com.cavetale.watchman.sql.SQLLog;
import com.winthier.sql.SQLTable;
import org.bukkit.Material;
import static com.cavetale.watchman.WatchmanPlugin.dictionary;

public final class MaterialLookup extends ChangedTypeLookup {
    private final Material material;

    public MaterialLookup(final char prefix, final Material material) {
        super(prefix);
        this.material = material;
    }

    @Override
    public SQLTable<SQLLog>.Finder accept(SQLTable<SQLLog>.Finder finder) {
        return finder.eq("changedEnum", dictionary().getIndex(material));
    }

    @Override
    public String getParameters() {
        return "" + prefix + ":" + material.getKey().getKey();
    }
}
