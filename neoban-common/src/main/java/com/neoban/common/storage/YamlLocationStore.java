package com.neoban.common.storage;

import com.neoban.common.util.YamlIo;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class YamlLocationStore implements LocationStore {

    private static final Logger LOGGER = Logger.getLogger("NeoBan");

    private static class Entry {
        String world;
        double x;
        double y;
        double z;
        float yaw;
        float pitch;
    }

    private final File file;
    private final Map<UUID, Entry> entries = new HashMap<UUID, Entry>();

    public YamlLocationStore(File file) {
        this.file = file;
    }

    @Override
    public void load() {
        entries.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration cfg = YamlIo.load(file);
        ConfigurationSection sec = cfg.getConfigurationSection("locations");
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
            e.world = s.getString("world", "");
            e.x = s.getDouble("x");
            e.y = s.getDouble("y");
            e.z = s.getDouble("z");
            e.yaw = (float) s.getDouble("yaw");
            e.pitch = (float) s.getDouble("pitch");
            entries.put(uuid, e);
        }
    }

    @Override
    public void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<UUID, Entry> me : entries.entrySet()) {
            String path = "locations." + me.getKey();
            Entry e = me.getValue();
            cfg.set(path + ".world", e.world);
            cfg.set(path + ".x", e.x);
            cfg.set(path + ".y", e.y);
            cfg.set(path + ".z", e.z);
            cfg.set(path + ".yaw", e.yaw);
            cfg.set(path + ".pitch", e.pitch);
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
    public void save(UUID uuid, Location loc) {
        Entry e = new Entry();
        e.world = loc.getWorld() == null ? "" : loc.getWorld().getName();
        e.x = loc.getX();
        e.y = loc.getY();
        e.z = loc.getZ();
        e.yaw = loc.getYaw();
        e.pitch = loc.getPitch();
        entries.put(uuid, e);
        save();
    }

    @Override
    public Location get(UUID uuid) {
        Entry e = entries.get(uuid);
        if (e == null || e.world == null || e.world.isEmpty()) {
            return null;
        }
        World w = Bukkit.getWorld(e.world);
        if (w == null) {
            return null;
        }
        return new Location(w, e.x, e.y, e.z, e.yaw, e.pitch);
    }

    @Override
    public void clear(UUID uuid) {
        if (entries.remove(uuid) != null) {
            save();
        }
    }
}
