package com.neoban.legacy.listener;

import com.neoban.common.NeoBanBase;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPickupItemEvent;

public class LegacyPickupListener implements Listener {

    private final NeoBanBase plugin;

    public LegacyPickupListener(NeoBanBase plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(PlayerPickupItemEvent event) {
        if (plugin.isRestricted(event.getPlayer())) {
            event.setCancelled(true);
        }
    }
}
