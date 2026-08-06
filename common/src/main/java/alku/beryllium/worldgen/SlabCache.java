package alku.beryllium.worldgen;

import java.util.Arrays;

/**
 * Cache for computed noise chunk slab values.
 * Stores interpolated density values for an entire slab to avoid recomputation.
 */
public final class SlabCache {
    private long cacheKey = -1;
    private int interpolatorCount;
    private int cellCountY;
    private int cellCountXZ;
    private int cellWidth;
    private int cellHeight;
    public double[] values = new double[0];

    public boolean isValid(long key, int interpolators, int countY, int countXZ, int width, int height) {
        return this.cacheKey == key
            && this.interpolatorCount == interpolators
            && this.cellCountY == countY
            && this.cellCountXZ == countXZ
            && this.cellWidth == width
            && this.cellHeight == height;
    }

    public void update(long key, int interpolators, int countY, int countXZ, int width, int height, double[] source) {
        this.cacheKey = key;
        this.interpolatorCount = interpolators;
        this.cellCountY = countY;
        this.cellCountXZ = countXZ;
        this.cellWidth = width;
        this.cellHeight = height;

        int requiredLength = source.length;
        if (this.values.length < requiredLength) {
            this.values = Arrays.copyOf(this.values, requiredLength);
        }
        System.arraycopy(source, 0, this.values, 0, requiredLength);
    }
}
