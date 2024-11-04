package com.cavetale.watchman.sql;

import com.winthier.sql.SQLRow;
import com.winthier.sql.SQLRow.Key;
import com.winthier.sql.SQLRow.Name;
import com.winthier.sql.SQLRow.NotNull;
import lombok.Data;

@Data @NotNull @Name("logs")
@Key({"server", "world", "x", "y", "z", "time"}) // /wmtool
@Key({"actorType", "actorUuid"}) // Player search
@Key({"expiry"}) // Delete Old
@Key({"server", "world"})
@Key({"actionType", "changedEnum"})
@Key("z")
@Key("x")
public final class SQLLog implements SQLRow {
    @Id private Long id;
    // Base
    private long time;
    private long expiry;
    @TinyInt private int actionType; // ActionType.index
    private int changedEnum; // SQLNamespace: Material OR EntityType
    // Actor
    @TinyInt private int actorType; // ActorType.index
    private int actorEnum; // SQLNamespace (EntityType or Material)
    private int actorUuid; // SQLNamespace (UUID)
    // Location
    private int server; // SQLNamespace ("server")
    private int world; // SQLNamespace ("world")
    private int x;
    private int y;
    private int z;
    @TinyInt private int extra; // SQLExtra count
    // Optional, action specific
    private int oldBlockData; // SQLNamespace
    private int newBlockData; // SQLNamespace
    @Default("0") private int eventName;
}
