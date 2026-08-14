package alku.beryllium.mixin;

import alku.beryllium.api.MinecraftNormalNoiseAccess;
import alku.beryllium.bridge.NativeBridge;
import alku.beryllium.compute.NativeBatching;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.world.level.levelgen.DensityFunctions$Noise")
public class DensityFunctionsNoiseMixin {
    @Unique
    private static final ThreadLocal<double[]> BERYLLIUM_POSITIONS = ThreadLocal.withInitial(() -> new double[0]);

    @Shadow
    @Final
    private DensityFunction.NoiseHolder noise;
    @Shadow
    @Final
    private double xzScale;
    @Shadow
    @Final
    private double yScale;

    @Inject(method = "fillArray", at = @At("HEAD"), cancellable = true)
    private void beryllium$batchSampleNormalNoise(
        double[] output,
        DensityFunction.ContextProvider contextProvider,
        CallbackInfo callback
    ) {
        NormalNoise normalNoise = this.noise.noise();
        if (normalNoise == null) {
            return;
        }
        MinecraftNormalNoiseAccess nativeAccess = (MinecraftNormalNoiseAccess) (Object) normalNoise;
        int sampleCount = output.length;
        if (!NativeBatching.shouldUseNativeNormalNoiseBatch(sampleCount, nativeAccess.beryllium$activeOctaves())) {
            return;
        }
        int generator = nativeAccess.beryllium$getOrCreateNativeGenerator();
        if (generator < 0) {
            return;
        }

        int positionsLength = Math.multiplyExact(sampleCount, 3);
        double[] positions = BERYLLIUM_POSITIONS.get();
        if (positions.length < positionsLength) {
            positions = new double[Integer.highestOneBit(positionsLength - 1) << 1];
            BERYLLIUM_POSITIONS.set(positions);
        }
        for (int index = 0; index < sampleCount; index++) {
            DensityFunction.FunctionContext context = contextProvider.forIndex(index);
            int offset = index * 3;
            positions[offset] = context.blockX() * this.xzScale;
            positions[offset + 1] = context.blockY() * this.yScale;
            positions[offset + 2] = context.blockZ() * this.xzScale;
        }

        if (NativeBridge.batchSampleNoise3D(generator, positions, sampleCount, output)) {
            callback.cancel();
        }
    }
}
