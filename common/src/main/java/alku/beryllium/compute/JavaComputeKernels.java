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
        return findNearestBlockCenterIndex(originX, originY, originZ, positions, positions.length / 3);
    }

    public static int findNearestBlockCenterIndex(
        double originX,
        double originY,
        double originZ,
        int[] positions,
        int positionCount
    ) {
        validatePositionCount(positions, positionCount);

        int nearestIndex = -1;
        double nearestDistance = Double.MAX_VALUE;
        for (int index = 0; index < positionCount; index++) {
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

        int[] result = new int[positions.length / 3];
        int count = filterWithinRadius(originX, originY, originZ, radiusSquared, positions, result);
        return Arrays.copyOf(result, count);
    }

    public static int filterWithinRadius(
        double originX,
        double originY,
        double originZ,
        double radiusSquared,
        double[] positions,
        int[] output
    ) {
        validatePositions(positions);
        if (radiusSquared < 0.0) {
            throw new IllegalArgumentException("radiusSquared must be non-negative");
        }
        validateOutputCapacity(positions.length / 3, output);

        int count = 0;
        for (int index = 0; index < positions.length / 3; index++) {
            if (squaredDistanceAt(originX, originY, originZ, positions, index) <= radiusSquared) {
                output[count] = index;
                count++;
            }
        }

        return count;
    }

    public static int[] filterWithinRadiusExclusive(double originX, double originY, double originZ, double radiusSquared, double[] positions) {
        validatePositions(positions);
        if (radiusSquared < 0.0) {
            throw new IllegalArgumentException("radiusSquared must be non-negative");
        }

        int[] result = new int[positions.length / 3];
        int count = filterWithinRadiusExclusive(originX, originY, originZ, radiusSquared, positions, result);
        return Arrays.copyOf(result, count);
    }

    public static int filterWithinRadiusExclusive(
        double originX,
        double originY,
        double originZ,
        double radiusSquared,
        double[] positions,
        int[] output
    ) {
        validatePositions(positions);
        if (radiusSquared < 0.0) {
            throw new IllegalArgumentException("radiusSquared must be non-negative");
        }
        validateOutputCapacity(positions.length / 3, output);

        int count = 0;
        for (int index = 0; index < positions.length / 3; index++) {
            if (squaredDistanceAt(originX, originY, originZ, positions, index) < radiusSquared) {
                output[count] = index;
                count++;
            }
        }

        return count;
    }

    public static int[] filterWithinExclusiveChunkDistance(
        double originX,
        double originZ,
        double radiusSquared,
        double[] positions
    ) {
        validateXzPositions(positions);
        if (radiusSquared < 0.0) {
            throw new IllegalArgumentException("radiusSquared must be non-negative");
        }

        int[] result = new int[positions.length / 2];
        int count = filterWithinExclusiveChunkDistance(originX, originZ, radiusSquared, positions, result);
        return Arrays.copyOf(result, count);
    }

    public static int filterWithinExclusiveChunkDistance(
        double originX,
        double originZ,
        double radiusSquared,
        double[] positions,
        int[] output
    ) {
        validateXzPositions(positions);
        if (radiusSquared < 0.0) {
            throw new IllegalArgumentException("radiusSquared must be non-negative");
        }
        validateOutputCapacity(positions.length / 2, output);

        int count = 0;
        for (int index = 0; index < positions.length / 2; index++) {
            int offset = index * 2;
            double dx = positions[offset] - originX;
            double dz = positions[offset + 1] - originZ;
            if (dx * dx + dz * dz < radiusSquared) {
                output[count] = index;
                count++;
            }
        }

        return count;
    }

    public static int[] sortWithinRadiusExclusive(double originX, double originY, double originZ, double radiusSquared, double[] positions) {
        validatePositions(positions);
        if (radiusSquared < 0.0) {
            throw new IllegalArgumentException("radiusSquared must be non-negative");
        }

        int[] result = new int[positions.length / 3];
        int count = sortWithinRadiusExclusive(originX, originY, originZ, radiusSquared, positions, result);
        return Arrays.copyOf(result, count);
    }

    public static int sortWithinRadiusExclusive(
        double originX,
        double originY,
        double originZ,
        double radiusSquared,
        double[] positions,
        int[] output
    ) {
        validatePositions(positions);
        if (radiusSquared < 0.0) {
            throw new IllegalArgumentException("radiusSquared must be non-negative");
        }
        validateOutputCapacity(positions.length / 3, output);

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

        for (int index = 0; index < count; index++) {
            output[index] = matches[index];
        }
        return count;
    }

    public static int sortByDistanceAndCountWithinRadiusExclusive(
        double originX,
        double originY,
        double originZ,
        double radiusSquared,
        double[] positions,
        int[] output
    ) {
        validatePositions(positions);
        if (radiusSquared < 0.0) {
            throw new IllegalArgumentException("radiusSquared must be non-negative");
        }
        int positionCount = positions.length / 3;
        validateOutputCapacity(positionCount, output);

        DistanceIndex[] distances = new DistanceIndex[positionCount];
        for (int index = 0; index < positionCount; index++) {
            distances[index] = new DistanceIndex(
                index,
                squaredDistanceAt(originX, originY, originZ, positions, index)
            );
        }
        Arrays.sort(distances, (left, right) -> {
            int distanceComparison = Double.compare(left.distance(), right.distance());
            return distanceComparison != 0
                ? distanceComparison
                : Integer.compare(left.index(), right.index());
        });

        int prefixCount = 0;
        for (int outputIndex = 0; outputIndex < positionCount; outputIndex++) {
            DistanceIndex distance = distances[outputIndex];
            output[outputIndex] = distance.index();
            if (distance.distance() < radiusSquared) {
                prefixCount++;
            }
        }
        return prefixCount;
    }

    public static int[] filterWithinRadii(double originX, double originY, double originZ, double[] positions, double[] radiiSquared) {
        validatePositions(positions);
        validateRadii(positions, radiiSquared);

        int[] result = new int[positions.length / 3];
        int count = filterWithinRadii(originX, originY, originZ, positions, radiiSquared, result);
        return Arrays.copyOf(result, count);
    }

    public static int filterWithinRadii(
        double originX,
        double originY,
        double originZ,
        double[] positions,
        double[] radiiSquared,
        int[] output
    ) {
        validatePositions(positions);
        validateRadii(positions, radiiSquared);
        validateOutputCapacity(positions.length / 3, output);

        int count = 0;
        for (int index = 0; index < positions.length / 3; index++) {
            double radiusSquared = radiiSquared[index];
            if (radiusSquared < 0.0) {
                throw new IllegalArgumentException("radiusSquared must be non-negative");
            }
            if (squaredDistanceAt(originX, originY, originZ, positions, index) <= radiusSquared) {
                output[count] = index;
                count++;
            }
        }

        return count;
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

        int[] result = new int[positions.length / 3];
        int count = filterWithinAabb(minX, minY, minZ, maxX, maxY, maxZ, positions, result);
        return Arrays.copyOf(result, count);
    }

    public static int filterWithinAabb(
        double minX,
        double minY,
        double minZ,
        double maxX,
        double maxY,
        double maxZ,
        double[] positions,
        int[] output
    ) {
        validatePositions(positions);
        validateOutputCapacity(positions.length / 3, output);

        int count = 0;
        for (int index = 0; index < positions.length / 3; index++) {
            if (containsAabb(minX, minY, minZ, maxX, maxY, maxZ, positions, index)) {
                output[count] = index;
                count++;
            }
        }

        return count;
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

        int[] result = new int[boxes.length / 6];
        int count = filterIntersectingAabb(
            queryMinX,
            queryMinY,
            queryMinZ,
            queryMaxX,
            queryMaxY,
            queryMaxZ,
            boxes,
            result
        );
        return Arrays.copyOf(result, count);
    }

    public static int filterIntersectingAabb(
        double queryMinX,
        double queryMinY,
        double queryMinZ,
        double queryMaxX,
        double queryMaxY,
        double queryMaxZ,
        double[] boxes,
        int[] output
    ) {
        validateBoxes(boxes);
        validateOutputCapacity(boxes.length / 6, output);

        int count = 0;
        for (int index = 0; index < boxes.length / 6; index++) {
            if (intersectsAabb(queryMinX, queryMinY, queryMinZ, queryMaxX, queryMaxY, queryMaxZ, boxes, index)) {
                output[count] = index;
                count++;
            }
        }

        return count;
    }

    public static int[] filterWithinRadius(int originX, int originY, int originZ, long radiusSquared, int[] positions) {
        validatePositions(positions);
        if (radiusSquared < 0) {
            throw new IllegalArgumentException("radiusSquared must be non-negative");
        }

        int[] result = new int[positions.length / 3];
        int count = filterWithinRadius(originX, originY, originZ, radiusSquared, positions, result);
        return Arrays.copyOf(result, count);
    }

    public static int filterWithinRadius(
        int originX,
        int originY,
        int originZ,
        long radiusSquared,
        int[] positions,
        int[] output
    ) {
        validatePositions(positions);
        if (radiusSquared < 0) {
            throw new IllegalArgumentException("radiusSquared must be non-negative");
        }
        validateOutputCapacity(positions.length / 3, output);

        int count = 0;
        for (int index = 0; index < positions.length / 3; index++) {
            if (squaredDistanceAt(originX, originY, originZ, positions, index) <= radiusSquared) {
                output[count] = index;
                count++;
            }
        }

        return count;
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

    /**
     * Selects packed chunk positions with the same distance-only Top-K algorithm used by Guava.
     */
    public static int selectNearestChunkIndices(
        int originX,
        int originZ,
        long[] packedChunkPositions,
        int limit,
        int[] output
    ) {
        int selectedCount = validateChunkSelection(packedChunkPositions, limit, output);
        if (selectedCount == 0) {
            return 0;
        }

        int[] distances = new int[packedChunkPositions.length];
        for (int index = 0; index < packedChunkPositions.length; index++) {
            distances[index] = chunkDistanceSquared(originX, originZ, packedChunkPositions[index]);
        }

        int bufferCapacity = selectedCount == packedChunkPositions.length
            ? selectedCount
            : Math.multiplyExact(selectedCount, 2);
        int[] buffer = new int[bufferCapacity];
        int bufferSize = 0;
        int thresholdDistance = 0;

        for (int index = 0; index < packedChunkPositions.length; index++) {
            int distance = distances[index];
            if (bufferSize == 0) {
                buffer[0] = index;
                thresholdDistance = distance;
                bufferSize = 1;
            } else if (bufferSize < selectedCount) {
                buffer[bufferSize] = index;
                bufferSize++;
                if (distance > thresholdDistance) {
                    thresholdDistance = distance;
                }
            } else if (distance < thresholdDistance) {
                buffer[bufferSize] = index;
                bufferSize++;
                if (bufferSize == selectedCount * 2) {
                    thresholdDistance = trimChunkSelection(buffer, selectedCount, distances);
                    bufferSize = selectedCount;
                }
            }
        }

        stableSortChunkSelection(buffer, 0, bufferSize, distances);
        System.arraycopy(buffer, 0, output, 0, selectedCount);
        return selectedCount;
    }

    public static int validateChunkSelection(long[] packedChunkPositions, int limit, int[] output) {
        if (packedChunkPositions == null) {
            throw new IllegalArgumentException("packedChunkPositions must not be null");
        }
        if (limit < 0) {
            throw new IllegalArgumentException("limit must be non-negative");
        }

        int selectedCount = Math.min(limit, packedChunkPositions.length);
        if (output == null || output.length < selectedCount) {
            throw new IllegalArgumentException("output must contain at least one slot per selected chunk");
        }
        return selectedCount;
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

    public static void validateXzPositions(double[] positions) {
        if (positions == null || positions.length % 2 != 0) {
            throw new IllegalArgumentException("positions must contain x/z pairs");
        }
    }

    public static void validatePositionCount(int[] positions, int positionCount) {
        validatePositions(positions);
        if (positionCount < 0 || positionCount > positions.length / 3) {
            throw new IllegalArgumentException("positionCount must not exceed the packed position count");
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

    public static void validateOutputCapacity(int requiredLength, int[] output) {
        if (output == null || output.length < requiredLength) {
            throw new IllegalArgumentException("output must contain at least one slot per position");
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

    private static int chunkDistanceSquared(int originX, int originZ, long packedChunkPosition) {
        int dx = (int) packedChunkPosition - originX;
        int dz = (int) (packedChunkPosition >>> 32) - originZ;
        return dx * dx + dz * dz;
    }

    private static int trimChunkSelection(int[] buffer, int selectedCount, int[] distances) {
        int left = 0;
        int right = selectedCount * 2 - 1;
        int minThresholdPosition = 0;
        int iterations = 0;
        int maxIterations = ceilingLog2(right - left) * 3;

        while (left < right) {
            int pivotIndex = (left + right + 1) >>> 1;
            int pivotNewIndex = partitionChunkSelection(buffer, left, right, pivotIndex, distances);
            if (pivotNewIndex > selectedCount) {
                right = pivotNewIndex - 1;
            } else if (pivotNewIndex < selectedCount) {
                left = Math.max(pivotNewIndex, left + 1);
                minThresholdPosition = pivotNewIndex;
            } else {
                break;
            }

            iterations++;
            if (iterations >= maxIterations) {
                stableSortChunkSelection(buffer, left, right + 1, distances);
                break;
            }
        }

        int thresholdDistance = distances[buffer[minThresholdPosition]];
        for (int index = minThresholdPosition + 1; index < selectedCount; index++) {
            thresholdDistance = Math.max(thresholdDistance, distances[buffer[index]]);
        }
        return thresholdDistance;
    }

    private static int partitionChunkSelection(
        int[] buffer,
        int left,
        int right,
        int pivotIndex,
        int[] distances
    ) {
        int pivotValue = buffer[pivotIndex];
        buffer[pivotIndex] = buffer[right];
        int pivotNewIndex = left;
        for (int index = left; index < right; index++) {
            if (Integer.compare(distances[buffer[index]], distances[pivotValue]) < 0) {
                int previous = buffer[pivotNewIndex];
                buffer[pivotNewIndex] = buffer[index];
                buffer[index] = previous;
                pivotNewIndex++;
            }
        }
        buffer[right] = buffer[pivotNewIndex];
        buffer[pivotNewIndex] = pivotValue;
        return pivotNewIndex;
    }

    private static void stableSortChunkSelection(int[] buffer, int from, int to, int[] distances) {
        Integer[] boxed = new Integer[to - from];
        for (int index = from; index < to; index++) {
            boxed[index - from] = buffer[index];
        }
        Arrays.sort(boxed, (left, right) -> Integer.compare(distances[left], distances[right]));
        for (int index = from; index < to; index++) {
            buffer[index] = boxed[index - from];
        }
    }

    private static int ceilingLog2(int value) {
        return value <= 1 ? 0 : Integer.SIZE - Integer.numberOfLeadingZeros(value - 1);
    }

    private record DistanceIndex(int index, double distance) {
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
