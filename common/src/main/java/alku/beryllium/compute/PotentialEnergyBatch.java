package alku.beryllium.compute;

import alku.beryllium.bridge.NativeBridge;
import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

/**
 * Batches PotentialCalculator point-charge math while keeping vanilla summation order.
 */
public final class PotentialEnergyBatch {
    private static volatile Object cachedChargesList;
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

        int[] positions = new int[charges.size() * 3];
        double[] chargeValues = new double[charges.size()];
        for (int index = 0; index < charges.size(); index++) {
            T charge = charges.get(index);
            BlockPos chargePosition = positionGetter.apply(charge);
            int offset = index * 3;
            positions[offset] = chargePosition.getX();
            positions[offset + 1] = chargePosition.getY();
            positions[offset + 2] = chargePosition.getZ();
            chargeValues[index] = chargeGetter.applyAsDouble(charge);
        }

        return NativeBridge.computePotentialEnergyChange(
            position.getX(),
            position.getY(),
            position.getZ(),
            positions,
            chargeValues,
            chargeMultiplier
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
}
