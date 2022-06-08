package com.cavetale.watchman;

import com.winthier.sql.SQLDatabase;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import lombok.Getter;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class WatchmanPlugin extends JavaPlugin {
    @Getter private static WatchmanPlugin instance;
    SQLDatabase database;
    List<SQLAction> storage = new ArrayList<>();
    WatchmanCommand watchmanCommand = new WatchmanCommand(this);
    WatchmanToolCommand watchmanToolCommand = new WatchmanToolCommand(this);
    WatchmanPageCommand watchmanPageCommand = new WatchmanPageCommand(this);
    RewindCommand rewindCommand = new RewindCommand(this);
    EventListener eventListener = new EventListener(this);
    WorldEditListener worldEditListener = new WorldEditListener(this);
    protected long deleteActionsAfter = 10L;
    protected boolean eventBlockGrow;
    protected boolean eventBlockForm;
    protected boolean eventBlockSpread;
    protected boolean eventEntityBlockForm;
    protected boolean worldEdit;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConf();
        instance = this;
        database = new SQLDatabase(this);
        database.registerTables(SQLAction.class);
        database.createAllTables();
        watchmanCommand.enable();
        watchmanToolCommand.enable();
        watchmanPageCommand.enable();
        rewindCommand.enable();
        eventListener.enable();
        worldEditListener.enable();
        Bukkit.getScheduler().runTaskTimer(this, this::drainStorage, 20L, 20L);
        Bukkit.getScheduler().runTaskLater(this, this::deleteExpiredLogs, 20L);
    }

    @Override
    public void onDisable() {
        for (Player player: getServer().getOnlinePlayers()) {
            exit(player);
        }
        drainStorage();
        database.waitForAsyncTask();
    }

    protected void loadConf() {
        reloadConfig();
        deleteActionsAfter = getConfig().getLong("DeleteActionsAfter", 10L);
        eventBlockGrow = getConfig().getBoolean("Events.BlockGrow");
        eventBlockForm = getConfig().getBoolean("Events.BlockForm");
        eventBlockSpread = getConfig().getBoolean("Events.BlockSpread");
        eventEntityBlockForm = getConfig().getBoolean("Events.EntityBlockForm");
        worldEdit = getConfig().getBoolean("WorldEdit");
        worldEditListener.disable();
    }

    protected void exit(Player player) {
        player.removeMetadata(Meta.TOOL_KEY, this);
        player.removeMetadata(Meta.LOOKUP, this);
        player.removeMetadata(Meta.LOOKUP_META, this);
    }

    private void deleteExpiredLogs() {
        final long days = deleteActionsAfter;
        final long now = System.currentTimeMillis();
        final Date then = new Date(now - Duration.ofDays(days).toMillis());
        final int limit = 10000;
        getLogger().info("Deleting actions older than " + days + " days (" + then + ") limit=" + limit);
        database.find(SQLAction.class)
            .lt("time", then)
            .limit(limit)
            .deleteAsync(count -> {
                    long stop = (System.currentTimeMillis() - now) / 1000L;
                    getLogger().info("Deleted " + count + " old actions in " + stop + "s");
                    final long delay = limit == count ? 20L : 20L * 60L * 10L;
                    Bukkit.getScheduler().runTaskLater(this, this::deleteExpiredLogs, delay);
                });
    }

    protected void store(SQLAction action) {
        storage.add(action);
    }

    /**
     * Call regularly and when plugin is disabled.
     */
    private void drainStorage() {
        if (storage.isEmpty()) return;
        List<SQLAction> copy = storage;
        storage = new ArrayList<>();
        for (SQLAction it : copy) {
            it.sanitize();
        }
        final int max = 1024;
        final int size = copy.size();
        for (int i = 0; i < size; i += max) {
            List<SQLAction> slice = copy.subList(i, Math.min(size, i + max));
            database.insertAsync(slice, null);
        }
    }

    protected void showActionPage(Player player, List<SQLAction> actions, LookupMeta meta, int page) {
        int pageLen = 5;
        int totalPageCount = (actions.size() - 1) / pageLen + 1;
        player.sendMessage(ChatColor.YELLOW + "Watchman log page " + (page + 1) + "/" + totalPageCount
                           + ChatColor.GRAY + ChatColor.ITALIC + " (" + actions.size() + " logs)");
        StringBuilder sb = new StringBuilder();
        if (meta.location != null) {
            sb.append("location=").append(meta.world)
                .append(":").append(meta.location.x)
                .append(",").append(meta.location.y)
                .append(",").append(meta.location.z);
        } else if (meta.global) {
            sb.append("global");
        } else if (meta.worldwide) {
            sb.append("worldwide");
            sb.append(" world=").append(meta.world);
        } else {
            sb.append("radius=").append(meta.radius);
            sb.append(" center=").append(meta.world)
                .append(":").append(meta.cx)
                .append(",").append(meta.cz);
        }
        if (meta.action != null) sb.append(" action=").append(meta.action.human);
        if (meta.type != null) sb.append(" type=").append(meta.type.getKey().toString());
        if (meta.oldType != null) sb.append(" old=").append(meta.oldType);
        if (meta.newType != null) sb.append(" new=").append(meta.newType);
        if (meta.seconds > 0L) sb.append(" time=").append(Time.formatSeconds(meta.seconds));
        player.sendMessage("Param: " + ChatColor.GRAY + sb.toString());
        int fromIndex = page * pageLen;
        int toIndex = fromIndex + pageLen - 1;
        for (int i = fromIndex; i <= toIndex; i += 1) {
            if (i >= actions.size()) continue;
            SQLAction action = actions.get(i);
            action.showShortInfo(player, meta, i);
        }
        // Prev and Next
        ComponentBuilder cb = new ComponentBuilder("");
        cb.append("[Prev]");
        if (page > 0) {
            cb.color(ChatColor.GOLD)
                .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/wmpage " + page))
                .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(ChatColor.GOLD + "/wmpage " + page)));
        } else {
            cb.color(ChatColor.DARK_GRAY);
        }
        cb.append("  ").reset();
        cb.append("[Next]");
        if (page < totalPageCount - 1) {
            cb.color(ChatColor.GOLD)
                .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/wmpage " + (page + 2)))
                .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(ChatColor.GOLD + "/wmpage " + (page + 2))));
        } else {
            cb.color(ChatColor.DARK_GRAY);
        }
        player.spigot().sendMessage(cb.create());
    }
}
