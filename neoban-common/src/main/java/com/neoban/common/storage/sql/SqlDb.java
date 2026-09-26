package com.neoban.common.storage.sql;

import com.neoban.common.config.Settings;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public final class SqlDb {

    public interface RowMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }

    private final String url;
    private final String user;
    private final String password;
    private Connection conn;

    public SqlDb(Settings s) {
        StringBuilder url = new StringBuilder("jdbc:mysql://");
        url.append(s.mysqlHost()).append(':').append(s.mysqlPort())
                .append('/').append(s.mysqlDatabase());
        String props = s.mysqlProperties();
        if (props != null && !props.trim().isEmpty()) {
            url.append('?').append(props.trim());
        }
        this.url = url.toString();
        this.user = s.mysqlUser();
        this.password = s.mysqlPassword();
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("MySQL driver not bundled", e);
        }
    }

    public synchronized Connection connection() throws SQLException {
        if (conn != null) {
            try {
                if (conn.isValid(2)) {
                    return conn;
                }
            } catch (SQLException ignored) {
            }
            closeQuietly();
        }
        conn = DriverManager.getConnection(url, user, password);
        return conn;
    }

    public synchronized void ping() throws SQLException {
        connection().isValid(2);
    }

    private static void bind(PreparedStatement ps, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            Object v = params[i];
            if (v == null) {
                ps.setNull(i + 1, java.sql.Types.NULL);
            } else if (v instanceof String) {
                ps.setString(i + 1, (String) v);
            } else if (v instanceof Integer) {
                ps.setInt(i + 1, (Integer) v);
            } else if (v instanceof Long) {
                ps.setLong(i + 1, (Long) v);
            } else if (v instanceof Boolean) {
                ps.setBoolean(i + 1, (Boolean) v);
            } else if (v instanceof Double) {
                ps.setDouble(i + 1, (Double) v);
            } else if (v instanceof Float) {
                ps.setFloat(i + 1, (Float) v);
            } else {
                ps.setObject(i + 1, v);
            }
        }
    }

    public synchronized int update(String sql, Object... params) throws SQLException {
        PreparedStatement ps = connection().prepareStatement(sql);
        try {
            bind(ps, params);
            return ps.executeUpdate();
        } finally {
            ps.close();
        }
    }

    public synchronized long updateKey(String sql, Object... params) throws SQLException {
        PreparedStatement ps = connection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
        try {
            bind(ps, params);
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            try {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            } finally {
                rs.close();
            }
            throw new SQLException("No generated key returned");
        } finally {
            ps.close();
        }
    }

    public synchronized <T> List<T> queryList(String sql, RowMapper<T> mapper, Object... params) throws SQLException {
        List<T> out = new ArrayList<T>();
        PreparedStatement ps = connection().prepareStatement(sql);
        try {
            bind(ps, params);
            ResultSet rs = ps.executeQuery();
            try {
                while (rs.next()) {
                    out.add(mapper.map(rs));
                }
            } finally {
                rs.close();
            }
        } finally {
            ps.close();
        }
        return out;
    }

    public synchronized <T> T queryOne(String sql, RowMapper<T> mapper, Object... params) throws SQLException {
        List<T> list = queryList(sql, mapper, params);
        return list.isEmpty() ? null : list.get(0);
    }

    public synchronized int count(String sql, Object... params) throws SQLException {
        Integer c = queryOne(sql, new RowMapper<Integer>() {
            @Override
            public Integer map(ResultSet rs) throws SQLException {
                return rs.getInt(1);
            }
        }, params);
        return c == null ? 0 : c;
    }

    public synchronized void close() {
        closeQuietly();
    }

    private void closeQuietly() {
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException ignored) {
            }
            conn = null;
        }
    }
}
