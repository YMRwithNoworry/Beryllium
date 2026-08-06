package alku.beryllium.mixin;

import alku.beryllium.api.NoiseChunkInterpolationAccess;
import alku.beryllium.api.NoiseInterpolatorAccess;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NoiseChunk.NoiseInterpolator.class)
public abstract class NoiseInterpolatorMixin implements NoiseInterpolatorAccess {
    @Shadow(remap = false)
    @Final
    private NoiseChunk this$0;

    @Shadow
    private double[][] slice0;

    @Shadow
    private double[][] slice1;

    @Unique
    private double[] beryllium$nativeValues;

    @Unique
    private int beryllium$nativeOffset;

    @Override
    public void beryllium$writeSlabCorners(
        double[] output,
        int interpolatorIndex,
        int interpolatorCount,
        int cellCountY,
        int cellCountXZ
    ) {
        for (int y = 0; y < cellCountY; y++) {
            for (int z = 0; z < cellCountXZ; z++) {
                int cellIndex = y * cellCountXZ + z;
                int offset = (cellIndex * interpolatorCount + interpolatorIndex) * 8;
                output[offset] = this.slice0[z][y];
                output[offset + 1] = this.slice1[z][y];
                output[offset + 2] = this.slice0[z][y + 1];
                output[offset + 3] = this.slice1[z][y + 1];
                output[offset + 4] = this.slice0[z + 1][y];
                output[offset + 5] = this.slice1[z + 1][y];
                output[offset + 6] = this.slice0[z + 1][y + 1];
                output[offset + 7] = this.slice1[z + 1][y + 1];
            }
        }
    }

    @Override
    public void beryllium$useNativeCell(double[] values, int offset) {
        this.beryllium$nativeValues = values;
        this.beryllium$nativeOffset = offset;
    }

    @Override
    public void beryllium$clearNativeCell() {
        this.beryllium$nativeValues = null;
    }

    @Inject(method = "compute", at = @At("HEAD"), cancellable = true)
    private void beryllium$readNativeCell(
        DensityFunction.FunctionContext context,
        CallbackInfoReturnable<Double> callback
    ) {
        double[] values = this.beryllium$nativeValues;
        if (values == null || context != this.this$0) {
            return;
        }

        int index = this.beryllium$nativeOffset
            + ((NoiseChunkInterpolationAccess) (Object) this.this$0).beryllium$nativeCellIndex();
        callback.setReturnValue(values[index]);
    }
}
