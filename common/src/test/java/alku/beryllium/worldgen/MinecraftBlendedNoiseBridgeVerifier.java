package alku.beryllium.worldgen;

import alku.beryllium.bridge.NativeBridge;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.synth.BlendedNoise;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;
import net.minecraft.world.level.levelgen.synth.PerlinNoise;

import java.lang.reflect.Field;
import java.util.Arrays;

public final class MinecraftBlendedNoiseBridgeVerifier {
    private static final int LIMIT_OCTAVES = 16;
    private static final int MAIN_OCTAVES = 8;
    private static final int TOTAL_OCTAVES = LIMIT_OCTAVES * 2 + MAIN_OCTAVES;
    private static final Field MIN_LIMIT_NOISE = field(BlendedNoise.class, "minLimitNoise");
    private static final Field MAX_LIMIT_NOISE = field(BlendedNoise.class, "maxLimitNoise");
    private static final Field MAIN_NOISE = field(BlendedNoise.class, "mainNoise");
    private static final Field XZ_MULTIPLIER = field(BlendedNoise.class, "xzMultiplier");
    private static final Field Y_MULTIPLIER = field(BlendedNoise.class, "yMultiplier");
    private static final Field XZ_FACTOR = field(BlendedNoise.class, "xzFactor");
    private static final Field Y_FACTOR = field(BlendedNoise.class, "yFactor");
    private static final Field SMEAR_SCALE_MULTIPLIER = field(BlendedNoise.class, "smearScaleMultiplier");
    private static final Field PERMUTATION = field(ImprovedNoise.class, "p");

    private MinecraftBlendedNoiseBridgeVerifier() {
    }

    public static void verifyNativeMatchesMinecraftReference() {
        verifyConfiguration(0x1234_5678_9abc_def0L, 0.25, 0.125, 80.0, 160.0, 8.0);
        verifyConfiguration(-0x1020_3040_5060_7080L, 0.25, 0.375, 80.0, 60.0, 8.0);
        verifyConfiguration(0x55aa_33cc_77ee_11ddL, 0.25, 0.25, 80.0, 160.0, 4.0);
    }

    static BlendedNoise createNoise(long seed, double xzScale, double yScale, double xzFactor, double yFactor, double smear) {
        return new BlendedNoise(new XoroshiroRandomSource(seed), xzScale, yScale, xzFactor, yFactor, smear);
    }

    static int createNativeGenerator(BlendedNoise noise) {
        try {
            double[] origins = new double[TOTAL_OCTAVES * 3];
            int[] permutations = new int[TOTAL_OCTAVES * 256];
            writeLevels((PerlinNoise) MIN_LIMIT_NOISE.get(noise), LIMIT_OCTAVES, 0, origins, permutations);
            writeLevels((PerlinNoise) MAX_LIMIT_NOISE.get(noise), LIMIT_OCTAVES, LIMIT_OCTAVES, origins, permutations);
            writeLevels((PerlinNoise) MAIN_NOISE.get(noise), MAIN_OCTAVES, LIMIT_OCTAVES * 2, origins, permutations);
            return NativeBridge.createMinecraftBlendedNoise(
                origins,
                permutations,
                XZ_MULTIPLIER.getDouble(noise),
                Y_MULTIPLIER.getDouble(noise),
                XZ_FACTOR.getDouble(noise),
                Y_FACTOR.getDouble(noise),
                SMEAR_SCALE_MULTIPLIER.getDouble(noise)
            );
        } catch (IllegalAccessException failure) {
            throw new AssertionError("Unable to read Minecraft BlendedNoise state", failure);
        }
    }

    static int[] createPositions(int sampleCount) {
        int[] positions = new int[Math.multiplyExact(sampleCount, 3)];
        long state = 0x6a09_e667_f3bc_c909L;
        for (int index = 0; index < sampleCount; index++) {
            state = mix(state + index);
            int offset = index * 3;
            positions[offset] = index < 8 ? edgeCoordinate(index) : (int) (state % 60_000_001L) - 30_000_000;
            positions[offset + 1] = (int) ((state >>> 24) % 4_097L) - 2_048;
            state = mix(state);
            positions[offset + 2] = index < 8 ? edgeCoordinate(7 - index) : (int) (state % 60_000_001L) - 30_000_000;
        }
        return positions;
    }

    static double[] packPositions(int[] positions) {
        double[] packed = new double[positions.length];
        for (int index = 0; index < positions.length; index++) {
            packed[index] = positions[index];
        }
        return packed;
    }

    private static void verifyConfiguration(
        long seed,
        double xzScale,
        double yScale,
        double xzFactor,
        double yFactor,
        double smear
    ) {
        BlendedNoise noise = createNoise(seed, xzScale, yScale, xzFactor, yFactor, smear);
        int generator = createNativeGenerator(noise);
        if (generator < 0) {
            throw new AssertionError("Minecraft BlendedNoise registration failed: " + generator);
        }

        int sampleCount = 2_048;
        int[] positions = createPositions(sampleCount);
        double[] packedPositions = packPositions(positions);
        double[] actual = new double[sampleCount + 3];
        Arrays.fill(actual, Double.longBitsToDouble(0x7ff8_0000_0000_1234L));
        try {
            if (!NativeBridge.batchSampleNoise3D(generator, packedPositions, sampleCount, actual)) {
                throw new AssertionError("Minecraft BlendedNoise native batch failed");
            }
            for (int index = 0; index < sampleCount; index++) {
                int offset = index * 3;
                double expected = noise.compute(new DensityFunction.SinglePointContext(
                    positions[offset],
                    positions[offset + 1],
                    positions[offset + 2]
                ));
                if (Double.doubleToLongBits(expected) != Double.doubleToLongBits(actual[index])) {
                    throw new AssertionError(
                        "Minecraft BlendedNoise mismatch at " + index + ", expected " + expected + " but got " + actual[index]
                    );
                }
            }
            for (int index = sampleCount; index < actual.length; index++) {
                if (Double.doubleToRawLongBits(actual[index]) != 0x7ff8_0000_0000_1234L) {
                    throw new AssertionError("Minecraft BlendedNoise native batch overwrote output tail");
                }
            }
        } finally {
            NativeBridge.destroyNoiseGenerator(generator);
        }
        if (NativeBridge.batchSampleNoise3D(generator, new double[] {0.0, 0.0, 0.0}, new double[1])) {
            throw new AssertionError("Destroyed Minecraft BlendedNoise generator remained callable");
        }
    }

    private static void writeLevels(
        PerlinNoise noise,
        int levelCount,
        int outputOffset,
        double[] origins,
        int[] permutations
    ) throws IllegalAccessException {
        for (int index = 0; index < levelCount; index++) {
            ImprovedNoise level = noise.getOctaveNoise(index);
            if (level == null) {
                throw new AssertionError("Minecraft BlendedNoise contains an unexpected empty octave");
            }
            int outputLevel = outputOffset + index;
            int originOffset = outputLevel * 3;
            origins[originOffset] = level.xo;
            origins[originOffset + 1] = level.yo;
            origins[originOffset + 2] = level.zo;

            byte[] permutation = (byte[]) PERMUTATION.get(level);
            int permutationOffset = outputLevel * 256;
            for (int permutationIndex = 0; permutationIndex < 256; permutationIndex++) {
                permutations[permutationOffset + permutationIndex] = permutation[permutationIndex] & 0xff;
            }
        }
    }

    private static Field field(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    private static int edgeCoordinate(int index) {
        return switch (index) {
            case 0 -> -30_000_000;
            case 1 -> -16_777_216;
            case 2 -> -1;
            case 3 -> 0;
            case 4 -> 1;
            case 5 -> 16_777_216;
            case 6 -> 29_999_999;
            default -> 30_000_000;
        };
    }

    private static long mix(long value) {
        value ^= value >>> 30;
        value *= 0xbf58_476d_1ce4_e5b9L;
        value ^= value >>> 27;
        value *= 0x94d0_49bb_1331_11ebL;
        return value ^ value >>> 31;
    }
}
