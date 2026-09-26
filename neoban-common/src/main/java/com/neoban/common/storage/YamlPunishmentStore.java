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

public class YamlPunishmentStore implements PunishmentStore {

    private static final Logger LOGGER = Logger.getLogger("NeoBan");

    private final File file;
    private final PunishmentType type;
    private final String root;
    private final Map<String, Punishment> byKey = new LinkedHashMap<String, Punishment>();
    private int nextId = 1;

    public YamlPunishmentStore(File file, PunishmentType type) {
        this.file = file;
        this.type = type;
        this.root = type == PunishmentType.BAN ? "bans" : "mutes";
    }

    @Override
    public synchronized void load() {
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
            String ip = s.getString("ip", "");
            p.setIp(ip == null || ip.isEmpty() ? null : ip);
            byKey.put(key, p);
            if (p.getId() >= nextId) {
                nextId = p.getId() + 1;
            }
        }
    }

    @Override
    public synchronized void save() {
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
            cfg.set(path + ".ip", p.getIp());
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

    @Override
    public synchronized Punishment findActive(UUID uuid, String name) {
        Punishment result = null;
        if (uuid != null) {
            Punishment p = byKey.get(PunishmentStore.uuidKey(uuid));
            if (p != null && !stillActive(p)) {
                p = null;
            }
            result = p;
        }
        if (result == null && name != null && !name.isEmpty()) {
            Punishment p = byKey.get(PunishmentStore.nameKey(name));
            if (p != null && stillActive(p)) {
                if (uuid != null && p.getUuid() == null) {
                    migrateToUuid(name, uuid);
                }
                result = p;
            }
        }
        return result;
    }

    @Override
    public synchronized Punishment findById(int id) {
        for (Punishment p : byKey.values()) {
            if (p.getId() == id) {
                return p;
            }
        }
        return null;
    }

    @Override
    public synchronized UUID findUuidByName(String name) {
        if (name == null) {
            return null;
        }
        Punishment p = byKey.get(PunishmentStore.nameKey(name));
        return p == null ? null : p.getUuid();
    }

    @Override
    public synchronized Punishment create(UUID uuid, String name, String issuer, String reason, long durationMs, String ip) {
        Punishment p = new Punishment(type);
        p.setId(nextId++);
        p.setUuid(uuid);
        p.setName(name);
        p.setIssuer(issuer);
        p.setReason(reason);
        p.setIp(ip);
        long now = System.currentTimeMillis();
        p.setCreated(now);
        p.setExpires(durationMs <= 0 ? 0L : now + durationMs);
        p.setActive(true);
        String key = uuid != null ? PunishmentStore.uuidKey(uuid) : PunishmentStore.nameKey(name);
        byKey.put(key, p);
        save();
        return p;
    }

    @Override
    public synchronized void deactivate(Punishment p) {
        p.setActive(false);
        save();
    }

    @Override
    public synchronized void migrateToUuid(String name, UUID uuid) {
        if (name == null || uuid == null) {
            return;
        }
        String oldKey = PunishmentStore.nameKey(name);
        Punishment p = byKey.get(oldKey);
        if (p == null) {
            return;
        }
        String newKey = PunishmentStore.uuidKey(uuid);
        if (byKey.containsKey(newKey)) {
            return;
        }
        byKey.remove(oldKey);
        p.setUuid(uuid);
        byKey.put(newKey, p);
        save();
    }

    @Override
    public synchronized void updateIp(Punishment p, String ip) {
        p.setIp(ip);
        save();
    }

    @Override
    public synchronized List<Punishment> pollExpired() {
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

    @Override
    public synchronized int activeCount() {
        int count = 0;
        long now = System.currentTimeMillis();
        for (Punishment p : byKey.values()) {
            if (p.isActive() && !p.isExpired(now)) {
                count++;
            }
        }
        return count;
    }

    @Override
    public synchronized int countActiveByIp(String ip) {
        if (ip == null || ip.isEmpty()) {
            return 0;
        }
        int count = 0;
        long now = System.currentTimeMillis();
        for (Punishment p : byKey.values()) {
            if (p.isActive() && !p.isExpired(now) && ip.equals(p.getIp())) {
                count++;
            }
        }
        return count;
    }

    @Override
    public synchronized Collection<Punishment> all() {
        return byKey.values();
    }
}
