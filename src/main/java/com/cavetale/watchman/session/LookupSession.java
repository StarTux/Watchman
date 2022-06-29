package com.cavetale.watchman.session;

import com.cavetale.watchman.action.Action;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data @AllArgsConstructor
public final class LookupSession {
    private final UUID uuid;
    private final String params;
    private final List<Action> actions;
}
