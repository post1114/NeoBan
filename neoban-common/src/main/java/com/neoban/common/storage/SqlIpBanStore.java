package com.neoban.common.storage;

import com.neoban.common.model.IpBan;
import com.neoban.common.storage.sql.SqlDb;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

public class SqlIpBanStore implements IpBanStore {

    private final SqlDb db;
    private final String table;

    public SqlIpBanStore(SqlDb db, String prefix) {
        this.db = db;
        this.table = prefix + "ip_bans";
    }

    private IpBan row(ResultSet rs) throws SQLException {
        IpBan b = new IpBan();
        b.setId(rs.getInt("id"));
        b.setIp(rs.getString("ip"));
        b.setIssuer(rs.getString("issuer"));
        b.setReason(rs.getString("reason"));
        b.setCreated(rs.getLong("created_ms"));
        b.setExpires(rs.getLong("expires_ms"));
        b.setActive(rs.getInt("active") != 0);
        b.setAuto(rs.getInt("auto_created") != 0);
        return b;
    }

    private static String key(String ip) {
        return ip == null ? "" : ip.toLowerCase(Locale.ROOT);
    }

    @Override
    public void load() {
    }

    @Override
    public void save() {
    }

    @Override
    public IpBan findActive(String ip) {
        if (ip == null) {
            return null;
        }
        try {
            IpBan b = db.queryOne("SELECT * FROM " + table + " WHERE active=1 AND ip=? LIMIT 1",
                    new SqlDb.RowMapper<IpBan>() {
                        @Override
                        public IpBan map(ResultSet rs) throws SQLException {
                            return row(rs);
                        }
                    }, key(ip));
            if (b == null) {
                return null;
            }
            if (b.isExpired(System.currentTimeMillis())) {
                deactivate(b);
                return null;
            }
            return b;
        } catch (SQLException e) {
            throw new StorageException("ipBans.findActive", e);
        }
    }

    @Override
    public IpBan findById(int id) {
        try {
            return db.queryOne("SELECT * FROM " + table + " WHERE id=? LIMIT 1",
                    new SqlDb.RowMapper<IpBan>() {
                        @Override
                        public IpBan map(ResultSet rs) throws SQLException {
                            return row(rs);
                        }
                    }, id);
        } catch (SQLException e) {
            throw new StorageException("ipBans.findById", e);
        }
    }

    @Override
    public IpBan create(String ip, String issuer, String reason, long durationMs, boolean auto) {
        try {
            IpBan b = new IpBan();
            long now = System.currentTimeMillis();
            long expires = durationMs <= 0 ? 0L : now + durationMs;
            long id = db.updateKey("INSERT INTO " + table
                            + " (ip, issuer, reason, created_ms, expires_ms, active, auto_created) "
                            + "VALUES (?,?,?,?,?,?,?)",
                    key(ip), issuer == null ? "" : issuer, reason == null ? "" : reason,
                    now, expires, 1, auto ? 1 : 0);
            b.setId((int) id);
            b.setIp(key(ip));
            b.setIssuer(issuer);
            b.setReason(reason);
            b.setCreated(now);
            b.setExpires(expires);
            b.setActive(true);
            b.setAuto(auto);
            return b;
        } catch (SQLException e) {
            throw new StorageException("ipBans.create", e);
        }
    }

    @Override
    public void deactivate(IpBan ban) {
        try {
            db.update("UPDATE " + table + " SET active=0 WHERE id=?", ban.getId());
            ban.setActive(false);
        } catch (SQLException e) {
            throw new StorageException("ipBans.deactivate", e);
        }
    }

    @Override
    public List<IpBan> pollExpired() {
        try {
            long now = System.currentTimeMillis();
            List<IpBan> rows = db.queryList(
                    "SELECT * FROM " + table + " WHERE active=1 AND expires_ms>0 AND expires_ms<=?",
                    new SqlDb.RowMapper<IpBan>() {
                        @Override
                        public IpBan map(ResultSet rs) throws SQLException {
                            return row(rs);
                        }
                    }, now);
            for (IpBan b : rows) {
                deactivate(b);
            }
            return rows;
        } catch (SQLException e) {
            throw new StorageException("ipBans.pollExpired", e);
        }
    }

    @Override
    public int activeCount() {
        try {
            long now = System.currentTimeMillis();
            return db.count("SELECT COUNT(*) FROM " + table
                    + " WHERE active=1 AND (expires_ms=0 OR expires_ms>?)", now);
        } catch (SQLException e) {
            throw new StorageException("ipBans.activeCount", e);
        }
    }

    @Override
    public List<String> activeIps() {
        try {
            long now = System.currentTimeMillis();
            List<String> rows = db.queryList(
                    "SELECT ip FROM " + table + " WHERE active=1 AND (expires_ms=0 OR expires_ms>?)",
                    new SqlDb.RowMapper<String>() {
                        @Override
                        public String map(ResultSet rs) throws SQLException {
                            return rs.getString(1);
                        }
                    }, now);
            return new ArrayList<String>(rows);
        } catch (SQLException e) {
            throw new StorageException("ipBans.activeIps", e);
        }
    }

    @Override
    public Collection<IpBan> all() {
        try {
            return db.queryList("SELECT * FROM " + table + " ORDER BY id",
                    new SqlDb.RowMapper<IpBan>() {
                        @Override
                        public IpBan map(ResultSet rs) throws SQLException {
                            return row(rs);
                        }
                    });
        } catch (SQLException e) {
            throw new StorageException("ipBans.all", e);
        }
    }
}
