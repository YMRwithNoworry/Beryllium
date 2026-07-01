package alku.beryllium.compute;

import java.util.Arrays;

/**
 * Pure Java reference implementation for Beryllium compute kernels.
 */
public final class JavaComputeKernels {
    private JavaComputeKernels() {
    }

    public static long[] squaredDistances(int originX, int originY, int originZ, int[] positions) {
        validatePositions(positions);

        long[] result = new long[positions.length / 3];
        for (int index = 0; index < result.length; index++) {
            result[index] = squaredDistanceAt(originX, originY, originZ, positions, index);
        }

        return result;
    }

    public static double[] squaredDistances(double originX, double originY, double originZ, double[] positions) {
        validatePositions(positions);

        double[] result = new double[positions.length / 3];
        for (int index = 0; index < result.length; index++) {
            result[index] = squaredDistanceAt(originX, originY, originZ, positions, index);
        }

        return result;
    }

    public static int findNearestIndex(double originX, double originY, double originZ, double maxDistanceSquared, double[] positions) {
        validatePositions(positions);

        int nearestIndex = -1;
        double nearestDistance = -1.0;
        for (int index = 0; index < positions.length / 3; index++) {
            double distance = squaredDistanceAt(originX, originY, originZ, positions, index);
            if (maxDistanceSquared >= 0.0 && distance > maxDistanceSquared) {
                continue;
            }
            if (nearestIndex == -1 || distance < nearestDistance) {
                nearestIndex = index;
                nearestDistance = distance;
            }
        }

        return nearestIndex;
    }

    public static int[] filterWithinRadius(double originX, double originY, double originZ, double radiusSquared, double[] positions) {
        validatePositions(positions);
        if (radiusSquared < 0.0) {
            throw new IllegalArgumentException("radiusSquared must be non-negative");
        }

        int[] matches = new int[positions.length / 3];
        int count = 0;
        for (int index = 0; index < matches.length; index++) {
            if (squaredDistanceAt(originX, originY, originZ, positions, index) <= radiusSquared) {
                matches[count] = index;
                count++;
            }
        }

        return Arrays.copyOf(matches, count);
    }

    public static int[] filterWithinAabb(
        double minX,
        double minY,
        double minZ,
        double maxX,
        double maxY,
        double maxZ,
        double[] positions
    ) {
        validatePositions(positions);

        int[] matches = new int[positions.length / 3];
        int count = 0;
        for (int index = 0; index < matches.length; index++) {
            if (containsAabb(minX, minY, minZ, maxX, maxY, maxZ, positions, index)) {
                matches[count] = index;
                count++;
            }
        }

        return Arrays.copyOf(matches, count);
    }

    public static int[] filterWithinRadius(int originX, int originY, int originZ, long radiusSquared, int[] positions) {
        validatePositions(positions);
        if (radiusSquared < 0) {
            throw new IllegalArgumentException("radiusSquared must be non-negative");
        }

        int[] matches = new int[positions.length / 3];
        int count = 0;
        for (int index = 0; index < matches.length; index++) {
            if (squaredDistanceAt(originX, originY, originZ, positions, index) <= radiusSquared) {
                matches[count] = index;
                count++;
            }
        }

        return Arrays.copyOf(matches, count);
    }

    public static int[] sortByDistance(int originX, int originY, int originZ, int[] positions) {
        validatePositions(positions);

        Integer[] boxed = new Integer[positions.length / 3];
        for (int index = 0; index < boxed.length; index++) {
            boxed[index] = index;
        }

        Arrays.sort(boxed, (left, right) -> {
            long leftDistance = squaredDistanceAt(originX, originY, originZ, positions, left);
            long rightDistance = squaredDistanceAt(originX, originY, originZ, positions, right);
            int distanceComparison = Long.compare(leftDistance, rightDistance);
            return distanceComparison != 0 ? distanceComparison : Integer.compare(left, right);
        });

        int[] result = new int[boxed.length];
        for (int index = 0; index < boxed.length; index++) {
            result[index] = boxed[index];
        }
        return result;
    }

    public static void validatePositions(int[] positions) {
        if (positions == null || positions.length % 3 != 0) {
            throw new IllegalArgumentException("positions must contain x/y/z triples");
        }
    }

    public static void validatePositions(double[] positions) {
        if (positions == null || positions.length % 3 != 0) {
            throw new IllegalArgumentException("positions must contain x/y/z triples");
        }
    }

    private static long squaredDistanceAt(int originX, int originY, int originZ, int[] positions, int index) {
        int offset = index * 3;
        long dx = (long) positions[offset] - originX;
        long dy = (long) positions[offset + 1] - originY;
        long dz = (long) positions[offset + 2] - originZ;
        return dx * dx + dy * dy + dz * dz;
    }

    private static double squaredDistanceAt(double originX, double originY, double originZ, double[] positions, int index) {
        int offset = index * 3;
        double dx = positions[offset] - originX;
        double dy = positions[offset + 1] - originY;
        double dz = positions[offset + 2] - originZ;
        return dx * dx + dy * dy + dz * dz;
    }

    private static boolean containsAabb(
        double minX,
        double minY,
        double minZ,
        double maxX,
        double maxY,
        double maxZ,
        double[] positions,
        int index
    ) {
        int offset = index * 3;
        double x = positions[offset];
        double y = positions[offset + 1];
        double z = positions[offset + 2];
        return x >= minX && x < maxX && y >= minY && y < maxY && z >= minZ && z < maxZ;
    }
}
