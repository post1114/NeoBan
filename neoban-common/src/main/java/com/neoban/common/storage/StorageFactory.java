package com.neoban.common.storage;

import com.neoban.common.config.Settings;
import com.neoban.common.model.PunishmentType;
import com.neoban.common.storage.sql.SqlDb;
import com.neoban.common.storage.sql.SqlImport;
import com.neoban.common.storage.sql.SqlSchema;

import java.io.File;
import java.sql.SQLException;
import java.util.logging.Logger;

public final class StorageFactory {

    public static final class Context {
        public PunishmentStore bans;
        public PunishmentStore mutes;
        public AppealStore appeals;
        public LocationStore locations;
        public IpBanStore ipBans;
        public PlayerIpStore playerIps;
        public SqlDb db;
        public Settings.StorageType type;

        public void loadAll() {
            bans.load();
            mutes.load();
            appeals.load();
            locations.load();
            ipBans.load();
            playerIps.load();
        }

        public void saveAll() {
            bans.save();
            mutes.save();
            appeals.save();
            locations.save();
            ipBans.save();
            playerIps.save();
        }

        public void close() {
            if (db != null) {
                db.close();
            }
        }
    }

    private StorageFactory() {
    }

    public static Context create(File dataDir, Settings settings, Logger logger) throws SQLException {
        Context ctx = new Context();
        ctx.type = settings.storageType();
        if (ctx.type == Settings.StorageType.MYSQL) {
            SqlDb db = new SqlDb(settings);
            db.ping();
            String prefix = settings.mysqlTablePrefix();
            SqlSchema.init(db, prefix);
            SqlImport.importYamlIfEmpty(db, prefix, dataDir, logger);
            ctx.db = db;
            ctx.bans = new SqlPunishmentStore(db, prefix, PunishmentType.BAN);
            ctx.mutes = new SqlPunishmentStore(db, prefix, PunishmentType.MUTE);
            ctx.appeals = new SqlAppealStore(db, prefix);
            ctx.locations = new SqlLocationStore(db, prefix);
            ctx.ipBans = new SqlIpBanStore(db, prefix);
            ctx.playerIps = new SqlPlayerIpStore(db, prefix);
        } else {
            ctx.bans = new YamlPunishmentStore(new File(dataDir, "bans.yml"), PunishmentType.BAN);
            ctx.mutes = new YamlPunishmentStore(new File(dataDir, "mutes.yml"), PunishmentType.MUTE);
            ctx.appeals = new YamlAppealStore(new File(dataDir, "appeals.yml"));
            ctx.locations = new YamlLocationStore(new File(dataDir, "locations.yml"));
            ctx.ipBans = new YamlIpBanStore(new File(dataDir, "ipbans.yml"));
            ctx.playerIps = new YamlPlayerIpStore(new File(dataDir, "player-ips.yml"));
        }
        ctx.loadAll();
        return ctx;
    }
}
