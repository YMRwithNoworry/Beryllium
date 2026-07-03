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

    public static double potentialEnergyChange(
        int originX,
        int originY,
        int originZ,
        int[] positions,
        double[] charges,
        double chargeMultiplier
    ) {
        if (chargeMultiplier == 0.0) {
            return 0.0;
        }

        validatePositions(positions);
        validateCharges(positions, charges);

        double energy = 0.0;
        for (int index = 0; index < charges.length; index++) {
            double distance = blockCornerDistanceAt(originX, originY, originZ, positions, index);
            energy += distance == 0.0 ? Double.POSITIVE_INFINITY : charges[index] / Math.sqrt(distance);
        }

        return energy * chargeMultiplier;
    }

    public static int findNearestIndex(double originX, double originY, double originZ, double maxDistanceSquared, double[] positions) {
        return findNearestIndex(originX, originY, originZ, maxDistanceSquared, positions, true);
    }

    public static int findNearestIndexExclusive(double originX, double originY, double originZ, double maxDistanceSquared, double[] positions) {
        return findNearestIndex(originX, originY, originZ, maxDistanceSquared, positions, false);
    }

    public static boolean hasAnyWithinRadiusExclusive(double originX, double originY, double originZ, double maxDistanceSquared, double[] positions) {
        validatePositions(positions);

        for (int index = 0; index < positions.length / 3; index++) {
            double distance = squaredDistanceAt(originX, originY, originZ, positions, index);
            if (maxDistanceSquared < 0.0 || distance < maxDistanceSquared) {
                return true;
            }
        }

        return false;
    }

    public static int findNearestBlockCenterIndex(double originX, double originY, double originZ, int[] positions) {
        validatePositions(positions);

        int nearestIndex = -1;
        double nearestDistance = Double.MAX_VALUE;
        for (int index = 0; index < positions.length / 3; index++) {
            double distance = blockCenterDistanceAt(originX, originY, originZ, positions, index);
            if (distance < nearestDistance || (distance == nearestDistance && (nearestIndex == -1 || compareBlockPos(positions, nearestIndex, index) < 0))) {
                nearestIndex = index;
                nearestDistance = distance;
            }
        }

        return nearestIndex;
    }

    public static int findNearestBlockCornerIndex(int originX, int originY, int originZ, int[] positions) {
        validatePositions(positions);

        int nearestIndex = -1;
        double nearestDistance = Double.MAX_VALUE;
        for (int index = 0; index < positions.length / 3; index++) {
            double distance = blockCornerDistanceAt(originX, originY, originZ, positions, index);
            if (nearestIndex == -1 || distance < nearestDistance) {
                nearestIndex = index;
                nearestDistance = distance;
            }
        }

        return nearestIndex;
    }

    public static int findNearestBlockCornerIndexWithinRadius(int originX, int originY, int originZ, long radiusSquared, int[] positions) {
        validatePositions(positions);
        if (radiusSquared < 0) {
            throw new IllegalArgumentException("radiusSquared must be non-negative");
        }

        int nearestIndex = -1;
        double nearestDistance = Double.MAX_VALUE;
        for (int index = 0; index < positions.length / 3; index++) {
            if (squaredDistanceAt(originX, originY, originZ, positions, index) > radiusSquared) {
                continue;
            }

            double distance = blockCornerDistanceAt(originX, originY, originZ, positions, index);
            if (nearestIndex == -1 || distance < nearestDistance) {
                nearestIndex = index;
                nearestDistance = distance;
            }
        }

        return nearestIndex;
    }

    private static int findNearestIndex(
        double originX,
        double originY,
        double originZ,
        double maxDistanceSquared,
        double[] positions,
        boolean includeMaxDistance
    ) {
        validatePositions(positions);

        int nearestIndex = -1;
        double nearestDistance = -1.0;
        for (int index = 0; index < positions.length / 3; index++) {
            double distance = squaredDistanceAt(originX, originY, originZ, positions, index);
            if (maxDistanceSquared >= 0.0 && exceedsMaxDistance(distance, maxDistanceSquared, includeMaxDistance)) {
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

    public static int[] filterWithinRadiusExclusive(double originX, double originY, double originZ, double radiusSquared, double[] positions) {
        validatePositions(positions);
        if (radiusSquared < 0.0) {
            throw new IllegalArgumentException("radiusSquared must be non-negative");
        }

        int[] matches = new int[positions.length / 3];
        int count = 0;
        for (int index = 0; index < matches.length; index++) {
            if (squaredDistanceAt(originX, originY, originZ, positions, index) < radiusSquared) {
                matches[count] = index;
                count++;
            }
        }

        return Arrays.copyOf(matches, count);
    }

    public static int[] sortWithinRadiusExclusive(double originX, double originY, double originZ, double radiusSquared, double[] positions) {
        validatePositions(positions);
        if (radiusSquared < 0.0) {
            throw new IllegalArgumentException("radiusSquared must be non-negative");
        }

        Integer[] matches = new Integer[positions.length / 3];
        int count = 0;
        for (int index = 0; index < matches.length; index++) {
            if (squaredDistanceAt(originX, originY, originZ, positions, index) < radiusSquared) {
                matches[count] = index;
                count++;
            }
        }

        Arrays.sort(matches, 0, count, (left, right) -> {
            double leftDistance = squaredDistanceAt(originX, originY, originZ, positions, left);
            double rightDistance = squaredDistanceAt(originX, originY, originZ, positions, right);
            int distanceComparison = Double.compare(leftDistance, rightDistance);
            return distanceComparison != 0 ? distanceComparison : Integer.compare(left, right);
        });

        int[] result = new int[count];
        for (int index = 0; index < count; index++) {
            result[index] = matches[index];
        }
        return result;
    }

    public static int[] filterWithinRadii(double originX, double originY, double originZ, double[] positions, double[] radiiSquared) {
        validatePositions(positions);
        validateRadii(positions, radiiSquared);

        int[] matches = new int[positions.length / 3];
        int count = 0;
        for (int index = 0; index < matches.length; index++) {
            double radiusSquared = radiiSquared[index];
            if (radiusSquared < 0.0) {
                throw new IllegalArgumentException("radiusSquared must be non-negative");
            }
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

    public static int[] filterIntersectingAabb(
        double queryMinX,
        double queryMinY,
        double queryMinZ,
        double queryMaxX,
        double queryMaxY,
        double queryMaxZ,
        double[] boxes
    ) {
        validateBoxes(boxes);

        int[] matches = new int[boxes.length / 6];
        int count = 0;
        for (int index = 0; index < matches.length; index++) {
            if (intersectsAabb(queryMinX, queryMinY, queryMinZ, queryMaxX, queryMaxY, queryMaxZ, boxes, index)) {
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

    public static int countWithinRadius(int originX, int originY, int originZ, long radiusSquared, int[] positions) {
        validatePositions(positions);
        if (radiusSquared < 0) {
            throw new IllegalArgumentException("radiusSquared must be non-negative");
        }

        int count = 0;
        for (int index = 0; index < positions.length / 3; index++) {
            if (squaredDistanceAt(originX, originY, originZ, positions, index) <= radiusSquared) {
                count++;
            }
        }

        return count;
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

    public static int[] sortByBlockDistance(int originX, int originY, int originZ, int[] positions) {
        validatePositions(positions);

        Integer[] boxed = new Integer[positions.length / 3];
        for (int index = 0; index < boxed.length; index++) {
            boxed[index] = index;
        }

        Arrays.sort(boxed, (left, right) -> {
            double leftDistance = blockCornerDistanceAt(originX, originY, originZ, positions, left);
            double rightDistance = blockCornerDistanceAt(originX, originY, originZ, positions, right);
            int distanceComparison = Double.compare(leftDistance, rightDistance);
            return distanceComparison != 0 ? distanceComparison : Integer.compare(left, right);
        });

        int[] result = new int[boxed.length];
        for (int index = 0; index < boxed.length; index++) {
            result[index] = boxed[index];
        }
        return result;
    }

    public static int[] sortByDistance(double originX, double originY, double originZ, double[] positions) {
        validatePositions(positions);

        Integer[] boxed = new Integer[positions.length / 3];
        for (int index = 0; index < boxed.length; index++) {
            boxed[index] = index;
        }

        Arrays.sort(boxed, (left, right) -> {
            double leftDistance = squaredDistanceAt(originX, originY, originZ, positions, left);
            double rightDistance = squaredDistanceAt(originX, originY, originZ, positions, right);
            int distanceComparison = Double.compare(leftDistance, rightDistance);
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

    public static void validateBoxes(double[] boxes) {
        if (boxes == null || boxes.length % 6 != 0) {
            throw new IllegalArgumentException("boxes must contain min/max x/y/z sextuples");
        }
    }

    public static void validateRadii(double[] positions, double[] radiiSquared) {
        validatePositions(positions);
        if (radiiSquared == null || radiiSquared.length != positions.length / 3) {
            throw new IllegalArgumentException("radiiSquared must contain one value per position");
        }
    }

    public static void validateCharges(int[] positions, double[] charges) {
        validatePositions(positions);
        if (charges == null || charges.length != positions.length / 3) {
            throw new IllegalArgumentException("charges must contain one value per position");
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

    private static double blockCenterDistanceAt(double originX, double originY, double originZ, int[] positions, int index) {
        int offset = index * 3;
        double dx = positions[offset] + 0.5 - originX;
        double dy = positions[offset + 1] + 0.5 - originY;
        double dz = positions[offset + 2] + 0.5 - originZ;
        return dx * dx + dy * dy + dz * dz;
    }

    private static double blockCornerDistanceAt(int originX, int originY, int originZ, int[] positions, int index) {
        int offset = index * 3;
        double dx = (double) positions[offset] - originX;
        double dy = (double) positions[offset + 1] - originY;
        double dz = (double) positions[offset + 2] - originZ;
        return dx * dx + dy * dy + dz * dz;
    }

    private static int compareBlockPos(int[] positions, int leftIndex, int rightIndex) {
        int leftOffset = leftIndex * 3;
        int rightOffset = rightIndex * 3;
        int yComparison = positions[leftOffset + 1] - positions[rightOffset + 1];
        if (yComparison != 0) {
            return yComparison;
        }

        int zComparison = positions[leftOffset + 2] - positions[rightOffset + 2];
        return zComparison != 0 ? zComparison : positions[leftOffset] - positions[rightOffset];
    }

    private static boolean exceedsMaxDistance(double distance, double maxDistanceSquared, boolean includeMaxDistance) {
        return includeMaxDistance ? distance > maxDistanceSquared : distance >= maxDistanceSquared;
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

    private static boolean intersectsAabb(
        double queryMinX,
        double queryMinY,
        double queryMinZ,
        double queryMaxX,
        double queryMaxY,
        double queryMaxZ,
        double[] boxes,
        int index
    ) {
        int offset = index * 6;
        return boxes[offset + 3] > queryMinX
            && boxes[offset] < queryMaxX
            && boxes[offset + 4] > queryMinY
            && boxes[offset + 1] < queryMaxY
            && boxes[offset + 5] > queryMinZ
            && boxes[offset + 2] < queryMaxZ;
    }
}
