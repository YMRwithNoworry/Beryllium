package alku.beryllium.worldgen;

import alku.beryllium.bridge.NativeBridge;

import java.util.Arrays;

public final class MinecraftNormalNoiseBridgeVerifier {
    private static final double WRAP = 33_554_432.0;
    private static final double NORMAL_INPUT_FACTOR = 1.0181268882175227;
    private static final int[][] GRADIENTS = {
        {1, 1, 0}, {-1, 1, 0}, {1, -1, 0}, {-1, -1, 0},
        {1, 0, 1}, {-1, 0, 1}, {1, 0, -1}, {-1, 0, -1},
        {0, 1, 1}, {0, -1, 1}, {0, 1, -1}, {0, -1, -1},
        {1, 1, 0}, {0, -1, 1}, {-1, 1, 0}, {0, -1, -1}
    };

    private MinecraftNormalNoiseBridgeVerifier() {
    }

    public static void verifyNativeMatchesMinecraftReference() {
        double[] amplitudes = {1.0, 0.5, 0.0, 1.25};
        double[] origins = createOrigins(amplitudes.length);
        int[] permutations = createPermutations(amplitudes.length);
        double inputFactor = 0.25;
        double valueFactor = 8.0 / 15.0;
        double normalValueFactor = 5.0 / 6.0;
        int generator = NativeBridge.createMinecraftNormalNoise(
            amplitudes,
            origins,
            permutations,
            inputFactor,
            valueFactor,
            normalValueFactor
        );
        if (generator < 0) {
            throw new AssertionError("Minecraft NormalNoise registration failed: " + generator);
        }

        try {
            int sampleCount = 2_048;
            double[] positions = new double[sampleCount * 3 + 9];
            for (int index = 0; index < sampleCount; index++) {
                int offset = index * 3;
                positions[offset] = index == 0 ? WRAP * 4.5 + 0.25 : index * 13.125 - 8_192.5;
                positions[offset + 1] = index == 1 ? -WRAP * 2.5 - 0.75 : index * -0.875 + 64.0;
                positions[offset + 2] = index * index * 0.03125 - 32_768.0;
            }

            double[] actual = new double[sampleCount + 2];
            Arrays.fill(actual, 91.25);
            if (!NativeBridge.batchSampleNoise3D(generator, positions, sampleCount, actual)) {
                throw new AssertionError("Minecraft NormalNoise native batch failed");
            }
            for (int index = 0; index < sampleCount; index++) {
                int offset = index * 3;
                double expected = sampleNormalNoise(
                    amplitudes,
                    origins,
                    permutations,
                    inputFactor,
                    valueFactor,
                    normalValueFactor,
                    positions[offset],
                    positions[offset + 1],
                    positions[offset + 2]
                );
                if (Double.doubleToRawLongBits(expected) != Double.doubleToRawLongBits(actual[index])) {
                    throw new AssertionError(
                        "Minecraft NormalNoise mismatch at " + index + ", expected " + expected + " but got " + actual[index]
                    );
                }
            }
            if (actual[sampleCount] != 91.25 || actual[sampleCount + 1] != 91.25) {
                throw new AssertionError("Minecraft NormalNoise native batch overwrote output tail");
            }
        } finally {
            NativeBridge.destroyNoiseGenerator(generator);
        }

        if (NativeBridge.batchSampleNoise3D(generator, new double[] {0.0, 0.0, 0.0}, new double[1])) {
            throw new AssertionError("Destroyed Minecraft NormalNoise generator remained callable");
        }
    }

    static double[] createOrigins(int levelCount) {
        double[] origins = new double[levelCount * 6];
        for (int level = 0; level < levelCount * 2; level++) {
            int offset = level * 3;
            origins[offset] = 0.25 + level * 17.0;
            origins[offset + 1] = 127.75 - level * 9.5;
            origins[offset + 2] = 63.125 + level * 3.25;
        }
        return origins;
    }

    static int[] createPermutations(int levelCount) {
        int[] permutations = new int[levelCount * 512];
        for (int level = 0; level < levelCount * 2; level++) {
            int offset = level * 256;
            for (int index = 0; index < 256; index++) {
                permutations[offset + index] = (index * 73 + level * 41) & 255;
            }
        }
        return permutations;
    }

    static double sampleNormalNoise(
        double[] amplitudes,
        double[] origins,
        int[] permutations,
        double inputFactor,
        double valueFactor,
        double normalValueFactor,
        double x,
        double y,
        double z
    ) {
        int levelCount = amplitudes.length;
        double first = samplePerlin(amplitudes, origins, permutations, 0, inputFactor, valueFactor, x, y, z);
        double second = samplePerlin(
            amplitudes,
            origins,
            permutations,
            levelCount,
            inputFactor,
            valueFactor,
            x * NORMAL_INPUT_FACTOR,
            y * NORMAL_INPUT_FACTOR,
            z * NORMAL_INPUT_FACTOR
        );
        return (first + second) * normalValueFactor;
    }

    private static double samplePerlin(
        double[] amplitudes,
        double[] origins,
        int[] permutations,
        int levelOffset,
        double inputFactor,
        double valueFactor,
        double x,
        double y,
        double z
    ) {
        double result = 0.0;
        for (int level = 0; level < amplitudes.length; level++) {
            if (amplitudes[level] != 0.0) {
                double sample = sampleImproved(
                    origins,
                    permutations,
                    levelOffset + level,
                    wrap(x * inputFactor),
                    wrap(y * inputFactor),
                    wrap(z * inputFactor)
                );
                result += amplitudes[level] * sample * valueFactor;
            }
            inputFactor *= 2.0;
            valueFactor /= 2.0;
        }
        return result;
    }

    private static double wrap(double value) {
        return value - floorLong(value / WRAP + 0.5) * WRAP;
    }

    private static long floorLong(double value) {
        long truncated = (long) value;
        return value < truncated ? truncated - 1 : truncated;
    }

    private static double sampleImproved(
        double[] origins,
        int[] permutations,
        int level,
        double x,
        double y,
        double z
    ) {
        int originOffset = level * 3;
        double shiftedX = x + origins[originOffset];
        double shiftedY = y + origins[originOffset + 1];
        double shiftedZ = z + origins[originOffset + 2];
        int floorX = floor(shiftedX);
        int floorY = floor(shiftedY);
        int floorZ = floor(shiftedZ);
        double localX = shiftedX - floorX;
        double localY = shiftedY - floorY;
        double localZ = shiftedZ - floorZ;
        int px0 = permutation(permutations, level, floorX);
        int px1 = permutation(permutations, level, floorX + 1);
        int pxy00 = permutation(permutations, level, px0 + floorY);
        int pxy01 = permutation(permutations, level, px0 + floorY + 1);
        int pxy10 = permutation(permutations, level, px1 + floorY);
        int pxy11 = permutation(permutations, level, px1 + floorY + 1);
        double value000 = gradDot(permutation(permutations, level, pxy00 + floorZ), localX, localY, localZ);
        double value100 = gradDot(permutation(permutations, level, pxy10 + floorZ), localX - 1.0, localY, localZ);
        double value010 = gradDot(permutation(permutations, level, pxy01 + floorZ), localX, localY - 1.0, localZ);
        double value110 = gradDot(permutation(permutations, level, pxy11 + floorZ), localX - 1.0, localY - 1.0, localZ);
        double value001 = gradDot(permutation(permutations, level, pxy00 + floorZ + 1), localX, localY, localZ - 1.0);
        double value101 = gradDot(permutation(permutations, level, pxy10 + floorZ + 1), localX - 1.0, localY, localZ - 1.0);
        double value011 = gradDot(permutation(permutations, level, pxy01 + floorZ + 1), localX, localY - 1.0, localZ - 1.0);
        double value111 = gradDot(
            permutation(permutations, level, pxy11 + floorZ + 1),
            localX - 1.0,
            localY - 1.0,
            localZ - 1.0
        );
        return lerp3(
            smoothstep(localX),
            smoothstep(localY),
            smoothstep(localZ),
            value000,
            value100,
            value010,
            value110,
            value001,
            value101,
            value011,
            value111
        );
    }

    private static int floor(double value) {
        int truncated = (int) value;
        return value < truncated ? truncated - 1 : truncated;
    }

    private static int permutation(int[] permutations, int level, int index) {
        return permutations[level * 256 + (index & 255)];
    }

    private static double gradDot(int hash, double x, double y, double z) {
        int[] gradient = GRADIENTS[hash & 15];
        return gradient[0] * x + gradient[1] * y + gradient[2] * z;
    }

    private static double smoothstep(double value) {
        return value * value * value * (value * (value * 6.0 - 15.0) + 10.0);
    }

    private static double lerp(double delta, double start, double end) {
        return start + delta * (end - start);
    }

    private static double lerp2(double x, double y, double v00, double v10, double v01, double v11) {
        return lerp(y, lerp(x, v00, v10), lerp(x, v01, v11));
    }

    private static double lerp3(
        double x,
        double y,
        double z,
        double value000,
        double value100,
        double value010,
        double value110,
        double value001,
        double value101,
        double value011,
        double value111
    ) {
        return lerp(
            z,
            lerp2(x, y, value000, value100, value010, value110),
            lerp2(x, y, value001, value101, value011, value111)
        );
    }
}
