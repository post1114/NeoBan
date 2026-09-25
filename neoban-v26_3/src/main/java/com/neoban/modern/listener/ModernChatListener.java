package com.neoban.modern.listener;

import com.neoban.common.NeoBanBase;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class ModernChatListener implements Listener {

    private final NeoBanBase plugin;

    public ModernChatListener(NeoBanBase plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        String plain = PlainTextComponentSerializer.plainText().serialize(event.message());
        if (plugin.handleChat(event.getPlayer(), plain)) {
            event.setCancelled(true);
        }
    }
}
