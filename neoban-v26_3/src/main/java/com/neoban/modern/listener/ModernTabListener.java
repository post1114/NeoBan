package com.neoban.modern.listener;

import com.neoban.common.NeoBanBase;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandSendEvent;

import java.util.Iterator;
import java.util.Locale;

public class ModernTabListener implements Listener {

    private final NeoBanBase plugin;

    public ModernTabListener(NeoBanBase plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onCommandSend(PlayerCommandSendEvent event) {
        if (!plugin.isRestricted(event.getPlayer())) {
            return;
        }
        Iterator<String> it = event.getCommands().iterator();
        while (it.hasNext()) {
            String cmd = it.next().toLowerCase(Locale.ROOT);
            if (cmd.startsWith("/")) {
                cmd = cmd.substring(1);
            }
            int colon = cmd.indexOf(':');
            if (colon >= 0) {
                cmd = cmd.substring(colon + 1);
            }
            if (!plugin.settings().allowedCommands().contains(cmd)) {
                it.remove();
            }
        }
    }
}
