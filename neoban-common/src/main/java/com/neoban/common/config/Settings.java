package com.neoban.common.config;

import com.neoban.common.util.Text;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class Settings {

    public enum WorldType {
        VOID,
        CUSTOM
    }

    public enum CountScope {
        PER_PUNISHMENT,
        LIFETIME
    }

    private final int appealMax;
    private final long cooldownMs;
    private final CountScope countScope;
    private final Set<String> allowedCommands;
    private final WorldType worldType;
    private final String voidName;
    private final String customName;
    private final Double spawnX;
    private final Double spawnY;
    private final Double spawnZ;
    private final Map<String, String> messages;

    public Settings(FileConfiguration cfg) {
        this.appealMax = cfg.getInt("appeal.max-appeals", 5);
        long minutes = cfg.getLong("appeal.cooldown-minutes", 60L);
        if (minutes < 0) {
            minutes = 0;
        }
        this.cooldownMs = minutes * 60000L;
        String scope = cfg.getString("appeal.count-scope", "PER_PUNISHMENT");
        this.countScope = "LIFETIME".equalsIgnoreCase(scope) ? CountScope.LIFETIME : CountScope.PER_PUNISHMENT;

        Set<String> cmds = new HashSet<String>();
        for (String c : cfg.getStringList("appeal.allowed-commands")) {
            if (c == null) {
                continue;
            }
            String norm = c.trim().toLowerCase(Locale.ROOT);
            if (norm.startsWith("/")) {
                norm = norm.substring(1);
            }
            if (!norm.isEmpty()) {
                cmds.add(norm);
            }
        }
        this.allowedCommands = Collections.unmodifiableSet(cmds);

        String type = cfg.getString("appeal.world.type", "VOID");
        this.worldType = "CUSTOM".equalsIgnoreCase(type) ? WorldType.CUSTOM : WorldType.VOID;
        this.voidName = cfg.getString("appeal.world.void-name", "NeoBan_AppealVoid");
        this.customName = cfg.getString("appeal.world.custom-name", "appeal_world");

        ConfigurationSection spawn = cfg.getConfigurationSection("appeal.world.custom-spawn");
        if (spawn != null && spawn.contains("x") && spawn.contains("y") && spawn.contains("z")) {
            this.spawnX = spawn.getDouble("x");
            this.spawnY = spawn.getDouble("y");
            this.spawnZ = spawn.getDouble("z");
        } else {
            this.spawnX = null;
            this.spawnY = null;
            this.spawnZ = null;
        }

        Map<String, String> msgs = new HashMap<String, String>();
        ConfigurationSection m = cfg.getConfigurationSection("messages");
        if (m != null) {
            for (String key : m.getKeys(false)) {
                String value = m.getString(key);
                if (value != null) {
                    msgs.put(key, Text.color(value));
                }
            }
        }
        this.messages = Collections.unmodifiableMap(msgs);
    }

    public int appealMax() {
        return appealMax;
    }

    public long cooldownMs() {
        return cooldownMs;
    }

    public CountScope countScope() {
        return countScope;
    }

    public Set<String> allowedCommands() {
        return allowedCommands;
    }

    public WorldType worldType() {
        return worldType;
    }

    public String voidName() {
        return voidName;
    }

    public String customName() {
        return customName;
    }

    public boolean hasCustomSpawn() {
        return spawnX != null && spawnY != null && spawnZ != null;
    }

    public double spawnX() {
        return spawnX == null ? 0 : spawnX;
    }

    public double spawnY() {
        return spawnY == null ? 65 : spawnY;
    }

    public double spawnZ() {
        return spawnZ == null ? 0 : spawnZ;
    }

    public String prefix() {
        String p = messages.get("prefix");
        return p == null ? "" : p;
    }

    public String message(String key) {
        String m = messages.get(key);
        return m == null ? key : m;
    }
}
