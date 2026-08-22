package alku.beryllium.worldgen;

import alku.beryllium.bridge.NativeBridge;
import alku.beryllium.bridge.NativeStatus;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.synth.BlendedNoise;

import java.util.Arrays;
import java.util.Locale;

public final class MinecraftBlendedNoisePerformanceBenchmark {
    private static final int WARMUP_ITERATIONS = 120;
    private static final int MEASURE_ITERATIONS = 401;
    private static volatile double blackhole;

    private MinecraftBlendedNoisePerformanceBenchmark() {
    }

    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        if (NativeBridge.initialize() != NativeStatus.OK) {
            throw new AssertionError("Minecraft BlendedNoise benchmark requires the native backend");
        }
        MinecraftBlendedNoiseBridgeVerifier.verifyNativeMatchesMinecraftReference();

        for (int sampleCount : new int[] {17, 33, 49, 128, 512, 1_024, 2_048}) {
            benchmark(sampleCount);
        }
    }

    private static void benchmark(int sampleCount) {
        BlendedNoise noise = MinecraftBlendedNoiseBridgeVerifier.createNoise(
            0x1234_5678_9abc_def0L,
            0.25,
            0.125,
            80.0,
            160.0,
            8.0
        );
        int generator = MinecraftBlendedNoiseBridgeVerifier.createNativeGenerator(noise);
        if (generator < 0) {
            throw new AssertionError("Minecraft BlendedNoise benchmark registration failed");
        }

        int[] positions = MinecraftBlendedNoiseBridgeVerifier.createPositions(sampleCount);
        double[] packedPositions = new double[positions.length];
        double[] javaOutput = new double[sampleCount];
        double[] nativeOutput = new double[sampleCount];
        MutableContext context = new MutableContext();
        int repetitions = Math.max(1, 2_048 / sampleCount);
        Runnable javaWork = () -> {
            for (int repeat = 0; repeat < repetitions; repeat++) {
                for (int index = 0; index < sampleCount; index++) {
                    int offset = index * 3;
                    context.set(positions[offset], positions[offset + 1], positions[offset + 2]);
                    javaOutput[index] = noise.compute(context);
                }
                blackhole = javaOutput[(repeat * 31) % sampleCount];
            }
        };
        Runnable nativeWork = () -> {
            for (int repeat = 0; repeat < repetitions; repeat++) {
                for (int index = 0; index < positions.length; index++) {
                    packedPositions[index] = positions[index];
                }
                if (!NativeBridge.batchSampleNoise3D(generator, packedPositions, sampleCount, nativeOutput)) {
                    throw new AssertionError("Minecraft BlendedNoise benchmark native call failed");
                }
                blackhole = nativeOutput[(repeat * 31) % sampleCount];
            }
        };

        try {
            for (int iteration = 0; iteration < WARMUP_ITERATIONS; iteration++) {
                if ((iteration & 1) == 0) {
                    javaWork.run();
                    nativeWork.run();
                } else {
                    nativeWork.run();
                    javaWork.run();
                }
            }

            long javaMedian = medianBatchNanos(javaWork, repetitions);
            long nativeMedian = medianBatchNanos(nativeWork, repetitions);
            double speedup = (double) javaMedian / nativeMedian;
            System.out.printf(
                Locale.ROOT,
                "blended_noise samples=%d java_median_ns=%d native_median_ns=%d speedup=%.3fx%n",
                sampleCount,
                javaMedian,
                nativeMedian,
                speedup
            );
        } finally {
            NativeBridge.destroyNoiseGenerator(generator);
        }
    }

    private static long medianBatchNanos(Runnable work, int repetitions) {
        long[] samples = new long[MEASURE_ITERATIONS];
        for (int iteration = 0; iteration < samples.length; iteration++) {
            long start = System.nanoTime();
            work.run();
            samples[iteration] = (System.nanoTime() - start) / repetitions;
        }
        Arrays.sort(samples);
        return samples[samples.length / 2];
    }

    private static final class MutableContext implements DensityFunction.FunctionContext {
        private int x;
        private int y;
        private int z;

        private void set(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public int blockX() {
            return this.x;
        }

        @Override
        public int blockY() {
            return this.y;
        }

        @Override
        public int blockZ() {
            return this.z;
        }
    }
}
