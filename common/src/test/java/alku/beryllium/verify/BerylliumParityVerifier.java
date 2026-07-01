package alku.beryllium.verify;

import alku.beryllium.bridge.NativeBridge;
import alku.beryllium.bridge.NativeStatus;
import alku.beryllium.compute.JavaComputeKernels;

import java.util.Arrays;

public final class BerylliumParityVerifier {
    private BerylliumParityVerifier() {
    }

    public static void main(String[] args) {
        verifyJavaKernel();
        verifyJavaKernelDouble();
        verifyNativeBridgeFallback();
        verifyNativeBridgeFallbackDouble();
        verifyInvalidInput();
    }

    private static void verifyJavaKernel() {
        int[] positions = {0, 64, 0, 3, 68, 4, -1, 63, -2};
        long[] expected = {0, 41, 6};
        long[] actual = JavaComputeKernels.squaredDistances(0, 64, 0, positions);
        assertArrayEquals(expected, actual, "Java reference kernel");
    }

    private static void verifyNativeBridgeFallback() {
        NativeStatus status = NativeBridge.initialize();
        if (status != NativeStatus.OK && status != NativeStatus.UNAVAILABLE) {
            throw new AssertionError("Unexpected native initialization status: " + status);
        }

        int[] positions = {0, 64, 0, 3, 68, 4, -1, 63, -2};
        long[] expected = {0, 41, 6};
        long[] actual = NativeBridge.computeSquaredDistances(0, 64, 0, positions);
        assertArrayEquals(expected, actual, "Native bridge fallback/parity");
    }

    private static void verifyJavaKernelDouble() {
        double[] positions = {0.0, 64.0, 0.0, 3.0, 68.0, 4.0, -1.0, 63.0, -2.0};
        double[] expected = {0.0, 41.0, 6.0};
        double[] actual = JavaComputeKernels.squaredDistances(0.0, 64.0, 0.0, positions);
        assertArrayEquals(expected, actual, "Java double reference kernel");
    }

    private static void verifyNativeBridgeFallbackDouble() {
        double[] positions = {0.0, 64.0, 0.0, 3.0, 68.0, 4.0, -1.0, 63.0, -2.0};
        double[] expected = {0.0, 41.0, 6.0};
        double[] actual = NativeBridge.computeSquaredDistances(0.0, 64.0, 0.0, positions);
        assertArrayEquals(expected, actual, "Native bridge double fallback/parity");
    }

    private static void verifyInvalidInput() {
        try {
            JavaComputeKernels.squaredDistances(0, 0, 0, new int[] {1, 2});
        } catch (IllegalArgumentException expected) {
            return;
        }

        throw new AssertionError("Expected invalid packed positions to be rejected");
    }

    private static void assertArrayEquals(long[] expected, long[] actual, String label) {
        if (!Arrays.equals(expected, actual)) {
            throw new AssertionError(label + " mismatch, expected " + Arrays.toString(expected) + " but got " + Arrays.toString(actual));
        }
    }

    private static void assertArrayEquals(double[] expected, double[] actual, String label) {
        if (!Arrays.equals(expected, actual)) {
            throw new AssertionError(label + " mismatch, expected " + Arrays.toString(expected) + " but got " + Arrays.toString(actual));
        }
    }
}
