package alku.beryllium.mixin;

import alku.beryllium.compute.PotentialEnergyBatch;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.PotentialCalculator;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;

@Mixin(PotentialCalculator.class)
public class PotentialCalculatorMixin {
    @Shadow
    @Final
    private List<?> charges;

    /**
     * @reason Batch point-charge potential math through the native backend while preserving vanilla summation order.
     * @author YMRwithNoworry
     */
    @Overwrite
    public double getPotentialEnergyChange(BlockPos position, double chargeMultiplier) {
        return PotentialEnergyBatch.getPotentialEnergyChange(
            this.charges,
            position,
            chargeMultiplier,
            charge -> ((PotentialCalculatorPointChargeAccessor) charge).beryllium$pos(),
            charge -> ((PotentialCalculatorPointChargeAccessor) charge).beryllium$charge()
        );
    }
}
