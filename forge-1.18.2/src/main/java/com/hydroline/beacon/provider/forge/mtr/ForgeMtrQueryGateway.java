package com.hydroline.beacon.provider.forge.mtr;

import com.hydroline.beacon.provider.mtr.MtrDataMapper;
import com.hydroline.beacon.provider.mtr.MtrDimensionSnapshot;
import com.hydroline.beacon.provider.mtr.MtrModels.DepotInfo;
import com.hydroline.beacon.provider.mtr.MtrModels.DimensionOverview;
import com.hydroline.beacon.provider.mtr.MtrModels.FareAreaInfo;
import com.hydroline.beacon.provider.mtr.MtrModels.NodePage;
import com.hydroline.beacon.provider.mtr.MtrModels.RouteDetail;
import com.hydroline.beacon.provider.mtr.MtrModels.StationInfo;
import com.hydroline.beacon.provider.mtr.MtrModels.StationTimetable;
import com.hydroline.beacon.provider.mtr.MtrModels.TrainStatus;
import com.hydroline.beacon.provider.mtr.MtrQueryGateway;
import com.hydroline.beacon.provider.mtr.MtrRailwayDataAccess;
import com.hydroline.beacon.provider.mtr.MtrSnapshotCache;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import mtr.data.RailwayData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ForgeMtrQueryGateway implements MtrQueryGateway {
    private static final Logger LOGGER = LoggerFactory.getLogger(ForgeMtrQueryGateway.class);
    private final Supplier<MinecraftServer> serverSupplier;
    private static final long SNAPSHOT_CACHE_TTL_MILLIS = 1000L;
    private final MtrSnapshotCache snapshotCache;

    public ForgeMtrQueryGateway(Supplier<MinecraftServer> serverSupplier) {
        this.serverSupplier = serverSupplier;
        this.snapshotCache = new MtrSnapshotCache(this::captureSnapshotsNow, SNAPSHOT_CACHE_TTL_MILLIS);
    }

    @Override
    public boolean isReady() {
        return !executeOnServer(this::captureSnapshots, Collections.emptyList()).isEmpty();
    }

    @Override
    public List<DimensionOverview> fetchNetworkOverview() {
        return executeOnServer(() -> MtrDataMapper.buildNetworkOverview(captureSnapshots()), Collections.emptyList());
    }

    @Override
    public Optional<RouteDetail> fetchRouteDetail(String dimensionId, long routeId) {
        return executeOnServer(() -> {
            List<MtrDimensionSnapshot> snapshots = captureSnapshots();
            return findSnapshot(snapshots, dimensionId)
                .flatMap(snapshot -> MtrDataMapper.buildRouteDetail(snapshot, routeId));
        }, Optional.empty());
    }

    @Override
    public List<DepotInfo> fetchDepots(String dimensionId) {
        return executeOnServer(() -> {
            List<MtrDimensionSnapshot> snapshots = captureSnapshots();
            if (dimensionId == null || dimensionId.isEmpty()) {
                return snapshots.stream()
                    .flatMap(snapshot -> MtrDataMapper.buildDepots(snapshot).stream())
                    .collect(Collectors.toList());
            }
            return findSnapshot(snapshots, dimensionId)
                .map(MtrDataMapper::buildDepots)
                .orElseGet(Collections::emptyList);
        }, Collections.emptyList());
    }

    @Override
    public List<FareAreaInfo> fetchFareAreas(String dimensionId) {
        return executeOnServer(() -> {
            List<MtrDimensionSnapshot> snapshots = captureSnapshots();
            return findSnapshot(snapshots, dimensionId)
                .map(MtrDataMapper::buildFareAreas)
                .orElseGet(Collections::emptyList);
        }, Collections.emptyList());
    }

    @Override
    public NodePage fetchNodes(String dimensionId, String cursor, int limit) {
        NodePage fallback = new NodePage(dimensionId == null ? "" : dimensionId, Collections.emptyList(), null);
        return executeOnServer(() -> {
            List<MtrDimensionSnapshot> snapshots = captureSnapshots();
            return findSnapshot(snapshots, dimensionId)
                .map(snapshot -> MtrDataMapper.buildNodePage(snapshot, cursor, limit))
                .orElse(fallback);
        }, fallback);
    }

    @Override
    public Optional<StationTimetable> fetchStationTimetable(String dimensionId, long stationId, Long platformId) {
        return executeOnServer(() -> {
            List<MtrDimensionSnapshot> snapshots = captureSnapshots();
            return findSnapshot(snapshots, dimensionId)
                .flatMap(snapshot -> MtrDataMapper.buildStationTimetable(snapshot, stationId, platformId));
        }, Optional.empty());
    }

    @Override
    public List<StationInfo> fetchStations(String dimensionId) {
        return executeOnServer(() -> {
            List<MtrDimensionSnapshot> snapshots = captureSnapshots();
            if (dimensionId == null || dimensionId.isEmpty()) {
                return snapshots.stream()
                    .flatMap(snapshot -> MtrDataMapper.buildStations(snapshot).stream())
                    .collect(Collectors.toList());
            }
            return findSnapshot(snapshots, dimensionId)
                .map(MtrDataMapper::buildStations)
                .orElseGet(Collections::emptyList);
        }, Collections.emptyList());
    }

    @Override
    public List<TrainStatus> fetchRouteTrains(String dimensionId, long routeId) {
        return executeOnServer(() -> {
            List<MtrDimensionSnapshot> snapshots = captureSnapshots();
            java.util.function.Function<java.util.UUID, String> nameResolver = this::resolvePlayerName;
            return snapshots.stream()
                .filter(snapshot -> dimensionId == null || dimensionId.isEmpty()
                    || snapshot.getDimensionId().equals(dimensionId))
                .flatMap(snapshot -> MtrDataMapper.buildRouteTrains(snapshot, routeId, nameResolver).stream())
                .collect(Collectors.toList());
        }, Collections.emptyList());
    }

    @Override
    public List<TrainStatus> fetchDepotTrains(String dimensionId, long depotId) {
        return executeOnServer(() -> {
            List<MtrDimensionSnapshot> snapshots = captureSnapshots();
            java.util.function.Function<java.util.UUID, String> nameResolver = this::resolvePlayerName;
            return findSnapshot(snapshots, dimensionId)
                .map(snapshot -> MtrDataMapper.buildDepotTrains(snapshot, depotId, nameResolver))
                .orElseGet(Collections::emptyList);
        }, Collections.emptyList());
    }

    private String resolvePlayerName(java.util.UUID uuid) {
        MinecraftServer server = serverSupplier.get();
        if (server != null) {
            net.minecraft.server.level.ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                return player.getGameProfile().getName();
            }
        }
        return null;
    }

    @Override
    public List<MtrDimensionSnapshot> fetchSnapshots() {
        return executeOnServer(this::captureSnapshots, Collections.emptyList());
    }

    private List<MtrDimensionSnapshot> captureSnapshots() {
        return snapshotCache.get();
    }

    private List<MtrDimensionSnapshot> captureSnapshotsNow() {
        MinecraftServer server = serverSupplier.get();
        if (server == null) {
            return Collections.emptyList();
        }
        List<MtrDimensionSnapshot> snapshots = new ArrayList<>();
        try {
            for (ServerLevel level : server.getAllLevels()) {
                try {
                    final RailwayData data = MtrRailwayDataAccess.resolve(level);
                    if (data != null) {
                        snapshots.add(new MtrDimensionSnapshot(resolveDimensionId(level), data));
                    }
                } catch (Throwable throwable) {
                    ResourceLocation id = level.dimension().location();
                    LOGGER.debug("Failed to sample MTR data for {}", id, throwable);
                }
            }
        } catch (Throwable throwable) {
            LOGGER.warn("Unable to enumerate Forge server levels", throwable);
            return Collections.emptyList();
        }
        return snapshots;
    }

    private static String resolveDimensionId(ServerLevel level) {
        return level.dimension().location().toString();
    }

    private Optional<MtrDimensionSnapshot> findSnapshot(List<MtrDimensionSnapshot> snapshots, String dimensionId) {
        if (snapshots.isEmpty() || dimensionId == null || dimensionId.isEmpty()) {
            return Optional.empty();
        }
        return snapshots.stream()
            .filter(snapshot -> snapshot.getDimensionId().equals(dimensionId))
            .findFirst();
    }

    private <T> T executeOnServer(Supplier<T> supplier, T fallback) {
        MinecraftServer server = serverSupplier.get();
        if (server == null) {
            return fallback;
        }
        CompletableFuture<T> future = server.submit(() -> {
            try {
                return supplier.get();
            } catch (Throwable throwable) {
                throw new RuntimeException(throwable);
            }
        });
        try {
            T result = future.get();
            return result == null ? fallback : result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.warn("Interrupted while executing MTR query on server thread", e);
        } catch (ExecutionException e) {
            LOGGER.warn("Failed to execute MTR query on server thread", e.getCause());
        }
        return fallback;
    }
}
