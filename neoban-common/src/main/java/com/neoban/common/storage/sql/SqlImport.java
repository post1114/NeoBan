package com.neoban.common.storage.sql;

import com.neoban.common.util.YamlIo;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.sql.SQLException;
import java.util.UUID;
import java.util.logging.Logger;

public final class SqlImport {

    private SqlImport() {
    }

    public static void importYamlIfEmpty(SqlDb db, String prefix, File dataDir, Logger logger) throws SQLException {
        if (!SqlSchema.isEmpty(db, prefix)) {
            return;
        }
        boolean prev = db.connection().getAutoCommit();
        int total = 0;
        db.connection().setAutoCommit(false);
        try {
            total += punishments(db, prefix, new File(dataDir, "bans.yml"), "bans", "BAN");
            total += punishments(db, prefix, new File(dataDir, "mutes.yml"), "mutes", "MUTE");
            total += appeals(db, prefix, new File(dataDir, "appeals.yml"));
            total += locations(db, prefix, new File(dataDir, "locations.yml"));
            total += ipBans(db, prefix, new File(dataDir, "ipbans.yml"));
            total += playerIps(db, prefix, new File(dataDir, "player-ips.yml"));
            db.connection().commit();
        } catch (SQLException e) {
            try {
                db.connection().rollback();
            } catch (SQLException ignored) {
            }
            throw e;
        } finally {
            try {
                db.connection().setAutoCommit(prev);
            } catch (SQLException ignored) {
            }
        }
        if (total > 0) {
            logger.info("Imported " + total + " records from YAML files into MySQL.");
        } else {
            logger.info("MySQL storage ready (no YAML data found to import).");
        }
    }

    private static int punishments(SqlDb db, String prefix, File file, String root, String type)
            throws SQLException {
        if (!file.exists() || file.length() == 0) {
            return 0;
        }
        YamlConfiguration cfg = YamlIo.load(file);
        ConfigurationSection sec = cfg.getConfigurationSection(root);
        if (sec == null) {
            return 0;
        }
        int n = 0;
        for (String key : sec.getKeys(false)) {
            ConfigurationSection s = sec.getConfigurationSection(key);
            if (s == null) {
                continue;
            }
            String uuid = emptyToNull(s.getString("uuid", ""));
            String ip = emptyToNull(s.getString("ip", ""));
            db.update("INSERT INTO " + prefix + "punishments "
                    + "(id, type, uuid, name, issuer, reason, created_ms, expires_ms, active, ip) "
                    + "VALUES (?,?,?,?,?,?,?,?,?,?)",
                    s.getInt("id"), type, uuid, s.getString("name", ""),
                    s.getString("issuer", ""), s.getString("reason", ""),
                    s.getLong("created"), s.getLong("expires"),
                    s.getBoolean("active", true) ? 1 : 0, ip);
            n++;
        }
        return n;
    }

    private static int appeals(SqlDb db, String prefix, File file) throws SQLException {
        if (!file.exists() || file.length() == 0) {
            return 0;
        }
        YamlConfiguration cfg = YamlIo.load(file);
        int n = 0;
        ConfigurationSection sec = cfg.getConfigurationSection("appeals");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                ConfigurationSection s = sec.getConfigurationSection(key);
                if (s == null) {
                    continue;
                }
                String type = s.getString("type", "BAN");
                db.update("INSERT INTO " + prefix + "appeals "
                        + "(id, ptype, pid, uuid, name, reason, created_ms, status, decided_by, decided_at_ms, note, delivered) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                        s.getInt("id"), "MUTE".equalsIgnoreCase(type) ? "MUTE" : "BAN",
                        s.getInt("punishment-id"), emptyToNull(s.getString("uuid", "")),
                        s.getString("name", ""), s.getString("reason", ""),
                        s.getLong("created"), s.getString("status", "PENDING"),
                        s.getString("decided-by", ""), s.getLong("decided-at"),
                        s.getString("note", ""), s.getBoolean("delivered", false) ? 1 : 0);
                n++;
            }
        }
        ConfigurationSection csec = cfg.getConfigurationSection("counters");
        if (csec != null) {
            for (String key : csec.getKeys(false)) {
                ConfigurationSection s = csec.getConfigurationSection(key);
                if (s == null) {
                    continue;
                }
                db.update("INSERT INTO " + prefix + "appeal_counters "
                                + "(counter_key, lifetime, last_appeal_ms) VALUES (?,?,?)",
                        key, (int) s.getLong("lifetime"), s.getLong("last-appeal"));
                n++;
                ConfigurationSection bp = s.getConfigurationSection("by-punishment");
                if (bp != null) {
                    for (String pk : bp.getKeys(false)) {
                        int dash = pk.indexOf('-');
                        if (dash <= 0) {
                            continue;
                        }
                        String ptype = pk.substring(0, dash);
                        int pid;
                        try {
                            pid = Integer.parseInt(pk.substring(dash + 1));
                        } catch (NumberFormatException e) {
                            continue;
                        }
                        db.update("INSERT INTO " + prefix + "appeal_counts "
                                        + "(counter_key, ptype, pid, cnt) VALUES (?,?,?,?)",
                                key, ptype, pid, bp.getInt(pk));
                        n++;
                    }
                }
            }
        }
        return n;
    }

    private static int locations(SqlDb db, String prefix, File file) throws SQLException {
        if (!file.exists() || file.length() == 0) {
            return 0;
        }
        YamlConfiguration cfg = YamlIo.load(file);
        ConfigurationSection sec = cfg.getConfigurationSection("locations");
        if (sec == null) {
            return 0;
        }
        int n = 0;
        for (String key : sec.getKeys(false)) {
            ConfigurationSection s = sec.getConfigurationSection(key);
            if (s == null) {
                continue;
            }
            try {
                UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                continue;
            }
            db.update("INSERT INTO " + prefix + "locations "
                            + "(uuid, world, x, y, z, yaw, pitch) VALUES (?,?,?,?,?,?,?)",
                    key, s.getString("world", ""), s.getDouble("x"), s.getDouble("y"),
                    s.getDouble("z"), (float) s.getDouble("yaw"), (float) s.getDouble("pitch"));
            n++;
        }
        return n;
    }

    private static int ipBans(SqlDb db, String prefix, File file) throws SQLException {
        if (!file.exists() || file.length() == 0) {
            return 0;
        }
        YamlConfiguration cfg = YamlIo.load(file);
        ConfigurationSection sec = cfg.getConfigurationSection("ipbans");
        if (sec == null) {
            return 0;
        }
        int n = 0;
        for (String key : sec.getKeys(false)) {
            ConfigurationSection s = sec.getConfigurationSection(key);
            if (s == null) {
                continue;
            }
            String ip = emptyToNull(s.getString("ip", ""));
            if (ip == null) {
                continue;
            }
            db.update("INSERT INTO " + prefix + "ip_bans "
                            + "(id, ip, issuer, reason, created_ms, expires_ms, active, auto_created) "
                            + "VALUES (?,?,?,?,?,?,?,?)",
                    s.getInt("id"), ip, s.getString("issuer", ""), s.getString("reason", ""),
                    s.getLong("created"), s.getLong("expires"),
                    s.getBoolean("active", true) ? 1 : 0,
                    s.getBoolean("auto", false) ? 1 : 0);
            n++;
        }
        return n;
    }

    private static int playerIps(SqlDb db, String prefix, File file) throws SQLException {
        if (!file.exists() || file.length() == 0) {
            return 0;
        }
        YamlConfiguration cfg = YamlIo.load(file);
        ConfigurationSection sec = cfg.getConfigurationSection("players");
        if (sec == null) {
            return 0;
        }
        int n = 0;
        for (String key : sec.getKeys(false)) {
            ConfigurationSection s = sec.getConfigurationSection(key);
            if (s == null) {
                continue;
            }
            try {
                UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                continue;
            }
            String ip = emptyToNull(s.getString("ip", ""));
            if (ip == null) {
                continue;
            }
            db.update("INSERT INTO " + prefix + "player_ips "
                            + "(uuid, name, ip, last_seen_ms) VALUES (?,?,?,?)",
                    key, s.getString("name", ""), ip, s.getLong("last-seen"));
            n++;
        }
        return n;
    }

    private static String emptyToNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }
}
