package com.neoban.legacy;

import com.neoban.common.NeoBanBase;
import com.neoban.common.PlatformAdapter;
import com.neoban.legacy.listener.LegacyChatListener;
import com.neoban.legacy.listener.LegacyPickupListener;

public class NeoBanLegacy extends NeoBanBase {

    @Override
    protected PlatformAdapter createAdapter() {
        return new LegacyAdapter();
    }

    @Override
    protected void registerPlatformListeners() {
        getServer().getPluginManager().registerEvents(new LegacyChatListener(this), this);
        getServer().getPluginManager().registerEvents(new LegacyPickupListener(this), this);
    }
}
