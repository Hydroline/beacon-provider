package com.hydroline.beacon.provider.service.mtr;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.hydroline.beacon.provider.mtr.MtrDimensionSnapshot;
import com.hydroline.beacon.provider.mtr.MtrJsonWriter;
import com.hydroline.beacon.provider.mtr.MtrModels.DimensionOverview;
import com.hydroline.beacon.provider.mtr.MtrModels.PlatformTimetable;
import com.hydroline.beacon.provider.mtr.MtrModels.RouteSummary;
import com.hydroline.beacon.provider.mtr.MtrModels.ScheduleEntry;
import com.hydroline.beacon.provider.mtr.MtrModels.StationInfo;
import com.hydroline.beacon.provider.mtr.MtrModels.StationPlatformInfo;
import com.hydroline.beacon.provider.mtr.MtrModels.StationTimetable;
import com.hydroline.beacon.provider.mtr.MtrQueryGateway;
import com.hydroline.beacon.provider.protocol.BeaconMessage;
import com.hydroline.beacon.provider.protocol.BeaconResponse;
import com.hydroline.beacon.provider.transport.TransportContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class MtrGetStationScheduleActionHandler extends AbstractMtrActionHandler {
    public static final String ACTION = "mtr:get_station_schedule";

    @Override
    public String action() {
        return ACTION;
    }

    @Override
    public BeaconResponse handle(BeaconMessage message, TransportContext context) {
        MtrQueryGateway gateway = gateway();
        if (!gateway.isReady()) {
            return notReady(message.getRequestId());
        }
        JsonObject payload = message.getPayload();
        if (payload == null || !payload.has("stationId")) {
            return invalidPayload(message.getRequestId(), "stationId is required");
        }
        long stationId = payload.get("stationId").getAsLong();
        String dimension = payload.has("dimension") ? payload.get("dimension").getAsString() : null;
        Long platformId = payload.has("platformId") ? payload.get("platformId").getAsLong() : null;

        List<MtrDimensionSnapshot> snapshots = gateway.fetchSnapshots();
        Set<String> targetDimensions = collectTargetDimensions(dimension, snapshots, gateway.fetchNetworkOverview());
        if (targetDimensions.isEmpty()) {
            return invalidPayload(message.getRequestId(), "no registered dimensions");
        }

        List<DimensionOverview> overviews = gateway.fetchNetworkOverview();
        Map<String, Map<Long, String>> routeNamesByDimension = buildRouteNameIndex(overviews);
        Map<String, Map<Long, String>> platformNamesByDimension = buildPlatformNameIndex(gateway.fetchStations(null));

        JsonArray timetablesArray = new JsonArray();
        for (String dimId : targetDimensions) {
            Optional<StationTimetable> optional = gateway.fetchStationTimetable(dimId, stationId, platformId);
            if (!optional.isPresent()) {
                continue;
            }
            StationTimetable timetable = optional.get();
            JsonObject entry = new JsonObject();
            entry.addProperty("dimension", dimId);
            entry.add("platforms", writePlatformSchedules(
                timetable.getPlatforms(),
                platformNamesByDimension.getOrDefault(dimId, Collections.emptyMap()),
                routeNamesByDimension.getOrDefault(dimId, Collections.emptyMap())
            ));
            timetablesArray.add(entry);
        }
        if (timetablesArray.size() == 0) {
            return invalidPayload(message.getRequestId(), "station timetable unavailable");
        }

        JsonObject responsePayload = new JsonObject();
        responsePayload.addProperty("timestamp", System.currentTimeMillis());
        responsePayload.addProperty("stationId", stationId);
        if (dimension != null && !dimension.isEmpty()) {
            responsePayload.addProperty("dimension", dimension);
        }
        responsePayload.add("timetables", timetablesArray);
        return ok(message.getRequestId(), responsePayload);
    }

    private static Set<String> collectTargetDimensions(String requestedDimension,
            List<MtrDimensionSnapshot> snapshots,
            List<DimensionOverview> overviews) {
        Set<String> targets = new LinkedHashSet<>();
        if (requestedDimension != null && !requestedDimension.isEmpty()) {
            targets.add(requestedDimension);
            return targets;
        }
        if (snapshots != null) {
            for (MtrDimensionSnapshot snapshot : snapshots) {
                if (snapshot != null) {
                    targets.add(snapshot.getDimensionId());
                }
            }
        }
        if (targets.isEmpty() && overviews != null) {
            for (DimensionOverview overview : overviews) {
                targets.add(overview.getDimensionId());
            }
        }
        return targets;
    }

    private static Map<String, Map<Long, String>> buildRouteNameIndex(List<DimensionOverview> overviews) {
        Map<String, Map<Long, String>> index = new HashMap<>();
        if (overviews == null) {
            return index;
        }
        for (DimensionOverview overview : overviews) {
            Map<Long, String> map = index.computeIfAbsent(overview.getDimensionId(), key -> new HashMap<>());
            if (overview.getRoutes() == null) {
                continue;
            }
            for (RouteSummary route : overview.getRoutes()) {
                map.putIfAbsent(route.getRouteId(), route.getName());
            }
        }
        return index;
    }

    private static Map<String, Map<Long, String>> buildPlatformNameIndex(List<StationInfo> stations) {
        Map<String, Map<Long, String>> index = new HashMap<>();
        if (stations == null) {
            return index;
        }
        for (StationInfo station : stations) {
            if (station == null) {
                continue;
            }
            Map<Long, String> names = index.computeIfAbsent(station.getDimensionId(), key -> new HashMap<>());
            for (StationPlatformInfo platform : station.getPlatforms()) {
                if (platform == null || platform.getPlatformName() == null || platform.getPlatformName().isEmpty()) {
                    continue;
                }
                names.putIfAbsent(platform.getPlatformId(), platform.getPlatformName());
            }
        }
        return index;
    }

    private static JsonArray writePlatformSchedules(List<PlatformTimetable> platforms,
            Map<Long, String> platformNames,
            Map<Long, String> routeNames) {
        JsonArray array = new JsonArray();
        if (platforms == null || platforms.isEmpty()) {
            return array;
        }
        for (PlatformTimetable platform : platforms) {
            JsonObject platformJson = new JsonObject();
            platformJson.addProperty("platformId", platform.getPlatformId());
            String platformName = platformNames.get(platform.getPlatformId());
            if (platformName != null && !platformName.isEmpty()) {
                platformJson.addProperty("platformName", platformName);
            }
            JsonArray entries = new JsonArray();
            for (ScheduleEntry entry : platform.getEntries()) {
                entries.add(MtrJsonWriter.writeScheduleEntry(entry, routeNames));
            }
            platformJson.add("entries", entries);
            array.add(platformJson);
        }
        return array;
    }
}
