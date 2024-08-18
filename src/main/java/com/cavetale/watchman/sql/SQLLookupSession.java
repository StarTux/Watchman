package com.cavetale.watchman.sql;

import com.cavetale.core.util.Json;
import com.winthier.sql.SQLRow;
import com.winthier.sql.SQLRow.Name;
import com.winthier.sql.SQLRow.NotNull;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import lombok.Data;

@Data @NotNull @Name("lookup_sessions")
public final class SQLLookupSession implements SQLRow {
    @Id private Integer id;
    @Unique private UUID uuid;
    @LongText private String tag;
    private Date created;

    public SQLLookupSession() { }

    public SQLLookupSession(final UUID uuid, final String params, final List<SQLLog> logs) {
        this.uuid = uuid;
        Tag theTag = new Tag();
        theTag.params = params;
        theTag.logs = logs;
        this.tag = Json.serialize(theTag);
        this.created = new Date();
    }

    public Tag parseTag() {
        return Json.deserialize(tag, Tag.class);
    }

    @Data
    public static class Tag {
        protected String params;
        protected List<SQLLog> logs;
    }
}
