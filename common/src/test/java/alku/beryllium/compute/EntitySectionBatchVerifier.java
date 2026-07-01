package alku.beryllium.compute;

import net.minecraft.core.BlockPos;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntityInLevelCallback;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

public final class EntitySectionBatchVerifier {
    private EntitySectionBatchVerifier() {
    }

    public static void verifyAcceptIntersecting() {
        List<SimpleEntityAccess> entities = sizedEntities(40);
        AABB query = new AABB(0.25, -1.0, -1.0, 33.25, 2.0, 2.0);

        List<Integer> accepted = new ArrayList<>();
        AbortableIterationConsumer.Continuation continuation = EntitySectionBatch.acceptIntersecting(
            entities,
            query,
            entity -> {
                accepted.add(entity.id);
                return AbortableIterationConsumer.Continuation.CONTINUE;
            }
        );

        assertContinuation(AbortableIterationConsumer.Continuation.CONTINUE, continuation, "entity-section continue");
        assertListEquals(descendingRange(33, 0), accepted, "entity-section order");
        assertArrayEquals(
            range(6, 40),
            EntitySectionBatch.intersectingEntityIndices(query, entities),
            "entity-section intersection indices"
        );
    }

    public static void verifyAcceptIntersectingAbort() {
        List<SimpleEntityAccess> entities = sizedEntities(40);
        AABB query = new AABB(0.25, -1.0, -1.0, 33.25, 2.0, 2.0);

        List<Integer> accepted = new ArrayList<>();
        AbortableIterationConsumer.Continuation continuation = EntitySectionBatch.acceptIntersecting(
            entities,
            query,
            entity -> {
                accepted.add(entity.id);
                return accepted.size() == 3
                    ? AbortableIterationConsumer.Continuation.ABORT
                    : AbortableIterationConsumer.Continuation.CONTINUE;
            }
        );

        assertContinuation(AbortableIterationConsumer.Continuation.ABORT, continuation, "entity-section abort");
        assertListEquals(List.of(33, 32, 31), accepted, "entity-section abort order");
    }

    public static void verifyTypedAcceptIntersecting() {
        List<SimpleEntityAccess> entities = sizedEntities(40);
        AABB query = new AABB(0.25, -1.0, -1.0, 33.25, 2.0, 2.0);
        EntityTypeTest<EntityAccess, SimpleEntityAccess> evenIdType = new EntityTypeTest<>() {
            @Override
            public SimpleEntityAccess tryCast(EntityAccess entity) {
                if (entity instanceof SimpleEntityAccess simpleEntity && simpleEntity.id % 2 == 0) {
                    return simpleEntity;
                }

                return null;
            }

            @Override
            public Class<? extends EntityAccess> getBaseClass() {
                return EntityAccess.class;
            }
        };

        List<Integer> accepted = new ArrayList<>();
        AbortableIterationConsumer.Continuation continuation = EntitySectionBatch.acceptIntersecting(
            entities,
            evenIdType,
            query,
            entity -> {
                accepted.add(entity.id);
                return AbortableIterationConsumer.Continuation.CONTINUE;
            }
        );

        assertContinuation(AbortableIterationConsumer.Continuation.CONTINUE, continuation, "typed entity-section continue");
        assertListEquals(List.of(32, 30, 28, 26, 24, 22, 20, 18, 16, 14, 12, 10, 8, 6, 4, 2, 0), accepted, "typed entity-section order");
    }

    private static List<SimpleEntityAccess> sizedEntities(int count) {
        List<SimpleEntityAccess> entities = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            double min = count - 1 - index;
            entities.add(new SimpleEntityAccess((int) min, new AABB(min, 0.0, 0.0, min + 0.5, 1.0, 1.0)));
        }

        return entities;
    }

    private static int[] range(int startInclusive, int endExclusive) {
        int[] result = new int[endExclusive - startInclusive];
        for (int index = 0; index < result.length; index++) {
            result[index] = startInclusive + index;
        }
        return result;
    }

    private static List<Integer> descendingRange(int startInclusive, int endInclusive) {
        List<Integer> result = new ArrayList<>(startInclusive - endInclusive + 1);
        for (int value = startInclusive; value >= endInclusive; value--) {
            result.add(value);
        }
        return result;
    }

    private static void assertContinuation(
        AbortableIterationConsumer.Continuation expected,
        AbortableIterationConsumer.Continuation actual,
        String label
    ) {
        if (actual != expected) {
            throw new AssertionError(label + " mismatch, expected " + expected + " but got " + actual);
        }
    }

    private static void assertListEquals(List<Integer> expected, List<Integer> actual, String label) {
        if (!expected.equals(actual)) {
            throw new AssertionError(label + " mismatch, expected " + expected + " but got " + actual);
        }
    }

    private static void assertArrayEquals(int[] expected, int[] actual, String label) {
        if (!java.util.Arrays.equals(expected, actual)) {
            throw new AssertionError(label + " mismatch, expected " + java.util.Arrays.toString(expected) + " but got " + java.util.Arrays.toString(actual));
        }
    }

    private record SimpleEntityAccess(int id, AABB box) implements EntityAccess {
        @Override
        public int getId() {
            return id;
        }

        @Override
        public UUID getUUID() {
            return new UUID(0L, id);
        }

        @Override
        public BlockPos blockPosition() {
            return BlockPos.ZERO;
        }

        @Override
        public AABB getBoundingBox() {
            return box;
        }

        @Override
        public void setLevelCallback(EntityInLevelCallback callback) {
        }

        @Override
        public Stream<? extends EntityAccess> getSelfAndPassengers() {
            return Stream.of(this);
        }

        @Override
        public Stream<? extends EntityAccess> getPassengersAndSelf() {
            return Stream.of(this);
        }

        @Override
        public void setRemoved(Entity.RemovalReason removalReason) {
        }

        @Override
        public boolean shouldBeSaved() {
            return true;
        }

        @Override
        public boolean isAlwaysTicking() {
            return false;
        }
    }
}
