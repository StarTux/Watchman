package com.cavetale.watchman.session;

import com.cavetale.watchman.WatchmanPlugin;
import com.cavetale.watchman.action.Action;
import com.cavetale.watchman.sql.SQLLog;
import com.cavetale.watchman.sql.SQLLookupSession;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import static com.cavetale.watchman.WatchmanPlugin.database;
import static net.kyori.adventure.text.Component.join;
import static net.kyori.adventure.text.Component.space;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.JoinConfiguration.separator;
import static net.kyori.adventure.text.event.ClickEvent.runCommand;
import static net.kyori.adventure.text.event.HoverEvent.showText;
import static net.kyori.adventure.text.format.NamedTextColor.*;

@RequiredArgsConstructor
public final class Sessions implements Listener {
    private final WatchmanPlugin plugin;
    private Map<UUID, LookupSession> lookupSessionMap = new HashMap<>();
    private Set<UUID> wmtools = new HashSet<>();

    public void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void wmtool(Player player, boolean value) {
        if (value) {
            wmtools.add(player.getUniqueId());
        } else {
            wmtools.remove(player.getUniqueId());
        }
    }

    public boolean wmtool(Player player) {
        return wmtools.contains(player.getUniqueId());
    }

    public void get(UUID uuid, Consumer<LookupSession> callback) {
        LookupSession cached = lookupSessionMap.get(uuid);
        if (cached != null) {
            callback.accept(cached);
            return;
        }
        database().scheduleAsyncTask(() -> {
                SQLLookupSession row = database().find(SQLLookupSession.class)
                    .eq("uuid", uuid)
                    .findUnique();
                if (row == null) {
                    Bukkit.getScheduler().runTask(plugin, () -> callback.accept(null));
                    return;
                }
                SQLLookupSession.Tag tag = row.parseTag();
                List<Action> actions = new ArrayList<>();
                for (SQLLog log : tag.getLogs()) {
                    actions.add(new Action().fetch(log));
                }
                LookupSession session = new LookupSession(uuid, tag.getParams(), actions);
                lookupSessionMap.put(uuid, session);
                Bukkit.getScheduler().runTask(plugin, () -> callback.accept(session));
            });
    }

    public void set(UUID uuid, String params, List<SQLLog> logs, Consumer<LookupSession> callback) {
        database().scheduleAsyncTask(() -> {
                SQLLookupSession row = new SQLLookupSession(uuid, params, logs);
                database().save(row);
                List<Action> actions = new ArrayList<>();
                for (SQLLog log : logs) {
                    actions.add(new Action().fetch(log));
                }
                LookupSession session = new LookupSession(uuid, params, actions);
                lookupSessionMap.put(uuid, session);
                Bukkit.getScheduler().runTask(plugin, () -> callback.accept(session));
            });
    }

    public void clear(UUID uuid) {
        lookupSessionMap.remove(uuid);
        database().find(SQLLookupSession.class).eq("uuid", uuid).deleteAsync(null);
    }

    public void showPage(Player player, int pageIndex) {
        get(player.getUniqueId(), session -> {
                if (session == null) {
                    player.sendMessage(text("No lookup to show", RED));
                    return;
                }
                if (session.getActions().isEmpty()) {
                    player.sendMessage(text("No logs to show: " + session.getParams(), RED));
                    return;
                }
                int pageSize = 10;
                int pageCount = (session.getActions().size() - 1) / pageSize + 1;
                if (pageIndex < 0 || pageIndex >= pageCount) {
                    player.sendMessage(text("Invalid page: " + (pageIndex + 1) + "/" + pageCount));
                }
                List<Integer> pageButtons = new ArrayList<>();
                pageButtons.add(0);
                if (pageIndex > 1
                    && !pageButtons.contains(pageIndex - 1)) pageButtons.add(pageIndex - 1);
                if (pageIndex < pageCount - 1
                    && !pageButtons.contains(pageIndex + 1)) pageButtons.add(pageIndex + 1);
                if (!pageButtons.contains(pageCount - 1)) pageButtons.add(pageCount - 1);
                List<Component> line = new ArrayList<>();
                for (int pageButton : pageButtons) {
                    int pageNumber = pageButton + 1;
                    String cmd = "/wmpage " + pageNumber;
                    line.add(text("[" + pageNumber + "]", AQUA)
                             .hoverEvent(showText(text(cmd, AQUA)))
                             .clickEvent(runCommand(cmd)));
                }
                line.add(text("Page " + (pageIndex + 1) + "/" + pageCount + ": " + session.getParams(), YELLOW));
                player.sendMessage(join(separator(space()), line));
                for (int i = 0; i < pageSize; i += 1) {
                    int actionIndex = pageIndex * pageSize + i;
                    if (actionIndex >= session.getActions().size()) break;
                    Action action = session.getActions().get(i);
                    player.sendMessage(action.getMessage());
                }
            });
    }

    @EventHandler
    private void onPlayerQuit(PlayerQuitEvent event) {
        lookupSessionMap.remove(event.getPlayer().getUniqueId());
        wmtools.remove(event.getPlayer().getUniqueId());
    }
}
