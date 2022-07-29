package com.cavetale.watchman.action;

import org.junit.Test;
import java.util.Map;
import java.util.HashMap;

public final class ActionTypeTest {
    @Test
    public void test() {
        Map<Integer, ActionType> indexes = new HashMap<>();
        for (ActionType actionType : ActionType.values()) {
            ActionType otherType = indexes.get(actionType.index);
            if (otherType != null) {
                throw new IllegalStateException("Duplicate Action index: " + actionType.index
                                                + " " + otherType + "/" + actionType);
            }
            indexes.put(actionType.index, actionType);
        }
    }
}
