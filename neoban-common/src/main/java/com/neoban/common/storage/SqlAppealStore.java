package com.neoban.common.storage;

import com.neoban.common.model.Appeal;
import com.neoban.common.model.AppealStatus;
import com.neoban.common.model.PunishmentType;
import com.neoban.common.storage.sql.SqlDb;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class SqlAppealStore implements AppealStore {

    private final SqlDb db;
    private final String appealsTable;
    private final String countersTable;
    private final String countsTable;

    public SqlAppealStore(SqlDb db, String prefix) {
        this.db = db;
        this.appealsTable = prefix + "appeals";
        this.countersTable = prefix + "appeal_counters";
        this.countsTable = prefix + "appeal_counts";
    }

    private Appeal row(ResultSet rs) throws SQLException {
        Appeal a = new Appeal();
        a.setId(rs.getInt("id"));
        String type = rs.getString("ptype");
        a.setPunishmentType("MUTE".equalsIgnoreCase(type) ? PunishmentType.MUTE : PunishmentType.BAN);
        a.setPunishmentId(rs.getInt("pid"));
        String u = rs.getString("uuid");
        if (u != null && !u.isEmpty()) {
            try {
                a.setUuid(UUID.fromString(u));
            } catch (IllegalArgumentException ignored) {
            }
        }
        a.setName(rs.getString("name"));
        a.setReason(rs.getString("reason"));
        a.setCreated(rs.getLong("created_ms"));
        String status = rs.getString("status");
        try {
            a.setStatus(AppealStatus.valueOf(status));
        } catch (IllegalArgumentException e) {
            a.setStatus(AppealStatus.PENDING);
        }
        a.setDecidedBy(rs.getString("decided_by"));
        a.setDecidedAt(rs.getLong("decided_at_ms"));
        String note = rs.getString("note");
        a.setNote(note == null ? "" : note);
        a.setDelivered(rs.getInt("delivered") != 0);
        return a;
    }

    @Override
    public void load() {
    }

    @Override
    public void save() {
    }

    @Override
    public Appeal get(int id) {
        try {
            return db.queryOne("SELECT * FROM " + appealsTable + " WHERE id=? LIMIT 1",
                    new SqlDb.RowMapper<Appeal>() {
                        @Override
                        public Appeal map(ResultSet rs) throws SQLException {
                            return row(rs);
                        }
                    }, id);
        } catch (SQLException e) {
            throw new StorageException("appeals.get", e);
        }
    }

    @Override
    public Collection<Appeal> all() {
        try {
            return db.queryList("SELECT * FROM " + appealsTable + " ORDER BY id",
                    new SqlDb.RowMapper<Appeal>() {
                        @Override
                        public Appeal map(ResultSet rs) throws SQLException {
                            return row(rs);
                        }
                    });
        } catch (SQLException e) {
            throw new StorageException("appeals.all", e);
        }
    }

    @Override
    public List<Appeal> pending() {
        try {
            return db.queryList("SELECT * FROM " + appealsTable + " WHERE status='PENDING' ORDER BY id",
                    new SqlDb.RowMapper<Appeal>() {
                        @Override
                        public Appeal map(ResultSet rs) throws SQLException {
                            return row(rs);
                        }
                    });
        } catch (SQLException e) {
            throw new StorageException("appeals.pending", e);
        }
    }

    @Override
    public Appeal add(Appeal a) {
        try {
            long id = db.updateKey("INSERT INTO " + appealsTable
                            + " (ptype, pid, uuid, name, reason, created_ms, status, decided_by, decided_at_ms, note, delivered) "
                            + "VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                    a.getPunishmentType().name(), a.getPunishmentId(),
                    a.getUuid() == null ? null : a.getUuid().toString(),
                    a.getName() == null ? "" : a.getName(),
                    a.getReason() == null ? "" : a.getReason(),
                    a.getCreated(), a.getStatus().name(),
                    a.getDecidedBy() == null ? "" : a.getDecidedBy(),
                    a.getDecidedAt(), a.getNote() == null ? "" : a.getNote(),
                    a.isDelivered() ? 1 : 0);
            a.setId((int) id);
            return a;
        } catch (SQLException e) {
            throw new StorageException("appeals.add", e);
        }
    }

    @Override
    public void update(Appeal a) {
        try {
            db.update("UPDATE " + appealsTable
                            + " SET status=?, decided_by=?, decided_at_ms=?, note=?, delivered=? WHERE id=?",
                    a.getStatus().name(),
                    a.getDecidedBy() == null ? "" : a.getDecidedBy(),
                    a.getDecidedAt(), a.getNote() == null ? "" : a.getNote(),
                    a.isDelivered() ? 1 : 0, a.getId());
        } catch (SQLException e) {
            throw new StorageException("appeals.update", e);
        }
    }

    @Override
    public int countFor(String counterKey, PunishmentType type, int punishmentId, boolean lifetimeScope) {
        try {
            if (lifetimeScope) {
                Integer v = db.queryOne("SELECT lifetime FROM " + countersTable + " WHERE counter_key=?",
                        new SqlDb.RowMapper<Integer>() {
                            @Override
                            public Integer map(ResultSet rs) throws SQLException {
                                return rs.getInt(1);
                            }
                        }, counterKey);
                return v == null ? 0 : v;
            }
            Integer v = db.queryOne("SELECT cnt FROM " + countsTable
                            + " WHERE counter_key=? AND ptype=? AND pid=?",
                    new SqlDb.RowMapper<Integer>() {
                        @Override
                        public Integer map(ResultSet rs) throws SQLException {
                            return rs.getInt(1);
                        }
                    }, counterKey, type.name(), punishmentId);
            return v == null ? 0 : v;
        } catch (SQLException e) {
            throw new StorageException("appeals.countFor", e);
        }
    }

    @Override
    public void bump(String counterKey, PunishmentType type, int punishmentId) {
        try {
            long now = System.currentTimeMillis();
            int updated = db.update("UPDATE " + countersTable
                    + " SET lifetime=lifetime+1, last_appeal_ms=? WHERE counter_key=?", now, counterKey);
            if (updated == 0) {
                db.update("INSERT INTO " + countersTable
                        + " (counter_key, lifetime, last_appeal_ms) VALUES (?,1,?)", counterKey, now);
            }
            int updated2 = db.update("UPDATE " + countsTable
                    + " SET cnt=cnt+1 WHERE counter_key=? AND ptype=? AND pid=?",
                    counterKey, type.name(), punishmentId);
            if (updated2 == 0) {
                db.update("INSERT INTO " + countsTable
                                + " (counter_key, ptype, pid, cnt) VALUES (?,?,?,1)",
                        counterKey, type.name(), punishmentId);
            }
        } catch (SQLException e) {
            throw new StorageException("appeals.bump", e);
        }
    }

    @Override
    public long lastAppeal(String counterKey) {
        try {
            Long v = db.queryOne("SELECT last_appeal_ms FROM " + countersTable + " WHERE counter_key=?",
                    new SqlDb.RowMapper<Long>() {
                        @Override
                        public Long map(ResultSet rs) throws SQLException {
                            return rs.getLong(1);
                        }
                    }, counterKey);
            return v == null ? 0L : v;
        } catch (SQLException e) {
            throw new StorageException("appeals.lastAppeal", e);
        }
    }

    @Override
    public void reset(String counterKey) {
        try {
            db.update("DELETE FROM " + countersTable + " WHERE counter_key=?", counterKey);
            db.update("DELETE FROM " + countsTable + " WHERE counter_key=?", counterKey);
        } catch (SQLException e) {
            throw new StorageException("appeals.reset", e);
        }
    }

    @Override
    public int pendingCount() {
        try {
            return db.count("SELECT COUNT(*) FROM " + appealsTable + " WHERE status='PENDING'");
        } catch (SQLException e) {
            throw new StorageException("appeals.pendingCount", e);
        }
    }
}
