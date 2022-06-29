package com.cavetale.watchman.lookup;

import com.cavetale.watchman.action.ActionType;
import com.cavetale.watchman.sql.SQLLog;
import com.winthier.sql.SQLTable;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class ActionTypeLookup implements Lookup {
    private final ActionType actionType;

    @Override
    public SQLTable<SQLLog>.Finder accept(SQLTable<SQLLog>.Finder finder) {
        return finder.eq("actionType", actionType.index);
    }

    @Override
    public String getParameters() {
        return "a:" + actionType.name().toLowerCase();
    }
}
