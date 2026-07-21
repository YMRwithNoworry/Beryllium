package alku.beryllium.compute;

import alku.beryllium.bridge.NativeBridge;
import net.minecraft.core.BlockPos;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

/**
 * Batches PotentialCalculator point-charge math while keeping vanilla summation order.
 */
public final class PotentialEnergyBatch {
    private static final Object NATIVE_CACHE_LOCK = new Object();
    private static WeakReference<Object> cachedKey = new WeakReference<>(null);
    private static WeakReference<List<?>> cachedCharges = new WeakReference<>(null);
    private static int cachedVersion = Integer.MIN_VALUE;

    private PotentialEnergyBatch() {
    }

    public static <T> double getPotentialEnergyChange(
        List<? extends T> charges,
        BlockPos position,
        double chargeMultiplier,
        Function<? super T, BlockPos> positionGetter,
        ToDoubleFunction<? super T> chargeGetter
    ) {
        if (chargeMultiplier == 0.0) {
            return 0.0;
        }

        if (!NativeBatching.shouldUseNativePotentialBatch(charges.size())) {
            return getPotentialEnergyChangeJava(
                charges,
                position,
                chargeMultiplier,
                positionGetter,
                chargeGetter
            );
        }

        PackedCharges packedCharges = packCharges(charges, positionGetter, chargeGetter);

        return NativeBridge.computePotentialEnergyChange(
            position.getX(),
            position.getY(),
            position.getZ(),
            packedCharges.positions(),
            packedCharges.values(),
            chargeMultiplier
        );
    }

    /**
     * Reuses the native charge cache until the owner increments {@code cacheVersion} after mutating its charge list.
     */
    public static <T> double getPotentialEnergyChangeCached(
        Object cacheKey,
        int cacheVersion,
        List<? extends T> charges,
        BlockPos position,
        double chargeMultiplier,
        Function<? super T, BlockPos> positionGetter,
        ToDoubleFunction<? super T> chargeGetter
    ) {
        if (cacheKey == null) {
            throw new IllegalArgumentException("cacheKey must not be null");
        }

        if (chargeMultiplier == 0.0) {
            return 0.0;
        }

        if (!NativeBatching.shouldUseNativePotentialBatch(charges.size())) {
            return getPotentialEnergyChangeJava(
                charges,
                position,
                chargeMultiplier,
                positionGetter,
                chargeGetter
            );
        }

        synchronized (NATIVE_CACHE_LOCK) {
            PackedCharges packedCharges = null;
            try {
                if (cachedKey.get() != cacheKey || cachedCharges.get() != charges || cachedVersion != cacheVersion) {
                    packedCharges = packCharges(charges, positionGetter, chargeGetter);
                    NativeBridge.setPotentialCharges(packedCharges.positions(), packedCharges.values());
                    cachedKey = new WeakReference<>(cacheKey);
                    cachedCharges = new WeakReference<>((List<?>) charges);
                    cachedVersion = cacheVersion;
                }

                return NativeBridge.computePotentialEnergyChangeCached(
                    position.getX(),
                    position.getY(),
                    position.getZ(),
                    chargeMultiplier
                );
            } catch (IllegalStateException ignored) {
                cachedKey = new WeakReference<>(null);
                cachedCharges = new WeakReference<>(null);
                cachedVersion = Integer.MIN_VALUE;
                if (packedCharges != null) {
                    return JavaComputeKernels.potentialEnergyChange(
                        position.getX(),
                        position.getY(),
                        position.getZ(),
                        packedCharges.positions(),
                        packedCharges.values(),
                        chargeMultiplier
                    );
                }
            }
        }

        return getPotentialEnergyChangeJava(
            charges,
            position,
            chargeMultiplier,
            positionGetter,
            chargeGetter
        );
    }

    static <T> double getPotentialEnergyChangeJava(
        List<? extends T> charges,
        BlockPos position,
        double chargeMultiplier,
        Function<? super T, BlockPos> positionGetter,
        ToDoubleFunction<? super T> chargeGetter
    ) {
        if (chargeMultiplier == 0.0) {
            return 0.0;
        }

        int originX = position.getX();
        int originY = position.getY();
        int originZ = position.getZ();
        double energy = 0.0;
        for (T charge : charges) {
            BlockPos chargePosition = positionGetter.apply(charge);
            double chargeValue = chargeGetter.applyAsDouble(charge);
            double dx = (double) chargePosition.getX() - originX;
            double dy = (double) chargePosition.getY() - originY;
            double dz = (double) chargePosition.getZ() - originZ;
            double distance = dx * dx + dy * dy + dz * dz;
            energy += distance == 0.0 ? Double.POSITIVE_INFINITY : chargeValue / Math.sqrt(distance);
        }

        return energy * chargeMultiplier;
    }

    private static <T> PackedCharges packCharges(
        List<? extends T> charges,
        Function<? super T, BlockPos> positionGetter,
        ToDoubleFunction<? super T> chargeGetter
    ) {
        int[] positions = new int[charges.size() * 3];
        double[] values = new double[charges.size()];
        for (int index = 0; index < charges.size(); index++) {
            T charge = charges.get(index);
            BlockPos chargePosition = positionGetter.apply(charge);
            int offset = index * 3;
            positions[offset] = chargePosition.getX();
            positions[offset + 1] = chargePosition.getY();
            positions[offset + 2] = chargePosition.getZ();
            values[index] = chargeGetter.applyAsDouble(charge);
        }
        return new PackedCharges(positions, values);
    }

    private record PackedCharges(int[] positions, double[] values) {
    }
}
