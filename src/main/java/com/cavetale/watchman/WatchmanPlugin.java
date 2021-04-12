package com.cavetale.watchman;

import com.winthier.sql.SQLDatabase;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import lombok.Getter;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class WatchmanPlugin extends JavaPlugin {
    @Getter private static WatchmanPlugin instance;
    SQLDatabase database;
    List<SQLAction> storage = new ArrayList<>();
    WatchmanCommand watchmanCommand = new WatchmanCommand(this);
    EventListener eventListener = new EventListener(this);
    private EntityHider entityHider = null;
    protected long deleteActionsAfter = 10L;
    protected boolean eventBlockGrow;
    protected boolean eventBlockForm;
    protected boolean eventBlockSpread;
    protected boolean eventEntityBlockForm;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConf();
        instance = this;
        database = new SQLDatabase(this);
        database.registerTables(SQLAction.class);
        database.createAllTables();
        deleteExpiredLogs();
        watchmanCommand.enable();
        eventListener.enable();
        getServer().getScheduler().runTaskTimer(this, this::drainStorage, 20, 20);
    }

    @Override
    public void onDisable() {
        for (Player player: getServer().getOnlinePlayers()) {
            exit(player);
        }
        drainStorage();
        database.waitForAsyncTask();
    }

    void loadConf() {
        reloadConfig();
        deleteActionsAfter = getConfig().getLong("DeleteActionsAfter", 10L);
        eventBlockGrow = getConfig().getBoolean("Events.BlockGrow");
        eventBlockForm = getConfig().getBoolean("Events.BlockForm");
        eventBlockSpread = getConfig().getBoolean("Events.BlockSpread");
        eventEntityBlockForm = getConfig().getBoolean("Events.EntityBlockForm");
    }

    void exit(Player player) {
        player.removeMetadata(Meta.TOOL_KEY, this);
        player.removeMetadata(Meta.LOOKUP, this);
        player.removeMetadata(Meta.LOOKUP_META, this);
    }

    void deleteExpiredLogs() {
        long days = deleteActionsAfter;
        Date then = new Date(System.currentTimeMillis()
                             - days * 24L * 60L * 60L * 1000L);
        getLogger().info("Deleting actions older than " + days + " days (" + then + ")");
        database.find(SQLAction.class)
            .lt("time", then)
            .deleteAsync(count -> {
                    getLogger().info("Deleted " + count + " old actions");
                });
    }

    void store(SQLAction action) {
        storage.add(action);
    }

    /**
     * Call regularly and when plugin is disabled.
     */
    void drainStorage() {
        if (storage.isEmpty()) return;
        database.saveAsync(storage, null);
        storage = new ArrayList<>();
    }

    void showActionPage(Player player, List<SQLAction> actions, LookupMeta meta, int page) {
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
        if (meta.oldType != null) sb.append(" old=").append(meta.oldType);
        if (meta.newType != null) sb.append(" new=").append(meta.newType);
        if (meta.after != null) sb.append(" after=").append(new Date(meta.after));
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
                .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/watchman page " + page))
                .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(ChatColor.GOLD + "/wm page " + page)));
        } else {
            cb.color(ChatColor.DARK_GRAY);
        }
        cb.append("  ").reset();
        cb.append("[Next]");
        if (page < totalPageCount - 1) {
            cb.color(ChatColor.GOLD)
                .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/watchman page " + (page + 2)))
                .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(ChatColor.GOLD + "/wm page " + (page + 2))));
        } else {
            cb.color(ChatColor.DARK_GRAY);
        }
        player.spigot().sendMessage(cb.create());
    }

    public EntityHider getEntityHider() {
        if (entityHider == null) {
            entityHider = new EntityHider(this, EntityHider.Policy.BLACKLIST);
        }
        return entityHider;
    }
}
