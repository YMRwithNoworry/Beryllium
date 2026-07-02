package alku.beryllium.mixin;

import alku.beryllium.compute.BlockDistanceSort;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.PrepareRamNearestTarget;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Path;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Mixin(PrepareRamNearestTarget.class)
public abstract class PrepareRamNearestTargetMixin {
    @Shadow
    @Final
    private int minRamDistance;

    @Shadow
    @Final
    private int maxRamDistance;

    @Shadow
    private boolean isWalkableBlock(PathfinderMob mob, BlockPos position) {
        throw new AssertionError();
    }

    @Inject(method = "calculateRammingStartPosition", at = @At("HEAD"), cancellable = true)
    private void beryllium$calculateRammingStartPosition(
        PathfinderMob mob,
        LivingEntity target,
        CallbackInfoReturnable<Optional<BlockPos>> callback
    ) {
        BlockPos targetPosition = target.blockPosition();
        if (!this.isWalkableBlock(mob, targetPosition)) {
            callback.setReturnValue(Optional.empty());
            return;
        }

        List<BlockPos> startPositions = new ArrayList<>();
        BlockPos.MutableBlockPos mutablePosition = targetPosition.mutable();
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            mutablePosition.set(targetPosition);
            for (int distance = 0; distance < this.maxRamDistance; distance++) {
                if (!this.isWalkableBlock(mob, mutablePosition.move(direction))) {
                    mutablePosition.move(direction.getOpposite());
                    break;
                }
            }

            if (mutablePosition.distManhattan(targetPosition) >= this.minRamDistance) {
                startPositions.add(mutablePosition.immutable());
            }
        }

        PathNavigation navigation = mob.getNavigation();
        BlockPos reachableStartPosition = BlockDistanceSort.findFirstSortedByDistance(
            startPositions,
            mob.blockPosition(),
            position -> position,
            position -> {
                Path path = navigation.createPath(position, 0);
                return path != null && path.canReach();
            }
        );
        callback.setReturnValue(Optional.ofNullable(reachableStartPosition));
    }
}
