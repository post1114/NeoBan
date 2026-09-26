package com.neoban.common.storage;

import org.bukkit.Location;

import java.util.UUID;

public interface LocationStore {

    void load();

    void save();

    void save(UUID uuid, Location loc);

    Location get(UUID uuid);

    void clear(UUID uuid);
}
