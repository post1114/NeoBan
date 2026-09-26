package com.neoban.common.storage.sql;

import java.sql.SQLException;

public final class SqlSchema {

    private SqlSchema() {
    }

    public static void init(SqlDb db, String prefix) throws SQLException {
        db.update("CREATE TABLE IF NOT EXISTS " + prefix + "punishments ("
                + "id INT NOT NULL,"
                + "type VARCHAR(8) NOT NULL,"
                + "uuid VARCHAR(36) NULL,"
                + "name VARCHAR(64) NOT NULL DEFAULT '',"
                + "issuer VARCHAR(64) NOT NULL DEFAULT '',"
                + "reason TEXT,"
                + "created_ms BIGINT NOT NULL DEFAULT 0,"
                + "expires_ms BIGINT NOT NULL DEFAULT 0,"
                + "active TINYINT NOT NULL DEFAULT 1,"
                + "ip VARCHAR(45) NULL,"
                + "PRIMARY KEY (type, id),"
                + "KEY idx_type_active (type, active),"
                + "KEY idx_uuid (type, uuid),"
                + "KEY idx_name (type, name),"
                + "KEY idx_ip (type, active, ip)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci");

        db.update("CREATE TABLE IF NOT EXISTS " + prefix + "appeals ("
                + "id INT NOT NULL AUTO_INCREMENT,"
                + "ptype VARCHAR(8) NOT NULL,"
                + "pid INT NOT NULL,"
                + "uuid VARCHAR(36) NULL,"
                + "name VARCHAR(64) NOT NULL DEFAULT '',"
                + "reason TEXT,"
                + "created_ms BIGINT NOT NULL DEFAULT 0,"
                + "status VARCHAR(16) NOT NULL DEFAULT 'PENDING',"
                + "decided_by VARCHAR(64) NOT NULL DEFAULT '',"
                + "decided_at_ms BIGINT NOT NULL DEFAULT 0,"
                + "note TEXT,"
                + "delivered TINYINT NOT NULL DEFAULT 0,"
                + "PRIMARY KEY (id),"
                + "KEY idx_status (status)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci");

        db.update("CREATE TABLE IF NOT EXISTS " + prefix + "appeal_counters ("
                + "counter_key VARCHAR(64) NOT NULL,"
                + "lifetime INT NOT NULL DEFAULT 0,"
                + "last_appeal_ms BIGINT NOT NULL DEFAULT 0,"
                + "PRIMARY KEY (counter_key)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci");

        db.update("CREATE TABLE IF NOT EXISTS " + prefix + "appeal_counts ("
                + "counter_key VARCHAR(64) NOT NULL,"
                + "ptype VARCHAR(8) NOT NULL,"
                + "pid INT NOT NULL,"
                + "cnt INT NOT NULL DEFAULT 0,"
                + "PRIMARY KEY (counter_key, ptype, pid)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci");

        db.update("CREATE TABLE IF NOT EXISTS " + prefix + "locations ("
                + "uuid VARCHAR(36) NOT NULL,"
                + "world VARCHAR(64) NOT NULL DEFAULT '',"
                + "x DOUBLE NOT NULL DEFAULT 0,"
                + "y DOUBLE NOT NULL DEFAULT 0,"
                + "z DOUBLE NOT NULL DEFAULT 0,"
                + "yaw FLOAT NOT NULL DEFAULT 0,"
                + "pitch FLOAT NOT NULL DEFAULT 0,"
                + "PRIMARY KEY (uuid)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci");

        db.update("CREATE TABLE IF NOT EXISTS " + prefix + "ip_bans ("
                + "id INT NOT NULL AUTO_INCREMENT,"
                + "ip VARCHAR(45) NOT NULL,"
                + "issuer VARCHAR(64) NOT NULL DEFAULT '',"
                + "reason TEXT,"
                + "created_ms BIGINT NOT NULL DEFAULT 0,"
                + "expires_ms BIGINT NOT NULL DEFAULT 0,"
                + "active TINYINT NOT NULL DEFAULT 1,"
                + "auto_created TINYINT NOT NULL DEFAULT 0,"
                + "PRIMARY KEY (id),"
                + "KEY idx_ip_active (ip, active)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci");

        db.update("CREATE TABLE IF NOT EXISTS " + prefix + "player_ips ("
                + "uuid VARCHAR(36) NOT NULL,"
                + "name VARCHAR(64) NOT NULL DEFAULT '',"
                + "ip VARCHAR(45) NOT NULL,"
                + "last_seen_ms BIGINT NOT NULL DEFAULT 0,"
                + "PRIMARY KEY (uuid),"
                + "KEY idx_player_name (name)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci");
    }

    public static boolean isEmpty(SqlDb db, String prefix) throws SQLException {
        return db.count("SELECT COUNT(*) FROM " + prefix + "punishments") == 0
                && db.count("SELECT COUNT(*) FROM " + prefix + "appeals") == 0
                && db.count("SELECT COUNT(*) FROM " + prefix + "appeal_counters") == 0
                && db.count("SELECT COUNT(*) FROM " + prefix + "locations") == 0
                && db.count("SELECT COUNT(*) FROM " + prefix + "ip_bans") == 0
                && db.count("SELECT COUNT(*) FROM " + prefix + "player_ips") == 0;
    }
}
