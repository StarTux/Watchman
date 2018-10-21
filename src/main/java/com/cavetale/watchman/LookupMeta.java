package com.cavetale.watchman;

import java.util.UUID;
import lombok.RequiredArgsConstructor;

final class LookupMeta {
    @RequiredArgsConstructor static final class Vec {
        final int x, y, z;
    }
    Vec location = null;
    String world = null;
    UUID player = null;
    SQLAction.Type action = null;
    boolean global, worldwide;
    int cx, cz, radius;
    String oldType, newType;
    Long after = null;
}
