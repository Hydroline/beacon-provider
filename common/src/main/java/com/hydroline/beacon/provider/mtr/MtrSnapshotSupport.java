package com.hydroline.beacon.provider.mtr;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

final class MtrSnapshotSupport {
    private MtrSnapshotSupport() {
    }

    static <T> List<T> collectFromSnapshots(List<MtrDimensionSnapshot> snapshots, Function<MtrDimensionSnapshot, List<T>> mapper) {
        if (snapshots == null || snapshots.isEmpty()) {
            return Collections.emptyList();
        }
        return snapshots.stream()
            .map(mapper)
            .filter(list -> list != null && !list.isEmpty())
            .flatMap(List::stream)
            .collect(Collectors.toList());
    }

    static List<MtrDimensionSnapshot> filterSnapshots(List<MtrDimensionSnapshot> snapshots, String dimensionId) {
        if (snapshots == null || snapshots.isEmpty() || dimensionId == null || dimensionId.isEmpty()) {
            return snapshots != null ? snapshots : Collections.emptyList();
        }
        return snapshots.stream()
            .filter(snapshot -> dimensionId.equals(snapshot.getDimensionId()))
            .collect(Collectors.toList());
    }
}
