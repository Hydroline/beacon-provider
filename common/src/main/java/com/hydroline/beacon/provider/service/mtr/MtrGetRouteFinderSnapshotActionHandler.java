package com.hydroline.beacon.provider.service.mtr;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.hydroline.beacon.provider.mtr.MtrJsonWriter;
import com.hydroline.beacon.provider.mtr.MtrQueryGateway;
import com.hydroline.beacon.provider.protocol.BeaconMessage;
import com.hydroline.beacon.provider.protocol.BeaconResponse;
import com.hydroline.beacon.provider.transport.TransportContext;

public final class MtrGetRouteFinderSnapshotActionHandler extends AbstractMtrActionHandler {
    public static final String ACTION = "mtr:get_routefinder_snapshot";

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
        String dimension = getDimension(message);
        JsonArray snapshots = MtrJsonWriter.writeRouteFinderSnapshots(gateway.fetchRouteFinderSnapshots(dimension));
        JsonObject payload = new JsonObject();
        payload.add("snapshots", snapshots);
        if (dimension != null) {
            payload.addProperty("dimension", dimension);
        }
        return ok(message.getRequestId(), payload);
    }

    private static String getDimension(BeaconMessage message) {
        if (message.getPayload() == null || !message.getPayload().has("dimension")) {
            return null;
        }
        return message.getPayload().get("dimension").getAsString();
    }
}
