package com.neoban.common.util;

import org.bukkit.ChatColor;

import java.util.HashMap;
import java.util.Map;

public final class Text {

    private Text() {
    }

    public static String color(String input) {
        if (input == null) {
            return "";
        }
        return ChatColor.translateAlternateColorCodes('&', input);
    }

    public static String apply(String template, Map<String, String> vars) {
        if (template == null) {
            return "";
        }
        String out = template;
        if (vars != null) {
            for (Map.Entry<String, String> e : vars.entrySet()) {
                String value = e.getValue() == null ? "" : e.getValue();
                out = out.replace("{" + e.getKey() + "}", value);
            }
        }
        return out;
    }

    public static Map<String, String> vars(String... kv) {
        Map<String, String> map = new HashMap<String, String>();
        if (kv != null) {
            for (int i = 0; i + 1 < kv.length; i += 2) {
                map.put(kv[i], kv[i + 1]);
            }
        }
        return map;
    }

    public static String join(String[] args, int from) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < args.length; i++) {
            if (i > from) {
                sb.append(' ');
            }
            sb.append(args[i]);
        }
        return sb.toString();
    }
}
