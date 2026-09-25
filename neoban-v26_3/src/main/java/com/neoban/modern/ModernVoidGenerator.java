package com.neoban.modern;

import org.bukkit.HeightMap;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.util.Random;

public class ModernVoidGenerator extends ChunkGenerator {

    @Override
    public int getBaseHeight(WorldInfo worldInfo, Random random, int x, int z, HeightMap heightMap) {
        return 0;
    }

    @Override
    public boolean canSpawn(org.bukkit.World world, int x, int z) {
        return true;
    }
}
