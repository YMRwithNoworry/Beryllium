package alku.beryllium.compute;

import alku.beryllium.bridge.NativeBridge;

public final class ChunkDensityInterpolationVerifier {
    private ChunkDensityInterpolationVerifier() {
    }

    public static void main(String[] args) {
        NativeBridge.initialize();
        verifyJavaKernelMatchesVanillaStagedInterpolation();
        verifyInvalidBatchesAreRejected();
        verifyNativeKernelMatchesJavaReference();
    }

    public static void verifyJavaKernelMatchesVanillaStagedInterpolation() {
        double[] corners = {
            -1.25, 3.5, 7.75, -9.0, 0.125, -4.25, 16.0, 2.0,
            1000.0, -0.0, -17.0, 31.0, 0.5, 8.0, -2.0, 64.0
        };
        int cellWidth = 4;
        int cellHeight = 8;
        double[] expected = vanillaStagedInterpolation(corners, 2, cellWidth, cellHeight);
        double[] actual = new double[expected.length];

        JavaComputeKernels.interpolateDensityCells(corners, 2, cellWidth, cellHeight, actual);

        assertRawArrayEquals(expected, actual, "Java chunk density interpolation");
    }

    public static void verifyNativeKernelMatchesJavaReference() {
        double[] corners = {
            -1.25, 3.5, 7.75, -9.0, 0.125, -4.25, 16.0, 2.0,
            1000.0, -0.0, -17.0, 31.0, 0.5, 8.0, -2.0, 64.0
        };
        double[] expected = new double[2 * 4 * 4 * 8];
        double[] actual = new double[expected.length];
        JavaComputeKernels.interpolateDensityCells(corners, 2, 4, 8, expected);

        boolean completed = NativeBridge.tryInterpolateDensityCells(corners, 2, 4, 8, actual);
        if (!completed) {
            throw new AssertionError("Native chunk density interpolation did not complete");
        }
        assertRawArrayEquals(expected, actual, "Native chunk density interpolation");
    }

    public static void verifyInvalidBatchesAreRejected() {
        assertRejected(() -> JavaComputeKernels.interpolateDensityCells(new double[7], 1, 4, 8, new double[128]));
        assertRejected(() -> JavaComputeKernels.interpolateDensityCells(new double[8], 1, 0, 8, new double[0]));
        assertRejected(() -> JavaComputeKernels.interpolateDensityCells(new double[8], 1, 4, 8, new double[127]));
    }

    private static double[] vanillaStagedInterpolation(
        double[] corners,
        int interpolatorCount,
        int cellWidth,
        int cellHeight
    ) {
        int cellVolume = cellWidth * cellWidth * cellHeight;
        double[] output = new double[interpolatorCount * cellVolume];
        for (int interpolator = 0; interpolator < interpolatorCount; interpolator++) {
            int cornerOffset = interpolator * 8;
            int outputOffset = interpolator * cellVolume;
            for (int y = 0; y < cellHeight; y++) {
                double deltaY = (double) y / (double) cellHeight;
                double valueXZ00 = lerp(deltaY, corners[cornerOffset], corners[cornerOffset + 2]);
                double valueXZ10 = lerp(deltaY, corners[cornerOffset + 1], corners[cornerOffset + 3]);
                double valueXZ01 = lerp(deltaY, corners[cornerOffset + 4], corners[cornerOffset + 6]);
                double valueXZ11 = lerp(deltaY, corners[cornerOffset + 5], corners[cornerOffset + 7]);
                for (int x = 0; x < cellWidth; x++) {
                    double deltaX = (double) x / (double) cellWidth;
                    double valueZ0 = lerp(deltaX, valueXZ00, valueXZ10);
                    double valueZ1 = lerp(deltaX, valueXZ01, valueXZ11);
                    for (int z = 0; z < cellWidth; z++) {
                        double deltaZ = (double) z / (double) cellWidth;
                        output[outputOffset + (y * cellWidth + x) * cellWidth + z] = lerp(deltaZ, valueZ0, valueZ1);
                    }
                }
            }
        }
        return output;
    }

    private static double lerp(double delta, double start, double end) {
        return start + delta * (end - start);
    }

    private static void assertRawArrayEquals(double[] expected, double[] actual, String label) {
        if (expected.length != actual.length) {
            throw new AssertionError(label + " length mismatch");
        }
        for (int index = 0; index < expected.length; index++) {
            if (Double.doubleToRawLongBits(expected[index]) != Double.doubleToRawLongBits(actual[index])) {
                throw new AssertionError(
                    label + " mismatch at " + index + ": expected " + expected[index] + " but got " + actual[index]
                );
            }
        }
    }

    private static void assertRejected(Runnable action) {
        try {
            action.run();
            throw new AssertionError("Expected invalid density interpolation batch to be rejected");
        } catch (IllegalArgumentException expected) {
            // Expected validation path.
        }
    }
}
