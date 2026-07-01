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

        long[] output = new long[positions.length / 3];
        if (!isLoaded()) {
            return JavaComputeKernels.squaredDistances(originX, originY, originZ, positions);
        }

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

        double[] output = new double[positions.length / 3];
        if (!isLoaded()) {
            return JavaComputeKernels.squaredDistances(originX, originY, originZ, positions);
        }

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
}
