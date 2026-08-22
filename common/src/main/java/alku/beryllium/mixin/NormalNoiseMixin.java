package alku.beryllium.mixin;

import alku.beryllium.api.MinecraftNormalNoiseAccess;
import alku.beryllium.bridge.NativeBridge;
import alku.beryllium.worldgen.NativeNoiseCleaner;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.minecraft.world.level.levelgen.synth.PerlinNoise;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.lang.ref.Cleaner;

@Mixin(NormalNoise.class)
public class NormalNoiseMixin implements MinecraftNormalNoiseAccess {
    @Unique
    private static final int BERYLLIUM_UNINITIALIZED = -1;
    @Unique
    private static final int BERYLLIUM_FAILED = -2;
    @Shadow
    @Final
    private PerlinNoise first;
    @Shadow
    @Final
    private PerlinNoise second;
    @Shadow
    @Final
    private double valueFactor;

    @Unique
    private volatile int beryllium$nativeGenerator = BERYLLIUM_UNINITIALIZED;
    @Unique
    private Cleaner.Cleanable beryllium$nativeCleanup;
    @Unique
    private volatile int beryllium$activeOctaves = BERYLLIUM_UNINITIALIZED;

    @Override
    public int beryllium$activeOctaves() {
        int current = this.beryllium$activeOctaves;
        if (current >= 0) {
            return current;
        }
        DoubleList amplitudes = ((PerlinNoiseAccessor) (Object) this.first).beryllium$amplitudes();
        int active = 0;
        for (int index = 0; index < amplitudes.size(); index++) {
            if (amplitudes.getDouble(index) != 0.0) {
                active++;
            }
        }
        this.beryllium$activeOctaves = active;
        return active;
    }

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

    @Unique
    private int beryllium$registerNativeGenerator() {
        if (!NativeBridge.isLoaded()) {
            return BERYLLIUM_FAILED;
        }

        try {
            PerlinNoiseAccessor firstAccess = (PerlinNoiseAccessor) (Object) this.first;
            PerlinNoiseAccessor secondAccess = (PerlinNoiseAccessor) (Object) this.second;
            DoubleList firstAmplitudes = firstAccess.beryllium$amplitudes();
            DoubleList secondAmplitudes = secondAccess.beryllium$amplitudes();
            ImprovedNoise[] firstLevels = firstAccess.beryllium$noiseLevels();
            ImprovedNoise[] secondLevels = secondAccess.beryllium$noiseLevels();
            int levelCount = firstLevels.length;
            if (levelCount == 0
                || secondLevels.length != levelCount
                || firstAmplitudes.size() != levelCount
                || !beryllium$equalAmplitudes(firstAmplitudes, secondAmplitudes)
                || Double.doubleToLongBits(firstAccess.beryllium$lowestFrequencyInputFactor())
                    != Double.doubleToLongBits(secondAccess.beryllium$lowestFrequencyInputFactor())
                || Double.doubleToLongBits(firstAccess.beryllium$lowestFrequencyValueFactor())
                    != Double.doubleToLongBits(secondAccess.beryllium$lowestFrequencyValueFactor())) {
                return BERYLLIUM_FAILED;
            }

            double[] amplitudes = firstAmplitudes.toDoubleArray();
            double[] origins = new double[Math.multiplyExact(levelCount, 6)];
            int[] permutations = new int[Math.multiplyExact(levelCount, 512)];
            for (int level = 0; level < levelCount; level++) {
                if (amplitudes[level] == 0.0) {
                    continue;
                }
                if (firstLevels[level] == null || secondLevels[level] == null) {
                    return BERYLLIUM_FAILED;
                }
                beryllium$writeLevel(firstLevels[level], level, origins, permutations);
                beryllium$writeLevel(secondLevels[level], levelCount + level, origins, permutations);
            }

            return NativeBridge.createMinecraftNormalNoise(
                amplitudes,
                origins,
                permutations,
                firstAccess.beryllium$lowestFrequencyInputFactor(),
                firstAccess.beryllium$lowestFrequencyValueFactor(),
                this.valueFactor
            );
        } catch (RuntimeException failure) {
            return BERYLLIUM_FAILED;
        }
    }

    @Unique
    private static void beryllium$writeLevel(
        ImprovedNoise level,
        int outputLevel,
        double[] origins,
        int[] permutations
    ) {
        int originOffset = outputLevel * 3;
        origins[originOffset] = level.xo;
        origins[originOffset + 1] = level.yo;
        origins[originOffset + 2] = level.zo;

        byte[] permutation = ((ImprovedNoiseAccessor) (Object) level).beryllium$permutation();
        int permutationOffset = outputLevel * 256;
        for (int index = 0; index < 256; index++) {
            permutations[permutationOffset + index] = permutation[index] & 0xff;
        }
    }

    @Unique
    private static boolean beryllium$equalAmplitudes(DoubleList first, DoubleList second) {
        if (first.size() != second.size()) {
            return false;
        }
        for (int index = 0; index < first.size(); index++) {
            if (Double.doubleToLongBits(first.getDouble(index)) != Double.doubleToLongBits(second.getDouble(index))) {
                return false;
            }
        }
        return true;
    }

}
