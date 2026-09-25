package com.neoban.modern;

import com.neoban.common.PlatformAdapter;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.Plugin;

public class ModernAdapter implements PlatformAdapter {

    private final Plugin plugin;

    public ModernAdapter(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void hidePlayer(Player viewer, Player target) {
        viewer.hidePlayer(plugin, target);
    }

    @Override
    public void showPlayer(Player viewer, Player target) {
        viewer.showPlayer(plugin, target);
    }

    @Override
    public ChunkGenerator createVoidGenerator() {
        return new ModernVoidGenerator();
    }

    @Override
    public void kick(Player player, String message) {
        player.kickPlayer(message);
    }
}
