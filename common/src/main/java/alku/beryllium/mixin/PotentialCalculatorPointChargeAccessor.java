package alku.beryllium.mixin;

import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.world.level.PotentialCalculator$PointCharge")
public interface PotentialCalculatorPointChargeAccessor {
    @Accessor("pos")
    BlockPos beryllium$pos();

    @Accessor("charge")
    double beryllium$charge();
}
