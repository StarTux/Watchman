package com.cavetale.watchman;

import java.util.UUID;
import lombok.RequiredArgsConstructor;

final class LookupMeta {
    @RequiredArgsConstructor static final class Vec {
        final int x;
        final int y;
        final int z;
    }
    Vec location = null;
    String world = null;
    UUID player = null;
    SQLAction.Type action = null;
    boolean global;
    boolean worldwide;
    boolean worldedit;
    int cx;
    int cz;
    Cuboid selection; // set when worldedit=true
    int radius;
    String oldType;
    String newType;
    Long after = null;
}
