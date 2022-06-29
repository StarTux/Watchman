package com.cavetale.watchman;

import com.cavetale.watchman.action.Action;
import com.cavetale.watchman.session.Sessions;
import com.cavetale.watchman.sql.SQLDictionary;
import com.cavetale.watchman.sql.SQLExtra;
import com.cavetale.watchman.sql.SQLLog;
import com.cavetale.watchman.sql.SQLLookupSession;
import com.winthier.sql.SQLDatabase;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class WatchmanPlugin extends JavaPlugin {
    @Getter private static WatchmanPlugin instance;
    protected SQLDatabase database;
    protected List<Action> actionQueue = new ArrayList<>();
    protected WatchmanCommand watchmanCommand = new WatchmanCommand(this);
    protected WatchmanToolCommand watchmanToolCommand = new WatchmanToolCommand(this);
    protected WatchmanPageCommand watchmanPageCommand = new WatchmanPageCommand(this);
    protected RewindCommand rewindCommand = new RewindCommand(this);
    protected EventListener eventListener = new EventListener(this);
    private WorldEditListener worldEditListener = new WorldEditListener(this);
    protected boolean eventBlockGrow;
    protected boolean eventBlockForm;
    protected boolean eventBlockSpread;
    protected boolean eventEntityBlockForm;
    protected boolean worldEdit;
    protected Duration logExpiry;
    private Dictionary dictionary = new Dictionary(this);
    protected Sessions sessions = new Sessions(this);

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConf();
        instance = this;
        database = new SQLDatabase(this);
        database.registerTables(List.of(SQLLog.class,
                                        SQLExtra.class,
                                        SQLDictionary.class,
                                        SQLLookupSession.class));
        database.createAllTables();
        watchmanCommand.enable();
        watchmanToolCommand.enable();
        watchmanPageCommand.enable();
        rewindCommand.enable();
        eventListener.enable();
        sessions.enable();
        if (worldEdit && Bukkit.getPluginManager().isPluginEnabled("WorldEdit")) {
            worldEditListener.enable();
        }
        Bukkit.getScheduler().runTaskTimer(this, this::drainStorage, 20L, 20L);
        Bukkit.getScheduler().runTaskLater(this, this::deleteExpiredLogs, 20L);
    }

    @Override
    public void onDisable() {
        drainStorage();
        database.waitForAsyncTask();
    }

    protected void loadConf() {
        reloadConfig();
        eventBlockGrow = getConfig().getBoolean("Events.BlockGrow");
        eventBlockForm = getConfig().getBoolean("Events.BlockForm");
        eventBlockSpread = getConfig().getBoolean("Events.BlockSpread");
        eventEntityBlockForm = getConfig().getBoolean("Events.EntityBlockForm");
        worldEdit = getConfig().getBoolean("WorldEdit");
        logExpiry = Duration.ofDays(getConfig().getLong("LogExpiry", 30L));
        getLogger().info("LogExpiry=" + logExpiry.toDays());
    }

    protected boolean expiring;

    private void deleteExpiredLogs() {
        if (expiring) return;
        final long now = System.currentTimeMillis();
        final int limit = 30_000;
        getLogger().info("Deleting expired logs: limit=" + limit);
        expiring = true;
        database.find(SQLLog.class)
            .lt("expiry", now)
            .limit(limit)
            .deleteAsync(count -> {
                    long stop = (System.currentTimeMillis() - now) / 1000L;
                    getLogger().info("Deleted " + count + " old actions in " + stop + "s");
                    final long delay = limit == count ? 20L : 20L * 60L * 10L;
                    Bukkit.getScheduler().runTaskLater(this, this::deleteExpiredLogs, delay);
                    expiring = false;
                });
    }

    public void store(Action action) {
        if (action.getServer() == null || action.getWorld() == null) {
            throw new IllegalStateException(action.toString());
        }
        actionQueue.add(action);
    }

    protected volatile boolean draining;

    /**
     * Call regularly and when plugin is disabled.
     */
    private void drainStorage() {
        if (draining || actionQueue.isEmpty()) {
            return;
        }
        List<Action> actions = List.copyOf(actionQueue);
        actionQueue.clear();
        draining = true;
        database.scheduleAsyncTask(() -> {
                List<SQLLog> logs = new ArrayList<>();
                //List<SQLExtra> extras = new ArrayList<>();
                for (Action action : actions) {
                    logs.add(action.store());
                }
                final int max = 1024;
                int size = logs.size();
                for (int i = 0; i < size; i += max) {
                    List<SQLLog> slice = List.copyOf(logs.subList(i, Math.min(size, i + max)));
                    database.insertIgnore(slice);
                }
                List<SQLExtra> extraList = new ArrayList<>();
                for (Action action : actions) {
                    SQLLog log = action.getLog();
                    if (log.getId() == null) {
                        getLogger().warning("Could not store log: " + log);
                    }
                    if (log.getExtra() == 0) continue;
                    action.makeExtras(extraList);
                }
                size = extraList.size();
                for (int i = 0; i < size; i += max) {
                    List<SQLExtra> slice = List.copyOf(extraList.subList(i, Math.min(size, i + max)));
                    database.insertIgnore(slice);
                }
                for (SQLExtra extra : extraList) {
                    if (extra.getId() == null) {
                        getLogger().warning("Could not store extra: " + extra);
                    }
                }
                draining = false;
            });
    }

    public static Dictionary dictionary() {
        return instance.dictionary;
    }

    public static SQLDatabase database() {
        return instance.database;
    }

    public static Duration logExpiry() {
        return instance.logExpiry;
    }
}
