package com.neoban.common.util;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Locale;

public final class Ips {

    private Ips() {
    }

    public static String of(InetAddress addr) {
        return addr == null ? null : addr.getHostAddress();
    }

    public static String of(InetSocketAddress addr) {
        return addr == null ? null : of(addr.getAddress());
    }

    public static boolean looksLike(String input) {
        if (input == null || input.isEmpty()) {
            return false;
        }
        return input.indexOf(':') >= 0 || isIpv4Shaped(input);
    }

    public static String normalize(String input) {
        if (input == null) {
            return null;
        }
        String s = input.trim();
        if (s.isEmpty()) {
            return null;
        }
        if (s.indexOf(':') < 0 && !isIpv4Shaped(s)) {
            return null;
        }
        try {
            return InetAddress.getByName(s).getHostAddress();
        } catch (UnknownHostException e) {
            return null;
        }
    }

    private static boolean isIpv4Shaped(String s) {
        int dots = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '.') {
                dots++;
            } else if (c < '0' || c > '9') {
                return false;
            }
        }
        return dots == 3;
    }

    public static String canonicalKey(String ip) {
        String n = normalize(ip);
        return n == null ? (ip == null ? "" : ip.toLowerCase(Locale.ROOT)) : n;
    }
}
