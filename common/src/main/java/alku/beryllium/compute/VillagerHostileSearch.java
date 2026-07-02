package alku.beryllium.compute;

import alku.beryllium.mixin.NearestVisibleLivingEntitiesAccessor;
import alku.beryllium.mixin.VillagerHostilesSensorAccessor;
import com.google.common.collect.ImmutableMap;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Preserves VillagerHostilesSensor predicate order while batching per-type distance checks.
 */
public final class VillagerHostileSearch {
    private VillagerHostileSearch() {
    }

    public static Optional<LivingEntity> findNearest(LivingEntity source) {
        Optional<NearestVisibleLivingEntities> visibleEntities = source.getBrain()
            .getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES);
        if (visibleEntities.isEmpty()) {
            return Optional.empty();
        }

        NearestVisibleLivingEntitiesAccessor accessor = (NearestVisibleLivingEntitiesAccessor) visibleEntities.get();
        List<LivingEntity> candidates = accessor.beryllium$nearbyEntities();
        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        ImmutableMap<EntityType<?>, Float> acceptableDistances = VillagerHostilesSensorAccessor.beryllium$acceptableDistanceFromHostiles();
        List<HostileCandidate> hostileCandidates = new ArrayList<>();
        for (LivingEntity candidate : candidates) {
            Float acceptableDistance = acceptableDistances.get(candidate.getType());
            if (acceptableDistance != null) {
                float radius = acceptableDistance;
                hostileCandidates.add(new HostileCandidate(candidate, radius * radius));
            }
        }

        if (hostileCandidates.isEmpty()) {
            return Optional.empty();
        }

        Predicate<LivingEntity> lineOfSightTest = accessor.beryllium$lineOfSightTest();
        return EntityVariableRadiusFilter.findFirstWithinInclusiveDistancesAfterDistance(
            hostileCandidates,
            source.getX(),
            source.getY(),
            source.getZ(),
            HostileCandidate::radiusSquared,
            candidate -> lineOfSightTest.test(candidate.entity()),
            candidate -> candidate.entity().getX(),
            candidate -> candidate.entity().getY(),
            candidate -> candidate.entity().getZ()
        ).map(HostileCandidate::entity);
    }

    private record HostileCandidate(LivingEntity entity, double radiusSquared) {
    }
}
