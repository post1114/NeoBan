package com.neoban.common.manager;

import com.neoban.common.NeoBanBase;
import com.neoban.common.model.Punishment;

import java.util.UUID;

public class BanManager {

    private final NeoBanBase plugin;

    public BanManager(NeoBanBase plugin) {
        this.plugin = plugin;
    }

    public Punishment find(UUID uuid, String name) {
        return plugin.banStore().findActive(uuid, name);
    }

    public Punishment ban(UUID uuid, String name, String issuer, String reason, long durationMs, String ip) {
        return plugin.banStore().create(uuid, name, issuer, reason, durationMs, ip);
    }

    public boolean unban(UUID uuid, String name) {
        Punishment p = plugin.banStore().findActive(uuid, name);
        if (p == null) {
            return false;
        }
        plugin.banStore().deactivate(p);
        return true;
    }
}
