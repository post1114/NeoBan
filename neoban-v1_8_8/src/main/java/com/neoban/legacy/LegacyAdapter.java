package com.neoban.legacy;

import com.neoban.common.PlatformAdapter;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;

public class LegacyAdapter implements PlatformAdapter {

    @Override
    public void hidePlayer(Player viewer, Player target) {
        viewer.hidePlayer(target);
    }

    @Override
    public void showPlayer(Player viewer, Player target) {
        viewer.showPlayer(target);
    }

    @Override
    public ChunkGenerator createVoidGenerator() {
        return new LegacyVoidGenerator();
    }

    @Override
    public void kick(Player player, String message) {
        player.kickPlayer(message);
    }
}
