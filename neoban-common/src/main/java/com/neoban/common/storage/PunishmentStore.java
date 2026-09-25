package com.neoban.common.storage;

import com.neoban.common.model.Punishment;
import com.neoban.common.model.PunishmentType;
import com.neoban.common.util.YamlIo;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PunishmentStore {

    private static final Logger LOGGER = Logger.getLogger("NeoBan");

    private final File file;
    private final PunishmentType type;
    private final String root;
    private final Map<String, Punishment> byKey = new LinkedHashMap<String, Punishment>();
    private int nextId = 1;

    public PunishmentStore(File file, PunishmentType type) {
        this.file = file;
        this.type = type;
        this.root = type == PunishmentType.BAN ? "bans" : "mutes";
    }

    public static String nameKey(String name) {
        return "name-" + name.toLowerCase(Locale.ROOT).replace('.', '_');
    }

    public static String uuidKey(UUID uuid) {
        return "uuid-" + uuid.toString();
    }

    public void load() {
        byKey.clear();
        nextId = 1;
        if (!file.exists()) {
            return;
        }
        YamlConfiguration cfg = YamlIo.load(file);
        ConfigurationSection sec = cfg.getConfigurationSection(root);
        if (sec == null) {
            return;
        }
        for (String key : sec.getKeys(false)) {
            ConfigurationSection s = sec.getConfigurationSection(key);
            if (s == null) {
                continue;
            }
            Punishment p = new Punishment(type);
            p.setId(s.getInt("id"));
            p.setName(s.getString("name", ""));
            String u = s.getString("uuid", "");
            if (u != null && !u.isEmpty()) {
                try {
                    p.setUuid(UUID.fromString(u));
                } catch (IllegalArgumentException ignored) {
                }
            }
            p.setIssuer(s.getString("issuer", ""));
            p.setReason(s.getString("reason", ""));
            p.setCreated(s.getLong("created"));
            p.setExpires(s.getLong("expires"));
            p.setActive(s.getBoolean("active", true));
            byKey.put(key, p);
            if (p.getId() >= nextId) {
                nextId = p.getId() + 1;
            }
        }
    }

    public void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("next-id", nextId);
        for (Map.Entry<String, Punishment> e : byKey.entrySet()) {
            String path = root + "." + e.getKey();
            Punishment p = e.getValue();
            cfg.set(path + ".id", p.getId());
            cfg.set(path + ".name", p.getName());
            cfg.set(path + ".uuid", p.getUuid() == null ? "" : p.getUuid().toString());
            cfg.set(path + ".issuer", p.getIssuer());
            cfg.set(path + ".reason", p.getReason());
            cfg.set(path + ".created", p.getCreated());
            cfg.set(path + ".expires", p.getExpires());
            cfg.set(path + ".active", p.isActive());
        }
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            YamlIo.save(cfg, file);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to save " + file.getName(), e);
        }
    }

    private boolean stillActive(Punishment p) {
        if (!p.isActive()) {
            return false;
        }
        if (p.isExpired(System.currentTimeMillis())) {
            p.setActive(false);
            save();
            return false;
        }
        return true;
    }

    public Punishment findActive(UUID uuid, String name) {
        Punishment result = null;
        if (uuid != null) {
            Punishment p = byKey.get(uuidKey(uuid));
            if (p != null && !stillActive(p)) {
                p = null;
            }
            result = p;
        }
        if (result == null && name != null && !name.isEmpty()) {
            Punishment p = byKey.get(nameKey(name));
            if (p != null && stillActive(p)) {
                if (uuid != null && p.getUuid() == null) {
                    migrateToUuid(name, uuid);
                }
                result = p;
            }
        }
        return result;
    }

    public Punishment findById(int id) {
        for (Punishment p : byKey.values()) {
            if (p.getId() == id) {
                return p;
            }
        }
        return null;
    }

    public UUID findUuidByName(String name) {
        if (name == null) {
            return null;
        }
        Punishment p = byKey.get(nameKey(name));
        return p == null ? null : p.getUuid();
    }

    public Punishment create(UUID uuid, String name, String issuer, String reason, long durationMs) {
        Punishment p = new Punishment(type);
        p.setId(nextId++);
        p.setUuid(uuid);
        p.setName(name);
        p.setIssuer(issuer);
        p.setReason(reason);
        long now = System.currentTimeMillis();
        p.setCreated(now);
        p.setExpires(durationMs <= 0 ? 0L : now + durationMs);
        p.setActive(true);
        String key = uuid != null ? uuidKey(uuid) : nameKey(name);
        byKey.put(key, p);
        save();
        return p;
    }

    public void deactivate(Punishment p) {
        p.setActive(false);
        save();
    }

    public void migrateToUuid(String name, UUID uuid) {
        if (name == null || uuid == null) {
            return;
        }
        String oldKey = nameKey(name);
        Punishment p = byKey.get(oldKey);
        if (p == null) {
            return;
        }
        String newKey = uuidKey(uuid);
        if (byKey.containsKey(newKey)) {
            return;
        }
        byKey.remove(oldKey);
        p.setUuid(uuid);
        byKey.put(newKey, p);
        save();
    }

    public List<Punishment> pollExpired() {
        List<Punishment> expired = new ArrayList<Punishment>();
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (Punishment p : byKey.values()) {
            if (p.isActive() && p.isExpired(now)) {
                p.setActive(false);
                expired.add(p);
                changed = true;
            }
        }
        if (changed) {
            save();
        }
        return expired;
    }

    public int activeCount() {
        int count = 0;
        long now = System.currentTimeMillis();
        for (Punishment p : byKey.values()) {
            if (p.isActive() && !p.isExpired(now)) {
                count++;
            }
        }
        return count;
    }

    public Collection<Punishment> all() {
        return byKey.values();
    }
}
