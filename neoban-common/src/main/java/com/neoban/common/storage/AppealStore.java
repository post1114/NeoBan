package com.neoban.common.storage;

import com.neoban.common.model.Appeal;
import com.neoban.common.model.AppealStatus;
import com.neoban.common.model.PunishmentType;
import com.neoban.common.util.YamlIo;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AppealStore {

    private static final Logger LOGGER = Logger.getLogger("NeoBan");

    private static class Counter {
        long lifetime;
        long lastAppeal;
        final Map<String, Integer> byPunishment = new HashMap<String, Integer>();
    }

    private final File file;
    private final Map<Integer, Appeal> appeals = new LinkedHashMap<Integer, Appeal>();
    private final Map<String, Counter> counters = new HashMap<String, Counter>();
    private int nextId = 1;

    public AppealStore(File file) {
        this.file = file;
    }

    public void load() {
        appeals.clear();
        counters.clear();
        nextId = 1;
        if (!file.exists()) {
            return;
        }
        YamlConfiguration cfg = YamlIo.load(file);
        nextId = cfg.getInt("next-id", 1);
        ConfigurationSection sec = cfg.getConfigurationSection("appeals");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                ConfigurationSection s = sec.getConfigurationSection(key);
                if (s == null) {
                    continue;
                }
                Appeal a = new Appeal();
                a.setId(s.getInt("id"));
                String type = s.getString("type", "BAN");
                a.setPunishmentType("MUTE".equalsIgnoreCase(type) ? PunishmentType.MUTE : PunishmentType.BAN);
                a.setPunishmentId(s.getInt("punishment-id"));
                String u = s.getString("uuid", "");
                if (u != null && !u.isEmpty()) {
                    try {
                        a.setUuid(UUID.fromString(u));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
                a.setName(s.getString("name", ""));
                a.setReason(s.getString("reason", ""));
                a.setCreated(s.getLong("created"));
                String status = s.getString("status", "PENDING");
                try {
                    a.setStatus(AppealStatus.valueOf(status));
                } catch (IllegalArgumentException e) {
                    a.setStatus(AppealStatus.PENDING);
                }
                a.setDecidedBy(s.getString("decided-by", ""));
                a.setDecidedAt(s.getLong("decided-at"));
                a.setNote(s.getString("note", ""));
                a.setDelivered(s.getBoolean("delivered", false));
                appeals.put(a.getId(), a);
                if (a.getId() >= nextId) {
                    nextId = a.getId() + 1;
                }
            }
        }
        ConfigurationSection csec = cfg.getConfigurationSection("counters");
        if (csec != null) {
            for (String key : csec.getKeys(false)) {
                ConfigurationSection s = csec.getConfigurationSection(key);
                if (s == null) {
                    continue;
                }
                Counter c = new Counter();
                c.lifetime = s.getLong("lifetime");
                c.lastAppeal = s.getLong("last-appeal");
                ConfigurationSection bp = s.getConfigurationSection("by-punishment");
                if (bp != null) {
                    for (String pk : bp.getKeys(false)) {
                        c.byPunishment.put(pk, bp.getInt(pk));
                    }
                }
                counters.put(key, c);
            }
        }
    }

    public void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("next-id", nextId);
        for (Appeal a : appeals.values()) {
            String path = "appeals." + a.getId();
            cfg.set(path + ".id", a.getId());
            cfg.set(path + ".type", a.getPunishmentType().name());
            cfg.set(path + ".punishment-id", a.getPunishmentId());
            cfg.set(path + ".uuid", a.getUuid() == null ? "" : a.getUuid().toString());
            cfg.set(path + ".name", a.getName());
            cfg.set(path + ".reason", a.getReason());
            cfg.set(path + ".created", a.getCreated());
            cfg.set(path + ".status", a.getStatus().name());
            cfg.set(path + ".decided-by", a.getDecidedBy());
            cfg.set(path + ".decided-at", a.getDecidedAt());
            cfg.set(path + ".note", a.getNote());
            cfg.set(path + ".delivered", a.isDelivered());
        }
        for (Map.Entry<String, Counter> e : counters.entrySet()) {
            String path = "counters." + e.getKey();
            Counter c = e.getValue();
            cfg.set(path + ".lifetime", c.lifetime);
            cfg.set(path + ".last-appeal", c.lastAppeal);
            for (Map.Entry<String, Integer> bp : c.byPunishment.entrySet()) {
                cfg.set(path + ".by-punishment." + bp.getKey(), bp.getValue());
            }
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

    public Appeal get(int id) {
        return appeals.get(id);
    }

    public Collection<Appeal> all() {
        return appeals.values();
    }

    public List<Appeal> pending() {
        List<Appeal> list = new ArrayList<Appeal>();
        for (Appeal a : appeals.values()) {
            if (a.getStatus() == AppealStatus.PENDING) {
                list.add(a);
            }
        }
        return list;
    }

    public Appeal add(Appeal a) {
        a.setId(nextId++);
        appeals.put(a.getId(), a);
        save();
        return a;
    }

    private Counter counter(String key) {
        Counter c = counters.get(key);
        if (c == null) {
            c = new Counter();
            counters.put(key, c);
        }
        return c;
    }

    private static String punishmentKey(PunishmentType type, int punishmentId) {
        return type.name() + "-" + punishmentId;
    }

    public int countFor(String counterKey, PunishmentType type, int punishmentId, boolean lifetimeScope) {
        Counter c = counters.get(counterKey);
        if (c == null) {
            return 0;
        }
        if (lifetimeScope) {
            return (int) c.lifetime;
        }
        Integer v = c.byPunishment.get(punishmentKey(type, punishmentId));
        return v == null ? 0 : v;
    }

    public void bump(String counterKey, PunishmentType type, int punishmentId) {
        Counter c = counter(counterKey);
        c.lifetime++;
        c.lastAppeal = System.currentTimeMillis();
        String pk = punishmentKey(type, punishmentId);
        Integer v = c.byPunishment.get(pk);
        c.byPunishment.put(pk, (v == null ? 0 : v) + 1);
        save();
    }

    public long lastAppeal(String counterKey) {
        Counter c = counters.get(counterKey);
        return c == null ? 0L : c.lastAppeal;
    }

    public void reset(String counterKey) {
        counters.remove(counterKey);
        save();
    }

    public int pendingCount() {
        return pending().size();
    }
}
