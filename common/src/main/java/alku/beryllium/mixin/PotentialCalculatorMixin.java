package alku.beryllium.mixin;

import alku.beryllium.compute.PotentialEnergyBatch;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.PotentialCalculator;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(PotentialCalculator.class)
public class PotentialCalculatorMixin {
    @Shadow
    @Final
    private List<?> charges;

    @Unique
    private int beryllium$chargeVersion;

    /**
     * @reason Batch point-charge potential math through the native backend while preserving vanilla summation order.
     * @author YMRwithNoworry
     */
    @Overwrite
    public double getPotentialEnergyChange(BlockPos position, double chargeMultiplier) {
        return PotentialEnergyBatch.getPotentialEnergyChangeCached(
            this,
            this.beryllium$chargeVersion,
            this.charges,
            position,
            chargeMultiplier,
            charge -> ((PotentialCalculatorPointChargeAccessor) charge).beryllium$pos(),
            charge -> ((PotentialCalculatorPointChargeAccessor) charge).beryllium$charge()
        );
    }

    @Inject(method = "addCharge", at = @At("TAIL"))
    private void beryllium$invalidateNativeChargeCache(BlockPos position, double charge, CallbackInfo callbackInfo) {
        this.beryllium$chargeVersion++;
    }
}
