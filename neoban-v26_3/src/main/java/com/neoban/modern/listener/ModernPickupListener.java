package com.neoban.modern.listener;

import com.neoban.common.NeoBanBase;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;

public class ModernPickupListener implements Listener {

    private final NeoBanBase plugin;

    public ModernPickupListener(NeoBanBase plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player && plugin.isRestricted((Player) event.getEntity())) {
            event.setCancelled(true);
        }
    }
}
