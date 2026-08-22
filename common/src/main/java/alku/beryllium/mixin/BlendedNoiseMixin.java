package alku.beryllium.mixin;

import alku.beryllium.api.MinecraftBlendedNoiseAccess;
import alku.beryllium.bridge.NativeBridge;
import alku.beryllium.compute.NativeBatching;
import alku.beryllium.worldgen.NativeNoiseCleaner;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.synth.BlendedNoise;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;
import net.minecraft.world.level.levelgen.synth.PerlinNoise;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.lang.ref.Cleaner;

@Mixin(BlendedNoise.class)
public abstract class BlendedNoiseMixin implements MinecraftBlendedNoiseAccess, DensityFunction.SimpleFunction {
    @Unique
    private static final int BERYLLIUM_UNINITIALIZED = -1;
    @Unique
    private static final int BERYLLIUM_FAILED = -2;
    @Unique
    private static final int BERYLLIUM_LIMIT_OCTAVES = 16;
    @Unique
    private static final int BERYLLIUM_MAIN_OCTAVES = 8;
    @Unique
    private static final int BERYLLIUM_TOTAL_OCTAVES = BERYLLIUM_LIMIT_OCTAVES * 2 + BERYLLIUM_MAIN_OCTAVES;
    @Unique
    private static final ThreadLocal<double[]> BERYLLIUM_POSITIONS = ThreadLocal.withInitial(() -> new double[0]);

    @Shadow
    @Final
    private PerlinNoise minLimitNoise;
    @Shadow
    @Final
    private PerlinNoise maxLimitNoise;
    @Shadow
    @Final
    private PerlinNoise mainNoise;
    @Shadow
    @Final
    private double xzMultiplier;
    @Shadow
    @Final
    private double yMultiplier;
    @Shadow
    @Final
    private double xzFactor;
    @Shadow
    @Final
    private double yFactor;
    @Shadow
    @Final
    private double smearScaleMultiplier;

    @Unique
    private volatile int beryllium$nativeGenerator = BERYLLIUM_UNINITIALIZED;
    @Unique
    private Cleaner.Cleanable beryllium$nativeCleanup;

    @Override
    public int beryllium$getOrCreateNativeGenerator() {
        int current = this.beryllium$nativeGenerator;
        if (current != BERYLLIUM_UNINITIALIZED) {
            return current;
        }

        synchronized (this) {
            current = this.beryllium$nativeGenerator;
            if (current != BERYLLIUM_UNINITIALIZED) {
                return current;
            }

            int generator = this.beryllium$registerNativeGenerator();
            this.beryllium$nativeGenerator = generator >= 0 ? generator : BERYLLIUM_FAILED;
            if (generator >= 0) {
                this.beryllium$nativeCleanup = NativeNoiseCleaner.register(this, generator);
            }
            return this.beryllium$nativeGenerator;
        }
    }

    @Override
    public void fillArray(double[] output, DensityFunction.ContextProvider contextProvider) {
        int sampleCount = output.length;
        if (!NativeBatching.shouldUseNativeBlendedNoiseBatch(sampleCount)) {
            contextProvider.fillAllDirectly(output, (BlendedNoise) (Object) this);
            return;
        }
        int generator = this.beryllium$getOrCreateNativeGenerator();
        if (generator < 0) {
            contextProvider.fillAllDirectly(output, (BlendedNoise) (Object) this);
            return;
        }

        int positionsLength = Math.multiplyExact(sampleCount, 3);
        double[] positions = BERYLLIUM_POSITIONS.get();
        if (positions.length < positionsLength) {
            positions = new double[positionsLength];
            BERYLLIUM_POSITIONS.set(positions);
        }
        for (int index = 0; index < sampleCount; index++) {
            DensityFunction.FunctionContext context = contextProvider.forIndex(index);
            int offset = index * 3;
            positions[offset] = context.blockX();
            positions[offset + 1] = context.blockY();
            positions[offset + 2] = context.blockZ();
        }

        if (NativeBridge.batchSampleNoise3D(generator, positions, sampleCount, output)) {
            return;
        }
        BlendedNoise blendedNoise = (BlendedNoise) (Object) this;
        for (int index = 0; index < sampleCount; index++) {
            int offset = index * 3;
            output[index] = blendedNoise.compute(new DensityFunction.SinglePointContext(
                (int) positions[offset],
                (int) positions[offset + 1],
                (int) positions[offset + 2]
            ));
        }
    }

    @Unique
    private int beryllium$registerNativeGenerator() {
        if (!NativeBridge.isLoaded()) {
            return BERYLLIUM_FAILED;
        }

        try {
            double[] origins = new double[BERYLLIUM_TOTAL_OCTAVES * 3];
            int[] permutations = new int[BERYLLIUM_TOTAL_OCTAVES * 256];
            if (!beryllium$writeLevels(this.minLimitNoise, BERYLLIUM_LIMIT_OCTAVES, 0, origins, permutations)
                || !beryllium$writeLevels(
                    this.maxLimitNoise,
                    BERYLLIUM_LIMIT_OCTAVES,
                    BERYLLIUM_LIMIT_OCTAVES,
                    origins,
                    permutations
                )
                || !beryllium$writeLevels(
                    this.mainNoise,
                    BERYLLIUM_MAIN_OCTAVES,
                    BERYLLIUM_LIMIT_OCTAVES * 2,
                    origins,
                    permutations
                )) {
                return BERYLLIUM_FAILED;
            }
            return NativeBridge.createMinecraftBlendedNoise(
                origins,
                permutations,
                this.xzMultiplier,
                this.yMultiplier,
                this.xzFactor,
                this.yFactor,
                this.smearScaleMultiplier
            );
        } catch (RuntimeException failure) {
            return BERYLLIUM_FAILED;
        }
    }

    @Unique
    private static boolean beryllium$writeLevels(
        PerlinNoise noise,
        int levelCount,
        int outputOffset,
        double[] origins,
        int[] permutations
    ) {
        for (int index = 0; index < levelCount; index++) {
            ImprovedNoise level = noise.getOctaveNoise(index);
            if (level == null) {
                return false;
            }
            int outputLevel = outputOffset + index;
            int originOffset = outputLevel * 3;
            origins[originOffset] = level.xo;
            origins[originOffset + 1] = level.yo;
            origins[originOffset + 2] = level.zo;

            byte[] permutation = ((ImprovedNoiseAccessor) (Object) level).beryllium$permutation();
            int permutationOffset = outputLevel * 256;
            for (int permutationIndex = 0; permutationIndex < 256; permutationIndex++) {
                permutations[permutationOffset + permutationIndex] = permutation[permutationIndex] & 0xff;
            }
        }
        return true;
    }
}
