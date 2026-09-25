package com.neoban.legacy.listener;

import com.neoban.common.NeoBanBase;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public class LegacyChatListener implements Listener {

    private final NeoBanBase plugin;

    public LegacyChatListener(NeoBanBase plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        if (plugin.handleChat(event.getPlayer(), event.getMessage())) {
            event.setCancelled(true);
        }
    }
}
