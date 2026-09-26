package com.neoban.common.storage;

import com.neoban.common.util.YamlIo;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class YamlPlayerIpStore implements PlayerIpStore {

    private static final Logger LOGGER = Logger.getLogger("NeoBan");

    private static class Entry {
        String name;
        String ip;
        long lastSeen;
    }

    private final File file;
    private final Map<UUID, Entry> byUuid = new LinkedHashMap<UUID, Entry>();

    public YamlPlayerIpStore(File file) {
        this.file = file;
    }

    @Override
    public void load() {
        byUuid.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration cfg = YamlIo.load(file);
        ConfigurationSection sec = cfg.getConfigurationSection("players");
        if (sec == null) {
            return;
        }
        for (String key : sec.getKeys(false)) {
            ConfigurationSection s = sec.getConfigurationSection(key);
            if (s == null) {
                continue;
            }
            UUID uuid;
            try {
                uuid = UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                continue;
            }
            Entry e = new Entry();
            e.name = s.getString("name", "");
            e.ip = s.getString("ip", "");
            e.lastSeen = s.getLong("last-seen");
            if (e.ip == null || e.ip.isEmpty()) {
                continue;
            }
            byUuid.put(uuid, e);
        }
    }

    @Override
    public void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<UUID, Entry> me : byUuid.entrySet()) {
            String path = "players." + me.getKey();
            Entry e = me.getValue();
            cfg.set(path + ".name", e.name);
            cfg.set(path + ".ip", e.ip);
            cfg.set(path + ".last-seen", e.lastSeen);
        }
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            YamlIo.save(cfg, file);
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "Failed to save " + file.getName(), ex);
        }
    }

    @Override
    public void record(UUID uuid, String name, String ip) {
        if (uuid == null || ip == null || ip.isEmpty()) {
            return;
        }
        Entry e = new Entry();
        e.name = name == null ? "" : name;
        e.ip = ip;
        e.lastSeen = System.currentTimeMillis();
        byUuid.put(uuid, e);
        save();
    }

    @Override
    public String findByUuid(UUID uuid) {
        Entry e = uuid == null ? null : byUuid.get(uuid);
        return e == null ? null : e.ip;
    }

    @Override
    public String findByName(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        String n = name.toLowerCase(Locale.ROOT);
        for (Entry e : byUuid.values()) {
            if (e.name != null && e.name.toLowerCase(Locale.ROOT).equals(n)) {
                return e.ip;
            }
        }
        return null;
    }
}
