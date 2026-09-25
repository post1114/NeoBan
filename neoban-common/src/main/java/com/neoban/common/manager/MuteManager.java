package com.neoban.common.manager;

import com.neoban.common.NeoBanBase;
import com.neoban.common.model.Punishment;

import java.util.UUID;

public class MuteManager {

    private final NeoBanBase plugin;

    public MuteManager(NeoBanBase plugin) {
        this.plugin = plugin;
    }

    public Punishment find(UUID uuid, String name) {
        return plugin.muteStore().findActive(uuid, name);
    }

    public Punishment mute(UUID uuid, String name, String issuer, String reason, long durationMs) {
        return plugin.muteStore().create(uuid, name, issuer, reason, durationMs);
    }

    public boolean unmute(UUID uuid, String name) {
        Punishment p = plugin.muteStore().findActive(uuid, name);
        if (p == null) {
            return false;
        }
        plugin.muteStore().deactivate(p);
        return true;
    }
}
