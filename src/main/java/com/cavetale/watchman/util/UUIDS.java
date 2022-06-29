package com.cavetale.watchman.util;

import java.util.UUID;

public final class UUIDS {
    public static byte[] uuidToBytes(UUID uuid) {
        long lo = uuid.getLeastSignificantBits();
        long hi = uuid.getMostSignificantBits();
        return new byte[] {
            (byte) ((hi >> 56) & 0xFF),
            (byte) ((hi >> 48) & 0xFF),
            (byte) ((hi >> 40) & 0xFF),
            (byte) ((hi >> 32) & 0xFF),
            (byte) ((hi >> 24) & 0xFF),
            (byte) ((hi >> 16) & 0xFF),
            (byte) ((hi >> 8) & 0xFF),
            (byte) ((hi >> 0) & 0xFF),
            //
            (byte) ((lo >> 56) & 0xFF),
            (byte) ((lo >> 48) & 0xFF),
            (byte) ((lo >> 40) & 0xFF),
            (byte) ((lo >> 32) & 0xFF),
            (byte) ((lo >> 24) & 0xFF),
            (byte) ((lo >> 16) & 0xFF),
            (byte) ((lo >> 8) & 0xFF),
            (byte) ((lo >> 0) & 0xFF),
        };
    }

    public static UUID uuidFromBytes(byte[] bytes) {
        long lo = 0L
            | ((long) bytes[8] << 56) & 0xFF00000000000000L
            | ((long) bytes[9] << 48) & 0xFF000000000000L
            | ((long) bytes[10] << 40) & 0xFF0000000000L
            | ((long) bytes[11] << 32) & 0xFF00000000L
            | ((long) bytes[12] << 24) & 0xFF000000L
            | ((long) bytes[13] << 16) & 0xFF0000L
            | ((long) bytes[14] << 8) & 0xFF00L
            | ((long) bytes[15] << 0) & 0xFFL;
        long hi = 0L
            | ((long) bytes[0] << 56) & 0xFF00000000000000L
            | ((long) bytes[1] << 48) & 0xFF000000000000L
            | ((long) bytes[2] << 40) & 0xFF0000000000L
            | ((long) bytes[3] << 32) & 0xFF00000000L
            | ((long) bytes[4] << 24) & 0xFF000000L
            | ((long) bytes[5] << 16) & 0xFF0000L
            | ((long) bytes[6] << 8) & 0xFF00L
            | ((long) bytes[7] << 0) & 0xFFL;
        return new UUID(hi, lo);
    }

    private UUIDS() { }
}
