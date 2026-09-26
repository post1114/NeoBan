package com.neoban.common.storage;

import com.neoban.common.model.Punishment;
import com.neoban.common.model.PunishmentType;
import com.neoban.common.storage.sql.SqlDb;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class SqlPunishmentStore implements PunishmentStore {

    private final SqlDb db;
    private final String table;
    private final PunishmentType type;

    public SqlPunishmentStore(SqlDb db, String prefix, PunishmentType type) {
        this.db = db;
        this.table = prefix + "punishments";
        this.type = type;
    }

    private Punishment row(ResultSet rs) throws SQLException {
        Punishment p = new Punishment(type);
        p.setId(rs.getInt("id"));
        String u = rs.getString("uuid");
        if (u != null && !u.isEmpty()) {
            try {
                p.setUuid(UUID.fromString(u));
            } catch (IllegalArgumentException ignored) {
            }
        }
        p.setName(rs.getString("name"));
        p.setIssuer(rs.getString("issuer"));
        p.setReason(rs.getString("reason"));
        p.setCreated(rs.getLong("created_ms"));
        p.setExpires(rs.getLong("expires_ms"));
        p.setActive(rs.getInt("active") != 0);
        p.setIp(emptyToNull(rs.getString("ip")));
        return p;
    }

    private static String emptyToNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }

    @Override
    public void load() {
    }

    @Override
    public void save() {
    }

    @Override
    public Punishment findActive(UUID uuid, String name) {
        try {
            long now = System.currentTimeMillis();
            if (uuid != null) {
                Punishment p = db.queryOne(
                        "SELECT * FROM " + table + " WHERE type=? AND active=1 AND uuid=? LIMIT 1",
                        new SqlDb.RowMapper<Punishment>() {
                            @Override
                            public Punishment map(ResultSet rs) throws SQLException {
                                return row(rs);
                            }
                        }, type.name(), uuid.toString());
                if (p != null) {
                    if (p.isExpired(now)) {
                        deactivate(p);
                    } else {
                        return p;
                    }
                }
            }
            if (name != null && !name.isEmpty()) {
                Punishment p = db.queryOne(
                        "SELECT * FROM " + table + " WHERE type=? AND active=1 AND name=? LIMIT 1",
                        new SqlDb.RowMapper<Punishment>() {
                            @Override
                            public Punishment map(ResultSet rs) throws SQLException {
                                return row(rs);
                            }
                        }, type.name(), name);
                if (p != null && !p.isExpired(now)) {
                    if (uuid != null && p.getUuid() == null) {
                        migrateToUuid(name, uuid);
                    }
                    return p;
                }
                if (p != null) {
                    deactivate(p);
                }
            }
            return null;
        } catch (SQLException e) {
            throw new StorageException("findActive", e);
        }
    }

    @Override
    public Punishment findById(int id) {
        try {
            return db.queryOne("SELECT * FROM " + table + " WHERE type=? AND id=? LIMIT 1",
                    new SqlDb.RowMapper<Punishment>() {
                        @Override
                        public Punishment map(ResultSet rs) throws SQLException {
                            return row(rs);
                        }
                    }, type.name(), id);
        } catch (SQLException e) {
            throw new StorageException("findById", e);
        }
    }

    @Override
    public UUID findUuidByName(String name) {
        if (name == null) {
            return null;
        }
        try {
            String u = db.queryOne(
                    "SELECT uuid FROM " + table + " WHERE type=? AND name=? LIMIT 1",
                    new SqlDb.RowMapper<String>() {
                        @Override
                        public String map(ResultSet rs) throws SQLException {
                            return rs.getString(1);
                        }
                    }, type.name(), name);
            if (u == null || u.isEmpty()) {
                return null;
            }
            return UUID.fromString(u);
        } catch (SQLException e) {
            throw new StorageException("findUuidByName", e);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public Punishment create(UUID uuid, String name, String issuer, String reason, long durationMs, String ip) {
        try {
            Punishment p = new Punishment(type);
            long now = System.currentTimeMillis();
            long expires = durationMs <= 0 ? 0L : now + durationMs;
            int nextId = db.queryOne(
                    "SELECT COALESCE(MAX(id),0)+1 FROM " + table + " WHERE type=?",
                    new SqlDb.RowMapper<Integer>() {
                        @Override
                        public Integer map(ResultSet rs) throws SQLException {
                            return rs.getInt(1);
                        }
                    }, type.name());
            db.update(
                    "INSERT INTO " + table
                            + " (id, type, uuid, name, issuer, reason, created_ms, expires_ms, active, ip) "
                            + "VALUES (?,?,?,?,?,?,?,?,?,?)",
                    nextId, type.name(), uuid == null ? null : uuid.toString(), name == null ? "" : name,
                    issuer == null ? "" : issuer, reason == null ? "" : reason,
                    now, expires, 1, ip);
            p.setId(nextId);
            p.setUuid(uuid);
            p.setName(name);
            p.setIssuer(issuer);
            p.setReason(reason);
            p.setCreated(now);
            p.setExpires(expires);
            p.setActive(true);
            p.setIp(ip);
            return p;
        } catch (SQLException e) {
            throw new StorageException("create", e);
        }
    }

    @Override
    public void deactivate(Punishment p) {
        try {
            db.update("UPDATE " + table + " SET active=0 WHERE type=? AND id=?",
                    type.name(), p.getId());
            p.setActive(false);
        } catch (SQLException e) {
            throw new StorageException("deactivate", e);
        }
    }

    @Override
    public void migrateToUuid(String name, UUID uuid) {
        if (name == null || uuid == null) {
            return;
        }
        try {
            Integer existing = db.queryOne(
                    "SELECT id FROM " + table + " WHERE type=? AND uuid=? LIMIT 1",
                    new SqlDb.RowMapper<Integer>() {
                        @Override
                        public Integer map(ResultSet rs) throws SQLException {
                            return rs.getInt(1);
                        }
                    }, type.name(), uuid.toString());
            if (existing != null) {
                return;
            }
            db.update("UPDATE " + table
                            + " SET uuid=? WHERE type=? AND name=? AND (uuid IS NULL OR uuid='')",
                    uuid.toString(), type.name(), name);
        } catch (SQLException e) {
            throw new StorageException("migrateToUuid", e);
        }
    }

    @Override
    public void updateIp(Punishment p, String ip) {
        try {
            db.update("UPDATE " + table + " SET ip=? WHERE type=? AND id=?",
                    ip, type.name(), p.getId());
            p.setIp(ip);
        } catch (SQLException e) {
            throw new StorageException("updateIp", e);
        }
    }

    @Override
    public List<Punishment> pollExpired() {
        try {
            long now = System.currentTimeMillis();
            List<Punishment> rows = db.queryList(
                    "SELECT * FROM " + table
                            + " WHERE type=? AND active=1 AND expires_ms>0 AND expires_ms<=?",
                    new SqlDb.RowMapper<Punishment>() {
                        @Override
                        public Punishment map(ResultSet rs) throws SQLException {
                            return row(rs);
                        }
                    }, type.name(), now);
            for (Punishment p : rows) {
                deactivate(p);
            }
            return rows;
        } catch (SQLException e) {
            throw new StorageException("pollExpired", e);
        }
    }

    @Override
    public int activeCount() {
        try {
            long now = System.currentTimeMillis();
            return db.count("SELECT COUNT(*) FROM " + table
                    + " WHERE type=? AND active=1 AND (expires_ms=0 OR expires_ms>?)", type.name(), now);
        } catch (SQLException e) {
            throw new StorageException("activeCount", e);
        }
    }

    @Override
    public int countActiveByIp(String ip) {
        if (ip == null || ip.isEmpty()) {
            return 0;
        }
        try {
            long now = System.currentTimeMillis();
            return db.count("SELECT COUNT(*) FROM " + table
                    + " WHERE type=? AND active=1 AND (expires_ms=0 OR expires_ms>?) AND ip=?",
                    type.name(), now, ip);
        } catch (SQLException e) {
            throw new StorageException("countActiveByIp", e);
        }
    }

    @Override
    public Collection<Punishment> all() {
        try {
            return db.queryList("SELECT * FROM " + table + " WHERE type=? ORDER BY id",
                    new SqlDb.RowMapper<Punishment>() {
                        @Override
                        public Punishment map(ResultSet rs) throws SQLException {
                            return row(rs);
                        }
                    }, type.name());
        } catch (SQLException e) {
            throw new StorageException("all", e);
        }
    }
}
