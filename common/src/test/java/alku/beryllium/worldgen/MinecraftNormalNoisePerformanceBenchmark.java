package alku.beryllium.worldgen;

import alku.beryllium.bridge.NativeBridge;
import alku.beryllium.bridge.NativeStatus;

import java.util.Arrays;
import java.util.Locale;

public final class MinecraftNormalNoisePerformanceBenchmark {
    private static final int WARMUP_ITERATIONS = 200;
    private static final int MEASUREMENT_ITERATIONS = 401;
    private static volatile long blackHole;

    private MinecraftNormalNoisePerformanceBenchmark() {
    }

    public static void main(String[] args) {
        NativeStatus status = NativeBridge.initialize();
        if (status != NativeStatus.OK) {
            throw new AssertionError("Expected bundled native backend to load, got " + status);
        }
        MinecraftNormalNoiseBridgeVerifier.verifyNativeMatchesMinecraftReference();

        double inputFactor = 0.125;
        double normalValueFactor = 5.0 / 6.0;
        System.out.println("benchmark=minecraft-normal-noise-fill-array");
        double[][] profiles = {
            {1.0},
            {1.0, 0.0, 0.0, 0.0, 0.0, 1.0},
            {1.0, 1.0, 0.0, 0.5, 1.0, 1.0}
        };
        for (double[] amplitudes : profiles) {
            int activeOctaves = (int) Arrays.stream(amplitudes).filter(value -> value != 0.0).count();
            double[] origins = MinecraftNormalNoiseBridgeVerifier.createOrigins(amplitudes.length);
            int[] permutations = MinecraftNormalNoiseBridgeVerifier.createPermutations(amplitudes.length);
            double valueFactor = Math.scalb(1.0, amplitudes.length - 1) / (Math.scalb(1.0, amplitudes.length) - 1.0);
            int generator = NativeBridge.createMinecraftNormalNoise(
                amplitudes,
                origins,
                permutations,
                inputFactor,
                valueFactor,
                normalValueFactor
            );
            if (generator < 0) {
                throw new AssertionError("Minecraft NormalNoise benchmark registration failed");
            }

            try {
                for (int sampleCount : new int[] {32, 49, 64, 128, 256, 512}) {
                    runCase(
                        generator,
                        activeOctaves,
                        amplitudes,
                        origins,
                        permutations,
                        inputFactor,
                        valueFactor,
                        normalValueFactor,
                        sampleCount
                    );
                }
            } finally {
                NativeBridge.destroyNoiseGenerator(generator);
            }
        }

        if (blackHole == Long.MIN_VALUE) {
            throw new AssertionError("unreachable benchmark sink");
        }
    }

    private static void runCase(
        int generator,
        int activeOctaves,
        double[] amplitudes,
        double[] origins,
        int[] permutations,
        double inputFactor,
        double valueFactor,
        double normalValueFactor,
        int sampleCount
    ) {
        double[] x = new double[sampleCount];
        double[] y = new double[sampleCount];
        double[] z = new double[sampleCount];
        for (int index = 0; index < sampleCount; index++) {
            x[index] = index * 4.0 - 128.0;
            y[index] = 320.0 - index * 8.0;
            z[index] = index * 3.0 + 17.0;
        }
        double[] positions = new double[sampleCount * 3];
        double[] output = new double[sampleCount];

        Runnable javaPath = () -> {
            long sink = 0L;
            for (int index = 0; index < sampleCount; index++) {
                output[index] = MinecraftNormalNoiseBridgeVerifier.sampleNormalNoise(
                    amplitudes,
                    origins,
                    permutations,
                    inputFactor,
                    valueFactor,
                    normalValueFactor,
                    x[index],
                    y[index],
                    z[index]
                );
                sink ^= Double.doubleToRawLongBits(output[index]);
            }
            blackHole ^= sink;
        };
        Runnable nativePath = () -> {
            for (int index = 0; index < sampleCount; index++) {
                int offset = index * 3;
                positions[offset] = x[index];
                positions[offset + 1] = y[index];
                positions[offset + 2] = z[index];
            }
            if (!NativeBridge.batchSampleNoise3D(generator, positions, sampleCount, output)) {
                throw new AssertionError("Minecraft NormalNoise benchmark native call failed");
            }
            blackHole ^= Double.doubleToRawLongBits(output[0]);
            blackHole ^= Double.doubleToRawLongBits(output[sampleCount - 1]);
        };

        for (int iteration = 0; iteration < WARMUP_ITERATIONS; iteration++) {
            javaPath.run();
            nativePath.run();
        }
        long javaMedian = median(javaPath);
        long nativeMedian = median(nativePath);
        System.out.printf(
            Locale.ROOT,
            "active_octaves=%d samples=%d work=%d java_median_ns=%d native_median_ns=%d speedup=%.3fx%n",
            activeOctaves,
            sampleCount,
            activeOctaves * sampleCount,
            javaMedian,
            nativeMedian,
            (double) javaMedian / nativeMedian
        );
    }

    private static long median(Runnable operation) {
        long[] durations = new long[MEASUREMENT_ITERATIONS];
        for (int iteration = 0; iteration < durations.length; iteration++) {
            long start = System.nanoTime();
            operation.run();
            durations[iteration] = System.nanoTime() - start;
        }
        Arrays.sort(durations);
        return durations[durations.length / 2];
    }
}
