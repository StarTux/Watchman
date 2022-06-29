package com.cavetale.watchman.action;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum ActorType {
    UNKNOWN(0),
    PLAYER(1),
    ENTITY(2),
    BLOCK(3),
    NATURE(4),
    FAKE(5),
    ;

    public final int index;
    private static final ActorType[] INDEX_ARRAY;

    static {
        ActorType[] array = values();
        INDEX_ARRAY = new ActorType[array.length];
        for (ActorType actorType : array) {
            INDEX_ARRAY[actorType.index] = actorType;
        }
    }

    public static ActorType ofIndex(int index) {
        return INDEX_ARRAY[index];
    }
}
