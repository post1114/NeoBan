package com.neoban.common.storage;

import java.util.UUID;

public interface PlayerIpStore {

    void load();

    void save();

    void record(UUID uuid, String name, String ip);

    String findByUuid(UUID uuid);

    String findByName(String name);
}
