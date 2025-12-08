package com.hydroline.beacon.provider.fabric;

import com.hydroline.beacon.provider.BeaconProviderMod;
import com.hydroline.beacon.provider.fabric.network.FabricBeaconNetwork;
import net.fabricmc.api.ModInitializer;

/**
 * Fabric entrypoint delegating into the shared bootstrap.
 */
public final class BeaconProviderFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        BeaconProviderMod.init();
        new FabricBeaconNetwork();
    }
}
