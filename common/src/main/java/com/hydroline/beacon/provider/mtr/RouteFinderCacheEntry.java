package com.hydroline.beacon.provider.mtr;

import java.util.Objects;

public final class RouteFinderCacheEntry {
    private final String dimension;
    private final long routeId;
    private final Integer railwayDataVersion;
    private final String routefinderVersion;
    private final String formatVersion;
    private final String polylineJson;
    private final String edgesJson;
    private final String nodesJson;
    private final String stateJson;
    private final String costJson;
    private final long updatedAt;

    public RouteFinderCacheEntry(String dimension, long routeId, Integer railwayDataVersion, String routefinderVersion,
                                String formatVersion, String polylineJson, String edgesJson, String nodesJson,
                                String stateJson, String costJson, long updatedAt) {
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        this.routeId = routeId;
        this.railwayDataVersion = railwayDataVersion;
        this.routefinderVersion = routefinderVersion;
        this.formatVersion = formatVersion;
        this.polylineJson = polylineJson;
        this.edgesJson = edgesJson;
        this.nodesJson = nodesJson;
        this.stateJson = stateJson;
        this.costJson = costJson;
        this.updatedAt = updatedAt;
    }

    public String getDimension() {
        return dimension;
    }

    public long getRouteId() {
        return routeId;
    }

    public Integer getRailwayDataVersion() {
        return railwayDataVersion;
    }

    public String getRoutefinderVersion() {
        return routefinderVersion;
    }

    public String getFormatVersion() {
        return formatVersion;
    }

    public String getPolylineJson() {
        return polylineJson;
    }

    public String getEdgesJson() {
        return edgesJson;
    }

    public String getNodesJson() {
        return nodesJson;
    }

    public String getStateJson() {
        return stateJson;
    }

    public String getCostJson() {
        return costJson;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }
}
