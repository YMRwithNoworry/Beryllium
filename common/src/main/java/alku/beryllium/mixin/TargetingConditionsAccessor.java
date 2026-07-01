package alku.beryllium.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.function.Predicate;

@Mixin(TargetingConditions.class)
public interface TargetingConditionsAccessor {
    @Accessor("isCombat")
    boolean beryllium$isCombat();

    @Accessor("range")
    double beryllium$range();

    @Accessor("checkLineOfSight")
    boolean beryllium$checkLineOfSight();

    @Accessor("testInvisible")
    boolean beryllium$testInvisible();

    @Accessor("selector")
    @Nullable
    Predicate<LivingEntity> beryllium$selector();
}
