package alku.beryllium.worldgen;

import alku.beryllium.bridge.NativeBridge;

public class NativeNoiseGenerator implements AutoCloseable {
    private final int generatorId;
    private boolean closed;

    private NativeNoiseGenerator(int generatorId) {
        this.generatorId = generatorId;
        this.closed = false;
    }

    public static NativeNoiseGenerator create(NoiseGeneratorType type, long seed) {
        if (!NativeBridge.isLoaded()) {
            throw new IllegalStateException("Native bridge not loaded");
        }

        int id = switch (type) {
            case PERLIN -> NativeBridge.createPerlinNoise(seed);
            case SIMPLEX -> NativeBridge.createSimplexNoise(seed);
            case OPENSIMPLEX2 -> NativeBridge.createOpenSimplex2Noise(seed);
        };

        if (id < 0) {
            throw new IllegalStateException("Failed to create noise generator: error code " + id);
        }

        return new NativeNoiseGenerator(id);
    }

    public void batchSample3D(double[] positions, double[] output) {
        if (closed) {
            throw new IllegalStateException("Noise generator already closed");
        }
        if (positions.length % 3 != 0) {
            throw new IllegalArgumentException("positions must contain x/y/z triples");
        }
        if (output.length != positions.length / 3) {
            throw new IllegalArgumentException("output length must match position count");
        }

        boolean success = NativeBridge.batchSampleNoise3D(generatorId, positions, output);
        if (!success) {
            throw new IllegalStateException("Batch noise sampling failed");
        }
    }

    public double sample3D(double x, double y, double z) {
        double[] positions = {x, y, z};
        double[] output = new double[1];
        batchSample3D(positions, output);
        return output[0];
    }

    @Override
    public void close() {
        if (!closed && NativeBridge.isLoaded()) {
            NativeBridge.destroyNoiseGenerator(generatorId);
            closed = true;
        }
    }

    public int getGeneratorId() {
        return generatorId;
    }

    public boolean isClosed() {
        return closed;
    }
}
