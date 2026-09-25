package com.neoban.common;

import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;

public interface PlatformAdapter {

    void hidePlayer(Player viewer, Player target);

    void showPlayer(Player viewer, Player target);

    ChunkGenerator createVoidGenerator();

    void kick(Player player, String message);
}
