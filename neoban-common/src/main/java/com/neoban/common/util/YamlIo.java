package com.neoban.common.util;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class YamlIo {

    private static final Logger LOGGER = Logger.getLogger("NeoBan");

    private YamlIo() {
    }

    public static YamlConfiguration load(File file) {
        YamlConfiguration cfg = new YamlConfiguration();
        if (file == null || !file.exists() || file.length() == 0) {
            return cfg;
        }
        Reader reader = null;
        try {
            reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8);
            cfg.load(reader);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Cannot load " + file, e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignored) {
                }
            }
        }
        return cfg;
    }

    public static void save(YamlConfiguration cfg, File file) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8);
        try {
            writer.write(cfg.saveToString());
        } finally {
            writer.close();
        }
    }
}
