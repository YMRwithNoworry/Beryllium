package alku.beryllium.worldgen;

import alku.beryllium.bridge.NativeBridge;

public class NativeBiomeMixer {

    public static BiomeWeightPair[] computeBiomeWeights3D(
        double[] samplePositions,
        double[] biomeCenters,
        double influenceRadius,
        int maxBiomesPerSample
    ) {
        if (!NativeBridge.isLoaded()) {
            throw new IllegalStateException("Native bridge not loaded");
        }
        if (samplePositions.length % 3 != 0) {
            throw new IllegalArgumentException("samplePositions must contain x/y/z triples");
        }
        if (biomeCenters.length % 3 != 0) {
            throw new IllegalArgumentException("biomeCenters must contain x/y/z triples");
        }

        int sampleCount = samplePositions.length / 3;
        BiomeWeightPair[] output = new BiomeWeightPair[sampleCount * maxBiomesPerSample];

        boolean success = NativeBridge.computeBiomeWeights3D(
            samplePositions,
            biomeCenters,
            influenceRadius,
            output,
            maxBiomesPerSample
        );

        if (!success) {
            throw new IllegalStateException("Failed to compute biome weights");
        }

        return output;
    }

    public static double[] interpolateBiomeValues(
        BiomeWeightPair[] weights,
        double[] biomeValues,
        int samplesPerPosition
    ) {
        if (!NativeBridge.isLoaded()) {
            throw new IllegalStateException("Native bridge not loaded");
        }
        if (weights.length % samplesPerPosition != 0) {
            throw new IllegalArgumentException("weights length must be divisible by samplesPerPosition");
        }

        int[] biomeIndices = new int[weights.length];
        double[] weightValues = new double[weights.length];

        for (int i = 0; i < weights.length; i++) {
            biomeIndices[i] = weights[i].getBiomeIndex();
            weightValues[i] = weights[i].getWeight();
        }

        int positionCount = weights.length / samplesPerPosition;
        double[] output = new double[positionCount];

        boolean success = NativeBridge.interpolateBiomeValues(
            biomeIndices,
            weightValues,
            biomeValues,
            samplesPerPosition,
            output
        );

        if (!success) {
            throw new IllegalStateException("Failed to interpolate biome values");
        }

        return output;
    }
}
