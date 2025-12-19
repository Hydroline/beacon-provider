package com.hydroline.beacon.provider.mtr;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.hydroline.beacon.provider.mtr.MtrModels.Bounds;
import com.hydroline.beacon.provider.mtr.MtrModels.DepotInfo;
import com.hydroline.beacon.provider.mtr.MtrModels.DimensionOverview;
import com.hydroline.beacon.provider.mtr.MtrModels.FareAreaInfo;
import com.hydroline.beacon.provider.mtr.MtrModels.NodeInfo;
import com.hydroline.beacon.provider.mtr.MtrModels.NodePage;
import com.hydroline.beacon.provider.mtr.MtrModels.PlatformSummary;
import com.hydroline.beacon.provider.mtr.MtrModels.PlatformTimetable;
import com.hydroline.beacon.provider.mtr.MtrModels.RouteDetail;
import com.hydroline.beacon.provider.mtr.MtrModels.RouteNode;
import com.hydroline.beacon.provider.mtr.MtrModels.RouteSummary;
import com.hydroline.beacon.provider.mtr.MtrModels.ScheduleEntry;
import com.hydroline.beacon.provider.mtr.MtrModels.StationInfo;
import com.hydroline.beacon.provider.mtr.MtrModels.StationPlatformInfo;
import com.hydroline.beacon.provider.mtr.MtrModels.StationTimetable;
import com.hydroline.beacon.provider.mtr.MtrModels.TrainStatus;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Converts DTOs into the JSON schema expected by Bukkit / website callers.
 */
public final class MtrJsonWriter {
    private MtrJsonWriter() {
    }

    public static JsonArray writeDimensionOverview(List<DimensionOverview> dimensions) {
        JsonArray array = new JsonArray();
        if (dimensions == null) {
            return array;
        }
        for (DimensionOverview overview : dimensions) {
            if (overview == null) {
                continue;
            }
            JsonObject json = new JsonObject();
            json.addProperty("dimension", overview.getDimensionId());
            JsonArray routes = new JsonArray();
            for (RouteSummary summary : overview.getRoutes()) {
                routes.add(writeRouteSummary(summary));
            }
            json.add("routes", routes);

            JsonArray depots = new JsonArray();
            for (DepotInfo depot : overview.getDepots()) {
                depots.add(writeDepotInfo(depot));
            }
            json.add("depots", depots);

            JsonArray fareAreas = new JsonArray();
            for (FareAreaInfo info : overview.getFareAreas()) {
                fareAreas.add(writeFareAreaInfo(info));
            }
            json.add("fareAreas", fareAreas);
            array.add(json);
        }
        return array;
    }

    public static JsonObject writeRouteDetail(RouteDetail detail) {
        JsonObject json = new JsonObject();
        json.addProperty("dimension", detail.getDimensionId());
        json.addProperty("routeId", detail.getRouteId());
        json.addProperty("name", detail.getName());
        json.addProperty("color", detail.getColor());
        json.addProperty("routeType", detail.getRouteType());
        JsonArray nodes = new JsonArray();
        for (RouteNode node : detail.getNodes()) {
            nodes.add(writeRouteNode(node));
        }
        json.add("nodes", nodes);
        return json;
    }

    public static JsonArray writeRouteFinderSnapshots(List<MtrModels.RouteFinderSnapshot> snapshots) {
        JsonArray array = new JsonArray();
        if (snapshots == null) {
            return array;
        }
        for (MtrModels.RouteFinderSnapshot snapshot : snapshots) {
            JsonObject json = new JsonObject();
            json.addProperty("dimension", snapshot.getDimensionId());
            json.addProperty("routeId", snapshot.getRouteId());
            json.addProperty("name", snapshot.getRouteName());
            JsonArray nodes = new JsonArray();
            for (RouteNode node : snapshot.getNodes()) {
                nodes.add(writeRouteNode(node));
            }
            json.add("nodes", nodes);
            array.add(json);
        }
        return array;
    }

    public static JsonArray writeRouteFinderData(List<MtrModels.RouteFinderDataEntry> entries) {
        JsonArray array = new JsonArray();
        if (entries == null) {
            return array;
        }
        for (MtrModels.RouteFinderDataEntry entry : entries) {
            JsonObject json = new JsonObject();
            json.addProperty("dimension", entry.getDimensionId());
            json.addProperty("pos", entry.getPos());
            json.addProperty("routeId", entry.getRouteId());
            json.addProperty("duration", entry.getDuration());
            json.addProperty("waitingTime", entry.getWaitingTime());
            json.addProperty("source", entry.getSource());
            json.add("stationIds", writeLongList(entry.getStationIds()));
            array.add(json);
        }
        return array;
    }

    public static JsonArray writeRouteFinderEdges(List<MtrModels.RouteFinderEdge> edges) {
        JsonArray array = new JsonArray();
        if (edges == null) {
            return array;
        }
        for (MtrModels.RouteFinderEdge edge : edges) {
            JsonObject json = new JsonObject();
            json.addProperty("dimension", edge.getDimensionId());
            json.addProperty("routeId", edge.getRouteId());
            json.addProperty("source", edge.getSource());
            json.addProperty("index", edge.getIndex());
            json.addProperty("fromPos", edge.getFromPos());
            json.addProperty("toPos", edge.getToPos());
            edge.getConnectionDensity().ifPresent(value -> json.addProperty("connectionDensity", value));
            array.add(json);
        }
        return array;
    }

    public static JsonObject writeRouteFinderModuleState(Optional<MtrModels.RouteFinderModuleState> state) {
        JsonObject json = new JsonObject();
        if (state == null || !state.isPresent()) {
            return json;
        }
        MtrModels.RouteFinderModuleState value = state.get();
        json.addProperty("dimension", value.getDimensionId());
        value.getStartPos().ifPresent(v -> json.addProperty("startPos", v));
        value.getEndPos().ifPresent(v -> json.addProperty("endPos", v));
        value.getTotalTime().ifPresent(v -> json.addProperty("totalTime", v));
        value.getCount().ifPresent(v -> json.addProperty("count", v));
        value.getStartMillis().ifPresent(v -> json.addProperty("startMillis", v));
        value.getTickStage().ifPresent(v -> json.addProperty("tickStage", v));
        json.add("globalBlacklist", writeLongIntMap(value.getGlobalBlacklist()));
        json.add("localBlacklist", writeLongIntMap(value.getLocalBlacklist()));
        return json;
    }

    public static JsonArray writeConnectionProfiles(List<MtrModels.ConnectionProfile> profiles) {
        JsonArray array = new JsonArray();
        if (profiles == null) {
            return array;
        }
        for (MtrModels.ConnectionProfile profile : profiles) {
            JsonObject json = new JsonObject();
            json.addProperty("dimension", profile.getDimensionId());
            json.addProperty("fromPos", profile.getFromPos());
            json.addProperty("toPos", profile.getToPos());
            json.addProperty("platformStart", profile.getPlatformStartPos());
            profile.getShortestDuration().ifPresent(value -> json.addProperty("shortestDuration", value));
            json.add("durationInfo", writeLongIntMap(profile.getDurationInfo()));
            profile.getConnectionDensity().ifPresent(value -> json.addProperty("connectionDensity", value));
            array.add(json);
        }
        return array;
    }

    public static JsonArray writePlatformPositions(List<MtrModels.PlatformPosition> positions) {
        JsonArray array = new JsonArray();
        if (positions == null) {
            return array;
        }
        for (MtrModels.PlatformPosition position : positions) {
            JsonObject json = new JsonObject();
            json.addProperty("dimension", position.getDimensionId());
            json.addProperty("platformId", position.getPlatformId());
            json.addProperty("pos1", position.getPos1());
            json.addProperty("pos2", position.getPos2());
            json.addProperty("midPos", position.getMidPos());
            json.addProperty("platformStart", position.getPlatformStart());
            json.addProperty("platformEnd", position.getPlatformEnd());
            array.add(json);
        }
        return array;
    }

    public static JsonArray writeRailCurveSegments(List<MtrModels.RailCurveSegment> segments) {
        JsonArray array = new JsonArray();
        if (segments == null) {
            return array;
        }
        for (MtrModels.RailCurveSegment segment : segments) {
            JsonObject json = new JsonObject();
            json.addProperty("dimension", segment.getDimensionId());
            json.addProperty("fromPos", segment.getFromPos());
            json.addProperty("toPos", segment.getToPos());
            json.addProperty("railType", segment.getRailType());
            json.addProperty("transportMode", segment.getTransportMode());
            json.add("segment1", writeRailSegmentParams(segment.getSegment1()));
            json.add("segment2", writeRailSegmentParams(segment.getSegment2()));
            array.add(json);
        }
        return array;
    }

    public static JsonObject writeRoutefinderVersion(MtrModels.RoutefinderVersion version) {
        JsonObject json = new JsonObject();
        if (version == null) {
            return json;
        }
        json.addProperty("dimension", version.getDimensionId());
        if (version.getMtrVersion() != null) {
            json.addProperty("mtrVersion", version.getMtrVersion());
        }
        version.getRailwayDataVersion().ifPresent(value -> json.addProperty("railwayDataVersion", value));
        return json;
    }

    private static JsonObject writeRailSegmentParams(MtrModels.RailSegmentParams params) {
        JsonObject json = new JsonObject();
        if (params == null) {
            return json;
        }
        json.addProperty("h", params.getH());
        json.addProperty("k", params.getK());
        json.addProperty("r", params.getR());
        json.addProperty("tStart", params.getTStart());
        json.addProperty("tEnd", params.getTEnd());
        json.addProperty("reverse", params.isReverse());
        json.addProperty("straight", params.isStraight());
        json.addProperty("yStart", params.getYStart());
        json.addProperty("yEnd", params.getYEnd());
        return json;
    }

    private static JsonObject writeLongIntMap(Map<Long, Integer> map) {
        JsonObject json = new JsonObject();
        if (map == null || map.isEmpty()) {
            return json;
        }
        for (Map.Entry<Long, Integer> entry : map.entrySet()) {
            json.addProperty(Long.toString(entry.getKey()), entry.getValue());
        }
        return json;
    }

    private static JsonArray writeLongList(List<Long> values) {
        JsonArray array = new JsonArray();
        if (values == null) {
            return array;
        }
        for (Long value : values) {
            if (value != null) {
                array.add(value);
            }
        }
        return array;
    }

    public static JsonArray writeDepots(List<DepotInfo> depots) {
        JsonArray array = new JsonArray();
        if (depots != null) {
            for (DepotInfo depot : depots) {
                array.add(writeDepotInfo(depot));
            }
        }
        return array;
    }

    public static JsonArray writeStations(List<StationInfo> stations) {
        JsonArray array = new JsonArray();
        if (stations != null) {
            for (StationInfo station : stations) {
                array.add(writeStationInfo(station));
            }
        }
        return array;
    }

    public static JsonArray writeFareAreas(List<FareAreaInfo> fareAreas) {
        JsonArray array = new JsonArray();
        if (fareAreas != null) {
            for (FareAreaInfo info : fareAreas) {
                array.add(writeFareAreaInfo(info));
            }
        }
        return array;
    }

    public static JsonArray writeTrainStatuses(List<TrainStatus> statuses) {
        JsonArray array = new JsonArray();
        if (statuses != null) {
            for (TrainStatus status : statuses) {
                array.add(writeTrainStatus(status));
            }
        }
        return array;
    }

    public static JsonObject writeNodePage(NodePage page) {
        JsonObject json = new JsonObject();
        json.addProperty("dimension", page.getDimensionId());
        JsonArray nodes = new JsonArray();
        for (NodeInfo node : page.getNodes()) {
            nodes.add(writeNodeInfo(node));
        }
        json.add("nodes", nodes);
        page.getNextCursor().ifPresent(cursor -> json.addProperty("nextCursor", cursor));
        json.addProperty("hasMore", page.getNextCursor().isPresent());
        return json;
    }

    public static JsonObject writeStationTimetable(StationTimetable timetable) {
        JsonObject json = new JsonObject();
        json.addProperty("dimension", timetable.getDimensionId());
        json.addProperty("stationId", timetable.getStationId());
        JsonArray platforms = new JsonArray();
        for (PlatformTimetable platform : timetable.getPlatforms()) {
            platforms.add(writePlatformTimetable(platform));
        }
        json.add("platforms", platforms);
        return json;
    }

    private static JsonObject writeRouteSummary(RouteSummary summary) {
        JsonObject json = new JsonObject();
        json.addProperty("routeId", summary.getRouteId());
        json.addProperty("name", summary.getName());
        json.addProperty("color", summary.getColor());
        json.addProperty("transportMode", summary.getTransportMode());
        json.addProperty("routeType", summary.getRouteType());
        json.addProperty("hidden", summary.isHidden());
        JsonArray platforms = new JsonArray();
        for (PlatformSummary platform : summary.getPlatforms()) {
            platforms.add(writePlatformSummary(platform));
        }
        json.add("platforms", platforms);
        return json;
    }

    private static JsonObject writePlatformSummary(PlatformSummary summary) {
        JsonObject json = new JsonObject();
        json.addProperty("platformId", summary.getPlatformId());
        json.addProperty("stationId", summary.getStationId());
        json.addProperty("stationName", summary.getStationName());
        if (summary.getBounds() != null) {
            json.add("bounds", writeBounds(summary.getBounds()));
        }
        json.add("interchangeRouteIds", writeLongArray(summary.getInterchangeRouteIds()));
        return json;
    }

    private static JsonObject writeDepotInfo(DepotInfo depot) {
        JsonObject json = new JsonObject();
        json.addProperty("depotId", depot.getDepotId());
        json.addProperty("name", depot.getName());
        json.addProperty("transportMode", depot.getTransportMode());
        json.add("routeIds", writeLongArray(depot.getRouteIds()));
        json.add("departures", writeIntArray(depot.getDepartures()));
        json.addProperty("useRealTime", depot.isUseRealTime());
        json.addProperty("repeatInfinitely", depot.isRepeatInfinitely());
        json.addProperty("cruisingAltitude", depot.getCruisingAltitude());
        depot.getNextDepartureMillis().ifPresent(value -> json.addProperty("nextDepartureMillis", value));
        return json;
    }

    private static JsonObject writeFareAreaInfo(FareAreaInfo info) {
        JsonObject json = new JsonObject();
        json.addProperty("stationId", info.getStationId());
        json.addProperty("name", info.getName());
        json.addProperty("zone", info.getZone());
        if (info.getBounds() != null) {
            json.add("bounds", writeBounds(info.getBounds()));
        }
        json.add("interchangeRouteIds", writeLongArray(info.getInterchangeRouteIds()));
        return json;
    }

    private static JsonObject writeStationInfo(StationInfo info) {
        JsonObject json = new JsonObject();
        json.addProperty("dimension", info.getDimensionId());
        json.addProperty("stationId", info.getStationId());
        json.addProperty("name", info.getName());
        json.addProperty("zone", info.getZone());
        if (info.getBounds() != null) {
            json.add("bounds", writeBounds(info.getBounds()));
        }
        json.add("interchangeRouteIds", writeLongArray(info.getInterchangeRouteIds()));
        JsonArray platforms = new JsonArray();
        for (StationPlatformInfo platform : info.getPlatforms()) {
            platforms.add(writeStationPlatform(platform));
        }
        json.add("platforms", platforms);
        return json;
    }

    private static JsonObject writeStationPlatform(StationPlatformInfo platform) {
        JsonObject json = new JsonObject();
        json.addProperty("platformId", platform.getPlatformId());
        json.addProperty("name", platform.getPlatformName());
        json.add("routeIds", writeLongArray(platform.getRouteIds()));
        platform.getDepotId().ifPresent(id -> json.addProperty("depotId", id));
        return json;
    }

    private static JsonObject writeRouteNode(RouteNode node) {
        JsonObject json = writeNodeInfo(node.getNode());
        json.addProperty("segmentCategory", node.getSegmentCategory());
        json.addProperty("sequence", node.getSequence());
        return json;
    }

    private static JsonObject writeNodeInfo(NodeInfo node) {
        JsonObject json = new JsonObject();
        json.addProperty("x", node.getX());
        json.addProperty("y", node.getY());
        json.addProperty("z", node.getZ());
        json.addProperty("railType", node.getRailType());
        json.addProperty("platformSegment", node.isPlatformSegment());
        node.getStationId().ifPresent(id -> json.addProperty("stationId", id));
        return json;
    }

    private static JsonObject writePlatformTimetable(PlatformTimetable timetable) {
        JsonObject json = new JsonObject();
        json.addProperty("platformId", timetable.getPlatformId());
        JsonArray entries = new JsonArray();
        for (ScheduleEntry entry : timetable.getEntries()) {
            entries.add(writeScheduleEntry(entry));
        }
        json.add("entries", entries);
        return json;
    }

    private static JsonObject writeScheduleEntry(ScheduleEntry entry) {
        JsonObject json = new JsonObject();
        json.addProperty("routeId", entry.getRouteId());
        json.addProperty("arrivalMillis", entry.getArrivalMillis());
        json.addProperty("trainCars", entry.getTrainCars());
        json.addProperty("currentStationIndex", entry.getCurrentStationIndex());
        entry.getDelayMillis().ifPresent(delay -> json.addProperty("delayMillis", delay));
        return json;
    }

    private static JsonObject writeTrainStatus(TrainStatus status) {
        JsonObject json = new JsonObject();
        if (status.getTrainUuid() != null) {
            json.addProperty("trainUuid", status.getTrainUuid().toString());
        }
        json.addProperty("dimension", status.getDimensionId());
        json.addProperty("routeId", status.getRouteId());
        status.getDepotId().ifPresent(id -> json.addProperty("depotId", id));
        json.addProperty("transportMode", status.getTransportMode());
        status.getCurrentStationId().ifPresent(id -> json.addProperty("currentStationId", id));
        status.getNextStationId().ifPresent(id -> json.addProperty("nextStationId", id));
        status.getDelayMillis().ifPresent(delay -> json.addProperty("delayMillis", delay));
        json.addProperty("segmentCategory", status.getSegmentCategory());
        json.addProperty("progress", status.getProgress());
        status.getNode().ifPresent(node -> json.add("node", writeNodeInfo(node)));
        return json;
    }

    private static JsonObject writeBounds(Bounds bounds) {
        JsonObject json = new JsonObject();
        json.addProperty("minX", bounds.getMinX());
        json.addProperty("minY", bounds.getMinY());
        json.addProperty("minZ", bounds.getMinZ());
        json.addProperty("maxX", bounds.getMaxX());
        json.addProperty("maxY", bounds.getMaxY());
        json.addProperty("maxZ", bounds.getMaxZ());
        return json;
    }

    private static JsonArray writeLongArray(List<Long> values) {
        JsonArray array = new JsonArray();
        if (values != null) {
            for (Long value : values) {
                if (value != null) {
                    array.add(value);
                }
            }
        }
        return array;
    }

    private static JsonArray writeIntArray(List<Integer> values) {
        JsonArray array = new JsonArray();
        if (values != null) {
            for (Integer value : values) {
                if (value != null) {
                    array.add(value);
                }
            }
        }
        return array;
    }
}
