package com.cavetale.watchman;

import com.cavetale.core.command.AbstractCommand;
import com.cavetale.core.command.CommandArgCompleter;
import com.cavetale.core.font.Unicode;
import com.cavetale.watchman.action.Action;
import com.cavetale.watchman.action.ActionType;
import com.cavetale.watchman.session.LookupSession;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.*;

public final class RewindCommand extends AbstractCommand<WatchmanPlugin> {
    private Location center;
    private Location pos1;
    private Location pos2;

    protected RewindCommand(final WatchmanPlugin plugin) {
        super(plugin, "rewind");
    }

    @Override
    protected void onEnable() {
        rootNode.addChild("start").arguments("<duration> [flags]")
            .description("Start a rewind")
            .completers(CommandArgCompleter.integer(i -> i > 1))
            .playerCaller(this::start);
        rootNode.addChild("pos").arguments("<1|2|center|reset>")
            .description("Set or reset positions")
            .completers(CommandArgCompleter.list("1", "2", "center", "reset"),
                        CommandArgCompleter.enumLowerList(RewindTask.Flag.class))
            .playerCaller(this::pos);
        rootNode.addChild("flags").denyTabCompletion()
            .description("Explain all flags")
            .playerCaller(this::flags);
    }

    protected boolean start(Player player, String[] args) {
        final int duration;
        if (args.length >= 1) {
            try {
                duration = Integer.parseInt(args[0]);
            } catch (NumberFormatException nfe) {
                player.sendMessage(text("Bad duration (seconds): " + args[0], RED));
                return true;
            }
        } else {
            duration = 10;
        }
        String worldName = player.getWorld().getName();
        Set<RewindTask.Flag> flags = EnumSet.noneOf(RewindTask.Flag.class);
        for (int i = 1; i < args.length; i += 1) {
            String arg = args[i];
            RewindTask.Flag flag;
            try {
                flag = RewindTask.Flag.valueOf(arg.toUpperCase().replace("-", "_"));
            } catch (IllegalArgumentException iae) {
                player.sendMessage(text("Invalid flag: " + arg, RED));
                return true;
            }
            flags.add(flag);
        }
        plugin.sessions.get(player.getUniqueId(), session -> {
                if (session == null || session.getActions().isEmpty()) {
                    player.sendMessage(text("Make a lookup first", RED));
                    return;
                }
                Cuboid cuboid = WorldEdit.getSelection(player);
                if (cuboid == null) cuboid = Cuboid.ZERO;
                rewindCallback(player, session, duration, cuboid, flags);
                player.sendMessage("Preparing rewind of " + cuboid + " within " + duration + "s...");
            });
        return true;
    }

    protected void rewindCallback(Player player, LookupSession session, int duration, Cuboid cuboid, Set<RewindTask.Flag> flags) {
        Location loc1 = pos1 != null && pos1.getWorld().equals(player.getWorld()) ? pos1 : null;
        Location loc2 = pos2 != null && pos2.getWorld().equals(player.getWorld()) ? pos2 : null;
        Location loc3 = center != null && center.getWorld().equals(player.getWorld()) ? center : null;
        int durationInTicks = duration * 20;
        List<Action> actions = new ArrayList<>(session.getActions());
        actions.removeIf(a -> a.getActionType().category != ActionType.Category.BLOCK);
        int blocksPerTick = Math.max(1, actions.size() / durationInTicks);
        RewindTask task = new RewindTask(plugin, player, actions, 100L, blocksPerTick, cuboid, flags, loc1, loc2, loc3);
        task.start();
    }

    protected boolean pos(Player player, String[] args) {
        if (args.length != 1) return false;
        String arg = args[0];
        switch (arg) {
        case "1":
            pos1 = player.getLocation();
            player.sendMessage("Start position saved");
            return true;
        case "2":
            pos2 = player.getLocation();
            player.sendMessage("End position saved");
            return true;
        case "center":
            center = player.getLocation();
            player.sendMessage("Center position saved");
            return true;
        case "reset":
            pos1 = null;
            pos2 = null;
            center = null;
            player.sendMessage("Positions reset");
            return true;
        default: return false;
        }
    }

    protected boolean flags(Player player, String[] args) {
        if (args.length != 0) return false;
        for (RewindTask.Flag flag : RewindTask.Flag.values()) {
            player.sendMessage(Component.join(JoinConfiguration.noSeparators(),
                                              Component.text(flag.name().toLowerCase(), NamedTextColor.GOLD),
                                              Component.text(Unicode.EMDASH.string, NamedTextColor.DARK_GRAY),
                                              Component.text(flag.description, NamedTextColor.GRAY)));
        }
        return true;
    }
}
