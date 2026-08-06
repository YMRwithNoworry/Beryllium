package alku.beryllium.worldgen;

import java.util.Arrays;

/**
 * Thread-local scratch space for noise chunk interpolation.
 * Reuses arrays to avoid repeated allocations during density computation.
 */
public final class InterpolationScratch {
    public double[] corners = new double[0];
    public double[] values = new double[0];

    public double[] corners(int requiredLength) {
        if (this.corners.length < requiredLength) {
            this.corners = Arrays.copyOf(this.corners, grow(this.corners.length, requiredLength));
        }
        return this.corners;
    }

    public double[] values(int requiredLength) {
        if (this.values.length < requiredLength) {
            this.values = Arrays.copyOf(this.values, grow(this.values.length, requiredLength));
        }
        return this.values;
    }

    private static int grow(int currentLength, int requiredLength) {
        int grown = Math.max(16, currentLength);
        while (grown < requiredLength) {
            grown = Math.multiplyExact(grown, 2);
        }
        return grown;
    }
}
