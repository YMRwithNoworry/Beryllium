package alku.beryllium.compute;

import alku.beryllium.mixin.TargetingConditionsAccessor;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

/**
 * Evaluates TargetingConditions using precomputed squared distances.
 */
public final class TargetingConditionsBatch {
    private TargetingConditionsBatch() {
    }

    public static boolean pretest(@Nullable LivingEntity source, LivingEntity target, TargetingConditions conditions) {
        if (source == target) {
            return false;
        }
        if (!target.canBeSeenByAnyone()) {
            return false;
        }

        TargetingConditionsAccessor accessor = (TargetingConditionsAccessor) conditions;
        Predicate<LivingEntity> selector = accessor.beryllium$selector();
        if (selector != null && !selector.test(target)) {
            return false;
        }

        if (source == null) {
            return !accessor.beryllium$isCombat() || (target.canBeSeenAsEnemy() && target.level().getDifficulty() != Difficulty.PEACEFUL);
        }

        if (accessor.beryllium$isCombat() && (!source.canAttack(target) || !source.canAttackType(target.getType()) || source.isAlliedTo(target))) {
            return false;
        }

        if (accessor.beryllium$checkLineOfSight() && source instanceof Mob mob && !mob.getSensing().hasLineOfSight(target)) {
            return false;
        }

        return true;
    }

    public static boolean testDistance(@Nullable LivingEntity source, LivingEntity target, TargetingConditions conditions, double distanceSquared) {
        TargetingConditionsAccessor accessor = (TargetingConditionsAccessor) conditions;
        if (source == null) {
            return true;
        }

        double range = accessor.beryllium$range();
        if (range > 0.0) {
            double visibilityPercent = accessor.beryllium$testInvisible() ? target.getVisibilityPercent(source) : 1.0;
            double maxDistance = Math.max(range * visibilityPercent, 2.0);
            if (distanceSquared > maxDistance * maxDistance) {
                return false;
            }
        }

        return true;
    }
}
