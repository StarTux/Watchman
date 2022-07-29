package com.cavetale.watchman.action;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum ActionType {
    BREAK(0, "break", Category.BLOCK),
    PLACE(1, "place", Category.BLOCK),

    KILL(2, "kill", Category.ENTITY),
    SPAWN(3, "spawn", Category.ENTITY),

    OPEN(4, "open", Category.INVENTORY),
    ACCESS(5, "access", Category.INVENTORY),

    DROP(6, "drop", Category.ITEM),
    PICKUP(7, "pickup", Category.ITEM),
    WRITE(12, "write", Category.ITEM),

    CHAT(8, "said", Category.PLAYER),
    JOIN(9, "join", Category.PLAYER),
    QUIT(10, "quit", Category.PLAYER),
    DEATH(11, "died", Category.PLAYER);

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

        ITEM,
        INVENTORY;
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
