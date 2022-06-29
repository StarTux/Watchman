package com.cavetale.watchman.lookup;

public abstract class ChangedTypeLookup implements Lookup {
    protected final char prefix;

    ChangedTypeLookup(final char prefix) {
        this.prefix = prefix;
    }
}
