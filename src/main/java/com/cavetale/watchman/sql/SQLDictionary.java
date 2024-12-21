package com.cavetale.watchman.sql;

import com.winthier.sql.SQLRow;
import com.winthier.sql.SQLRow.Name;
import com.winthier.sql.SQLRow.NotNull;
import com.winthier.sql.SQLRow.UniqueKey;
import java.util.UUID;
import lombok.Data;

@Data @NotNull @Name("dictionary")
@UniqueKey({"namespace", "key"})
public final class SQLDictionary implements SQLRow {
    @Id private Integer id;
    @VarChar(80) private String namespace;
    @VarChar(255) private String key;
    private transient Enum enumCache;

    public SQLDictionary() { }

    public SQLDictionary(final String namespace, final String key) {
        this.namespace = namespace;
        this.key = key;
    }

    public SQLDictionary(final Enum it) {
        this(it.getClass().getName(), it.name());
        enumCache = it;
    }

    public SQLDictionary(final UUID uuid) {
        this("java.util.UUID", uuid.toString());
    }

    @SuppressWarnings("unchecked")
    public Enum getEnum() {
        if (enumCache == null) {
            try {
                Class clazz = Class.forName(namespace);
                enumCache = Enum.valueOf(clazz, key);
            } catch (ClassNotFoundException cnfe) {
                throw new IllegalStateException(toString(), cnfe);
            }
        }
        return enumCache;
    }

    public UUID getUuid() {
        return UUID.fromString(key);
    }
}
