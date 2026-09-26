package com.neoban.common.storage;

import com.neoban.common.model.IpBan;

import java.util.Collection;
import java.util.List;

public interface IpBanStore {

    void load();

    void save();

    IpBan findActive(String ip);

    IpBan findById(int id);

    IpBan create(String ip, String issuer, String reason, long durationMs, boolean auto);

    void deactivate(IpBan ban);

    List<IpBan> pollExpired();

    int activeCount();

    List<String> activeIps();

    Collection<IpBan> all();
}
