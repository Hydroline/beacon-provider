package com.hydroline.beacon.provider.mtr;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import mtr.data.DataCache;
import mtr.data.Platform;
import mtr.data.RailwayData;
import mtr.data.RailwayDataRouteFinderModule;
import mtr.data.Route;
import mtr.data.SavedRailBase;

public final class RouteFinderCacheCoordinator {
    private static final Logger LOGGER = Logger.getLogger(RouteFinderCacheCoordinator.class.getName());
    private static final Duration REFRESH_INTERVAL = Duration.ofMinutes(30);
    private static final Duration ROUTE_TIMEOUT = Duration.ofSeconds(20);
    private static final long ROUTE_DELAY_MILLIS = 100L;
    private static final long STALE_TTL_MILLIS = Duration.ofHours(6).toMillis();
    private static final String FORMAT_VERSION = "v1";
    private static final int DEFAULT_MAX_COUNT = 2048;
    private static final Field RAILWAY_DATA_VERSION_FIELD = locateField(RailwayData.class, "DATA_VERSION");
    private static final Field RAILWAY_DATA_ROUTES = locateField(RailwayData.class, "routes");
    private static final Field ROUTE_PLATFORM_IDS = locateField(Route.class, "platformIds");
    private static final Field ROUTE_PLATFORM_ID = locateField(Route.RoutePlatform.class, "platformId");
    private static final Method FIND_ROUTE_METHOD = locateFindRoute();
    private static final Method ROUTE_FINDER_GET_MAX_COUNT = locateMethod(RailwayDataRouteFinderModule.class, "getMaxCount");
    private static final Method SAVED_RAIL_GET_MID_POS = locateMethod(mtr.data.SavedRailBase.class, "getMidPos");
    private static final Method SAVED_RAIL_GET_POSITION = locateSavedRailPositionMethod();
    private static final Field SAVED_RAIL_POSITIONS = locateField(SavedRailBase.class, "positions");
    private static final Field MODULE_GLOBAL_BLACKLIST = locateField(RailwayDataRouteFinderModule.class, "globalBlacklist");
    private static final Field MODULE_LOCAL_BLACKLIST = locateField(RailwayDataRouteFinderModule.class, "localBlacklist");
    private static final Method SERVER_GET_ALL_LEVELS = locateServerGetAllLevels();
    private static final Method SERVER_EXECUTE = locateServerExecute();
    private static final RouteFinderDataAccessor DATA_ACCESSOR = new RouteFinderDataAccessor();

    private static volatile RouteFinderCacheCoordinator INSTANCE;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ScheduledExecutorService scheduler;
    private final ExecutorService worker;
    private final Object server;
    private final RouteFinderCacheStorage storage;

    private RouteFinderCacheCoordinator(Object server) throws Exception {
        this.server = server;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "beacon-routefinder-scheduler"));
        this.worker = Executors.newSingleThreadExecutor(r -> new Thread(r, "beacon-routefinder-worker"));
        this.storage = new RouteFinderCacheStorage();
    }

    public static synchronized void start(Object server) {
        if (server == null) {
            return;
        }
        if (INSTANCE != null) {
            INSTANCE.stop();
        }
        try {
            INSTANCE = new RouteFinderCacheCoordinator(server);
            INSTANCE.startInternal();
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Failed to start routefinder cache coordinator", ex);
        }
    }

    public static synchronized void stop() {
        if (INSTANCE != null) {
            INSTANCE.stopInternal();
            INSTANCE = null;
        }
    }

    private void startInternal() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        scheduler.scheduleWithFixedDelay(this::triggerRefresh, 5, REFRESH_INTERVAL.getSeconds(), TimeUnit.SECONDS);
        LOGGER.info("Routefinder cache scheduler started");
    }

    private void stopInternal() {
        running.set(false);
        scheduler.shutdownNow();
        worker.shutdownNow();
        storage.close();
        LOGGER.info("Routefinder cache scheduler stopped");
    }

    private void triggerRefresh() {
        if (!running.get()) {
            return;
        }
        worker.submit(this::refreshOnce);
    }

    private void refreshOnce() {
        List<Object> levels = getServerLevels(server);
        RefreshStats stats = new RefreshStats();
        long refreshStart = System.currentTimeMillis();
        if (!levels.isEmpty()) {
            for (Object level : levels) {
                RailwayData railwayData = MtrRailwayDataAccess.resolve(level);
                if (railwayData == null) {
                    continue;
                }
                String dimensionId = resolveDimensionId(level);
                if (dimensionId == null) {
                    continue;
                }
                stats.dimensions++;
                Integer railwayDataVersion = readRailwayDataVersion();
                String routefinderVersion = describeVersion(railwayData);
                storage.deleteByVersionMismatch(routefinderVersion, railwayDataVersion);
                runRouteBatch(railwayData, dimensionId, railwayDataVersion, routefinderVersion, stats);
            }
        }
        if (stats.dimensions == 0) {
            runSnapshotFallback(stats);
        }
        storage.deleteStale(refreshStart - STALE_TTL_MILLIS);
        LOGGER.info(String.format(
            "Routefinder cache refresh finished: dimensions=%d routes=%d computed=%d skippedFresh=%d missingPos=%d failed=%d",
            stats.dimensions, stats.routesTotal, stats.computed, stats.skippedFresh, stats.missingPositions, stats.failed
        ));
    }

    private void runSnapshotFallback(RefreshStats stats) {
        List<MtrDimensionSnapshot> snapshots = MtrQueryRegistry.get().fetchSnapshots();
        if (snapshots.isEmpty()) {
            LOGGER.info("Routefinder cache refresh skipped: no server levels available (snapshot fallback empty)");
            return;
        }
        for (MtrDimensionSnapshot snapshot : snapshots) {
            RailwayData railwayData = snapshot.getRailwayData();
            String dimensionId = snapshot.getDimensionId();
            if (railwayData == null || dimensionId == null) {
                continue;
            }
            stats.dimensions++;
            Integer railwayDataVersion = readRailwayDataVersion();
            String routefinderVersion = describeVersion(railwayData);
            storage.deleteByVersionMismatch(routefinderVersion, railwayDataVersion);
            runRouteBatch(railwayData, dimensionId, railwayDataVersion, routefinderVersion, stats);
        }
    }

    private void runRouteBatch(RailwayData railwayData, String dimensionId, Integer railwayDataVersion,
                               String routefinderVersion, RefreshStats stats) {
        if (railwayData == null) {
            return;
        }
        DataCache cache = railwayData.dataCache;
        if (cache == null) {
            return;
        }
        cache.sync();
        Map<Long, Platform> platformMap = cache.platformIdMap;
        Object routesObj = readField(railwayData, RAILWAY_DATA_ROUTES);
        if (!(routesObj instanceof Iterable)) {
            return;
        }
        List<Route> routes = new ArrayList<>();
        for (Object item : (Iterable<?>) routesObj) {
            if (item instanceof Route) {
                routes.add((Route) item);
            }
        }
        Set<Long> activeRouteIds = new HashSet<>();
        for (Route route : routes) {
            if (!running.get()) {
                break;
            }
            if (route == null) {
                continue;
            }
            stats.routesTotal++;
            activeRouteIds.add(route.id);
            List<Object> positions = resolveRoutePositions(route, platformMap);
            if (positions.size() < 2) {
                stats.missingPositions++;
                continue;
            }
            if (stats.debugLogged < 3) {
                LOGGER.info(String.format(
                    "Routefinder cache debug: dimension=%s routeId=%d positions=%d start=%d end=%d maxCount=%d",
                    dimensionId, route.id, positions.size(),
                    encodeBlockPos(positions.get(0)), encodeBlockPos(positions.get(positions.size() - 1)),
                    resolveMaxCount(railwayData.railwayDataRouteFinderModule)
                ));
                stats.debugLogged++;
            }
            RouteFinderCacheEntry entry = computeRouteCacheEntry(railwayData, dimensionId, route.id, positions, railwayDataVersion, routefinderVersion);
            if (entry != null) {
                storage.upsert(entry);
                stats.computed++;
            } else {
                stats.failed++;
            }
            sleepQuietly(ROUTE_DELAY_MILLIS);
        }
        storage.deleteMissingRoutes(dimensionId, activeRouteIds);
    }

    private RouteFinderCacheEntry computeRouteCacheEntry(RailwayData railwayData, String dimensionId, long routeId,
                                                         List<Object> positions, Integer railwayDataVersion, String routefinderVersion) {
        RailwayDataRouteFinderModule module = railwayData.railwayDataRouteFinderModule;
        if (module == null) {
            return null;
        }
        List<JsonObject> edges = new ArrayList<>();
        List<JsonObject> nodes = new ArrayList<>();
        List<JsonObject> costs = new ArrayList<>();
        int edgeIndex = 0;
        for (int i = 0; i < positions.size() - 1; i++) {
            Object start = positions.get(i);
            Object end = positions.get(i + 1);
            if (encodeBlockPos(start) == 0L || encodeBlockPos(end) == 0L) {
                continue;
            }
            RouteResult result = findRoute(module, start, end);
            if (result == null || result.data == null) {
                continue;
            }
            List<RouteFinderDataSnapshot> routeNodes = DATA_ACCESSOR.extract(result.data);
            if (routeNodes.isEmpty()) {
                continue;
            }
            for (int n = 0; n < routeNodes.size(); n++) {
                RouteFinderDataSnapshot node = routeNodes.get(n);
                JsonObject json = new JsonObject();
                json.addProperty("segmentIndex", i);
                json.addProperty("order", n);
                json.addProperty("pos", node.pos);
                json.addProperty("duration", node.duration);
                json.addProperty("waitingTime", node.waitingTime);
                nodes.add(json);
            }
            for (int n = 0; n < routeNodes.size() - 1; n++) {
                RouteFinderDataSnapshot current = routeNodes.get(n);
                RouteFinderDataSnapshot next = routeNodes.get(n + 1);
                JsonObject json = new JsonObject();
                json.addProperty("segmentIndex", i);
                json.addProperty("order", edgeIndex++);
                json.addProperty("fromPos", current.pos);
                json.addProperty("toPos", next.pos);
                edges.add(json);
            }
            JsonObject cost = new JsonObject();
            cost.addProperty("segmentIndex", i);
            cost.addProperty("totalCost", result.totalCost);
            costs.add(cost);
        }
        if (edges.isEmpty()) {
            return null;
        }
        JsonObject state = buildModuleState(module);
        JsonObject polyline = new JsonObject();
        polyline.add("points", new JsonArray());
        polyline.addProperty("step", 0.0);
        polyline.addProperty("formatVersion", FORMAT_VERSION);
        JsonObject edgesJson = new JsonObject();
        edgesJson.add("edges", toJsonArray(edges));
        JsonObject nodesJson = new JsonObject();
        nodesJson.add("nodes", toJsonArray(nodes));
        JsonObject costJson = new JsonObject();
        costJson.add("segments", toJsonArray(costs));
        long updatedAt = System.currentTimeMillis();
        return new RouteFinderCacheEntry(
            dimensionId,
            routeId,
            railwayDataVersion,
            routefinderVersion,
            FORMAT_VERSION,
            polyline.toString(),
            edgesJson.toString(),
            nodesJson.toString(),
            state.toString(),
            costJson.toString(),
            updatedAt
        );
    }

    private RouteResult findRoute(RailwayDataRouteFinderModule module, Object start, Object end) {
        if (FIND_ROUTE_METHOD == null || start == null || end == null) {
            return null;
        }
        CompletableFuture<RouteResult> future = new CompletableFuture<>();
        BiConsumer<Object, Integer> callback = (data, cost) -> future.complete(new RouteResult(data, cost));
        int maxCount = resolveMaxCount(module);
        Runnable task = () -> {
            try {
                Object result = FIND_ROUTE_METHOD.invoke(module, start, end, maxCount, callback);
                if (result instanceof Boolean && !((Boolean) result)) {
                    future.complete(null);
                }
            } catch (IllegalAccessException | InvocationTargetException ex) {
                LOGGER.log(Level.WARNING, "Failed to invoke findRoute", ex);
                future.complete(null);
            }
        };
        executeOnServer(task);
        try {
            return future.get(ROUTE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Routefinder timed out", ex);
            return null;
        }
    }

    private int resolveMaxCount(RailwayDataRouteFinderModule module) {
        if (ROUTE_FINDER_GET_MAX_COUNT == null || module == null) {
            return DEFAULT_MAX_COUNT;
        }
        try {
            Object value = ROUTE_FINDER_GET_MAX_COUNT.invoke(module);
            if (value instanceof Integer) {
                int result = (Integer) value;
                return result > 0 ? result : DEFAULT_MAX_COUNT;
            }
        } catch (IllegalAccessException | InvocationTargetException ignored) {
        }
        return DEFAULT_MAX_COUNT;
    }

    private void executeOnServer(Runnable task) {
        if (SERVER_EXECUTE == null || server == null) {
            task.run();
            return;
        }
        try {
            SERVER_EXECUTE.invoke(server, task);
        } catch (IllegalAccessException | InvocationTargetException ex) {
            task.run();
        }
    }


    private static List<Object> resolveRoutePositions(Route route, Map<Long, Platform> platformMap) {
        if (route == null || platformMap == null || platformMap.isEmpty() || ROUTE_PLATFORM_IDS == null) {
            return Collections.emptyList();
        }
        Object platformListObj = readField(route, ROUTE_PLATFORM_IDS);
        if (!(platformListObj instanceof List)) {
            return Collections.emptyList();
        }
        List<?> platformRefs = (List<?>) platformListObj;
        if (platformRefs.isEmpty()) {
            return Collections.emptyList();
        }
        List<Object> positions = new ArrayList<>();
        for (Object ref : platformRefs) {
            if (ref == null) {
                continue;
            }
            Long platformId = readPlatformId(ref);
            if (platformId == null) {
                continue;
            }
            Platform platform = platformMap.get(platformId);
            if (platform == null) {
                continue;
            }
            Object pos = selectPlatformPosition(platform);
            if (pos != null) {
                positions.add(pos);
            }
        }
        return positions;
    }

    private static Object selectPlatformPosition(Platform platform) {
        if (platform == null) {
            return null;
        }
        Object pos0 = invoke(SAVED_RAIL_GET_POSITION, platform, 0);
        if (encodeBlockPos(pos0) != 0L) {
            return pos0;
        }
        Object pos1 = invoke(SAVED_RAIL_GET_POSITION, platform, 1);
        if (encodeBlockPos(pos1) != 0L) {
            return pos1;
        }
        Object positionsObj = readField(platform, SAVED_RAIL_POSITIONS);
        Object candidate = selectFirstPosition(positionsObj);
        if (candidate != null) {
            return candidate;
        }
        Object fallbackPositions = readPositionsFromFields(platform);
        candidate = selectFirstPosition(fallbackPositions);
        if (candidate != null) {
            return candidate;
        }
        return invoke(SAVED_RAIL_GET_MID_POS, platform);
    }

    private static Object selectFirstPosition(Object positionsObj) {
        if (positionsObj instanceof Iterable) {
            Object best = null;
            long bestPacked = Long.MAX_VALUE;
            for (Object item : (Iterable<?>) positionsObj) {
                long packed = encodeBlockPos(item);
                if (packed != 0L && packed < bestPacked) {
                    bestPacked = packed;
                    best = item;
                }
            }
            return best;
        }
        return null;
    }

    private static Object readPositionsFromFields(Object target) {
        if (target == null) {
            return null;
        }
        Class<?> type = target.getClass();
        while (type != null) {
            for (Field field : type.getDeclaredFields()) {
                if (!java.util.Set.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                field.setAccessible(true);
                try {
                    Object value = field.get(target);
                    if (value instanceof Iterable) {
                        for (Object candidate : (Iterable<?>) value) {
                            if (encodeBlockPos(candidate) != 0L) {
                                return value;
                            }
                        }
                    }
                } catch (IllegalAccessException ignored) {
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }

    private static Long readPlatformId(Object routePlatform) {
        if (ROUTE_PLATFORM_ID == null || routePlatform == null) {
            return null;
        }
        try {
            Object value = ROUTE_PLATFORM_ID.get(routePlatform);
            if (value instanceof Number) {
                return ((Number) value).longValue();
            }
        } catch (IllegalAccessException ignored) {
        }
        return null;
    }

    private static List<Object> getServerLevels(Object server) {
        if (server == null) {
            return Collections.emptyList();
        }
        List<Object> levels = invokeLevels(server, SERVER_GET_ALL_LEVELS);
        if (!levels.isEmpty()) {
            return levels;
        }
        Method fallback = locateServerLevelsFallback(server);
        if (fallback == null) {
            return collectLevelsFromFields(server);
        }
        levels = invokeLevels(server, fallback);
        if (!levels.isEmpty()) {
            return levels;
        }
        return collectLevelsFromFields(server);
    }

    private static List<Object> invokeLevels(Object server, Method method) {
        if (method == null) {
            return Collections.emptyList();
        }
        try {
            Object result = method.invoke(server);
            if (result instanceof Iterable) {
                List<Object> levels = new ArrayList<>();
                for (Object level : (Iterable<?>) result) {
                    levels.add(level);
                }
                return levels;
            }
            if (result != null && result.getClass().isArray()) {
                int length = java.lang.reflect.Array.getLength(result);
                List<Object> levels = new ArrayList<>(length);
                for (int i = 0; i < length; i++) {
                    levels.add(java.lang.reflect.Array.get(result, i));
                }
                return levels;
            }
        } catch (IllegalAccessException | InvocationTargetException ex) {
            LOGGER.log(Level.WARNING, "Failed to enumerate server levels", ex);
        }
        return Collections.emptyList();
    }

    private static Method locateServerLevelsFallback(Object server) {
        String[] names = new String[] {"getAllLevels", "getLevels", "getWorlds"};
        for (String name : names) {
            try {
                Method method = server.getClass().getMethod(name);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }

    private static List<Object> collectLevelsFromFields(Object server) {
        List<Object> levels = new ArrayList<>();
        Class<?> type = server.getClass();
        while (type != null) {
            for (Field field : type.getDeclaredFields()) {
                field.setAccessible(true);
                Object value;
                try {
                    value = field.get(server);
                } catch (IllegalAccessException ex) {
                    continue;
                }
                if (value instanceof Map) {
                    for (Object candidate : ((Map<?, ?>) value).values()) {
                        if (isLevelLike(candidate)) {
                            levels.add(candidate);
                        }
                    }
                } else if (value instanceof Iterable) {
                    for (Object candidate : (Iterable<?>) value) {
                        if (isLevelLike(candidate)) {
                            levels.add(candidate);
                        }
                    }
                } else if (value != null && value.getClass().isArray()) {
                    int length = java.lang.reflect.Array.getLength(value);
                    for (int i = 0; i < length; i++) {
                        Object candidate = java.lang.reflect.Array.get(value, i);
                        if (isLevelLike(candidate)) {
                            levels.add(candidate);
                        }
                    }
                }
            }
            type = type.getSuperclass();
        }
        return levels;
    }

    private static boolean isLevelLike(Object candidate) {
        if (candidate == null) {
            return false;
        }
        String name = candidate.getClass().getName();
        return name.contains("ServerLevel") || name.contains("Level") || name.contains("World");
    }

    private static String resolveDimensionId(Object level) {
        if (level == null) {
            return null;
        }
        try {
            Method dimensionMethod = level.getClass().getMethod("dimension");
            dimensionMethod.setAccessible(true);
            Object dimension = dimensionMethod.invoke(level);
            if (dimension == null) {
                return null;
            }
            try {
                Method locationMethod = dimension.getClass().getMethod("location");
                locationMethod.setAccessible(true);
                Object location = locationMethod.invoke(dimension);
                return location != null ? location.toString() : dimension.toString();
            } catch (NoSuchMethodException ignored) {
                return dimension.toString();
            }
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ex) {
            return null;
        }
    }

    private static Integer readRailwayDataVersion() {
        if (RAILWAY_DATA_VERSION_FIELD == null) {
            return null;
        }
        try {
            Object value = RAILWAY_DATA_VERSION_FIELD.get(null);
            if (value instanceof Integer) {
                return (Integer) value;
            }
        } catch (IllegalAccessException ignored) {
        }
        return null;
    }

    private static String describeVersion(RailwayData data) {
        if (data == null) {
            return "unknown";
        }
        Package pkg = data.getClass().getPackage();
        if (pkg == null) {
            return "unknown";
        }
        String version = pkg.getImplementationVersion();
        if (version == null || version.trim().isEmpty()) {
            version = pkg.getSpecificationVersion();
        }
        return version != null && !version.trim().isEmpty() ? version : "unknown";
    }

    private static JsonObject buildModuleState(RailwayDataRouteFinderModule module) {
        JsonObject json = new JsonObject();
        if (module == null) {
            return json;
        }
        json.add("globalBlacklist", writeLongIntMap(readField(module, MODULE_GLOBAL_BLACKLIST)));
        json.add("localBlacklist", writeLongIntMap(readField(module, MODULE_LOCAL_BLACKLIST)));
        return json;
    }

    private static JsonArray toJsonArray(List<JsonObject> items) {
        JsonArray array = new JsonArray();
        for (JsonObject item : items) {
            array.add(item);
        }
        return array;
    }

    private static void sleepQuietly(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private static Object readField(Object target, Field field) {
        if (target == null || field == null) {
            return null;
        }
        try {
            return field.get(target);
        } catch (IllegalAccessException ex) {
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
            return null;
        }
    }

    private static Object invoke(Method method, Object target, int value) {
        if (method == null || target == null) {
            return null;
        }
        try {
            return method.invoke(target, value);
        } catch (IllegalAccessException | InvocationTargetException ex) {
            return null;
        }
    }

    private static Method locateFindRoute() {
        for (Method method : RailwayDataRouteFinderModule.class.getMethods()) {
            if ("findRoute".equals(method.getName()) && method.getParameterCount() == 4) {
                method.setAccessible(true);
                return method;
            }
        }
        return null;
    }

    private static Method locateServerGetAllLevels() {
        return locateMethodFromName("getAllLevels");
    }

    private static Method locateServerExecute() {
        return locateMethodFromName("execute", Runnable.class);
    }

    private static Method locateMethodFromName(String name, Class<?>... params) {
        try {
            Class<?> serverClass = Class.forName("net.minecraft.server.MinecraftServer");
            Method method = serverClass.getMethod(name, params);
            method.setAccessible(true);
            return method;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Method locateMethod(Class<?> type, String name) {
        try {
            Method method = type.getDeclaredMethod(name);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static Method locateMethodWithParams(Class<?> type, String name, Class<?>... params) {
        try {
            Method method = type.getDeclaredMethod(name, params);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static Method locateSavedRailPositionMethod() {
        Method method = locateMethodWithParams(mtr.data.SavedRailBase.class, "getPosition", int.class);
        if (method != null) {
            return method;
        }
        for (Method candidate : mtr.data.SavedRailBase.class.getDeclaredMethods()) {
            if (candidate.getParameterCount() == 1 && candidate.getParameterTypes()[0] == int.class) {
                if (candidate.getReturnType().getName().contains("BlockPos")) {
                    candidate.setAccessible(true);
                    return candidate;
                }
            }
        }
        return null;
    }

    private static Field locateField(Class<?> type, String name) {
        try {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException ignored) {
            return null;
        }
    }

    private static final class RouteResult {
        private final Object data;
        private final int totalCost;

        private RouteResult(Object data, int totalCost) {
            this.data = data;
            this.totalCost = totalCost;
        }
    }

    private static final class RouteFinderDataAccessor {
        private final Field posField = locateField(RailwayDataRouteFinderModule.RouteFinderData.class, "pos");
        private final Field durationField = locateField(RailwayDataRouteFinderModule.RouteFinderData.class, "duration");
        private final Field waitingField = locateField(RailwayDataRouteFinderModule.RouteFinderData.class, "waitingTime");

        List<RouteFinderDataSnapshot> extract(Object data) {
            if (!(data instanceof List)) {
                return Collections.emptyList();
            }
            List<?> raw = (List<?>) data;
            if (raw.isEmpty()) {
                return Collections.emptyList();
            }
            List<RouteFinderDataSnapshot> result = new ArrayList<>();
            for (Object item : raw) {
                long pos = encodeBlockPos(readField(item, posField));
                int duration = toInt(readField(item, durationField));
                int waiting = toInt(readField(item, waitingField));
                result.add(new RouteFinderDataSnapshot(pos, duration, waiting));
            }
            return result;
        }

        private int toInt(Object value) {
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
            return 0;
        }
    }

    private static final class RouteFinderDataSnapshot {
        private final long pos;
        private final int duration;
        private final int waitingTime;

        private RouteFinderDataSnapshot(long pos, int duration, int waitingTime) {
            this.pos = pos;
            this.duration = duration;
            this.waitingTime = waitingTime;
        }
    }

    private static final class RefreshStats {
        private int dimensions;
        private int routesTotal;
        private int computed;
        private int skippedFresh;
        private int missingPositions;
        private int failed;
        private int debugLogged;
    }

    private static long encodeBlockPos(Object blockPos) {
        if (blockPos == null) {
            return 0L;
        }
        try {
            Method method = blockPos.getClass().getMethod("asLong");
            method.setAccessible(true);
            Object value = method.invoke(blockPos);
            return value instanceof Long ? (Long) value : 0L;
        } catch (NoSuchMethodException ex) {
            for (Method candidate : blockPos.getClass().getMethods()) {
                if (candidate.getParameterCount() == 0 && candidate.getReturnType() == long.class) {
                    try {
                        candidate.setAccessible(true);
                        Object value = candidate.invoke(blockPos);
                        return value instanceof Long ? (Long) value : 0L;
                    } catch (IllegalAccessException | InvocationTargetException ignored) {
                    }
                }
            }
            return 0L;
        } catch (IllegalAccessException | InvocationTargetException ex) {
            return 0L;
        }
    }

    private static JsonObject writeLongIntMap(Object raw) {
        JsonObject json = new JsonObject();
        if (raw == null) {
            return json;
        }
        if (raw instanceof it.unimi.dsi.fastutil.longs.Long2IntMap) {
            it.unimi.dsi.fastutil.longs.Long2IntMap map = (it.unimi.dsi.fastutil.longs.Long2IntMap) raw;
            map.long2IntEntrySet().forEach(entry -> json.addProperty(Long.toString(entry.getLongKey()), entry.getIntValue()));
            return json;
        }
        if (raw instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) raw;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null && entry.getValue() instanceof Number) {
                    json.addProperty(entry.getKey().toString(), ((Number) entry.getValue()).intValue());
                }
            }
        }
        return json;
    }
}
