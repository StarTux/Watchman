package com.cavetale.watchman.lookup;

import com.cavetale.core.command.CommandWarn;
import com.cavetale.watchman.sql.SQLLog;
import com.winthier.sql.SQLTable;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class TimeLookup implements Lookup {
    private final Duration duration;

    @Override
    public SQLTable<SQLLog>.Finder accept(SQLTable<SQLLog>.Finder finder) {
        return finder.gte("time", Instant.now().minus(duration).toEpochMilli());
    }

    @Override
    public String getParameters() {
        return "t:" + format();
    }

    public static TimeLookup parse(String input) {
        Duration t = Duration.ofSeconds(0);
        int value = 0;
        for (int i = 0; i < input.length(); i += 1) {
            char c = input.charAt(i);
            if (c >= '0' && c <= '9') {
                value = value * 10 + (int) (c - '0');
                continue;
            }
            switch (c) {
            case 's': t = t.plus(Duration.ofSeconds(value)); break;
            case 'm': t = t.plus(Duration.ofMinutes(value)); break;
            case 'h': t = t.plus(Duration.ofHours(value)); break;
            case 'd': t = t.plus(Duration.ofDays(value)); break;
            default: throw new CommandWarn("Illegal time code: " + c);
            }
            value = 0;
        }
        if (value > 0) t = t.plus(Duration.ofSeconds(value));
        return new TimeLookup(t);
    }

    public String format() {
        if (duration.toSeconds() == 0L) return "0s";
        long seconds = duration.toSeconds() % 60L;
        long minutes = duration.toMinutes() % 60L;
        long hours = duration.toHours() % 24L;
        long days = duration.toDays();
        return (days > 0 ? days + "d" : "")
            + (hours > 0 ? hours + "h" : "")
            + (minutes > 0 ? minutes + "m" : "")
            + (seconds > 0 ? seconds + "s" : "");
    }
}
