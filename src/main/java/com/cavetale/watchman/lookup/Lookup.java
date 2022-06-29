package com.cavetale.watchman.lookup;

import com.cavetale.watchman.sql.SQLLog;
import com.winthier.sql.SQLTable;

public interface Lookup {
    SQLTable<SQLLog>.Finder accept(SQLTable<SQLLog>.Finder finder);

    String getParameters();
}
