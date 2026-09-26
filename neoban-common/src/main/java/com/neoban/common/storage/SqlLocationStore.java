package com.neoban.common.storage;

import com.neoban.common.storage.sql.SqlDb;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class SqlLocationStore implements LocationStore {

    private final SqlDb db;
    private final String table;

    public SqlLocationStore(SqlDb db, String prefix) {
        this.db = db;
        this.table = prefix + "locations";
    }

    @Override
    public void load() {
    }

    @Override
    public void save() {
    }

    @Override
    public void save(UUID uuid, Location loc) {
        try {
            String world = loc.getWorld() == null ? "" : loc.getWorld().getName();
            db.update("INSERT INTO " + table
                            + " (uuid, world, x, y, z, yaw, pitch) VALUES (?,?,?,?,?,?,?) "
                            + "ON DUPLICATE KEY UPDATE world=VALUES(world), x=VALUES(x), y=VALUES(y), "
                            + "z=VALUES(z), yaw=VALUES(yaw), pitch=VALUES(pitch)",
                    uuid.toString(), world, loc.getX(), loc.getY(), loc.getZ(),
                    loc.getYaw(), loc.getPitch());
        } catch (SQLException e) {
            throw new StorageException("locations.save", e);
        }
    }

    @Override
    public Location get(UUID uuid) {
        try {
            LocationRow row = db.queryOne("SELECT * FROM " + table + " WHERE uuid=? LIMIT 1",
                    new SqlDb.RowMapper<LocationRow>() {
                        @Override
                        public LocationRow map(ResultSet rs) throws SQLException {
                            LocationRow r = new LocationRow();
                            r.world = rs.getString("world");
                            r.x = rs.getDouble("x");
                            r.y = rs.getDouble("y");
                            r.z = rs.getDouble("z");
                            r.yaw = rs.getFloat("yaw");
                            r.pitch = rs.getFloat("pitch");
                            return r;
                        }
                    }, uuid.toString());
            if (row == null || row.world == null || row.world.isEmpty()) {
                return null;
            }
            World w = Bukkit.getWorld(row.world);
            if (w == null) {
                return null;
            }
            return new Location(w, row.x, row.y, row.z, row.yaw, row.pitch);
        } catch (SQLException e) {
            throw new StorageException("locations.get", e);
        }
    }

    private static final class LocationRow {
        String world;
        double x;
        double y;
        double z;
        float yaw;
        float pitch;
    }

    @Override
    public void clear(UUID uuid) {
        try {
            db.update("DELETE FROM " + table + " WHERE uuid=?", uuid.toString());
        } catch (SQLException e) {
            throw new StorageException("locations.clear", e);
        }
    }
}
