package com.neoban.common.storage;

import com.neoban.common.model.IpBan;
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
import java.util.logging.Level;
import java.util.logging.Logger;

public class YamlIpBanStore implements IpBanStore {

    private static final Logger LOGGER = Logger.getLogger("NeoBan");

    private final File file;
    private final Map<String, IpBan> byIp = new LinkedHashMap<String, IpBan>();
    private int nextId = 1;

    public YamlIpBanStore(File file) {
        this.file = file;
    }

    private static String key(String ip) {
        return ip == null ? "" : ip.toLowerCase(Locale.ROOT);
    }

    @Override
    public synchronized void load() {
        byIp.clear();
        nextId = 1;
        if (!file.exists()) {
            return;
        }
        YamlConfiguration cfg = YamlIo.load(file);
        nextId = cfg.getInt("next-id", 1);
        ConfigurationSection sec = cfg.getConfigurationSection("ipbans");
        if (sec == null) {
            return;
        }
        for (String k : sec.getKeys(false)) {
            ConfigurationSection s = sec.getConfigurationSection(k);
            if (s == null) {
                continue;
            }
            IpBan b = new IpBan();
            b.setId(s.getInt("id"));
            b.setIp(s.getString("ip", ""));
            b.setIssuer(s.getString("issuer", ""));
            b.setReason(s.getString("reason", ""));
            b.setCreated(s.getLong("created"));
            b.setExpires(s.getLong("expires"));
            b.setActive(s.getBoolean("active", true));
            b.setAuto(s.getBoolean("auto", false));
            if (b.getIp() == null || b.getIp().isEmpty()) {
                continue;
            }
            byIp.put(key(b.getIp()), b);
            if (b.getId() >= nextId) {
                nextId = b.getId() + 1;
            }
        }
    }

    @Override
    public synchronized void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("next-id", nextId);
        for (Map.Entry<String, IpBan> e : byIp.entrySet()) {
            String path = "ipbans." + e.getValue().getId();
            IpBan b = e.getValue();
            cfg.set(path + ".id", b.getId());
            cfg.set(path + ".ip", b.getIp());
            cfg.set(path + ".issuer", b.getIssuer());
            cfg.set(path + ".reason", b.getReason());
            cfg.set(path + ".created", b.getCreated());
            cfg.set(path + ".expires", b.getExpires());
            cfg.set(path + ".active", b.isActive());
            cfg.set(path + ".auto", b.isAuto());
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

    private IpBan stillActive(IpBan b) {
        if (!b.isActive()) {
            return null;
        }
        if (b.isExpired(System.currentTimeMillis())) {
            b.setActive(false);
            save();
            return null;
        }
        return b;
    }

    @Override
    public synchronized IpBan findActive(String ip) {
        IpBan b = byIp.get(key(ip));
        return b == null ? null : stillActive(b);
    }

    @Override
    public synchronized IpBan findById(int id) {
        for (IpBan b : byIp.values()) {
            if (b.getId() == id) {
                return b;
            }
        }
        return null;
    }

    @Override
    public synchronized IpBan create(String ip, String issuer, String reason, long durationMs, boolean auto) {
        IpBan b = new IpBan();
        b.setId(nextId++);
        b.setIp(ip);
        b.setIssuer(issuer);
        b.setReason(reason);
        long now = System.currentTimeMillis();
        b.setCreated(now);
        b.setExpires(durationMs <= 0 ? 0L : now + durationMs);
        b.setActive(true);
        b.setAuto(auto);
        byIp.put(key(ip), b);
        save();
        return b;
    }

    @Override
    public synchronized void deactivate(IpBan ban) {
        ban.setActive(false);
        save();
    }

    @Override
    public synchronized List<IpBan> pollExpired() {
        List<IpBan> expired = new ArrayList<IpBan>();
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (IpBan b : byIp.values()) {
            if (b.isActive() && b.isExpired(now)) {
                b.setActive(false);
                expired.add(b);
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
        for (IpBan b : byIp.values()) {
            if (b.isActive() && !b.isExpired(now)) {
                count++;
            }
        }
        return count;
    }

    @Override
    public synchronized List<String> activeIps() {
        List<String> list = new ArrayList<String>();
        long now = System.currentTimeMillis();
        for (IpBan b : byIp.values()) {
            if (b.isActive() && !b.isExpired(now)) {
                list.add(b.getIp());
            }
        }
        return list;
    }

    @Override
    public synchronized Collection<IpBan> all() {
        return byIp.values();
    }
}
