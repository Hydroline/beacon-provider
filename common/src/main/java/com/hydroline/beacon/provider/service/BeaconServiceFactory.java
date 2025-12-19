package com.hydroline.beacon.provider.service;

import com.hydroline.beacon.provider.service.mtr.MtrGetConnectionProfileActionHandler;
import com.hydroline.beacon.provider.service.mtr.MtrGetPlatformPositionMapActionHandler;
import com.hydroline.beacon.provider.service.mtr.MtrGetRailCurveSegmentsActionHandler;
import com.hydroline.beacon.provider.service.mtr.MtrGetRouteFinderSnapshotActionHandler;
import com.hydroline.beacon.provider.service.mtr.MtrGetRoutefinderCacheActionHandler;
import com.hydroline.beacon.provider.service.mtr.MtrGetRoutefinderDataActionHandler;
import com.hydroline.beacon.provider.service.mtr.MtrGetRoutefinderEdgesActionHandler;
import com.hydroline.beacon.provider.service.mtr.MtrGetRoutefinderStateActionHandler;
import com.hydroline.beacon.provider.service.mtr.MtrGetRoutefinderVersionActionHandler;
import com.hydroline.beacon.provider.service.mtr.MtrGetRailwaySnapshotActionHandler;
import java.util.Arrays;

/**
 * Factory helpers to keep loader entrypoints concise.
 */
public final class BeaconServiceFactory {
    private BeaconServiceFactory() {
    }

    public static DefaultBeaconProviderService createDefault() {
        return new DefaultBeaconProviderService(Arrays.asList(
            new PingActionHandler(),
            new MtrGetRailwaySnapshotActionHandler(),
            new MtrGetRouteFinderSnapshotActionHandler(),
            new MtrGetRoutefinderDataActionHandler(),
            new MtrGetRoutefinderStateActionHandler(),
            new MtrGetRoutefinderEdgesActionHandler(),
            new MtrGetRoutefinderCacheActionHandler(),
            new MtrGetConnectionProfileActionHandler(),
            new MtrGetPlatformPositionMapActionHandler(),
            new MtrGetRailCurveSegmentsActionHandler(),
            new MtrGetRoutefinderVersionActionHandler()
        ));
    }
}
