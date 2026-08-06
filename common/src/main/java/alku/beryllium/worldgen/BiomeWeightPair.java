package alku.beryllium.worldgen;

public class BiomeWeightPair {
    private final int biomeIndex;
    private final double weight;

    public BiomeWeightPair(int biomeIndex, double weight) {
        this.biomeIndex = biomeIndex;
        this.weight = weight;
    }

    public int getBiomeIndex() {
        return biomeIndex;
    }

    public double getWeight() {
        return weight;
    }

    public boolean isValid() {
        return biomeIndex >= 0;
    }
}
