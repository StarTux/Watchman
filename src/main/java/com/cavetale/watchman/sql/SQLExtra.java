package com.cavetale.watchman.sql;

import com.cavetale.watchman.action.ExtraType;
import com.winthier.sql.SQLRow.Key;
import com.winthier.sql.SQLRow.Name;
import com.winthier.sql.SQLRow.NotNull;
import com.winthier.sql.SQLRow;
import java.sql.Blob;
import java.sql.SQLException;
import javax.sql.rowset.serial.SerialBlob;
import javax.sql.rowset.serial.SerialException;
import lombok.Data;

/**
 * Each SQLLog may have additional extra data.
 */
@Data @NotNull @Name("extra")
@Key({"logId", "type"})
@Key({"expiry"})
public final class SQLExtra implements SQLRow {
    @Id private Integer id;

    private long logId;
    @TinyInt private int type; // ExtraType.index

    private long expiry;
    @MediumBlob private Blob data;

    public SQLExtra() { }

    public SQLExtra(final SQLLog log, final ExtraType type, final byte[] data) {
        this.logId = log.getId();
        this.expiry = log.getExpiry();
        this.type = type.index;
        try {
            this.data = new SerialBlob(data);
        } catch (SerialException se) {
            throw new IllegalStateException(se);
        } catch (SQLException sqle) {
            throw new IllegalStateException(sqle);
        }
    }

    public byte[] getDataBytes() {
        try {
            return data.getBytes(1L, (int) data.length());
        } catch (SQLException sqle) {
            throw new IllegalStateException(sqle);
        }
    }
}
