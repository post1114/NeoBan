package com.neoban.common.storage;

import com.neoban.common.model.Punishment;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public interface PunishmentStore {

    static String nameKey(String name) {
        return "name-" + name.toLowerCase(Locale.ROOT).replace('.', '_');
    }

    static String uuidKey(UUID uuid) {
        return "uuid-" + uuid.toString();
    }

    void load();

    void save();

    Punishment findActive(UUID uuid, String name);

    Punishment findById(int id);

    UUID findUuidByName(String name);

    Punishment create(UUID uuid, String name, String issuer, String reason, long durationMs, String ip);

    void deactivate(Punishment p);

    void migrateToUuid(String name, UUID uuid);

    void updateIp(Punishment p, String ip);

    List<Punishment> pollExpired();

    int activeCount();

    int countActiveByIp(String ip);

    Collection<Punishment> all();
}
