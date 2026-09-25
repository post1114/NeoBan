package com.neoban.common.util;

import java.util.Locale;

public final class Durations {

    private Durations() {
    }

    public static long parse(String input) {
        if (input == null) {
            return -1;
        }
        String s = input.toLowerCase(Locale.ROOT).trim();
        if (s.isEmpty()) {
            return -1;
        }
        long total = 0;
        long num = -1;
        boolean any = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') {
                num = (num < 0 ? 0 : num * 10) + (c - '0');
                continue;
            }
            if (num < 0) {
                return -1;
            }
            long mult;
            switch (c) {
                case 's':
                    mult = 1000L;
                    break;
                case 'm':
                    mult = 60000L;
                    break;
                case 'h':
                    mult = 3600000L;
                    break;
                case 'd':
                    mult = 86400000L;
                    break;
                case 'w':
                    mult = 604800000L;
                    break;
                default:
                    return -1;
            }
            total += num * mult;
            num = -1;
            any = true;
        }
        if (num >= 0) {
            return -1;
        }
        return any ? total : -1;
    }

    public static String format(long millis) {
        long s = millis / 1000;
        if (s <= 0) {
            return "0s";
        }
        long w = s / 604800;
        s %= 604800;
        long d = s / 86400;
        s %= 86400;
        long h = s / 3600;
        s %= 3600;
        long m = s / 60;
        s %= 60;
        StringBuilder sb = new StringBuilder();
        if (w > 0) {
            sb.append(w).append("w");
        }
        if (d > 0) {
            sb.append(d).append("d");
        }
        if (h > 0) {
            sb.append(h).append("h");
        }
        if (m > 0) {
            sb.append(m).append("m");
        }
        if (s > 0) {
            sb.append(s).append("s");
        }
        return sb.toString();
    }
}
