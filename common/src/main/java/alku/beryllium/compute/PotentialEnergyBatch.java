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

        if (!NativeBatching.shouldUseNativeEntityBatch(charges.size())) {
            return JavaComputeKernels.potentialEnergyChange(
                position.getX(),
                position.getY(),
                position.getZ(),
                positions,
                chargeValues,
                chargeMultiplier
            );
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
}
