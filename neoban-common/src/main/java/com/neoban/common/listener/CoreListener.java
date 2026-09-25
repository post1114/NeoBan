package com.neoban.common.listener;

import com.neoban.common.NeoBanBase;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public class CoreListener implements Listener {

    private final NeoBanBase plugin;

    public CoreListener(NeoBanBase plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        plugin.handleJoin(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.onPlayerQuit(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!plugin.isRestricted(player)) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
            Location fixed = to.clone();
            fixed.setX(from.getX());
            fixed.setY(from.getY());
            fixed.setZ(from.getZ());
            event.setTo(fixed);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (plugin.isInternalTeleport()) {
            return;
        }
        Player player = event.getPlayer();
        if (!plugin.isRestricted(player)) {
            return;
        }
        Location to = event.getTo();
        if (to != null && plugin.appealWorld() != null
                && !to.getWorld().equals(plugin.appealWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        if (plugin.isInternalTeleport()) {
            return;
        }
        if (plugin.isRestricted(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (plugin.isBanned(player)) {
            Location spawn = plugin.appealSpawn();
            if (spawn != null) {
                event.setRespawnLocation(spawn);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (plugin.isRestricted(player)) {
            if (!plugin.inAppealWorld(player)) {
                plugin.teleportInternal(player, plugin.appealSpawn());
                plugin.syncVisibilityAround(player);
            }
        } else if (plugin.inAppealWorld(player)) {
            plugin.restoreFromAppeal(player);
        }
    }
}
