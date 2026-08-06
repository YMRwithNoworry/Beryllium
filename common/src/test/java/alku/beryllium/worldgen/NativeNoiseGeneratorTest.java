package alku.beryllium.worldgen;

import alku.beryllium.bridge.NativeBridge;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIf("alku.beryllium.bridge.NativeBridge#isLoaded")
class NativeNoiseGeneratorTest {
    @BeforeAll
    static void setUp() {
        NativeBridge.initialize();
    }

    @Test
    void perlin_noise_generates_deterministic_values() {
        try (NativeNoiseGenerator generator = NativeNoiseGenerator.create(NoiseGeneratorType.PERLIN, 12345L)) {
            double v1 = generator.sample3D(1.5, 2.5, 3.5);
            double v2 = generator.sample3D(1.5, 2.5, 3.5);
            assertEquals(v1, v2, 1e-10);
        }
    }

    @Test
    void simplex_noise_generates_deterministic_values() {
        try (NativeNoiseGenerator generator = NativeNoiseGenerator.create(NoiseGeneratorType.SIMPLEX, 12345L)) {
            double v1 = generator.sample3D(1.5, 2.5, 3.5);
            double v2 = generator.sample3D(1.5, 2.5, 3.5);
            assertEquals(v1, v2, 1e-10);
        }
    }

    @Test
    void opensimplex2_noise_generates_deterministic_values() {
        try (NativeNoiseGenerator generator = NativeNoiseGenerator.create(NoiseGeneratorType.OPENSIMPLEX2, 12345L)) {
            double v1 = generator.sample3D(1.5, 2.5, 3.5);
            double v2 = generator.sample3D(1.5, 2.5, 3.5);
            assertEquals(v1, v2, 1e-10);
        }
    }

    @Test
    void batch_sampling_produces_same_results_as_individual() {
        try (NativeNoiseGenerator generator = NativeNoiseGenerator.create(NoiseGeneratorType.PERLIN, 12345L)) {
            double[] positions = {1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0};
            double[] batchOutput = new double[3];
            generator.batchSample3D(positions, batchOutput);

            double v1 = generator.sample3D(1.0, 2.0, 3.0);
            double v2 = generator.sample3D(4.0, 5.0, 6.0);
            double v3 = generator.sample3D(7.0, 8.0, 9.0);

            assertEquals(v1, batchOutput[0], 1e-10);
            assertEquals(v2, batchOutput[1], 1e-10);
            assertEquals(v3, batchOutput[2], 1e-10);
        }
    }

    @Test
    void different_seeds_produce_different_noise() {
        try (NativeNoiseGenerator gen1 = NativeNoiseGenerator.create(NoiseGeneratorType.PERLIN, 12345L);
             NativeNoiseGenerator gen2 = NativeNoiseGenerator.create(NoiseGeneratorType.PERLIN, 54321L)) {
            double v1 = gen1.sample3D(1.5, 2.5, 3.5);
            double v2 = gen2.sample3D(1.5, 2.5, 3.5);
            assertNotEquals(v1, v2);
        }
    }

    @Test
    void closed_generator_throws_exception() {
        NativeNoiseGenerator generator = NativeNoiseGenerator.create(NoiseGeneratorType.PERLIN, 12345L);
        generator.close();
        assertThrows(IllegalStateException.class, () -> generator.sample3D(1.0, 2.0, 3.0));
    }
}
