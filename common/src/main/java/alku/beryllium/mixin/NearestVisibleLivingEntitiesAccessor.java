package alku.beryllium.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import java.util.function.Predicate;

@Mixin(NearestVisibleLivingEntities.class)
public interface NearestVisibleLivingEntitiesAccessor {
    @Accessor("nearbyEntities")
    List<LivingEntity> beryllium$nearbyEntities();

    @Accessor("lineOfSightTest")
    Predicate<LivingEntity> beryllium$lineOfSightTest();
}
