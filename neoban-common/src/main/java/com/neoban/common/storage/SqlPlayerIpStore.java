package com.neoban.common.storage;

import com.neoban.common.storage.sql.SqlDb;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import java.util.UUID;

public class SqlPlayerIpStore implements PlayerIpStore {

    private final SqlDb db;
    private final String table;

    public SqlPlayerIpStore(SqlDb db, String prefix) {
        this.db = db;
        this.table = prefix + "player_ips";
    }

    @Override
    public void load() {
    }

    @Override
    public void save() {
    }

    @Override
    public void record(UUID uuid, String name, String ip) {
        if (uuid == null || ip == null || ip.isEmpty()) {
            return;
        }
        try {
            db.update("INSERT INTO " + table
                            + " (uuid, name, ip, last_seen_ms) VALUES (?,?,?,?) "
                            + "ON DUPLICATE KEY UPDATE name=VALUES(name), ip=VALUES(ip), "
                            + "last_seen_ms=VALUES(last_seen_ms)",
                    uuid.toString(), name == null ? "" : name, ip, System.currentTimeMillis());
        } catch (SQLException e) {
            throw new StorageException("playerIps.record", e);
        }
    }

    @Override
    public String findByUuid(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        try {
            return db.queryOne("SELECT ip FROM " + table + " WHERE uuid=?",
                    new SqlDb.RowMapper<String>() {
                        @Override
                        public String map(ResultSet rs) throws SQLException {
                            return rs.getString(1);
                        }
                    }, uuid.toString());
        } catch (SQLException e) {
            throw new StorageException("playerIps.findByUuid", e);
        }
    }

    @Override
    public String findByName(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        try {
            return db.queryOne("SELECT ip FROM " + table
                            + " WHERE LOWER(name)=? ORDER BY last_seen_ms DESC LIMIT 1",
                    new SqlDb.RowMapper<String>() {
                        @Override
                        public String map(ResultSet rs) throws SQLException {
                            return rs.getString(1);
                        }
                    }, name.toLowerCase(Locale.ROOT));
        } catch (SQLException e) {
            throw new StorageException("playerIps.findByName", e);
        }
    }
}
