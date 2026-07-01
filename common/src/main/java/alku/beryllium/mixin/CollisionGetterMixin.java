package alku.beryllium.mixin;

import alku.beryllium.compute.SupportingBlockSearch;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockCollisions;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import java.util.Optional;

@Mixin(CollisionGetter.class)
public interface CollisionGetterMixin {
    /**
     * @reason Batch supporting-block center distance selection through the native nearest-block kernel.
     * @author YMRwithNoworry
     */
    @Overwrite
    default Optional<BlockPos> findSupportingBlock(Entity entity, AABB box) {
        Vec3 origin = entity.position();
        return SupportingBlockSearch.findNearest(
            () -> new BlockCollisions<BlockPos>((CollisionGetter) this, entity, box, false, (position, shape) -> position),
            origin.x,
            origin.y,
            origin.z
        );
    }
}
