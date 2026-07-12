package alku.beryllium.compute;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

public final class PotentialEnergyBatchVerifier {
    private PotentialEnergyBatchVerifier() {
    }

    public static void verifyPotentialEnergyChangeMatchesVanillaPointChargeMath() {
        int[] positions = {
            3, 0, 4,
            0, 0, 2,
            -6, 0, 8
        };
        double[] charges = {10.0, -4.0, 2.5};

        double expected = (10.0 / 5.0 - 4.0 / 2.0 + 2.5 / 10.0) * -3.0;
        double actual = JavaComputeKernels.potentialEnergyChange(0, 0, 0, positions, charges, -3.0);

        assertDoubleEquals(expected, actual, "potential energy change");
    }

    public static void verifyPotentialEnergyChangeReturnsInfinityAtSamePosition() {
        double actual = JavaComputeKernels.potentialEnergyChange(
            1,
            2,
            3,
            new int[] {1, 2, 3},
            new double[] {7.0},
            2.0
        );

        if (actual != Double.POSITIVE_INFINITY) {
            throw new AssertionError("same-position potential mismatch, expected Infinity but got " + actual);
        }
    }

    public static void verifyPotentialEnergyChangePreservesNegativeZeroMultiplierResult() {
        double actual = JavaComputeKernels.potentialEnergyChange(
            0,
            0,
            0,
            new int[] {1, 0, 0},
            new double[] {1.0},
            -0.0
        );

        if (Double.doubleToRawLongBits(actual) != Double.doubleToRawLongBits(0.0)) {
            throw new AssertionError("zero multiplier potential mismatch, expected +0.0 but got " + actual);
        }
    }

    public static void verifyPotentialEnergyBatchSkipsChargeAccessForZeroMultiplier() {
        List<SimpleCharge> charges = List.of(new SimpleCharge(0, BlockPos.ZERO, 1.0));
        double actual = PotentialEnergyBatch.getPotentialEnergyChange(
            charges,
            BlockPos.ZERO,
            0.0,
            charge -> {
                throw new AssertionError("zero multiplier should not read charge position");
            },
            charge -> {
                throw new AssertionError("zero multiplier should not read charge value");
            }
        );

        assertDoubleEquals(0.0, actual, "zero multiplier potential");
    }

    public static void verifyPotentialEnergyBatchPreservesExtractionOrder() {
        List<SimpleCharge> charges = new ArrayList<>();
        charges.add(new SimpleCharge(0, new BlockPos(3, 0, 4), 10.0));
        charges.add(new SimpleCharge(1, new BlockPos(0, 0, 2), -4.0));
        charges.add(new SimpleCharge(2, new BlockPos(-6, 0, 8), 2.5));

        List<Integer> positionReads = new ArrayList<>();
        List<Integer> chargeReads = new ArrayList<>();
        double actual = PotentialEnergyBatch.getPotentialEnergyChange(
            charges,
            BlockPos.ZERO,
            -3.0,
            charge -> {
                positionReads.add(charge.id);
                return charge.position;
            },
            charge -> {
                chargeReads.add(charge.id);
                return charge.charge;
            }
        );

        double expected = (10.0 / 5.0 - 4.0 / 2.0 + 2.5 / 10.0) * -3.0;
        assertDoubleEquals(expected, actual, "batched potential energy change");
        assertListEquals(List.of(0, 1, 2), positionReads, "potential position extraction order");
        assertListEquals(List.of(0, 1, 2), chargeReads, "potential charge extraction order");
    }

    public static void verifyPotentialEnergyBatchJavaPathPreservesSequentialAccumulation() {
        List<SimpleCharge> charges = List.of(
            new SimpleCharge(0, new BlockPos(3, 0, 4), 10.0),
            new SimpleCharge(1, new BlockPos(0, 0, 2), -4.0),
            new SimpleCharge(2, new BlockPos(-6, 0, 8), 2.5)
        );

        double expected = (10.0 / 5.0 - 4.0 / 2.0 + 2.5 / 10.0) * -3.0;
        double actual = PotentialEnergyBatch.getPotentialEnergyChangeJava(
            charges,
            BlockPos.ZERO,
            -3.0,
            charge -> charge.position,
            charge -> charge.charge
        );

        assertDoubleEquals(expected, actual, "small-batch Java potential energy change");
    }

    private static void assertDoubleEquals(double expected, double actual, String label) {
        if (Double.compare(expected, actual) != 0) {
            throw new AssertionError(label + " mismatch, expected " + expected + " but got " + actual);
        }
    }

    private static void assertListEquals(List<Integer> expected, List<Integer> actual, String label) {
        if (!expected.equals(actual)) {
            throw new AssertionError(label + " mismatch, expected " + expected + " but got " + actual);
        }
    }

    private record SimpleCharge(int id, BlockPos position, double charge) {
    }
}
