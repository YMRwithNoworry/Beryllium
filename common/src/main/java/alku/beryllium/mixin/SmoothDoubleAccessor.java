package alku.beryllium.mixin;

import net.minecraft.util.SmoothDouble;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SmoothDouble.class)
public interface SmoothDoubleAccessor {
    @Accessor("targetValue")
    double beryllium$targetValue();

    @Accessor("remainingValue")
    double beryllium$remainingValue();

    @Accessor("lastAmount")
    double beryllium$lastAmount();
}
