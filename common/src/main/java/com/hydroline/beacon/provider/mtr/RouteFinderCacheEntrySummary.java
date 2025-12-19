package com.hydroline.beacon.provider.mtr;

public final class RouteFinderCacheEntrySummary {
    private final String dimension;
    private final long routeId;
    private final Integer railwayDataVersion;
    private final String routefinderVersion;
    private final String formatVersion;
    private final long updatedAt;

    public RouteFinderCacheEntrySummary(String dimension, long routeId, Integer railwayDataVersion,
                                        String routefinderVersion, String formatVersion, long updatedAt) {
        this.dimension = dimension;
        this.routeId = routeId;
        this.railwayDataVersion = railwayDataVersion;
        this.routefinderVersion = routefinderVersion;
        this.formatVersion = formatVersion;
        this.updatedAt = updatedAt;
    }

    public String getDimension() {
        return dimension;
    }

    public long getRouteId() {
        return routeId;
    }

    public java.util.Optional<Integer> getRailwayDataVersion() {
        return java.util.Optional.ofNullable(railwayDataVersion);
    }

    public String getRoutefinderVersion() {
        return routefinderVersion;
    }

    public String getFormatVersion() {
        return formatVersion;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }
}
