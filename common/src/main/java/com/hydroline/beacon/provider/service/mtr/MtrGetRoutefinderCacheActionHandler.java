package com.hydroline.beacon.provider.service.mtr;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hydroline.beacon.provider.mtr.RouteFinderCacheEntry;
import com.hydroline.beacon.provider.mtr.RouteFinderCacheEntrySummary;
import com.hydroline.beacon.provider.mtr.RouteFinderCacheStorage;
import com.hydroline.beacon.provider.protocol.BeaconMessage;
import com.hydroline.beacon.provider.protocol.BeaconResponse;
import com.hydroline.beacon.provider.transport.TransportContext;
import java.util.List;
import java.util.Optional;

public final class MtrGetRoutefinderCacheActionHandler extends AbstractMtrActionHandler {
    public static final String ACTION = "mtr:get_routefinder_cache";

    @Override
    public String action() {
        return ACTION;
    }

    @Override
    public BeaconResponse handle(BeaconMessage message, TransportContext context) {
        String dimension = getDimension(message);
        if (dimension == null || dimension.trim().isEmpty()) {
            return invalidPayload(message.getRequestId(), "missing dimension");
        }
        Long routeId = getRouteId(message);
        JsonObject payload = new JsonObject();
        payload.addProperty("dimension", dimension);
        try (RouteFinderCacheStorage storage = new RouteFinderCacheStorage()) {
            if (routeId != null) {
                Optional<RouteFinderCacheEntry> entry = storage.read(dimension, routeId);
                JsonElement entryJson = entry.map(MtrGetRoutefinderCacheActionHandler::toJson)
                    .map(json -> (JsonElement) json)
                    .orElse(JsonNull.INSTANCE);
                payload.add("entry", entryJson);
                payload.addProperty("routeId", routeId);
            } else {
                List<RouteFinderCacheEntrySummary> entries = storage.list(dimension);
                payload.add("entries", toJson(entries));
                payload.addProperty("count", entries.size());
            }
        } catch (Exception ex) {
            if (routeId != null) {
                payload.add("entry", JsonNull.INSTANCE);
                payload.addProperty("routeId", routeId);
            } else {
                payload.add("entries", new JsonArray());
                payload.addProperty("count", 0);
            }
        }
        return ok(message.getRequestId(), payload);
    }

    private static JsonObject toJson(RouteFinderCacheEntry entry) {
        JsonObject json = new JsonObject();
        json.addProperty("dimension", entry.getDimension());
        json.addProperty("routeId", entry.getRouteId());
        if (entry.getRailwayDataVersion() != null) {
            json.addProperty("railwayDataVersion", entry.getRailwayDataVersion());
        }
        if (entry.getRoutefinderVersion() != null) {
            json.addProperty("routefinderVersion", entry.getRoutefinderVersion());
        }
        json.addProperty("formatVersion", entry.getFormatVersion());
        json.addProperty("updatedAt", entry.getUpdatedAt());
        json.add("polyline", parseObject(entry.getPolylineJson()));
        json.add("edges", parseObject(entry.getEdgesJson()));
        json.add("nodes", parseObject(entry.getNodesJson()));
        json.add("state", parseObject(entry.getStateJson()));
        json.add("cost", parseObject(entry.getCostJson()));
        return json;
    }

    private static JsonArray toJson(List<RouteFinderCacheEntrySummary> entries) {
        JsonArray array = new JsonArray();
        for (RouteFinderCacheEntrySummary summary : entries) {
            JsonObject json = new JsonObject();
            json.addProperty("dimension", summary.getDimension());
            json.addProperty("routeId", summary.getRouteId());
            summary.getRailwayDataVersion().ifPresent(value -> json.addProperty("railwayDataVersion", value));
            if (summary.getRoutefinderVersion() != null) {
                json.addProperty("routefinderVersion", summary.getRoutefinderVersion());
            }
            json.addProperty("formatVersion", summary.getFormatVersion());
            json.addProperty("updatedAt", summary.getUpdatedAt());
            array.add(json);
        }
        return array;
    }

    private static JsonObject parseObject(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return new JsonObject();
        }
        try {
            JsonElement element = new JsonParser().parse(raw);
            if (element.isJsonObject()) {
                return element.getAsJsonObject();
            }
        } catch (Exception ignored) {
        }
        return new JsonObject();
    }

    private static String getDimension(BeaconMessage message) {
        if (message.getPayload() == null || !message.getPayload().has("dimension")) {
            return null;
        }
        return message.getPayload().get("dimension").getAsString();
    }

    private static Long getRouteId(BeaconMessage message) {
        if (message.getPayload() == null || !message.getPayload().has("routeId")) {
            return null;
        }
        try {
            return message.getPayload().get("routeId").getAsLong();
        } catch (Exception ex) {
            return null;
        }
    }
}
