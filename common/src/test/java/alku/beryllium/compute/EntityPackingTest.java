package alku.beryllium.compute;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class EntityPackingTest {
    @Test
    void packPositionsShouldFlattenCoordinateObjects() {
        List<SimplePoint> points = List.of(new SimplePoint(1.0, 2.0, 3.0), new SimplePoint(-4.0, 5.0, -6.0));
        assertArrayEquals(
            new double[] {1.0, 2.0, 3.0, -4.0, 5.0, -6.0},
            EntityPacking.packPositions(points, p -> p.x, p -> p.y, p -> p.z)
        );
    }

    private record SimplePoint(double x, double y, double z) {
    }
}
