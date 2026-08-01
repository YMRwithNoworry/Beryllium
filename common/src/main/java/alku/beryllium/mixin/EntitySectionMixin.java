package alku.beryllium.mixin;

import net.minecraft.util.ClassInstanceMultiMap;
import net.minecraft.world.level.entity.EntitySection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Iterator;

/**
 * Mirrors Lithium's allocation-free iterator path for untyped entity-section queries.
 */
@Mixin(EntitySection.class)
public class EntitySectionMixin {
    @Redirect(
        method = "getEntities(Lnet/minecraft/world/phys/AABB;Lnet/minecraft/util/AbortableIterationConsumer;)Lnet/minecraft/util/AbortableIterationConsumer$Continuation;",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/util/ClassInstanceMultiMap;iterator()Ljava/util/Iterator;"
        )
    )
    private Iterator<?> beryllium$directIterator(ClassInstanceMultiMap<?> storage) {
        return ((ClassInstanceMultiMapAccessor<?>) storage).beryllium$allInstances().iterator();
    }
}
