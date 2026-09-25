package com.neoban.modern;

import com.neoban.common.NeoBanBase;
import com.neoban.common.PlatformAdapter;
import com.neoban.modern.listener.ModernChatListener;
import com.neoban.modern.listener.ModernPickupListener;
import com.neoban.modern.listener.ModernTabListener;

public class NeoBanModern extends NeoBanBase {

    @Override
    protected PlatformAdapter createAdapter() {
        return new ModernAdapter(this);
    }

    @Override
    protected void registerPlatformListeners() {
        getServer().getPluginManager().registerEvents(new ModernChatListener(this), this);
        getServer().getPluginManager().registerEvents(new ModernPickupListener(this), this);
        getServer().getPluginManager().registerEvents(new ModernTabListener(this), this);
    }
}
