package com.hydroline.beacon.provider.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class BeaconConfigPaths {
    private static final Path BASE_CONFIG_DIR = Paths.get("config", "beacon-provider");

    private BeaconConfigPaths() {
    }

    public static Path getBaseConfigDir() {
        try {
            Files.createDirectories(BASE_CONFIG_DIR);
        } catch (Exception ignored) {
        }
        return BASE_CONFIG_DIR;
    }

    public static Path getRoutefinderCachePath() {
        Path dir = getBaseConfigDir();
        Path target = dir.resolve("db-routefinder-cache.db");
        Path legacy = dir.resolve("routefinder-cache.sqlite");
        if (Files.exists(legacy) && Files.notExists(target)) {
            try {
                Files.move(legacy, target);
            } catch (Exception ignored) {
                return legacy;
            }
        }
        return target;
    }
}
