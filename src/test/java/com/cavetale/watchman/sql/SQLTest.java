package com.cavetale.watchman.sql;

import com.winthier.sql.SQLDatabase;
import org.junit.Test;

public final class SQLTest {
    @Test
    public void test() {
        System.out.println(SQLDatabase.testTableCreation(SQLLog.class));
        System.out.println(SQLDatabase.testTableCreation(SQLExtra.class));
        System.out.println(SQLDatabase.testTableCreation(SQLDictionary.class));
        System.out.println(SQLDatabase.testTableCreation(SQLLookupSession.class));
    }
}
