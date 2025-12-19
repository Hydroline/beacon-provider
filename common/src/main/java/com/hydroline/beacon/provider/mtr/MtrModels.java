package com.hydroline.beacon.provider.mtr;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable DTO definitions describing MTR data snapshots so loader-specific implementations
 * can map Minecraft/MTR objects into protocol-friendly representations without leaking
 * Minecraft classes into the common module.
 */
public final class MtrModels {
    private MtrModels() {
    }

    public static final class DimensionOverview {
        private final String dimensionId;
        private final List<RouteSummary> routes;
        private final List<DepotInfo> depots;
        private final List<FareAreaInfo> fareAreas;

        public DimensionOverview(String dimensionId, List<RouteSummary> routes, List<DepotInfo> depots,
                                 List<FareAreaInfo> fareAreas) {
            this.dimensionId = Objects.requireNonNull(dimensionId, "dimensionId");
            this.routes = copyList(routes);
            this.depots = copyList(depots);
            this.fareAreas = copyList(fareAreas);
        }

        public String getDimensionId() {
            return dimensionId;
        }

        public List<RouteSummary> getRoutes() {
            return routes;
        }

        public List<DepotInfo> getDepots() {
            return depots;
        }

        public List<FareAreaInfo> getFareAreas() {
            return fareAreas;
        }
    }

    public static final class RouteSummary {
        private final long routeId;
        private final String name;
        private final int color;
        private final String transportMode;
        private final String routeType;
        private final boolean hidden;
        private final List<PlatformSummary> platforms;

        public RouteSummary(long routeId, String name, int color, String transportMode, String routeType,
                            boolean hidden, List<PlatformSummary> platforms) {
            this.routeId = routeId;
            this.name = name;
            this.color = color;
            this.transportMode = transportMode;
            this.routeType = routeType;
            this.hidden = hidden;
            this.platforms = copyList(platforms);
        }

        public long getRouteId() {
            return routeId;
        }

        public String getName() {
            return name;
        }

        public int getColor() {
            return color;
        }

        public String getTransportMode() {
            return transportMode;
        }

        public String getRouteType() {
            return routeType;
        }

        public boolean isHidden() {
            return hidden;
        }

        public List<PlatformSummary> getPlatforms() {
            return platforms;
        }
    }

    public static final class PlatformSummary {
        private final long platformId;
        private final long stationId;
        private final String stationName;
        private final Bounds bounds;
        private final List<Long> interchangeRouteIds;

        public PlatformSummary(long platformId, long stationId, String stationName, Bounds bounds,
                               List<Long> interchangeRouteIds) {
            this.platformId = platformId;
            this.stationId = stationId;
            this.stationName = stationName;
            this.bounds = bounds;
            this.interchangeRouteIds = copyList(interchangeRouteIds);
        }

        public long getPlatformId() {
            return platformId;
        }

        public long getStationId() {
            return stationId;
        }

        public String getStationName() {
            return stationName;
        }

        public Bounds getBounds() {
            return bounds;
        }

        public List<Long> getInterchangeRouteIds() {
            return interchangeRouteIds;
        }
    }

    public static final class DepotInfo {
        private final long depotId;
        private final String name;
        private final String transportMode;
        private final List<Long> routeIds;
        private final List<Integer> departures;
        private final boolean useRealTime;
        private final boolean repeatInfinitely;
        private final int cruisingAltitude;
        private final Optional<Integer> nextDepartureMillis;

        public DepotInfo(long depotId, String name, String transportMode, List<Long> routeIds,
                         List<Integer> departures, boolean useRealTime, boolean repeatInfinitely,
                         int cruisingAltitude, Integer nextDepartureMillis) {
            this.depotId = depotId;
            this.name = name;
            this.transportMode = transportMode;
            this.routeIds = copyList(routeIds);
            this.departures = copyList(departures);
            this.useRealTime = useRealTime;
            this.repeatInfinitely = repeatInfinitely;
            this.cruisingAltitude = cruisingAltitude;
            this.nextDepartureMillis = Optional.ofNullable(nextDepartureMillis);
        }

        public long getDepotId() {
            return depotId;
        }

        public String getName() {
            return name;
        }

        public String getTransportMode() {
            return transportMode;
        }

        public List<Long> getRouteIds() {
            return routeIds;
        }

        public List<Integer> getDepartures() {
            return departures;
        }

        public boolean isUseRealTime() {
            return useRealTime;
        }

        public boolean isRepeatInfinitely() {
            return repeatInfinitely;
        }

        public int getCruisingAltitude() {
            return cruisingAltitude;
        }

        public Optional<Integer> getNextDepartureMillis() {
            return nextDepartureMillis;
        }
    }

    public static final class FareAreaInfo {
        private final long stationId;
        private final String name;
        private final int zone;
        private final Bounds bounds;
        private final List<Long> interchangeRouteIds;

        public FareAreaInfo(long stationId, String name, int zone, Bounds bounds, List<Long> interchangeRouteIds) {
            this.stationId = stationId;
            this.name = name;
            this.zone = zone;
            this.bounds = bounds;
            this.interchangeRouteIds = copyList(interchangeRouteIds);
        }

        public long getStationId() {
            return stationId;
        }

        public String getName() {
            return name;
        }

        public int getZone() {
            return zone;
        }

        public Bounds getBounds() {
            return bounds;
        }

        public List<Long> getInterchangeRouteIds() {
            return interchangeRouteIds;
        }
    }

    public static final class Bounds {
        private final int minX;
        private final int minY;
        private final int minZ;
        private final int maxX;
        private final int maxY;
        private final int maxZ;

        public Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
        }

        public int getMinX() {
            return minX;
        }

        public int getMinY() {
            return minY;
        }

        public int getMinZ() {
            return minZ;
        }

        public int getMaxX() {
            return maxX;
        }

        public int getMaxY() {
            return maxY;
        }

        public int getMaxZ() {
            return maxZ;
        }
    }

    public static final class StationInfo {
        private final String dimensionId;
        private final long stationId;
        private final String name;
        private final int zone;
        private final Bounds bounds;
        private final List<Long> interchangeRouteIds;
        private final List<StationPlatformInfo> platforms;

        public StationInfo(String dimensionId, long stationId, String name, int zone, Bounds bounds,
                           List<Long> interchangeRouteIds, List<StationPlatformInfo> platforms) {
            this.dimensionId = Objects.requireNonNull(dimensionId, "dimensionId");
            this.stationId = stationId;
            this.name = name == null ? "" : name;
            this.zone = zone;
            this.bounds = bounds;
            this.interchangeRouteIds = copyList(interchangeRouteIds);
            this.platforms = copyList(platforms);
        }

        public String getDimensionId() {
            return dimensionId;
        }

        public long getStationId() {
            return stationId;
        }

        public String getName() {
            return name;
        }

        public int getZone() {
            return zone;
        }

        public Bounds getBounds() {
            return bounds;
        }

        public List<Long> getInterchangeRouteIds() {
            return interchangeRouteIds;
        }

        public List<StationPlatformInfo> getPlatforms() {
            return platforms;
        }
    }

    public static final class StationPlatformInfo {
        private final long platformId;
        private final String platformName;
        private final List<Long> routeIds;
        private final Optional<Long> depotId;

        public StationPlatformInfo(long platformId, String platformName, List<Long> routeIds, Long depotId) {
            this.platformId = platformId;
            this.platformName = platformName == null ? "" : platformName;
            this.routeIds = copyList(routeIds);
            this.depotId = Optional.ofNullable(depotId);
        }

        public long getPlatformId() {
            return platformId;
        }

        public String getPlatformName() {
            return platformName;
        }

        public List<Long> getRouteIds() {
            return routeIds;
        }

        public Optional<Long> getDepotId() {
            return depotId;
        }
    }

    public static final class RouteDetail {
        private final String dimensionId;
        private final long routeId;
        private final String name;
        private final int color;
        private final String routeType;
        private final List<RouteNode> nodes;

        public RouteDetail(String dimensionId, long routeId, String name, int color, String routeType,
                           List<RouteNode> nodes) {
            this.dimensionId = dimensionId;
            this.routeId = routeId;
            this.name = name;
            this.color = color;
            this.routeType = routeType;
            this.nodes = copyList(nodes);
        }

        public String getDimensionId() {
            return dimensionId;
        }

        public long getRouteId() {
            return routeId;
        }

        public String getName() {
            return name;
        }

        public int getColor() {
            return color;
        }

        public String getRouteType() {
            return routeType;
        }

        public List<RouteNode> getNodes() {
            return nodes;
        }
    }

    public static final class RouteNode {
        private final NodeInfo node;
        private final String segmentCategory;
        private final long sequence;

        public RouteNode(NodeInfo node, String segmentCategory, long sequence) {
            this.node = node;
            this.segmentCategory = segmentCategory;
            this.sequence = sequence;
        }

        public NodeInfo getNode() {
            return node;
        }

        public String getSegmentCategory() {
            return segmentCategory;
        }

        public long getSequence() {
            return sequence;
        }
    }

    public static final class NodeInfo {
        private final int x;
        private final int y;
        private final int z;
        private final String railType;
        private final boolean platformSegment;
        private final Optional<Long> stationId;

        public NodeInfo(int x, int y, int z, String railType, boolean platformSegment, Long stationId) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.railType = railType;
            this.platformSegment = platformSegment;
            this.stationId = Optional.ofNullable(stationId);
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }

        public int getZ() {
            return z;
        }

        public String getRailType() {
            return railType;
        }

        public boolean isPlatformSegment() {
            return platformSegment;
        }

        public Optional<Long> getStationId() {
            return stationId;
        }
    }

    public static final class NodePage {
        private final String dimensionId;
        private final List<NodeInfo> nodes;
        private final Optional<String> nextCursor;

        public NodePage(String dimensionId, List<NodeInfo> nodes, String nextCursor) {
            this.dimensionId = dimensionId;
            this.nodes = copyList(nodes);
            this.nextCursor = Optional.ofNullable(nextCursor);
        }

        public String getDimensionId() {
            return dimensionId;
        }

        public List<NodeInfo> getNodes() {
            return nodes;
        }

        public Optional<String> getNextCursor() {
            return nextCursor;
        }
    }

    public static final class StationTimetable {
        private final String dimensionId;
        private final long stationId;
        private final List<PlatformTimetable> platforms;

        public StationTimetable(String dimensionId, long stationId, List<PlatformTimetable> platforms) {
            this.dimensionId = dimensionId;
            this.stationId = stationId;
            this.platforms = copyList(platforms);
        }

        public String getDimensionId() {
            return dimensionId;
        }

        public long getStationId() {
            return stationId;
        }

        public List<PlatformTimetable> getPlatforms() {
            return platforms;
        }
    }

    public static final class PlatformTimetable {
        private final long platformId;
        private final List<ScheduleEntry> entries;

        public PlatformTimetable(long platformId, List<ScheduleEntry> entries) {
            this.platformId = platformId;
            this.entries = copyList(entries);
        }

        public long getPlatformId() {
            return platformId;
        }

        public List<ScheduleEntry> getEntries() {
            return entries;
        }
    }

    public static final class ScheduleEntry {
        private final long routeId;
        private final long arrivalMillis;
        private final int trainCars;
        private final int currentStationIndex;
        private final Optional<Integer> delayMillis;

        public ScheduleEntry(long routeId, long arrivalMillis, int trainCars, int currentStationIndex,
                             Integer delayMillis) {
            this.routeId = routeId;
            this.arrivalMillis = arrivalMillis;
            this.trainCars = trainCars;
            this.currentStationIndex = currentStationIndex;
            this.delayMillis = Optional.ofNullable(delayMillis);
        }

        public long getRouteId() {
            return routeId;
        }

        public long getArrivalMillis() {
            return arrivalMillis;
        }

        public int getTrainCars() {
            return trainCars;
        }

        public int getCurrentStationIndex() {
            return currentStationIndex;
        }

        public Optional<Integer> getDelayMillis() {
            return delayMillis;
        }
    }

    public static final class TrainStatus {
        private final String dimensionId;
        private final java.util.UUID trainUuid;
        private final long routeId;
        private final Optional<Long> depotId;
        private final String transportMode;
        private final Optional<Long> currentStationId;
        private final Optional<Long> nextStationId;
        private final Optional<Integer> delayMillis;
        private final String segmentCategory;
        private final double progress;
        private final Optional<NodeInfo> node;

        public TrainStatus(String dimensionId, java.util.UUID trainUuid, long routeId, Long depotId,
                           String transportMode, Long currentStationId, Long nextStationId, Integer delayMillis,
                           String segmentCategory, double progress, NodeInfo node) {
            this.dimensionId = Objects.requireNonNull(dimensionId, "dimensionId");
            this.trainUuid = trainUuid;
            this.routeId = routeId;
            this.depotId = Optional.ofNullable(depotId);
            this.transportMode = transportMode == null ? "UNKNOWN" : transportMode;
            this.currentStationId = Optional.ofNullable(currentStationId);
            this.nextStationId = Optional.ofNullable(nextStationId);
            this.delayMillis = Optional.ofNullable(delayMillis);
            this.segmentCategory = segmentCategory == null ? "UNKNOWN" : segmentCategory;
            this.progress = progress;
            this.node = Optional.ofNullable(node);
        }

        public String getDimensionId() {
            return dimensionId;
        }

        public java.util.UUID getTrainUuid() {
            return trainUuid;
        }

        public long getRouteId() {
            return routeId;
        }

        public Optional<Long> getDepotId() {
            return depotId;
        }

        public String getTransportMode() {
            return transportMode;
        }

        public Optional<Long> getCurrentStationId() {
            return currentStationId;
        }

        public Optional<Long> getNextStationId() {
            return nextStationId;
        }

        public Optional<Integer> getDelayMillis() {
            return delayMillis;
        }

        public String getSegmentCategory() {
            return segmentCategory;
        }

        public double getProgress() {
            return progress;
        }

        public Optional<NodeInfo> getNode() {
            return node;
        }
    }

    private static <T> List<T> copyList(List<T> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new java.util.ArrayList<>(source));
    }

    public static final class RouteFinderSnapshot {
        private final String dimensionId;
        private final long routeId;
        private final String routeName;
        private final List<RouteNode> nodes;

        public RouteFinderSnapshot(String dimensionId, long routeId, String routeName, List<RouteNode> nodes) {
            this.dimensionId = dimensionId;
            this.routeId = routeId;
            this.routeName = routeName;
            this.nodes = copyList(nodes);
        }

        public String getDimensionId() {
            return dimensionId;
        }

        public long getRouteId() {
            return routeId;
        }

        public String getRouteName() {
            return routeName;
        }

        public List<RouteNode> getNodes() {
            return nodes;
        }
    }

    public static final class ConnectionProfile {
        private final String dimensionId;
        private final long fromPos;
        private final long toPos;
        private final long platformStartPos;
        private final Integer shortestDuration;
        private final Map<Long, Integer> durationInfo;
        private final Integer connectionDensity;

        public ConnectionProfile(String dimensionId, long fromPos, long toPos, long platformStartPos,
                                 Integer shortestDuration, Map<Long, Integer> durationInfo,
                                 Integer connectionDensity) {
            this.dimensionId = dimensionId;
            this.fromPos = fromPos;
            this.toPos = toPos;
            this.platformStartPos = platformStartPos;
            this.shortestDuration = shortestDuration;
            this.durationInfo = durationInfo == null ? Collections.emptyMap() : Collections.unmodifiableMap(new java.util.HashMap<>(durationInfo));
            this.connectionDensity = connectionDensity;
        }

        public String getDimensionId() {
            return dimensionId;
        }

        public long getFromPos() {
            return fromPos;
        }

        public long getToPos() {
            return toPos;
        }

        public long getPlatformStartPos() {
            return platformStartPos;
        }

        public Optional<Integer> getShortestDuration() {
            return Optional.ofNullable(shortestDuration);
        }

        public Map<Long, Integer> getDurationInfo() {
            return durationInfo;
        }

        public Optional<Integer> getConnectionDensity() {
            return Optional.ofNullable(connectionDensity);
        }
    }

    public static final class PlatformPosition {
        private final String dimensionId;
        private final long platformId;
        private final long pos1;
        private final long pos2;
        private final long midPos;
        private final long platformStart;
        private final long platformEnd;

        public PlatformPosition(String dimensionId, long platformId, long pos1, long pos2, long midPos,
                                long platformStart, long platformEnd) {
            this.dimensionId = dimensionId;
            this.platformId = platformId;
            this.pos1 = pos1;
            this.pos2 = pos2;
            this.midPos = midPos;
            this.platformStart = platformStart;
            this.platformEnd = platformEnd;
        }

        public String getDimensionId() {
            return dimensionId;
        }

        public long getPlatformId() {
            return platformId;
        }

        public long getPos1() {
            return pos1;
        }

        public long getPos2() {
            return pos2;
        }

        public long getMidPos() {
            return midPos;
        }

        public long getPlatformStart() {
            return platformStart;
        }

        public long getPlatformEnd() {
            return platformEnd;
        }
    }

    public static final class RailCurveSegment {
        private final String dimensionId;
        private final long fromPos;
        private final long toPos;
        private final String railType;
        private final String transportMode;
        private final RailSegmentParams segment1;
        private final RailSegmentParams segment2;

        public RailCurveSegment(String dimensionId, long fromPos, long toPos, String railType,
                                String transportMode, RailSegmentParams segment1, RailSegmentParams segment2) {
            this.dimensionId = dimensionId;
            this.fromPos = fromPos;
            this.toPos = toPos;
            this.railType = railType;
            this.transportMode = transportMode;
            this.segment1 = segment1;
            this.segment2 = segment2;
        }

        public String getDimensionId() {
            return dimensionId;
        }

        public long getFromPos() {
            return fromPos;
        }

        public long getToPos() {
            return toPos;
        }

        public String getRailType() {
            return railType;
        }

        public String getTransportMode() {
            return transportMode;
        }

        public RailSegmentParams getSegment1() {
            return segment1;
        }

        public RailSegmentParams getSegment2() {
            return segment2;
        }
    }

    public static final class RailSegmentParams {
        private final double h;
        private final double k;
        private final double r;
        private final double tStart;
        private final double tEnd;
        private final boolean reverse;
        private final boolean straight;
        private final int yStart;
        private final int yEnd;

        public RailSegmentParams(double h, double k, double r, double tStart, double tEnd,
                                 boolean reverse, boolean straight, int yStart, int yEnd) {
            this.h = h;
            this.k = k;
            this.r = r;
            this.tStart = tStart;
            this.tEnd = tEnd;
            this.reverse = reverse;
            this.straight = straight;
            this.yStart = yStart;
            this.yEnd = yEnd;
        }

        public double getH() {
            return h;
        }

        public double getK() {
            return k;
        }

        public double getR() {
            return r;
        }

        public double getTStart() {
            return tStart;
        }

        public double getTEnd() {
            return tEnd;
        }

        public boolean isReverse() {
            return reverse;
        }

        public boolean isStraight() {
            return straight;
        }

        public int getYStart() {
            return yStart;
        }

        public int getYEnd() {
            return yEnd;
        }
    }

    public static final class RoutefinderVersion {
        private final String dimensionId;
        private final String mtrVersion;
        private final Integer railwayDataVersion;

        public RoutefinderVersion(String dimensionId, String mtrVersion, Integer railwayDataVersion) {
            this.dimensionId = dimensionId;
            this.mtrVersion = mtrVersion;
            this.railwayDataVersion = railwayDataVersion;
        }

        public String getDimensionId() {
            return dimensionId;
        }

        public String getMtrVersion() {
            return mtrVersion;
        }

        public Optional<Integer> getRailwayDataVersion() {
            return Optional.ofNullable(railwayDataVersion);
        }
    }

    public static final class RouteFinderDataEntry {
        private final String dimensionId;
        private final long pos;
        private final long routeId;
        private final int duration;
        private final int waitingTime;
        private final List<Long> stationIds;
        private final String source;

        public RouteFinderDataEntry(String dimensionId, long pos, long routeId, int duration, int waitingTime,
                                    List<Long> stationIds, String source) {
            this.dimensionId = dimensionId;
            this.pos = pos;
            this.routeId = routeId;
            this.duration = duration;
            this.waitingTime = waitingTime;
            this.stationIds = stationIds == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(stationIds));
            this.source = source;
        }

        public String getDimensionId() {
            return dimensionId;
        }

        public long getPos() {
            return pos;
        }

        public long getRouteId() {
            return routeId;
        }

        public int getDuration() {
            return duration;
        }

        public int getWaitingTime() {
            return waitingTime;
        }

        public List<Long> getStationIds() {
            return stationIds;
        }

        public String getSource() {
            return source;
        }
    }

    public static final class RouteFinderModuleState {
        private final String dimensionId;
        private final Optional<Long> startPos;
        private final Optional<Long> endPos;
        private final Optional<Integer> totalTime;
        private final Optional<Integer> count;
        private final Optional<Long> startMillis;
        private final Optional<String> tickStage;
        private final Map<Long, Integer> globalBlacklist;
        private final Map<Long, Integer> localBlacklist;

        public RouteFinderModuleState(String dimensionId, long startPos, long endPos, Integer totalTime,
                                      Integer count, Long startMillis, String tickStage,
                                      Map<Long, Integer> globalBlacklist, Map<Long, Integer> localBlacklist) {
            this.dimensionId = dimensionId;
            this.startPos = startPos != 0 ? Optional.of(startPos) : Optional.empty();
            this.endPos = endPos != 0 ? Optional.of(endPos) : Optional.empty();
            this.totalTime = Optional.ofNullable(totalTime);
            this.count = Optional.ofNullable(count);
            this.startMillis = Optional.ofNullable(startMillis);
            this.tickStage = Optional.ofNullable(tickStage);
            this.globalBlacklist = globalBlacklist == null ? Collections.emptyMap() : globalBlacklist;
            this.localBlacklist = localBlacklist == null ? Collections.emptyMap() : localBlacklist;
        }

        public String getDimensionId() {
            return dimensionId;
        }

        public Optional<Long> getStartPos() {
            return startPos;
        }

        public Optional<Long> getEndPos() {
            return endPos;
        }

        public Optional<Integer> getTotalTime() {
            return totalTime;
        }

        public Optional<Integer> getCount() {
            return count;
        }

        public Optional<Long> getStartMillis() {
            return startMillis;
        }

        public Optional<String> getTickStage() {
            return tickStage;
        }

        public Map<Long, Integer> getGlobalBlacklist() {
            return globalBlacklist;
        }

        public Map<Long, Integer> getLocalBlacklist() {
            return localBlacklist;
        }
    }

    public static final class RouteFinderEdge {
        private final String dimensionId;
        private final long routeId;
        private final String source;
        private final int index;
        private final long fromPos;
        private final long toPos;
        private final Integer connectionDensity;

        public RouteFinderEdge(String dimensionId, long routeId, String source, int index,
                               long fromPos, long toPos, Integer connectionDensity) {
            this.dimensionId = dimensionId;
            this.routeId = routeId;
            this.source = source;
            this.index = index;
            this.fromPos = fromPos;
            this.toPos = toPos;
            this.connectionDensity = connectionDensity;
        }

        public String getDimensionId() {
            return dimensionId;
        }

        public long getRouteId() {
            return routeId;
        }

        public String getSource() {
            return source;
        }

        public int getIndex() {
            return index;
        }

        public long getFromPos() {
            return fromPos;
        }

        public long getToPos() {
            return toPos;
        }

        public Optional<Integer> getConnectionDensity() {
            return Optional.ofNullable(connectionDensity);
        }
    }
}
