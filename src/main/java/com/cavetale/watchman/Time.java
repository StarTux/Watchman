package com.cavetale.watchman;

public final class Time {
    private Time() { }

    public static long parseSeconds(String in) throws IllegalArgumentException {
        long result = 0L;
        int start = 0;
        for (int i = 0; i < in.length(); i += 1) {
            char c = in.charAt(i);
            if (c >= '0' && c <= '9') continue;
            final long mul; // time multiplier
            switch (c) {
            case 's': mul = 1L; break;
            case 'm': mul = 60L; break;
            case 'h': mul = 60L * 60L; break;
            case 'd': mul = 60L * 60L * 24L; break;
            default: throw new IllegalArgumentException("Unknown time format: " + c);
            }
            String arg = in.substring(start, i);
            start = i + 1;
            long amount = Long.parseLong(arg); // throws NFE
            result += mul * amount;
        }
        return result;
    }

    public static String formatSeconds(long s) {
        long m = s / 60L;
        long h = m / 60L;
        long d = h / 24L;
        return d + "d"
            + (h % 24L) + "h"
            + (m % 60L) + "m"
            + (s % 60L) + "s";
    }
}
