package alku.beryllium.bridge;

import alku.beryllium.compute.JavaComputeKernels;

/**
 * Entry point for the native backend.
 *
 * The Java side stays authoritative and falls back to pure Java when the
 * native library is unavailable or a native call cannot complete.
 */
public final class NativeBridge {
    private static volatile NativeStatus status = NativeStatus.UNAVAILABLE;
    private static volatile boolean initialized;

    private NativeBridge() {
    }

    public static synchronized NativeStatus initialize() {
        if (!initialized) {
            status = NativeLibraryLoader.tryLoad() ? NativeStatus.OK : NativeStatus.UNAVAILABLE;
            initialized = true;
        }

        return status;
    }

    public static boolean isLoaded() {
        return status.isSuccess();
    }

    public static NativeStatus status() {
        return status;
    }

    public static long[] computeSquaredDistances(int originX, int originY, int originZ, int[] positions) {
        JavaComputeKernels.validatePositions(positions);

        if (!isLoaded()) {
            return JavaComputeKernels.squaredDistances(originX, originY, originZ, positions);
        }

        long[] output = new long[positions.length / 3];
        NativeStatus nativeStatus = NativeStatus.fromCode(computeSquaredDistancesNative(
            originX,
            originY,
            originZ,
            positions,
            output
        ));
        if (!nativeStatus.isSuccess()) {
            return JavaComputeKernels.squaredDistances(originX, originY, originZ, positions);
        }

        return output;
    }

    public static double[] computeSquaredDistances(double originX, double originY, double originZ, double[] positions) {
        JavaComputeKernels.validatePositions(positions);

        if (!isLoaded()) {
            return JavaComputeKernels.squaredDistances(originX, originY, originZ, positions);
        }

        double[] output = new double[positions.length / 3];
        NativeStatus nativeStatus = NativeStatus.fromCode(computeSquaredDistancesDoubleNative(
            originX,
            originY,
            originZ,
            positions,
            output
        ));
        if (!nativeStatus.isSuccess()) {
            return JavaComputeKernels.squaredDistances(originX, originY, originZ, positions);
        }

        return output;
    }

    public static double[] computeSquaredDistances(double[] origin, double[] positions) {
        if (origin == null || origin.length != 3) {
            throw new IllegalArgumentException("origin must contain x/y/z triples");
        }

        return computeSquaredDistances(origin[0], origin[1], origin[2], positions);
    }

    public static double computePotentialEnergyChange(
        int originX,
        int originY,
        int originZ,
        int[] positions,
        double[] charges,
        double chargeMultiplier
    ) {
        if (chargeMultiplier == 0.0) {
            return 0.0;
        }

        JavaComputeKernels.validatePositions(positions);
        JavaComputeKernels.validateCharges(positions, charges);

        if (!isLoaded()) {
            return JavaComputeKernels.potentialEnergyChange(originX, originY, originZ, positions, charges, chargeMultiplier);
        }

        double[] output = new double[1];
        NativeStatus nativeStatus = NativeStatus.fromCode(computePotentialEnergyChangeNative(
            originX,
            originY,
            originZ,
            positions,
            charges,
            chargeMultiplier,
            output
        ));
        if (!nativeStatus.isSuccess()) {
            return JavaComputeKernels.potentialEnergyChange(originX, originY, originZ, positions, charges, chargeMultiplier);
        }

        return output[0];
    }

    public static int findNearestIndex(double originX, double originY, double originZ, double maxDistanceSquared, double[] positions) {
        JavaComputeKernels.validatePositions(positions);

        if (!isLoaded()) {
            return JavaComputeKernels.findNearestIndex(originX, originY, originZ, maxDistanceSquared, positions);
        }

        int nativeIndex = findNearestIndexDoubleNative(
            originX,
            originY,
            originZ,
            maxDistanceSquared,
            positions
        );
        if (nativeIndex < -1) {
            return JavaComputeKernels.findNearestIndex(originX, originY, originZ, maxDistanceSquared, positions);
        }

        return nativeIndex;
    }

    public static int findNearestIndexExclusive(double originX, double originY, double originZ, double maxDistanceSquared, double[] positions) {
        JavaComputeKernels.validatePositions(positions);

        if (!isLoaded()) {
            return JavaComputeKernels.findNearestIndexExclusive(originX, originY, originZ, maxDistanceSquared, positions);
        }

        int nativeIndex = findNearestIndexExclusiveDoubleNative(
            originX,
            originY,
            originZ,
            maxDistanceSquared,
            positions
        );
        if (nativeIndex < -1) {
            return JavaComputeKernels.findNearestIndexExclusive(originX, originY, originZ, maxDistanceSquared, positions);
        }

        return nativeIndex;
    }

    public static boolean hasAnyWithinRadiusExclusive(double originX, double originY, double originZ, double maxDistanceSquared, double[] positions) {
        JavaComputeKernels.validatePositions(positions);

        if (!isLoaded()) {
            return JavaComputeKernels.hasAnyWithinRadiusExclusive(originX, originY, originZ, maxDistanceSquared, positions);
        }

        int nativeResult = hasAnyWithinRadiusExclusiveDoubleNative(
            originX,
            originY,
            originZ,
            maxDistanceSquared,
            positions
        );
        if (nativeResult < 0) {
            return JavaComputeKernels.hasAnyWithinRadiusExclusive(originX, originY, originZ, maxDistanceSquared, positions);
        }

        return nativeResult != 0;
    }

    public static int findNearestBlockCenterIndex(double originX, double originY, double originZ, int[] positions) {
        JavaComputeKernels.validatePositions(positions);

        if (!isLoaded()) {
            return JavaComputeKernels.findNearestBlockCenterIndex(originX, originY, originZ, positions);
        }

        int nativeIndex = findNearestBlockCenterIndexNative(
            originX,
            originY,
            originZ,
            positions
        );
        if (nativeIndex < -1) {
            return JavaComputeKernels.findNearestBlockCenterIndex(originX, originY, originZ, positions);
        }

        return nativeIndex;
    }

    public static int findNearestBlockCornerIndex(int originX, int originY, int originZ, int[] positions) {
        JavaComputeKernels.validatePositions(positions);

        if (!isLoaded()) {
            return JavaComputeKernels.findNearestBlockCornerIndex(originX, originY, originZ, positions);
        }

        int nativeIndex = findNearestBlockCornerIndexNative(
            originX,
            originY,
            originZ,
            positions
        );
        if (nativeIndex < -1) {
            return JavaComputeKernels.findNearestBlockCornerIndex(originX, originY, originZ, positions);
        }

        return nativeIndex;
    }

    public static int findNearestBlockCornerIndexWithinRadius(int originX, int originY, int originZ, long radiusSquared, int[] positions) {
        JavaComputeKernels.validatePositions(positions);
        if (radiusSquared < 0) {
            throw new IllegalArgumentException("radiusSquared must be non-negative");
        }

        if (!isLoaded()) {
            return JavaComputeKernels.findNearestBlockCornerIndexWithinRadius(originX, originY, originZ, radiusSquared, positions);
        }

        int nativeIndex = findNearestBlockCornerIndexWithinRadiusNative(
            originX,
            originY,
            originZ,
            radiusSquared,
            positions
        );
        if (nativeIndex < -1) {
            return JavaComputeKernels.findNearestBlockCornerIndexWithinRadius(originX, originY, originZ, radiusSquared, positions);
        }

        return nativeIndex;
    }

    public static int[] filterWithinRadius(double originX, double originY, double originZ, double radiusSquared, double[] positions) {
        JavaComputeKernels.validatePositions(positions);
        if (radiusSquared < 0.0) {
            throw new IllegalArgumentException("radiusSquared must be non-negative");
        }

        if (!isLoaded()) {
            return JavaComputeKernels.filterWithinRadius(originX, originY, originZ, radiusSquared, positions);
        }

        int[] output = new int[positions.length / 3];
        int nativeCount = filterWithinRadiusDoubleNative(
            originX,
            originY,
            originZ,
            radiusSquared,
            positions,
            output
        );
        if (nativeCount < 0) {
            return JavaComputeKernels.filterWithinRadius(originX, originY, originZ, radiusSquared, positions);
        }

        return java.util.Arrays.copyOf(output, nativeCount);
    }

    public static int[] filterWithinRadiusExclusive(double originX, double originY, double originZ, double radiusSquared, double[] positions) {
        JavaComputeKernels.validatePositions(positions);
        if (radiusSquared < 0.0) {
            throw new IllegalArgumentException("radiusSquared must be non-negative");
        }

        if (!isLoaded()) {
            return JavaComputeKernels.filterWithinRadiusExclusive(originX, originY, originZ, radiusSquared, positions);
        }

        int[] output = new int[positions.length / 3];
        int nativeCount = filterWithinRadiusExclusiveDoubleNative(
            originX,
            originY,
            originZ,
            radiusSquared,
            positions,
            output
        );
        if (nativeCount < 0) {
            return JavaComputeKernels.filterWithinRadiusExclusive(originX, originY, originZ, radiusSquared, positions);
        }

        return java.util.Arrays.copyOf(output, nativeCount);
    }

    public static int[] sortWithinRadiusExclusive(double originX, double originY, double originZ, double radiusSquared, double[] positions) {
        JavaComputeKernels.validatePositions(positions);
        if (radiusSquared < 0.0) {
            throw new IllegalArgumentException("radiusSquared must be non-negative");
        }

        if (!isLoaded()) {
            return JavaComputeKernels.sortWithinRadiusExclusive(originX, originY, originZ, radiusSquared, positions);
        }

        int[] output = new int[positions.length / 3];
        int count = sortWithinRadiusExclusive(
            originX,
            originY,
            originZ,
            radiusSquared,
            positions,
            output
        );

        return java.util.Arrays.copyOf(output, count);
    }

    public static int sortWithinRadiusExclusive(
        double originX,
        double originY,
        double originZ,
        double radiusSquared,
        double[] positions,
        int[] output
    ) {
        JavaComputeKernels.validatePositions(positions);
        if (radiusSquared < 0.0) {
            throw new IllegalArgumentException("radiusSquared must be non-negative");
        }
        JavaComputeKernels.validateOutputCapacity(positions.length / 3, output);

        if (!isLoaded()) {
            return JavaComputeKernels.sortWithinRadiusExclusive(originX, originY, originZ, radiusSquared, positions, output);
        }

        int nativeCount = sortWithinRadiusExclusiveDoubleNative(
            originX,
            originY,
            originZ,
            radiusSquared,
            positions,
            output
        );
        if (nativeCount < 0) {
            return JavaComputeKernels.sortWithinRadiusExclusive(originX, originY, originZ, radiusSquared, positions, output);
        }

        return nativeCount;
    }

    public static int[] filterWithinRadii(double originX, double originY, double originZ, double[] positions, double[] radiiSquared) {
        JavaComputeKernels.validatePositions(positions);
        JavaComputeKernels.validateRadii(positions, radiiSquared);

        if (!isLoaded()) {
            return JavaComputeKernels.filterWithinRadii(originX, originY, originZ, positions, radiiSquared);
        }

        int[] output = new int[positions.length / 3];
        int nativeCount = filterWithinRadiiDoubleNative(
            originX,
            originY,
            originZ,
            positions,
            radiiSquared,
            output
        );
        if (nativeCount < 0) {
            return JavaComputeKernels.filterWithinRadii(originX, originY, originZ, positions, radiiSquared);
        }

        return java.util.Arrays.copyOf(output, nativeCount);
    }

    public static int[] filterWithinRadius(int originX, int originY, int originZ, long radiusSquared, int[] positions) {
        JavaComputeKernels.validatePositions(positions);
        if (radiusSquared < 0) {
            throw new IllegalArgumentException("radiusSquared must be non-negative");
        }

        if (!isLoaded()) {
            return JavaComputeKernels.filterWithinRadius(originX, originY, originZ, radiusSquared, positions);
        }

        int[] output = new int[positions.length / 3];
        int nativeCount = filterWithinRadiusNative(
            originX,
            originY,
            originZ,
            radiusSquared,
            positions,
            output
        );
        if (nativeCount < 0) {
            return JavaComputeKernels.filterWithinRadius(originX, originY, originZ, radiusSquared, positions);
        }

        return java.util.Arrays.copyOf(output, nativeCount);
    }

    public static int countWithinRadius(int originX, int originY, int originZ, long radiusSquared, int[] positions) {
        JavaComputeKernels.validatePositions(positions);
        if (radiusSquared < 0) {
            throw new IllegalArgumentException("radiusSquared must be non-negative");
        }

        if (!isLoaded()) {
            return JavaComputeKernels.countWithinRadius(originX, originY, originZ, radiusSquared, positions);
        }

        int nativeCount = countWithinRadiusNative(originX, originY, originZ, radiusSquared, positions);
        if (nativeCount < 0) {
            return JavaComputeKernels.countWithinRadius(originX, originY, originZ, radiusSquared, positions);
        }

        return nativeCount;
    }

    public static int[] filterWithinAabb(
        double minX,
        double minY,
        double minZ,
        double maxX,
        double maxY,
        double maxZ,
        double[] positions
    ) {
        JavaComputeKernels.validatePositions(positions);

        if (!isLoaded()) {
            return JavaComputeKernels.filterWithinAabb(minX, minY, minZ, maxX, maxY, maxZ, positions);
        }

        int[] output = new int[positions.length / 3];
        int nativeCount = filterWithinAabbDoubleNative(
            minX,
            minY,
            minZ,
            maxX,
            maxY,
            maxZ,
            positions,
            output
        );
        if (nativeCount < 0) {
            return JavaComputeKernels.filterWithinAabb(minX, minY, minZ, maxX, maxY, maxZ, positions);
        }

        return java.util.Arrays.copyOf(output, nativeCount);
    }

    public static int[] filterIntersectingAabb(
        double queryMinX,
        double queryMinY,
        double queryMinZ,
        double queryMaxX,
        double queryMaxY,
        double queryMaxZ,
        double[] boxes
    ) {
        JavaComputeKernels.validateBoxes(boxes);

        if (!isLoaded()) {
            return JavaComputeKernels.filterIntersectingAabb(
                queryMinX,
                queryMinY,
                queryMinZ,
                queryMaxX,
                queryMaxY,
                queryMaxZ,
                boxes
            );
        }

        int[] output = new int[boxes.length / 6];
        int nativeCount = filterIntersectingAabbDoubleNative(
            queryMinX,
            queryMinY,
            queryMinZ,
            queryMaxX,
            queryMaxY,
            queryMaxZ,
            boxes,
            output
        );
        if (nativeCount < 0) {
            return JavaComputeKernels.filterIntersectingAabb(
                queryMinX,
                queryMinY,
                queryMinZ,
                queryMaxX,
                queryMaxY,
                queryMaxZ,
                boxes
            );
        }

        return java.util.Arrays.copyOf(output, nativeCount);
    }

    public static int[] sortByDistance(int originX, int originY, int originZ, int[] positions) {
        JavaComputeKernels.validatePositions(positions);

        if (!isLoaded()) {
            return JavaComputeKernels.sortByDistance(originX, originY, originZ, positions);
        }

        int[] output = new int[positions.length / 3];
        NativeStatus nativeStatus = NativeStatus.fromCode(sortByDistanceNative(
            originX,
            originY,
            originZ,
            positions,
            output
        ));
        if (!nativeStatus.isSuccess()) {
            return JavaComputeKernels.sortByDistance(originX, originY, originZ, positions);
        }

        return output;
    }

    public static int[] sortByBlockDistance(int originX, int originY, int originZ, int[] positions) {
        JavaComputeKernels.validatePositions(positions);

        if (!isLoaded()) {
            return JavaComputeKernels.sortByBlockDistance(originX, originY, originZ, positions);
        }

        int[] output = new int[positions.length / 3];
        NativeStatus nativeStatus = NativeStatus.fromCode(sortByBlockDistanceNative(
            originX,
            originY,
            originZ,
            positions,
            output
        ));
        if (!nativeStatus.isSuccess()) {
            return JavaComputeKernels.sortByBlockDistance(originX, originY, originZ, positions);
        }

        return output;
    }

    public static int[] sortByDistance(double originX, double originY, double originZ, double[] positions) {
        JavaComputeKernels.validatePositions(positions);

        if (!isLoaded()) {
            return JavaComputeKernels.sortByDistance(originX, originY, originZ, positions);
        }

        int[] output = new int[positions.length / 3];
        NativeStatus nativeStatus = NativeStatus.fromCode(sortByDistanceDoubleNative(
            originX,
            originY,
            originZ,
            positions,
            output
        ));
        if (!nativeStatus.isSuccess()) {
            return JavaComputeKernels.sortByDistance(originX, originY, originZ, positions);
        }

        return output;
    }

    private static native int computeSquaredDistancesNative(
        int originX,
        int originY,
        int originZ,
        int[] positions,
        long[] output
    );

    private static native int computeSquaredDistancesDoubleNative(
        double originX,
        double originY,
        double originZ,
        double[] positions,
        double[] output
    );

    private static native int computePotentialEnergyChangeNative(
        int originX,
        int originY,
        int originZ,
        int[] positions,
        double[] charges,
        double chargeMultiplier,
        double[] output
    );

    private static native int filterWithinRadiusNative(
        int originX,
        int originY,
        int originZ,
        long radiusSquared,
        int[] positions,
        int[] output
    );

    private static native int countWithinRadiusNative(
        int originX,
        int originY,
        int originZ,
        long radiusSquared,
        int[] positions
    );

    private static native int filterWithinRadiusDoubleNative(
        double originX,
        double originY,
        double originZ,
        double radiusSquared,
        double[] positions,
        int[] output
    );

    private static native int filterWithinRadiusExclusiveDoubleNative(
        double originX,
        double originY,
        double originZ,
        double radiusSquared,
        double[] positions,
        int[] output
    );

    private static native int filterWithinRadiiDoubleNative(
        double originX,
        double originY,
        double originZ,
        double[] positions,
        double[] radiiSquared,
        int[] output
    );

    private static native int findNearestIndexDoubleNative(
        double originX,
        double originY,
        double originZ,
        double maxDistanceSquared,
        double[] positions
    );

    private static native int findNearestIndexExclusiveDoubleNative(
        double originX,
        double originY,
        double originZ,
        double maxDistanceSquared,
        double[] positions
    );

    private static native int hasAnyWithinRadiusExclusiveDoubleNative(
        double originX,
        double originY,
        double originZ,
        double maxDistanceSquared,
        double[] positions
    );

    private static native int findNearestBlockCenterIndexNative(
        double originX,
        double originY,
        double originZ,
        int[] positions
    );

    private static native int findNearestBlockCornerIndexNative(
        int originX,
        int originY,
        int originZ,
        int[] positions
    );

    private static native int findNearestBlockCornerIndexWithinRadiusNative(
        int originX,
        int originY,
        int originZ,
        long radiusSquared,
        int[] positions
    );

    private static native int filterWithinAabbDoubleNative(
        double minX,
        double minY,
        double minZ,
        double maxX,
        double maxY,
        double maxZ,
        double[] positions,
        int[] output
    );

    private static native int filterIntersectingAabbDoubleNative(
        double queryMinX,
        double queryMinY,
        double queryMinZ,
        double queryMaxX,
        double queryMaxY,
        double queryMaxZ,
        double[] boxes,
        int[] output
    );

    private static native int sortByDistanceNative(
        int originX,
        int originY,
        int originZ,
        int[] positions,
        int[] output
    );

    private static native int sortByBlockDistanceNative(
        int originX,
        int originY,
        int originZ,
        int[] positions,
        int[] output
    );

    private static native int sortByDistanceDoubleNative(
        double originX,
        double originY,
        double originZ,
        double[] positions,
        int[] output
    );

    private static native int sortWithinRadiusExclusiveDoubleNative(
        double originX,
        double originY,
        double originZ,
        double radiusSquared,
        double[] positions,
        int[] output
    );
}
