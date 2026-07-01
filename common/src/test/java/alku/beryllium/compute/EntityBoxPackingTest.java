package alku.beryllium.compute;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntityInLevelCallback;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class EntityBoxPackingTest {
    @Test
    void packBoxesShouldFlattenEntityAccessBoundingBoxes() {
        List<EntityAccess> entities = List.of(
            new SimpleEntityAccess(new AABB(0.0, 1.0, 2.0, 3.0, 4.0, 5.0)),
            new SimpleEntityAccess(new AABB(-1.0, -2.0, -3.0, 1.0, 2.0, 3.0))
        );

        assertArrayEquals(
            new double[] {0.0, 1.0, 2.0, 3.0, 4.0, 5.0, -1.0, -2.0, -3.0, 1.0, 2.0, 3.0},
            EntityBoxPacking.packBoxes(entities)
        );
    }

    private record SimpleEntityAccess(AABB box) implements EntityAccess {
        @Override
        public int getId() {
            return 0;
        }

        @Override
        public UUID getUUID() {
            return new UUID(0L, 0L);
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
