package com.neoban.common.storage;

import com.neoban.common.model.Appeal;

import java.util.Collection;
import java.util.List;

public interface AppealStore {

    void load();

    void save();

    Appeal get(int id);

    Collection<Appeal> all();

    List<Appeal> pending();

    Appeal add(Appeal a);

    void update(Appeal a);

    int countFor(String counterKey, com.neoban.common.model.PunishmentType type, int punishmentId, boolean lifetimeScope);

    void bump(String counterKey, com.neoban.common.model.PunishmentType type, int punishmentId);

    long lastAppeal(String counterKey);

    void reset(String counterKey);

    int pendingCount();
}
