package com.cavetale.watchman.lookup;

import com.cavetale.watchman.sql.SQLLog;
import com.winthier.sql.SQLTable;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public final class LookupContainer implements Lookup {
    protected PlaceLookup where;
    protected PlayerLookup who;
    protected ActionTypeLookup actionType;
    protected ChangedTypeLookup changedType;
    protected TimeLookup time;

    public boolean isEmpty() {
        return listLookups().isEmpty();
    }

    private List<Lookup> listLookups() {
        List<Lookup> list = new ArrayList<>();
        if (where != null) list.add(where);
        if (who != null) list.add(who);
        if (actionType != null) list.add(actionType);
        if (changedType != null) list.add(changedType);
        if (time != null) list.add(time);
        return list;
    }

    @Override
    public SQLTable<SQLLog>.Finder accept(SQLTable<SQLLog>.Finder finder) {
        for (Lookup lookup : listLookups()) {
            lookup.accept(finder);
        }
        return finder;
    }

    @Override
    public String getParameters() {
        List<String> params = new ArrayList<>();
        for (Lookup lookup : listLookups()) {
            params.add(lookup.getParameters());
        }
        return String.join(" ", params);
    }
}
