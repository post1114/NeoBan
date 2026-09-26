package com.neoban.common.config;

import com.neoban.common.util.Durations;
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

    public enum StorageType {
        YAML,
        MYSQL
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
    private final StorageType storageType;
    private final String mysqlHost;
    private final int mysqlPort;
    private final String mysqlDatabase;
    private final String mysqlUser;
    private final String mysqlPassword;
    private final String mysqlTablePrefix;
    private final String mysqlProperties;
    private final boolean ipAutoEnabled;
    private final int ipAutoThreshold;
    private final long ipAutoDurationMs;
    private final String ipAutoReason;
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

        String storage = cfg.getString("storage.type", "YAML");
        this.storageType = "MYSQL".equalsIgnoreCase(storage) ? StorageType.MYSQL : StorageType.YAML;
        this.mysqlHost = cfg.getString("storage.mysql.host", "localhost");
        this.mysqlPort = cfg.getInt("storage.mysql.port", 3306);
        this.mysqlDatabase = cfg.getString("storage.mysql.database", "neoban");
        this.mysqlUser = cfg.getString("storage.mysql.user", "root");
        this.mysqlPassword = cfg.getString("storage.mysql.password", "");
        String prefix = cfg.getString("storage.mysql.table-prefix", "neoban_");
        if (prefix == null || prefix.isEmpty()) {
            prefix = "neoban_";
        }
        this.mysqlTablePrefix = prefix;
        this.mysqlProperties = cfg.getString("storage.mysql.properties",
                "useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8&serverTimezone=UTC");

        this.ipAutoEnabled = cfg.getBoolean("ipban.auto.enabled", true);
        int threshold = cfg.getInt("ipban.auto.min-banned-players", 7);
        this.ipAutoThreshold = threshold < 1 ? 7 : threshold;
        long autoMs = Durations.parse(cfg.getString("ipban.auto.duration", "2d"));
        if (autoMs <= 0) {
            autoMs = 2L * 86400000L;
        }
        this.ipAutoDurationMs = autoMs;
        String autoReason = cfg.getString("ipban.auto.reason",
                "Automatic IP ban: too many banned players on this IP");
        this.ipAutoReason = autoReason == null || autoReason.isEmpty()
                ? "Automatic IP ban: too many banned players on this IP" : autoReason;

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

    public StorageType storageType() {
        return storageType;
    }

    public String mysqlHost() {
        return mysqlHost;
    }

    public int mysqlPort() {
        return mysqlPort;
    }

    public String mysqlDatabase() {
        return mysqlDatabase;
    }

    public String mysqlUser() {
        return mysqlUser;
    }

    public String mysqlPassword() {
        return mysqlPassword;
    }

    public String mysqlTablePrefix() {
        return mysqlTablePrefix;
    }

    public String mysqlProperties() {
        return mysqlProperties;
    }

    public boolean ipAutoEnabled() {
        return ipAutoEnabled;
    }

    public int ipAutoThreshold() {
        return ipAutoThreshold;
    }

    public long ipAutoDurationMs() {
        return ipAutoDurationMs;
    }

    public String ipAutoReason() {
        return ipAutoReason;
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
