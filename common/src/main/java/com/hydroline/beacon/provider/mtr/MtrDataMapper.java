package com.hydroline.beacon.provider.mtr;

import com.hydroline.beacon.provider.mtr.MtrModels;
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
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import mtr.data.AreaBase;
import mtr.data.DataCache;
import mtr.data.Depot;
import mtr.data.Platform;
import mtr.data.Rail;
import mtr.data.RailType;
import mtr.data.RailwayData;
import mtr.data.RailwayDataRouteFinderModule;
import mtr.data.Route;
import mtr.data.Route.RoutePlatform;
import mtr.data.SavedRailBase;
import mtr.data.Siding;
import mtr.data.Station;
import mtr.data.Train;
import mtr.data.TrainServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper utilities that convert live MTR data into the DTOs defined under {@link MtrModels}.
 */
public final class MtrDataMapper {
    private static final Logger LOGGER = LoggerFactory.getLogger(MtrDataMapper.class);
    private static final Method SAVED_RAIL_GET_MID_POS = locateMethod(SavedRailBase.class, "getMidPos");
    private static final Method AREA_BASE_GET_CENTER = locateMethod(AreaBase.class, "getCenter");
    private static final Map<Class<?>, Method> AS_LONG_METHOD_CACHE = new ConcurrentHashMap<>();
    private static final Field AREA_CORNER1 = locateField(AreaBase.class, "corner1");
    private static final Field AREA_CORNER2 = locateField(AreaBase.class, "corner2");
    private static final Field DATA_CACHE_BLOCK_POS_TO_STATION = locateField(DataCache.class, "blockPosToStation");
    private static final Field SIDING_TRAINS = locateField(Siding.class, "trains");
    private static final Field SIDING_DEPOT = locateField(Siding.class, "depot");
    private static final Field TRAIN_SERVER_ROUTE_ID = locateField(TrainServer.class, "routeId");
    private static final Field TRAIN_NEXT_STOPPING_INDEX = locateField(Train.class, "nextStoppingIndex");
    private static final Field ROUTE_FINDER_CONNECTION_DENSITY = locateField(RailwayDataRouteFinderModule.class, "connectionDensity");
    private static final Field RAILWAY_DATA_VERSION = locateField(RailwayData.class, "DATA_VERSION");
    private static final Field CONNECTION_DETAILS_SHORT_DURATION = locateField(RailwayDataRouteFinderModule.ConnectionDetails.class, "shortestDuration");
    private static final Field CONNECTION_DETAILS_DURATION_INFO = locateField(RailwayDataRouteFinderModule.ConnectionDetails.class, "durationInfo");
    private static final Field CONNECTION_DETAILS_PLATFORM_START = locateField(RailwayDataRouteFinderModule.ConnectionDetails.class, "platformStart");
    private static final Method SAVED_RAIL_GET_POSITION = locateMethod(SavedRailBase.class, "getPosition");
    private static final Field RAIL_H1 = locateField(Rail.class, "h1");
    private static final Field RAIL_K1 = locateField(Rail.class, "k1");
    private static final Field RAIL_R1 = locateField(Rail.class, "r1");
    private static final Field RAIL_T_START_1 = locateField(Rail.class, "tStart1");
    private static final Field RAIL_T_END_1 = locateField(Rail.class, "tEnd1");
    private static final Field RAIL_IS_STRAIGHT_1 = locateField(Rail.class, "isStraight1");
    private static final Field RAIL_REVERSE_T_1 = locateField(Rail.class, "reverseT1");
    private static final Field RAIL_H2 = locateField(Rail.class, "h2");
    private static final Field RAIL_K2 = locateField(Rail.class, "k2");
    private static final Field RAIL_R2 = locateField(Rail.class, "r2");
    private static final Field RAIL_T_START_2 = locateField(Rail.class, "tStart2");
    private static final Field RAIL_T_END_2 = locateField(Rail.class, "tEnd2");
    private static final Field RAIL_IS_STRAIGHT_2 = locateField(Rail.class, "isStraight2");
    private static final Field RAIL_REVERSE_T_2 = locateField(Rail.class, "reverseT2");
    private static final Field RAIL_Y_START = locateField(Rail.class, "yStart");
    private static final Field RAIL_Y_END = locateField(Rail.class, "yEnd");
    private static final Field RAIL_TRANSPORT_MODE = locateField(Rail.class, "transportMode");
    private static final Field RAIL_RAIL_TYPE = locateField(Rail.class, "railType");
    private static final Field DATA_CACHE_PLATFORM_CONNECTIONS = locateField(DataCache.class, "platformConnections");
    private static final Field ROUTE_FINDER_DATA_FIELD = locateField(RailwayDataRouteFinderModule.class, "data");
    private static final Field ROUTE_FINDER_TEMP_DATA_FIELD = locateField(RailwayDataRouteFinderModule.class, "tempData");
    private static final Field ROUTE_FINDER_START_POS = locateField(RailwayDataRouteFinderModule.class, "startPos");
    private static final Field ROUTE_FINDER_END_POS = locateField(RailwayDataRouteFinderModule.class, "endPos");
    private static final Field ROUTE_FINDER_TOTAL_TIME = locateField(RailwayDataRouteFinderModule.class, "totalTime");
    private static final Field ROUTE_FINDER_COUNT = locateField(RailwayDataRouteFinderModule.class, "count");
    private static final Field ROUTE_FINDER_START_MILLIS = locateField(RailwayDataRouteFinderModule.class, "startMillis");
    private static final Field ROUTE_FINDER_TICK_STAGE = locateField(RailwayDataRouteFinderModule.class, "tickStage");
    private static final Field ROUTE_FINDER_GLOBAL_BLACKLIST = locateField(RailwayDataRouteFinderModule.class, "globalBlacklist");
    private static final Field ROUTE_FINDER_LOCAL_BLACKLIST = locateField(RailwayDataRouteFinderModule.class, "localBlacklist");
    private static final Field ROUTE_FINDER_DATA_POS = locateField(RailwayDataRouteFinderModule.RouteFinderData.class, "pos");
    private static final Field ROUTE_FINDER_DATA_DURATION = locateField(RailwayDataRouteFinderModule.RouteFinderData.class, "duration");
    private static final Field ROUTE_FINDER_DATA_ROUTE_ID = locateField(RailwayDataRouteFinderModule.RouteFinderData.class, "routeId");
    private static final Field ROUTE_FINDER_DATA_WAITING_TIME = locateField(RailwayDataRouteFinderModule.RouteFinderData.class, "waitingTime");
    private static final Field ROUTE_FINDER_DATA_STATION_IDS = locateField(RailwayDataRouteFinderModule.RouteFinderData.class, "stationIds");

    private MtrDataMapper() {
    }

    public static List<DimensionOverview> buildNetworkOverview(List<MtrDimensionSnapshot> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) {
            return Collections.emptyList();
        }
        List<DimensionOverview> results = new ArrayList<>(snapshots.size());
        for (MtrDimensionSnapshot snapshot : snapshots) {
            DimensionContext context = DimensionContext.from(snapshot);
            if (context == null) {
                continue;
            }
            results.add(new DimensionOverview(
                context.dimensionId,
                buildRouteSummaries(context),
                buildDepotInfos(context),
                buildFareAreaInfos(context)
            ));
        }
        return results;
    }

    public static Optional<RouteDetail> buildRouteDetail(MtrDimensionSnapshot snapshot, long routeId) {
        DimensionContext context = DimensionContext.from(snapshot);
        if (context == null) {
            return Optional.empty();
        }
        return context.routes.stream()
            .filter(route -> route.id == routeId)
            .findFirst()
            .map(route -> new RouteDetail(
                context.dimensionId,
                route.id,
                safeName(route.name),
                route.color,
                describeRouteType(route),
                buildRouteNodes(context, route)
            ));
    }

    public static List<DepotInfo> buildDepots(MtrDimensionSnapshot snapshot) {
        DimensionContext context = DimensionContext.from(snapshot);
        if (context == null) {
            return Collections.emptyList();
        }
        return buildDepotInfos(context);
    }

    public static List<FareAreaInfo> buildFareAreas(MtrDimensionSnapshot snapshot) {
        DimensionContext context = DimensionContext.from(snapshot);
        if (context == null) {
            return Collections.emptyList();
        }
        return buildFareAreaInfos(context);
    }

    public static List<StationInfo> buildStations(MtrDimensionSnapshot snapshot) {
        DimensionContext context = DimensionContext.from(snapshot);
        if (context == null) {
            return Collections.emptyList();
        }
        List<StationInfo> stations = new ArrayList<>(context.stations.size());
        context.stations.values().stream()
            .sorted(Comparator.comparingLong(station -> station.id))
            .forEach(station -> stations.add(new StationInfo(
                context.dimensionId,
                station.id,
                safeName(station.name),
                station.zone,
                buildBoundsFromArea(station),
                toSortedList(context.stationRouteIds.get(station.id)),
                buildStationPlatforms(context, station)
            )));
        return stations;
    }

    public static List<MtrModels.RouteFinderSnapshot> buildRouteFinderSnapshots(MtrDimensionSnapshot snapshot) {
        DimensionContext context = DimensionContext.from(snapshot);
        if (context == null || context.routes.isEmpty()) {
            return Collections.emptyList();
        }
        List<MtrModels.RouteFinderSnapshot> result = new ArrayList<>();
        for (Route route : context.routes) {
            List<RouteNode> nodes = buildRouteNodes(context, route);
            if (!nodes.isEmpty()) {
                result.add(new MtrModels.RouteFinderSnapshot(
                    context.dimensionId,
                    route.id,
                    safeName(route.name),
                    nodes
                ));
            }
        }
        return result;
    }

    public static List<MtrModels.ConnectionProfile> buildConnectionProfiles(MtrDimensionSnapshot snapshot) {
        DimensionContext context = DimensionContext.from(snapshot);
        if (context == null || context.cache == null || context.cache.platformConnections == null) {
            return Collections.emptyList();
        }
        List<MtrModels.ConnectionProfile> result = new ArrayList<>();
        Object rawConnections = readPlatformConnections(context.cache);
        if (!(rawConnections instanceof Map)) {
            return Collections.emptyList();
        }
        Map<?, ?> connections = (Map<?, ?>) rawConnections;
        for (Map.Entry<?, ?> entry : connections.entrySet()) {
            long fromPos = toLong(entry.getKey());
            Object rawTargets = entry.getValue();
            if (!(rawTargets instanceof Map)) {
                continue;
            }
            Map<?, ?> targets = (Map<?, ?>) rawTargets;
            for (Map.Entry<?, ?> targetEntry : targets.entrySet()) {
                long toPos = toLong(targetEntry.getKey());
                Object details = targetEntry.getValue();
                long platformStart = extractPlatformStart(details);
                Integer shortestDuration = extractShortestDuration(details);
                Map<Long, Integer> durationInfo = extractDurationInfo(details);
                Integer density = context.routeFinderModule != null
                    ? extractConnectionDensity(context.routeFinderModule, fromPos, toPos)
                    : null;
                result.add(new MtrModels.ConnectionProfile(
                    context.dimensionId,
                    fromPos,
                    toPos,
                    platformStart,
                    shortestDuration,
                    durationInfo,
                    density
                ));
            }
        }
        return result;
    }

    public static List<MtrModels.PlatformPosition> buildPlatformPositions(MtrDimensionSnapshot snapshot) {
        DimensionContext context = DimensionContext.from(snapshot);
        if (context == null || context.platforms.isEmpty()) {
            return Collections.emptyList();
        }
        List<MtrModels.PlatformPosition> result = new ArrayList<>(context.platforms.size());
        context.platforms.values().stream()
            .sorted(Comparator.comparingLong(platform -> platform.id))
            .forEach(platform -> {
                Object firstPos = invokeSavedRailPosition(platform, 0);
                Object secondPos = invokeSavedRailPosition(platform, 1);
                Object midPos = invokeSavedRailMidPos(platform);
                result.add(new MtrModels.PlatformPosition(
                    context.dimensionId,
                    platform.id,
                    toBlockPosLong(firstPos),
                    toBlockPosLong(secondPos),
                    toBlockPosLong(midPos),
                    toBlockPosLong(firstPos),
                    toBlockPosLong(secondPos)
                ));
            });
        return result;
    }

    public static List<MtrModels.RailCurveSegment> buildRailCurveSegments(MtrDimensionSnapshot snapshot) {
        DimensionContext context = DimensionContext.from(snapshot);
        if (context == null) {
            return Collections.emptyList();
        }
        Map<Object, Map<Object, Rail>> rails = RailsFieldAccessor.get(context.railwayData);
        if (rails.isEmpty()) {
            return Collections.emptyList();
        }
        List<MtrModels.RailCurveSegment> result = new ArrayList<>();
        for (Map.Entry<Object, Map<Object, Rail>> outer : rails.entrySet()) {
            long from = encodeBlockPos(outer.getKey());
            Map<Object, Rail> inner = outer.getValue();
            if (inner == null) {
                continue;
            }
            for (Map.Entry<Object, Rail> entry : inner.entrySet()) {
                long to = encodeBlockPos(entry.getKey());
                Rail rail = entry.getValue();
                if (rail == null) {
                    continue;
                }
                result.add(new MtrModels.RailCurveSegment(
                    context.dimensionId,
                    from,
                    to,
                    readRailType(rail),
                    readTransportMode(rail),
                    buildRailSegmentParams(rail, 1),
                    buildRailSegmentParams(rail, 2)
                ));
            }
        }
        return result;
    }

    public static Optional<MtrModels.RoutefinderVersion> buildRoutefinderVersion(MtrDimensionSnapshot snapshot) {
        DimensionContext context = DimensionContext.from(snapshot);
        if (context == null) {
            return Optional.empty();
        }
        return Optional.of(new MtrModels.RoutefinderVersion(
            context.dimensionId,
            context.mtrVersion,
            context.railwayDataVersion
        ));
    }

    public static List<MtrModels.RouteFinderDataEntry> buildRouteFinderData(MtrDimensionSnapshot snapshot) {
        DimensionContext context = DimensionContext.from(snapshot);
        if (context == null || context.routeFinderModule == null) {
            return Collections.emptyList();
        }
        List<MtrModels.RouteFinderDataEntry> result = new ArrayList<>();
        collectRouteFinderData(result, context.dimensionId, context.routeFinderModule, ROUTE_FINDER_DATA_FIELD, "data");
        collectRouteFinderData(result, context.dimensionId, context.routeFinderModule, ROUTE_FINDER_TEMP_DATA_FIELD, "temp");
        return result;
    }

    public static Optional<MtrModels.RouteFinderModuleState> buildRouteFinderModuleState(MtrDimensionSnapshot snapshot) {
        DimensionContext context = DimensionContext.from(snapshot);
        if (context == null || context.routeFinderModule == null) {
            return Optional.empty();
        }
        RailwayDataRouteFinderModule module = context.routeFinderModule;
        return Optional.of(new MtrModels.RouteFinderModuleState(
            context.dimensionId,
            toBlockPosLong(readField(module, ROUTE_FINDER_START_POS)),
            toBlockPosLong(readField(module, ROUTE_FINDER_END_POS)),
            readIntegerField(module, ROUTE_FINDER_TOTAL_TIME),
            readIntegerField(module, ROUTE_FINDER_COUNT),
            readLongField(module, ROUTE_FINDER_START_MILLIS),
            readEnumName(module, ROUTE_FINDER_TICK_STAGE),
            toLongIntMap(readField(module, ROUTE_FINDER_GLOBAL_BLACKLIST)),
            toLongIntMap(readField(module, ROUTE_FINDER_LOCAL_BLACKLIST))
        ));
    }

    public static List<MtrModels.RouteFinderEdge> buildRouteFinderEdges(MtrDimensionSnapshot snapshot) {
        DimensionContext context = DimensionContext.from(snapshot);
        if (context == null) {
            return Collections.emptyList();
        }
        List<MtrModels.RouteFinderDataEntry> entries = buildRouteFinderData(snapshot);
        if (entries.isEmpty()) {
            return Collections.emptyList();
        }
        List<MtrModels.RouteFinderEdge> result = new ArrayList<>();
        int index = 0;
        for (int i = 0; i < entries.size() - 1; i++) {
            MtrModels.RouteFinderDataEntry current = entries.get(i);
            MtrModels.RouteFinderDataEntry next = entries.get(i + 1);
            if (current == null || next == null) {
                continue;
            }
            if (current.getRouteId() != next.getRouteId() || !Objects.equals(current.getSource(), next.getSource())) {
                continue;
            }
            long from = current.getPos();
            long to = next.getPos();
            Integer density = context.routeFinderModule != null
                ? extractConnectionDensity(context.routeFinderModule, from, to)
                : null;
            result.add(new MtrModels.RouteFinderEdge(
                context.dimensionId,
                current.getRouteId(),
                current.getSource(),
                index++,
                from,
                to,
                density
            ));
        }
        return result;
    }

    public static NodePage buildNodePage(MtrDimensionSnapshot snapshot, String cursor, int limit) {
        DimensionContext context = DimensionContext.from(snapshot);
        if (context == null) {
            return new NodePage(snapshot.getDimensionId(), Collections.emptyList(), null);
        }
        List<NodeInfo> allNodes = collectNodes(context);
        if (allNodes.isEmpty()) {
            return new NodePage(context.dimensionId, Collections.emptyList(), null);
        }
        int safeLimit = Math.max(1, limit);
        int offset = parseCursor(cursor);
        if (offset >= allNodes.size()) {
            return new NodePage(context.dimensionId, Collections.emptyList(), null);
        }
        int end = Math.min(allNodes.size(), offset + safeLimit);
        List<NodeInfo> slice = new ArrayList<>(allNodes.subList(offset, end));
        String nextCursor = end < allNodes.size() ? Integer.toString(end) : null;
        return new NodePage(context.dimensionId, slice, nextCursor);
    }

    public static Optional<StationTimetable> buildStationTimetable(MtrDimensionSnapshot snapshot, long stationId, Long platformId) {
        DimensionContext context = DimensionContext.from(snapshot);
        if (context == null || !context.stations.containsKey(stationId)) {
            return Optional.empty();
        }
        Map<Long, List<mtr.data.ScheduleEntry>> scheduleMap = new HashMap<>();
        context.railwayData.getSchedulesForStation(scheduleMap, stationId);
        if (scheduleMap.isEmpty()) {
            return Optional.empty();
        }
        List<PlatformTimetable> platforms = scheduleMap.entrySet().stream()
            .filter(entry -> platformId == null || Objects.equals(entry.getKey(), platformId))
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> new PlatformTimetable(entry.getKey(), toScheduleEntries(entry.getValue())))
            .collect(Collectors.toList());
        if (platforms.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new StationTimetable(context.dimensionId, stationId, platforms));
    }

    public static List<TrainStatus> buildRouteTrains(MtrDimensionSnapshot snapshot, long routeId) {
        DimensionContext context = DimensionContext.from(snapshot);
        if (context == null) {
            return Collections.emptyList();
        }
        List<TrainStatus> trains = collectTrainStatuses(context);
        if (routeId == 0 || trains.isEmpty()) {
            return trains;
        }
        return trains.stream()
            .filter(status -> status.getRouteId() == routeId)
            .collect(Collectors.toList());
    }

    public static List<TrainStatus> buildDepotTrains(MtrDimensionSnapshot snapshot, long depotId) {
        DimensionContext context = DimensionContext.from(snapshot);
        if (context == null) {
            return Collections.emptyList();
        }
        List<TrainStatus> trains = collectTrainStatuses(context);
        if (depotId == 0 || trains.isEmpty()) {
            return trains;
        }
        return trains.stream()
            .filter(status -> status.getDepotId().map(id -> id == depotId).orElse(false))
            .collect(Collectors.toList());
    }

    private static List<RouteSummary> buildRouteSummaries(DimensionContext context) {
        if (context.routes.isEmpty()) {
            return Collections.emptyList();
        }
        List<RouteSummary> summaries = new ArrayList<>(context.routes.size());
        context.routes.stream()
            .sorted(Comparator.comparingLong(route -> route.id))
            .forEach(route -> summaries.add(new RouteSummary(
                route.id,
                safeName(route.name),
                route.color,
                describeTransportMode(route.transportMode),
                describeRouteType(route),
                route.isHidden,
                buildPlatformSummaries(context, route)
            )));
        return summaries;
    }

    private static List<DepotInfo> buildDepotInfos(DimensionContext context) {
        if (context.depots.isEmpty()) {
            return Collections.emptyList();
        }
        List<DepotInfo> depots = new ArrayList<>(context.depots.size());
        context.depots.stream()
            .sorted(Comparator.comparingLong(depot -> depot.id))
            .forEach(depot -> depots.add(new DepotInfo(
                depot.id,
                safeName(depot.name),
                describeTransportMode(depot.transportMode),
                copyLongList(depot.routeIds),
                copyIntegerList(depot.departures),
                depot.useRealTime,
                depot.repeatInfinitely,
                depot.cruisingAltitude,
                depot.getNextDepartureMillis()
            )));
        return depots;
    }

    private static List<FareAreaInfo> buildFareAreaInfos(DimensionContext context) {
        if (context.stations.isEmpty()) {
            return Collections.emptyList();
        }
        List<FareAreaInfo> results = new ArrayList<>(context.stations.size());
        context.stations.values().stream()
            .sorted(Comparator.comparingLong(station -> station.id))
            .forEach(station -> results.add(new FareAreaInfo(
                station.id,
                safeName(station.name),
                station.zone,
                buildBoundsFromArea(station),
                toSortedList(context.stationRouteIds.get(station.id))
            )));
        return results;
    }

    private static List<RouteNode> buildRouteNodes(DimensionContext context, Route route) {
        if (route.platformIds == null || route.platformIds.isEmpty()) {
            return buildStationRouteNodes(context, route);
        }
        List<RouteNode> nodes = new ArrayList<>(route.platformIds.size());
        long sequence = 0;
        for (Route.RoutePlatform reference : route.platformIds) {
            Platform platform = context.platforms.get(reference.platformId);
            if (platform == null) {
                continue;
            }
            Station station = context.platformToStation.get(reference.platformId);
            NodeInfo nodeInfo = buildNodeInfoFromSavedRail(platform, station != null ? station.id : null, true);
            if (nodeInfo != null) {
                nodes.add(new RouteNode(nodeInfo, "PLATFORM", sequence++));
            }
        }
        List<Long> stationOrder = context.routeStationOrder.get(route.id);
        if ((stationOrder != null && stationOrder.size() > nodes.size()) || nodes.size() <= 2) {
            List<RouteNode> fallback = buildStationRouteNodes(context, route);
            if (!fallback.isEmpty()) {
                return fallback;
            }
        }
        return nodes.isEmpty() ? buildStationRouteNodes(context, route) : nodes;
    }

    private static List<RouteNode> buildStationRouteNodes(DimensionContext context, Route route) {
        List<Long> stationOrder = context.routeStationOrder.get(route.id);
        if (stationOrder == null || stationOrder.isEmpty()) {
            return Collections.emptyList();
        }
        List<RouteNode> nodes = new ArrayList<>(stationOrder.size());
        long sequence = 0;
        for (Long stationId : stationOrder) {
            Station station = context.stations.get(stationId);
            if (station == null) {
                continue;
            }
            NodeInfo nodeInfo = buildNodeInfoFromStation(station);
            if (nodeInfo != null) {
                nodes.add(new RouteNode(nodeInfo, "STATION", sequence++));
            }
        }
        return nodes;
    }

    private static List<PlatformSummary> buildPlatformSummaries(DimensionContext context, Route route) {
        if (route.platformIds == null || route.platformIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<PlatformSummary> summaries = new ArrayList<>(route.platformIds.size());
        for (Route.RoutePlatform reference : route.platformIds) {
            Platform platform = context.platforms.get(reference.platformId);
            if (platform == null) {
                continue;
            }
            Station station = context.platformToStation.get(reference.platformId);
            Bounds bounds = buildBoundsFromSavedRail(platform);
            List<Long> interchangeRoutes = station != null
                ? toSortedList(context.stationRouteIds.get(station.id))
                : Collections.emptyList();
            summaries.add(new PlatformSummary(
                platform.id,
                station != null ? station.id : 0L,
                station != null ? safeName(station.name) : "",
                bounds,
                interchangeRoutes
            ));
        }
        return summaries;
    }

    private static List<TrainStatus> collectTrainStatuses(DimensionContext context) {
        if (context.sidings.isEmpty()) {
            return Collections.emptyList();
        }
        List<TrainStatus> statuses = new ArrayList<>();
        for (Siding siding : context.sidings) {
            Collection<TrainServer> sidingTrains = readTrainServers(siding);
            if (sidingTrains.isEmpty()) {
                continue;
            }
            Long depotId = resolveDepotId(siding);
            for (TrainServer train : sidingTrains) {
                TrainStatus status = toTrainStatus(context, train, depotId);
                if (status != null) {
                    statuses.add(status);
                }
            }
        }
        return statuses;
    }

    @SuppressWarnings("unchecked")
    private static Collection<TrainServer> readTrainServers(Siding siding) {
        Object value = readField(SIDING_TRAINS, siding);
        if (value instanceof Collection<?>) {
            Collection<?> collection = (Collection<?>) value;
            if (collection.isEmpty()) {
                return Collections.emptyList();
            }
            List<TrainServer> trains = new ArrayList<>(collection.size());
            for (Object element : collection) {
                if (element instanceof TrainServer) {
                    trains.add((TrainServer) element);
                }
            }
            return trains;
        }
        return Collections.emptyList();
    }

    private static Long resolveDepotId(Siding siding) {
        Object value = readField(SIDING_DEPOT, siding);
        if (value instanceof Depot) {
            return ((Depot) value).id;
        }
        return null;
    }

    private static TrainStatus toTrainStatus(DimensionContext context, TrainServer train, Long depotId) {
        long routeId = readLong(TRAIN_SERVER_ROUTE_ID, train);
        if (routeId == 0) {
            return null;
        }
        List<Long> stationOrder = context.routeStationOrder.get(routeId);
        int nextStopIndex = readInt(TRAIN_NEXT_STOPPING_INDEX, train, -1);
        Long nextStationId = resolveStationFromIndex(stationOrder, nextStopIndex);
        Long currentStationId = resolveStationFromIndex(stationOrder, nextStopIndex - 1);
        String segmentCategory = train.getIsOnRoute() ? "ROUTE" : "DEPOT";
        double progress = normalizeRailProgress(train);
        UUID uuid = resolveTrainUuid(context.dimensionId, train);
        return new TrainStatus(
            context.dimensionId,
            uuid,
            routeId,
            depotId,
            describeTransportMode(train.transportMode),
            currentStationId,
            nextStationId,
            null,
            segmentCategory,
            progress,
            null
        );
    }

    private static Long resolveStationFromIndex(List<Long> stations, int index) {
        if (stations == null || stations.isEmpty() || index < 0 || index >= stations.size()) {
            return null;
        }
        return stations.get(index);
    }

    private static double normalizeRailProgress(Train train) {
        double progress = train.getRailProgress();
        List<?> path = train.path;
        if (path != null && !path.isEmpty()) {
            double normalized = progress / path.size();
            return Math.max(0D, Math.min(1D, normalized));
        }
        return 0D;
    }

    private static UUID resolveTrainUuid(String dimensionId, Train train) {
        String key = train.trainId != null && !train.trainId.isEmpty()
            ? train.trainId
            : Long.toString(train.id);
        String combined = dimensionId + ":" + key;
        return UUID.nameUUIDFromBytes(combined.getBytes(StandardCharsets.UTF_8));
    }

    private static List<StationPlatformInfo> buildStationPlatforms(DimensionContext context, Station station) {
        if (context.platforms.isEmpty()) {
            return Collections.emptyList();
        }
        List<StationPlatformInfo> platforms = new ArrayList<>();
        context.platforms.values().stream()
            .filter(platform -> {
                Station mapped = context.platformToStation.get(platform.id);
                return mapped != null && mapped.id == station.id;
            })
            .sorted(Comparator.comparingLong(platform -> platform.id))
            .forEach(platform -> platforms.add(new StationPlatformInfo(
                platform.id,
                safeName(platform.name),
                toSortedList(context.platformRouteIds.get(platform.id)),
                context.platformDepotIds.get(platform.id)
            )));
        return platforms;
    }

    private static List<NodeInfo> collectNodes(DimensionContext context) {
        Map<Object, Map<Object, Rail>> rails = RailsFieldAccessor.get(context.railwayData);
        if (rails.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, NodeAccumulator> accumulators = new HashMap<>();
        rails.forEach((startPos, edges) -> {
            NodeAccumulator start = NodeAccumulator.obtain(accumulators, startPos, context);
            if (edges != null) {
                edges.forEach((endPos, rail) -> {
                    if (start != null) {
                        start.absorb(rail);
                    }
                    NodeAccumulator end = NodeAccumulator.obtain(accumulators, endPos, context);
                    if (end != null) {
                        end.absorb(rail);
                    }
                });
            }
        });
        if (accumulators.isEmpty()) {
            return Collections.emptyList();
        }
        List<Map.Entry<Long, NodeAccumulator>> entries = new ArrayList<>(accumulators.entrySet());
        entries.sort(Map.Entry.comparingByKey());
        List<NodeInfo> nodes = new ArrayList<>(entries.size());
        for (Map.Entry<Long, NodeAccumulator> entry : entries) {
            NodeInfo info = entry.getValue().toNodeInfo();
            if (info != null) {
                nodes.add(info);
            }
        }
        return nodes;
    }

    private static List<ScheduleEntry> toScheduleEntries(List<mtr.data.ScheduleEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return Collections.emptyList();
        }
        return entries.stream()
            .sorted(Comparator.comparingLong(entry -> entry.arrivalMillis))
            .map(entry -> new ScheduleEntry(entry.routeId, entry.arrivalMillis, entry.trainCars, entry.currentStationIndex, null))
            .collect(Collectors.toList());
    }

    private static Bounds buildBoundsFromArea(AreaBase area) {
        Object corner1 = readField(AREA_CORNER1, area);
        Object corner2 = readField(AREA_CORNER2, area);
        if (area == null || corner1 == null || corner2 == null) {
            return null;
        }
        TupleValues min = TupleValues.from(corner1);
        TupleValues max = TupleValues.from(corner2);
        if (min == null || max == null) {
            return null;
        }
        int y = resolveAreaY(area);
        int minX = Math.min(min.x, max.x);
        int maxX = Math.max(min.x, max.x);
        int minZ = Math.min(min.z, max.z);
        int maxZ = Math.max(min.z, max.z);
        return new Bounds(minX, y, minZ, maxX, y, maxZ);
    }

    private static Bounds buildBoundsFromSavedRail(SavedRailBase rail) {
        if (rail == null) {
            return null;
        }
        NodeInfo nodeInfo = buildNodeInfoFromSavedRail(rail, null, false);
        if (nodeInfo == null) {
            return null;
        }
        return new Bounds(nodeInfo.getX(), nodeInfo.getY(), nodeInfo.getZ(), nodeInfo.getX(), nodeInfo.getY(), nodeInfo.getZ());
    }

    private static NodeInfo buildNodeInfoFromStation(Station station) {
        if (station == null) {
            return null;
        }
        Bounds bounds = buildBoundsFromArea(station);
        if (bounds == null) {
            return null;
        }
        int centerX = bounds.getMinX() + (bounds.getMaxX() - bounds.getMinX()) / 2;
        int centerY = bounds.getMinY();
        int centerZ = bounds.getMinZ() + (bounds.getMaxZ() - bounds.getMinZ()) / 2;
        return new NodeInfo(centerX, centerY, centerZ, "STATION", false, station.id);
    }

    private static NodeInfo buildNodeInfoFromSavedRail(SavedRailBase rail, Long stationId, boolean platformSegment) {
        if (rail == null || SAVED_RAIL_GET_MID_POS == null) {
            return null;
        }
        Object blockPos = invoke(SAVED_RAIL_GET_MID_POS, rail);
        BlockPosCoord coord = BlockPosEncoding.coordinates(blockPos);
        if (coord == null) {
            return null;
        }
        String railType = platformSegment ? RailType.PLATFORM.name() : "UNKNOWN";
        return new NodeInfo(coord.x, coord.y, coord.z, railType, platformSegment, stationId);
    }

    private static int resolveAreaY(AreaBase area) {
        if (AREA_BASE_GET_CENTER == null) {
            return 0;
        }
        Object blockPos = invoke(AREA_BASE_GET_CENTER, area);
        BlockPosCoord coord = BlockPosEncoding.coordinates(blockPos);
        return coord != null ? coord.y : 0;
    }

    private static List<Long> toSortedList(Set<Long> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> list = new ArrayList<>(values);
        list.sort(Long::compareTo);
        return list;
    }

    private static List<Long> copyLongList(List<Long> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(values);
    }

    private static List<Integer> copyIntegerList(List<Integer> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(values);
    }

    private static String safeName(String name) {
        return name == null ? "" : name;
    }

    private static String describeTransportMode(mtr.data.TransportMode mode) {
        return mode == null ? "UNKNOWN" : mode.name();
    }

    private static String describeRouteType(Route route) {
        if (route.routeType != null) {
            return route.routeType.name();
        }
        return route.isLightRailRoute ? "LIGHT_RAIL" : "NORMAL";
    }

    private static int parseCursor(String cursor) {
        if (cursor == null || cursor.isEmpty()) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(cursor));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static Method locateMethod(Class<?> owner, String name) {
        try {
            Method method = owner.getMethod(name);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException ex) {
            LOGGER.warn("Unable to find method {} on {}", name, owner.getName());
            return null;
        }
    }

    private static Field locateField(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException ex) {
            LOGGER.warn("Unable to find field {} on {}", name, owner.getName());
            return null;
        }
    }

    private static Object invoke(Method method, Object target) {
        if (method == null || target == null) {
            return null;
        }
        try {
            return method.invoke(target);
        } catch (IllegalAccessException | InvocationTargetException ex) {
            LOGGER.debug("Failed to invoke {} on {}", method.getName(), target.getClass().getName(), ex);
            return null;
        }
    }

    private static Object readField(Field field, Object target) {
        if (field == null || target == null) {
            return null;
        }
        try {
            return field.get(target);
        } catch (IllegalAccessException ex) {
            LOGGER.debug("Failed to read field {} on {}", field.getName(), target.getClass().getName(), ex);
            return null;
        }
    }

    private static long readLong(Field field, Object target) {
        Object value = readField(field, target);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return 0L;
    }

    private static int readInt(Field field, Object target, int fallback) {
        Object value = readField(field, target);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return fallback;
    }

    @SuppressWarnings("unchecked")
    private static Map<Object, Station> extractBlockPosStationMap(DataCache cache) {
        Object value = readField(DATA_CACHE_BLOCK_POS_TO_STATION, cache);
        if (value instanceof Map) {
            return (Map<Object, Station>) value;
        }
        return Collections.emptyMap();
    }

    private static final class DimensionContext {
        final String dimensionId;
        final RailwayData railwayData;
        final DataCache cache;
        final RailwayDataRouteFinderModule routeFinderModule;
        final String mtrVersion;
        final Integer railwayDataVersion;
        final Map<Long, Station> stations;
        final Map<Long, Platform> platforms;
        final Map<Long, Station> platformToStation;
        final Map<Object, Station> blockPosToStation;
        final List<Route> routes;
        final List<Depot> depots;
        final List<Siding> sidings;
        final Map<Long, Set<Long>> stationRouteIds;
        final Map<Long, Set<Long>> platformRouteIds;
        final Map<Long, Long> platformDepotIds;
        final Map<Long, List<Long>> routeStationOrder;

        private DimensionContext(String dimensionId, RailwayData railwayData, DataCache cache) {
            this.dimensionId = dimensionId;
            this.railwayData = railwayData;
            this.cache = cache;
            this.routeFinderModule = railwayData.railwayDataRouteFinderModule;
            this.mtrVersion = describeMtrVersion(railwayData);
            this.railwayDataVersion = readRailwayDataVersion();
            this.stations = cache.stationIdMap != null ? cache.stationIdMap : Collections.emptyMap();
            this.platforms = cache.platformIdMap != null ? cache.platformIdMap : Collections.emptyMap();
            this.platformToStation = cache.platformIdToStation != null ? cache.platformIdToStation : Collections.emptyMap();
            this.blockPosToStation = extractBlockPosStationMap(cache);
            this.routes = railwayData.routes != null ? new ArrayList<>(railwayData.routes) : Collections.emptyList();
            this.depots = railwayData.depots != null ? new ArrayList<>(railwayData.depots) : Collections.emptyList();
            this.sidings = railwayData.sidings != null ? new ArrayList<>(railwayData.sidings) : Collections.emptyList();
            this.stationRouteIds = buildStationRouteIndex(routes, platformToStation);
            this.platformRouteIds = buildPlatformRouteIndex(routes);
            this.platformDepotIds = buildPlatformDepotIndex(depots);
            this.routeStationOrder = buildRouteStationOrder(routes, platformToStation);
        }

        static DimensionContext from(MtrDimensionSnapshot snapshot) {
            if (snapshot == null) {
                return null;
            }
            try {
                DataCache cache = snapshot.refreshAndGetCache();
                if (cache == null) {
                    return null;
                }
                return new DimensionContext(snapshot.getDimensionId(), snapshot.getRailwayData(), cache);
            } catch (Exception ex) {
                LOGGER.warn("Failed to refresh MTR cache for dimension {}", snapshot.getDimensionId(), ex);
                return null;
            }
        }

        private static String describeMtrVersion(RailwayData data) {
            if (data == null) {
                return null;
            }
            Package pkg = data.getClass().getPackage();
            if (pkg == null) {
                return null;
            }
            String version = pkg.getImplementationVersion();
            return version != null ? version : pkg.getSpecificationVersion();
        }

        private static Integer readRailwayDataVersion() {
            if (RAILWAY_DATA_VERSION == null) {
                return null;
            }
            try {
                Object value = RAILWAY_DATA_VERSION.get(null);
                if (value instanceof Integer) {
                    return (Integer) value;
                }
            } catch (IllegalAccessException ignored) {
            }
            return null;
        }
    }

    private static Map<Long, Set<Long>> buildStationRouteIndex(List<Route> routes, Map<Long, Station> platformToStation) {
        if (routes.isEmpty() || platformToStation.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, Set<Long>> index = new HashMap<>();
        for (Route route : routes) {
            if (route.platformIds == null) {
                continue;
            }
            for (Route.RoutePlatform reference : route.platformIds) {
                Station station = platformToStation.get(reference.platformId);
                if (station == null) {
                    continue;
                }
                index.computeIfAbsent(station.id, key -> new LinkedHashSet<>()).add(route.id);
            }
        }
        return index;
    }

    private static Map<Long, Set<Long>> buildPlatformRouteIndex(List<Route> routes) {
        if (routes.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, Set<Long>> index = new HashMap<>();
        for (Route route : routes) {
            if (route.platformIds == null) {
                continue;
            }
            for (Route.RoutePlatform reference : route.platformIds) {
                index.computeIfAbsent(reference.platformId, key -> new LinkedHashSet<>()).add(route.id);
            }
        }
        return index;
    }

    private static Map<Long, Long> buildPlatformDepotIndex(List<Depot> depots) {
        if (depots.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, Long> index = new HashMap<>();
        for (Depot depot : depots) {
            if (depot.platformTimes == null || depot.platformTimes.isEmpty()) {
                continue;
            }
            for (Map<Long, Float> platformTimes : depot.platformTimes.values()) {
                if (platformTimes == null) {
                    continue;
                }
                for (Long platformId : platformTimes.keySet()) {
                    index.putIfAbsent(platformId, depot.id);
                }
            }
        }
        return index;
    }

    private static Map<Long, List<Long>> buildRouteStationOrder(List<Route> routes, Map<Long, Station> platformToStation) {
        if (routes.isEmpty() || platformToStation.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, List<Long>> order = new HashMap<>();
        for (Route route : routes) {
            if (route.platformIds == null || route.platformIds.isEmpty()) {
                continue;
            }
            List<Long> stations = new ArrayList<>();
            for (RoutePlatform reference : route.platformIds) {
                Station station = platformToStation.get(reference.platformId);
                if (station != null) {
                    stations.add(station.id);
                }
            }
            order.put(route.id, stations);
        }
        return order;
    }

    private static final class NodeAccumulator {
        final int x;
        final int y;
        final int z;
        final Long stationId;
        String railType;
        boolean platformSegment;
        int priority;

        private NodeAccumulator(int x, int y, int z, Long stationId) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.stationId = stationId;
        }

        static NodeAccumulator obtain(Map<Long, NodeAccumulator> cache, Object blockPos, DimensionContext context) {
            BlockPosCoord coord = BlockPosEncoding.coordinates(blockPos);
            if (coord == null) {
                return null;
            }
            NodeAccumulator existing = cache.get(coord.packed);
            if (existing != null) {
                return existing;
            }
            Station station = context.blockPosToStation.get(blockPos);
            NodeAccumulator created = new NodeAccumulator(coord.x, coord.y, coord.z, station != null ? station.id : null);
            cache.put(coord.packed, created);
            return created;
        }

        void absorb(Rail rail) {
            if (rail == null || rail.railType == null) {
                return;
            }
            int candidatePriority = priority(rail.railType);
            if (candidatePriority >= this.priority) {
                this.priority = candidatePriority;
                this.railType = rail.railType.name();
                this.platformSegment = rail.railType == RailType.PLATFORM;
            }
        }

        NodeInfo toNodeInfo() {
            String type = railType != null ? railType : "UNKNOWN";
            return new NodeInfo(x, y, z, type, platformSegment, stationId);
        }

        private static int priority(RailType type) {
            if (type == null) {
                return 0;
            }
            if (type == RailType.PLATFORM) {
                return 3;
            }
            if (type == RailType.SIDING) {
                return 2;
            }
            return 1;
        }
    }

    private static final class TupleValues {
        final int x;
        final int z;

        private TupleValues(int x, int z) {
            this.x = x;
            this.z = z;
        }

        static TupleValues from(Object tuple) {
            if (tuple == null) {
                return null;
            }
            TupleAccessor accessor = TupleAccessor.forClass(tuple.getClass());
            if (accessor == null) {
                return null;
            }
            Integer first = accessor.first(tuple);
            Integer second = accessor.second(tuple);
            if (first == null || second == null) {
                return null;
            }
            return new TupleValues(first, second);
        }
    }

    private static final class TupleAccessor {
        private static final Map<Class<?>, TupleAccessor> CACHE = new ConcurrentHashMap<>();
        private final Field first;
        private final Field second;

        private TupleAccessor(Field first, Field second) {
            this.first = first;
            this.second = second;
        }

        static TupleAccessor forClass(Class<?> clazz) {
            return CACHE.computeIfAbsent(clazz, TupleAccessor::create);
        }

        static TupleAccessor create(Class<?> clazz) {
            Field[] fields = clazz.getDeclaredFields();
            List<Field> valueFields = new ArrayList<>();
            for (Field field : fields) {
                if (!Modifier.isStatic(field.getModifiers())) {
                    field.setAccessible(true);
                    valueFields.add(field);
                }
            }
            if (valueFields.size() < 2) {
                LOGGER.debug("Tuple class {} does not expose two value fields", clazz.getName());
                return null;
            }
            return new TupleAccessor(valueFields.get(0), valueFields.get(1));
        }

        Integer first(Object target) {
            return read(first, target);
        }

        Integer second(Object target) {
            return read(second, target);
        }

        private Integer read(Field field, Object target) {
            if (field == null || target == null) {
                return null;
            }
            try {
                Object value = field.get(target);
                if (value instanceof Integer) {
                    return (Integer) value;
                }
            } catch (IllegalAccessException ex) {
                LOGGER.debug("Unable to read tuple field {}", field.getName(), ex);
            }
            return null;
        }
    }

    private static final class BlockPosCoord {
        final long packed;
        final int x;
        final int y;
        final int z;

        private BlockPosCoord(long packed, int x, int y, int z) {
            this.packed = packed;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private static final class BlockPosEncoding {
        private static final int X_BITS = 26;
        private static final int Y_BITS = 12;
        private static final int Z_BITS = 26;
        private static final long X_MASK = (1L << X_BITS) - 1L;
        private static final long Y_MASK = (1L << Y_BITS) - 1L;
        private static final long Z_MASK = (1L << Z_BITS) - 1L;
        private static final int Y_OFFSET = 0;
        private static final int Z_OFFSET = Y_BITS;
        private static final int X_OFFSET = Y_BITS + Z_BITS;

        private BlockPosEncoding() {
        }

        static BlockPosCoord coordinates(Object blockPos) {
            if (blockPos == null) {
                return null;
            }
            try {
                long packed = encode(blockPos);
                int x = unpack(packed, X_OFFSET, X_MASK, X_BITS);
                int y = unpack(packed, Y_OFFSET, Y_MASK, Y_BITS);
                int z = unpack(packed, Z_OFFSET, Z_MASK, Z_BITS);
                return new BlockPosCoord(packed, x, y, z);
            } catch (Exception ex) {
                LOGGER.debug("Unable to decode BlockPos {}", blockPos.getClass().getName(), ex);
                return null;
            }
        }

        private static long encode(Object blockPos) throws InvocationTargetException, IllegalAccessException {
            Method method = AS_LONG_METHOD_CACHE.computeIfAbsent(blockPos.getClass(), BlockPosEncoding::locateAsLongMethod);
            if (method == null) {
                throw new IllegalStateException("Missing asLong method for " + blockPos.getClass().getName());
            }
            Object value = method.invoke(blockPos);
            return (long) value;
        }

        private static Method locateAsLongMethod(Class<?> clazz) {
            try {
                Method method = clazz.getMethod("asLong");
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                for (Method candidate : clazz.getMethods()) {
                    if (candidate.getParameterCount() == 0 && candidate.getReturnType() == long.class) {
                        candidate.setAccessible(true);
                        return candidate;
                    }
                }
                LOGGER.warn("Unable to locate asLong-like method on {}", clazz.getName());
                return null;
            }
        }

        private static int unpack(long value, int offset, long mask, int bits) {
            long shifted = (value >> offset) & mask;
            int limit = 1 << (bits - 1);
            if (shifted >= limit) {
                shifted -= (1L << bits);
            }
            return (int) shifted;
        }
    }

    private static final class RailsFieldAccessor {
        private static final Field FIELD = locate();

        private RailsFieldAccessor() {
        }

        @SuppressWarnings("unchecked")
        static Map<Object, Map<Object, Rail>> get(RailwayData data) {
            if (FIELD == null || data == null) {
                return Collections.emptyMap();
            }
            try {
                Object value = FIELD.get(data);
                if (value instanceof Map) {
                    return (Map<Object, Map<Object, Rail>>) value;
                }
            } catch (IllegalAccessException ex) {
                LOGGER.debug("Unable to read RailwayData.rails", ex);
            }
            return Collections.emptyMap();
        }

        private static Field locate() {
            try {
                Field field = RailwayData.class.getDeclaredField("rails");
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ex) {
                LOGGER.warn("Failed to locate RailwayData.rails field", ex);
                return null;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static long extractPlatformStart(Object details) {
        if (details == null || CONNECTION_DETAILS_PLATFORM_START == null) {
            return 0L;
        }
        try {
            Object platform = CONNECTION_DETAILS_PLATFORM_START.get(details);
            return toBlockPosLong(invokeSavedRailMidPos(platform));
        } catch (IllegalAccessException ignored) {
        }
        return 0L;
    }

    @SuppressWarnings("unchecked")
    private static Map<Long, Integer> extractDurationInfo(Object details) {
        if (details == null || CONNECTION_DETAILS_DURATION_INFO == null) {
            return Collections.emptyMap();
        }
        try {
            Object value = CONNECTION_DETAILS_DURATION_INFO.get(details);
            if (value instanceof Map) {
                return Collections.unmodifiableMap(new HashMap<>((Map<Long, Integer>) value));
            }
        } catch (IllegalAccessException ignored) {
        }
        return Collections.emptyMap();
    }

    private static Integer extractShortestDuration(Object details) {
        if (details == null || CONNECTION_DETAILS_SHORT_DURATION == null) {
            return null;
        }
        try {
            Object value = CONNECTION_DETAILS_SHORT_DURATION.get(details);
            if (value instanceof Integer) {
                return (Integer) value;
            }
        } catch (IllegalAccessException ignored) {
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Integer extractConnectionDensity(RailwayDataRouteFinderModule module, long from, long to) {
        if (module == null || ROUTE_FINDER_CONNECTION_DENSITY == null) {
            return null;
        }
        try {
            Object value = ROUTE_FINDER_CONNECTION_DENSITY.get(module);
            if (!(value instanceof Map)) {
                return null;
            }
            Map<?, ?> density = (Map<?, ?>) value;
            Object target = density.get(from);
            if (!(target instanceof Map)) {
                return null;
            }
            Map<?, ?> targetMap = (Map<?, ?>) target;
            Object densityValue = targetMap.get(to);
            if (densityValue instanceof Number) {
                return ((Number) densityValue).intValue();
            }
        } catch (IllegalAccessException ignored) {
        }
        return null;
    }

    private static Object readPlatformConnections(DataCache cache) {
        if (cache == null || DATA_CACHE_PLATFORM_CONNECTIONS == null) {
            return null;
        }
        try {
            return DATA_CACHE_PLATFORM_CONNECTIONS.get(cache);
        } catch (IllegalAccessException ex) {
            LOGGER.debug("Unable to read DataCache.platformConnections", ex);
            return null;
        }
    }

    private static void collectRouteFinderData(List<MtrModels.RouteFinderDataEntry> buffer, String dimensionId,
                                               RailwayDataRouteFinderModule module, Field field, String source) {
        if (buffer == null || module == null || field == null) {
            return;
        }
        List<?> items = readListField(module, field);
        if (items.isEmpty()) {
            return;
        }
        for (Object item : items) {
            MtrModels.RouteFinderDataEntry entry = buildRouteFinderDataEntry(item, dimensionId, source);
            if (entry != null) {
                buffer.add(entry);
            }
        }
    }

    private static List<?> readListField(Object target, Field field) {
        if (target == null || field == null) {
            return Collections.emptyList();
        }
        try {
            Object value = field.get(target);
            if (value instanceof List) {
                return (List<?>) value;
            }
        } catch (IllegalAccessException ignored) {
        }
        return Collections.emptyList();
    }

    private static MtrModels.RouteFinderDataEntry buildRouteFinderDataEntry(Object target, String dimensionId, String source) {
        if (target == null) {
            return null;
        }
        long pos = toBlockPosLong(readField(target, ROUTE_FINDER_DATA_POS));
        Integer duration = readIntegerField(target, ROUTE_FINDER_DATA_DURATION);
        Long routeId = readLongField(target, ROUTE_FINDER_DATA_ROUTE_ID);
        Integer waitingTime = readIntegerField(target, ROUTE_FINDER_DATA_WAITING_TIME);
        List<Long> stationIds = readLongListField(target, ROUTE_FINDER_DATA_STATION_IDS);
        return new MtrModels.RouteFinderDataEntry(
            dimensionId,
            pos,
            routeId != null ? routeId : 0L,
            duration != null ? duration : 0,
            waitingTime != null ? waitingTime : 0,
            stationIds,
            source
        );
    }

    private static Integer readIntegerField(Object target, Field field) {
        return toInteger(readField(target, field));
    }

    private static Long readLongField(Object target, Field field) {
        Object value = readField(target, field);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return null;
    }

    private static String readEnumName(Object target, Field field) {
        Object value = readField(target, field);
        return value != null ? value.toString() : null;
    }

    private static List<Long> readLongListField(Object target, Field field) {
        List<?> raw = readListField(target, field);
        if (raw.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> result = new ArrayList<>();
        for (Object value : raw) {
            if (value instanceof Number) {
                result.add(((Number) value).longValue());
            } else if (value != null) {
                try {
                    result.add(Long.parseLong(value.toString()));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return Collections.unmodifiableList(result);
    }

    private static Integer toInteger(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private static Map<Long, Integer> toLongIntMap(Object value) {
        if (!(value instanceof Map)) {
            return Collections.emptyMap();
        }
        Map<?, ?> source = (Map<?, ?>) value;
        Map<Long, Integer> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            Long key = toLong(entry.getKey());
            Integer val = toInteger(entry.getValue());
            if (key != null && val != null) {
                result.put(key, val);
            }
        }
        return Collections.unmodifiableMap(result);
    }

    private static Object readField(Object target, Field field) {
        if (target == null || field == null) {
            return null;
        }
        try {
            return field.get(target);
        } catch (IllegalAccessException ex) {
            LOGGER.debug("Unable to read field {} on {}", field.getName(), target.getClass().getName(), ex);
            return null;
        }
    }

    private static Object invokeSavedRailPosition(Object rail, int index) {
        if (!(rail instanceof SavedRailBase) || SAVED_RAIL_GET_POSITION == null) {
            return null;
        }
        try {
            return SAVED_RAIL_GET_POSITION.invoke(rail, index);
        } catch (IllegalAccessException | InvocationTargetException ex) {
            LOGGER.debug("Unable to read SavedRailBase position {}", index, ex);
            return null;
        }
    }

    private static Object invokeSavedRailMidPos(Object rail) {
        if (!(rail instanceof SavedRailBase) || SAVED_RAIL_GET_MID_POS == null) {
            return null;
        }
        try {
            return SAVED_RAIL_GET_MID_POS.invoke(rail);
        } catch (IllegalAccessException | InvocationTargetException ex) {
            LOGGER.debug("Unable to read SavedRailBase midPos", ex);
            return null;
        }
    }

    private static long toBlockPosLong(Object blockPos) {
        BlockPosCoord coord = BlockPosEncoding.coordinates(blockPos);
        return coord != null ? coord.packed : 0L;
    }

    private static long encodeBlockPos(Object blockPos) {
        return toBlockPosLong(blockPos);
    }

    private static long toLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return encodeBlockPos(value);
    }

    private static String readRailType(Rail rail) {
        if (rail == null || RAIL_RAIL_TYPE == null) {
            return "UNKNOWN";
        }
        try {
            Object value = RAIL_RAIL_TYPE.get(rail);
            if (value != null) {
                return value.toString();
            }
        } catch (IllegalAccessException ignored) {
        }
        return "UNKNOWN";
    }

    private static String readTransportMode(Rail rail) {
        if (rail == null || RAIL_TRANSPORT_MODE == null) {
            return "UNKNOWN";
        }
        try {
            Object value = RAIL_TRANSPORT_MODE.get(rail);
            if (value != null) {
                return value.toString();
            }
        } catch (IllegalAccessException ignored) {
        }
        return "UNKNOWN";
    }

    private static MtrModels.RailSegmentParams buildRailSegmentParams(Rail rail, int segment) {
        if (rail == null) {
            return null;
        }
        double h = readRailFieldDouble(segment == 1 ? RAIL_H1 : RAIL_H2, rail);
        double k = readRailFieldDouble(segment == 1 ? RAIL_K1 : RAIL_K2, rail);
        double r = readRailFieldDouble(segment == 1 ? RAIL_R1 : RAIL_R2, rail);
        double tStart = readRailFieldDouble(segment == 1 ? RAIL_T_START_1 : RAIL_T_START_2, rail);
        double tEnd = readRailFieldDouble(segment == 1 ? RAIL_T_END_1 : RAIL_T_END_2, rail);
        boolean straight = readRailFieldBoolean(segment == 1 ? RAIL_IS_STRAIGHT_1 : RAIL_IS_STRAIGHT_2, rail);
        boolean reverse = readRailFieldBoolean(segment == 1 ? RAIL_REVERSE_T_1 : RAIL_REVERSE_T_2, rail);
        int yStart = readRailFieldInt(segment == 1 ? RAIL_Y_START : RAIL_Y_START, rail);
        int yEnd = readRailFieldInt(segment == 1 ? RAIL_Y_END : RAIL_Y_END, rail);
        return new MtrModels.RailSegmentParams(h, k, r, tStart, tEnd, reverse, straight, yStart, yEnd);
    }

    private static double readRailFieldDouble(Field field, Rail rail) {
        if (field == null || rail == null) {
            return 0D;
        }
        try {
            Object value = field.get(rail);
            if (value instanceof Number) {
                return ((Number) value).doubleValue();
            }
        } catch (IllegalAccessException ignored) {
        }
        return 0D;
    }

    private static int readRailFieldInt(Field field, Rail rail) {
        if (field == null || rail == null) {
            return 0;
        }
        try {
            Object value = field.get(rail);
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
        } catch (IllegalAccessException ignored) {
        }
        return 0;
    }

    private static boolean readRailFieldBoolean(Field field, Rail rail) {
        if (field == null || rail == null) {
            return false;
        }
        try {
            Object value = field.get(rail);
            if (value instanceof Boolean) {
                return (Boolean) value;
            }
        } catch (IllegalAccessException ignored) {
        }
        return false;
    }
}
