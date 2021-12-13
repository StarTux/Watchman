package com.cavetale.watchman;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum ActionType {
    PLAYER_JOIN(0, "join", Category.PLAYER),
    PLAYER_QUIT(1, "quit", Category.PLAYER),
    PLAYER_DEATH(2, "death", Category.PLAYER),
    BLOCK_BREAK(3, "break", Category.BLOCK),
    BLOCK_PLACE(4, "place", Category.BLOCK),
    BLOCK_CHANGE(5, "change", Category.BLOCK),
    BLOCK_GROW(6, "grow", Category.BLOCK),
    BLOCK_FORM(7, "form", Category.BLOCK),
    BLOCK_SHEAR(8, "shear", Category.BLOCK),
    BLOCK_DESTROY(9, "destroy", Category.BLOCK),
    BLOCK_FAKE(10, "fake", Category.BLOCK),
    BUCKET_EMPTY(11, "bucket", Category.BLOCK),
    BUCKET_FILL(12, "debucket", Category.BLOCK),
    BLOCK_EXPLODE(13, "explode", Category.BLOCK),
    BLOCK_WORLDEDIT(14, "worldedit", Category.BLOCK),
    ENTITY_KILL(15, "kill", Category.ENTITY),
    ENTITY_PLACE(16, "place", Category.ENTITY),
    ITEM_DROP(17, "drop", Category.INVENTORY),
    ITEM_PICKUP(18, "pickup", Category.INVENTORY),
    ITEM_INSERT(19, "insert", Category.INVENTORY),
    ITEM_REMOVE(20, "remove", Category.INVENTORY),
    ITEM_SWAP(21, "swap", Category.INVENTORY),
    INVENTORY_OPEN(22, "open", Category.INVENTORY),
    COMMAND(23, "command", Category.CHAT),
    CHAT(24, "chat", Category.CHAT);

    public enum Category {
        /**
         * Block changing from oldState to newState.
         */
        BLOCK,

        /**
         * Player related.
         */
        PLAYER,

        /**
         * Entity related.
         */
        ENTITY,

        INVENTORY,
        CHAT;
    }

    public final int index;
    public final String key;
    public final String human;
    public final Category category;
    private static final ActionType[] INDEX_ARRAY;

    ActionType(final int index, final String human, final Category category) {
        this.index = index;
        this.key = name().toLowerCase();
        this.human = human;
        this.category = category;
    }

    static {
        ActionType[] array = ActionType.values();
        INDEX_ARRAY = new ActionType[array.length];
        for (ActionType actionType : array) {
            INDEX_ARRAY[actionType.index] = actionType;
        }
    }

    public static List<String> inCategory(Category category) {
        return Stream.of(ActionType.values())
            .filter(t -> t.category == category)
            .map(t -> t.key)
            .collect(Collectors.toList());
    }

    public static List<String> inCategories(Category first, Category... others) {
        Set<Category> set = EnumSet.of(first, others);
        return Stream.of(ActionType.values())
            .filter(t -> set.contains(t.category))
            .map(t -> t.key)
            .collect(Collectors.toList());
    }

    public static ActionType ofIndex(int index) {
        return INDEX_ARRAY[index];
    }
}
