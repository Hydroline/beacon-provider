package com.hydroline.beacon.provider.service.mtr;

import com.google.gson.JsonObject;
import com.hydroline.beacon.provider.mtr.MtrJsonWriter;
import com.hydroline.beacon.provider.mtr.MtrModels;
import com.hydroline.beacon.provider.mtr.MtrQueryGateway;
import com.hydroline.beacon.provider.protocol.BeaconMessage;
import com.hydroline.beacon.provider.protocol.BeaconResponse;
import com.hydroline.beacon.provider.transport.TransportContext;
import java.util.Optional;

public final class MtrGetRoutefinderVersionActionHandler extends AbstractMtrActionHandler {
    public static final String ACTION = "mtr:get_routefinder_version";

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
        String requestedDimension = getDimension(message);
        JsonObject payload = new JsonObject();
        Optional<MtrModels.RoutefinderVersion> version = gateway.fetchRoutefinderVersion(requestedDimension);
        version.ifPresent(value -> payload.add("version", MtrJsonWriter.writeRoutefinderVersion(value)));
        String effectiveDimension = requestedDimension != null ? requestedDimension : version.map(MtrModels.RoutefinderVersion::getDimensionId).orElse(null);
        if (effectiveDimension != null) {
            payload.addProperty("dimension", effectiveDimension);
        }
        if (!payload.has("version")) {
            payload.addProperty("message", "routefinder metadata unavailable");
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
