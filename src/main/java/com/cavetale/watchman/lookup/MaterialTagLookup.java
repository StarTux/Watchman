package com.cavetale.watchman.lookup;

import com.cavetale.watchman.sql.SQLLog;
import com.winthier.sql.SQLTable;
import java.util.Set;
import java.util.TreeSet;
import org.bukkit.Material;
import org.bukkit.Tag;
import static com.cavetale.watchman.WatchmanPlugin.dictionary;

public final class MaterialTagLookup extends ChangedTypeLookup {
    private final Tag<Material> tag;

    public MaterialTagLookup(final char prefix, final Tag<Material> tag) {
        super(prefix);
        this.tag = tag;
    }

    @Override
    public SQLTable<SQLLog>.Finder accept(SQLTable<SQLLog>.Finder finder) {
        Set<Integer> set = new TreeSet<>();
        for (Material mat : tag.getValues()) {
            set.add(dictionary().getIndex(mat));
        }
        return finder.in("changedEnum", set);
    }

    @Override
    public String getParameters() {
        return prefix + ':' + '#' + tag.getKey().getKey();
    }
}
