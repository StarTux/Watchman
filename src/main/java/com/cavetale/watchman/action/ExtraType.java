package com.cavetale.watchman.action;

import lombok.RequiredArgsConstructor;

/**
 * The key for the SQLExtra row.
 */
@RequiredArgsConstructor
public enum ExtraType {
    OLD_BLOCK_STRUCTURE(0),
    NEW_BLOCK_STRUCTURE(1),
    ENTITY(2),
    ITEM(3),
    CHAT_MESSAGE(5),
    ;

    public final int index;
    private static final ExtraType[] INDEX_ARRAY;

    static {
        int max = 0;
        ExtraType[] array = values();
        for (ExtraType it : array) {
            if (it.index > max) max = it.index;
        }
        INDEX_ARRAY = new ExtraType[max + 1];
        for (ExtraType it : array) {
            INDEX_ARRAY[it.index] = it;
        }
    }

    public static ExtraType ofIndex(int index) {
        return INDEX_ARRAY[index];
    }
}
